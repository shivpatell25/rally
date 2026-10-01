import Foundation

actor M3uIptvRepository: IptvRepository {
  private let http: HTTPClient
  private let settings: SettingsStore
  private var catalog: (identity: String, loaded: Date, channels: [IptvChannel])?
  private var pending: (identity: String, task: Task<[IptvChannel], Error>)?
  init(http: HTTPClient, settings: SettingsStore) {
    self.http = http
    self.settings = settings
  }
  func authenticate() async -> Bool { ((try? await channels()) ?? []).isEmpty == false }
  func channels() async throws -> [IptvChannel] {
    let source = settings.m3uPlaylistUrl
    let name = settings.m3uPlaylistName
    guard !source.isEmpty else { return [] }
    let identity = source + "\n" + name
    if let catalog, catalog.identity == identity, Date().timeIntervalSince(catalog.loaded) < 900 {
      return catalog.channels
    }
    if let pending, pending.identity == identity { return try await pending.task.value }
    let http = http
    let task = Task<[IptvChannel], Error> {
      guard let url = URL(string: source) else { throw M3uPlaylistError.invalid }
      let data: Data
      let final: URL
      if url.isFileURL {
        guard M3uPlaylistFiles.isOwned(url),
          let size = try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize,
          size <= M3uPlaylistParser.maximumBytes
        else { throw M3uPlaylistError.unavailableFile }
        data = try Data(contentsOf: url, options: .mappedIfSafe)
        final = url
      } else {
        let response = try await http.playlistData(url: url)
        data = response.0
        final = response.1.url ?? url
      }
      guard let text = String(data: data, encoding: .utf8) else { throw M3uPlaylistError.invalid }
      try Task.checkCancellation()
      return try M3uPlaylistParser.parse(text, source: final, name: name)
    }
    pending = (identity, task)
    do {
      let channels = try await task.value
      if pending?.identity == identity { pending = nil }
      if settings.m3uPlaylistUrl == source && settings.m3uPlaylistName == name {
        catalog = (identity, Date(), channels)
      }
      return channels
    } catch {
      if pending?.identity == identity { pending = nil }
      throw error
    }
  }
  func refreshChannels() async throws -> [IptvChannel] {
    await clearMemoryCache()
    return try await channels()
  }
  func clearMemoryCache() async {
    catalog = nil
    pending?.task.cancel()
    pending = nil
  }
  func streamUrl(forChannelId channelId: String) async throws -> URL {
    guard let url = try await channels().first(where: { $0.id == channelId })?.streamUrl else {
      throw M3uPlaylistError.missingChannel
    }
    return url
  }
  func guide(forChannelId channelId: String) async -> ChannelGuide? { nil }
}

extension HTTPClient {
  /// Bound resident memory while reading a provider's potentially large catalog.
  func playlistData(url: URL) async throws -> (Data, HTTPURLResponse) {
    guard NetworkPolicy.shared.permits(url) else {
      throw URLError(.appTransportSecurityRequiresSecureConnection)
    }
    let result: (Data, HTTPURLResponse)
    if url.scheme == "http" {
      result = try await data(url: url, timeout: 30)
    } else {
      var request = URLRequest(url: url)
      request.timeoutInterval = 30
      let (file, response) = try await session.download(for: request)
      defer { try? FileManager.default.removeItem(at: file) }
      guard let response = response as? HTTPURLResponse else {
        throw RallyNetworkError.invalidResponse
      }
      guard (200..<300).contains(response.statusCode) else {
        throw RallyNetworkError.httpStatus(response.statusCode, url)
      }
      guard let size = try file.resourceValues(forKeys: [.fileSizeKey]).fileSize,
        size <= M3uPlaylistParser.maximumBytes
      else { throw M3uPlaylistError.oversized }
      result = (try Data(contentsOf: file, options: .mappedIfSafe), response)
    }
    guard result.0.count <= M3uPlaylistParser.maximumBytes else { throw M3uPlaylistError.oversized }
    return result
  }
}
