import Foundation

/// Add-on stream discovery. Ports `StremioRepositoryImpl`:
/// parallel addon/catalog resolution (partial results kept), 30-min manifest TTL,
/// 90-s stream TTL, quality/bitrate parsing, HTML-page filtering.
public actor StremioRepositoryImpl: StremioRepository {
  private let http: HTTPClient
  private let settings: SettingsStore

  private var manifestCache: [String: (manifest: StremioManifest, at: Date)] = [:]
  private var streamCache: [String: (streams: [StremioStreamOption], at: Date)] = [:]

  private static let manifestTTL: TimeInterval = 30 * 60
  private static let streamTTL: TimeInterval = 90
  private let addonTimeout: TimeInterval

  public init(http: HTTPClient, settings: SettingsStore, discoveryTimeout: TimeInterval = 20) {
    self.http = http
    self.settings = settings
    self.addonTimeout = max(0.1, discoveryTimeout)
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

  private func resolve(base: URL, event: SportEvent?, query: String) async -> [StremioStreamOption] {
    let started = Date()
    let manifestURL = base.path.hasSuffix("manifest.json") ? base : base.appendingPathComponent("manifest.json")
    var root = manifestURL
    root.deleteLastPathComponent()
    let manifest: StremioManifest
    do {
      if let cached = manifestCache[manifestURL.absoluteString],
        Date().timeIntervalSince(cached.at) < Self.manifestTTL {
        manifest = cached.manifest
      } else {
        manifest = try await http.json(url: manifestURL, timeout: min(8, addonTimeout))
        manifestCache[manifestURL.absoluteString] = (manifest, Date())
      }
    } catch {
      Self.recordFailure("Manifest", error: error)
      return []
    }
    guard !Task.isCancelled else { return [] }
    let catalogs = Self.catalogs(manifest, event: event)
    if catalogs.isEmpty {
      RallyDiagnostics.shared.record("Stremio", code: "Addon has no discoverable catalogs")
      return []
    }
    let remaining = max(0.01, addonTimeout - Date().timeIntervalSince(started))
    var result: [StremioStreamOption] = []
    // Resolve catalogs concurrently with bounded fan-out. A slow catalog must not
    // discard streams already found by another catalog in the same addon.
    await withTaskGroup(of: [StremioStreamOption]?.self) { group in
      var iterator = catalogs.makeIterator()
      var pending = 0
      func enqueue(_ catalog: StremioCatalogDesc) {
        pending += 1
        group.addTask {
          await self.discover(catalog: catalog, root: root, manifest: manifest, event: event, query: query)
        }
      }
      for _ in 0..<4 {
        if let catalog = iterator.next() { enqueue(catalog) }
      }
      group.addTask {
        do { try await Task.sleep(for: .seconds(remaining)) } catch { return nil }
        return nil
      }
      while pending > 0, let completed = await group.next() {
        guard let streams = completed else {
          RallyDiagnostics.shared.record("Stremio", code: "Discovery timeout; keeping completed sources")
          break
        }
        result += streams
        pending -= 1
        if let catalog = iterator.next(), !Task.isCancelled { enqueue(catalog) }
      }
      group.cancelAll()
      // Cancelled catalog tasks also return any streams fetched before cancellation.
      for await streams in group { result += streams ?? [] }
    }
    let deduped = result.uniqued(by: \.streamUrl)
    RallyDiagnostics.shared.record("Stremio", code: "Checked \(catalogs.count) catalogs; \(deduped.count) sources, \(deduped.filter(\.isDirectPlayable).count) native candidates")
    return deduped
  }

  static func catalogs(_ manifest: StremioManifest, event: SportEvent?) -> [StremioCatalogDesc] {
    let all = manifest.catalogs.filter { $0.id?.isEmpty == false }
    guard let event else { return Array(all.prefix(16)) }
    let aliases: [String]
    switch event.league.uppercased() {
    case "NFL", "NCAAF": aliases = ["american", "nfl", "college", "ncaa"]
    case "NBA", "NCAAB": aliases = ["basket", "nba", "ncaab"]
    case "MLB": aliases = ["baseball", "mlb"]
    case "NHL": aliases = ["hockey", "nhl"]
    default: aliases = event.sport.lowercased().contains("soccer") ? ["soccer", "football"] : [event.sport.lowercased()]
    }
    let prioritized = all.filter {
      let text = (($0.id ?? "") + " " + ($0.name ?? "")).lowercased()
      return (["live", "today", "schedule", event.league.lowercased()] + aliases).contains { !($0.isEmpty) && text.contains($0) }
    }
    let generic = all.filter { ["sport", "sports", "tv", "events", "channel"].contains(($0.type ?? "sport").lowercased()) }
    // Preserve matched custom-type catalogs when adding generic fallbacks.
    let fallback = prioritized.isEmpty ? (generic.isEmpty ? all : generic) : (all.count <= 3 ? all : [])
    return Array((prioritized + fallback).uniqued { "\($0.type ?? "sport")|\($0.id ?? "")" }.prefix(16))
  }

  private func discover(catalog: StremioCatalogDesc, root: URL, manifest: StremioManifest,
    event: SportEvent?, query: String) async -> [StremioStreamOption] {
    guard let id = catalog.id, !Task.isCancelled else { return [] }
    let type = catalog.type ?? "sport"
    let extras = catalog.extra ?? []
    var required: [String: String] = [:]
    for extra in extras where extra.isRequired == true && extra.name != "search" {
      if extra.name == "date" {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        required[extra.name] = formatter.string(from: event?.startTime ?? Date())
      } else if let value = extra.options?.first {
        required[extra.name] = extra.options?.first { $0.localizedCaseInsensitiveContains(event?.league ?? query) } ?? value
      } else {
        RallyDiagnostics.shared.record("Stremio", code: "Catalog requires an unsupported filter")
        return []
      }
    }
    func matches(_ meta: StremioMetaItem) -> Bool {
      let text = [meta.name, meta.description].compactMap { $0 }.joined(separator: " ")
      return event.map { Self.matches(text, event: $0) } ?? text.localizedCaseInsensitiveContains(query)
    }
    var metas: [StremioMetaItem] = []
    if !extras.contains(where: { $0.name == "search" && $0.isRequired == true }) {
      do {
        let response: StremioCatalogResponse = try await http.json(
          url: Self.resourceURL(root: root, resource: "catalog", type: type, id: id, extras: required), timeout: min(8, addonTimeout))
        metas += response.metas.filter(matches)
      } catch { Self.recordFailure("Catalog", error: error) }
    }
    // Search only where supported (or for legacy manifests that omit `extra`).
    if metas.isEmpty && (catalog.extra == nil || extras.contains(where: { $0.name == "search" })) {
      let queries = event.map(Self.searchQueries) ?? [query]
      for search in queries where !Task.isCancelled {
        var values = required
        values["search"] = search
        do {
          let response: StremioCatalogResponse = try await http.json(
            url: Self.resourceURL(root: root, resource: "catalog", type: type, id: id, extras: values), timeout: min(6, addonTimeout))
          metas += response.metas.filter(matches)
          if !metas.isEmpty { break }
        } catch { Self.recordFailure("Search", error: error) }
      }
    }
    var result: [StremioStreamOption] = []
    await withTaskGroup(of: [StremioStreamOption].self) { group in
      var iterator = metas.uniqued(by: \.id).prefix(16).makeIterator()
      func enqueue(_ meta: StremioMetaItem) {
        group.addTask {
          guard !Task.isCancelled else { return [] }
          do {
            let response: StremioStreamResponse = try await self.http.json(
              url: Self.resourceURL(root: root, resource: "stream", type: meta.type ?? type, id: meta.id), timeout: min(8, self.addonTimeout))
            return response.streams.compactMap { Self.option(dto: $0, addonName: manifest.name ?? root.host) }
          } catch {
            Self.recordFailure("Stream", error: error)
            return []
          }
        }
      }
      for _ in 0..<2 { if let meta = iterator.next() { enqueue(meta) } }
      for await streams in group {
        result += streams
        if let meta = iterator.next(), !Task.isCancelled { enqueue(meta) }
      }
    }
    return result
  }

  static func resourceURL(root: URL, resource: String, type: String, id: String, extras: [String: String] = [:]) -> URL {
    let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
    func encode(_ value: String) -> String { value.addingPercentEncoding(withAllowedCharacters: allowed) ?? "" }
    var components = URLComponents(url: root, resolvingAgainstBaseURL: false)!
    let path = components.percentEncodedPath.hasSuffix("/") ? components.percentEncodedPath : components.percentEncodedPath + "/"
    components.percentEncodedPath = path + [resource, type, id].map(encode).joined(separator: "/")
    if !extras.isEmpty {
      components.percentEncodedPath += "/" + extras.keys.sorted().map { encode($0) + "=" + encode(extras[$0]!) }.joined(separator: "&")
    }
    components.percentEncodedPath += ".json"
    components.fragment = nil
    return components.url!
  }

  private static let stopwords: Set<String> = ["at", "vs", "versus", "the", "and", "state", "university", "college", "club", "fc", "sc", "united", "city", "real", "athletic", "st", "men", "women"]
  private static func keywords(_ name: String) -> [String] {
    TextMatching.normalize(name.folding(options: [.diacriticInsensitive], locale: .current))
      .split(separator: " ").map(String.init).filter { $0.count >= 3 && !stopwords.contains($0) }
  }
  static func matches(_ text: String, event: SportEvent) -> Bool {
    let normalized = TextMatching.normalize(text.folding(options: [.diacriticInsensitive], locale: .current))
    guard !normalized.isEmpty else { return false }
    if let home = event.homeTeam, let away = event.awayTeam {
      let homeWords = Set(keywords(home.name)), awayWords = Set(keywords(away.name))
      let words = Set(normalized.split(separator: " ").map(String.init))
      // Shared city words alone cannot identify a derby between two different teams.
      let homeMatch = !homeWords.subtracting(awayWords).isDisjoint(with: words)
      let awayMatch = !awayWords.subtracting(homeWords).isDisjoint(with: words)
      if homeMatch && awayMatch { return true }
      let h = TextMatching.normalize(home.abbreviation), a = TextMatching.normalize(away.abbreviation)
      if h != a && h.count >= 2 && a.count >= 2 && words.contains(h) && words.contains(a) { return true }
    }
    let name = TextMatching.normalize(event.name)
    return name.count > 5 && (normalized.contains(name) || name == normalized)
  }
  static func searchQueries(_ event: SportEvent) -> [String] {
    let names = [event.homeTeam?.name, event.awayTeam?.name].compactMap { $0 }
    let queries = names.compactMap { keywords($0).first } + names + [event.name]
    return Array(queries.uniqued { $0.lowercased() }.filter { !$0.isEmpty }.prefix(5))
  }
  private static func recordFailure(_ stage: String, error: Error) {
    guard !(error is CancellationError), (error as? URLError)?.code != .cancelled else { return }
    let reason: String
    if case RallyNetworkError.httpStatus(let status, _) = error { reason = "HTTP \(status)" }
    else if case RallyNetworkError.decoding = error { reason = "invalid response" }
    else if let network = error as? URLError { reason = "network \(network.code.rawValue)" }
    else { reason = "unavailable" }
    RallyDiagnostics.shared.record("Stremio", code: "\(stage): \(reason)")
  }

  static func option(dto: StremioStreamDTO, addonName: String?) -> StremioStreamOption? {
    let availability = [dto.title, dto.name, dto.streamDescription].compactMap { $0 }.joined(separator: " ").lowercased()
    guard !availability.contains("🔒"), !availability.contains("upgrade to"), !availability.contains("premium required") else { return nil }
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
      ["http", "https"].contains(url.scheme?.lowercased() ?? "")
    else { return nil }
    // `notWebReady` describes browser support, not native AVPlayer support.
    // Header-bearing HLS streams commonly require this flag in the addon protocol.
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
