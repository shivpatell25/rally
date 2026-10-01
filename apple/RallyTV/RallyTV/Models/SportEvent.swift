import Foundation

/// Core event aggregate. Mirrors Android `SportEvent`.
public struct SportEvent: Sendable, Hashable, Codable, Identifiable {
  public let id: String
  public let name: String
  public let homeTeam: Team?
  public let awayTeam: Team?
  public let startTime: Date
  public let status: EventStatus
  public let scoreHome: Int?
  public let scoreAway: Int?
  public let sport: String
  public let league: String
  public let bannerUrl: URL?
  public let homeTeamBadge: URL?
  public let awayTeamBadge: URL?
  public let venue: String?
  public let venueImageUrl: URL?
  public let eventContextTitle: String?
  public let liveStats: [String: String]
  public let gameStatusDetail: String?
  public let teamStats: [TeamStatComparison]
  public let playerLeaders: [PlayerLeader]
  public let highlightClips: [HighlightClip]
  public let winProbability: [WinProbabilityPoint]
  public let playerStatTables: [PlayerStatTable]
  public let plays: [GamePlay]

  public init(
    id: String,
    name: String,
    homeTeam: Team? = nil,
    awayTeam: Team? = nil,
    startTime: Date,
    status: EventStatus,
    scoreHome: Int? = nil,
    scoreAway: Int? = nil,
    sport: String,
    league: String,
    bannerUrl: URL? = nil,
    homeTeamBadge: URL? = nil,
    awayTeamBadge: URL? = nil,
    venue: String? = nil,
    venueImageUrl: URL? = nil,
    eventContextTitle: String? = nil,
    liveStats: [String: String] = [:],
    gameStatusDetail: String? = nil,
    teamStats: [TeamStatComparison] = [],
    playerLeaders: [PlayerLeader] = [],
    highlightClips: [HighlightClip] = [],
    winProbability: [WinProbabilityPoint] = [],
    playerStatTables: [PlayerStatTable] = [],
    plays: [GamePlay] = []
  ) {
    self.id = id
    self.name = name
    self.homeTeam = homeTeam
    self.awayTeam = awayTeam
    self.startTime = startTime
    self.status = status
    self.scoreHome = scoreHome
    self.scoreAway = scoreAway
    self.sport = sport
    self.league = league
    self.bannerUrl = bannerUrl
    self.homeTeamBadge = homeTeamBadge
    self.awayTeamBadge = awayTeamBadge
    self.venue = venue
    self.venueImageUrl = venueImageUrl
    self.eventContextTitle = eventContextTitle
    self.liveStats = liveStats
    self.gameStatusDetail = gameStatusDetail
    self.teamStats = teamStats
    self.playerLeaders = playerLeaders
    self.highlightClips = highlightClips
    self.winProbability = winProbability
    self.playerStatTables = playerStatTables
    self.plays = plays
  }
}
