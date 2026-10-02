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

struct GamePlayerCategory: Identifiable {
  let id: String
  let values: [(String, String)]
}
struct GamePlayerBoxScore: Identifiable {
  let id: String
  let player: PlayerStatRow
  let categories: [GamePlayerCategory]
}
struct GameTeamPlayers: Identifiable {
  let id: String
  let name: String
  let logo: URL?
  let players: [GamePlayerBoxScore]
}
extension SportEvent {
  /// Preserve every published participant and category, once per athlete and team.
  var allGamePlayers: [GameTeamPlayers] {
    var order: [String] = []
    var grouped: [String: [PlayerStatTable]] = [:]
    for table in playerStatTables {
      let key = table.teamId ?? (table.teamAbbreviation.isEmpty ? table.teamName : table.teamAbbreviation)
      if grouped[key] == nil { order.append(key) }
      grouped[key, default: []].append(table)
    }
    return order.compactMap { key in
      guard let tables = grouped[key], let first = tables.first else { return nil }
      var playerOrder: [String] = []
      var rows: [String: [(PlayerStatTable, PlayerStatRow)]] = [:]
      for table in tables {
        for row in table.rows where !row.displayName.isEmpty {
          if rows[row.id] == nil { playerOrder.append(row.id) }
          rows[row.id, default: []].append((table, row))
        }
      }
      let players = playerOrder.compactMap { id -> GamePlayerBoxScore? in
        guard let entries = rows[id], let row = entries.first?.1 else { return nil }
        var categoryOrder: [String] = []
        var stats: [String: [(String, String)]] = [:]
        for (table, entry) in entries {
          let name = (table.category ?? "Players").capitalized
          if stats[name] == nil { categoryOrder.append(name) }
          for (index, value) in entry.stats.enumerated() {
            let label = table.labels.indices.contains(index) ? table.labels[index] : "Stat \(index + 1)"
            if !(stats[name] ?? []).contains(where: { $0.0 == label && $0.1 == value }) {
              stats[name, default: []].append((label, value))
            }
          }
        }
        return GamePlayerBoxScore(id: key + ":" + id, player: row,
          categories: categoryOrder.map { GamePlayerCategory(id: $0, values: stats[$0] ?? []) })
      }
      return players.isEmpty ? nil : GameTeamPlayers(id: key, name: first.teamName, logo: first.teamLogoUrl, players: players)
    }
  }
}
