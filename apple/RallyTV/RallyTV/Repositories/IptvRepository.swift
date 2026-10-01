import Foundation

/// Channel catalog. Mirrors Android `IptvRepository`.
public protocol IptvRepository: Sendable {
  func authenticate() async -> Bool
  func channels() async throws -> [IptvChannel]
  func refreshChannels() async throws -> [IptvChannel]
  func searchChannels(query: String, limit: Int) async throws -> [IptvChannel]
  func streamUrl(forChannelId channelId: String) async throws -> URL
  func streamHeaders(forChannelId channelId: String) async throws -> [String: String]
  func guide(forChannelId channelId: String) async -> ChannelGuide?
  func clearMemoryCache() async
}

extension IptvRepository {
  public func streamHeaders(forChannelId channelId: String) async throws -> [String: String] {
    try await channels().first(where: { $0.id == channelId })?.streamHeaders ?? [:]
  }
  public func searchChannels(query: String, limit: Int = 50) async throws -> [IptvChannel] {
    let all = try await channels()
    return all.filter {
      $0.name.localizedCaseInsensitiveContains(query)
        || $0.category.localizedCaseInsensitiveContains(query)
        || $0.number.localizedCaseInsensitiveContains(query)
    }.prefix(limit).map { $0 }
  }
  public func clearMemoryCache() async {}
}
