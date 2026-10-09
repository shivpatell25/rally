import AVFoundation
import Libmpv
import UIKit

struct MPVCacheWindow {
  struct Range { let start: Double; let end: Double }
  private var previousPosition: Double?
  private var discontinuityFloor: Double?
  private(set) var discontinuities = 0
  mutating func observe(position: Double, ranges: [Range], end: Double?, intentionalSeek: Bool)
    -> Range
  {
    if let previousPosition, !intentionalSeek,
      position - previousPosition > 30 || position < previousPosition - 2
    {
      discontinuityFloor = position
      discontinuities += 1
    }
    previousPosition = position
    let active = ranges.filter {
      $0.start.isFinite && $0.end.isFinite && $0.start <= position + 1 && $0.end >= position - 1
    }.max { $0.start < $1.start }
    let start = min(position, max(active?.start ?? position, discontinuityFloor ?? -.infinity))
    let candidate = min(active?.end ?? end ?? position, end ?? active?.end ?? position)
    let safeEnd = candidate >= position && candidate - position <= 120 ? candidate : position
    return Range(start: start, end: safeEnd)
  }
}

/// The media engine owns decoding and rendering; feature views only send transport actions.
/// All libmpv calls and destruction are serialized off the UI thread.
final class MPVPlaybackEngine: @unchecked Sendable {
  static func isOutputInitializationFailure(_ code: Int) -> Bool {
    [MPV_ERROR_UNINITIALIZED, MPV_ERROR_AO_INIT_FAILED, MPV_ERROR_VO_INIT_FAILED]
      .contains { Int($0.rawValue) == code }
  }
  static func isFormatFailure(_ code: Int) -> Bool {
    [MPV_ERROR_UNKNOWN_FORMAT, MPV_ERROR_UNSUPPORTED].contains { Int($0.rawValue) == code }
  }
  struct Track: Decodable, Sendable, Equatable {
    let id: Int
    let type: String
    let title: String?
    let lang: String?
    let selected: Bool?
    var name: String {
      if let title, !title.isEmpty { return title }
      if let lang, !lang.isEmpty {
        return Locale.current.localizedString(forLanguageCode: lang) ?? lang
      }
      return "Track \(id)"
    }
  }
  private struct CacheState: Decodable {
    struct Range: Decodable {
      let start: Double
      let end: Double
    }
    var ranges: [Range]?
    var end: Double?
    var rate: Double?
    enum CodingKeys: String, CodingKey {
      case ranges = "seekable-ranges"
      case end = "cache-end"
      case rate = "raw-input-rate"
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
  private let queue = DispatchQueue(label: "com.rally.playback.mpv", qos: .userInitiated)
  private var handle: OpaquePointer?
  private var timer: DispatchSourceTimer?
  private var loaded = false
  private var failure: Int32?
  private var surface: RallyMetalVideoLayer?
  private var callback: (@Sendable (Snapshot) -> Void)?
  private var playWhenReady = true
  private var liveConfiguration: Bool?
  private var cachedWindow = MPVCacheWindow()
  private var intentionalSeekUntil = Date.distantPast

  func open(
    url: URL, layer: RallyMetalVideoLayer, live: Bool, lowLatency: Bool,
    position: Double?, playing: Bool, muted: Bool, cacheBytes: Int = 67_108_864,
    update: @escaping @Sendable (Snapshot) -> Void
  ) {
    queue.async { [self] in
      destroy()
      surface = layer  // Keep the layer alive until the video-output thread has stopped.
      callback = update
      playWhenReady = playing
      liveConfiguration = live
      guard let next = mpv_create() else {
        reportFailure(-1)
        return
      }
      handle = next
      var window = Int64(Int(bitPattern: Unmanaged.passUnretained(layer).toOpaque()))
      mpv_set_option(next, "wid", MPV_FORMAT_INT64, &window)
      let options: [String: String] = [
        "config": "no", "load-scripts": "no", "osc": "no", "input-default-bindings": "no",
        "vo": "gpu-next", "gpu-api": "vulkan", "gpu-context": "moltenvk",
        "hwdec": "videotoolbox", "ao": "avfoundation,audiounit",
        "audio-channels": "stereo", "audio-fallback-to-null": "no",
        "vulkan-swap-mode": "fifo", "vulkan-queue-count": "1",
        "vulkan-async-compute": "no", "vulkan-async-transfer": "no",
        "vulkan-disable-interop": "yes", "target-colorspace-hint": "yes",
        "tone-mapping": "auto", "hdr-compute-peak": "yes",
        "keep-open": "yes", "loop-file": "no", "network-timeout": "15",
        // Opaque proxy URLs must enter FFmpeg's HLS demuxer, never mpv's M3U clip playlist.
        "demuxer": "lavf",
        "cache": "yes", "cache-pause": "yes", "cache-pause-wait": lowLatency ? "1" : "2",
        "demuxer-readahead-secs": live ? (lowLatency ? "4" : "12") : "30",
        "demuxer-max-bytes": String(cacheBytes), "demuxer-max-back-bytes": String(cacheBytes / 2),
        "demuxer-seekable-cache": "yes",
        "demuxer-lavf-o": "live_start_index=-2,http_persistent=0,allowed_extensions=ALL",
        "pause": playing ? "no" : "yes", "mute": muted ? "yes" : "no",
        "msg-level": "all=no",  // Engine messages may contain private provider URLs.
      ]
      for (name, value) in options {
        let result = mpv_set_option_string(next, name, value)
        if result < 0
        { /* Unsupported optional controls never prevent an otherwise valid decoder. */
        }
      }
      if let position, position.isFinite, position > 0 {
        // Apply the start offset before decoder initialization. A seek issued
        // from FILE_LOADED can race the first frame of fragmented HLS.
        mpv_set_option_string(next, "start", String(position))
      }
      let status = mpv_initialize(next)
      guard status >= 0 else {
        reportFailure(status)
        destroy()
        return
      }
      commandNow(["loadfile", url.absoluteString, "replace"])
      let source = DispatchSource.makeTimerSource(queue: queue)
      source.schedule(deadline: .now(), repeating: .milliseconds(250))
      source.setEventHandler { [weak self] in self?.poll() }
      timer = source
      source.resume()
    }
  }
  func pause(_ value: Bool) { set("pause", value ? "yes" : "no") }
  func mute(_ value: Bool) { set("mute", value ? "yes" : "no") }
  func audio(_ id: Int?) { set("aid", id.map(String.init) ?? "auto") }
  func caption(_ id: Int?) { set("sid", id.map(String.init) ?? "no") }
  /// The manifest is authoritative, including channel URLs that have no event metadata.
  func configureLive(_ live: Bool, lowLatency: Bool) {
    queue.async { [self] in
      guard let handle, liveConfiguration != live else { return }
      liveConfiguration = live
      mpv_set_property_string(
        handle, "demuxer-readahead-secs", live ? (lowLatency ? "4" : "12") : "30")
    }
  }
  func seek(_ seconds: Double) {
    guard seconds.isFinite else { return }
    queue.async { [self] in
      intentionalSeekUntil = Date().addingTimeInterval(3)
      commandNow(["seek", String(max(0, seconds)), "absolute+exact"])
    }
  }
  func stop() { queue.async { [self] in destroy() } }
  func shutdown() async {
    await withCheckedContinuation { continuation in
      queue.async { [self] in
        destroy()
        continuation.resume()
      }
    }
  }
  private func set(_ name: String, _ value: String) {
    queue.async { [self] in
      guard let handle else { return }
      mpv_set_property_string(handle, name, value)
    }
  }
  private func command(_ args: [String]) { queue.async { [self] in commandNow(args) } }
  private func commandNow(_ args: [String]) {
    guard let handle else { return }
    let strings = args.map { strdup($0) }
    defer { strings.forEach { free($0) } }
    var pointers = strings.map { UnsafePointer<CChar>($0) }
    pointers.append(nil)
    let result = pointers.withUnsafeMutableBufferPointer { mpv_command(handle, $0.baseAddress) }
    if result < 0 {
      let operation = args.first ?? "command"
      Task { @MainActor in
        RallyDiagnostics.shared.record("MPV", code: "\(operation) failed: \(result)")
      }
    }
  }
  private func number(_ key: String) -> Double {
    guard let handle else { return 0 }
    var result: Double = 0
    return mpv_get_property(handle, key, MPV_FORMAT_DOUBLE, &result) >= 0 && result.isFinite
      ? result : 0
  }
  private func flag(_ key: String) -> Bool {
    guard let handle else { return false }
    var result: Int32 = 0
    return mpv_get_property(handle, key, MPV_FORMAT_FLAG, &result) >= 0 && result != 0
  }
  private func string(_ key: String) -> String {
    guard let handle, let pointer = mpv_get_property_string(handle, key) else { return "" }
    defer { mpv_free(pointer) }
    return String(cString: pointer)
  }
  private func json<T: Decodable>(_ key: String, as type: T.Type) -> T? {
    guard let handle else { return nil }
    var node = mpv_node()
    guard mpv_get_property(handle, key, MPV_FORMAT_NODE, &node) >= 0 else { return nil }
    defer { mpv_free_node_contents(&node) }
    guard let object = object(node), JSONSerialization.isValidJSONObject(object),
      let data = try? JSONSerialization.data(withJSONObject: object)
    else { return nil }
    return try? JSONDecoder().decode(type, from: data)
  }
  private func object(_ node: mpv_node) -> Any? {
    switch node.format {
    case MPV_FORMAT_STRING: return node.u.string.map { String(cString: $0) }
    case MPV_FORMAT_FLAG: return node.u.flag != 0
    case MPV_FORMAT_INT64: return node.u.int64
    case MPV_FORMAT_DOUBLE: return node.u.double_.isFinite ? node.u.double_ : 0
    case MPV_FORMAT_NODE_ARRAY, MPV_FORMAT_NODE_MAP:
      guard let list = node.u.list?.pointee, let values = list.values else { return nil }
      if node.format == MPV_FORMAT_NODE_ARRAY {
        return (0..<Int(list.num)).map { object(values[$0]) ?? NSNull() }
      }
      guard let keys = list.keys else { return nil }
      var result: [String: Any] = [:]
      for i in 0..<Int(list.num) {
        if let key = keys[i] { result[String(cString: key)] = object(values[i]) ?? NSNull() }
      }
      return result
    default: return nil
    }
  }
  private func poll() {
    guard let handle else { return }
    while let event = mpv_wait_event(handle, 0), event.pointee.event_id != MPV_EVENT_NONE {
      switch event.pointee.event_id {
      case MPV_EVENT_FILE_LOADED:
        loaded = true
        intentionalSeekUntil = Date().addingTimeInterval(3)
        mpv_set_property_string(handle, "pause", playWhenReady ? "no" : "yes")
      case MPV_EVENT_END_FILE:
        if let data = event.pointee.data?.assumingMemoryBound(to: mpv_event_end_file.self),
          data.pointee.reason == MPV_END_FILE_REASON_ERROR
        {
          failure = data.pointee.error
        }
      default: break
      }
    }
    var state = Snapshot()
    state.ready = loaded
    state.buffering = !loaded || flag("paused-for-cache") || flag("seeking")
    state.paused = flag("pause")
    state.ended = flag("eof-reached")
    state.position = number("time-pos")
    state.duration = number("duration")
    state.seekable = flag("seekable")
    if let cache = json("demuxer-cache-state", as: CacheState.self) {
      let window = cachedWindow.observe(
        position: state.position,
        ranges: (cache.ranges ?? []).map { MPVCacheWindow.Range(start: $0.start, end: $0.end) },
        end: cache.end, intentionalSeek: Date() < intentionalSeekUntil || flag("seeking"))
      state.cacheEnd = window.end
      state.cacheStart = window.start
      state.discontinuities = cachedWindow.discontinuities
      state.inputBitrate = max(0, (cache.rate ?? 0) * 8)
    }
    state.width = number("video-params/w")
    state.height = number("video-params/h")
    state.fps = number("container-fps")
    state.codec = string("video-codec")
    state.format = string("file-format")
    state.transfer = string("video-params/gamma")
    state.tracks = json("track-list", as: [Track].self) ?? []
    state.failure = failure
    callback?(state)
  }
  private func reportFailure(_ code: Int32) {
    var state = Snapshot()
    state.failure = code
    callback?(state)
  }
  private func destroy() {
    timer?.setEventHandler {}
    timer?.cancel()
    timer = nil
    callback = nil
    if let handle {
      self.handle = nil
      mpv_terminate_destroy(handle)
    }
    surface = nil
    loaded = false
    failure = nil
    liveConfiguration = nil
    cachedWindow = MPVCacheWindow()
    intentionalSeekUntil = .distantPast
  }
  deinit { if let handle { mpv_terminate_destroy(handle) } }
}

/// libplacebo owns output color space. UI resizing must never block its video-output thread.
final class RallyMetalVideoLayer: CAMetalLayer, @unchecked Sendable {
  override var drawableSize: CGSize {
    get { super.drawableSize }
    set { if newValue.width > 1 && newValue.height > 1 { super.drawableSize = newValue } }
  }
}
