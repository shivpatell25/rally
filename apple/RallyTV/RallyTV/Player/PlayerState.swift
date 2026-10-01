import Foundation

/// Player UI states. Mirrors Android `PlayerUiState` shape for the coming Player slice.
public enum PlayerUiState: Sendable, Hashable {
  case loading
  case success(PlayerLoadedState)
  case error(String)
}

public struct PlayerLoadedState: Sendable, Hashable {
  public let event: SportEvent?
  public let streamURL: URL
  public let streamHeaders: [String: String]
  public let relevantChannels: [RelevantChannel]
  public let stremioStreams: [StremioStreamOption]
  public let candidates: [StreamCandidate]
  public let isSwitchingGame: Bool

  public init(
    event: SportEvent? = nil, streamURL: URL, streamHeaders: [String: String] = [:],
    relevantChannels: [RelevantChannel] = [], stremioStreams: [StremioStreamOption] = [],
    candidates: [StreamCandidate] = [], isSwitchingGame: Bool = false
  ) {
    self.event = event
    self.streamURL = streamURL
    self.streamHeaders = streamHeaders
    self.relevantChannels = relevantChannels
    self.stremioStreams = stremioStreams
    self.candidates = candidates
    self.isSwitchingGame = isSwitchingGame
  }
}
