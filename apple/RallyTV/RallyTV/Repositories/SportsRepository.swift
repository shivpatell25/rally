import Foundation

/// Read-only sports schedule + metadata. Mirrors Android `SportsRepository`.
public protocol SportsRepository: Sendable {
  func liveEvents() async throws -> [SportEvent]
  func upcomingEvents() async throws -> [SportEvent]
  func eventsSnapshot() async throws -> [SportEvent]
  func recentEvents() async throws -> [SportEvent]
  func events(forLeague league: String) async throws -> [SportEvent]
  func event(id: String) async throws -> SportEvent?
  func searchEvents(query: String) async throws -> [SportEvent]
  func tvStations(forEventId eventId: String) async throws -> [String]
  func eventSummary(_ event: SportEvent) async throws -> SportEvent
  func events(forDate date: Date, league: String) async throws -> [SportEvent]
  func teams(league: String) async throws -> [FavoriteTeam]
  func teamHub(league: String, teamId: String) async throws -> TeamHub?
  func leagueHub(league: String) async throws -> LeagueHub
  func recentHighlights() async throws -> [HighlightItem]
  func refresh() async
}

public struct HighlightItem: Sendable, Hashable, Identifiable {
  public var id: String { clip.id }
  public let clip: HighlightClip
  public let event: SportEvent?
  public let league: String
  public init(clip: HighlightClip, event: SportEvent? = nil, league: String) {
    self.clip = clip
    self.event = event
    self.league = league
  }
}

extension SportsRepository {
  public func events(forDate date: Date, league: String) async throws -> [SportEvent] {
    try await events(forLeague: league).filter {
      Calendar.current.isDate($0.startTime, inSameDayAs: date)
    }
  }
  public func teams(league: String) async throws -> [FavoriteTeam] { [] }
  public func teamHub(league: String, teamId: String) async throws -> TeamHub? { nil }
  public func leagueHub(league: String) async throws -> LeagueHub {
    LeagueHub(league: league, events: try await events(forLeague: league))
  }
  public func recentHighlights() async throws -> [HighlightItem] {
    var result: [HighlightItem] = []
    for event in try await recentEvents().filter({ $0.status == .finished }).suffix(8).reversed() {
      let detailed = try await eventSummary(event)
      result += detailed.highlightClips.map {
        HighlightItem(clip: $0, event: detailed, league: detailed.league)
      }
    }
    return result
  }
  public func refresh() async {}
}
