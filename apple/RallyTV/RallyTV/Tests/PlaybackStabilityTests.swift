import AVKit
import Network
import XCTest

@testable import RallyTV

final class PlaybackStabilityTests: XCTestCase {
  private let names = [("Kansas City Chiefs", "Baltimore Ravens"), ("Green Bay Packers", "Chicago Bears"),
    ("Philadelphia Eagles", "Dallas Cowboys"), ("Buffalo Bills", "Miami Dolphins")]
  private func games() -> [SportEvent] {
    names.enumerated().map { index, pair in
      SportEvent(id: "NFL:g\(index + 1)", name: "\(pair.0) vs \(pair.1)",
        homeTeam: Team(id: "h\(index)", name: pair.1, abbreviation: "H\(index)"),
        awayTeam: Team(id: "a\(index)", name: pair.0, abbreviation: "A\(index)"),
        startTime: Date(), status: .live, sport: "football", league: "NFL")
    }
  }
  @MainActor private func setup(portal: Bool = false) async throws -> (PlaybackFaultServer, AppContainer, [SportEvent]) {
    let events = games()
    let asset = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "segment", withExtension: "ts"))
    let server = try PlaybackFaultServer(segment: Data(contentsOf: asset), games: events)
    try await server.start()
    let suite = "rally.playback-stability.\(UUID().uuidString)"
    let settings = SettingsStore(defaults: UserDefaults(suiteName: suite)!, secrets: KeychainSecrets(service: suite))
    settings.portalUrl = server.base.absoluteString
    settings.macAddress = "00:1A:79:11:22:33"
    settings.authToken = "Bearer fixture-session"
    settings.stremioAddonUrls = portal ? [] : [server.base.appendingPathComponent("addon/manifest.json")]
    return (server, AppContainer(settings: settings), events)
  }
  @MainActor private func waitPlaying(_ sessions: [PlaybackSession], timeout: TimeInterval = 45) async throws {
    let until = Date().addingTimeInterval(timeout)
    while Date() < until {
      if sessions.allSatisfy({ $0.error == nil && $0.player.currentItem?.status == .readyToPlay && $0.player.rate > 0 && $0.elapsed > 0.1 }) { return }
      try await Task.sleep(for: .milliseconds(200))
    }
    XCTFail("Streams did not advance: \(sessions.map { "\($0.error ?? "no error") / \($0.elapsed) / \($0.player.currentItem?.status.rawValue ?? -1)" })")
  }
  @MainActor private func addonSessions(_ container: AppContainer, events: [SportEvent]) async throws -> [PlaybackSession] {
    var sessions: [PlaybackSession] = []
    for event in events {
      let streams = await container.stremio.streams(for: event)
      let option = try XCTUnwrap(streams.first)
      let candidate = StreamCandidate(addon: option)
      let session = PlaybackSession()
      session.setMultiViewCaps(count: 4)
      await session.open(candidate.playbackTarget.absoluteString, event: event, candidate: candidate, container: container)
      sessions.append(session)
    }
    try await waitPlaying(sessions)
    return sessions
  }
  @MainActor func testFourAddonStreamsRenewExpiredLinksWithoutRestartingOtherPlayers() async throws {
    let (server, container, events) = try await setup()
    defer { server.stop() }
    let sessions = try await addonSessions(container, events: events)
    defer { sessions.forEach { $0.stop() } }
    let items = sessions.map { $0.player.currentItem }
    server.revoke(game: "g1")
    let until = Date().addingTimeInterval(50)
    while Date() < until && sessions[0].player.currentItem === items[0] {
      try await Task.sleep(for: .milliseconds(250))
    }
    try await waitPlaying(sessions)
    XCTAssertGreaterThan(server.linkCount("g1"), 1)
    XCTAssertFalse(sessions[0].player.currentItem === items[0])
    for i in 1...3 { XCTAssertTrue(sessions[i].player.currentItem === items[i]) }
    XCTAssertEqual(server.invalidHeaders, 0)
    for i in 0..<4 { await sessions[i].retry(); try await waitPlaying(sessions) }
    let positions = sessions.map { $0.elapsed }
    try await Task.sleep(for: .seconds(4))
    for i in 0..<4 { XCTAssertGreaterThan(sessions[i].elapsed, positions[i]) }
  }
  @MainActor func testPortalFrozenTileRecoveryAndForegroundReleasePreservePause() async throws {
    let (server, container, events) = try await setup(portal: true)
    defer { server.stop() }
    let channels = try await container.iptv.channels()
    XCTAssertEqual(channels.count, 4)
    var sessions: [PlaybackSession] = []
    defer { sessions.forEach { $0.stop() } }
    for channel in channels {
      let session = PlaybackSession()
      session.setMultiViewCaps(count: 4)
      await session.open(channel.id, event: events[sessions.count], candidate: StreamCandidate(channel: channel), container: container)
      sessions.append(session)
    }
    try await waitPlaying(sessions)
    let items = sessions.map { $0.player.currentItem }
    server.freeze(game: "g2", enabled: true)
    let until = Date().addingTimeInterval(50)
    while Date() < until && server.linkCount("g2") < 2 { try await Task.sleep(for: .milliseconds(250)) }
    XCTAssertGreaterThan(server.linkCount("g2"), 1)
    server.freeze(game: "g2", enabled: false)
    try await waitPlaying(sessions)
    XCTAssertTrue(sessions[0].player.currentItem === items[0])
    sessions[3].pause()
    for _ in 0..<3 {
      sessions.forEach { $0.suspendForScene() }
      XCTAssertTrue(sessions.allSatisfy { $0.player.currentItem == nil })
      try await Task.sleep(for: .milliseconds(200))
      sessions.forEach { $0.restoreForScene() }
      try await waitPlaying(Array(sessions.prefix(3)))
      XCTAssertFalse(sessions[3].playbackRequested)
      XCTAssertEqual(sessions[3].player.rate, 0)
    }
    sessions[3].resume()
    try await waitPlaying(sessions)
    XCTAssertEqual(server.invalidHeaders, 0)
  }
  @MainActor func testConcurrentPortalNegotiationIsOrderedAndRejectsInvalidLink() async throws {
    let (server, container, _) = try await setup(portal: true)
    defer { server.stop() }
    let channels = try await container.iptv.channels()
    let repo = container.iptv
    let urls = try await withThrowingTaskGroup(of: URL.self) { group in
      for channel in channels { group.addTask { try await repo.streamUrl(forChannelId: channel.id) } }
      var result: [URL] = []
      for try await url in group { result.append(url) }
      return result
    }
    XCTAssertEqual(urls.count, 4)
    XCTAssertEqual(server.maximumConcurrentLinks, 1)
    server.invalidLinks = true
    do {
      _ = try await repo.streamUrl(forChannelId: channels[0].id)
      XCTFail("An invalid portal command must not become a playback URL")
    } catch { XCTAssertTrue(error is RallyNetworkError) }
  }
  @MainActor func testUnavailableAddonStopsRetryingWhileOtherStreamsContinue() async throws {
    let (server, container, events) = try await setup()
    defer { server.stop() }
    let sessions = try await addonSessions(container, events: events)
    defer { sessions.forEach { $0.stop() } }
    let healthyItem = sessions[1].player.currentItem
    server.setOffline("g1", enabled: true)
    let until = Date().addingTimeInterval(70)
    while Date() < until && sessions[0].error == nil { try await Task.sleep(for: .milliseconds(250)) }
    XCTAssertNotNil(sessions[0].error)
    XCTAssertNil(sessions[0].player.currentItem)
    try await waitPlaying(Array(sessions.dropFirst()))
    XCTAssertTrue(sessions[1].player.currentItem === healthyItem)
    let attempts = server.linkCount("g1")
    try await Task.sleep(for: .seconds(4))
    XCTAssertEqual(server.linkCount("g1"), attempts)
    server.setOffline("g1", enabled: false)
    await sessions[0].retry()
    try await waitPlaying(sessions)
  }
  @MainActor func testProxyPrunesLiveWindowResourcesAndCancelsPendingHTTP() async throws {
    let (server, _, _) = try await setup()
    defer { server.stop() }
    let proxy = HeaderMediaProxy(headers: [:])
    defer { proxy.stop() }
    let url = try await proxy.start(server.base.appendingPathComponent("window.m3u8"))
    let client = URLSession(configuration: .ephemeral)
    defer { client.invalidateAndCancel() }
    for _ in 0..<40 {
      let (data, _) = try await client.data(from: url)
      XCTAssertTrue(String(decoding: data, as: UTF8.self).hasPrefix("#EXTM3U"))
      XCTAssertLessThanOrEqual(proxy.resourceCount, 5, "Expired live URLs must be pruned")
    }
    proxy.stop()
    let slowProxy = HeaderMediaProxy(headers: [:])
    defer { slowProxy.stop() }
    let slowURL = try await slowProxy.start(server.base.appendingPathComponent("slow.m3u8"))
    let pending = Task { try? await client.data(from: slowURL) }
    for _ in 0..<100 {
      if slowProxy.activeConnectionCount > 0 { break }
      try await Task.sleep(for: .milliseconds(10))
    }
    XCTAssertGreaterThan(slowProxy.activeConnectionCount, 0)
    let stopped = Date()
    slowProxy.stop()
    _ = await pending.value
    XCTAssertLessThan(Date().timeIntervalSince(stopped), 2)
    XCTAssertEqual(slowProxy.activeConnectionCount, 0)
    XCTAssertEqual(proxy.resourceCount, 0)
  }
}

/// Local HLS plus real addon and Stalker endpoints; never uses customer accounts.
private final class PlaybackFaultServer: @unchecked Sendable {
  private let listener: NWListener
  private let queue = DispatchQueue(label: "rally.tests.playback-http")
  private let lock = NSLock()
  private let segment: Data
  private let games: [SportEvent]
  private let started = Date()
  private var versions: [String: Int] = [:]
  private var revoked: Set<String> = []
  private var offline: Set<String> = []
  private var frozen: [String: Int] = [:]
  private var expectedHeaders: [String: String] = [:]
  private var connections: [ObjectIdentifier: NWConnection] = [:]
  private var window = 0
  private var badHeaders = 0
  private var activeLinks = 0
  private var maxLinks = 0
  private var badLinks = false
  private var readinessDelivered = false
  var base: URL { URL(string: "http://127.0.0.1:\(listener.port!.rawValue)")! }
  var invalidHeaders: Int { lock.withLock { badHeaders } }
  var maximumConcurrentLinks: Int { lock.withLock { maxLinks } }
  var invalidLinks: Bool { get { lock.withLock { badLinks } } set { lock.withLock { badLinks = newValue } } }
  init(segment: Data, games: [SportEvent]) throws {
    self.segment = segment
    self.games = games
    let parameters = NWParameters.tcp
    parameters.requiredLocalEndpoint = .hostPort(host: .ipv4(.loopback), port: .any)
    listener = try NWListener(using: parameters)
  }
  func start() async throws {
    listener.newConnectionHandler = { [weak self] connection in
      guard let self else { connection.cancel(); return }
      self.lock.withLock { self.connections[ObjectIdentifier(connection)] = connection }
      connection.start(queue: self.queue)
      self.read(connection, buffer: Data())
    }
    try await withCheckedThrowingContinuation { (c: CheckedContinuation<Void, Error>) in
      listener.stateUpdateHandler = { [self] state in
        switch state { case .ready, .failed, .cancelled: break; default: return }
        let claim = lock.withLock {
          guard !readinessDelivered else { return false }
          readinessDelivered = true
          return true
        }
        guard claim else { return }
        switch state {
        case .ready: c.resume()
        case .failed(let error): c.resume(throwing: error)
        case .cancelled: c.resume(throwing: CancellationError())
        default: break
        }
      }
      listener.start(queue: queue)
    }
  }
  func stop() {
    listener.cancel()
    let active = lock.withLock { let all = Array(connections.values); connections.removeAll(); return all }
    active.forEach { $0.cancel() }
  }
  func linkCount(_ game: String) -> Int { lock.withLock { versions[game, default: 0] } }
  func revoke(game: String) { _ = lock.withLock { revoked.insert("\(game)-\(versions[game, default: 0])") } }
  func setOffline(_ game: String, enabled: Bool) {
    lock.withLock { if enabled { offline.insert(game) } else { offline.remove(game) } }
  }
  func freeze(game: String, enabled: Bool) {
    lock.withLock { frozen[game] = enabled ? sequence : nil }
  }
  private var sequence: Int { 6 + Int(Date().timeIntervalSince(started) / 2) }
  private func read(_ connection: NWConnection, buffer: Data) {
    connection.receive(minimumIncompleteLength: 1, maximumLength: 16384) { [weak self] data, _, complete, error in
      guard let self else { connection.cancel(); return }
      var buffer = buffer
      if let data { buffer.append(data) }
      if let text = String(data: buffer, encoding: .utf8), text.contains("\r\n\r\n") { self.respond(connection, text: text) }
      else if error == nil && !complete && buffer.count < 65536 { self.read(connection, buffer: buffer) }
      else { self.close(connection) }
    }
  }
  private func respond(_ connection: NWConnection, text: String) {
    let lines = text.components(separatedBy: "\r\n")
    let path = lines.first?.split(separator: " ").dropFirst().first.map(String.init) ?? "/"
    guard let url = URL(string: path, relativeTo: base)?.absoluteURL else { close(connection); return }
    let query = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
    let values = Dictionary(query.map { ($0.name, $0.value ?? "") }, uniquingKeysWith: { _, new in new })
    let headers = Dictionary(lines.dropFirst().compactMap { line -> (String, String)? in
      guard let i = line.firstIndex(of: ":") else { return nil }
      return (line[..<i].lowercased(), line[line.index(after: i)...].trimmingCharacters(in: .whitespaces))
    }, uniquingKeysWith: { _, new in new })
    func json(_ object: Any) { send(connection, body: try! JSONSerialization.data(withJSONObject: object), type: "application/json") }
    func issue(_ game: String, portal: Bool) -> String {
      lock.withLock {
        versions[game, default: 0] += 1
        let token = "\(game)-\(versions[game]!)"
        expectedHeaders[token] = portal ? (headers["authorization"] ?? "") : "RallyFixture-\(token)"
        return token
      }
    }
    if url.path == "/addon/manifest.json" {
      json(["name": "Fixture Sports", "catalogs": [["id": "nfl-live", "type": "sport"]]])
    } else if url.path.contains("/catalog/") {
      json(["metas": games.map { ["id": $0.id.components(separatedBy: ":").last!, "name": $0.name] }])
    } else if url.path.contains("/stream/") {
      let game = url.deletingPathExtension().lastPathComponent
      let token = issue(game, portal: false)
      json(["streams": [["title": "\(game) broadcast", "url": "\(base)/hls/\(game)/master.m3u8?token=\(token)",
        "behaviorHints": ["proxyHeaders": ["request": ["User-Agent": "RallyFixture-\(token)", "Referer": base.absoluteString]]]]]])
    } else if url.path.hasSuffix("load.php") {
      switch values["action"] {
      case "handshake": json(["js": ["token": "fixture-session"]])
      case "get_profile": json(["js": [:] as [String: String]])
      case "get_genres": json(["js": [] as [String]])
      case "get_all_channels": json(["js": ["data": games.map { event in
        let game = event.id.components(separatedBy: ":").last!
        return ["id": game, "name": event.name, "number": game, "cmd": "ffrt http://localhost/ch/\(game)"]
      }]])
      case "create_link":
        let game = values["cmd"]?.components(separatedBy: "/").last ?? "g1"
        let token = issue(game, portal: true)
        lock.withLock { activeLinks += 1; maxLinks = max(maxLinks, activeLinks) }
        queue.asyncAfter(deadline: .now() + 0.1) { [self] in
          lock.withLock { activeLinks -= 1 }
          json(["js": ["cmd": invalidLinks ? "ffrt http://localhost/ch/123" : "ffmpeg \(base)/hls/\(game)/master.m3u8?token=\(token)"]])
        }
      default: json(["js": [] as [String]])
      }
    } else if url.path == "/slow.m3u8" {
      // Leave the upstream request open until stop cancels it.
      return
    } else if url.path == "/window.m3u8" {
      let next = lock.withLock { window += 1; return window }
      let body = "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:\(next)\n" + (0..<4).map { "#EXTINF:2,\nsegment\(next * 4 + $0).ts\n" }.joined()
      send(connection, body: Data(body.utf8), type: "application/vnd.apple.mpegurl")
    } else if url.path.hasPrefix("/hls/") {
      let game = url.path.components(separatedBy: "/")[2]
      let token = values["token"] ?? ""
      let invalid = lock.withLock { () -> Bool in
        let expected = expectedHeaders[token] ?? ""
        let okay = headers["user-agent"] == expected || (!expected.isEmpty && headers["authorization"] == expected && headers["cookie"]?.contains("mac=") == true)
        if !okay { badHeaders += 1 }
        return !okay || revoked.contains(token) || offline.contains(game)
      }
      if invalid { send(connection, status: 403); return }
      if url.path.hasSuffix(".m3u8") {
        let seq = lock.withLock { frozen[game] ?? sequence }
        var body = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:\(seq)\n"
        for i in seq..<(seq + 6) { body += "#EXT-X-DISCONTINUITY\n#EXTINF:2.0,\nsegment.ts?token=\(token)&seq=\(i)\n" }
        send(connection, body: Data(body.utf8), type: "application/vnd.apple.mpegurl")
      } else { send(connection, body: segment, type: "video/mp2t") }
    } else { send(connection, status: 404) }
  }
  private func send(_ connection: NWConnection, status: Int = 200, body: Data = Data(), type: String = "application/octet-stream") {
    var payload = Data("HTTP/1.1 \(status) Response\r\nContent-Type: \(type)\r\nContent-Length: \(body.count)\r\nConnection: close\r\n\r\n".utf8)
    payload.append(body)
    connection.send(content: payload, completion: .contentProcessed { [weak self] _ in self?.close(connection) })
  }
  private func close(_ connection: NWConnection) {
    _ = lock.withLock { connections.removeValue(forKey: ObjectIdentifier(connection)) }
    connection.cancel()
  }
}
