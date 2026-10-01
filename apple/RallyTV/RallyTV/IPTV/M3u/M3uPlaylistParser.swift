import CryptoKit
import Foundation

enum M3uPlaylistError: LocalizedError {
  case invalid, empty, oversized, localHLS, missingChannel, unavailableFile, storage
  var errorDescription: String? {
    switch self {
    case .invalid: return "This is not an M3U/M3U8 playlist. Check the URL or import another file."
    case .empty:
      return "No playable channels found. The playlist must contain HTTP or HTTPS stream URLs."
    case .oversized: return "The playlist exceeds the 16 MB or 20,000 channel limit."
    case .localHLS:
      return
        "For a single HLS stream, enter its HTTP or HTTPS URL. Imported files must contain a channel playlist."
    case .missingChannel: return "This playlist channel is no longer available. Refresh Live TV."
    case .unavailableFile: return "The imported playlist is no longer available. Import it again."
    case .storage: return "The playlist could not be saved securely. Try again."
    }
  }
}

/// Extended M3U is a catalog; an HLS manifest remains one stream with all variants/tracks.
enum M3uPlaylistParser {
  static let maximumBytes = 16 * 1024 * 1024
  static func parse(_ text: String, source: URL?, name: String = "") throws -> [IptvChannel] {
    guard text.utf8.count <= maximumBytes else { throw M3uPlaylistError.oversized }
    let lines = text.replacingOccurrences(of: "\u{FEFF}", with: "").components(
      separatedBy: .newlines
    )
    .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
    guard
      lines.first?.uppercased().hasPrefix("#EXTM3U") == true
        || lines.contains(where: { $0.uppercased().hasPrefix("#EXTINF:") })
    else {
      throw M3uPlaylistError.invalid
    }
    if lines.contains(where: { $0.uppercased().hasPrefix("#EXT-X-") }) {
      guard let source, let url = resolve(source.absoluteString, source: nil) else {
        throw M3uPlaylistError.localHLS
      }
      return [
        channel(
          url, name: name.isEmpty ? "Live Stream" : name, category: "Live TV", logo: nil,
          number: "1", headers: [:])
      ]
    }
    var result: [IptvChannel] = []
    var ids = Set<String>()
    var attributes: [String: String] = [:]
    var headers: [String: String] = [:]
    var title = ""
    var group = ""
    for line in lines {
      let upper = line.uppercased()
      if upper.hasPrefix("#EXTINF:") {
        let comma = separator(line)
        attributes = attrs(String(line[..<(comma ?? line.endIndex)]))
        title =
          comma.map { String(line[line.index(after: $0)...]).trimmingCharacters(in: .whitespaces) }
          ?? ""
        group = attributes["group-title"] ?? ""
        headers = [:]
        put(&headers, key: "User-Agent", value: attributes["user-agent"] ?? "")
        put(&headers, key: "Referer", value: attributes["referrer"] ?? "")
      } else if upper.hasPrefix("#EXTGRP:") {
        group = String(line.dropFirst(8)).trimmingCharacters(in: .whitespaces)
      } else if upper.hasPrefix("#EXTVLCOPT:") {
        let parts = line.dropFirst(11).split(
          separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
        if parts.count == 2, let key = headerName(String(parts[0])) {
          put(&headers, key: key, value: String(parts[1]).trimmingCharacters(in: .whitespaces))
        }
      } else if !line.hasPrefix("#") {
        let parts = line.split(separator: "|", maxSplits: 1, omittingEmptySubsequences: false)
        if let url = resolve(String(parts[0]), source: source) {
          if parts.count > 1 {
            for pair in parts[1].split(separator: "&") {
              let entry = pair.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
              if entry.count == 2, let key = headerName(String(entry[0])) {
                put(
                  &headers, key: key,
                  value: String(entry[1]).replacingOccurrences(of: "+", with: " ")
                    .removingPercentEncoding ?? "")
              }
            }
          }
          let ordinal = String(result.count + 1)
          let item = channel(
            url,
            name: title.isEmpty
              ? (attributes["tvg-name"].flatMap { $0.isEmpty ? nil : $0 } ?? "Channel \(ordinal)")
              : title,
            category: group.isEmpty ? "Live TV" : group,
            logo: resolve(attributes["tvg-logo"] ?? "", source: source),
            number: attributes["tvg-chno"].flatMap { $0.isEmpty ? nil : $0 } ?? ordinal,
            headers: headers)
          if ids.insert(item.id).inserted { result.append(item) }
          guard result.count <= 20_000 else { throw M3uPlaylistError.oversized }
        }
        attributes = [:]
        headers = [:]
        title = ""
        group = ""
      }
    }
    guard !result.isEmpty else { throw M3uPlaylistError.empty }
    return result
  }
  private static func channel(
    _ url: URL, name: String, category: String, logo: URL?, number: String,
    headers: [String: String]
  ) -> IptvChannel {
    let identity =
      url.absoluteString
      + headers.keys.sorted().map { "\($0)=\(headers[$0]!)" }.joined(separator: ", ")
    let hash = SHA256.hash(data: Data(identity.utf8)).prefix(12).map { String(format: "%02x", $0) }
      .joined()
    return IptvChannel(
      id: "m3u:" + hash, number: number, name: name, category: category, logoUrl: logo,
      streamUrl: url, streamHeaders: headers)
  }
  private static func resolve(_ value: String, source: URL?) -> URL? {
    let value = value.trimmingCharacters(in: .whitespaces)
    guard !value.isEmpty else { return nil }
    let base = source.flatMap {
      ["http", "https"].contains($0.scheme?.lowercased() ?? "") ? $0 : nil
    }
    guard let url = URL(string: value, relativeTo: base)?.absoluteURL,
      ["http", "https"].contains(url.scheme?.lowercased() ?? ""), url.host?.isEmpty == false
    else { return nil }
    if let port = url.port, !(1...65535).contains(port) { return nil }
    return url
  }
  private static func separator(_ text: String) -> String.Index? {
    var quote: Character?
    for index in text.indices {
      let char = text[index]
      if char == quote {
        quote = nil
      } else if quote == nil && (char == "'" || char == "\"") {
        quote = char
      } else if quote == nil && char == "," {
        return index
      }
    }
    return nil
  }
  private static let pattern = try! NSRegularExpression(
    pattern: #"([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s]+))"#)
  private static func attrs(_ text: String) -> [String: String] {
    var output: [String: String] = [:]
    for match in pattern.matches(in: text, range: NSRange(text.startIndex..., in: text)) {
      guard let key = Range(match.range(at: 1), in: text) else { continue }
      output[text[key].lowercased()] =
        (2...4).compactMap { Range(match.range(at: $0), in: text).map { String(text[$0]) } }.first
        ?? ""
    }
    return output
  }
  private static func headerName(_ value: String) -> String? {
    switch value.lowercased() {
    case "http-user-agent", "user-agent": return "User-Agent"
    case "http-referrer", "http-referer", "referer", "referrer": return "Referer"
    case "http-origin", "origin": return "Origin"
    default: return nil
    }
  }
  private static func put(_ headers: inout [String: String], key: String, value: String) {
    guard !value.isEmpty,
      !value.unicodeScalars.contains(where: { $0.value < 32 || $0.value == 127 })
    else { return }
    headers[key] = value
  }
}

/// tvOS has no general document picker. A user-started phone/computer transfer supplies
/// catalog files, restricted to Rally's own directory and excluded from backups.
enum M3uPlaylistFiles {
  private static var directory: URL {
    FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
      .appendingPathComponent("RallyPlaylists", isDirectory: true)
  }
  static func isOwned(_ url: URL) -> Bool {
    url.isFileURL
      && url.standardizedFileURL.deletingLastPathComponent() == directory.standardizedFileURL
      && url.pathExtension == "m3u"
      && url.resolvingSymlinksInPath() == url.standardizedFileURL
  }
  static func importCatalog(_ data: Data, name: String, settings: SettingsStore) throws -> Int {
    guard data.count <= M3uPlaylistParser.maximumBytes else { throw M3uPlaylistError.oversized }
    guard let text = String(data: data, encoding: .utf8) else { throw M3uPlaylistError.invalid }
    let catalog = try M3uPlaylistParser.parse(text, source: nil)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    let url = directory.appendingPathComponent(UUID().uuidString + ".m3u")
    try data.write(to: url, options: .atomic)
    var excluded = url
    var values = URLResourceValues()
    values.isExcludedFromBackup = true
    try excluded.setResourceValues(values)
    let previous = URL(string: settings.m3uPlaylistUrl)
    settings.m3uPlaylistUrl = url.absoluteString
    guard settings.m3uPlaylistUrl == url.absoluteString else {
      removeOwned(url)
      throw M3uPlaylistError.storage
    }
    settings.m3uPlaylistName = name
    settings.provider = .m3u
    settings.setupComplete = true
    removeOwned(previous)
    return catalog.count
  }
  static func removeOwned(_ url: URL?) {
    if let url, isOwned(url) { try? FileManager.default.removeItem(at: url) }
  }
}
