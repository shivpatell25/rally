#if DEBUG
  import Foundation

  /// Deterministic UI fixtures opt in with --fixtures; excluded from Release builds.
  /// Video is real HLS. Synthetic scores and teams are never a shipping fallback.
  enum TVOSFixtures {
    static let video = URL(
      string:
        "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"
    )!
    @MainActor static func container() -> AppContainer {
      let defaults = UserDefaults(suiteName: "com.shiv.rally.tv.ui-fixtures")!
      defaults.removePersistentDomain(forName: "com.shiv.rally.tv.ui-fixtures")
      let settings = SettingsStore(
        defaults: defaults, secrets: KeychainSecrets(service: "com.shiv.rally.tv.test-secrets"))
      settings.setupComplete = true
      settings.followedTeams = [
        FavoriteTeam(
          teamId: "12", league: "NFL", name: "Kansas City Chiefs", abbreviation: "KC",
          logoUrl: URL(string: "https://a.espncdn.com/i/teamlogos/nfl/500/kc.png"),
          colors: ["E31837"])
      ]
      let repository = FixtureSports(
        noLive: ProcessInfo.processInfo.arguments.contains("--no-live"))
      return AppContainer(
        settings: settings, sportsOverride: repository, stremioOverride: FixtureStreams(),
        iptvOverride: FixtureChannels())
    }
  }
  private struct FixtureSports: SportsRepository {
    let noLive: Bool
    private var all: [SportEvent] {
      let kc = Team(
        id: "12", name: "Kansas City Chiefs", abbreviation: "KC",
        logoUrl: URL(string: "https://a.espncdn.com/i/teamlogos/nfl/500/kc.png"),
        colors: ["E31837"], shortName: "Chiefs"
      )
      let bal = Team(
        id: "33", name: "Baltimore Ravens", abbreviation: "BAL",
        logoUrl: URL(string: "https://a.espncdn.com/i/teamlogos/nfl/500/bal.png"),
        colors: ["241773"], shortName: "Ravens")
      let clips = (0..<4).map {
        HighlightClip(
          id: "clip-\($0)",
          title: ["Mahomes 24-yard pass", "Henry 18-yard run", "Kelce touchdown", "Ravens sack"][
            $0], durationSeconds: 42,
          thumbnailUrl: URL(
            string:
              "https://espnmedia-cdn.akamaized.net/espn/media/common/wsc/2026/0929/12368b95-9d9c-4983-a711-00b503e382c0/12368b95-9d9c-4983-a711-00b503e382c0.jpg"
          ),
          streamUrl: TVOSFixtures.video)
      }
      var tables: [PlayerStatTable] = []
      for team in [kc, bal] {
        for category in ["Passing", "Rushing", "Receiving"] {
          var rows: [PlayerStatRow] = []
          for index in 0..<5 {
            let fullName =
              index == 0
              ? (team.id == "12" ? "Patrick Mahomes" : "Lamar Jackson") : "Player \(index + 1)"
            let shortName =
              index == 0 ? (team.id == "12" ? "P. Mahomes" : "L. Jackson") : "Player \(index + 1)"
            rows.append(
              PlayerStatRow(
                athleteId: "\(team.id)-\(category)-\(index)", displayName: fullName,
                shortName: shortName,
                headshotUrl: URL(
                  string: "https://a.espncdn.com/i/headshots/nfl/players/full/3139477.png"),
                jersey: "15", position: "QB", stats: ["24", "32", "284", "2"]))
          }
          tables.append(
            PlayerStatTable(
              teamId: team.id, teamName: team.name, teamAbbreviation: team.abbreviation,
              teamLogoUrl: team.logoUrl, category: category, labels: ["CMP", "ATT", "YDS", "TD"],
              rows: rows))
        }
      }
      var leaders: [PlayerLeader] = []
      let categories = ["Passing", "Receiving", "Rushing"]
      let statValues = ["284 YDS", "7 REC · 92 YDS", "18 ATT · 78 YDS"]
      let positions = ["QB", "TE", "RB"]
      for team in [kc, bal] {
        for index in 0..<3 {
          let name =
            index == 0 ? (team.id == "12" ? "P. Mahomes" : "L. Jackson") : "Player \(index + 1)"
          let leader = PlayerLeader(
            category: categories[index], teamLogoUrl: team.logoUrl,
            teamAbbreviation: team.abbreviation, playerShortName: name,
            statDisplay: statValues[index],
            position: positions[index],
            headshotUrl: URL(
              string: "https://a.espncdn.com/i/headshots/nfl/players/full/3139477.png"))
          leaders.append(leader)
        }
      }
      let plays: [GamePlay] = (0..<15).map { i in
        GamePlay(
          id: "play-\(i)", sequence: 15 - i,
          text: "Mahomes rolls right and completes a pass to Rice for 24 yards.", awayScore: 24,
          homeScore: 20, period: 4, clock: "3:42", isScoringPlay: i == 3)
      }
      let comparisons: [TeamStatComparison] = [
        TeamStatComparison(label: "Total Yards", awayValue: "382", homeValue: "355"),
        TeamStatComparison(label: "First Downs", awayValue: "24", homeValue: "28"),
        TeamStatComparison(label: "3rd Down", awayValue: "6 / 10", homeValue: "5 / 11"),
        TeamStatComparison(label: "Red Zone", awayValue: "2 / 2", homeValue: "3 / 4"),
        TeamStatComparison(label: "Turnovers", awayValue: "1", homeValue: "2"),
      ]
      return (0..<10).map { (i: Int) -> SportEvent in
        let status: EventStatus = i < 6 ? (noLive ? .finished : .live) : .notStarted
        let start = Date().addingTimeInterval(Double(i < 6 ? -1000 : (i - 5) * 1800))
        return
          SportEvent(
            id: "NFL:qa\(i+1)", name: "Chiefs vs Ravens", homeTeam: bal, awayTeam: kc,
            startTime: start,
            status: status, scoreHome: i < 6 ? 20 : nil,
            scoreAway: i < 6 ? 24 : nil, sport: "football", league: "NFL",
            venue: "M&T Bank Stadium",
            liveStats: [
              "KC Record": "11–6", "BAL Record": "13–4", "TV Broadcast": "CBS",
              "Current Drive": "KC · 7 plays · 72 yards · 3:28",
            ], gameStatusDetail: i < 6 ? "4th · 3:42" : nil,
            teamStats: comparisons, playerLeaders: leaders, highlightClips: clips,
            winProbability: [WinProbabilityPoint(homeWinPercentage: 0.42, sequence: 0)],
            playerStatTables: tables,
            plays: plays)
      }
    }
    func liveEvents() async throws -> [SportEvent] { all.filter(\.status.isLive) }
    func upcomingEvents() async throws -> [SportEvent] { all.filter { $0.status == .notStarted } }
    func eventsSnapshot() async throws -> [SportEvent] { all }
    func recentEvents() async throws -> [SportEvent] { all }
    func events(forLeague league: String) async throws -> [SportEvent] {
      league == "NFL" ? all : []
    }
    func event(id: String) async throws -> SportEvent? { all.first { $0.id == id } }
    func searchEvents(query: String) async throws -> [SportEvent] {
      all.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }
    func tvStations(forEventId eventId: String) async throws -> [String] { ["CBS"] }
    func eventSummary(_ event: SportEvent) async throws -> SportEvent { event }
    func recentHighlights() async throws -> [HighlightItem] {
      all.first!.highlightClips.map { HighlightItem(clip: $0, event: all.first, league: "NFL") }
    }
    func teams(league: String) async throws -> [FavoriteTeam] {
      guard league == "NFL" else { return [] }
      return [all[0].awayTeam!, all[0].homeTeam!].map {
        FavoriteTeam(
          teamId: $0.id, league: league, name: $0.name, abbreviation: $0.abbreviation,
          logoUrl: $0.logoUrl, colors: $0.colors)
      }
    }
    func teamHub(league: String, teamId: String) async throws -> TeamHub? {
      guard let team = try await teams(league: league).first(where: { $0.teamId == teamId }) else {
        return nil
      }
      return TeamHub(
        team: team, standing: TeamStanding(summary: "11–6"), schedule: all,
        roster: (0..<12).map {
          TeamPlayer(id: "p\($0)", name: "Player \($0+1)", position: "QB", jersey: "\($0+1)")
        },
        injuries: [
          TeamInjury(
            playerName: "Player 1", status: "Questionable", detail: "Limited practice participation"
          )
        ])
    }
    func leagueHub(league: String) async throws -> LeagueHub {
      LeagueHub(
        league: league, events: try await events(forLeague: league),
        standings: [("Kansas City Chiefs", "AFC · 11–6"), ("Baltimore Ravens", "AFC · 13–4")],
        playoffPicture: [
          ("Baltimore Ravens", "AFC · Seed 1"), ("Kansas City Chiefs", "AFC · Seed 3"),
        ])
    }
  }
  private struct FixtureStreams: StremioRepository {
    func streams(for event: SportEvent) async -> [StremioStreamOption] {
      [
        StremioStreamOption(
          title: "\(event.name) · HLS", streamUrl: TVOSFixtures.video, quality: "1080p",
          addonName: "UI Test", headers: ["User-Agent": "Rally-tvOS-QA"]),
        StremioStreamOption(
          title: "\(event.name) · Alternate",
          streamUrl: TVOSFixtures.video.appending(queryItems: [URLQueryItem(name: "qa", value: "2")]
          ), quality: "720p", addonName: "UI Test"),
      ]
    }
    func searchStreams(query: String) async -> [StremioStreamOption] { [] }
  }
  private struct FixtureChannels: IptvRepository {
    func authenticate() async -> Bool { true }
    func channels() async throws -> [IptvChannel] {
      (0..<5).map {
        IptvChannel(
          id: "qa:\($0)", number: "\($0+1)", name: $0 == 4 ? "NFL RedZone" : "Game Channel \($0+1)",
          category: "Sports", streamUrl: TVOSFixtures.video)
      }
    }
    func refreshChannels() async throws -> [IptvChannel] { try await channels() }
    func streamUrl(forChannelId channelId: String) async throws -> URL { TVOSFixtures.video }
    func guide(forChannelId channelId: String) async -> ChannelGuide? {
      ChannelGuide(
        now: EpgProgram(title: "Live game"), next: EpgProgram(title: "Sports highlights"))
    }
  }
#endif
