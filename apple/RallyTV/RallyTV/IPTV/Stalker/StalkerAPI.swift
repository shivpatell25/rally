import Foundation

/// Stalker/Ministra middleware client. Ports `StalkerApi` + handshake behavior.
/// Transport: GET server/load.php with type/action/query + Bearer token header.
public struct StalkerAPI: Sendable {
  public let http: HTTPClient
  public let portalURL: URL
  public let macAddress: String
  public let serialNumber: String
  public let deviceId: String

  public init(
    http: HTTPClient, portalURL: URL, macAddress: String, serialNumber: String = "",
    deviceId: String = ""
  ) {
    self.http = http
    self.portalURL = portalURL
    self.macAddress = macAddress
    self.serialNumber = serialNumber
    self.deviceId = deviceId
  }

  private func loadURL(params: [String: String]) -> URL? {
    var base = portalURL
    if !base.path.hasSuffix("load.php") {
      if base.path.hasSuffix("/c") { base.deleteLastPathComponent() }
      base.appendPathComponent("server/load.php")
    }
    var components = URLComponents(url: base, resolvingAgainstBaseURL: false)
    components?.queryItems = params.map { URLQueryItem(name: $0.key, value: $0.value) }
    return components?.url
  }

  private var authHeaders: [String: String] {
    [
      "Cookie":
        "mac=\(macAddress.addingPercentEncoding(withAllowedCharacters:.alphanumerics) ?? macAddress); stb_lang=en; timezone=America%2FNew_York",
      "User-Agent":
        "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 MAG250 stbapp ver: 4 rev: 1812 Mobile Safari/533.3",
    ]
  }

  public func handshake(token: String?) async throws -> String? {
    guard let url = loadURL(params: ["type": "stb", "action": "handshake"]) else { return nil }
    let response: StalkerEnvelope<StalkerHandshakeJS> = try await http.json(
      url: url, headers: authHeaders)
    if let error = response.error, !error.isEmpty {
      RallyLogger.iptv.error("stalker handshake rejected by provider")
      return nil
    }
    return response.js?.token
  }

  public func profile(token: String) async throws -> Bool {
    guard
      let url = loadURL(params: [
        "type": "stb", "action": "get_profile", "sn": serialNumber, "device_id": deviceId,
        "stb_type": "MAG250",
      ])
    else { return false }
    let response: StalkerEnvelope<StalkerEmptyJS> = try await http.json(
      url: url,
      headers: authHeaders.merging(["Authorization": StreamHeaders.normalizedBearerToken(token)]) {
        _, new in new
      }
    )
    return response.error?.isEmpty ?? true
  }

  public func allChannels(token: String) async throws -> [StalkerChannelDTO] {
    guard let url = loadURL(params: ["type": "itv", "action": "get_all_channels"]) else {
      return []
    }
    let response: StalkerEnvelope<StalkerChannelListJS> = try await http.json(
      url: url,
      headers: authHeaders.merging(["Authorization": StreamHeaders.normalizedBearerToken(token)]) {
        _, new in new
      }
    )
    return response.js?.data ?? []
  }

  public func genres(token: String) async throws -> [StalkerGenreDTO] {
    guard let url = loadURL(params: ["type": "itv", "action": "get_genres"]) else { return [] }
    let response: StalkerEnvelope<[StalkerGenreDTO]> = try await http.json(
      url: url,
      headers: authHeaders.merging(["Authorization": StreamHeaders.normalizedBearerToken(token)]) {
        _, new in new
      }
    )
    return response.js ?? []
  }

  public func shortEPG(token: String, channelId: String, size: Int = 2) async throws
    -> [StalkerEpgDTO]
  {
    guard
      let url = loadURL(params: [
        "type": "itv", "action": "get_short_epg", "ch_id": channelId, "size": String(size),
      ])
    else { return [] }
    let response: StalkerEnvelope<[StalkerEpgDTO]> = try await http.json(
      url: url,
      headers: authHeaders.merging(["Authorization": StreamHeaders.normalizedBearerToken(token)]) {
        _, new in new
      }
    )
    return response.js ?? []
  }

  public func createLink(token: String, cmd: String) async throws -> URL? {
    guard let url = loadURL(params: ["type": "itv", "action": "create_link", "cmd": cmd]) else {
      return nil
    }
    let response: StalkerEnvelope<StalkerLinkJS> = try await http.json(
      url: url,
      headers: authHeaders.merging(["Authorization": StreamHeaders.normalizedBearerToken(token)]) {
        _, new in new
      }
    )
    guard let cmd = response.js?.cmd, !cmd.isEmpty else { return nil }
    // cmd is "ffmpeg http://..." — the URL follows the first space.
    let parts = cmd.split(separator: " ", maxSplits: 1).map(String.init)
    let raw = parts.count == 2 ? parts[1] : cmd
    guard let link = URL(string: raw), ["http", "https"].contains(link.scheme?.lowercased() ?? ""),
      let host = link.host, !host.isEmpty, host.lowercased() != "localhost"
    else { return nil }
    return link
  }
}

public struct StalkerEnvelope<T: Decodable>: Decodable, Sendable where T: Sendable {
  public let js: T?
  public let error: String?
}

public struct StalkerHandshakeJS: Decodable, Sendable {
  public let token: String?
}

public struct StalkerEmptyJS: Decodable, Sendable {}

public struct StalkerChannelListJS: Decodable, Sendable {
  public let data: [StalkerChannelDTO]
  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    data = try c.decodeIfPresent([StalkerChannelDTO].self, forKey: .data) ?? []
  }
  enum CodingKeys: String, CodingKey { case data }
}

public struct StalkerChannelDTO: Decodable, Sendable {
  public let id: String
  public let name: String
  public let number: String
  public let logo: String?
  public let genreId: String?
  public let cmd: String

  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    if let s = try? c.decode(String.self, forKey: .id) {
      id = s
    } else if let n = try? c.decode(Int.self, forKey: .id) {
      id = String(n)
    } else {
      id = ""
    }
    name = (try? c.decode(String.self, forKey: .name)) ?? ""
    if let s = try? c.decode(String.self, forKey: .number) {
      number = s
    } else if let n = try? c.decode(Int.self, forKey: .number) {
      number = String(n)
    } else {
      number = ""
    }
    logo = try c.decodeIfPresent(String.self, forKey: .logo)
    genreId = try c.decodeIfPresent(String.self, forKey: .genreId)
    cmd = (try? c.decode(String.self, forKey: .cmd)) ?? ""
  }
  enum CodingKeys: String, CodingKey {
    case id, name, number, logo, cmd
    case genreId = "tv_genre_id"
  }
}

public struct StalkerGenreDTO: Decodable, Sendable {
  public let id: String?
  public let title: String?
}

public struct StalkerEpgDTO: Decodable, Sendable {
  public let name: String?
  public let descr: String?
  public let startTimestamp: Int?
  public let stopTimestamp: Int?

  enum CodingKeys: String, CodingKey {
    case name, descr
    case startTimestamp = "start_timestamp"
    case stopTimestamp = "stop_timestamp"
  }
}

public struct StalkerLinkJS: Decodable, Sendable {
  public let cmd: String?
}
