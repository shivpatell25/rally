import Foundation

/// Followed team profile. Mirrors Android `FavoriteTeam`.
public struct FavoriteTeam: Sendable, Hashable, Codable, Identifiable {
  public var id: String { key }
  public let teamId: String
  public let league: String
  public let name: String
  public let abbreviation: String
  public let logoUrl: URL?
  public let colors: [String]

  public init(
    teamId: String, league: String, name: String, abbreviation: String, logoUrl: URL? = nil,
    colors: [String] = []
  ) {
    self.teamId = teamId
    self.league = league
    self.name = name
    self.abbreviation = abbreviation
    self.logoUrl = logoUrl
    self.colors = colors
  }

  public var key: String { "\(league):\(teamId)" }
}

public struct TeamStanding: Sendable, Hashable, Codable {
  public let summary: String
  public let rank: Int?
  public let wins: Int?
  public let losses: Int?
  public let ties: Int?

  public init(
    summary: String, rank: Int? = nil, wins: Int? = nil, losses: Int? = nil, ties: Int? = nil
  ) {
    self.summary = summary
    self.rank = rank
    self.wins = wins
    self.losses = losses
    self.ties = ties
  }
}

public struct TeamPlayer: Sendable, Hashable, Codable, Identifiable {
  public let id: String
  public let name: String
  public let position: String?
  public let jersey: String?
  public let headshotUrl: URL?

  public init(
    id: String, name: String, position: String? = nil, jersey: String? = nil,
    headshotUrl: URL? = nil
  ) {
    self.id = id
    self.name = name
    self.position = position
    self.jersey = jersey
    self.headshotUrl = headshotUrl
  }
}

public struct TeamInjury: Sendable, Hashable, Codable {
  public let playerName: String
  public let status: String
  public let detail: String?

  public init(playerName: String, status: String, detail: String? = nil) {
    self.playerName = playerName
    self.status = status
    self.detail = detail
  }
}

/// Team detail aggregate. Mirrors Android `TeamHub`.
public struct TeamHub: Sendable, Hashable, Codable {
  public let team: FavoriteTeam
  public let standing: TeamStanding?
  public let schedule: [SportEvent]
  public let roster: [TeamPlayer]
  public let injuries: [TeamInjury]

  public init(
    team: FavoriteTeam, standing: TeamStanding? = nil, schedule: [SportEvent] = [],
    roster: [TeamPlayer] = [], injuries: [TeamInjury] = []
  ) {
    self.team = team
    self.standing = standing
    self.schedule = schedule
    self.roster = roster
    self.injuries = injuries
  }
}

/// League detail aggregate. Mirrors Android `LeagueHub`.
/// Tuple standings block Codable/Hashable synthesis, so this aggregate is
/// Sendable + Equatable only — persistence happens at the `SportEvent` level.
public struct LeagueHub: Sendable, Equatable {
  public let league: String
  public let events: [SportEvent]
  public let standings: [(String, String)]
  public let postseasonEvents: [SportEvent]
  public let playoffPicture: [(String, String)]

  public init(
    league: String, events: [SportEvent] = [],
    standings: [(String, String)] = [],
    postseasonEvents: [SportEvent] = [],
    playoffPicture: [(String, String)] = []
  ) {
    self.league = league
    self.events = events
    self.standings = standings
    self.postseasonEvents = postseasonEvents
    self.playoffPicture = playoffPicture
  }

  public static func == (lhs: LeagueHub, rhs: LeagueHub) -> Bool {
    lhs.league == rhs.league && lhs.events == rhs.events
      && lhs.postseasonEvents == rhs.postseasonEvents
      && lhs.standings.elementsEqual(rhs.standings, by: { $0.0 == $1.0 && $0.1 == $1.1 })
      && lhs.playoffPicture.elementsEqual(rhs.playoffPicture, by: { $0.0 == $1.0 && $0.1 == $1.1 })
  }
}
