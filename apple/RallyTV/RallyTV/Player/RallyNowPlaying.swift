import AVFoundation
import MediaPlayer

/// Native Now Playing / Siri transport integration for the foreground single player.
@MainActor final class RallyNowPlaying {
  private weak var session: PlaybackSession?
  private var handlers: [(MPRemoteCommand, Any)] = []
  private var task: Task<Void, Never>?
  init(session: PlaybackSession) {
    self.session = session
    let center = MPRemoteCommandCenter.shared()
    register(center.playCommand) { $0.remoteTransport(.play) }
    register(center.pauseCommand) { $0.remoteTransport(.pause) }
    register(center.togglePlayPauseCommand) { $0.remoteTransport(.toggle) }
    center.skipForwardCommand.preferredIntervals = [10]
    center.skipBackwardCommand.preferredIntervals = [10]
    register(center.skipForwardCommand) { $0.seek(10) }
    register(center.skipBackwardCommand) { $0.seek(-10) }
    let target = center.changePlaybackPositionCommand.addTarget { [weak session] event in
      guard let event = event as? MPChangePlaybackPositionCommandEvent else {
        return .commandFailed
      }
      Task { @MainActor in
        guard let session else { return }
        session.seek(event.positionTime - session.elapsed)
      }
      return .success
    }
    handlers.append((center.changePlaybackPositionCommand, target))
    task = Task { [weak self] in
      while !Task.isCancelled {
        guard let self, let session = self.session else { return }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
          MPMediaItemPropertyTitle: session.sourceTitle,
          MPNowPlayingInfoPropertyElapsedPlaybackTime: session.elapsed,
          MPNowPlayingInfoPropertyPlaybackRate: session.playing ? 1 : 0,
          MPNowPlayingInfoPropertyIsLiveStream: session.isLive,
          MPMediaItemPropertyPlaybackDuration: session.duration.isFinite ? session.duration : 0,
        ]
        do { try await Task.sleep(for: .seconds(1)) } catch { return }
      }
    }
  }
  private func register(
    _ command: MPRemoteCommand, action: @escaping @MainActor (PlaybackSession) -> Void
  ) {
    command.isEnabled = true
    let target = command.addTarget { [weak session] _ in
      Task { @MainActor in if let session { action(session) } }
      return .success
    }
    handlers.append((command, target))
  }
  func stop() {
    task?.cancel()
    task = nil
    for (command, target) in handlers { command.removeTarget(target) }
    handlers.removeAll()
    MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
  }
}
