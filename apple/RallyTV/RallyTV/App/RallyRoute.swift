import Foundation

/// Typed navigation destinations. Mirrors the MainActivity route table:
/// home, leagues, league/{id}, team/{league}/{id}, event/{id},
/// player/{target}?eventId, multiview, iptv, search, highlights, watchlist, settings.
public enum RallyRoute: Hashable, Sendable {
  case home
  case schedule
  case live
  case leagues
  case leagueHub(league: String)
  case teamHub(league: String, teamId: String)
  case eventDetail(eventId: String)
  case player(target: String, eventId: String?)
  case playerSource(candidate: StreamCandidate, eventId: String?)
  case playerClip(url: URL, title: String, eventId: String?)
  case playerFull(target: String, candidate: StreamCandidate?, eventId: String?)
  case multiView(channelId: String?, eventId: String?, eventIds: [String])
  case multiViewSource(candidate: StreamCandidate, eventId: String?)
  case iptvBrowser
  case search
  case highlights
  case watchlist
  case settings
}
