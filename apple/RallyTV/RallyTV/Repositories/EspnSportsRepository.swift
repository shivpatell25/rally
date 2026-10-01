import Foundation

public actor EspnSportsRepository: SportsRepository {
  private let http: HTTPClient
  private let diskCache: ScheduleDiskCache
  private let settings: SettingsStore
  private var snapshot: [SportEvent] = []
  private var cache: [String: SportEvent] = [:]
  private var fetched: Date = .distantPast
  private var fetchTask: Task<[SportEvent], Error>?
  private var directory: [String: ([FavoriteTeam], Date)] = [:]
  private var summaries: [String: (SportEvent, Date)] = [:]
  private var clips: ([HighlightItem], Date)?
  public init(
    http: HTTPClient = HTTPClient(), diskCache: ScheduleDiskCache = ScheduleDiskCache(),
    settings: SettingsStore = SettingsStore()
  ) {
    self.http = http
    self.diskCache = diskCache
    self.settings = settings
  }
  public func refresh() async {
    fetched = .distantPast
    clips = nil
  }
  public func liveEvents() async throws -> [SportEvent] {
    try await fetch().filter(\.status.isLive)
  }
  public func upcomingEvents() async throws -> [SportEvent] {
    try await fetch().filter {
      $0.status == .notStarted && $0.startTime > Date().addingTimeInterval(-3600)
    }
  }
  public func eventsSnapshot() async throws -> [SportEvent] {
    try await fetch().filter {
      $0.status.isLive
        || ($0.status == .notStarted && $0.startTime > Date().addingTimeInterval(-3600))
    }
  }
  public func recentEvents() async throws -> [SportEvent] { try await fetch() }
  public func events(forLeague league: String) async throws -> [SportEvent] {
    try await fetch().filter { league == "Soccer" ? $0.sport == "soccer" : $0.league == league }
  }
  public func event(id: String) async throws -> SportEvent? {
    if let cached = cache[id] { return cached }
    if let event = try await fetch().first(where: { $0.id == id }) { return event }
    let parts = id.split(separator: ":", maxSplits: 1).map(String.init)
    guard parts.count == 2, let path = EspnEndpoints.path(forLeague: parts[0]),
      let url = EspnEndpoints.summary(sport: path.sport, league: path.league, eventId: parts[1])
    else { return nil }
    let s: JSONValue = try await http.json(url: url)
    var object: [String: JSONValue] = [
      "id": .string(parts[1]), "name": s["header"]["name"],
      "date": s["header"]["competitions"].first["date"],
      "competitions": s["header"]["competitions"],
    ]
    if object["date"] == .null {
      object["date"] = .string(ISO8601DateFormatter().string(from: Date()))
    }
    guard let base = ESPNWire.event(.object(object), league: parts[0], sport: path.sport) else {
      return nil
    }
    let enriched = ESPNWire.enrich(base, summary: s)
    cache[id] = enriched
    return enriched
  }
  public func searchEvents(query: String) async throws -> [SportEvent] {
    try await fetch().filter {
      ($0.name + " " + ($0.homeTeam?.name ?? "") + " " + ($0.awayTeam?.name ?? ""))
        .localizedCaseInsensitiveContains(query)
    }
  }
  public func tvStations(forEventId id: String) async throws -> [String] {
    try await event(id: id)?.liveStats["TV Broadcast"]?.components(separatedBy: ", ") ?? []
  }
  public func eventSummary(_ event: SportEvent) async throws -> SportEvent {
    if let value = summaries[event.id],
      Date().timeIntervalSince(value.1) < (event.status.isLive ? 25 : 900)
    {
      return value.0
    }
    guard let path = EspnEndpoints.path(forLeague: event.league),
      let url = EspnEndpoints.summary(
        sport: path.sport, league: path.league,
        eventId: event.id.split(separator: ":").last.map(String.init) ?? event.id)
    else { return event }
    let s: JSONValue = try await http.json(url: url)
    let enriched = ESPNWire.enrich(event, summary: s)
    summaries[event.id] = (enriched, Date())
    cache[event.id] = enriched
    return enriched
  }
  public func events(forDate date: Date, league: String) async throws -> [SportEvent] {
    guard let path = EspnEndpoints.path(forLeague: league),
      let url = EspnEndpoints.scoreboard(
        sport: path.sport, league: path.league, dates: Self.day(date), limit: 1000)
    else { return [] }
    let response: JSONValue = try await http.json(url: url)
    let events = response["events"].array.compactMap {
      ESPNWire.event($0, league: league, sport: path.sport)
    }
    for event in events { cache[event.id] = event }
    return events.sorted { $0.startTime < $1.startTime }
  }
  public func teams(league: String) async throws -> [FavoriteTeam] {
    if let value = directory[league], Date().timeIntervalSince(value.1) < 86400 { return value.0 }
    guard let url = endpoint(league, "teams", query: [URLQueryItem(name: "limit", value: "1000")])
    else { return [] }
    let s: JSONValue = try await http.json(url: url)
    let entries = s["sports"].array.flatMap { $0["leagues"].array }.flatMap { $0["teams"].array }
    let teams = entries.compactMap { ESPNWire.team($0["team"]) }.map {
      FavoriteTeam(
        teamId: $0.id, league: league, name: $0.name, abbreviation: $0.abbreviation,
        logoUrl: $0.logoUrl, colors: $0.colors)
    }.sorted { $0.name < $1.name }
    directory[league] = (teams, Date())
    return teams
  }
  public func teamHub(league: String, teamId: String) async throws -> TeamHub? {
    guard let url = endpoint(league, "teams/\(teamId)") else { return nil }
    let s: JSONValue = try await http.json(url: url)
    guard let team = ESPNWire.team(s["team"]) else { return nil }
    let favorite = FavoriteTeam(
      teamId: team.id, league: league, name: team.name, abbreviation: team.abbreviation,
      logoUrl: team.logoUrl, colors: team.colors)
    let record =
      s["team"]["record"]["items"].array.first { $0["type"].text == "total" }
      ?? s["team"]["record"]["items"].first
    async let schedule = fetchJSON(endpoint(league, "teams/\(teamId)/schedule"))
    async let roster = fetchJSON(endpoint(league, "teams/\(teamId)/roster"))
    async let injuriesFeed = fetchJSON(endpoint(league, "teams/\(teamId)/injuries"))
    let (scheduleJSON, rosterJSON, injuryJSON) = await (schedule, roster, injuriesFeed)
    let path = EspnEndpoints.path(forLeague: league)!
    let events = scheduleJSON["events"].array.compactMap {
      ESPNWire.event($0, league: league, sport: path.sport)
    }.sorted { $0.startTime < $1.startTime }
    events.forEach { cache[$0.id] = $0 }
    let athletes = rosterJSON["athletes"].array.flatMap {
      $0["items"].array.isEmpty ? [$0] : $0["items"].array
    }
    let players = athletes.compactMap { a -> TeamPlayer? in
      guard !a["id"].text.isEmpty else { return nil }
      return TeamPlayer(
        id: a["id"].text, name: a["displayName"].text,
        position: a["position"]["abbreviation"].string, jersey: a["jersey"].string,
        headshotUrl: a["headshot"]["href"].url)
    }
    let injuries = injuryJSON.objects {
      $0["athlete"] != .null && ($0["status"] != .null || $0["type"] != .null)
    }.map {
      TeamInjury(
        playerName: $0["athlete"]["displayName"].string ?? $0["athlete"]["fullName"].text,
        status: $0["status"].string ?? $0["type"]["description"].text,
        detail: $0["details"].string ?? $0["detail"].string ?? $0["description"].string)
    }
    return TeamHub(
      team: favorite,
      standing: record == .null ? nil : TeamStanding(summary: record["summary"].text),
      schedule: events, roster: players, injuries: injuries)
  }
  public func leagueHub(league: String) async throws -> LeagueHub {
    let events = try await events(forLeague: league)
    guard let path = EspnEndpoints.path(forLeague: league),
      let url = URL(
        string: "https://site.api.espn.com/apis/v2/sports/\(path.sport)/\(path.league)/standings")
    else { return LeagueHub(league: league, events: events) }
    let standings = await fetchJSON(url)
    var rows: [(String, String)] = []
    var seeds: [(String, String)] = []
    func walk(_ node: JSONValue, group: String) {
      let title = node["name"].string ?? group
      for entry in node["standings"]["entries"].array {
        let stats = entry["stats"].array
        func value(_ names: [String]) -> String {
          stats.first { names.contains($0["name"].text) }?["displayValue"].string ?? ""
        }
        let wins = value(["wins"])
        let losses = value(["losses"])
        let ties = value(["ties"])
        let record = [wins, losses, ties == "0" ? "" : ties].filter { !$0.isEmpty }.joined(
          separator: "–")
        let team = entry["team"]["displayName"].text
        rows.append((team, "\(title) · \(record)"))
        let seed = value(["playoffSeed", "seed"])
        if !seed.isEmpty { seeds.append((team, "\(title) · Seed \(seed) · \(record)")) }
      }
      for child in node["children"].array { walk(child, group: title) }
    }
    walk(standings, group: league)
    return LeagueHub(
      league: league, events: events, standings: rows,
      postseasonEvents: events.filter {
        ($0.eventContextTitle ?? "").localizedCaseInsensitiveContains("playoff")
      }, playoffPicture: seeds)
  }
  public func recentHighlights() async throws -> [HighlightItem] {
    if let c = clips, Date().timeIntervalSince(c.1) < 300 { return c.0 }
    let all = try await fetch()
    var completed = all.filter { $0.status == .finished }
    let enabled = settings.enabledLeagues
    let recentLeagues = ["MLB", "NHL", "NBA", "NFL", "NCAAF", "EPL"].filter {
      enabled.isEmpty || enabled.contains($0)
    }
    for days in 1...7 {
      if Set(completed.map(\.id)).count >= 8 { break }
      let date = Calendar.current.date(byAdding: .day, value: -days, to: Date())!
      for start in stride(from: 0, to: recentLeagues.count, by: 2) {
        await withTaskGroup(of: [SportEvent].self) { group in
          for league in recentLeagues[start..<min(start + 2, recentLeagues.count)] {
            group.addTask {
              (try? await self.events(forDate: date, league: league))?.filter {
                $0.status == .finished
              } ?? []
            }
          }
          for await events in group { completed += events }
        }
      }
    }
    var seen = Set<String>()
    let finals = Array(
      completed.filter { seen.insert($0.id).inserted }.sorted { $0.startTime > $1.startTime }
        .prefix(12))
    var result: [HighlightItem] = []
    // Bounded request batches keep playback and remote input responsive.
    for start in stride(from: 0, to: finals.count, by: 3) {
      let slice = Array(finals[start..<min(start + 3, finals.count)])
      await withTaskGroup(of: [HighlightItem].self) { group in
        for event in slice {
          group.addTask {
            let summary = (try? await self.eventSummary(event)) ?? event
            return summary.highlightClips.filter { $0.streamUrl != nil }.map {
              HighlightItem(clip: $0, event: summary, league: event.league)
            }
          }
        }
        for await items in group { result += items }
      }
      if result.count >= 24 { break }
    }
    var ids = Set<String>()
    result = result.filter { ids.insert($0.id).inserted }
    clips = (result, Date())
    return result
  }
  private func fetchJSON(_ url: URL?) async -> JSONValue {
    guard let url else { return .null }
    return (try? await http.json(url: url)) ?? .null
  }
  private func endpoint(_ league: String, _ suffix: String, query: [URLQueryItem] = []) -> URL? {
    guard let path = EspnEndpoints.path(forLeague: league) else { return nil }
    var c = URLComponents(
      url: EspnEndpoints.baseURL.appendingPathComponent(
        "sports/\(path.sport)/\(path.league)/\(suffix)"), resolvingAgainstBaseURL: false)
    c?.queryItems = query.isEmpty ? nil : query
    return c?.url
  }
  private static func day(_ date: Date) -> String {
    let f = DateFormatter()
    f.dateFormat = "yyyyMMdd"
    return f.string(from: date)
  }
  private func fetch() async throws -> [SportEvent] {
    if Date().timeIntervalSince(fetched) < 60 { return snapshot }
    if let task = fetchTask { return try await task.value }
    let enabled = settings.enabledLeagues
    let leagues = EspnEndpoints.leagues.filter { enabled.isEmpty || enabled.contains($0.key) }
    let http = self.http
    let previous = snapshot
    let today = Self.day(Date())
    let f = DateFormatter()
    f.dateFormat = "yyyyMM"
    let months = Array(
      Set([
        f.string(from: Date()),
        f.string(from: Calendar.current.date(byAdding: .day, value: 7, to: Date())!),
      ]))
    let task = Task<[SportEvent], Error> {
      var events: [SportEvent] = []
      var succeeded = 0
      let entries = Array(leagues)
      for start in stride(from: 0, to: entries.count, by: 4) {
        await withTaskGroup(of: [SportEvent]?.self) { group in
          for (league, path) in entries[start..<min(start + 4, entries.count)] {
            group.addTask {
              let date = league == "NFL" || league == "NCAAF" ? nil : today
              guard
                let url = EspnEndpoints.scoreboard(
                  sport: path.sport, league: path.league, dates: date, limit: 200),
                let response: JSONValue = try? await http.json(url: url)
              else {
                return previous.filter { $0.league == league }.isEmpty
                  ? nil : previous.filter { $0.league == league }
              }
              var events = response["events"].array.compactMap {
                ESPNWire.event($0, league: league, sport: path.sport)
              }
              if events.isEmpty && date != nil {
                for month in months {
                  if let url = EspnEndpoints.scoreboard(
                    sport: path.sport, league: path.league, dates: month, limit: 1000),
                    let monthly: JSONValue = try? await http.json(url: url)
                  {
                    events += monthly["events"].array.compactMap {
                      ESPNWire.event($0, league: league, sport: path.sport)
                    }
                  }
                }
              }
              return events
            }
          }
          for await value in group {
            if let value {
              succeeded += 1
              events += value
            }
          }
        }
      }
      guard succeeded > 0 else { throw RallyNetworkError.invalidResponse }
      var seen = Set<String>()
      return events.filter { seen.insert($0.id).inserted }.sorted { $0.startTime < $1.startTime }
    }
    fetchTask = task
    defer { fetchTask = nil }
    do {
      let events = try await task.value
      for event in events { cache[event.id] = event }
      snapshot = events
      fetched = Date()
      await diskCache.write(events)
      return events
    } catch {
      fetched = Date()
      if !snapshot.isEmpty { return snapshot }
      let stored = await diskCache.read()
      if !stored.isEmpty {
        stored.forEach { cache[$0.id] = $0 }
        snapshot = stored
        return stored
      }
      throw error
    }
  }
}
