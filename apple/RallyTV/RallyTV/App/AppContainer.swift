import Foundation

/// Composition root. Replaces Hilt's DataModule/RepositoryModule with an explicit,
/// test-seam-friendly container. ViewModels receive exactly what they need —
/// never the whole container.
@MainActor
public final class AppContainer: Sendable {
  public let settings: SettingsStore
  public let http: HTTPClient
  public let scheduleCache: ScheduleDiskCache
  public let channelStore: any ChannelStore
  public let sports: any SportsRepository
  public let matcher: any MatcherService
  public let selectBestStream: SelectBestStream
  public let stremio: any StremioRepository
  public private(set) var iptv: any IptvRepository

  public init(
    settings: SettingsStore = SettingsStore(),
    http: HTTPClient? = nil,
    scheduleCache: ScheduleDiskCache = ScheduleDiskCache(),
    channelStore: (any ChannelStore)? = nil,
    sportsOverride: (any SportsRepository)? = nil,
    stremioOverride: (any StremioRepository)? = nil,
    iptvOverride: (any IptvRepository)? = nil
  ) {
    NetworkPolicy.shared.configure(settings)
    let http = http ?? HTTPClient()
    let store = channelStore ?? InMemoryChannelStore()
    let sports: any SportsRepository =
      sportsOverride
      ?? EspnSportsRepository(http: http, diskCache: scheduleCache, settings: settings)
    let matcher = MatchEventToStream(tvStations: { eventId in
      (try? await sports.tvStations(forEventId: eventId)) ?? []
    })
    let stremio: any StremioRepository =
      stremioOverride ?? StremioRepositoryImpl(http: http, settings: settings)
    let iptv: any IptvRepository
    switch settings.provider {
    case .stalker:
      iptv = StalkerIptvRepository(http: http, settings: settings, store: store)
    case .xtream:
      iptv = XtreamIptvRepository(http: http, settings: settings)
    case .m3u:
      iptv = M3uIptvRepository(http: http, settings: settings)
    }
    self.settings = settings
    self.http = http
    self.scheduleCache = scheduleCache
    self.channelStore = store
    self.sports = sports
    self.matcher = matcher
    self.selectBestStream = SelectBestStream(matcher: matcher)
    self.stremio = stremio
    self.iptv = iptvOverride ?? iptv
  }

  public func applyProvider() {
    NetworkPolicy.shared.configure(settings)
    settings.authToken = ""
    switch settings.provider {
    case .stalker:
      iptv = StalkerIptvRepository(http: http, settings: settings, store: InMemoryChannelStore())
    case .xtream: iptv = XtreamIptvRepository(http: http, settings: settings)
    case .m3u: iptv = M3uIptvRepository(http: http, settings: settings)
    }
  }

}
