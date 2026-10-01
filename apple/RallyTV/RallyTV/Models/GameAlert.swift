import Foundation

public enum GameAlertType: String, Sendable, Hashable, Codable {
  case kickoff
  case score
  case closeGame
  case overtime
  case final
  case redZone
}

public struct GameAlert: Sendable, Hashable, Codable, Identifiable {
  public let id: String
  public let eventId: String?
  public let type: GameAlertType
  public let title: String
  public let message: String

  public init(id: String, eventId: String?, type: GameAlertType, title: String, message: String) {
    self.id = id
    self.eventId = eventId
    self.type = type
    self.title = title
    self.message = message
  }
}
