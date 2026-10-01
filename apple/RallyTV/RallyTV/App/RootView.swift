import SwiftUI

struct RootView: View {
  let container: AppContainer
  @Binding var path: [RallyRoute]
  @State private var store: RallyStore
  @State private var onboarding: Bool
  @State private var saver = false
  init(container: AppContainer, path: Binding<[RallyRoute]>) {
    self.container = container
    _path = path
    _store = State(initialValue: RallyStore(container: container))
    _onboarding = State(
      initialValue: !container.settings.setupComplete
        && !ProcessInfo.processInfo.arguments.contains("--ui-testing"))
  }
  var body: some View {
    // Re-evaluate native environment values when non-observable preferences change.
    let _ = store.settingsRevision
    NavigationStack(path: $path) {
      shell(.home).navigationDestination(for: RallyRoute.self) { route in shell(route) }
    }.environment(store).preferredColorScheme(.dark)
      .onOpenURL { url in if let route = RallyDeepLink.route(url) { navigate(route) } }
      .task {
        #if DEBUG
          if let index = ProcessInfo.processInfo.arguments.firstIndex(of: "--route"),
            ProcessInfo.processInfo.arguments.count > index + 1,
            let route = RallyDeepLink.route(
              URL(string: "rally://" + ProcessInfo.processInfo.arguments[index + 1])!)
          {
            navigate(route)
          }
        #endif
        while !Task.isCancelled {
          do { try await Task.sleep(for: .seconds(15)) } catch { return }
          saver =
            container.settings.scoreSaverEnabled && !store.isPlaying && !onboarding
            && Date().timeIntervalSince(store.lastInteraction) > 300
        }
      }
      .overlay {
        if onboarding {
          RallyCanvas {
            ZStack {
              RallyBackdrop()
              VStack(spacing: RallyDesign.pt(20)) {
                BundleArt.image("rally_wordmark_color_ui.png").resizable().scaledToFit().frame(
                  width: RallyDesign.pt(160), height: RallyDesign.pt(70))
                Text("Your sports. Your sources. One place.").font(RallyDesign.font(25, .semibold))
                Text("Follow teams, track games, and connect your IPTV provider or sports addons.")
                  .font(RallyDesign.font(14)).foregroundStyle(RallyDesign.muted)
                RallyAction(title: "Continue to Setup", primary: true) {
                  onboarding = false
                  container.settings.setupComplete = true
                  navigate(.settings)
                }
              }
            }
          }.environment(store)
        }
      }
      .overlay {
        if saver {
          ScoreSaverScreen(events: store.previousEvents) {
            saver = false
            store.interacted()
          }.environment(store)
        }
      }
  }
  private func navigate(_ route: RallyRoute) {
    store.interacted()
    if route == .home {
      path.removeAll()
      store.homeReset += 1
    } else if path.last != route {
      path.append(route)
    }
  }
  private func shell(_ route: RallyRoute) -> some View {
    RallyCanvas {
      ZStack(alignment: .top) {
        RallyBackdrop()
        VStack(spacing: RallyDesign.pt(0)) {
          if !route.isPlayback {
            RallyTopNav(selected: route.tab, navigate: navigate).frame(height: RallyDesign.pt(70))
          }
          screen(route).frame(
            width: RallyDesign.pt(960), height: RallyDesign.pt(route.isPlayback ? 540 : 470),
            alignment: .top
          )
          .clipped()
        }
        if let alert = store.alert, !route.isPlayback {
          VStack {
            Spacer()
            RallyAction(title: alert.title + " · " + alert.message) {
              store.alert = nil
              if let id = alert.eventId {
                navigate(.eventDetail(eventId: id))
              } else if let channel = store.alertChannelId {
                navigate(.player(target: channel, eventId: nil))
              }
            }.padding(RallyDesign.pt(18))
          }.task(id: alert.id) {
            try? await Task.sleep(for: .seconds(8))
            if store.alert?.id == alert.id { store.alert = nil }
          }
        }
      }.font(RallyDesign.font(12)).dynamicTypeSize(container.settings.largeText ? .xxLarge : .large)
        .foregroundStyle(.white)
        .onMoveCommand { _ in store.interacted() }.onPlayPauseCommand { store.interacted() }
    }.toolbar(.hidden, for: .navigationBar)
  }
  @ViewBuilder private func screen(_ route: RallyRoute) -> some View {
    switch route {
    case .home: HomeScreen(navigate: navigate)
    case .schedule: ScheduleScreen(navigate: navigate)
    case .live: LiveScreen(navigate: navigate)
    case .leagues: LeaguesScreen(navigate: navigate)
    case .leagueHub(let league): LeagueScreen(league: league, navigate: navigate)
    case .teamHub(let league, let id): TeamScreen(league: league, teamId: id, navigate: navigate)
    case .eventDetail(let id): EventScreen(eventId: id, navigate: navigate)
    case .player(let target, let id):
      GameViewScreen(target: target, eventId: id, navigate: navigate)
    case .playerSource(let candidate, let id):
      GameViewScreen(
        target: candidate.channel?.id ?? candidate.playbackTarget.absoluteString,
        eventId: id, initialCandidate: candidate, navigate: navigate)
    case .playerClip(let url, let title, let id):
      GameViewScreen(
        target: url.absoluteString, eventId: id, initialClipTitle: title, navigate: navigate)
    case .playerFull(let target, let candidate, let id):
      GameViewScreen(
        target: target, eventId: id, initialCandidate: candidate, startFullscreen: true,
        navigate: navigate)
    case .multiView(let channel, let event, let ids):
      MultiViewScreen(channelId: channel, eventId: event, eventIds: ids, navigate: navigate)
    case .multiViewSource(let candidate, let event):
      MultiViewScreen(
        channelId: nil, eventId: event, eventIds: [], initialSource: candidate, navigate: navigate)
    case .iptvBrowser: ChannelScreen(navigate: navigate)
    case .search: SearchScreen(navigate: navigate)
    case .highlights: HighlightsScreen(navigate: navigate)
    case .watchlist: MyRallyScreen(navigate: navigate)
    case .settings: SettingsScreen(navigate: navigate)
    }
  }
}
struct RallyTopNav: View {
  let selected: String
  let navigate: (RallyRoute) -> Void
  private let tabs: [(String, RallyRoute)] = [
    ("Home", .home), ("Live", .live), ("Schedule", .schedule), ("Leagues", .leagues),
    ("Highlights", .highlights), ("My Rally", .watchlist),
  ]
  var body: some View {
    HStack(spacing: RallyDesign.pt(25)) {
      BundleArt.image("rally_wordmark_color_ui.png").resizable().scaledToFit()
        .frame(width: RallyDesign.pt(94), height: RallyDesign.pt(40)).accessibilityLabel("Rally")
      Spacer().frame(width: RallyDesign.pt(23))
      ForEach(tabs, id: \.0) { name, route in
        Button {
          navigate(route)
        } label: {
          Text(name).font(RallyDesign.font(12, selected == name ? .semibold : .regular))
            .lineLimit(1).fixedSize(horizontal: true, vertical: false)
            .foregroundStyle(selected == name ? .white : RallyDesign.muted)
            .padding(.horizontal, RallyDesign.pt(6)).padding(.vertical, RallyDesign.pt(8))
        }.buttonStyle(RallyButtonStyle(bare: true, selected: selected == name))
          .focusEffectDisabled().accessibilityIdentifier(
            "nav-\(name)")
      }
      Spacer(minLength: RallyDesign.pt(20))
      RallyAction(title: "", icon: "magnifyingglass", bare: true) { navigate(.search) }
        .accessibilityLabel("Search").accessibilityIdentifier("nav-Search")
      RallyAction(title: "", icon: "gearshape", bare: true) { navigate(.settings) }
        .accessibilityLabel("Settings").accessibilityIdentifier("nav-Settings")
    }.padding(.horizontal, RallyDesign.pt(60)).focusSection()
  }
}
extension RallyRoute {
  var isPlayback: Bool {
    switch self {
    case .player, .playerSource, .playerClip, .playerFull, .multiView, .multiViewSource: return true
    default: return false
    }
  }
  var tab: String {
    switch self {
    case .home: return "Home"
    case .live, .iptvBrowser: return "Live"
    case .schedule: return "Schedule"
    case .leagues, .leagueHub, .teamHub: return "Leagues"
    case .highlights: return "Highlights"
    case .watchlist: return "My Rally"
    case .search: return "Search"
    case .settings: return "Settings"
    default: return "Live"
    }
  }
}
enum RallyDeepLink {
  static func route(_ url: URL) -> RallyRoute? {
    let parts = ([url.host ?? ""] + url.pathComponents.filter { $0 != "/" }).filter { !$0.isEmpty }
    let q = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
    func query(_ key: String) -> String? { q.first { $0.name == key }?.value }
    guard let first = parts.first else { return nil }
    switch first.lowercased() {
    case "home": return .home
    case "live", "live-games": return .live
    case "schedule": return .schedule
    case "leagues": return .leagues
    case "league": return parts.count > 1 ? .leagueHub(league: parts[1]) : .leagues
    case "team": return parts.count > 2 ? .teamHub(league: parts[1], teamId: parts[2]) : .watchlist
    case "event": return parts.count > 1 ? .eventDetail(eventId: parts[1]) : nil
    case "player":
      if let title = query("clipTitle"), let target = query("target"),
        let url = URL(string: target),
        ["https", "http"].contains(url.scheme ?? "")
      {
        return .playerClip(url: url, title: title, eventId: query("eventId"))
      }
      return .player(
        target: query("target") ?? parts.dropFirst().joined(separator: "/"),
        eventId: query("eventId"))
    case "multiview":
      return .multiView(
        channelId: query("channelId"), eventId: query("eventId"),
        eventIds: (query("eventIds") ?? "").components(separatedBy: ",").filter { !$0.isEmpty })
    case "search": return .search
    case "highlights": return .highlights
    case "watchlist", "my-rally": return .watchlist
    case "settings": return .settings
    case "iptv": return .iptvBrowser
    default: return nil
    }
  }
}
struct ScoreSaverScreen: View {
  let events: [SportEvent]
  let dismiss: () -> Void
  var body: some View {
    RallyCanvas {
      ZStack {
        RallyBackdrop()
        VStack(alignment: .leading, spacing: RallyDesign.pt(18)) {
          Text(Date(), style: .time).font(RallyDesign.font(42, .light))
          Text("Around Rally").font(RallyDesign.font(22, .semibold))
          ForEach(Array(events.filter { $0.status.isLive || $0.status == .finished }.prefix(5))) {
            e in
            HStack {
              Text(e.compactMatchup)
              Spacer()
              Text(e.scoreLine)
              Text(e.statusLabel).foregroundStyle(RallyDesign.muted)
            }.font(RallyDesign.font(15))
          }
          RallyAction(title: "Back to Rally", action: dismiss)
        }.padding(RallyDesign.pt(60))
      }
    }.onMoveCommand { _ in dismiss() }.onExitCommand(perform: dismiss).onPlayPauseCommand(
      perform: dismiss)
  }
}
