import Foundation

/// Channel persistence seam. The foundation ships an in-memory actor with the
/// same interface the SwiftData store will implement — swapping it later is a
/// one-line container change, not a call-site migration.
public protocol ChannelStore: Sendable {
  func allChannels() async -> [IptvChannel]
  func searchChannels(query: String, limit: Int) async -> [IptvChannel]
  func replaceAll(_ channels: [IptvChannel]) async
  func clear() async
}

public actor InMemoryChannelStore: ChannelStore {
  private var channels: [IptvChannel] = []

  public init() {}

  public func allChannels() async -> [IptvChannel] { channels }

  public func searchChannels(query: String, limit: Int) async -> [IptvChannel] {
    channels.filter {
      $0.name.localizedCaseInsensitiveContains(query)
        || $0.category.localizedCaseInsensitiveContains(query)
        || $0.number.localizedCaseInsensitiveContains(query)
    }.prefix(limit).map { $0 }
  }

  public func replaceAll(_ channels: [IptvChannel]) async {
    self.channels = channels
  }

  public func clear() async {
    channels = []
  }
}
