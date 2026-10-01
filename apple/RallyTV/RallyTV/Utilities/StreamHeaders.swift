import Foundation

/// Header allowlist for third-party stream URLs. Ports Android `sanitizedStreamHeaders`.
public enum StreamHeaders {
  private static let allowed: Set<String> = [
    "accept", "accept-language", "authorization", "cookie",
    "origin", "referer", "user-agent",
  ]

  public static func sanitized(_ headers: [String: String]?) -> [String: String] {
    guard let headers else { return [:] }
    var out: [String: String] = [:]
    for (name, value) in headers {
      let lower = name.lowercased()
      guard allowed.contains(lower),
        name.count <= 64, value.count <= 4096,
        !name.contains("\n"), !name.contains("\r"),
        !value.contains("\n"), !value.contains("\r")
      else { continue }
      out[name] = value
    }
    return out
  }

  public static func normalizedBearerToken(_ token: String) -> String {
    token.lowercased().hasPrefix("bearer ") ? token : "Bearer \(token)"
  }
}
