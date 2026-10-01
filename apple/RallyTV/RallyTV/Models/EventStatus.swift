import Foundation

/// Match status for a sport event. Mirrors Android `EventStatus`.
public enum EventStatus: String, Sendable, Hashable, Codable {
  case notStarted = "NOT_STARTED"
  case live = "LIVE"
  case halftime = "HALFTIME"
  case finished = "FINISHED"
  case delayed = "DELAYED"
  case canceled = "CANCELED"

  public var isLive: Bool { self == .live || self == .halftime }
}
