import AVFoundation
import KSPlayer
import SwiftUI
import UIKit

/// Adapts KSPlayer's native AVPlayer / FFmpeg fallback to Rally's existing transport UI.
@MainActor final class KSPlaybackEngine: NSObject, KSPlayerLayerDelegate {
  struct Track: Sendable, Equatable {
    let id: Int32
    let type: String
    let title: String?
    let lang: String?
    let selected: Bool
    var name: String { if let title, !title.isEmpty { return title }; return lang ?? "Track \(id)" }
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

  private(set) var layer: KSPlayerLayer?
  private var callback: (@Sendable (Snapshot) -> Void)?

  var view: UIView? { layer?.player.view }
  private var snapshot = Snapshot()

  func open(
    url: URL, live: Bool, lowLatency: Bool, position: Double?, playing: Bool, muted: Bool,
    update: @escaping @Sendable (Snapshot) -> Void
  ) {
    stop()
    callback = update
    snapshot = Snapshot()

    let options = KSOptions()
    options.registerRemoteControll = false
    options.preferredForwardBufferDuration = lowLatency ? 3 : 8
    options.maxBufferDuration = live ? (lowLatency ? 5 : 12) : 30
    options.isSecondOpen = true
    options.userAgent = "Rally-tvOS/1.0"
    options.isAccurateSeek = false
    options.cache = true
    options.formatContextOptions["rw_timeout"] = "15000000"
    options.formatContextOptions["reconnect"] = "1"
    options.formatContextOptions["reconnect_streamed"] = "1"
    options.formatContextOptions["reconnect_delay_max"] = "3"
    if let position, position > 0 { options.startPlayTime = position }
    if muted { options.startPlayRate = 1 }

    // KSAVPlayer is an AVPlayer wrapper. Select KSPlayer's FFmpeg/Metal engine
    // for Rally's 4K/HDR route so these sources use the alternate decoder path.
    KSOptions.firstPlayerType = KSMEPlayer.self
    let playerLayer = KSPlayerLayer(url: url, isAutoPlay: playing, options: options, delegate: self)
    self.layer = playerLayer
    playerLayer.player.isMuted = muted
    if !playing { playerLayer.pause() }
    publish(playerLayer.state)
  }

  func pause(_ paused: Bool) {
    if paused { layer?.pause() } else { layer?.play() }
    publish(layer?.state ?? .initialized)
  }

  func stop() {
    guard let layer else { callback = nil; return }
    self.layer = nil
    layer.delegate = nil
    layer.stop()
    callback = nil
  }

  func seek(_ position: Double) {
    guard position.isFinite, let layer else { return }
    layer.seek(time: position, autoPlay: layer.state.isPlaying) { [weak self] _ in
      guard let self else { return }
      self.publish(self.layer?.state ?? .initialized)
    }
  }

  func audio(_ id: Int?) {
    guard let layer else { return }
    let track = layer.player.tracks(mediaType: .audio).first { Int($0.trackID) == id }
    if let track { layer.player.select(track: track) }
  }

  func caption(_ id: Int?) {
    guard let layer else { return }
    let tracks = layer.player.tracks(mediaType: .subtitle)
    guard let id else {
      tracks.forEach { $0.isEnabled = false }
      return
    }
    if let track = tracks.first(where: { Int($0.trackID) == id }) { layer.player.select(track: track) }
  }

  func player(layer: KSPlayerLayer, state: KSPlayerState) { publish(state) }
  func player(layer: KSPlayerLayer, currentTime: TimeInterval, totalTime: TimeInterval) {
    snapshot.position = currentTime.isFinite ? currentTime : 0
    snapshot.duration = totalTime.isFinite ? totalTime : 0
    snapshot.cacheEnd = max(snapshot.position, layer.player.playableTime)
    publish(layer.state)
  }
  func player(layer: KSPlayerLayer, finish error: Error?) {
    if error != nil { snapshot.failure = -1; publish(.error) }
    else { publish(.playedToTheEnd) }
  }
  func player(layer: KSPlayerLayer, bufferedCount: Int, consumeTime: TimeInterval) {
    snapshot.cacheEnd = max(snapshot.position, layer.player.playableTime)
    publish(layer.state)
  }

  private func publish(_ state: KSPlayerState) {
    guard let layer else { callback?(snapshot); return }
    snapshot.ready = state == .readyToPlay || state == .bufferFinished || state == .paused
    snapshot.buffering = state == .preparing || state == .buffering
    snapshot.paused = state == .paused
    snapshot.ended = state == .playedToTheEnd
    snapshot.seekable = layer.player.seekable
    snapshot.position = layer.player.currentPlaybackTime.isFinite ? layer.player.currentPlaybackTime : snapshot.position
    snapshot.duration = layer.player.duration.isFinite ? layer.player.duration : 0
    snapshot.cacheEnd = max(snapshot.position, layer.player.playableTime)
    snapshot.inputBitrate = Double(layer.player.dynamicInfo?.videoBitrate ?? 0)
    snapshot.fps = layer.player.dynamicInfo?.displayFPS ?? 0
    let video = layer.player.tracks(mediaType: .video).first
    if let description = video?.formatDescription {
      let dimensions = CMVideoFormatDescriptionGetDimensions(description)
      snapshot.width = Double(dimensions.width)
      snapshot.height = Double(dimensions.height)
      snapshot.codec = Self.fourCC(CMFormatDescriptionGetMediaSubType(description))
    }
    snapshot.tracks = ([AVMediaType.audio, .subtitle] as [AVMediaType]).flatMap { type in
      layer.player.tracks(mediaType: type).map {
        Track(
          id: $0.trackID,
          type: type == .audio ? "audio" : "sub",
          title: $0.name,
          lang: $0.languageCode,
          selected: $0.isEnabled
        )
      }
    }
    callback?(snapshot)
  }

  private static func fourCC(_ value: FourCharCode) -> String {
    let bytes = [UInt8((value >> 24) & 0xff), UInt8((value >> 16) & 0xff), UInt8((value >> 8) & 0xff), UInt8(value & 0xff)]
    return String(bytes: bytes, encoding: .macOSRoman)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
  }
}

@MainActor struct KSPlayerSurface: UIViewRepresentable {
  let engine: KSPlaybackEngine

  func makeUIView(context: Context) -> UIView {
    engine.view ?? UIView(frame: .zero)
  }

  func updateUIView(_ view: UIView, context: Context) {
    guard let playerView = engine.view, playerView !== view, playerView.superview !== view else { return }
    playerView.frame = view.bounds
    playerView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    if let current = view.subviews.first { current.removeFromSuperview() }
    view.addSubview(playerView)
  }
}
