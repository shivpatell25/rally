import Foundation
import SwiftUI
import UIKit
import VLCKit

/// Adapts VideoLAN's tvOS playback engine to Rally's existing transport UI.
/// VLC owns decoding and rendering; Rally continues to own source selection,
/// navigation, controls, watchdogs, and recovery policy.
@MainActor final class VLCPlaybackEngine: NSObject, VLCMediaPlayerDelegate {
  struct Track: Sendable, Equatable {
    let id: Int32
    let type: String
    let title: String?
    let lang: String?
    let selected: Bool
    var name: String {
      if let title, !title.isEmpty { return title }
      return lang ?? "Track \(id + 1)"
    }
  }

  struct Snapshot: Sendable {
    var ready = false
    var buffering = true
    var paused = false
    var ended = false
    var position: Double = 0
    var duration: Double = 0
    var cacheEnd: Double = 0
    var cacheStart: Double = 0
    var seekable = false
    var inputBitrate: Double = 0
    var discontinuities = 0
    var width: Double = 0
    var height: Double = 0
    var fps: Double = 0
    var codec = ""
    var format = ""
    var transfer = ""
    var tracks: [Track] = []
    var failure: Int32?
  }

  private(set) var player: VLCMediaPlayer?
  private var callback: (@Sendable (Snapshot) -> Void)?
  private var snapshot = Snapshot()
  private(set) var latestSnapshot = Snapshot()
  private var bufferingProgress: Float = 0
  private var updateTimer: Timer?
  private weak var drawableView: UIView?
  private var shouldPlay = true
  private var playbackStarted = false
  private var requestedPosition: Double?

  var view: UIView? { drawableView }

  func open(
    url: URL, live: Bool, lowLatency: Bool, position: Double?, playing: Bool, muted: Bool,
    update: @escaping @Sendable (Snapshot) -> Void
  ) {
    stop()
    callback = update
    snapshot = Snapshot()
    latestSnapshot = snapshot
    bufferingProgress = 0

    let player = VLCMediaPlayer()
    player.delegate = self
    player.timeChangeUpdateInterval = 0.5
    player.minimalTimePeriod = 500_000
    shouldPlay = playing
    requestedPosition = position
    playbackStarted = false
    if let drawableView, drawableView.window != nil { player.drawable = drawableView }
    player.audio?.isMuted = muted

    guard let media = VLCMedia(url: url) else {
      snapshot.failure = -1
      callback?(snapshot)
      return
    }
    let cacheMilliseconds = lowLatency ? 800 : (live ? 1_800 : 4_000)
    media.addOption(":network-caching=\(cacheMilliseconds)")
    media.addOption(":live-caching=\(cacheMilliseconds)")
    media.addOption(":file-caching=\(cacheMilliseconds)")
    media.addOption(":no-video-title-show")
    media.addOption(":http-reconnect")
    self.player = player
    player.media = media
    startWhenDrawableIsAttached()
    updateTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
      Task { @MainActor in self?.publish() }
    }
    publish()
  }

  func attach(to view: UIView) {
    drawableView = view
    player?.drawable = view
    startWhenDrawableIsAttached()
  }

  func detach(from view: UIView) {
    guard drawableView === view else { return }
    player?.drawable = nil
    drawableView = nil
  }

  private func startWhenDrawableIsAttached() {
    guard !playbackStarted, let player, drawableView?.window != nil else { return }
    playbackStarted = true
    player.play()
    if !shouldPlay { player.pause() }
    if let position = requestedPosition, position > 0 {
      DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self, weak player] in
        guard let self, let player, player.isSeekable else { return }
        self.seek(position)
      }
    }
  }

  func pause(_ paused: Bool) {
    shouldPlay = !paused
    guard let player else { return }
    if paused { player.pause() } else { startWhenDrawableIsAttached() }
    publish()
  }

  func stop() {
    updateTimer?.invalidate()
    updateTimer = nil
    guard let player else { callback = nil; return }
    player.delegate = nil
    player.stop()
    player.drawable = nil
    player.media = nil
    self.player = nil
    playbackStarted = false
    requestedPosition = nil
    callback = nil
  }

  func seek(_ position: Double) {
    guard position.isFinite, let player, player.isSeekable else { return }
    player.time = VLCTime(int: Int32(max(0, position * 1_000)))
    publish()
  }

  func audio(_ id: Int?) {
    guard let id, let player else { return }
    player.selectTrack(at: id, type: .audio)
    publish()
  }

  func caption(_ id: Int?) {
    guard let player else { return }
    if let id { player.selectTrack(at: id, type: .text) }
    else { player.selectTrack(at: -1, type: .text) }
    publish()
  }

  nonisolated func mediaPlayerStateChanged(_ newState: VLCMediaPlayerState) {
    Task { @MainActor in
      if newState == .error { snapshot.failure = Int32(newState.rawValue) }
      publish()
    }
  }

  nonisolated func mediaPlayerBufferingChanged(_ progress: Float) {
    Task { @MainActor in
      bufferingProgress = progress
      publish()
    }
  }

  private func publish() {
    guard let player else { latestSnapshot = snapshot; callback?(snapshot); return }
    let time = Double(player.time.intValue) / 1_000
    let duration = Double(player.media?.length.intValue ?? 0) / 1_000
    let ready = (player.state == .playing || player.state == .paused) && player.hasVideoOut
    snapshot.ready = ready
    snapshot.buffering = !ready || bufferingProgress < 1
    snapshot.paused = player.state == .paused || !player.isPlaying
    snapshot.position = time.isFinite ? time : 0
    snapshot.duration = duration.isFinite ? duration : 0
    snapshot.cacheEnd = max(snapshot.position, snapshot.duration)
    snapshot.seekable = player.isSeekable

    let size = player.videoSize
    if size.width > 0 && size.height > 0 {
      snapshot.width = size.width
      snapshot.height = size.height
    }
    if let track = player.media?.tracksInformation.first(where: { $0.type == .video }),
      let video = track.video {
      snapshot.fps = video.frameRateDenominator > 0
        ? Double(video.frameRate) / Double(video.frameRateDenominator) : 0
      snapshot.codec = track.codecName()
      snapshot.inputBitrate = Double(track.bitrate)
    }
    snapshot.tracks = player.audioTracks.enumerated().map { index, track in
      Track(id: Int32(index), type: "audio", title: track.trackName, lang: track.language, selected: track.isSelected)
    } + player.textTracks.enumerated().map { index, track in
      Track(id: Int32(index), type: "sub", title: track.trackName, lang: track.language, selected: track.isSelected)
    }
    latestSnapshot = snapshot
    callback?(snapshot)
  }
}

@MainActor struct VLCPlaybackSurface: UIViewRepresentable {
  let engine: VLCPlaybackEngine

  func makeUIView(context: Context) -> UIView {
    let view = VLCVideoContainerView(frame: .zero)
    view.backgroundColor = .black
    view.engine = engine
    engine.attach(to: view)
    return view
  }

  func updateUIView(_ view: UIView, context: Context) {
    engine.attach(to: view)
  }

  static func dismantleUIView(_ view: UIView, coordinator: ()) {
    if let view = view as? VLCVideoContainerView { view.engine?.detach(from: view) }
  }
}

@MainActor private final class VLCVideoContainerView: UIView {
  weak var engine: VLCPlaybackEngine?

  override func didMoveToWindow() {
    super.didMoveToWindow()
    if window != nil { engine?.attach(to: self) }
  }
}
