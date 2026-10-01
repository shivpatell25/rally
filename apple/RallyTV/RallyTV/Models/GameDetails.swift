import Foundation

/// Play-by-play entry. Mirrors Android `GamePlay`.
public struct GamePlay: Sendable, Hashable, Codable, Identifiable {
  public let id: String
  public let sequence: Int
  public let text: String
  public let awayScore: Int?
  public let homeScore: Int?
  public let period: Int?
  public let clock: String?
  public let isScoringPlay: Bool

  public init(
    id: String, sequence: Int, text: String,
    awayScore: Int? = nil, homeScore: Int? = nil,
    period: Int? = nil, clock: String? = nil, isScoringPlay: Bool = false
  ) {
    self.id = id
    self.sequence = sequence
    self.text = text
    self.awayScore = awayScore
    self.homeScore = homeScore
    self.period = period
    self.clock = clock
    self.isScoringPlay = isScoringPlay
  }
}

/// Highlight video reference. Mirrors Android `HighlightClip`.
public struct HighlightClip: Sendable, Hashable, Codable, Identifiable {
  public let id: String
  public let title: String
  public let description: String?
  public let durationSeconds: Int?
  public let thumbnailUrl: URL?
  public let streamUrl: URL?
  public let webUrl: URL?

  public init(
    id: String, title: String, description: String? = nil,
    durationSeconds: Int? = nil, thumbnailUrl: URL? = nil,
    streamUrl: URL? = nil, webUrl: URL? = nil
  ) {
    self.id = id
    self.title = title
    self.description = description
    self.durationSeconds = durationSeconds
    self.thumbnailUrl = thumbnailUrl
    self.streamUrl = streamUrl
    self.webUrl = webUrl
  }
}

/// Win-probability sample. Mirrors Android `WinProbabilityPoint`.
public struct WinProbabilityPoint: Sendable, Hashable, Codable {
  public let playId: String?
  public let homeWinPercentage: Double
  public let tiePercentage: Double
  public let period: Int?
  public let clock: String?
  public let sequence: Int

  public init(
    playId: String? = nil, homeWinPercentage: Double, tiePercentage: Double = 0,
    period: Int? = nil, clock: String? = nil, sequence: Int
  ) {
    self.playId = playId
    self.homeWinPercentage = homeWinPercentage
    self.tiePercentage = tiePercentage
    self.period = period
    self.clock = clock
    self.sequence = sequence
  }
}

/// Per-team player stat table. Mirrors Android `PlayerStatTable`.
public struct PlayerStatTable: Sendable, Hashable, Codable {
  public let teamId: String?
  public let teamName: String
  public let teamAbbreviation: String
  public let teamLogoUrl: URL?
  public let category: String?
  public let labels: [String]
  public let rows: [PlayerStatRow]

  public init(
    teamId: String? = nil, teamName: String, teamAbbreviation: String,
    teamLogoUrl: URL? = nil, category: String? = nil,
    labels: [String] = [], rows: [PlayerStatRow] = []
  ) {
    self.teamId = teamId
    self.teamName = teamName
    self.teamAbbreviation = teamAbbreviation
    self.teamLogoUrl = teamLogoUrl
    self.category = category
    self.labels = labels
    self.rows = rows
  }
}

public struct PlayerStatRow: Sendable, Hashable, Codable, Identifiable {
  public var id: String { athleteId ?? displayName }
  public let athleteId: String?
  public let displayName: String
  public let shortName: String?
  public let headshotUrl: URL?
  public let jersey: String?
  public let position: String?
  public let stats: [String]

  public init(
    athleteId: String? = nil, displayName: String, shortName: String? = nil,
    headshotUrl: URL? = nil, jersey: String? = nil, position: String? = nil,
    stats: [String] = []
  ) {
    self.athleteId = athleteId
    self.displayName = displayName
    self.shortName = shortName
    self.headshotUrl = headshotUrl
    self.jersey = jersey
    self.position = position
    self.stats = stats
  }
}

/// Home/away comparison row. Mirrors Android `TeamStatComparison`.
public struct TeamStatComparison: Sendable, Hashable, Codable {
  public let label: String
  public let awayValue: String
  public let homeValue: String

  public init(label: String, awayValue: String, homeValue: String) {
    self.label = label
    self.awayValue = awayValue
    self.homeValue = homeValue
  }
}

/// Category leader. Mirrors Android `PlayerLeader`.
public struct PlayerLeader: Sendable, Hashable, Codable {
  public let category: String
  public let teamLogoUrl: URL?
  public let teamAbbreviation: String?
  public let playerShortName: String
  public let statDisplay: String
  public let position: String?
  public let headshotUrl: URL?

  public init(
    category: String, teamLogoUrl: URL? = nil, teamAbbreviation: String? = nil,
    playerShortName: String, statDisplay: String,
    position: String? = nil, headshotUrl: URL? = nil
  ) {
    self.category = category
    self.teamLogoUrl = teamLogoUrl
    self.teamAbbreviation = teamAbbreviation
    self.playerShortName = playerShortName
    self.statDisplay = statDisplay
    self.position = position
    self.headshotUrl = headshotUrl
  }
}
