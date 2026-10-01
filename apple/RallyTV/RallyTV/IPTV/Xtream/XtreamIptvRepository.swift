import Foundation

/// Xtream-backed catalog. Ports `XtreamIptvRepositoryImpl` TTL semantics:
/// 15-min catalog, 5-min auth, 2-min guide. Memory-only by design — never
/// touches the Stalker channel store, so switching providers is non-destructive.
public actor XtreamIptvRepository: IptvRepository {
  private let http: HTTPClient
  private let api: XtreamAPI
  private let settings: SettingsStore

  private var cachedChannels: [IptvChannel] = []
  private var cachedAt = Date.distantPast
  private var authenticatedUntil = Date.distantPast
  private var cacheIdentity = ""
  private var guides: [String: (guide: ChannelGuide, at: Date)] = [:]

  private static let channelTTL: TimeInterval = 15 * 60
  private static let authTTL: TimeInterval = 5 * 60
  private static let guideTTL: TimeInterval = 2 * 60

  public init(http: HTTPClient, settings: SettingsStore) {
    self.http = http
    self.api = XtreamAPI(http: http)
    self.settings = settings
  }

  private var identity: String? {
    let server = settings.xtreamServerUrl
    let user = settings.xtreamUsername
    guard !server.isEmpty, !user.isEmpty else { return nil }
    return "\(server)|\(user)"
  }

  public func authenticate() async -> Bool {
    guard let identity,
      let url = XtreamUrlBuilder.playerApi(
        server: settings.xtreamServerUrl,
        username: settings.xtreamUsername,
        password: settings.xtreamPassword
      )
    else { return false }
    if identity == cacheIdentity, Date() < authenticatedUntil { return true }
    guard let info = try? await api.account(url: url) else {
      authenticatedUntil = .distantPast
      return false
    }
    guard info.isAccepted, info.isActive else {
      authenticatedUntil = .distantPast
      RallyLogger.iptv.error("xtream account rejected or inactive")
      return false
    }
    cacheIdentity = identity
    authenticatedUntil = Date().addingTimeInterval(Self.authTTL)
    return true
  }

  public func channels() async throws -> [IptvChannel] {
    guard identity != nil else { return [] }
    if !cachedChannels.isEmpty, Date().timeIntervalSince(cachedAt) < Self.channelTTL {
      return cachedChannels
    }
    guard await authenticate() else { return cachedChannels }
    let fetched = await fetchChannels()
    if !fetched.isEmpty {
      cachedChannels = fetched
      cachedAt = Date()
    }
    return cachedChannels
  }

  public func refreshChannels() async throws -> [IptvChannel] {
    cachedChannels = []
    cachedAt = .distantPast
    return try await channels()
  }

  public func streamUrl(forChannelId channelId: String) async throws -> URL {
    if let direct = URL(string: channelId), direct.scheme?.hasPrefix("http") == true {
      return direct
    }
    if let cached = cachedChannels.first(where: { $0.id == channelId }),
      let stream = cached.streamUrl
    {
      return stream
    }
    let streamId = channelId.replacingOccurrences(of: "xtream:", with: "")
    if let url = XtreamUrlBuilder.liveStream(
      server: settings.xtreamServerUrl,
      username: settings.xtreamUsername,
      password: settings.xtreamPassword,
      streamId: streamId
    ) {
      return url
    }
    throw RallyNetworkError.invalidResponse
  }

  public func guide(forChannelId channelId: String) async -> ChannelGuide? {
    if let cached = guides[channelId],
      Date().timeIntervalSince(cached.at) < Self.guideTTL
    {
      return cached.guide
    }
    guard
      var c = XtreamUrlBuilder.playerApi(
        server: settings.xtreamServerUrl, username: settings.xtreamUsername,
        password: settings.xtreamPassword, action: "get_short_epg"
      ).flatMap({ URLComponents(url: $0, resolvingAgainstBaseURL: false) })
    else { return nil }
    c.queryItems =
      (c.queryItems ?? []) + [
        URLQueryItem(
          name: "stream_id", value: channelId.replacingOccurrences(of: "xtream:", with: "")),
        URLQueryItem(name: "limit", value: "2"),
      ]
    guard let url = c.url, let response: JSONValue = try? await http.json(url: url) else {
      return nil
    }
    let programs = response["epg_listings"].array.map { v -> EpgProgram in
      func decode(_ s: String) -> String {
        Data(base64Encoded: s).flatMap { String(data: $0, encoding: .utf8) } ?? s
      }
      func date(_ key: String, _ alt: String) -> Date? {
        if let n = v[key].double { return Date(timeIntervalSince1970: n) }
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return f.date(from: v[alt].text)
      }
      return EpgProgram(
        title: decode(v["title"].text), description: decode(v["description"].text),
        startTime: date("start_timestamp", "start"), endTime: date("stop_timestamp", "end"))
    }
    let guide = ChannelGuide(now: programs.first, next: programs.dropFirst().first)
    guides[channelId] = (guide, Date())
    return guide
  }

  public func clearMemoryCache() async {
    cachedChannels = []
    guides = [:]
  }

  // MARK: - Fetch

  private func fetchChannels() async -> [IptvChannel] {
    guard
      let url = XtreamUrlBuilder.playerApi(
        server: settings.xtreamServerUrl,
        username: settings.xtreamUsername,
        password: settings.xtreamPassword,
        action: "get_live_streams"
      ),
      let dtos = try? await api.liveStreams(url: url)
    else { return [] }
    var categoryNames: [String: String] = [:]
    if let url = XtreamUrlBuilder.playerApi(
      server: settings.xtreamServerUrl, username: settings.xtreamUsername,
      password: settings.xtreamPassword, action: "get_live_categories"),
      let categories: JSONValue = try? await http.json(url: url)
    {
      for c in categories.array { categoryNames[c["category_id"].text] = c["category_name"].text }
    }
    return dtos.compactMap { dto in
      guard !dto.streamId.isEmpty else { return nil }
      let stream = XtreamUrlBuilder.liveStream(
        server: settings.xtreamServerUrl,
        username: settings.xtreamUsername,
        password: settings.xtreamPassword,
        streamId: dto.streamId
      )
      return IptvChannel(
        id: "xtream:\(dto.streamId)",
        number: dto.streamId,
        name: dto.name,
        category: dto.categoryId.flatMap { categoryNames[$0] } ?? "Other",
        logoUrl: dto.streamIcon.flatMap(URL.init(string:)),
        streamUrl: stream
      )
    }
  }
}
