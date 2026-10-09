import Foundation

/// Lossless flexible wire values for ESPN/provider fields whose types vary by sport.
public enum JSONValue: Codable, Sendable, Hashable {
  case object([String: JSONValue])
  case array([JSONValue])
  case string(String)
  case number(Double)
  case bool(Bool)
  case null
  public init(from decoder: Decoder) throws {
    let c = try decoder.singleValueContainer()
    if c.decodeNil() {
      self = .null
    } else if let v = try? c.decode(Bool.self) {
      self = .bool(v)
    } else if let v = try? c.decode(Double.self) {
      self = .number(v)
    } else if let v = try? c.decode(String.self) {
      self = .string(v)
    } else if let v = try? c.decode([JSONValue].self) {
      self = .array(v)
    } else {
      self = .object(try c.decode([String: JSONValue].self))
    }
  }
  public func encode(to encoder: Encoder) throws {
    var c = encoder.singleValueContainer()
    switch self {
    case .object(let v): try c.encode(v)
    case .array(let v): try c.encode(v)
    case .string(let v): try c.encode(v)
    case .number(let v): try c.encode(v)
    case .bool(let v): try c.encode(v)
    case .null: try c.encodeNil()
    }
  }
  public subscript(_ key: String) -> JSONValue {
    if case .object(let v) = self { return v[key] ?? .null }
    return .null
  }
  public var array: [JSONValue] {
    if case .array(let v) = self { return v }
    return []
  }
  public var string: String? {
    switch self {
    case .string(let v): return v
    case .number(let v): return v.rounded() == v ? String(Int(v)) : String(v)
    default: return nil
    }
  }
  public var text: String { string ?? "" }
  public var double: Double? {
    if case .number(let v) = self { return v }
    return string.flatMap(Double.init)
  }
  public var int: Int? { double.map(Int.init) }
  public var bool: Bool {
    if case .bool(let v) = self { return v }
    return text == "true" || text == "1"
  }
  public var url: URL? { string.flatMap(URL.init(string:)) }
  public func objects(where predicate: (JSONValue) -> Bool) -> [JSONValue] {
    switch self {
    case .object(let v):
      return (predicate(self) ? [self] : []) + v.values.flatMap { $0.objects(where: predicate) }
    case .array(let v): return v.flatMap { $0.objects(where: predicate) }
    default: return []
    }
  }
  public var first: JSONValue { array.first ?? .null }
}

public enum ESPNWire {
  public static func date(_ text: String) -> Date? {
    let f = ISO8601DateFormatter()
    if let date = f.date(from: text) { return date }
    f.formatOptions.insert(.withFractionalSeconds)
    if let date = f.date(from: text) { return date }
    // ESPN scoreboards publish minute precision (for example 2026-10-02T00:15Z).
    let minutes = DateFormatter()
    minutes.locale = Locale(identifier: "en_US_POSIX")
    minutes.calendar = Calendar(identifier: .gregorian)
    minutes.dateFormat = "yyyy-MM-dd'T'HH:mmXXXXX"
    return minutes.date(from: text)
  }
  public static func team(_ t: JSONValue) -> Team? {
    guard !t["id"].text.isEmpty else { return nil }
    return Team(
      id: t["id"].text, name: t["displayName"].string ?? t["name"].text,
      abbreviation: t["abbreviation"].text,
      logoUrl: t["logo"].url ?? t["logos"].first["href"].url,
      colors: [t["color"].text, t["alternateColor"].text].filter { !$0.isEmpty },
      shortName: t["name"].string ?? t["shortDisplayName"].string)
  }
  public static func status(_ s: JSONValue) -> EventStatus {
    let detail = (s["name"].text + " " + s["detail"].text).lowercased()
    if detail.contains("cancel") { return .canceled }
    if detail.contains("postpon") || detail.contains("delay") { return .delayed }
    if detail.contains("halftime") { return .halftime }
    if s["completed"].bool || s["state"].text == "post" { return .finished }
    return s["state"].text == "in" ? .live : .notStarted
  }
  private static func broadcasts(_ competition: JSONValue) -> [String] {
    competition["broadcasts"].array.flatMap { broadcast -> [String] in
      let names = broadcast["names"].array.compactMap(\.string)
      if !names.isEmpty { return names }
      guard broadcast["type"]["shortName"].text != "Radio",
        let name = broadcast["media"]["shortName"].string
      else { return [] }
      return [name]
    }
  }
  private static func situation(
    _ competition: JSONValue, home: Team?, away: Team?, stats: inout [String: String]
  ) {
    let current = competition["situation"]
    if let yard = current["yardLine"].string { stats["Drive Yard Line"] = yard }
    if let down = current["shortDownDistanceText"].string ?? current["downDistanceText"].string {
      stats["Current Drive"] = down + (current["isRedZone"].bool ? " · Red zone" : "")
    }
    let possession = current["possession"].text
    if !possession.isEmpty {
      if possession == home?.id { stats["Possession"] = home?.abbreviation }
      if possession == away?.id { stats["Possession"] = away?.abbreviation }
    }
    let address = competition["venue"]["address"]
    let city = [address["city"].text, address["state"].text].filter { !$0.isEmpty }.joined(
      separator: ", ")
    if !city.isEmpty { stats["Venue City"] = city }
  }
  public static func event(_ e: JSONValue, league: String, sport: String) -> SportEvent? {
    let c = e["competitions"].first
    guard !e["id"].text.isEmpty, !c["competitors"].array.isEmpty, let date = date(e["date"].text)
    else { return nil }
    let h = c["competitors"].array.first { $0["homeAway"].text == "home" } ?? .null
    let a = c["competitors"].array.first { $0["homeAway"].text == "away" } ?? .null
    let home = team(h["team"])
    let away = team(a["team"])
    var stats: [String: String] = [:]
    let networks = broadcasts(c)
    if !networks.isEmpty { stats["TV Broadcast"] = networks.joined(separator: ", ") }
    for (v, t) in [(h, home), (a, away)] {
      if let r = v["records"].array.first(where: { $0["name"].text == "total" })
        ?? v["records"].array.first
      {
        stats["\(t?.abbreviation ?? "") Record"] = r["summary"].text
      }
    }
    if let weather = c["weather"]["displayValue"].string { stats["Weather"] = weather }
    if let temp = c["weather"]["temperature"].string { stats["Temperature"] = temp + "°" }
    situation(c, home: home, away: away, stats: &stats)
    let s = c["status"]["type"]
    return SportEvent(
      id: "\(league):\(e["id"].text)", name: e["name"].string ?? e["shortName"].text,
      homeTeam: home, awayTeam: away, startTime: date, status: status(s),
      scoreHome: h["score"].int ?? h["score"]["displayValue"].int,
      scoreAway: a["score"].int ?? a["score"]["displayValue"].int, sport: sport, league: league,
      homeTeamBadge: home?.logoUrl, awayTeamBadge: away?.logoUrl,
      venue: c["venue"]["fullName"].string,
      venueImageUrl: c["venue"]["images"].first["href"].url,
      eventContextTitle: c["notes"].first["headline"].string,
      liveStats: stats, gameStatusDetail: s["shortDetail"].string ?? s["detail"].string)
  }
  public static func clip(_ v: JSONValue, context: String = "video") -> HighlightClip? {
    guard !v["headline"].text.isEmpty else { return nil }
    let source = v["links"]["source"]
    let stream =
      source["HLS"]["HD"]["href"].url ?? source["HLS"]["href"].url ?? source["HD"]["href"].url
      ?? source["href"].url ?? v["links"]["mobile"]["source"]["href"].url
    return HighlightClip(
      id: v["id"].string ?? "\(context):\(v["headline"].text)", title: v["headline"].text,
      description: v["description"].string, durationSeconds: v["duration"].int,
      thumbnailUrl: v["thumbnail"].url, streamUrl: stream, webUrl: v["links"]["web"]["href"].url)
  }
  public static func enrich(_ base: SportEvent, summary s: JSONValue) -> SportEvent {
    let competition = s["header"]["competitions"].first
    let h = competition["competitors"].array.first { $0["homeAway"].text == "home" } ?? .null
    let a = competition["competitors"].array.first { $0["homeAway"].text == "away" } ?? .null
    let home = team(h["team"]) ?? base.homeTeam
    let away = team(a["team"]) ?? base.awayTeam
    let groups = s["boxscore"]["teams"].array
    let hg = groups.first { $0["team"]["id"].text == home?.id } ?? .null
    let ag = groups.first { $0["team"]["id"].text == away?.id } ?? .null
    func statistics(_ group: JSONValue) -> [JSONValue] {
      group["statistics"].array.flatMap { entry in
        entry["stats"].array.isEmpty ? [entry] : entry["stats"].array
      }.filter { !$0["displayValue"].text.isEmpty }
    }
    let awayStats = statistics(ag)
    let homeStats = statistics(hg)
    let preferred: [(String, String)]
    switch base.sport {
    case "football":
      preferred = [
        ("netPassingYards", "Passing"), ("rushingYards", "Rushing"), ("totalYards", "Total Yds"),
        ("turnovers", "Turnovers"), ("firstDowns", "1st Downs"),
      ]
    case "basketball":
      preferred = [
        ("fieldGoals", "FG%"), ("threePointFieldGoals", "3PT%"), ("totalRebounds", "Rebounds"),
        ("turnovers", "Turnovers"), ("assists", "Assists"),
      ]
    case "baseball":
      preferred = [
        ("hits", "Hits"), ("errors", "Errors"), ("strikeouts", "Strikeouts"), ("walks", "Walks"),
      ]
    case "hockey":
      preferred = [
        ("shots", "SOG"), ("powerPlayGoals", "Power Play"), ("blockedShots", "Blocks"),
        ("hits", "Hits"),
      ]
    default:
      preferred = [
        ("shotsOnTarget", "SOG"), ("possession", "Possession"), ("fouls", "Fouls"),
        ("cornerKicks", "Corners"),
      ]
    }
    let keys = (preferred.map(\.0) + (awayStats + homeStats).map { $0["name"].text }).reduce(
      into: [String]()
    ) { if !$0.contains($1) { $0.append($1) } }
    var comparisons = keys.compactMap { key -> TeamStatComparison? in
      let av = awayStats.first { $0["name"].text == key } ?? .null
      let hv = homeStats.first { $0["name"].text == key } ?? .null
      guard av != .null || hv != .null else { return nil }
      return TeamStatComparison(
        label: preferred.first { $0.0 == key }?.1 ?? av["label"].string ?? hv["label"].string ?? av[
          "displayName"
        ].string ?? hv["displayName"].string ?? key,
        awayValue: av["displayValue"].string ?? "—", homeValue: hv["displayValue"].string ?? "—")
    }
    if comparisons.isEmpty {
      for (key, label) in [("score", "Score"), ("hits", "Hits"), ("errors", "Errors")] {
        if let av = a[key].string, let hv = h[key].string {
          comparisons.append(TeamStatComparison(label: label, awayValue: av, homeValue: hv))
        }
      }
    }
    let odds = s["pickcenter"].first
    func signed(_ value: Double) -> String {
      value > 0 ? "+\(JSONValue.number(value).text)" : JSONValue.number(value).text
    }
    if let spread = odds["spread"].double {
      comparisons.append(
        TeamStatComparison(label: "Spread", awayValue: signed(-spread), homeValue: signed(spread)))
    } else if let line = odds["details"].string {
      comparisons.append(TeamStatComparison(label: "Line", awayValue: line, homeValue: line))
    }
    if let total = odds["overUnder"].string {
      comparisons.append(
        TeamStatComparison(label: "Over/Under", awayValue: "O " + total, homeValue: "U " + total))
    }
    if let av = odds["awayTeamOdds"]["moneyLine"].double,
      let hv = odds["homeTeamOdds"]["moneyLine"].double
    {
      comparisons.append(
        TeamStatComparison(label: "Moneyline", awayValue: signed(av), homeValue: signed(hv)))
    }
    let projectedHome = s["predictor"]["homeTeam"]["gameProjection"].double
    let projectedAway = s["predictor"]["awayTeam"]["gameProjection"].double
    if let av = projectedAway, let hv = projectedHome, (0...100).contains(av),
      (0...100).contains(hv)
    {
      comparisons.append(
        TeamStatComparison(label: "Win Prob", awayValue: "\(av)%", homeValue: "\(hv)%"))
    }
    let tables = s["boxscore"]["players"].array.flatMap { group in
      group["statistics"].array.map { category in
        PlayerStatTable(
          teamId: group["team"]["id"].string, teamName: group["team"]["displayName"].text,
          teamAbbreviation: group["team"]["abbreviation"].text,
          teamLogoUrl: team(group["team"])?.logoUrl,
          category: category["name"].string ?? category["type"].string,
          labels: category["labels"].array.compactMap(\.string),
          rows: category["athletes"].array.compactMap { item in
            let athlete = item["athlete"]
            guard !athlete["displayName"].text.isEmpty else { return nil }
            return PlayerStatRow(
              athleteId: athlete["id"].string, displayName: athlete["displayName"].text,
              shortName: athlete["shortName"].string, headshotUrl: athlete["headshot"]["href"].url,
              jersey: athlete["jersey"].string,
              position: athlete["position"]["abbreviation"].string,
              stats: item["stats"].array.compactMap(\.string))
          })
      }
    }
    var leaders = s["leaders"].array.flatMap { group in
      group["leaders"].array.flatMap { category in
        category["leaders"].array.prefix(1).map { v in
          PlayerLeader(
            category: category["displayName"].string ?? category["name"].text,
            teamLogoUrl: team(group["team"])?.logoUrl,
            teamAbbreviation: group["team"]["abbreviation"].string,
            playerShortName: v["athlete"]["shortName"].string ?? v["athlete"]["displayName"].text,
            statDisplay: v["displayValue"].text,
            position: v["athlete"]["position"]["abbreviation"].string,
            headshotUrl: v["athlete"]["headshot"]["href"].url)
        }
      }
    }
    if leaders.isEmpty {
      leaders = tables.compactMap { table in
        guard let row = table.rows.first else { return nil }
        let selectedLabels =
          table.category == "batting"
          ? ["H-AB", "RBI"]
          : table.category == "pitching" ? ["IP", "K"] : Array(table.labels.prefix(2))
        let values = selectedLabels.compactMap { label -> String? in
          guard let index = table.labels.firstIndex(of: label), row.stats.indices.contains(index)
          else { return nil }
          return row.stats[index] + " " + label
        }
        guard !values.isEmpty else { return nil }
        return PlayerLeader(
          category: (table.category ?? "Player Stats").capitalized, teamLogoUrl: table.teamLogoUrl,
          teamAbbreviation: table.teamAbbreviation,
          playerShortName: row.shortName ?? row.displayName,
          statDisplay: values.joined(separator: " · "), position: row.position,
          headshotUrl: row.headshotUrl)
      }
    }
    let drivePlays = s["drives"]["previous"].array.flatMap { $0["plays"].array } + s["drives"]["current"]["plays"].array
    let scoringIDs = Set(s["scoringPlays"].array.compactMap { $0["id"].string })
    var seenPlays = Set<String>()
    let publishedPlays = (s["plays"].array + drivePlays + s["scoringPlays"].array).filter { p in
      let key = p["id"].string ?? "\(p["sequenceNumber"].text):\(p["text"].text)"
      return !p["text"].text.isEmpty && seenPlays.insert(key).inserted
    }
    let plays = publishedPlays.enumerated().compactMap { i, p -> GamePlay? in
      guard !p["text"].text.isEmpty else { return nil }
      return GamePlay(
        id: p["id"].string ?? "\(base.id):\(i)", sequence: base.sport == "baseball" ? i : p["sequenceNumber"].int ?? i,
        text: p["text"].text, awayScore: p["awayScore"].int, homeScore: p["homeScore"].int,
        period: p["period"]["number"].int, clock: p["clock"]["displayValue"].string,
        isScoringPlay: p["scoringPlay"].bool || scoringIDs.contains(p["id"].text))
    }.sorted { $0.sequence > $1.sequence }
    var probability = s["winprobability"].array.enumerated().compactMap {
      i, p -> WinProbabilityPoint? in
      guard let value = p["homeWinPercentage"].double, value >= 0, value <= 1 else { return nil }
      return WinProbabilityPoint(
        playId: p["playId"].string, homeWinPercentage: value,
        tiePercentage: min(1 - value, max(0, p["tiePercentage"].double ?? 0)), sequence: i)
    }
    if probability.isEmpty, let home = projectedHome, (0...100).contains(home) {
      probability = [WinProbabilityPoint(homeWinPercentage: home / 100, sequence: 0)]
    }
    var stats = base.liveStats
    let networks = broadcasts(competition)
    if !networks.isEmpty { stats["TV Broadcast"] = networks.joined(separator: ", ") }
    for (v, t) in [(h, home), (a, away)] {
      if let r = v["record"].array.first {
        stats["\(t?.abbreviation ?? "") Record"] = r["summary"].text
      }
    }
    let drive = s["drives"]["current"]
    if let desc = drive["description"].string { stats["Current Drive"] = desc }
    if let yard = drive["yards"].string { stats["Drive Yards"] = yard }
    if let latest = plays.first { stats["Latest Play"] = latest.text }
    situation(competition, home: home, away: away, stats: &stats)
    let venue = s["gameInfo"]["venue"]
    return SportEvent(
      id: base.id, name: base.name, homeTeam: home, awayTeam: away, startTime: base.startTime,
      status: competition["status"]["type"] == .null
        ? base.status : status(competition["status"]["type"]),
      scoreHome: h["score"].int ?? base.scoreHome, scoreAway: a["score"].int ?? base.scoreAway,
      sport: base.sport, league: base.league, bannerUrl: base.bannerUrl,
      homeTeamBadge: home?.logoUrl, awayTeamBadge: away?.logoUrl,
      venue: venue["fullName"].string ?? base.venue,
      venueImageUrl: venue["images"].first["href"].url ?? competition["venue"]["images"].first[
        "href"
      ].url ?? base.venueImageUrl,
      eventContextTitle: base.eventContextTitle, liveStats: stats,
      gameStatusDetail: competition["status"]["type"]["shortDetail"].string
        ?? base.gameStatusDetail,
      teamStats: comparisons, playerLeaders: leaders,
      highlightClips: s["videos"].array.compactMap { clip($0, context: base.id) },
      winProbability: probability, playerStatTables: tables, plays: plays)
  }
}
