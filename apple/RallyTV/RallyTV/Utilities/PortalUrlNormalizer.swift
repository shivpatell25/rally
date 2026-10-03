import Foundation

/// One source of truth for user-entered portal and add-on URLs.
/// Ports Android `PortalUrlNormalizer`.
public enum PortalUrlNormalizer {
  public static func normalizePortal(_ rawValue: String) -> String {
    var value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !value.isEmpty else { return "" }
    if !value.lowercased().hasPrefix("http://") && !value.lowercased().hasPrefix("https://") {
      value = "http://" + value
    }
    value = repairRemoteColonTypo(value)
    value = stripSuffixes(value, ["/server/load.php", "/load.php", "/"])
    guard var components = URLComponents(string: value) else { return "" }
    components.fragment = nil
    guard let url = components.url else { return "" }
    return url.absoluteString.hasSuffix("/")
      ? String(url.absoluteString.dropLast()) : url.absoluteString
  }

  public static func normalizeAddon(_ rawValue: String) -> URL? {
    var value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !value.isEmpty else { return nil }
    if value.lowercased().hasPrefix("stremio://") {
      value = "https://" + value.dropFirst("stremio://".count)
    }
    if !value.lowercased().hasPrefix("http://") && !value.lowercased().hasPrefix("https://") {
      value = "https://" + value
    }
    guard var components = URLComponents(string: value),
      ["http", "https"].contains(components.scheme?.lowercased() ?? ""),
      components.host?.isEmpty == false else { return nil }
    components.fragment = nil
    if !components.path.hasSuffix("manifest.json") {
      components.path = (components.path as NSString).appendingPathComponent("manifest.json")
    }
    return components.url
  }

  /// Xtream hosts use non-standard ports and domains — preserved exactly.
  public static func normalizeXtreamServer(_ rawValue: String) -> String {
    var value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !value.isEmpty else { return "" }
    if !value.lowercased().hasPrefix("http://") && !value.lowercased().hasPrefix("https://") {
      value = "http://" + value
    }
    value = stripSuffixes(value, ["/player_api.php", "/get.php", "/"])
    guard var components = URLComponents(string: value) else { return "" }
    components.query = nil
    components.fragment = nil
    guard let url = components.url else { return "" }
    return url.absoluteString.hasSuffix("/")
      ? String(url.absoluteString.dropLast()) : url.absoluteString
  }

  private static func stripSuffixes(_ value: String, _ suffixes: [String]) -> String {
    var result = value
    for suffix in suffixes where result.hasSuffix(suffix) {
      result = String(result.dropLast(suffix.count))
    }
    if result.hasSuffix("/") { result = String(result.dropLast()) }
    return result
  }

  private static func repairRemoteColonTypo(_ value: String) -> String {
    guard let schemeEnd = value.range(of: "://") else { return value }
    let authorityStart = schemeEnd.upperBound
    let pathStart = value[authorityStart...].firstIndex(of: "/") ?? value.endIndex
    let authority = String(value[authorityStart..<pathStart])
    if authority.hasPrefix("[") || authority.contains("@") { return value }
    guard let colon = authority.lastIndex(of: ":") else { return value }
    let suffix = String(authority[authority.index(after: colon)...])
    if suffix.isEmpty || suffix.allSatisfy(\.isNumber) { return value }
    let repaired = String(authority[..<colon]) + "." + suffix
    return String(value[..<authorityStart]) + repaired + String(value[pathStart...])
  }
}
