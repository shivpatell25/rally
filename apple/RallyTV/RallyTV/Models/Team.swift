import Foundation

/// A team identity. Mirrors Android `Team`.
public struct Team: Sendable, Hashable, Codable, Identifiable {
  public let id: String
  public let name: String
  public let abbreviation: String
  public let logoUrl: URL?
  public let colors: [String]
  public let shortName: String?

  public init(
    id: String,
    name: String,
    abbreviation: String,
    logoUrl: URL? = nil,
    colors: [String] = [],
    shortName: String? = nil
  ) {
    self.id = id
    self.name = name
    self.abbreviation = abbreviation
    self.logoUrl = logoUrl
    self.colors = colors
    self.shortName = shortName
  }
}
