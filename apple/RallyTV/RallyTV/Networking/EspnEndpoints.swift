import Foundation

/// ESPN endpoint surface. Base matches Android DataModule:
/// https://site.api.espn.com/apis/site/v2/
public enum EspnEndpoints {
  public static let baseURL = URL(string: "https://site.api.espn.com/apis/site/v2/")!

  /// Domain leagues → ESPN (sport, league) path pairs. Mirrors EspnRepositoryImpl.espnLeagues.
  public static let leagues: [String: (sport: String, league: String)] = [
    "NFL": ("football", "nfl"),
    "NCAAF": ("football", "college-football"),
    "NBA": ("basketball", "nba"),
    "NCAAB": ("basketball", "mens-college-basketball"),
    "MLB": ("baseball", "mlb"),
    "NHL": ("hockey", "nhl"),
    "EPL": ("soccer", "eng.1"),
    "La Liga": ("soccer", "esp.1"),
    "Champions League": ("soccer", "uefa.champions"),
    "Serie A": ("soccer", "ita.1"),
    "MLS": ("soccer", "usa.1"),
  ]

  public static func scoreboard(
    sport: String, league: String, dates: String? = nil, limit: Int = 100
  ) -> URL? {
    var components = URLComponents(
      url: baseURL.appendingPathComponent("sports/\(sport)/\(league)/scoreboard"),
      resolvingAgainstBaseURL: false
    )
    var items = [URLQueryItem(name: "limit", value: String(limit))]
    if let dates { items.append(URLQueryItem(name: "dates", value: dates)) }
    components?.queryItems = items
    return components?.url
  }

  public static func summary(sport: String, league: String, eventId: String) -> URL? {
    var components = URLComponents(
      url: baseURL.appendingPathComponent("sports/\(sport)/\(league)/summary"),
      resolvingAgainstBaseURL: false
    )
    components?.queryItems = [URLQueryItem(name: "event", value: eventId)]
    return components?.url
  }

  /// Resolves which (sport, league) path owns an ESPN event id prefix.
  /// Event ids are namespaced per league on the client; callers pass the league through.
  public static func path(forLeague league: String) -> (sport: String, league: String)? {
    leagues[league]
  }
}
