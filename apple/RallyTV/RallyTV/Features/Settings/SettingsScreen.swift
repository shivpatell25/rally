import CoreImage.CIFilterBuiltins
import SwiftUI

struct SettingsScreen: View {
  @Environment(RallyStore.self) private var store
  let navigate: (RallyRoute) -> Void
  @State private var tab = "Sources"
  @State private var accountTab = "My Rally"
  @State private var streamTab = "IPTV"
  @State private var provider = IptvProvider.stalker
  @State private var portal = ""
  @State private var mac = ""
  @State private var serial = ""
  @State private var device = ""
  @State private var server = ""
  @State private var username = ""
  @State private var password = ""
  @State private var playlist = ""
  @State private var playlistName = ""
  @State private var addon = ""
  @State private var message: String?
  @State private var busy = false
  @State private var teamLeague = "NFL"
  @State private var teamQuery = ""
  @State private var teams: [FavoriteTeam] = []
  @State private var diagnostics = ""
  @State private var backupInput = ""
  @State private var transfer = false
  @State private var link: SupportLink?
  @FocusState private var accountFocus: String?
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(16)) {
      Text("Settings").font(RallyDesign.font(25, .semibold))
      HStack(alignment: .top, spacing: RallyDesign.pt(30)) {
        VStack(alignment: .leading, spacing: RallyDesign.pt(8)) {
          ForEach(["Sources", "Playback", "Appearance", "Account", "Alerts", "Support"], id: \.self) { section in
            Button { tab = section } label: {
              Text(sectionTitle(section)).font(RallyDesign.font(14, tab == section ? .semibold : .regular))
                .frame(width: RallyDesign.pt(154), height: RallyDesign.pt(28), alignment: .leading)
            }.buttonStyle(RallyButtonStyle(bare: true, selected: tab == section)).focusEffectDisabled()
              .accessibilityIdentifier("tab-" + section)
          }
        }.frame(width: RallyDesign.pt(174), alignment: .leading).focusSection()
        VStack(alignment: .leading, spacing: RallyDesign.pt(10)) {
          Text(sectionTitle(tab)).font(RallyDesign.font(20, .semibold))
          if let message {
            Text(message).font(RallyDesign.font(11)).foregroundStyle(RallyDesign.muted)
              .accessibilityIdentifier("Settings status")
          }
          ScrollView {
            VStack(alignment: .leading, spacing: RallyDesign.pt(18)) {
              switch tab {
              case "Sources":
                RallyTabs(tabs: ["IPTV", "Addon Manifests"], selection: $streamTab)
                if streamTab == "IPTV" { sourceSettings } else { addonSettings }
              case "Account":
                RallyAction(title: "Import / Export Preferences") { transfer = true }
                RallyTabs(tabs: ["My Rally", "Sports"], selection: $accountTab, focus: $accountFocus) { _, direction in
                  if direction == .down, accountTab == "Sports",
                     let league = store.settings.sportsOrder.first {
                    accountFocus = "sport-enabled-" + league
                  }
                }
                if accountTab == "Sports" { sportSettings } else { teamSettings }
              case "Alerts":
                toggle("Live game alerts", get: { store.settings.liveGameAlertsEnabled },
                       set: { store.settings.liveGameAlertsEnabled = $0 })
                toggle("NFL RedZone alerts", get: { store.settings.redZoneAlertsEnabled },
                       set: { store.settings.redZoneAlertsEnabled = $0 })
              case "Playback": playbackSettings
              case "Appearance": appearanceSettings
              case "Support": supportSettings
              default: EmptyView()
              }
            }.padding(.vertical, RallyDesign.pt(10))
              .padding(.horizontal, RallyDesign.pt(6))
          }.padding(.horizontal, RallyDesign.pt(-6)).focusSection()
        }.frame(maxWidth: .infinity, alignment: .leading).focusSection()
      }
    }.padding(.horizontal, RallyDesign.pt(60)).padding(.top, RallyDesign.pt(12))
      .padding(.bottom, RallyDesign.pt(22))
    .task {
      let s = store.settings
      provider = s.provider
      portal = s.portalUrl
      mac = s.macAddress
      serial = s.serialNumber
      device = s.deviceId
      server = s.xtreamServerUrl
      username = s.xtreamUsername
      password = s.xtreamPassword
      playlist = s.m3uPlaylistUrl
      playlistName = s.m3uPlaylistName
    }
    .task(id: teamLeague) {
      teams = (try? await store.container.sports.teams(league: teamLeague)) ?? []
    }
    .sheet(item: $link) { item in SupportLinkSheet(item: item) }
    .sheet(
      isPresented: $transfer,
      onDismiss: {
        provider = store.settings.provider
        playlist = store.settings.m3uPlaylistUrl
        playlistName = store.settings.m3uPlaylistName
        store.applySettings()
      }
    ) {
      PersonalizationTransferSheet {
        transfer = false
        store.settingsRevision += 1
      }
    }

  }
  private var sourceNotice: some View {
    Text("Rally is a media player and aggregation interface. Rally does not provide, host, sell, or redistribute third-party streams or channels. Only connect services and content sources that you are legally authorized to access. You are responsible for complying with applicable laws, copyright requirements, and the terms of the services you use.").font(RallyDesign.font(11)).foregroundStyle(RallyDesign.muted)
  }
  private var sourceSettings: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(14)) {
      HStack {
        RallyAction(title: "Stalker / Ministra", primary: provider == .stalker) {
          provider = .stalker
        }
        RallyAction(title: "Xtream Codes", primary: provider == .xtream) { provider = .xtream }
        RallyAction(title: "M3U / M3U8", primary: provider == .m3u) { provider = .m3u }
      }
      if provider == .stalker {
        field("Portal URL", text: $portal)
        field("MAC Address", text: $mac)
        field("Serial Number (optional)", text: $serial)
        field("Device ID (optional)", text: $device)
      } else if provider == .xtream {
        field("Server URL", text: $server)
        field("Username", text: $username)
        field("Password", text: $password, secure: true)
      } else {
        if let url = URL(string: playlist), M3uPlaylistFiles.isOwned(url) {
          Text("Imported playlist: " + playlistName).font(RallyDesign.font(13))
          RallyAction(title: "Use Playlist URL") { playlist = "" }
        } else {
          field("Playlist URL", text: $playlist)
        }
        field("Playlist Name (optional)", text: $playlistName)
        actionRow {
          RallyAction(title: "Import Playlist File", icon: "doc.badge.plus") { transfer = true }
        }
        Text(
          "Enter a channel playlist or single HLS URL, or import an M3U/M3U8 file from your phone or computer."
        ).font(RallyDesign.font(11)).foregroundStyle(RallyDesign.muted)
      }
      Text(
        "HTTP-only providers are supported when configured explicitly. Provider credentials stay in this device’s Keychain and are excluded from backups."
      ).font(RallyDesign.font(10)).foregroundStyle(RallyDesign.muted)
      HStack {
        RallyAction(title: "Save & Apply", primary: true) { saveProvider() }
        RallyAction(title: "Test Provider") { Task { await testProvider() } }
        RallyAction(title: "Remove Provider") {
          store.settings.clearCredentials()
          store.applySettings()
          portal = ""
          server = ""
          username = ""
          password = ""
          playlist = ""
          playlistName = ""
          message = "Provider removed."
        }
      }.frame(maxWidth: .infinity, alignment: .leading).focusSection().disabled(busy)
      sourceNotice
    }
  }
  private var addonSettings: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(12)) {
      Text("Addon Manifests").font(RallyDesign.font(18, .semibold))
      field("Manifest URL", text: $addon)
      actionRow {
        RallyAction(title: "Add Manifest", icon: "plus") { Task { await addAddon() } }.disabled(busy)
      }
      if store.settings.stremioAddonUrls.isEmpty {
        Text("No manifests configured. Rally does not bundle stream sources.").foregroundStyle(
          RallyDesign.muted)
      }
      ForEach(store.settings.stremioAddonUrls, id: \.self) { url in
        HStack {
          Text(url.host ?? "Addon").font(RallyDesign.font(13))
          Spacer()
          RallyAction(title: "Test") { Task { await testAddon(url) } }
          RallyAction(title: "Remove") {
            store.settings.stremioAddonUrls.removeAll { $0 == url }
            NetworkPolicy.shared.configure(store.settings)
            store.settingsRevision += 1
          }
        }.frame(maxWidth: .infinity, alignment: .leading).focusSection()
      }
      actionRow {
        RallyAction(title: "Remove All Manifests") {
          store.settings.stremioAddonUrls = []
          NetworkPolicy.shared.configure(store.settings)
          store.settingsRevision += 1
          message = "Manifests removed."
        }
      }
      sourceNotice
    }
  }
  private var sportSettings: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(12)) {
      Text("Enabled leagues & rail order").font(RallyDesign.font(18, .semibold))
      ForEach(Array(store.settings.sportsOrder.enumerated()), id: \.element) {
        index, league in
        HStack {
          RallyLeagueMark(league: league, size: 24)
          Text(league).font(RallyDesign.font(12)).frame(width: RallyDesign.pt(74), alignment: .leading)
          RallyAction(
            title: isEnabled(league) ? "Enabled" : "Disabled",
            icon: isEnabled(league) ? "checkmark" : nil
          ) {
            var enabled = store.settings.enabledLeagues
            if enabled.isEmpty { enabled = Set(EspnEndpoints.leagues.keys) }
            if enabled.contains(league) {
              guard enabled.count > 1 else {
                message = "Keep at least one league enabled."
                return
              }
              enabled.remove(league)
            } else {
              enabled.insert(league)
            }
            store.settings.enabledLeagues = enabled
            store.settingsRevision += 1
            Task { await store.container.sports.refresh() }
          }.accessibilityIdentifier("sport-enabled-" + league)
            .focused($accountFocus, equals: "sport-enabled-" + league)

          RallyAction(
            title: "Favorite", icon: store.settings.favoriteSports.contains(league) ? "star.fill" : "star"
          ) {
            var values = store.settings.favoriteSports
            if !values.insert(league).inserted { values.remove(league) }
            store.settings.favoriteSports = values
            store.settingsRevision += 1
          }.accessibilityIdentifier("sport-favorite-" + league)
          Spacer()
          RallyAction(title: "Up", icon: "arrow.up", bare: true) { moveLeague(index, -1) }.disabled(
            index == 0).accessibilityIdentifier("sport-up-" + league)
          RallyAction(title: "Down", icon: "arrow.down", bare: true) { moveLeague(index, 1) }
            .disabled(index == store.settings.sportsOrder.count - 1)
            .accessibilityIdentifier("sport-down-" + league)
        }.focusSection()
          .onMoveCommand { direction in
            if direction == .up, index == 0 { accountFocus = "tab-Sports" }
          }
      }
    }
  }
  private var teamSettings: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(12)) {
      Text("Followed Teams").font(RallyDesign.font(18, .semibold))
      ForEach(store.settings.followedTeams) { t in
        RallyTeamRow(
          team: t, followed: true, open: { navigate(.teamHub(league: t.league, teamId: t.teamId)) },
          follow: { store.follow(t) })
      }
      Menu {
        ForEach(store.settings.sportsOrder, id: \.self) { league in
          Button(league) { teamLeague = league }
        }
      } label: {
        Text("League: " + teamLeague)
      }.buttonStyle(RallyButtonStyle())
      field("Find Team", text: $teamQuery)
      ForEach(
        teams.filter { teamQuery.isEmpty || $0.name.localizedCaseInsensitiveContains(teamQuery) }
      ) { t in
        RallyTeamRow(
          team: t, followed: store.settings.favoriteTeamKeys.contains(t.key),
          open: { navigate(.teamHub(league: t.league, teamId: t.teamId)) },
          follow: { store.follow(t) })
      }
    }
  }
  private func sectionTitle(_ section: String) -> String {
    switch section {
    case "Account": return "Personalization"
    case "Appearance": return "Appearance & Accessibility"
    case "Sources": return "Sources"
    case "Alerts": return "Notifications"
    case "Support": return "About Rally"
    default: return section
    }
  }
  private var appearanceSettings: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(16)) {
      toggle("Reduce Motion", get: { store.settings.reducedMotion }, set: { store.settings.reducedMotion = $0 })
      toggle("High Contrast Focus", get: { store.settings.highContrastFocus }, set: { store.settings.highContrastFocus = $0 })
      toggle("Larger Text", get: { store.settings.largeText }, set: { store.settings.largeText = $0 })
      toggle("Spoken Score Summaries", get: { store.settings.spokenScoreSummaries },
             set: { store.settings.spokenScoreSummaries = $0 })
      toggle("Score Saver", get: { store.settings.scoreSaverEnabled }, set: { store.settings.scoreSaverEnabled = $0 })
    }
  }
  private var playbackSettings: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(12)) {
      toggle(
        "Low Latency", get: { store.settings.lowLatencyMode },
        set: { store.settings.lowLatencyMode = $0 })
      toggle(
        "Adaptive Quality", get: { store.settings.adaptiveQualityEnabled },
        set: { store.settings.adaptiveQualityEnabled = $0 })
      VStack(alignment: .leading, spacing: RallyDesign.pt(12)) {
        Text("Audio Normalization").font(RallyDesign.font(15, .semibold))
        Text(
          "Apple TV manages sound compression with Reduce Loud Sounds. Enable it in Apple TV Settings → Video and Audio. AVPlayer’s live HLS path does not expose Android’s per-player limiter."
        ).font(RallyDesign.font(12)).foregroundStyle(RallyDesign.muted)
        RallyAction(title: "Apple TV Audio Help") {
          link = SupportLink(
            title: "Reduce Loud Sounds",
            url: URL(
              string:
                "https://support.apple.com/guide/tv/adjust-audio-and-video-settings-atvba773c3c9/tvos"
            )!)
        }
      }
    }
  }
  private var supportSettings: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(14)) {
      Text(
        "Rally for Apple TV · \(Bundle.main.object(forInfoDictionaryKey:"CFBundleShortVersionString") as? String ?? "1.0")"
      ).font(RallyDesign.font(17, .semibold))
      HStack {
        RallyAction(title: "Run Diagnostics") { Task { await runDiagnostics() } }.disabled(busy)
        RallyAction(title: "Clear Diagnostics") {
          diagnostics = ""
          RallyDiagnostics.shared.clear()
          message = "Diagnostics cleared."
        }
        RallyAction(title: "Export Support Report") {
          Task {
            await runDiagnostics()
            transfer = true
          }
        }
      }
      if !diagnostics.isEmpty {
        Text(diagnostics).font(RallyDesign.font(11)).foregroundStyle(RallyDesign.muted).lineSpacing(
          RallyDesign.pt(5))
      }
      HStack {
        RallyAction(title: "Check for Updates") { Task { await checkUpdates() } }.disabled(busy)
      }
      Text(
        "tvOS installs are distributed through App Store or TestFlight. Android APKs cannot be installed on Apple TV."
      ).font(RallyDesign.font(11)).foregroundStyle(RallyDesign.muted)
      HStack {
        RallyAction(title: "Privacy") {
          link = SupportLink(
            title: "Rally Privacy",
            url: URL(string: "https://github.com/shivpatell25/rally/blob/main/PRIVACY.md")!)
        }
        RallyAction(title: "Releases") {
          link = SupportLink(
            title: "Rally Releases",
            url: URL(string: "https://github.com/shivpatell25/rally/releases")!)
        }
      }
    }
  }
  // Full-width focus regions bridge the right-aligned inputs and compact left actions.
  // Keep the visible buttons compact; the native focus engine uses the enclosing row.
  private func actionRow<Content: View>(@ViewBuilder content: () -> Content) -> some View {
    HStack { content() }.frame(maxWidth: .infinity, alignment: .leading).focusSection()
  }
  private func field(_ title: String, text: Binding<String>, secure: Bool = false) -> some View {
    HStack(spacing: RallyDesign.pt(12)) {
      Text(title).font(RallyDesign.font(12)).frame(width: RallyDesign.pt(138), alignment: .leading)
      Group {
        if secure {
          SecureField("Provider password", text: text)
        } else {
          TextField(title, text: text)
        }
      }.font(RallyDesign.font(14)).frame(maxWidth: .infinity)
        .frame(height: RallyDesign.pt(34)).autocorrectionDisabled()
        .textInputAutocapitalization(.never)
        .keyboardType(title.contains("URL") ? .URL : .default)
        .submitLabel(.done)
        .accessibilityIdentifier("settings-input-" + title)
    }.frame(maxWidth: .infinity).focusSection()
  }
  private func toggle(_ title: String, get: @escaping () -> Bool, set: @escaping (Bool) -> Void)
    -> some View
  {
    Toggle(
      title,
      isOn: Binding(
        get: get,
        set: { value in
          set(value)
          store.settingsRevision += 1
        })
    ).font(RallyDesign.font(14)).padding(RallyDesign.pt(6))
      .accessibilityIdentifier("setting-" + title)
  }
  private func isEnabled(_ league: String) -> Bool {
    store.settings.enabledLeagues.isEmpty
      || store.settings.enabledLeagues.contains(league)
  }
  private func moveLeague(_ index: Int, _ direction: Int) {
    var order = store.settings.sportsOrder
    let next = index + direction
    guard order.indices.contains(next) else { return }
    order.swapAt(index, next)
    store.settings.sportsOrder = order
    store.settingsRevision += 1
  }
  private func saveProvider() {
    let raw = provider == .stalker ? portal : provider == .xtream ? server : playlist
    guard let url = URL(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)),
      (["http", "https"].contains(url.scheme ?? "") && url.host != nil)
        || (provider == .m3u && M3uPlaylistFiles.isOwned(url))
    else {
      message = "Enter a valid HTTP or HTTPS provider URL."
      return
    }
    if provider == .stalker
      && mac.range(of: "^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$", options: .regularExpression) == nil
    {
      message = "Enter a MAC address with six pairs separated by colons."
      return
    }
    if provider == .xtream && (username.isEmpty || password.isEmpty) {
      message = "Enter the provider username and password."
      return
    }
    let s = store.settings
    s.provider = provider
    s.portalUrl = portal
    s.macAddress = mac
    s.serialNumber = serial
    s.deviceId = device
    s.xtreamServerUrl = server
    s.xtreamUsername = username
    s.xtreamPassword = password
    if provider == .m3u {
      let previous = URL(string: s.m3uPlaylistUrl)
      s.m3uPlaylistUrl = playlist
      guard s.m3uPlaylistUrl == playlist.trimmingCharacters(in: .whitespacesAndNewlines) else {
        message = "The playlist address could not be saved to Keychain. Try again."
        return
      }
      s.m3uPlaylistName = playlistName
      if previous?.absoluteString != s.m3uPlaylistUrl { M3uPlaylistFiles.removeOwned(previous) }
    }
    s.setupComplete = true
    guard provider != .xtream || s.xtreamPassword == password else {
      message = "The password could not be saved to Keychain. Try again."
      return
    }
    store.applySettings()
    message = "Provider saved. Test it or open Live TV."
  }
  private func testProvider() async {
    saveProvider()
    guard message?.hasPrefix("Provider saved") == true else { return }
    busy = true
    let ok = await store.container.iptv.authenticate()
    if ok {
      let channels = (try? await store.container.iptv.channels()) ?? []
      message = "Connected · \(channels.count) channels."
    } else {
      if provider == .m3u {
        do { _ = try await store.container.iptv.refreshChannels() } catch {
          message =
            (error as? M3uPlaylistError)?.localizedDescription
            ?? "The playlist could not be loaded. Check its URL and network connection."
          busy = false
          return
        }
      }
      message = "Authentication failed. Check the provider URL and identity fields."
    }
    busy = false
  }
  private func addAddon() async {
    guard let url = PortalUrlNormalizer.normalizeAddon(addon),
      ["http", "https"].contains(url.scheme ?? "")
    else {
      message = "Enter a valid addon manifest URL."
      return
    }
    busy = true
    await testAddon(url)
    if message?.hasPrefix("Addon available") == true {
      if !store.settings.stremioAddonUrls.contains(url) {
        store.settings.stremioAddonUrls.append(url)
      }
      NetworkPolicy.shared.configure(store.settings)
      addon = ""
      store.settingsRevision += 1
    }
    busy = false
  }
  private func testAddon(_ url: URL) async {
    NetworkPolicy.shared.allowTemporarily(url)
    defer { NetworkPolicy.shared.revokeTemporary(url) }
    let manifest =
      url.path.hasSuffix("manifest.json") ? url : url.appendingPathComponent("manifest.json")
    do {
      let result: StremioManifest = try await store.container.http.json(url: manifest)
      message =
        "Addon available: \(result.name ?? "Sports addon") · \(result.catalogs.count) catalogs."
    } catch { message = "This addon manifest could not be loaded." }
  }
  private func runDiagnostics() async {
    busy = true
    let events = (try? await store.container.sports.recentEvents()) ?? []
    let ok = await store.container.iptv.authenticate()
    diagnostics =
      "Rally tvOS · AVPlayer\nSports data: \(events.count) events\nIPTV: \(ok ? "Authenticated" : "Not configured or unavailable")\nAddons: \(store.settings.stremioAddonUrls.count) configured\nAccessibility: motion \(store.settings.reducedMotion ? "reduced" : "standard")\nNo provider URLs, identifiers or credentials are included."
    diagnostics += "\n\nRecent diagnostics\n" + RallyDiagnostics.shared.report()
    store.settings.supportReport = diagnostics
    busy = false
  }
  private func checkUpdates() async {
    busy = true
    do {
      let release: JSONValue = try await store.container.http.json(
        url: URL(string: "https://api.github.com/repos/shivpatell25/rally/releases/latest")!)
      message =
        "Latest Rally release: \(release["tag_name"].text). Apple TV builds require App Store or TestFlight distribution."
    } catch { message = "Couldn’t check releases. Try again later." }
    busy = false
  }
}
struct SupportLink: Identifiable {
  var id: String { url.absoluteString }
  let title: String
  let url: URL
}
struct SupportLinkSheet: View {
  @Environment(\.dismiss) private var dismiss
  let item: SupportLink
  var body: some View {
    RallyCanvas {
      ZStack {
        RallyBackdrop()
        VStack(spacing: RallyDesign.pt(18)) {
          Text(item.title).font(RallyDesign.font(26, .bold))
          QRImage(value: item.url.absoluteString).frame(
            width: RallyDesign.pt(150), height: RallyDesign.pt(150))
          Text("Scan with your phone").font(RallyDesign.font(15))
          Text(item.url.absoluteString).font(RallyDesign.font(12)).foregroundStyle(
            RallyDesign.muted)
          RallyAction(title: "Done") { dismiss() }
        }
      }
    }.onExitCommand { dismiss() }.presentationBackground(.black)
  }
}
struct QRImage: View {
  let value: String
  private var image: UIImage? {
    let f = CIFilter.qrCodeGenerator()
    f.message = Data(value.utf8)
    guard let output = f.outputImage,
      let cg = CIContext().createCGImage(
        output.transformed(by: CGAffineTransform(scaleX: 8, y: 8)),
        from: output.extent.applying(CGAffineTransform(scaleX: 8, y: 8)))
    else { return nil }
    return UIImage(cgImage: cg)
  }
  var body: some View {
    Group {
      if let image {
        Image(uiImage: image).interpolation(.none).resizable().scaledToFit().padding(
          RallyDesign.pt(8)
        ).background(
          .white)
      }
    }
  }
}
