import SwiftUI

struct LiveScreen: View {
  @Environment(RallyStore.self) private var store
  let navigate: (RallyRoute) -> Void
  @State private var events: [SportEvent] = []
  @State private var loading = true
  @State private var error: String?
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(20)) {
      RallySectionHeader(title: "Live Now", actionTitle: "Browse Live TV") {
        navigate(.iptvBrowser)
      }
      if loading {
        RallyLoading()
      } else if let error {
        RallyEmptyState(title: "Live games unavailable", message: error, actionTitle: "Retry") {
          Task { await load() }
        }
      } else if events.isEmpty {
        RallyEmptyState(
          title: "No games are live right now.",
          message: "Browse your channels or check the schedule.", actionTitle: "Browse Live TV"
        ) { navigate(.iptvBrowser) }
      } else {
        ScrollView {
          LazyVGrid(
            columns: Array(
              repeating: GridItem(.fixed(RallyDesign.pt(272)), spacing: RallyDesign.pt(12)),
              count: 3),
            alignment: .leading, spacing: RallyDesign.pt(24)
          ) {
            ForEach(events) { e in RallyLiveCard(event: e) { navigate(.eventDetail(eventId: e.id)) }
            }
          }.padding(.vertical, RallyDesign.pt(8))
        }.scrollClipDisabled().focusSection()
      }
    }.padding(.horizontal, RallyDesign.pt(60)).padding(.top, RallyDesign.pt(12)).task {
      await load()
      while !Task.isCancelled {
        do { try await Task.sleep(for: .seconds(30)) } catch { return }
        await load()
      }
    }
  }
  private func load() async {
    do {
      events = try await store.container.sports.liveEvents()
      error = nil
    } catch { self.error = "Check your connection and try again." }
    loading = false
  }
}
struct ChannelScreen: View {
  @Environment(RallyStore.self) private var store
  let navigate: (RallyRoute) -> Void
  @State private var channels: [IptvChannel] = []
  @State private var loading = true
  @State private var error: String?
  @State private var query = ""
  @State private var category = "All"
  private var visible: [IptvChannel] {
    channels.filter {
      (category == "All" || $0.category == category)
        && (query.isEmpty || ($0.name + " " + $0.number).localizedCaseInsensitiveContains(query))
    }
  }
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(14)) {
      RallySectionHeader(title: "Live TV", actionTitle: "Refresh") {
        Task { await load(refresh: true) }
      }
      HStack {
        TextField("Search channels", text: $query).font(RallyDesign.font(14)).frame(
          width: RallyDesign.pt(450), height: RallyDesign.pt(34))
        Spacer()
        Menu {
          Button("All categories") { category = "All" }
          ForEach(Array(Set(channels.map(\.category))).sorted(), id: \.self) { name in
            Button(name) { category = name }
          }
        } label: {
          Text(category)
        }.buttonStyle(RallyButtonStyle())
      }.focusSection()
      if loading {
        RallyLoading(title: "Loading channels…")
      } else if let error {
        RallyEmptyState(title: "Connect your TV source", message: error, actionTitle: "Settings") {
          navigate(.settings)
        }
      } else if visible.isEmpty {
        RallyEmptyState(
          title: "No channels found",
          message: "Connect an IPTV provider in Settings, or change your search.",
          actionTitle: "Settings"
        ) { navigate(.settings) }
      } else {
        ScrollView {
          LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: RallyDesign.pt(12)), count: 4),
            spacing: RallyDesign.pt(12)
          ) {
            ForEach(visible) { c in
              Button {
                navigate(.player(target: c.id, eventId: nil))
              } label: {
                VStack(spacing: RallyDesign.pt(8)) {
                  RallyRemoteImage(url: c.logoUrl, fit: true).frame(height: RallyDesign.pt(45))
                  Text(c.name).font(RallyDesign.font(12, .medium)).lineLimit(2)
                  Text(c.number + " · " + c.category).font(RallyDesign.font(9)).foregroundStyle(
                    RallyDesign.muted)
                }.padding(RallyDesign.pt(12)).frame(maxWidth: .infinity).frame(
                  height: RallyDesign.pt(110)
                ).background(
                  RallyDesign.surface, in: RoundedRectangle(cornerRadius: RallyDesign.pt(8)))
              }.buttonStyle(RallyMediaFocus()).focusEffectDisabled()
                .accessibilityIdentifier("channel-" + c.id)
            }
          }.padding(.vertical, RallyDesign.pt(8))
        }.scrollClipDisabled().focusSection()
      }
    }.padding(.horizontal, RallyDesign.pt(60)).padding(.top, RallyDesign.pt(12)).task(
      id: store.settingsRevision
    ) { await load() }

  }
  private func load(refresh: Bool = false) async {
    loading = channels.isEmpty
    guard await store.container.iptv.authenticate() else {
      error = "Provider authentication failed or no provider is configured."
      loading = false
      return
    }
    do {
      channels =
        try await
        (refresh ? store.container.iptv.refreshChannels() : store.container.iptv.channels())
      error = nil
    } catch { self.error = "Couldn’t load this provider’s channel catalog." }
    loading = false
  }
}
