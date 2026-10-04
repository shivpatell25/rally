import Foundation

/// Event→channel matching. Ports `MatchEventToStreamUseCase` scoring.
public protocol MatcherService: Sendable {
  func matchEventToChannels(event: SportEvent, channels: [IptvChannel]) async -> [MatchResult]
  func relevantChannels(for event: SportEvent, channels: [IptvChannel]) async -> [RelevantChannel]
}

public struct MatchEventToStream: MatcherService {
  private let tvStations: @Sendable (String) async -> [String]

  public init(tvStations: @Sendable @escaping (String) async -> [String] = { _ in [] }) {
    self.tvStations = tvStations
  }

  public func matchEventToChannels(event: SportEvent, channels: [IptvChannel]) async
    -> [MatchResult]
  {
    guard !channels.isEmpty else { return [] }
    let stations = await tvStations(event.id).map {
      $0.lowercased().replacingOccurrences(of: " network", with: "").replacingOccurrences(
        of: " channel", with: "")
    }
    let keywords = Self.keywords(for: event)
    var results: [MatchResult] = []
    for channel in channels {
      let lower = channel.name.lowercased()
      let stationMatch = stations.contains { !$0.isEmpty && $0.count > 2 && lower.contains($0) }
      let hits = keywords.filter { lower.contains($0) }.count
      guard hits > 0 || stationMatch else { continue }
      let home = event.homeTeam.map { Self.teamKeywords($0) } ?? []
      let away = event.awayTeam.map { Self.teamKeywords($0) } ?? []
      let homeHit = home.contains { lower.contains($0) }
      let awayHit = away.contains { lower.contains($0) }
      let leagueHit = !event.league.isEmpty && lower.contains(event.league.lowercased())
      // Both-team + station matches score highest; single-team next; league-only last.
      let confidence: Float =
        if stationMatch, homeHit, awayHit {
          0.95
        } else if homeHit, awayHit {
          0.85
        } else if stationMatch {
          0.7
        } else if homeHit || awayHit {
          0.55 + 0.05 * Float(min(hits, 4))
        } else if leagueHit {
          0.3
        } else {
          0.2
        }
      results.append(
        MatchResult(sportEvent: event, iptvChannel: channel, confidenceScore: min(confidence, 1)))
    }
    return results.sorted { $0.confidenceScore > $1.confidenceScore }
  }

  public func relevantChannels(for event: SportEvent, channels: [IptvChannel]) async
    -> [RelevantChannel]
  {
    let matches = await matchEventToChannels(event: event, channels: channels)
    return matches.map { match in
      RelevantChannel(
        channel: match.iptvChannel,
        likelihoodScore: match.confidenceScore,
        matchBadge: match.confidenceScore >= 0.85 ? "Likely" : nil,
        isOfficialBroadcast: match.confidenceScore >= 0.7
      )
    }
  }

  // MARK: -

  private static func keywords(for event: SportEvent) -> [String] {
    var words: [String] = []
    for team in [event.homeTeam, event.awayTeam].compactMap({ $0 }) {
      words += [team.name, team.abbreviation] + team.name.split(separator: " ").map(String.init)
    }
    words += [event.league, event.sport]
    return Array(Set(words.map { $0.lowercased() }.filter { $0.count > 2 }))
  }

  private static func teamKeywords(_ team: Team) -> [String] {
    ([team.name, team.abbreviation] + team.name.split(separator: " ").map(String.init))
      .map { $0.lowercased() }
      .filter { $0.count > 2 }
  }
}
