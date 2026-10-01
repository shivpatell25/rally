import Foundation

/// Middleware selected in Settings. Mirrors Android `IptvProvider`.
public enum IptvProvider: String, Sendable, Hashable, Codable, CaseIterable {
  case stalker
  case xtream
  case m3u
}

/// A playable channel. Mirrors Android `IptvChannel`.
public struct IptvChannel: Sendable, Hashable, Codable, Identifiable {
  public let id: String
  public let number: String
  public let name: String
  public let category: String
  public let logoUrl: URL?
  public let streamUrl: URL?
  public let guide: ChannelGuide?
  public let supportsCatchUp: Bool
  public let archiveDurationHours: Int?
  public let streamHeaders: [String: String]?

  public init(
    id: String, number: String, name: String, category: String,
    logoUrl: URL? = nil, streamUrl: URL? = nil, guide: ChannelGuide? = nil,
    supportsCatchUp: Bool = false, archiveDurationHours: Int? = nil,
    streamHeaders: [String: String]? = nil
  ) {
    self.id = id
    self.number = number
    self.name = name
    self.category = category
    self.logoUrl = logoUrl
    self.streamUrl = streamUrl
    self.guide = guide
    self.supportsCatchUp = supportsCatchUp
    self.archiveDurationHours = archiveDurationHours
    self.streamHeaders = streamHeaders
  }
}

public struct EpgProgram: Sendable, Hashable, Codable {
  public let title: String
  public let description: String?
  public let startTime: Date?
  public let endTime: Date?

  public init(
    title: String, description: String? = nil, startTime: Date? = nil, endTime: Date? = nil
  ) {
    self.title = title
    self.description = description
    self.startTime = startTime
    self.endTime = endTime
  }
}

public struct ChannelGuide: Sendable, Hashable, Codable {
  public let now: EpgProgram?
  public let next: EpgProgram?
  public let fetchedAt: Date

  public init(now: EpgProgram? = nil, next: EpgProgram? = nil, fetchedAt: Date = Date()) {
    self.now = now
    self.next = next
    self.fetchedAt = fetchedAt
  }
}
