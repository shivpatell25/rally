import Foundation

/// Xtream Codes client over `player_api.php`. Ports `XtreamApi` + `XtreamUrlBuilder` usage.
public struct XtreamAPI: Sendable {
  public let http: HTTPClient

  public init(http: HTTPClient) {
    self.http = http
  }

  public func account(url: URL) async throws -> XtreamUserInfo {
    let response: XtreamPlayerApiResponse = try await http.json(url: url)
    return response.userInfo
  }

  public func liveStreams(url: URL) async throws -> [XtreamStreamDTO] {
    // get_live_streams returns a bare JSON array.
    try await http.json(url: url)
  }

  public func shortEPG(url: URL) async throws -> XtreamEpgResponse {
    try await http.json(url: url)
  }
}

public struct XtreamPlayerApiResponse: Decodable, Sendable {
  public let userInfo: XtreamUserInfo
  enum CodingKeys: String, CodingKey { case userInfo = "user_info" }
  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    userInfo = try c.decodeIfPresent(XtreamUserInfo.self, forKey: .userInfo) ?? XtreamUserInfo()
  }
}

public struct XtreamUserInfo: Decodable, Sendable {
  public let auth: String?
  public let status: String?

  public init(auth: String? = nil, status: String? = nil) {
    self.auth = auth
    self.status = status
  }

  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    if let s = try? c.decode(String.self, forKey: .auth) {
      auth = s
    } else if let n = try? c.decode(Int.self, forKey: .auth) {
      auth = String(n)
    } else {
      auth = nil
    }
    if let s = try? c.decode(String.self, forKey: .status) {
      status = s
    } else if let n = try? c.decode(Int.self, forKey: .status) {
      status = String(n)
    } else {
      status = nil
    }
  }
  enum CodingKeys: String, CodingKey { case auth, status }

  public var isAccepted: Bool {
    guard let auth else { return true }
    return auth == "1" || auth.lowercased() == "true"
  }

  public var isActive: Bool {
    guard let status, !status.isEmpty else { return true }
    return status.lowercased() == "active" || status == "1"
  }
}

public struct XtreamStreamDTO: Decodable, Sendable {
  public let streamId: String
  public let name: String
  public let streamIcon: String?
  public let categoryId: String?

  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    if let s = try? c.decode(String.self, forKey: .streamId) {
      streamId = s
    } else if let n = try? c.decode(Int.self, forKey: .streamId) {
      streamId = String(n)
    } else {
      streamId = ""
    }
    name = (try? c.decode(String.self, forKey: .name)) ?? ""
    streamIcon = try c.decodeIfPresent(String.self, forKey: .streamIcon)
    if let s = try? c.decode(String.self, forKey: .categoryId) {
      categoryId = s
    } else if let n = try? c.decode(Int.self, forKey: .categoryId) {
      categoryId = String(n)
    } else {
      categoryId = nil
    }
  }
  enum CodingKeys: String, CodingKey {
    case name
    case streamId = "stream_id"
    case streamIcon = "stream_icon"
    case categoryId = "category_id"
  }
}

public struct XtreamEpgResponse: Decodable, Sendable {
  public let epgListings: [XtreamEpgDTO]
  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    epgListings = try c.decodeIfPresent([XtreamEpgDTO].self, forKey: .epgListings) ?? []
  }
  enum CodingKeys: String, CodingKey { case epgListings = "epg_listings" }
}

public struct XtreamEpgDTO: Decodable, Sendable {
  public let title: String?
  public let description: String?
  public let start: String?
  public let end: String?
}
