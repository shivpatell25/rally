import AVKit
import Observation
import SwiftUI
import Combine
import AetherEngine
import UIKit

@MainActor private enum PlaybackScreenAwakeRegistry {
  private static var activeSessions = Set<UUID>()

  static func setActive(_ active: Bool, for id: UUID) {
    if active { activeSessions.insert(id) } else { activeSessions.remove(id) }
    UIApplication.shared.isIdleTimerDisabled = !activeSessions.isEmpty
  }
}

@MainActor @Observable final class PlaybackSession {
  let player = AVPlayer()
  private(set) var aetherEngine: AetherEngine?
  private let playbackActivityID = UUID()
  var loading = true
  var playing = false
  var videoVisible = false
  var error: String?
  var elapsed: Double = 0
  var duration: Double = 0
  var sourceTitle = "Source"
  var quality = "Auto"
  var resolution = ""
  var bitrate = ""
  var fps = ""
  var codecs = ""
  var audioTracks: [AVMediaSelectionOption] = []
  var captionTracks: [AVMediaSelectionOption] = []
  var aetherAudioTracks: [TrackInfo] = []
  var aetherCaptionTracks: [TrackInfo] = []
  var selectedAudio: String?
  var selectedCaption: String?
  private(set) var target: String = ""
  private(set) var headers: [String: String] = [:]
  private var observation: NSKeyValueObservation?
  @ObservationIgnored nonisolated(unsafe) private var watchdogTask: Task<Void, Never>?
  @ObservationIgnored nonisolated(unsafe) private var reconnectTask: Task<Void, Never>?
  @ObservationIgnored nonisolated(unsafe) private var foregroundTask: Task<Void, Never>?
  @ObservationIgnored private var request: PlaybackRequest?
  private var reconnectAttempts = 0
  private var healthySince = Date()
  private var lastWatchedPosition: Double = 0
  private var scenePosition: Double?
  private var multiViewCount: Int?
  private var progress = PlaybackProgressWatchdog()
  private struct PlaybackRequest {
    let target: String
    let event: SportEvent?
    let candidate: StreamCandidate?
    let container: AppContainer
    let isLive: Bool
  }
  @ObservationIgnored nonisolated(unsafe) private var timeToken: Any?
  @ObservationIgnored nonisolated(unsafe) private var endToken: NSObjectProtocol?
  @ObservationIgnored nonisolated(unsafe) private var stallToken: NSObjectProtocol?
  @ObservationIgnored nonisolated(unsafe) private var interruptionToken: NSObjectProtocol?
  private var resumeAfterInterruption = false
  private var wantsPlayback = false
  private var sceneSuspended = false
  private var audioInterrupted = false
  private var proxy: HeaderMediaProxy?
  @ObservationIgnored private var playlistPermission: PlaylistMediaPermission?
  @ObservationIgnored private var audioGroup: AVMediaSelectionGroup?
  @ObservationIgnored private var captionGroup: AVMediaSelectionGroup?
  @ObservationIgnored private var aetherStateObservation: AnyCancellable?
  @ObservationIgnored private var aetherClockObservation: AnyCancellable?
  @ObservationIgnored private var resolvedSource: URL?
  private var generation = 0
  private var started = Date()
  private var settings: SettingsStore?
  private var firstFrame = false
  private var videoFrameWaitStarted: Date?
  private var lastRemoteTransport = Date.distantPast
  enum RemoteTransport { case play, pause, toggle }
  func remoteTransport(_ action: RemoteTransport) {
    // tvOS can deliver a Siri Remote transport event through SwiftUI and Now Playing.
    // Apply it once so one press never pauses and immediately resumes the stream.
    guard Date().timeIntervalSince(lastRemoteTransport) > 0.25 else { return }
    lastRemoteTransport = Date()
    switch action {
    case .play: resume()
    case .pause: pause()
    case .toggle: toggle()
    }
  }
  var seekable: Bool {
    if let engine = aetherEngine {
      return engine.isLive ? engine.seekableLiveRange != nil : engine.duration > 0
    }
    return player.currentItem?.seekableTimeRanges.isEmpty == false
  }
  var aetherSelectedAudio: Int? { aetherEngine?.activeAudioTrackIndex }
  var aetherSelectedCaption: Int? { aetherEngine?.activeSubtitleTrackIndex }
  var playbackRequested: Bool { wantsPlayback }
  var usesAetherEngine: Bool { aetherEngine != nil }
  var isLive: Bool { aetherEngine?.isLive ?? request?.isLive ?? (!duration.isFinite || duration == 0) }
  var bufferedSeconds: Double {
    if let engine = aetherEngine { return max(0, engine.bufferedPosition - elapsed) }
    guard let ranges = player.currentItem?.loadedTimeRanges else { return 0 }
    return ranges.map { max(0, CMTimeRangeGetEnd($0.timeRangeValue).seconds - elapsed) }.max() ?? 0
  }
  init() {
    player.automaticallyWaitsToMinimizeStalling = true
    // A periodic media-time observer stops firing during some stalls. Check wall time as well.
    watchdogTask = Task { [weak self] in
      while !Task.isCancelled {
        do { try await Task.sleep(for: .seconds(1)) } catch { return }
        self?.checkProgress()
      }
    }
    timeToken = player.addPeriodicTimeObserver(
      forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main
    ) { [weak self] time in Task { @MainActor in self?.tick(time) } }
    endToken = NotificationCenter.default.addObserver(
      forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main
    ) { [weak self] notification in
      Task { @MainActor in
        guard notification.object as? AVPlayerItem === self?.player.currentItem else { return }
        if let self, self.isLive, self.wantsPlayback {
          self.reconnect("This live stream ended. Retry or choose another source.")
          return
        }
        self?.playing = false
        self?.wantsPlayback = false
        self?.loading = false
        if let self { PlaybackScreenAwakeRegistry.setActive(false, for: self.playbackActivityID) }
      }
    }
    interruptionToken = NotificationCenter.default.addObserver(
      forName: AVAudioSession.interruptionNotification, object: nil, queue: .main
    ) { [weak self] notification in
      let type = (notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.uintValue
      let options =
        (notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.uintValue ?? 0
      Task { @MainActor in
        guard let self else { return }
        if type == AVAudioSession.InterruptionType.began.rawValue {
          self.resumeAfterInterruption = self.wantsPlayback
          self.audioInterrupted = true
          self.player.pause()
          PlaybackScreenAwakeRegistry.setActive(false, for: self.playbackActivityID)
          self.playing = false
        } else {
          self.audioInterrupted = false
          if self.resumeAfterInterruption
            && AVAudioSession.InterruptionOptions(rawValue: options).contains(.shouldResume)
          {
            self.resume()
          } else {
            self.wantsPlayback = false
          }
          self.resumeAfterInterruption = false
        }
      }
    }
    stallToken = NotificationCenter.default.addObserver(
      forName: .AVPlayerItemPlaybackStalled, object: nil, queue: .main
    ) { [weak self] notification in
      Task { @MainActor in
        guard notification.object as? AVPlayerItem === self?.player.currentItem, let self else {
          return
        }
        self.settings?.recordHealth(self.target, success: false, stalled: true)
        RallyDiagnostics.shared.record("Playback", code: "Buffering interruption")
      }
    }
  }
  func open(
    _ target: String, event: SportEvent?, candidate: StreamCandidate? = nil, container: AppContainer,
    refreshSource: Bool = false
  ) async {
    foregroundTask?.cancel()
    foregroundTask = nil
    reconnectTask?.cancel()
    reconnectTask = nil
    reconnectAttempts = 0
    wantsPlayback = true
    PlaybackScreenAwakeRegistry.setActive(true, for: playbackActivityID)
    let next = PlaybackRequest(
      target: target, event: event, candidate: candidate, container: container,
      isLive: candidate?.sourceKind == .iptv || candidate?.channel != nil
        || (event?.status.isLive ?? (candidate == nil))
    )
    request = next
    await load(next, refreshSource: refreshSource)
  }
  func retry() async {
    guard let request else { return }
    await open(request.target, event: request.event, candidate: request.candidate,
      container: request.container, refreshSource: true)
  }
  private func load(_ original: PlaybackRequest, refreshSource: Bool = false, resumePosition: Double? = nil) async {
    var request = original
    generation += 1
    let revision = generation
    loading = true
    playing = false
    error = nil
    firstFrame = false
    videoVisible = false
    videoFrameWaitStarted = nil
    elapsed = 0
    duration = 0
    resolution = ""
    bitrate = ""
    fps = ""
    codecs = ""
    audioTracks = []
    captionTracks = []
    aetherAudioTracks = []
    aetherCaptionTracks = []
    audioGroup = nil
    captionGroup = nil
    selectedAudio = nil
    selectedCaption = nil
    started = Date()
    healthySince = started
    lastWatchedPosition = 0
    progress = PlaybackProgressWatchdog()
    settings = request.container.settings
    aetherStateObservation = nil
    aetherClockObservation = nil
    aetherEngine?.stop()
    aetherEngine = nil
    resolvedSource = nil
    target = request.candidate?.id ?? request.target
    sourceTitle = request.candidate?.title ?? (request.event?.compactMatchup ?? "Live TV")
    headers = [:]
    observation = nil
    player.pause()
    player.replaceCurrentItem(with: nil)
    proxy?.stop()
    proxy = nil
    playlistPermission = nil
    guard !sceneSuspended else { loading = false; return }
    do {
      if refreshSource, request.candidate?.sourceKind == .stremio, let event = request.event {
        let options = await request.container.stremio.refreshStreams(for: event)
        try Task.checkCancellation()
        guard revision == generation else { return }
        let selection = await request.container.selectBestStream.select(event: event, channels: [],
          stremioStreams: options)
        let candidates = selection.candidates
        guard let selected = candidates.first(where: { $0.id == request.candidate?.id })
          ?? candidates.first(where: { $0.title == request.candidate?.title })
          ?? candidates.first else { throw RallyNetworkError.invalidResponse }
        request = PlaybackRequest(target: selected.playbackTarget.absoluteString, event: event,
          candidate: selected, container: request.container, isLive: request.isLive)
        guard revision == generation, !Task.isCancelled else { return }
        self.request = request
        target = selected.id
      }
      let resolved: URL
      var requestHeaders = request.candidate?.headers ?? [:]
      var resolvedTitle = request.candidate?.title ?? sourceTitle
      var isPlaylistChannel = false
      let channelId = request.candidate?.channel?.id ?? request.target
      if request.candidate?.channel == nil,
        let direct = URL(string: request.target), ["http", "https"].contains(direct.scheme ?? "") {
        resolved = direct
      } else {
        resolved = try await request.container.iptv.streamUrl(forChannelId: channelId)
        isPlaylistChannel = request.container.settings.provider == .m3u
        requestHeaders.merge(try await request.container.iptv.streamHeaders(forChannelId: channelId)) { _, latest in latest }
        if request.candidate == nil,
          let name = try await request.container.iptv.channels().first(where: { $0.id == channelId })?.name {
          resolvedTitle = name
        }
        if channelId.hasPrefix("stalker:") {
          requestHeaders["Cookie"] = "mac=\(request.container.settings.macAddress); stb_lang=en"
          requestHeaders["Authorization"] = StreamHeaders.normalizedBearerToken(request.container.settings.authToken)
        }
      }
      guard revision == generation, !Task.isCancelled else { return }
      resolvedSource = resolved
      let isAddonStream = request.candidate?.sourceKind == .stremio
      let permission = (isPlaylistChannel || isAddonStream) && resolved.scheme?.lowercased() == "http"
        ? PlaylistMediaPermission(resolved) : nil
      guard NetworkPolicy.shared.permits(resolved) else { throw URLError(.appTransportSecurityRequiresSecureConnection) }
      headers = requestHeaders
      playlistPermission = permission
      if multiViewCount == nil && Self.shouldUseAetherEngine(for: resolved, candidate: request.candidate) {
        try await startAetherEngine(
          source: resolved, headers: requestHeaders, event: request.event, isLive: request.isLive)
        guard revision == generation else { return }
        target = request.candidate?.id ?? request.target
        sourceTitle = resolvedTitle
        loading = false
        playing = true
        videoVisible = true
        firstFrame = true
        settings?.recordHealth(target, success: true, startup: Int(Date().timeIntervalSince(started) * 1000))
        return
      }
      // Playback validates the actual GET request. HEAD probes can reject working providers.
      var media = resolved
      var mediaProxy: HeaderMediaProxy?
      if !requestHeaders.isEmpty || resolved.scheme?.lowercased() == "http" {
        let nextProxy = HeaderMediaProxy(headers: requestHeaders)
        mediaProxy = nextProxy
        media = try await nextProxy.start(resolved)
      }
      guard revision == generation, !Task.isCancelled else { mediaProxy?.stop(); return }
      proxy = mediaProxy
      playlistPermission = permission
      headers = requestHeaders
      sourceTitle = resolvedTitle
      let asset = AVURLAsset(url: media, options: [AVURLAssetHTTPUserAgentKey: requestHeaders["User-Agent"] ?? "Rally-tvOS/1.0"])
      let item = AVPlayerItem(asset: asset)
      item.preferredForwardBufferDuration = request.container.settings.lowLatencyMode ? 3 : 12
      // Leave HLS quality selection to AVPlayer unless the user explicitly chooses a
      // resolution. A fixed 1080p ceiling here prevented Auto from ever requesting 4K,
      // even on Apple TV 4K displays.
      item.preferredMaximumResolution = .zero
      player.replaceCurrentItem(with: item)
      if quality != "Auto" { setQuality(quality) }
      if let multiViewCount { setMultiViewCaps(count: multiViewCount) }
      observation = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
        Task { @MainActor in
          guard let self, revision == self.generation, item === self.player.currentItem else { return }
          switch item.status {
          case .readyToPlay:
            self.error = nil
            if let resumePosition, resumePosition.isFinite, resumePosition > 0 {
              await self.player.seek(to: CMTime(seconds: resumePosition, preferredTimescale: 600))
            }
            guard revision == self.generation, item === self.player.currentItem else { return }
            self.loading = false
            if self.wantsPlayback && !self.sceneSuspended && !self.audioInterrupted {
              self.player.play()
              self.videoFrameWaitStarted = Date()
            }
            await self.loadTracks(asset)
          case .failed:
            self.reconnect("This source could not be played. Try another source or retry.")
          default: break
          }
        }
      }
      if wantsPlayback && !sceneSuspended && !audioInterrupted { player.play() }
    } catch {
      guard revision == generation, !Task.isCancelled else { return }
      reconnect("Couldn’t open this source. Check your provider connection and retry.")
    }
  }
  private func checkProgress() {
    guard request != nil, error == nil, reconnectTask == nil else { return }
    let active = wantsPlayback && !sceneSuspended && !audioInterrupted
    if let engine = aetherEngine {
      elapsed = engine.currentTime
      duration = engine.duration
      loading = active && (engine.state == .loading || engine.state == .seeking || engine.isBuffering)
      playing = engine.state == .playing
      if engine.state == .playing {
        PlaybackScreenAwakeRegistry.setActive(true, for: playbackActivityID)
      } else if !active || engine.state == .paused || engine.state == .ended {
        PlaybackScreenAwakeRegistry.setActive(false, for: playbackActivityID)
      }
      return
    }
    checkVideoFrame(active: active)
    let waiting = player.timeControlStatus == .waitingToPlayAtSpecifiedRate || player.currentItem?.status != .readyToPlay
    let time = player.currentTime().seconds
    let advancing = time.isFinite && abs(time - lastWatchedPosition) > 0.01
    lastWatchedPosition = time.isFinite ? time : 0
    if !active || waiting || !advancing { healthySince = Date() }
    else if Date().timeIntervalSince(healthySince) >= 60 { reconnectAttempts = 0; healthySince = Date() }
    let buffering = active && waiting
    if loading != buffering { loading = buffering }
    if progress.check(wantsPlayback: active, ready: player.currentItem?.status == .readyToPlay,
      position: time.isFinite ? time : 0) {
      reconnect("This source stopped updating. Choose another source or retry.")
    }
  }
  private func checkVideoFrame(active: Bool) {
    guard active, !videoVisible, let item = player.currentItem,
      item.status == .readyToPlay
    else {
      videoFrameWaitStarted = nil
      return
    }
    let now = Date()
    let started = videoFrameWaitStarted ?? now
    videoFrameWaitStarted = started
    guard now.timeIntervalSince(started) >= 8 else { return }
    if aetherEngine == nil, let resolvedSource, let request {
      videoFrameWaitStarted = now
      Task { [weak self] in
        do {
          try await self?.startAetherEngine(
            source: resolvedSource, headers: self?.headers ?? [:], event: request.event,
            isLive: request.isLive)
        } catch {
          await MainActor.run {
            guard let self else { return }
            self.loading = false
            self.wantsPlayback = false
            PlaybackScreenAwakeRegistry.setActive(false, for: self.playbackActivityID)
            self.error = "This source couldn’t be rendered by either player. Try another source."
            RallyDiagnostics.shared.record("Playback", code: "AetherEngine fallback failed: \(error.localizedDescription)")
          }
        }
      }
      return
    }
    guard aetherEngine != nil else { return }
    guard now.timeIntervalSince(started) >= 70 else { return }
    player.pause()
    wantsPlayback = false
    PlaybackScreenAwakeRegistry.setActive(false, for: playbackActivityID)
    playing = false
    loading = false
    error = "This source isn’t displaying video. Try another source."
    RallyDiagnostics.shared.record("Playback", code: "No video frame after AetherEngine fallback")
  }
  private static func shouldUseAetherEngine(for url: URL, candidate: StreamCandidate?) -> Bool {
    let extensionName = url.pathExtension.lowercased()
    let containersNeedingAether = ["mkv", "webm", "avi"]
    return containersNeedingAether.contains(extensionName)
      || candidate?.quality.is4K == true
      || candidate?.quality.isHdr == true
  }
  private func startAetherEngine(
    source: URL, headers: [String: String], event: SportEvent?, isLive: Bool
  ) async throws {
    player.pause()
    player.replaceCurrentItem(with: nil)
    let engine = try AetherEngine()
    aetherEngine?.stop()
    aetherEngine = engine
    let eventIsLive = event?.status.isLive ?? isLive
    aetherStateObservation = engine.$state.sink { [weak self] state in
      Task { @MainActor in
        guard let self, self.aetherEngine === engine else { return }
        switch state {
        case .idle: break
        case .loading: self.loading = true
        case .playing:
          self.loading = false
          self.playing = true
          self.videoVisible = true
          self.videoFrameWaitStarted = nil
          self.firstFrame = true
          PlaybackScreenAwakeRegistry.setActive(self.wantsPlayback, for: self.playbackActivityID)
        case .paused:
          self.loading = false
          self.playing = false
          PlaybackScreenAwakeRegistry.setActive(false, for: self.playbackActivityID)
        case .seeking: self.loading = true
        case .ended:
          self.loading = false
          self.playing = false
          self.wantsPlayback = false
          PlaybackScreenAwakeRegistry.setActive(false, for: self.playbackActivityID)
        case .error(let message):
          self.loading = false
          self.playing = false
          self.wantsPlayback = false
          self.error = message
          PlaybackScreenAwakeRegistry.setActive(false, for: self.playbackActivityID)
        }
      }
    }
    aetherClockObservation = engine.clock.$currentTime.sink { [weak self] time in
      Task { @MainActor in
        guard let self, self.aetherEngine === engine else { return }
        self.elapsed = time
        self.duration = engine.duration
      }
    }
    let options = LoadOptions(
      httpHeaders: headers,
      matchContentEnabled: true,
      panelIsInHDRMode: UIScreen.main.currentEDRHeadroom > 1,
      isLive: eventIsLive,
      // Live IPTV responses can be slow or sparse. FFmpeg's 50 MB / 60 s defaults can
      // make the initial demux probe look like a hung player. Sports streams expose
      // video and audio quickly, so bound discovery while leaving enough headroom for TS.
      probesize: 3 * 1024 * 1024,
      maxAnalyzeDuration: 3_000_000,
      autoplay: true
    )
    if let probe = try await engine.load(url: source, options: options) {
      aetherAudioTracks = probe.audioTracks
      aetherCaptionTracks = probe.subtitleTracks
      resolution = probe.videoWidth > 0 && probe.videoHeight > 0
        ? "\(probe.videoWidth) × \(probe.videoHeight)" : ""
      fps = probe.videoFrameRate.map { String(format: "%.2f fps", $0) } ?? ""
      let format: String
      switch probe.videoFormat {
      case .sdr: format = "SDR"
      case .hdr10: format = "HDR10"
      case .hdr10Plus: format = "HDR10+"
      case .dolbyVision: format = "Dolby Vision"
      case .hlg: format = "HLG"
      }
      codecs = [probe.videoCodecName, format].compactMap { $0 }.joined(separator: " · ")
      selectedAudio = probe.audioTracks.first(where: { $0.id == engine.activeAudioTrackIndex })?.name
      selectedCaption = probe.subtitleTracks.first(where: { $0.id == engine.activeSubtitleTrackIndex })?.name
    }
    videoVisible = true
    videoFrameWaitStarted = nil
    if wantsPlayback { engine.play() }
  }
  private func reconnect(_ message: String) {
    guard reconnectTask == nil, let request else { return }
    // Release the failed item's decoder, proxy and upstream connection immediately.
    observation = nil
    player.pause()
    player.replaceCurrentItem(with: nil)
    proxy?.stop()
    proxy = nil
    playlistPermission = nil
    settings?.recordHealth(target, success: false, stalled: true)
    guard reconnectAttempts < 2 else {
      loading = false
      playing = false
      player.pause()
      error = message
      PlaybackScreenAwakeRegistry.setActive(false, for: playbackActivityID)
      return
    }
    reconnectAttempts += 1
    loading = true
    let revision = generation
    RallyDiagnostics.shared.record("Playback", code: "Reconnecting stalled source")
    reconnectTask = Task { [weak self] in
      do { try await Task.sleep(for: .seconds(1)) } catch { return }
      guard let self, revision == self.generation, !Task.isCancelled else { return }
      self.reconnectTask = nil
      guard !self.sceneSuspended else { return }
      await self.load(request, refreshSource: true)
    }
  }
  func pause() {
    wantsPlayback = false
    PlaybackScreenAwakeRegistry.setActive(false, for: playbackActivityID)
    player.pause()
    aetherEngine?.pause()
    playing = false
  }
  func resume() {
    wantsPlayback = true
    PlaybackScreenAwakeRegistry.setActive(true, for: playbackActivityID)
    if !sceneSuspended && !audioInterrupted {
      player.play()
      aetherEngine?.play()
      if !videoVisible { videoFrameWaitStarted = Date() }
    }
    playing = aetherEngine?.state == .playing || player.rate > 0
  }
  func suspendForScene() {
    guard !sceneSuspended else { return }
    scenePosition = !isLive && (aetherEngine?.currentTime ?? player.currentTime().seconds).isFinite
      ? (aetherEngine?.currentTime ?? player.currentTime().seconds) : nil
    sceneSuspended = true
    PlaybackScreenAwakeRegistry.setActive(false, for: playbackActivityID)
    generation += 1
    reconnectTask?.cancel()
    reconnectTask = nil
    foregroundTask?.cancel()
    foregroundTask = nil
    observation = nil
    player.pause()
    aetherEngine?.pause()
    player.replaceCurrentItem(with: nil)
    proxy?.stop()
    proxy = nil
    playlistPermission = nil
    playing = false
    videoVisible = false
    videoFrameWaitStarted = nil
  }
  func restoreForScene() {
    guard sceneSuspended else { return }
    sceneSuspended = false
    guard let request, error == nil else { return }
    let position = scenePosition
    foregroundTask = Task { [weak self] in
      await self?.load(request, refreshSource: true, resumePosition: position)
    }
  }
  func toggle() { wantsPlayback ? pause() : resume() }
  func restart() {
    if let aetherEngine { Task { await aetherEngine.seek(to: 0); aetherEngine.play() }; return }
    guard let range = player.currentItem?.seekableTimeRanges.first?.timeRangeValue else { return }
    player.seek(to: range.start, toleranceBefore: .zero, toleranceAfter: .zero)
    resume()
  }
  func liveEdge() {
    if let aetherEngine, let edge = aetherEngine.seekableLiveRange?.upperBound {
      Task { await aetherEngine.seek(to: edge - 1); aetherEngine.play() }; return
    }
    guard let range = player.currentItem?.seekableTimeRanges.last?.timeRangeValue else { return }
    player.seek(
      to: CMTimeSubtract(CMTimeRangeGetEnd(range), CMTime(seconds: 1, preferredTimescale: 600)))
    resume()
  }
  func seek(_ seconds: Double) {
    if let aetherEngine {
      Task { await aetherEngine.seek(to: max(0, aetherEngine.currentTime + seconds)) }
      return
    }
    guard seconds.isFinite, let item = player.currentItem else { return }
    // A restored VOD item can be ready before its HLS seekable ranges arrive.
    // Its finite duration still permits normal seeking; live streams require
    // the actual DVR window. Use media time, not the delayed display observer.
    let range: CMTimeRange
    if let available = item.seekableTimeRanges.last?.timeRangeValue {
      range = available
    } else if item.duration.seconds.isFinite, item.duration.seconds > 0 {
      range = CMTimeRange(start: .zero, duration: item.duration)
    } else { return }
    let position = player.currentTime().seconds
    guard position.isFinite else { return }
    let next = min(CMTimeRangeGetEnd(range).seconds, max(range.start.seconds, position + seconds))
    player.seek(to: CMTime(seconds: next, preferredTimescale: 600))
  }
  func setQuality(_ label: String) {
    quality = label
    guard aetherEngine == nil else { return }
    videoFrameWaitStarted = videoVisible ? nil : Date()
    guard let item = player.currentItem else { return }
    let heights = ["Auto": 0, "2160p": 2160, "1080p": 1080, "720p": 720, "480p": 480]
    let height = heights[label] ?? 0
    item.preferredMaximumResolution = CGSize(
      width: CGFloat(height) * 16 / 9, height: CGFloat(height))
    item.preferredPeakBitRate = height == 480 ? 1_200_000 : height == 720 ? 2_500_000 : 0
  }
  func selectAudio(_ option: AVMediaSelectionOption?) {
    guard aetherEngine == nil else { return }
    if let group = audioGroup {
      player.currentItem?.select(option, in: group)
      selectedAudio = option?.displayName
    }
  }
  func selectCaption(_ option: AVMediaSelectionOption?) {
    guard aetherEngine == nil else { return }
    if let group = captionGroup {
      player.currentItem?.select(option, in: group)
      selectedCaption = option?.displayName
    }
  }
  func selectAetherAudio(_ index: Int) {
    guard let engine = aetherEngine else { return }
    engine.selectAudioTrack(index: index)
    selectedAudio = aetherAudioTracks.first(where: { $0.id == index })?.name
  }
  func selectAetherCaption(_ index: Int) {
    guard let engine = aetherEngine else { return }
    engine.selectSubtitleTrack(index: index)
    selectedCaption = aetherCaptionTracks.first(where: { $0.id == index })?.name
  }
  func clearCaption() {
    if let aetherEngine { aetherEngine.clearSubtitle(); selectedCaption = nil; return }
    selectCaption(nil)
  }
  func setMultiViewCaps(count: Int) {
    multiViewCount = count
    player.currentItem?.preferredMaximumResolution =
      count >= 3 ? CGSize(width: 854, height: 480) : CGSize(width: 1280, height: 720)
    player.currentItem?.preferredPeakBitRate = count >= 3 ? 1_200_000 : 2_500_000
    player.currentItem?.preferredForwardBufferDuration = 6
  }
  func stop() {
    generation += 1
    reconnectTask?.cancel()
    reconnectTask = nil
    foregroundTask?.cancel()
    foregroundTask = nil
    request = nil
    wantsPlayback = false
    PlaybackScreenAwakeRegistry.setActive(false, for: playbackActivityID)
    player.pause()
    aetherEngine?.stop()
    aetherEngine = nil
    aetherStateObservation = nil
    aetherClockObservation = nil
    player.replaceCurrentItem(with: nil)
    observation = nil
    proxy?.stop()
    proxy = nil
    playlistPermission = nil
    playing = false
    loading = false
  }
  private func loadTracks(_ asset: AVAsset) async {
    let audio = try? await asset.loadMediaSelectionGroup(for: .audible)
    let caption = try? await asset.loadMediaSelectionGroup(for: .legible)
    guard player.currentItem?.asset === asset else { return }
    audioGroup = audio
    captionGroup = caption
    audioTracks = audio?.options ?? []
    captionTracks = caption?.options ?? []
    if let audio {
      selectedAudio =
        player.currentItem?.currentMediaSelection.selectedMediaOption(in: audio)?.displayName
    }
    if let caption {
      selectedCaption =
        player.currentItem?.currentMediaSelection.selectedMediaOption(in: caption)?.displayName
    }
    if let track = try? await asset.loadTracks(withMediaType: .video).first {
      let frameRate = (try? await track.load(.nominalFrameRate)) ?? 0
      guard player.currentItem?.asset === asset else { return }
      fps = frameRate > 0 ? String(format: "%.1f fps", frameRate) : ""
      if let formats = try? await track.load(.formatDescriptions) {
        guard player.currentItem?.asset === asset else { return }
        codecs = formats.map { description in
          let code = CMFormatDescriptionGetMediaSubType(description)
          return String(
            bytes: [
              UInt8((code >> 24) & 255), UInt8((code >> 16) & 255), UInt8((code >> 8) & 255),
              UInt8(code & 255),
            ], encoding: .ascii) ?? "Unknown"
        }.joined(separator: ", ")
      }
    }
  }
  private func tick(_ time: CMTime) {
    elapsed = time.seconds.isFinite ? time.seconds : 0
    duration = player.currentItem?.duration.seconds ?? 0
    playing = player.rate > 0
    if playing && !firstFrame {
      firstFrame = true
      settings?.recordHealth(
        target, success: true, startup: Int(Date().timeIntervalSince(started) * 1000))
    }
    if let event = player.currentItem?.accessLog()?.events.last {
      bitrate = String(format: "%.1f Mbps", event.observedBitrate / 1_000_000)
      if fps.isEmpty { fps = "\(event.numberOfDroppedVideoFrames) dropped frames" }
    }
    if let size = player.currentItem?.presentationSize, size.height > 0 {
      resolution = "\(Int(size.width)) × \(Int(size.height))"
    }
  }
  deinit {
    watchdogTask?.cancel()
    reconnectTask?.cancel()
    foregroundTask?.cancel()
    if let timeToken { player.removeTimeObserver(timeToken) }
    if let endToken { NotificationCenter.default.removeObserver(endToken) }
    if let stallToken { NotificationCenter.default.removeObserver(stallToken) }
    if let interruptionToken { NotificationCenter.default.removeObserver(interruptionToken) }
  }
}
struct RallyVideoSurface: UIViewRepresentable {
  let player: AVPlayer
  var onReadyForDisplay: ((Bool) -> Void)? = nil
  final class Coordinator {
    var observation: NSKeyValueObservation?
    var onReady: ((Bool) -> Void)?
  }
  func makeCoordinator() -> Coordinator { Coordinator() }
  final class Surface: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    var videoLayer: AVPlayerLayer { layer as! AVPlayerLayer }
  }
  func makeUIView(context: Context) -> Surface {
    let view = Surface()
    view.backgroundColor = .black
    view.videoLayer.videoGravity = .resizeAspect
    view.videoLayer.player = player
    context.coordinator.onReady = onReadyForDisplay
    context.coordinator.observation = view.videoLayer.observe(\.isReadyForDisplay, options: [.initial, .new]) {
      [weak coordinator = context.coordinator] layer, _ in
      let ready = layer.isReadyForDisplay
      Task { @MainActor in coordinator?.onReady?(ready) }
    }
    return view
  }
  func updateUIView(_ view: Surface, context: Context) {
    context.coordinator.onReady = onReadyForDisplay
    if view.videoLayer.player !== player { view.videoLayer.player = player }
  }
  static func dismantleUIView(_ view: Surface, coordinator: Coordinator) {
    coordinator.observation = nil
    coordinator.onReady = nil
    view.videoLayer.player = nil
  }
}

struct RallyPlaybackSurface: View {
  let session: PlaybackSession

  var body: some View {
    if let engine = session.aetherEngine {
      AetherPlayerSurface(engine: engine).background(.black)
    } else {
      RallyVideoSurface(player: session.player) { session.videoVisible = $0 }
    }
  }
}


/// Wall-time monitoring continues while AVPlayer is stuck and respects intentional pauses.
struct PlaybackProgressWatchdog {
  private var lastProgressAt = Date().timeIntervalSinceReferenceDate
  private var lastPosition: Double = 0
  private var hasProgress = false
  mutating func check(now: Double = Date().timeIntervalSinceReferenceDate, wantsPlayback: Bool,
    ready: Bool, position: Double) -> Bool {
    defer { lastPosition = position }
    guard wantsPlayback else { lastProgressAt = now; return false }
    if ready && abs(position - lastPosition) > 0.01 {
      hasProgress = true
      lastProgressAt = now
      return false
    }
    return now - lastProgressAt >= (hasProgress ? 15 : 25)
  }
}
