import Foundation

/// Stalker-backed catalog. Ports `StalkerIptvRepositoryImpl` cache semantics:
/// 15-min channel TTL, 2-min guide TTL, portal autodiscovery.
public actor StalkerIptvRepository: IptvRepository {
  private let http: HTTPClient
  private let settings: SettingsStore
  private let store: any ChannelStore

  private var commands: [String: String] = [:]
  private var memoryChannels: [IptvChannel] = []
  private var lastFetchMs: Int64 = 0
  private var guides: [String: (guide: ChannelGuide, at: Date)] = [:

    ]
  private var linkBusy = false
  private var linkWaiters: [(UUID, CheckedContinuation<Void, Error>)] = []
  private var authTask: Task<Bool, Never>?

  private static let channelTTL: TimeInterval = 15 * 60
  private static let guideTTL: TimeInterval = 2 * 60

  public init(http: HTTPClient, settings: SettingsStore, store: any ChannelStore) {
    self.http = http
    self.settings = settings
    self.store = store
  }

  public func authenticate() async -> Bool {
    await authenticateInternal(force: false)
  }

  public func channels() async throws -> [IptvChannel] {
    if !memoryChannels.isEmpty,
      Date().timeIntervalSince1970 * 1000 - Double(lastFetchMs) < Self.channelTTL * 1000
    {
      return memoryChannels
    }
    let stored = await store.allChannels()
    if !stored.isEmpty,
      Date().timeIntervalSince1970 * 1000 - Double(lastFetchMs) < Self.channelTTL * 1000
    {
      memoryChannels = stored
      return stored
    }
    guard await authenticateInternal(force: false) else { return stored }
    let fetched = await fetchAllChannels()
    if !fetched.isEmpty {
      memoryChannels = fetched
      lastFetchMs = Int64(Date().timeIntervalSince1970 * 1000)
      await store.replaceAll(fetched)
      return fetched
    }
    return stored
  }

  public func refreshChannels() async throws -> [IptvChannel] {
    memoryChannels = []
    lastFetchMs = 0
    return try await channels()
  }

  public func streamUrl(forChannelId channelId: String) async throws -> URL {
    if let direct = URL(string: channelId), direct.scheme?.hasPrefix("http") == true {
      return direct
    }
    _ = try await channels()
    let id = channelId.replacingOccurrences(of: "stalker:", with: "")
    guard let cmd = commands[id], let api = makeAPI() else {
      throw RallyNetworkError.invalidResponse
    }
    try await acquireLink()
    defer { releaseLink() }
    try Task.checkCancellation()
    do {
      if let link = try await api.createLink(token: settings.authToken, cmd: cmd) { return link }
    } catch {
      try Task.checkCancellation()
      if error is CancellationError { throw error }
    }
    try Task.checkCancellation()
    guard await authenticateInternal(force: true),
      let link = try await api.createLink(token: settings.authToken, cmd: cmd)
    else { throw RallyNetworkError.invalidResponse }
    return link
  }

  // Actors are reentrant across network awaits. Explicitly order negotiations
  // without serializing playback downloads or swallowing cancelled waiters.
  private func acquireLink() async throws {
    try Task.checkCancellation()
    if !linkBusy { linkBusy = true; return }
    let id = UUID()
    try await withTaskCancellationHandler(operation: {
      try await withCheckedThrowingContinuation { continuation in
        linkWaiters.append((id, continuation))
      }
    }, onCancel: { Task { await self.cancelLinkWaiter(id) } })
    if Task.isCancelled { releaseLink(); throw CancellationError() }
  }
  private func cancelLinkWaiter(_ id: UUID) {
    guard let index = linkWaiters.firstIndex(where: { $0.0 == id }) else { return }
    linkWaiters.remove(at: index).1.resume(throwing: CancellationError())
  }
  private func releaseLink() {
    if linkWaiters.isEmpty { linkBusy = false }
    else { linkWaiters.removeFirst().1.resume() }
  }

  public func guide(forChannelId channelId: String) async -> ChannelGuide? {
    if let cached = guides[channelId],
      Date().timeIntervalSince(cached.at) < Self.guideTTL
    {
      return cached.guide
    }
    guard let api = makeAPI(), !settings.authToken.isEmpty else { return nil }
    guard
      let programs = try? await api.shortEPG(
        token: settings.authToken,
        channelId: channelId.replacingOccurrences(of: "stalker:", with: ""))
    else { return nil }
    let mapped = programs.map { dto in
      EpgProgram(
        title: dto.name ?? "",
        description: dto.descr,
        startTime: dto.startTimestamp.map { Date(timeIntervalSince1970: TimeInterval($0)) },
        endTime: dto.stopTimestamp.map { Date(timeIntervalSince1970: TimeInterval($0)) }
      )
    }
    let guide = ChannelGuide(now: mapped.first, next: mapped.dropFirst().first)
    guides[channelId] = (guide, Date())
    return guide
  }

  public func clearMemoryCache() async {
    memoryChannels = []
    guides = [:]
  }

  // MARK: - Auth

  private func authenticateInternal(force: Bool) async -> Bool {
    if let authTask { return await authTask.value }
    if !force, !settings.authToken.isEmpty { return true }
    let task = Task { await self.performAuthentication(force: force) }
    authTask = task
    let result = await task.value
    authTask = nil
    return result
  }
  private func performAuthentication(force: Bool) async -> Bool {
    if !force, !settings.authToken.isEmpty { return true }
    guard !settings.portalUrl.isEmpty else { return false }
    settings.authToken = ""
    if await tryAuth() { return true }
    // Portal autodiscovery: try common suffix variants.
    let base = settings.portalUrl
      .replacingOccurrences(of: "/server/load.php", with: "")
      .replacingOccurrences(of: "/load.php", with: "")
    let domain =
      base
      .replacingOccurrences(of: "/c", with: "")
      .replacingOccurrences(of: "/stalker_portal", with: "")
    let candidates = [
      base, domain + "/c", domain + "/stalker_portal", domain + "/stalker_portal/c",
    ]
    .map { $0.trimmingCharacters(in: CharacterSet(charactersIn: "/")) }
    var seen = Set<String>()
    for candidate in candidates where seen.insert(candidate).inserted {
      settings.portalUrl = candidate
      if await tryAuth() { return true }
    }
    settings.portalUrl = base
    return false
  }

  private func tryAuth() async -> Bool {
    guard let api = makeAPI() else { return false }
    guard let token = try? await api.handshake(token: nil),
      !token.isEmpty
    else { return false }
    let bearer = StreamHeaders.normalizedBearerToken(token)
    settings.authToken = bearer
    let ok = (try? await api.profile(token: bearer)) ?? false
    if !ok { settings.authToken = "" }
    return ok
  }

  private func makeAPI() -> StalkerAPI? {
    guard let url = URL(string: settings.portalUrl) else { return nil }
    return StalkerAPI(
      http: http, portalURL: url, macAddress: settings.macAddress,
      serialNumber: settings.serialNumber, deviceId: settings.deviceId)
  }

  private func fetchAllChannels() async -> [IptvChannel] {
    guard let api = makeAPI(), !settings.authToken.isEmpty else { return [] }
    guard let dtos = try? await api.allChannels(token: settings.authToken) else { return [] }
    var genreNames: [String: String] = [:]
    if let genres = try? await api.genres(token: settings.authToken) {
      for genre in genres {
        if let id = genre.id { genreNames[id] = genre.title ?? "" }
      }
    }
    var channels: [IptvChannel] = []
    for dto in dtos where !dto.id.isEmpty {
      commands[dto.id] = dto.cmd
      let stream = dto.cmd.hasPrefix("http") ? URL(string: dto.cmd) : nil
      channels.append(
        IptvChannel(
          id: "stalker:\(dto.id)",
          number: dto.number,
          name: dto.name,
          category: dto.genreId.flatMap { genreNames[$0] } ?? "",
          logoUrl: dto.logo.flatMap(URL.init(string:)),
          streamUrl: stream
        ))
    }
    return channels
  }
}
