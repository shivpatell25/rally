import Foundation

/// Stremio add-on wire models. Ports Android `StremioApi.kt` DTOs.
public struct StremioManifest: Decodable, Sendable {
  public let id: String?
  public let name: String?
  public let manifestDescription: String?
  public let version: String?
  public let types: [String]
  public let catalogs: [StremioCatalogDesc]

  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    id = try c.decodeIfPresent(String.self, forKey: .id)
    name = try c.decodeIfPresent(String.self, forKey: .name)
    manifestDescription = try c.decodeIfPresent(String.self, forKey: .manifestDescription)
    version = try c.decodeIfPresent(String.self, forKey: .version)
    types = try c.decodeIfPresent([String].self, forKey: .types) ?? []
    catalogs = try c.decodeIfPresent([StremioCatalogDesc].self, forKey: .catalogs) ?? []
  }
  enum CodingKeys: String, CodingKey {
    case id, name, version, types, catalogs
    case manifestDescription = "description"
  }
}

public struct StremioCatalogDesc: Decodable, Sendable {
  public let type: String?
  public let id: String?
  public let name: String?
  public let extra: [StremioCatalogExtra]?
}

public struct StremioCatalogExtra: Decodable, Sendable {
  public let name: String
  public let isRequired: Bool?
  public let options: [String]?
}

public struct StremioCatalogResponse: Decodable, Sendable {
  public let metas: [StremioMetaItem]
  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    metas = try c.decodeIfPresent([StremioMetaItem].self, forKey: .metas) ?? []
  }
  enum CodingKeys: String, CodingKey { case metas }
}

public struct StremioMetaItem: Decodable, Sendable {
  public let id: String
  public let type: String?
  public let name: String?
  public let poster: String?
  public let description: String?
}

public struct StremioStreamResponse: Decodable, Sendable {
  public let streams: [StremioStreamDTO]
  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    streams = try c.decodeIfPresent([StremioStreamDTO].self, forKey: .streams) ?? []
  }
  enum CodingKeys: String, CodingKey { case streams }
}

public struct StremioStreamDTO: Decodable, Sendable {
  public let name: String?
  public let title: String?
  public let streamDescription: String?
  public let url: String?
  public let externalUrl: String?
  public let ytId: String?
  public let behaviorHints: StremioBehaviorHints?

  enum CodingKeys: String, CodingKey {
    case name, title, url, externalUrl, ytId, behaviorHints
    case streamDescription = "description"
  }
}

public struct StremioBehaviorHints: Decodable, Sendable {
  public let proxyHeaders: StremioProxyHeaders?
  public let notWebReady: Bool?
}

public struct StremioProxyHeaders: Decodable, Sendable {
  public let request: [String: String]?
}
