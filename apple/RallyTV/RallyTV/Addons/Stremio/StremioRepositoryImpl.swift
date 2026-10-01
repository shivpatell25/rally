import Foundation

/// Add-on stream discovery. Ports `StremioRepositoryImpl`:
/// parallel per-addon resolution (partial results kept), 30-min manifest TTL,
/// 90-s stream TTL, quality/bitrate parsing, HTML-page filtering.
public actor StremioRepositoryImpl: StremioRepository {
  private let http: HTTPClient
  private let settings: SettingsStore

  private var manifestCache: [String: (manifest: StremioManifest, at: Date)] = [:]
  private var streamCache: [String: (streams: [StremioStreamOption], at: Date)] = [:]

  private static let manifestTTL: TimeInterval = 30 * 60
  private static let streamTTL: TimeInterval = 90
  private static let addonTimeout: TimeInterval = 20

  public init(http: HTTPClient, settings: SettingsStore) {
    self.http = http
    self.settings = settings
  }

  public func streams(for event: SportEvent) async -> [StremioStreamOption] {
    let addons = settings.stremioAddonUrls
    guard !addons.isEmpty else { return [] }
    let key = "\(event.id)|\(addons.map(\.absoluteString).joined(separator: "|"))"
    if let cached = streamCache[key],
      !cached.streams.isEmpty,
      Date().timeIntervalSince(cached.at) < Self.streamTTL
    {
      return cached.streams
    }
    var collected: [StremioStreamOption] = []
    await withTaskGroup(of: [StremioStreamOption].self) { group in
      for addon in addons {
        group.addTask {
          await self.resolve(base: addon, event: event, query: event.awayTeam?.name ?? event.name)
        }
      }
      for await streams in group { collected.append(contentsOf: streams) }
    }
    let deduped = collected.uniqued(by: \.streamUrl)
    if !deduped.isEmpty {
      streamCache[key] = (deduped, Date())
    } else {
      streamCache.removeValue(forKey: key)
    }
    return deduped
  }

  public func searchStreams(query: String) async -> [StremioStreamOption] {
    var result: [StremioStreamOption] = []
    await withTaskGroup(of: [StremioStreamOption].self) { group in
      for addon in settings.stremioAddonUrls {
        group.addTask { await self.resolve(base: addon, event: nil, query: query) }
      }
      for await streams in group { result += streams }
    }
    return result.uniqued(by: \.streamUrl)
  }

  // MARK: - Addon resolution

  private func resolve(base: URL, event: SportEvent?, query: String) async -> [StremioStreamOption]
  {
    await withTaskGroup(of: [StremioStreamOption].self) { group in
      group.addTask { await self.discover(base: base, event: event, query: query) }
      group.addTask {
        do { try await Task.sleep(for: .seconds(Self.addonTimeout)) } catch { return [] }
        return []
      }
      let result = await group.next() ?? []
      group.cancelAll()
      return result
    }
  }
  private func discover(base: URL, event: SportEvent?, query: String) async -> [StremioStreamOption]
  {
    var root = base
    if root.path.hasSuffix("manifest.json") { root.deleteLastPathComponent() }
    let manifestURL = root.appendingPathComponent("manifest.json")
    let manifest: StremioManifest
    do {
      if let c = manifestCache[manifestURL.absoluteString],
        Date().timeIntervalSince(c.at) < Self.manifestTTL
      {
        manifest = c.manifest
      } else {
        manifest = try await http.json(url: manifestURL, timeout: Self.addonTimeout)
        manifestCache[manifestURL.absoluteString] = (manifest, Date())
      }
    } catch { return [] }
    var catalogs = manifest.catalogs.filter { c in
      let text = ((c.id ?? "") + " " + (c.name ?? "")).lowercased()
      guard let event else { return true }
      let sport = event.sport == "football" ? "american_football" : event.sport
      return ["live", "today", "schedule", sport, event.league.lowercased()].contains {
        text.contains($0)
      }
    }
    if catalogs.isEmpty || manifest.catalogs.count <= 3 {
      catalogs = manifest.catalogs.filter {
        ["sport", "tv", "events"].contains($0.type ?? "sport")
      }
    }
    var metas: [StremioMetaItem] = []
    for c in catalogs.prefix(8) {
      guard !Task.isCancelled, let id = c.id else { break }
      let type = c.type ?? "sport"
      let normal: StremioCatalogResponse? = try? await http.json(
        url: root.appendingPathComponent("catalog/\(type)/\(id).json"), timeout: Self.addonTimeout)
      let matches =
        normal?.metas.filter { meta in
          event.map { TextMatching.textMatchesEvent(meta.name ?? "", event: $0) }
            ?? (meta.name?.localizedCaseInsensitiveContains(query) == true)
        } ?? []
      metas += matches
      if matches.isEmpty {
        let allowed = CharacterSet.urlPathAllowed.subtracting(CharacterSet(charactersIn: "&=/?#%"))
        let encoded = query.addingPercentEncoding(withAllowedCharacters: allowed) ?? ""
        var searchURL = URLComponents(
          url: root.appendingPathComponent("catalog/\(type)/\(id)"), resolvingAgainstBaseURL: false)!
        searchURL.percentEncodedPath += "/search=\(encoded).json"
        if let found: StremioCatalogResponse = try? await http.json(
          url: searchURL.url!,
          timeout: Self.addonTimeout)
        {
          metas += found.metas.filter { meta in
            event.map { TextMatching.textMatchesEvent(meta.name ?? "", event: $0) } ?? true
          }
        }
      }
    }
    var result: [StremioStreamOption] = []
    for meta in metas.uniqued(by: \.id).prefix(16) {
      guard !Task.isCancelled else { break }
      if let response: StremioStreamResponse = try? await http.json(
        url: root.appendingPathComponent("stream/\(meta.type ?? "sport")/\(meta.id).json"),
        timeout: Self.addonTimeout)
      {
        result += response.streams.compactMap {
          Self.option(dto: $0, addonName: manifest.name ?? root.host)
        }
      }
    }
    return result
  }

  static func option(dto: StremioStreamDTO, addonName: String?) -> StremioStreamOption? {
    if let ytId = dto.ytId, !ytId.isEmpty {
      // YouTube-backed entries need the YouTube player — not direct-playable.
      return StremioStreamOption(
        title: dto.title ?? dto.name ?? ytId,
        description: dto.streamDescription,
        streamUrl: URL(string: "https://www.youtube.com/watch?v=\(ytId)")!,
        addonName: addonName, isDirectPlayable: false
      )
    }
    if let external = dto.externalUrl, URL(string: external) != nil,
      dto.url == nil || dto.url!.isEmpty
    {
      return StremioStreamOption(
        title: dto.title ?? dto.name ?? external,
        description: dto.streamDescription,
        streamUrl: URL(string: external)!,
        addonName: addonName, isDirectPlayable: false
      )
    }
    guard let raw = dto.url, let url = URL(string: raw),
      url.scheme?.hasPrefix("http") == true
    else { return nil }
    if dto.behaviorHints?.notWebReady == true { return nil }
    let text = [dto.title, dto.streamDescription, dto.name].compactMap { $0 }.joined(separator: " ")
    let quality = QualityParsing.parseQuality(fromChannelName: text)
    let headers = dto.behaviorHints?.proxyHeaders?.request
    return StremioStreamOption(
      title: dto.title ?? dto.name ?? url.lastPathComponent,
      description: dto.streamDescription,
      streamUrl: url,
      quality: quality.resolution,
      bitrate: bitrate(in: text),
      addonName: addonName,
      headers: headers.map(StreamHeaders.sanitized),
      isDirectPlayable: true
    )
  }

  private static func bitrate(in text: String) -> String? {
    text.range(
      of: #"\d+(?:\.\d+)?\s*(?:MBPS|MB/S|KBPS)"#, options: [.regularExpression, .caseInsensitive]
    )
    .map { String(text[$0]).uppercased() }
  }
}

extension Array {
  fileprivate func uniqued<T: Hashable>(by key: (Element) -> T) -> [Element] {
    var seen = Set<T>()
    return filter { seen.insert(key($0)).inserted }
  }
}
