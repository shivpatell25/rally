import XCTest

final class RallyParityUITests: XCTestCase {
  let app = XCUIApplication(), remote = XCUIRemote.shared
  override func setUpWithError() throws { continueAfterFailure = false }
  func launch(_ route: String? = nil, noLive: Bool = false) {
    app.launchArguments = ["--ui-testing", "--fixtures"] + (noLive ? ["--no-live"] : [])
    if let route { app.launchArguments += ["--route", route] }
    app.launch()
  }
  func testWelcomeContinueUsesRemoteFocusAndOpensSetup() throws {
    app.launchArguments = ["--ui-testing", "--fixtures", "--show-welcome"]
    app.launch()
    let button = app.buttons["welcome-continue"]
    XCTAssertTrue(button.waitForExistence(timeout: 10))
    XCTAssertFalse(app.buttons["nav-Home"].exists, "Home must not compete for welcome focus")
    XCTAssertTrue(button.hasFocus, app.debugDescription)
    shot("welcome-remote-focus")
    remote.press(.select)
    XCTAssertTrue(app.buttons["tab-Sources"].waitForExistence(timeout: 10), app.debugDescription)
    XCTAssertFalse(button.exists)
    shot("welcome-setup")
  }
  // Explicit opt-in: this walkthrough uses public production feeds and HLS,
  // so network availability must not make the deterministic suite flaky.
  func testShippingRepositoriesAndPublishedClip() throws {
    try XCTSkipIf(ProcessInfo.processInfo.environment["RALLY_LIVE_QA"] != "1")
    // Dismiss a simulator system URL prompt left by a manual deep-link check.
    remote.press(.menu)
    func shipping(_ route: String? = nil) {
      app.launchArguments = ["--ui-testing"]
      if let route { app.launchArguments += ["--route", route] }
      app.launch()
    }
    shipping()
    XCTAssertTrue(app.buttons["upcoming-0"].waitForExistence(timeout: 90), app.debugDescription)
    let media = app.buttons.matching(
      NSPredicate(format: "identifier BEGINSWITH 'clip-' OR identifier BEGINSWITH 'event-'"))
    XCTAssertTrue(media.firstMatch.waitForExistence(timeout: 90), app.debugDescription)
    XCTAssertLessThan(app.buttons["nav-Highlights"].frame.height, 90)
    shot("real-home-top")
    try focus(app.buttons["upcoming-0"])
    remote.press(.down)
    XCTAssertTrue(app.buttons["sport-NCAAB"].waitForExistence(timeout: 4))
    shot("real-home-guide")
    shipping("schedule")
    XCTAssertTrue(app.buttons["Today"].waitForExistence(timeout: 20))
    XCTAssertTrue(
      app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'schedule-'")).firstMatch
        .waitForExistence(timeout: 60), app.debugDescription)
    shot("real-schedule")
    shipping("event/MLB:401907965")
    XCTAssertTrue(app.buttons["tab-Overview"].waitForExistence(timeout: 90), app.debugDescription)
    shot("real-event-overview")
    try focus(app.buttons["tab-Stats"])
    remote.press(.select)
    shot("real-event-stats")
    let hls =
      "https://cmp-espn.media.dssott.com/opp/hls/espn/wsc/2026/0929/12368b95-9d9c-4983-a711-00b503e382c0/12368b95-9d9c-4983-a711-00b503e382c0/playlist.m3u8"
    let encoded = hls.addingPercentEncoding(withAllowedCharacters: .alphanumerics)!
    shipping(
      "player?target=\(encoded)&eventId=MLB%3A401907965&clipTitle=Published%20ESPN%20highlight")
    XCTAssertTrue(app.buttons["Pause"].waitForExistence(timeout: 45), app.debugDescription)
    Thread.sleep(forTimeInterval: 3)
    XCTAssertLessThanOrEqual(
      app.descendants(matching: .any)["stats-plays"].frame.maxY, app.frame.height - 35)
    shot("real-highlight-playing")
    try focus(app.buttons["Fullscreen"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["Game View"].waitForExistence(timeout: 10))
    shot("real-highlight-fullscreen")
    XCTAssertEqual(app.state, .runningForeground)
  }
  func testHomePagedRailAndGuideComposition() throws {
    launch()
    XCTAssertTrue(app.buttons["event-NFL:qa1"].waitForExistence(timeout: 15))
    shot("home-top")
    try focus(app.buttons["event-NFL:qa1"])
    remote.press(.right)
    XCTAssertTrue(app.buttons["event-NFL:qa2"].hasFocus)
    remote.press(.right)
    XCTAssertTrue(app.buttons["event-NFL:qa3"].hasFocus)
    XCTAssertFalse(app.buttons["event-NFL:qa4"].exists)
    remote.press(.right)
    XCTAssertTrue(app.buttons["event-NFL:qa4"].waitForExistence(timeout: 4))
    XCTAssertFalse(app.buttons["event-NFL:qa1"].exists)
    try focus(app.buttons["upcoming-0"])
    shot("home-compact-focused")
    remote.press(.down)
    XCTAssertTrue(app.buttons["sport-NCAAB"].waitForExistence(timeout: 4))
    shot("home-guide")
    XCTAssertTrue(app.buttons["nav-Home"].exists)
    try focus(app.buttons["SEE FULL SCHEDULE"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["Today"].waitForExistence(timeout: 4))
    remote.press(.menu)
    try focus(app.buttons["nav-Home"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["upcoming-0"].waitForExistence(timeout: 4))
    XCTAssertFalse(app.buttons["SEE FULL SCHEDULE"].exists)
  }
  func testNoLiveUsesRealHighlightCards() throws {
    launch(noLive: true)
    XCTAssertTrue(app.buttons["clip-clip-0"].waitForExistence(timeout: 15))
    XCTAssertEqual(
      app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'clip-'")).count, 3)
    XCTAssertEqual(
      app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'event-'")).count, 0)
    shot("home-no-live")
    try focus(app.buttons["upcoming-0"])
    remote.press(.down)
    XCTAssertTrue(app.buttons["sport-NCAAB"].waitForExistence(timeout: 4))
    XCTAssertEqual(
      app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'clip-'")).count, 3)
    shot("home-no-live-guide")
    try focus(app.buttons["clip-clip-0"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["Pause"].waitForExistence(timeout: 20))
    shot("home-highlight-playing")
    remote.press(.menu)
    XCTAssertTrue(app.buttons["clip-clip-0"].waitForExistence(timeout: 5))
  }
  func testEveryDestinationAndEventTab() throws {
    for route in [
      "schedule", "live", "leagues", "league/NFL", "team/NFL/12", "watchlist", "highlights",
      "search", "settings", "iptv",
    ] {
      launch(route)
      XCTAssertTrue(app.buttons["nav-Home"].waitForExistence(timeout: 10), route)
      Thread.sleep(forTimeInterval: 1.5)
      XCTAssertEqual(app.state, .runningForeground, route)
      shot(route.replacingOccurrences(of: "/", with: "-"))
    }
    launch("event/NFL:qa1")
    XCTAssertTrue(app.buttons["tab-Overview"].waitForExistence(timeout: 10))
    shot("event-overview")
    for tab in ["Stats", "Lineups", "Plays", "Sources", "Highlights"] {
      try focus(app.buttons["tab-\(tab)"])
      remote.press(.select)
      Thread.sleep(forTimeInterval: 0.4)
      shot("event-\(tab.lowercased())")
      XCTAssertEqual(app.state, .runningForeground)
    }
  }
  func testPlayerControlsSourceAndFullscreen() throws {
    launch("player?target=auto&eventId=NFL:qa1")
    XCTAssertTrue(app.buttons["Pause"].waitForExistence(timeout: 35), app.debugDescription)
    let video = app.buttons["Video player"].frame
    XCTAssertEqual(video.width / video.height, 16.0 / 9.0, accuracy: 0.01)
    let moments = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'moment-'"))
    XCTAssertEqual(moments.count, 4)
    for moment in moments.allElementsBoundByIndex {
      XCTAssertLessThanOrEqual(moment.frame.maxY, app.frame.height - 30)
    }
    shot("game-view")
    XCTAssertLessThanOrEqual(
      app.descendants(matching: .any)["stats-plays"].frame.maxY, app.frame.height - 35)
    let leaders = app.descendants(matching: .any)["stats-leaders"]
    try focus(leaders)
    for id in ["stats-team", "stats-situation", "stats-plays"] {
      remote.press(.down)
      XCTAssertTrue(app.descendants(matching: .any)[id].hasFocus, id)
    }
    try focus(app.buttons["tab-Plays"])
    remote.press(.select)
    try focus(app.buttons["tab-Players"])
    remote.press(.select)
    try focus(app.buttons["tab-Sources"])
    remote.press(.select)
    try focus(app.buttons["tab-Stats"])
    remote.press(.select)
    try focus(app.buttons["tab-Other Live Games"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["other-game-NFL:qa1"].waitForExistence(timeout: 5))
    shot("player-other-games")
    try focus(app.buttons["tab-Key Moments"])
    remote.press(.select)
    try focus(app.buttons["Audio"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["Done"].waitForExistence(timeout: 4))
    shot("player-audio")
    remote.press(.menu)
    try focus(app.buttons["Captions"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["Off"].waitForExistence(timeout: 4))
    shot("player-captions")
    remote.press(.menu)
    try focus(app.buttons["header-source"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["source-1"].waitForExistence(timeout: 4))
    try focus(app.buttons["source-1"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["Pause"].waitForExistence(timeout: 20))
    try focus(app.buttons["Fullscreen"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["Game View"].waitForExistence(timeout: 4))
    shot("fullscreen-controls")
    try focus(app.buttons["Diagnostics"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["Done"].waitForExistence(timeout: 4))
    shot("player-diagnostics")
    remote.press(.menu)
    remote.press(.playPause)
    XCTAssertTrue(app.buttons["Play"].waitForExistence(timeout: 4))
    remote.press(.playPause)
    remote.press(.menu)
    XCTAssertTrue(app.buttons["Fullscreen"].waitForExistence(timeout: 4))
  }
  func testMultiviewStatsAndImmersive() throws {
    launch("multiview?eventIds=NFL:qa1,NFL:qa2,NFL:qa3")
    XCTAssertTrue(app.buttons["Add Stats"].waitForExistence(timeout: 35))
    try focus(app.buttons["Add Stats"])
    remote.press(.select)
    Thread.sleep(forTimeInterval: 1)
    shot("multiview-stats")
    try focus(app.buttons["Audio Follows Focus"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["Audio Pinned"].waitForExistence(timeout: 4))
    try focus(app.buttons["Immersive"])
    remote.press(.select)
    shot("multiview-immersive")
    remote.press(.menu)
    XCTAssertTrue(app.buttons["Immersive"].waitForExistence(timeout: 4))
  }
  func testSettingsRemoteSectionsAndBackups() throws {
    launch("settings")
    XCTAssertTrue(app.buttons["tab-Sources"].waitForExistence(timeout: 10))
    for tab in ["Addons", "Sports", "My Rally", "Alerts", "Viewing", "Support"] {
      try focus(app.buttons["tab-\(tab)"])
      remote.press(.select)
      shot("settings-\(tab.lowercased().replacingOccurrences(of:" ",with:"-"))")
    }
    try focus(app.buttons["Export / Import Preferences"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["Done"].waitForExistence(timeout: 5))
    shot("settings-transfer")
    remote.press(.menu)
    try focus(app.buttons["nav-Home"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["upcoming-0"].waitForExistence(timeout: 5))
  }
  @MainActor func testM3uFileImportChannelBrowsingAndPlayback() async throws {
    launch("settings")
    try focus(app.buttons["M3U / M3U8"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["Import Playlist File"].waitForExistence(timeout: 4))
    shot("settings-m3u")
    try focus(app.buttons["Import Playlist File"])
    remote.press(.select)
    let address = app.staticTexts["Local transfer address"]
    XCTAssertTrue(address.waitForExistence(timeout: 10))
    var components = try XCTUnwrap(URLComponents(string: address.label))
    components.host = "127.0.0.1"
    let base = try XCTUnwrap(components.url)
    let video =
      "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"
    let playlist =
      "#EXTM3U\n#EXTINF:-1 group-title=\"Sports\",Playlist Sports\n#EXTVLCOPT:http-user-agent=Rally M3U QA\n\(video)\n#EXTINF:-1 group-title=\"News\",Playlist News\n\(video)"
    var request = URLRequest(url: base.appendingPathComponent("playlist"))
    request.httpMethod = "POST"
    request.httpBody = Data(playlist.utf8)
    request.setValue("Test%20Playlist", forHTTPHeaderField: "X-Rally-Playlist-Name")
    let session = URLSession(configuration: .ephemeral)
    defer { session.invalidateAndCancel() }
    let (_, response) = try await session.data(for: request)
    XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 200)
    XCTAssertTrue(
      app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Playlist imported'"))
        .firstMatch.waitForExistence(timeout: 5))
    shot("settings-m3u-import")
    try focus(app.buttons["Done"])
    remote.press(.select)
    try focus(app.buttons["Test Provider"])
    remote.press(.select)
    XCTAssertTrue(app.staticTexts["Connected · 2 channels."].waitForExistence(timeout: 15))
    try focus(app.buttons["nav-Live"])
    remote.press(.select)
    try focus(app.buttons["BROWSE LIVE TV"])
    remote.press(.select)
    let channel = app.buttons.matching(NSPredicate(format: "label CONTAINS 'Playlist Sports'"))
      .firstMatch
    XCTAssertTrue(channel.waitForExistence(timeout: 15), app.debugDescription)
    shot("iptv-m3u")
    try focus(channel)
    remote.press(.select)
    XCTAssertTrue(app.buttons["Pause"].waitForExistence(timeout: 40), app.debugDescription)
    shot("m3u-channel-playing")
    XCTAssertTrue(app.buttons["Diagnostics"].exists)
    XCTAssertFalse(app.buttons["Game View"].exists)
    XCTAssertTrue(app.staticTexts["Playlist Sports"].exists)
    try focus(app.buttons["Pause"])
    remote.press(.up)
    XCTAssertTrue(app.buttons["Seek"].hasFocus)
    // Seeking consumes directional commands itself. Keep interacting beyond the
    // idle timeout to verify those commands keep the fullscreen controls alive.
    for _ in 0..<8 {
      remote.press(.right)
      try await Task.sleep(for: .seconds(1))
      XCTAssertTrue(app.buttons["Seek"].hasFocus, app.debugDescription)
    }
    remote.press(.up)
    XCTAssertTrue(app.buttons["Auto"].hasFocus)
    remote.press(.select)
    try focus(app.buttons["720p"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["720p"].waitForExistence(timeout: 5))
    try focus(app.buttons["Pick Source"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["source-1"].waitForExistence(timeout: 10))
    try focus(app.buttons["source-1"])
    remote.press(.select)
    XCTAssertTrue(app.staticTexts["Playlist News"].waitForExistence(timeout: 10))
    XCTAssertTrue(app.buttons["720p"].exists)
    XCTAssertTrue(app.buttons["Pause"].waitForExistence(timeout: 40))
    try await Task.sleep(for: .seconds(10))
    XCTAssertFalse(app.buttons["Pick Source"].exists)
    remote.press(.down)
    XCTAssertTrue(app.buttons["Pause"].waitForExistence(timeout: 4))
    XCTAssertTrue(app.buttons["Pause"].hasFocus)
    try focus(app.buttons["Multiview"])
    remote.press(.select)
    XCTAssertTrue(app.buttons["Immersive"].waitForExistence(timeout: 35))
    let tile = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'multiview-tile-'"))
    XCTAssertEqual(tile.count, 1)
    XCTAssertEqual(tile.firstMatch.label, "Playlist News")
    let playing = expectation(
      for: NSPredicate(format: "value == 'Playing'"), evaluatedWith: tile.firstMatch)
    await fulfillment(of: [playing], timeout: 25)
    shot("m3u-channel-multiview")
    XCTAssertEqual(app.state, .runningForeground)
  }
  func testMultiviewTwoThreeFourStreamBoundsAndManagement() throws {
    for count in 2...4 {
      launch("multiview?eventIds=" + (1...count).map { "NFL:qa\($0)" }.joined(separator: ","))
      XCTAssertTrue(app.buttons["Immersive"].waitForExistence(timeout: 35))
      let tiles = app.buttons.matching(
        NSPredicate(format: "identifier BEGINSWITH 'multiview-tile-'"))
      XCTAssertEqual(tiles.count, count)
      shot("multiview-\(count)")
      try focus(app.buttons["Immersive"])
      remote.press(.select)
      Thread.sleep(forTimeInterval: 0.4)
      let screen = app.frame
      for tile in tiles.allElementsBoundByIndex {
        let rect = tile.frame
        XCTAssertGreaterThanOrEqual(rect.minX, -1)
        XCTAssertGreaterThanOrEqual(rect.minY, -1)
        XCTAssertLessThanOrEqual(rect.maxX, screen.width + 1)
        XCTAssertLessThanOrEqual(rect.maxY, screen.height + 1)
        XCTAssertEqual(rect.width / rect.height, 16.0 / 9.0, accuracy: 0.02)
      }
      shot("multiview-\(count)-immersive")
      remote.press(.menu)
      try focus(tiles.element(boundBy: 0))
      remote.press(.select)
      XCTAssertTrue(app.buttons["Change Stream"].waitForExistence(timeout: 4))
      try focus(app.buttons["Promote to Main"])
      remote.press(.select)
      XCTAssertTrue(app.buttons["Grid"].waitForExistence(timeout: 4))
      let originalIDs = Set(tiles.allElementsBoundByIndex.map(\.identifier))
      try focus(tiles.element(boundBy: 0))
      remote.press(.select)
      try focus(app.buttons["Fullscreen"])
      remote.press(.select)
      XCTAssertTrue(app.buttons["Game View"].waitForExistence(timeout: 20))
      remote.press(.menu)
      XCTAssertTrue(app.buttons["Immersive"].waitForExistence(timeout: 20))
      XCTAssertEqual(Set(tiles.allElementsBoundByIndex.map(\.identifier)), originalIDs)
      let playing = NSPredicate(format: "value == 'Playing'")
      expectation(for: playing, evaluatedWith: tiles.element(boundBy: 0))
      waitForExpectations(timeout: 20)
    }
  }
  private func shot(_ name: String) {
    let a = XCTAttachment(screenshot: app.screenshot())
    a.name = name
    a.lifetime = .keepAlways
    add(a)
  }
  private func focus(_ target: XCUIElement) throws {
    XCTAssertTrue(target.waitForExistence(timeout: 8), "Missing target \(target.identifier)")
    var visits: [String: Int] = [:]
    for _ in 0..<35 {
      if target.hasFocus { return }
      let current = app.descendants(matching: .any).matching(
        NSPredicate(format: "hasFocus == true")
      ).firstMatch
      guard current.exists else {
        remote.press(.down)
        continue
      }
      let from = current.frame
      let to = target.frame
      let dx = to.midX - from.midX
      let dy = to.midY - from.midY
      let key = "\(current.identifier):\(Int(from.midX)):\(Int(from.midY))"
      visits[key, default: 0] += 1
      // A large video surface can make its center point misleading. Try the other
      // axis on a repeated node, using the same native remote events as a person.
      let alternate = visits[key, default: 0] % 2 == 0
      if (abs(dx) > abs(dy)) != alternate && abs(dx) > 30 {
        remote.press(dx > 0 ? .right : .left)
      } else if abs(dy) > 30 {
        remote.press(dy > 0 ? .down : .up)
      } else {
        remote.press(dx > 0 ? .right : .left)
      }
    }
    XCTFail("Could not focus \(target.identifier)\n\(app.debugDescription)")
  }
}
