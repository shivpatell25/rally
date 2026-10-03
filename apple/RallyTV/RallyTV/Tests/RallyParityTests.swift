import XCTest

@testable import RallyTV

final class RallyParityTests: XCTestCase {
  private func wire(_ value: Any) throws -> JSONValue {
    try JSONDecoder().decode(JSONValue.self, from: JSONSerialization.data(withJSONObject: value))
  }
  private func settings() -> SettingsStore {
    let suite = "rally.tests.\(UUID().uuidString)"
    return SettingsStore(
      defaults: UserDefaults(suiteName: suite)!, secrets: KeychainSecrets(service: suite))
  }
  private func event(
    _ id: String = "NFL:1", league: String = "NFL", date: String = "2026-09-27T17:00:00Z",
    home: Int = 7
  ) -> SportEvent {
    SportEvent(
      id: id, name: "Away vs Home", homeTeam: Team(id: "12", name: "Home", abbreviation: "H"),
      awayTeam: Team(id: "33", name: "Away", abbreviation: "A"), startTime: ESPNWire.date(date)!,
      status: .live, scoreHome: home, scoreAway: 3, sport: "football", league: league)
  }
  func testAllPlayerCategoriesAndRowsArePreserved() throws {
    let athletes: [[String: Any]] = (0..<22).map { index in
      [
        "athlete": [
          "id": "p\(index)", "displayName": "Player \(index)",
          "headshot": ["href": "https://example.test/p.png"],
        ], "stats": ["12", "234", "2"],
      ]
    }
    let categories: [[String: Any]] = ["passing", "rushing", "receiving", "defensive"].map {
      ["name": $0, "labels": ["ATT", "YDS", "TD"], "athletes": athletes]
    }
    let summary = try wire([
      "boxscore": [
        "players": [
          [
            "team": ["id": "12", "displayName": "Home", "abbreviation": "H"],
            "statistics": categories,
          ]
        ]
      ]
    ])
    let result = ESPNWire.enrich(event(), summary: summary)
    XCTAssertEqual(result.playerStatTables.count, 4)
    XCTAssertEqual(result.playerStatTables.map { $0.rows.count }, [22, 22, 22, 22])
    XCTAssertEqual(result.playerStatTables.first?.rows.last?.athleteId, "p21")
    XCTAssertEqual(result.playerStatTables.first?.labels, ["ATT", "YDS", "TD"])
    XCTAssertEqual(result.allGamePlayers.first?.players.count, 22)
    XCTAssertEqual(result.allGamePlayers.first?.players.first?.categories.count, 4)
  }
  func testStatusDatesAndMalformedProbability() throws {
    XCTAssertEqual(
      ESPNWire.status(try wire(["state": "in", "name": "STATUS_HALFTIME"])), .halftime)
    XCTAssertEqual(
      ESPNWire.status(try wire(["state": "post", "name": "STATUS_CANCELED"])), .canceled)
    XCTAssertNotNil(ESPNWire.date("2026-09-27T17:00:00.123Z"))
    XCTAssertEqual(
      ESPNWire.date("2026-10-02T00:15Z"), ESPNWire.date("2026-10-02T00:15:00Z"))
    XCTAssertEqual(
      ESPNWire.date("2026-10-01T18:15-06:00"), ESPNWire.date("2026-10-02T00:15:00Z"))
    let scoreboard = try wire([
      "id": "401872964", "date": "2026-10-02T00:15Z", "name": "Bills at Dolphins",
      "competitions": [
        [
          "competitors": [
            ["homeAway": "home", "team": ["id": "15", "displayName": "Dolphins"]],
            ["homeAway": "away", "team": ["id": "2", "displayName": "Bills"]],
          ], "status": ["type": ["state": "pre"]],
        ]
      ],
    ])
    XCTAssertEqual(
      ESPNWire.event(scoreboard, league: "NFL", sport: "football")?.id, "NFL:401872964")
    let summary = try wire([
      "winprobability": [
        ["homeWinPercentage": 0.9, "tiePercentage": 0.5], ["homeWinPercentage": 2.0],
      ]
    ])
    let result = ESPNWire.enrich(event(), summary: summary)
    XCTAssertEqual(result.winProbability.count, 1)
    XCTAssertEqual(result.winProbability[0].tiePercentage, 0.1, accuracy: 0.001)
  }
  func testTeamStatsMatchTeamIdentityInsteadOfArrayOrder() throws {
    let summary = try wire([
      "boxscore": [
        "teams": [
          [
            "team": ["id": "12"],
            "statistics": [["name": "yards", "label": "Total Yards", "displayValue": "355"]],
          ],
          [
            "team": ["id": "33"],
            "statistics": [["name": "yards", "label": "Total Yards", "displayValue": "382"]],
          ],
        ]
      ]
    ])
    let result = ESPNWire.enrich(event(), summary: summary)
    XCTAssertEqual(result.teamStats.first?.awayValue, "382")
    XCTAssertEqual(result.teamStats.first?.homeValue, "355")
  }
  func testNestedBaseballBoxscoreCategoriesAndLeaders() throws {
    let teams: [[String: Any]] = ["12", "33"].map { id in
      [
        "team": ["id": id, "displayName": id, "abbreviation": id],
        "statistics": [
          [
            "name": "batting",
            "stats": [
              ["name": "hits", "displayName": "Hits", "displayValue": id == "12" ? "7" : "6"],
              ["name": "strikeouts", "displayName": "Strikeouts", "displayValue": "9"],
            ],
          ],
          [
            "name": "fielding",
            "stats": [["name": "errors", "displayName": "Errors", "displayValue": "0"]],
          ],
        ],
      ]
    }
    let players: [[String: Any]] = ["12", "33"].map { id in
      [
        "team": ["id": id, "displayName": id, "abbreviation": id],
        "statistics": [
          [
            "type": "batting", "labels": ["H-AB", "RBI"],
            "athletes": [
              [
                "athlete": [
                  "id": "b\(id)", "displayName": "Batter \(id)", "shortName": "B. \(id)",
                  "headshot": ["href": "https://example.test/b.png"],
                ], "stats": ["2-4", "1"],
              ]
            ],
          ],
          [
            "type": "pitching", "labels": ["IP", "K"],
            "athletes": [
              [
                "athlete": ["id": "p\(id)", "displayName": "Pitcher \(id)"], "stats": ["6.0", "9"],
              ]
            ],
          ],
        ],
      ]
    }
    let summary = try wire([
      "boxscore": ["teams": teams, "players": players],
      "header": [
        "competitions": [
          ["broadcasts": [["type": ["shortName": "TV"], "media": ["shortName": "NBC"]]]]
        ]
      ],
    ])
    let result = ESPNWire.enrich(event(league: "MLB"), summary: summary)
    XCTAssertEqual(result.teamStats.first?.label, "Hits")
    XCTAssertEqual(result.teamStats.first?.awayValue, "6")
    XCTAssertEqual(result.teamStats.first?.homeValue, "7")
    XCTAssertFalse(result.teamStats.contains { $0.awayValue.isEmpty || $0.homeValue.isEmpty })
    XCTAssertEqual(
      result.playerStatTables.map(\.category), ["batting", "pitching", "batting", "pitching"])
    XCTAssertEqual(result.playerLeaders.count, 4)
    XCTAssertEqual(result.playerLeaders.first?.statDisplay, "2-4 H-AB · 1 RBI")
    XCTAssertNotNil(result.playerLeaders.first?.headshotUrl)
    XCTAssertEqual(result.liveStats["TV Broadcast"], "NBC")
  }
  func testGeometryPreservesAspectAndImmersiveEdges() {
    for count in 1...4 {
      for focus in [false, true] {
        let bounds = MultiViewGeometry.bounds(
          width: 1920, height: 1080, count: count, focus: focus, gap: 0)
        XCTAssertEqual(bounds.count, count)
        for rect in bounds {
          XCTAssertEqual(rect.width / rect.height, 16.0 / 9.0, accuracy: 0.001)
          XCTAssertGreaterThanOrEqual(rect.minX, 0)
          XCTAssertGreaterThanOrEqual(rect.minY, 0)
          XCTAssertLessThanOrEqual(rect.maxX, 1920.001)
          XCTAssertLessThanOrEqual(rect.maxY, 1080.001)
        }
        for i in bounds.indices {
          for j in bounds.indices where j > i { XCTAssertFalse(bounds[i].intersects(bounds[j])) }
        }
      }
    }
    let grid = MultiViewGeometry.bounds(width: 1920, height: 1080, count: 4, focus: false, gap: 0)
    XCTAssertEqual(grid[0].maxX, grid[1].minX)
    XCTAssertEqual(grid[0].maxY, grid[2].minY)
    XCTAssertEqual(grid.last?.maxY, 1080)
  }
  @MainActor func testRedZoneSlateAndDuplicateFeeds() {
    let first = event()
    let duplicate = event()
    let late = event("NFL:2", date: "2026-09-28T00:20:00Z")
    let second = event("NFL:3", date: "2026-09-27T20:25:00Z")
    let tomorrow = event("NFL:4", date: "2026-09-28T17:00:00Z")
    let slate = MultiViewGeometry.redZoneSlate(
      [first, late, second, tomorrow], date: first.startTime)
    XCTAssertEqual(slate.map(\.id), [first.id, second.id])
    let tiles = [MultiTile(event: first), MultiTile(event: duplicate), MultiTile(stats: true)]
    XCTAssertEqual(
      MultiViewGeometry.statsEvents(tiles, slate: slate).map(\.id), [first.id, second.id])
  }
  @MainActor func testMultiviewMutesEveryOtherStreamAndNeverRoutesAudioToStats() {
    let first = MultiTile()
    let second = MultiTile()
    let stats = MultiTile(stats: true)
    let tiles = [first, second, stats]
    MultiViewGeometry.routeAudio(tiles, pinned: nil, focused: second.id)
    XCTAssertTrue(first.session.player.isMuted)
    XCTAssertFalse(second.session.player.isMuted)
    XCTAssertTrue(stats.session.player.isMuted)
    MultiViewGeometry.routeAudio(tiles, pinned: first.id, focused: second.id)
    XCTAssertFalse(first.session.player.isMuted)
    XCTAssertTrue(second.session.player.isMuted)
    MultiViewGeometry.routeAudio([second, stats], pinned: first.id, focused: stats.id)
    XCTAssertFalse(second.session.player.isMuted)
    XCTAssertTrue(stats.session.player.isMuted)
  }
  func testAddonPlaybackNavigationPreservesRequiredHeaders() {
    let option = StremioStreamOption(
      title: "Protected HLS", streamUrl: URL(string: "https://example.test/master.m3u8")!,
      headers: ["Referer": "https://example.test/", "Authorization": "Bearer token"])
    let candidate = StreamCandidate(addon: option)
    XCTAssertEqual(candidate.headers, option.headers)
    if case .multiViewSource(let source, _) = RallyRoute.multiViewSource(
      candidate: candidate, eventId: "NFL:1")
    {
      XCTAssertEqual(source.headers, option.headers)
      XCTAssertEqual(source.playbackTarget, option.streamUrl)
    } else {
      XCTFail("Multiview lost the selected stream")
    }
    if case .playerSource(let resolved, _) = RallyRoute.playerSource(
      candidate: candidate, eventId: "NFL:1")
    {
      XCTAssertEqual(resolved.stremioStream, option)
      XCTAssertEqual(resolved.headers?["Authorization"], "Bearer token")
    } else {
      XCTFail("Source context lost")
    }
  }
  func testM3uCatalogHeadersRelativeURLsAndStableIdentity() throws {
    let source = URL(string: "https://provider.test/catalog/channels.m3u")!
    let text = """
      \u{FEFF}#EXTM3U
      #EXTINF:-1 tvg-chno="12" tvg-logo="../logos/team.png" group-title="Sports, Live",Sports HD
      #EXTVLCOPT:http-user-agent=Rally Playlist
      #EXTVLCOPT:http-referrer=https://provider.test/
      ../streams/main.m3u8|Origin=https%3A%2F%2Fprovider.test
      #EXTINF:-1,Duplicate
      #EXTVLCOPT:http-user-agent=Rally Playlist
      #EXTVLCOPT:http-referrer=https://provider.test/
      ../streams/main.m3u8|Origin=https%3A%2F%2Fprovider.test
      #EXTINF:-1 tvg-name='Second' group-title="News",Second Channel
      https://other.test/main.m3u8|User-Agent=Alternate+Agent&Referer=bad%0D%0AInjected
      #EXTINF:-1,Unsupported
      file:///private/provider-secret
      """
    let channels = try M3uPlaylistParser.parse(text, source: source)
    XCTAssertEqual(channels.count, 2)
    XCTAssertEqual(channels[0].number, "12")
    XCTAssertEqual(channels[0].category, "Sports, Live")
    XCTAssertEqual(channels[0].streamUrl?.absoluteString, "https://provider.test/streams/main.m3u8")
    XCTAssertEqual(channels[0].logoUrl?.absoluteString, "https://provider.test/logos/team.png")
    XCTAssertEqual(channels[0].streamHeaders?["User-Agent"], "Rally Playlist")
    XCTAssertEqual(channels[0].streamHeaders?["Referer"], "https://provider.test/")
    XCTAssertEqual(channels[0].streamHeaders?["Origin"], "https://provider.test")
    XCTAssertEqual(channels[1].streamHeaders?["User-Agent"], "Alternate Agent")
    XCTAssertNil(channels[1].streamHeaders?["Referer"])
    XCTAssertEqual(try M3uPlaylistParser.parse(text, source: source).map(\.id), channels.map(\.id))
    var previous = try XCTUnwrap(
      try JSONSerialization.jsonObject(with: JSONEncoder().encode(channels[0])) as? [String: Any])
    previous.removeValue(forKey: "streamHeaders")
    XCTAssertNil(
      try JSONDecoder().decode(
        IptvChannel.self, from: JSONSerialization.data(withJSONObject: previous)
      ).streamHeaders)
    XCTAssertThrowsError(try M3uPlaylistParser.parse("<html>Not a playlist</html>", source: source))
    let hls = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2000000\nhd.m3u8"
    let stream = try M3uPlaylistParser.parse(hls, source: source, name: "Sports Feed")
    XCTAssertEqual(stream.count, 1)
    XCTAssertEqual(stream[0].name, "Sports Feed")
    XCTAssertEqual(stream[0].streamUrl, source)
    XCTAssertThrowsError(try M3uPlaylistParser.parse(hls, source: nil))
    XCTAssertThrowsError(
      try M3uPlaylistParser.parse("#EXTM3U\n#EXTINF:-1,Local\nfile:///private/test", source: nil))
  }
  @MainActor func testM3uNativeHLSURLAndImportedChannelPlayback() async throws {
    let config = settings()
    config.provider = .m3u
    config.m3uPlaylistUrl = TVOSFixtures.video.absoluteString
    config.m3uPlaylistName = "Native HLS"
    let container = AppContainer(settings: config, sportsOverride: TVOSFixtures.container().sports)
    let remote = try await container.iptv.channels()
    XCTAssertEqual(remote.count, 1)
    XCTAssertEqual(remote[0].name, "Native HLS")
    XCTAssertEqual(remote[0].streamUrl, TVOSFixtures.video)
    let text =
      "#EXTM3U\n#EXTINF:-1 group-title=\"Sports\",Imported Sports\n#EXTVLCOPT:http-user-agent=Rally M3U Test\n\(TVOSFixtures.video.absoluteString)"
    XCTAssertEqual(
      try M3uPlaylistFiles.importCatalog(
        Data(text.utf8), name: "Personal Playlist", settings: config), 1)
    let savedFile = try XCTUnwrap(URL(string: config.m3uPlaylistUrl))
    defer { config.clearCredentials() }
    XCTAssertTrue(M3uPlaylistFiles.isOwned(savedFile))
    container.applyProvider()
    let channels = try await container.iptv.channels()
    let channel = try XCTUnwrap(channels.first)
    XCTAssertEqual(channel.name, "Imported Sports")
    let channelHeaders = try await container.iptv.streamHeaders(forChannelId: channel.id)
    XCTAssertEqual(channelHeaders["User-Agent"], "Rally M3U Test")
    let playback = PlaybackSession()
    defer { playback.stop() }
    await playback.open(channel.id, event: nil, container: container)
    for _ in 0..<120 {
      if !playback.loading && playback.elapsed > 0 { break }
      try await Task.sleep(for: .milliseconds(250))
    }
    XCTAssertNil(playback.error)
    XCTAssertEqual(playback.player.currentItem?.status, .readyToPlay)
    XCTAssertGreaterThan(playback.elapsed, 0)
    XCTAssertEqual(playback.sourceTitle, "Imported Sports")
    XCTAssertEqual(playback.headers["User-Agent"], "Rally M3U Test")
    XCTAssertFalse(try config.exportPersonalization().contains("m3u"))
    config.clearCredentials()
    XCTAssertFalse(FileManager.default.fileExists(atPath: savedFile.path))
  }
  @MainActor func testRemoteTransportAppliesDuplicateDeliveryOnce() {
    let session = PlaybackSession()
    session.playing = true
    session.remoteTransport(.pause)
    session.remoteTransport(.toggle)
    XCTAssertFalse(session.playing)
    session.stop()
  }
  @MainActor func testRealHLSPlaybackHeadersSeekingAndMediaSelection() async throws {
    let container = TVOSFixtures.container()
    let session = PlaybackSession()
    defer { session.stop() }
    let candidate = StreamCandidate(
      addon: StremioStreamOption(
        title: "HLS validation", streamUrl: TVOSFixtures.video,
        headers: ["User-Agent": "Rally-tvOS-Test/1.0"]))
    await session.open(
      candidate.playbackTarget.absoluteString, event: nil, candidate: candidate,
      container: container)
    XCTAssertTrue(session.playbackRequested)
    for _ in 0..<120 {
      if !session.loading && !session.audioTracks.isEmpty && session.elapsed > 0 { break }
      try await Task.sleep(for: .milliseconds(250))
    }
    XCTAssertNil(session.error)
    XCTAssertEqual(session.player.currentItem?.status, .readyToPlay)
    XCTAssertGreaterThan(session.elapsed, 0)
    XCTAssertTrue(session.seekable)
    session.suspendForScene()
    XCTAssertEqual(session.player.rate, 0)
    session.restoreForScene()
    XCTAssertGreaterThan(session.player.rate, 0)
    session.pause()
    XCTAssertFalse(session.playing)
    XCTAssertFalse(session.playbackRequested)
    session.suspendForScene()
    session.restoreForScene()
    XCTAssertEqual(
      session.player.rate, 0, "Returning to the foreground must preserve an explicit user pause")
    session.seek(30)
    try await Task.sleep(for: .seconds(1))
    XCTAssertGreaterThan(session.player.currentTime().seconds, 20)
    session.restart()
    try await Task.sleep(for: .seconds(1))
    XCTAssertLessThan(session.player.currentTime().seconds, 5)
    session.setQuality("720p")
    XCTAssertEqual(session.player.currentItem?.preferredMaximumResolution.height, 720)
    let asset = try XCTUnwrap(session.player.currentItem?.asset)
    let audio = try XCTUnwrap(session.audioTracks.first)
    let audioGroup = try await asset.loadMediaSelectionGroup(for: .audible)
    session.selectAudio(audio)
    XCTAssertEqual(
      session.player.currentItem?.currentMediaSelection.selectedMediaOption(
        in: try XCTUnwrap(audioGroup)), audio)
    let caption = try XCTUnwrap(session.captionTracks.first)
    let loadedCaptionGroup = try await asset.loadMediaSelectionGroup(for: .legible)
    let captionGroup = try XCTUnwrap(loadedCaptionGroup)
    session.selectCaption(caption)
    XCTAssertEqual(
      session.player.currentItem?.currentMediaSelection.selectedMediaOption(in: captionGroup),
      caption)
    session.selectCaption(nil)
    XCTAssertNil(
      session.player.currentItem?.currentMediaSelection.selectedMediaOption(in: captionGroup))
    for index in 0..<3 {
      let previous = session.player.currentItem
      let next = StreamCandidate(addon: StremioStreamOption(title: "Source \(index)", streamUrl: TVOSFixtures.video,
        headers: ["User-Agent": "Rally-switch-\(index)"]))
      await session.open(next.playbackTarget.absoluteString, event: nil, candidate: next, container: container)
      for _ in 0..<100 {
        if session.elapsed > 1 && session.player.currentItem?.status == .readyToPlay { break }
        try await Task.sleep(for: .milliseconds(200))
      }
      XCTAssertNil(session.error)
      XCTAssertFalse(previous === session.player.currentItem)
      XCTAssertGreaterThan(session.elapsed, 1, "A source change must produce advancing playback")
      XCTAssertEqual(session.headers["User-Agent"], "Rally-switch-\(index)")
      XCTAssertEqual(session.player.currentItem?.preferredMaximumResolution.height, 720)
    }
  }
  func testBackupsOmitCredentialsAndPreserveImportedTeamKeys() throws {
    let source = settings()
    source.xtreamUsername = "sensitive-user"
    source.xtreamPassword = "sensitive-password"
    source.portalUrl = "http://provider.test"
    source.favoriteTeamKeys = ["NFL:12", "MLB:12"]
    let backup = try source.exportPersonalization()
    XCTAssertFalse(backup.contains("sensitive"))
    XCTAssertFalse(backup.contains("provider.test"))
    let destination = settings()
    try destination.importPersonalization(backup)
    destination.toggleTeam(
      FavoriteTeam(teamId: "33", league: "NFL", name: "Ravens", abbreviation: "BAL"))
    XCTAssertEqual(destination.favoriteTeamKeys, ["NFL:12", "MLB:12", "NFL:33"])
    XCTAssertThrowsError(
      try destination.importPersonalization("{\"schema\":1,\"enabledLeagues\":[]}"))
    destination.sportsOrder = ["NFL", "NFL", "Unknown", "MLB"]
    XCTAssertEqual(destination.sportsOrder.prefix(2), ["NFL", "MLB"])
    XCTAssertEqual(Set(destination.sportsOrder).count, destination.sportsOrder.count)
  }
  @MainActor func testLocalTransferRequiresTokenImportsAndStops() async throws {
    let preferences = settings()
    preferences.xtreamPassword = "test-secret"
    XCTAssertEqual(
      preferences.xtreamPassword, "test-secret",
      "Run simulator tests with native ad-hoc signing to allow Keychain access")
    let transfer = PersonalizationTransfer()
    transfer.start(settings: preferences)
    defer { transfer.stop() }
    for _ in 0..<60 {
      if !transfer.address.isEmpty { break }
      try await Task.sleep(for: .milliseconds(50))
    }
    var components = try XCTUnwrap(URLComponents(string: transfer.address))
    components.host = "127.0.0.1"
    let base = try XCTUnwrap(components.url)
    let session = URLSession(configuration: .ephemeral)
    defer { session.invalidateAndCancel() }
    let (backup, response) = try await session.data(from: base.appendingPathComponent("backup"))
    XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 200)
    XCTAssertFalse(String(decoding: backup, as: UTF8.self).contains("test-secret"))
    let donor = settings()
    donor.enabledLeagues = ["NFL", "NCAAB"]
    donor.favoriteTeamKeys = ["NFL:12", "NCAAB:12"]
    var request = URLRequest(url: base.appendingPathComponent("import"))
    request.httpMethod = "POST"
    request.httpBody = Data(try donor.exportPersonalization().utf8)
    let (_, imported) = try await session.data(for: request)
    XCTAssertEqual((imported as? HTTPURLResponse)?.statusCode, 200)
    XCTAssertEqual(preferences.favoriteTeamKeys, donor.favoriteTeamKeys)
    XCTAssertEqual(preferences.enabledLeagues, donor.enabledLeagues)
    XCTAssertEqual(preferences.xtreamPassword, "test-secret")
    request.url = base.appendingPathComponent("playlist")
    request.httpBody = Data(
      "#EXTM3U\n#EXTINF:-1,Phone Imported\nhttps://example.test/live.m3u8|User-Agent=Rally+Imported"
        .utf8)
    request.setValue("Phone%20Playlist", forHTTPHeaderField: "X-Rally-Playlist-Name")
    let (_, playlistResponse) = try await session.data(for: request)
    XCTAssertEqual((playlistResponse as? HTTPURLResponse)?.statusCode, 200)
    XCTAssertEqual(preferences.provider, .m3u)
    XCTAssertEqual(preferences.m3uPlaylistName, "Phone Playlist")
    let importedPlaylist = M3uIptvRepository(http: HTTPClient(), settings: preferences)
    let importedChannels = try await importedPlaylist.channels()
    XCTAssertEqual(importedChannels.count, 1)
    XCTAssertEqual(importedChannels.first?.name, "Phone Imported")
    XCTAssertEqual(importedChannels.first?.streamHeaders?["User-Agent"], "Rally Imported")
    request.httpBody = Data("<html>Invalid playlist</html>".utf8)
    let (_, invalidPlaylist) = try await session.data(for: request)
    XCTAssertEqual((invalidPlaylist as? HTTPURLResponse)?.statusCode, 400)
    let unchangedChannels = try await importedPlaylist.channels()
    XCTAssertEqual(unchangedChannels, importedChannels)
    preferences.clearCredentials()
    components.path = "/invalid-token/backup"
    let (_, rejected) = try await session.data(from: XCTUnwrap(components.url))
    XCTAssertEqual((rejected as? HTTPURLResponse)?.statusCode, 403)
    transfer.stop()
    var closed = URLRequest(url: base)
    closed.timeoutInterval = 2
    do {
      _ = try await session.data(for: closed)
      XCTFail("Transfer accepted a connection after its panel closed")
    } catch {}
  }
  func testScopedHTTPNeverWidensConfiguredHosts() async {
    let config = settings()
    config.portalUrl = "http://provider.test:8080/portal"
    NetworkPolicy.shared.configure(config)
    XCTAssertTrue(NetworkPolicy.shared.permits(URL(string: "http://provider.test/epg")!))
    XCTAssertFalse(NetworkPolicy.shared.permits(URL(string: "http://other.test/")!))
    XCTAssertFalse(NetworkPolicy.shared.permits(URL(string: "http://provider.test.evil.test/")!))
    do {
      _ = try await ScopedHTTPTransport.data(
        URLRequest(url: URL(string: "http://provider.test:99999/playlist")!))
      XCTFail("Invalid ports must be rejected without attempting a connection or trapping")
    } catch {}
    XCTAssertTrue(NetworkPolicy.shared.permits(URL(string: "https://other.test/")!))
    config.m3uPlaylistUrl = "http://playlist.test/channels.m3u?token=private"
    NetworkPolicy.shared.configure(config)
    XCTAssertTrue(NetworkPolicy.shared.permits(URL(string: "http://playlist.test/live.m3u8")!))
    XCTAssertFalse(NetworkPolicy.shared.permits(URL(string: "http://cdn.playlist.test/live.m3u8")!))
    var selection: PlaylistMediaPermission? = PlaylistMediaPermission(
      URL(string: "http://media.test/live.m3u8")!)
    XCTAssertNotNil(selection)
    XCTAssertTrue(NetworkPolicy.shared.permits(URL(string: "http://media.test/segment.ts")!))
    XCTAssertFalse(NetworkPolicy.shared.permits(URL(string: "http://other-media.test/segment.ts")!))
    selection = nil
    XCTAssertFalse(NetworkPolicy.shared.permits(URL(string: "http://media.test/segment.ts")!))
    NetworkPolicy.shared.configure(settings())
    XCTAssertFalse(NetworkPolicy.shared.permits(URL(string: "http://provider.test/")!))
  }
  func testChunkedHTTPValidatesSizesAndFraming() throws {
    let body = Data("4\r\nWiki\r\n5;ext=yes\r\npedia\r\n0\r\n\r\n".utf8)
    XCTAssertEqual(
      String(decoding: try ScopedHTTPTransport.decodeChunked(body), as: UTF8.self), "Wikipedia")
    XCTAssertThrowsError(try ScopedHTTPTransport.decodeChunked(Data("4\r\nWikiXX0\r\n\r\n".utf8)))
    XCTAssertThrowsError(try ScopedHTTPTransport.decodeChunked(Data("ffffff\r\nshort".utf8)))
  }
  func testAlertsCannotCollideAcrossLeagues() {
    XCTAssertTrue(
      GameAlertEvaluator.evaluate(
        previous: [event(league: "MLB", home: 3)], current: [event(league: "MLB")],
        favoriteTeamIds: ["NFL:12"]
      ).isEmpty)
    XCTAssertFalse(
      GameAlertEvaluator.evaluate(
        previous: [event(home: 3)], current: [event()], favoriteTeamIds: ["NFL:12"]
      ).isEmpty)
  }
  func testTypedDeepLinksPreserveEventAndSourceParameters() {
    XCTAssertEqual(
      RallyDeepLink.route(URL(string: "rally://player?target=auto&eventId=NFL:qa1")!),
      .player(target: "auto", eventId: "NFL:qa1"))
    XCTAssertEqual(
      RallyDeepLink.route(URL(string: "rally://event/NFL:qa1")!), .eventDetail(eventId: "NFL:qa1"))
  }
  func testFootballDrivePlaysAndScoringMomentsAreMerged() throws {
    let summary = try wire([
      "drives": [
        "previous": [["plays": [
          ["id": "kick", "sequenceNumber": "3900", "text": "Kickoff", "period": ["number": 1]],
          ["id": "td", "sequenceNumber": "4600", "text": "Passing touchdown", "scoringPlay": false]
        ]]],
        "current": ["plays": [["id": "run", "sequenceNumber": "5000", "text": "Run for five yards"]]]
      ],
      "scoringPlays": [["id": "td", "text": "Touchdown"], ["id": "fg", "sequenceNumber": "6000", "text": "Field goal"]]
    ])
    let result = ESPNWire.enrich(event(), summary: summary)
    XCTAssertEqual(result.plays.map(\.id), ["fg", "run", "td", "kick"])
    XCTAssertEqual(result.plays.filter(\.isScoringPlay).count, 2)
    XCTAssertEqual(result.plays.last?.period, 1)
    XCTAssertEqual(result.plays.first { $0.id == "td" }?.text, "Passing touchdown")
  }
  func testPlaybackWatchdogDetectsStallsAndRespectsPause() {
    var progress = PlaybackProgressWatchdog()
    let start = Date().timeIntervalSinceReferenceDate
    XCTAssertFalse(progress.check(now: start + 24, wantsPlayback: true, ready: false, position: 0))
    XCTAssertTrue(progress.check(now: start + 26, wantsPlayback: true, ready: false, position: 0))
    XCTAssertFalse(progress.check(now: start + 27, wantsPlayback: true, ready: true, position: 1))
    XCTAssertTrue(progress.check(now: start + 42, wantsPlayback: true, ready: true, position: 1))
    XCTAssertFalse(progress.check(now: start + 100, wantsPlayback: false, ready: true, position: 1))
    XCTAssertFalse(progress.check(now: start + 101, wantsPlayback: true, ready: true, position: 2))
  }

}

final class StremioDiscoveryTests: XCTestCase {
  private func game() -> SportEvent {
    SportEvent(id: "MLB:test", name: "Boston Red Sox at New York Yankees",
      homeTeam: Team(id: "ny", name: "New York Yankees", abbreviation: "NYY"),
      awayTeam: Team(id: "bos", name: "Boston Red Sox", abbreviation: "BOS"),
      startTime: Date(), status: .live, sport: "baseball", league: "MLB")
  }
  private func fixture(timeout: TimeInterval = 3,
    handler: @escaping (String) -> AddonTestResponse
  ) -> (StremioRepositoryImpl, AddonTestFixture) {
    let fixture = AddonTestFixture(handler: handler)
    let host = "addon-\(UUID().uuidString.lowercased()).test"
    AddonTestURLProtocol.register(fixture, host: host)
    let config = URLSessionConfiguration.ephemeral
    config.protocolClasses = [AddonTestURLProtocol.self]
    let session = URLSession(configuration: config)
    let suite = "rally.stremio.\(UUID().uuidString)"
    let settings = SettingsStore(defaults: UserDefaults(suiteName: suite)!, secrets: KeychainSecrets(service: suite))
    settings.stremioAddonUrls = [URL(string: "https://\(host)/configured/manifest.json?token=preserved")!]
    return (StremioRepositoryImpl(http: HTTPClient(session: session), settings: settings, discoveryTimeout: timeout), fixture)
  }
  func testCustomCatalogTypeDescriptionsAndNativeHeaderStreams() async throws {
    let (repo, fixture) = fixture { path in
      switch path {
      case "/configured/manifest.json":
        return .json(#"{"name":"Sports","types":["live-event"],"catalogs":[{"id":"mlb-live","type":"live-event","name":"MLB"}]}"#)
      case "/configured/catalog/live-event/mlb-live.json":
        return .json(#"{"metas":[{"id":"game/123","name":"Live baseball","description":"Boston vs New York"},{"id":"wrong","name":"Boston vs Chicago"}]}"#)
      case "/configured/stream/live-event/game%2F123.json":
        return .json(#"{"streams":[{"name":"1080p","title":"Home broadcast","url":"https://media.test/live.m3u8","behaviorHints":{"notWebReady":true,"proxyHeaders":{"request":{"User-Agent":"SportsPlayer","Referer":"https://provider.test/"}}}}]}"#)
      default: return .json("{}", status: 404)
      }
    }
    let streams = await repo.streams(for: game())
    XCTAssertEqual(streams.count, 1)
    XCTAssertEqual(streams.first?.title, "Home broadcast")
    XCTAssertEqual(streams.first?.headers?["User-Agent"], "SportsPlayer")
    XCTAssertEqual(streams.first?.isDirectPlayable, true)
    XCTAssertFalse(fixture.paths.contains { $0.contains("wrong.json") || $0.contains("/stream/sport/") })
    XCTAssertTrue(fixture.queries.allSatisfy { $0 == "token=preserved" })
  }
  func testSearchOnlyCatalogSearchesBothTeamsAndPreservesType() async {
    let (repo, fixture) = fixture { path in
      if path.hasSuffix("manifest.json") {
        return .json(#"{"name":"Search","catalogs":[{"id":"events","type":"tv","extra":[{"name":"search","isRequired":true}]}]}"#)
      }
      if path.contains("search=new.json") { return .json(#"{"metas":[]}"#) }
      if path.contains("search=boston.json") {
        return .json(#"{"metas":[{"id":"bos-ny","name":"BOS vs NYY"}]}"#)
      }
      if path == "/configured/stream/tv/bos-ny.json" {
        return .json(#"{"streams":[{"url":"https://media.test/live.m3u8"}]}"#)
      }
      return .json("{}", status: 404)
    }
    let streams = await repo.streams(for: game())
    XCTAssertEqual(streams.count, 1)
    XCTAssertTrue(fixture.paths.contains { $0.contains("search=boston.json") })
    XCTAssertFalse(fixture.paths.contains("/configured/catalog/tv/events.json"))
  }
  func testSlowCatalogDoesNotDiscardCompletedSources() async {
    let (repo, _) = fixture(timeout: 0.5) { path in
      if path.hasSuffix("manifest.json") {
        return .json(#"{"name":"Sports","catalogs":[{"id":"mlb-fast","type":"tv","extra":[]},{"id":"mlb-slow","type":"tv","extra":[]}]}"#)
      }
      if path.contains("mlb-slow") { return .json(#"{"metas":[]}"#, delay: 5) }
      if path.contains("mlb-fast") { return .json(#"{"metas":[{"id":"game","name":"Boston vs New York"}]}"#) }
      if path.contains("/stream/tv/game.json") { return .json(#"{"streams":[{"url":"https://media.test/game.m3u8"}]}"#) }
      return .json("{}", status: 404)
    }
    let start = Date()
    let streams = await repo.streams(for: game())
    XCTAssertEqual(streams.count, 1)
    XCTAssertLessThan(Date().timeIntervalSince(start), 2)
  }
  func testFailedAddonDoesNotHideOtherAddonResults() async {
    let config = URLSessionConfiguration.ephemeral
    config.protocolClasses = [AddonTestURLProtocol.self]
    let suite = "rally.stremio.\(UUID().uuidString)"
    let settings = SettingsStore(defaults: UserDefaults(suiteName: suite)!, secrets: KeychainSecrets(service: suite))
    let good = "good-\(UUID().uuidString.lowercased()).test", bad = "bad-\(UUID().uuidString.lowercased()).test"
    AddonTestURLProtocol.register(AddonTestFixture { path in
      if path.hasSuffix("manifest.json") { return .json(#"{"catalogs":[{"type":"tv","id":"mlb","extra":[]}]}"#) }
      if path.contains("/catalog/") { return .json(#"{"metas":[{"id":"game","name":"Boston vs New York"}]}"#) }
      return .json(#"{"streams":[{"url":"https://media.test/live.m3u8"}]}"#)
    }, host: good)
    AddonTestURLProtocol.register(AddonTestFixture { _ in .json("{}", status: 401) }, host: bad)
    settings.stremioAddonUrls = [URL(string: "https://\(good)/manifest.json")!, URL(string: "https://\(bad)/manifest.json")!]
    let repo = StremioRepositoryImpl(http: HTTPClient(session: URLSession(configuration: config)), settings: settings)
    let streams = await repo.streams(for: game())
    XCTAssertEqual(streams.count, 1)
    XCTAssertTrue(RallyDiagnostics.shared.report().contains("Manifest: HTTP 401"))
  }
  func testMatchSafetyAndCustomAddonURLs() throws {
    XCTAssertTrue(StremioRepositoryImpl.matches("Boston vs New York", event: game()))
    XCTAssertFalse(StremioRepositoryImpl.matches("Boston vs Chicago", event: game()))
    let derby = SportEvent(id: "NBA:1", name: "Clippers vs Lakers",
      homeTeam: Team(id: "1", name: "Los Angeles Lakers", abbreviation: "LAL"),
      awayTeam: Team(id: "2", name: "Los Angeles Clippers", abbreviation: "LAC"),
      startTime: Date(), status: .live, sport: "basketball", league: "NBA")
    XCTAssertFalse(StremioRepositoryImpl.matches("Los Angeles vs Chicago", event: derby))
    XCTAssertTrue(StremioRepositoryImpl.matches("LAL vs LAC", event: derby))
    let noTeams = SportEvent(id: "tennis:1", name: "Sinner vs Alcaraz", startTime: Date(), status: .live, sport: "tennis", league: "ATP")
    XCTAssertTrue(StremioRepositoryImpl.matches("Live: Sinner vs Alcaraz", event: noTeams))
    let url = try XCTUnwrap(PortalUrlNormalizer.normalizeAddon("stremio://addon.test/config/manifest.json?key=test#fragment"))
    XCTAssertEqual(url.absoluteString, "https://addon.test/config/manifest.json?key=test")
    let resource = StremioRepositoryImpl.resourceURL(root: URL(string: "https://addon.test/config/?key=test")!, resource: "catalog", type: "tv", id: "games", extras: ["search": "New York & Boston"])
    XCTAssertEqual(URLComponents(url: resource, resolvingAgainstBaseURL: false)?.percentEncodedPath, "/config/catalog/tv/games/search=New%20York%20%26%20Boston.json")
    XCTAssertEqual(resource.query, "key=test")
  }
  func testSportsStreamsCollegeNamesAndNotWebReadySources() async {
    let (repo, _) = fixture { path in
      if path.hasSuffix("manifest.json") {
        return .json(#"{"name":"Sports Streams","types":["sport"],"catalogs":[{"id":"sports_live","type":"sport","extra":[]},{"id":"sports_today","type":"sport","extra":[]},{"id":"sports_football","type":"sport","extra":[]},{"id":"sports_american_football","type":"sport","extra":[]}]}"#)
      }
      if path.contains("sports_american_football.json") {
        return .json(#"{"metas":[{"id":"streamed:north-carolina-vs-notre-dame","type":"sport","name":"North Carolina vs Notre Dame"}]}"#)
      }
      if path.contains("/catalog/") { return .json(#"{"metas":[]}"#) }
      if path.contains("streamed%3Anorth-carolina-vs-notre-dame.json") {
        return .json(#"{"streams":[{"name":"Leaf · US : ESPN HD","title":"1280x720 · Stereo · ~4.8 Mbps","url":"https://media.test/ncaaf.m3u8","behaviorHints":{"notWebReady":true}},{"name":"CDN (Premium)","title":"🔒 Upgrade to watch","url":"https://media.test/locked","behaviorHints":{"notWebReady":true}}]}"#)
      }
      return .json("{}", status: 404)
    }
    let event = SportEvent(id: "NCAAF:test", name: "Notre Dame Fighting Irish at North Carolina Tar Heels",
      homeTeam: Team(id: "153", name: "North Carolina Tar Heels", abbreviation: "UNC"),
      awayTeam: Team(id: "87", name: "Notre Dame Fighting Irish", abbreviation: "ND"),
      startTime: Date(), status: .live, sport: "football", league: "NCAAF")
    let streams = await repo.streams(for: event)
    XCTAssertEqual(streams.count, 1)
    XCTAssertEqual(streams.first?.isDirectPlayable, true)
    XCTAssertEqual(streams.first?.addonName, "Sports Streams")
  }
  func testPublicSportsStreamsNCAAFDiscovery() async throws {
    try XCTSkipIf(ProcessInfo.processInfo.environment["RALLY_LIVE_STREMIO_QA"] != "1", "Opt-in live addon integration")
    let suite = "rally.stremio.live.\(UUID().uuidString)"
    let settings = SettingsStore(defaults: UserDefaults(suiteName: suite)!, secrets: KeychainSecrets(service: suite))
    settings.stremioAddonUrls = [URL(string: "https://sports.highfly.to/manifest.json")!]
    let repo = StremioRepositoryImpl(http: HTTPClient(), settings: settings)
    let event = SportEvent(id: "NCAAF:live-check", name: "Notre Dame Fighting Irish at North Carolina Tar Heels",
      homeTeam: Team(id: "153", name: "North Carolina Tar Heels", abbreviation: "UNC"),
      awayTeam: Team(id: "87", name: "Notre Dame Fighting Irish", abbreviation: "ND"),
      startTime: Date(), status: .live, sport: "football", league: "NCAAF")
    let streams = await repo.streams(for: event)
    XCTAssertFalse(streams.isEmpty, RallyDiagnostics.shared.report())
    XCTAssertTrue(streams.contains(where: \.isDirectPlayable))
    XCTAssertTrue(streams.allSatisfy { !$0.title.contains("🔒") })
    print("LIVE ADDON CHECK: \(streams.count) sources returned for North Carolina vs Notre Dame; \(streams.filter(\.isDirectPlayable).count) native candidates")
  }
  func testExternalSourcesRemainNonNativeAndCatalogPrioritiesRemain() throws {
    let dto = try JSONDecoder().decode(StremioStreamDTO.self, from: Data(#"{"externalUrl":"https://provider.test/watch","title":"Browser"}"#.utf8))
    XCTAssertEqual(StremioRepositoryImpl.option(dto: dto, addonName: "Sports")?.isDirectPlayable, false)
    let manifest = try JSONDecoder().decode(StremioManifest.self, from: Data(#"{"catalogs":[{"id":"baseball","type":"custom"},{"id":"generic","type":"tv"}]}"#.utf8))
    XCTAssertEqual(StremioRepositoryImpl.catalogs(manifest, event: game()).first?.id, "baseball")
    XCTAssertEqual(StremioRepositoryImpl.catalogs(manifest, event: game()).count, 2)
  }
}

private struct AddonTestResponse {
  let body: Data
  let status: Int
  let delay: TimeInterval
  static func json(_ text: String, status: Int = 200, delay: TimeInterval = 0) -> Self {
    Self(body: Data(text.utf8), status: status, delay: delay)
  }
}
private final class AddonTestFixture: @unchecked Sendable {
  private let lock = NSLock()
  private var requests: [URL] = []
  private let handler: (String) -> AddonTestResponse
  init(handler: @escaping (String) -> AddonTestResponse) { self.handler = handler }
  func reply(_ url: URL) -> AddonTestResponse {
    lock.lock(); requests.append(url); lock.unlock()
    return handler(URLComponents(url: url, resolvingAgainstBaseURL: false)!.percentEncodedPath)
  }
  var paths: [String] {
    lock.lock(); defer { lock.unlock() }
    return requests.map { URLComponents(url: $0, resolvingAgainstBaseURL: false)!.percentEncodedPath }
  }
  var queries: [String?] {
    lock.lock(); defer { lock.unlock() }
    return requests.map(\.query)
  }
}
private final class AddonTestURLProtocol: URLProtocol, @unchecked Sendable {
  private static let lock = NSLock()
  private static var fixtures: [String: AddonTestFixture] = [:]
  private var work: DispatchWorkItem?
  static func register(_ fixture: AddonTestFixture, host: String) {
    lock.lock(); fixtures[host] = fixture; lock.unlock()
  }
  override class func canInit(with request: URLRequest) -> Bool { true }
  override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
  override func startLoading() {
    guard let url = request.url, let host = url.host else { return }
    Self.lock.lock(); let fixture = Self.fixtures[host]; Self.lock.unlock()
    guard let fixture else {
      client?.urlProtocol(self, didFailWithError: URLError(.cannotFindHost)); return
    }
    let response = fixture.reply(url)
    let work = DispatchWorkItem { [weak self] in
      guard let self, self.work?.isCancelled == false else { return }
      let http = HTTPURLResponse(url: url, statusCode: response.status, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
      self.client?.urlProtocol(self, didReceive: http, cacheStoragePolicy: .notAllowed)
      self.client?.urlProtocol(self, didLoad: response.body)
      self.client?.urlProtocolDidFinishLoading(self)
    }
    self.work = work
    DispatchQueue.global().asyncAfter(deadline: .now() + response.delay, execute: work)
  }
  override func stopLoading() { work?.cancel() }
}
