import SwiftUI

struct SearchScreen: View {
  @Environment(RallyStore.self) private var store
  let navigate: (RallyRoute) -> Void
  @State private var query = ""
  @State private var events: [SportEvent] = []
  @State private var teams: [FavoriteTeam] = []
  @State private var channels: [IptvChannel] = []
  @State private var streams: [StremioStreamOption] = []
  @State private var indexing = false
  @State private var loading = false
  @State private var error: String?
  private var leagues: [String] {
    store.container.settings.sportsOrder.filter {
      $0.localizedCaseInsensitiveContains(query)
        || (query.lowercased().contains("soccer")
          && ["EPL", "MLS", "La Liga", "Champions League", "Serie A"].contains($0))
        || (query.lowercased().contains("college") && $0.hasPrefix("NCAA"))
    }
  }
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(14)) {
      RallySectionHeader(title: "Search Rally")
      TextField("Teams, games, leagues or channels", text: $query).font(RallyDesign.font(18)).frame(
        height: RallyDesign.pt(45)
      ).accessibilityIdentifier("Search query")
      if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        RallyEmptyState(
          title: "Find your next game",
          message: "Search live games, teams, leagues, and your TV channels.")
      } else if loading {
        RallyLoading(title: "Searching…")
      } else {
        ScrollView {
          LazyVStack(alignment: .leading, spacing: RallyDesign.pt(12)) {
            if let error { Text(error).foregroundStyle(RallyDesign.muted) }
            if !leagues.isEmpty {
              RallySectionHeader(title: "Leagues")
              HStack {
                ForEach(leagues, id: \.self) { l in
                  RallyLeagueShortcut(league: l) { navigate(.leagueHub(league: l)) }.frame(
                    width: RallyDesign.pt(100))
                }
              }
            }
            if !events.isEmpty {
              RallySectionHeader(title: "Games")
              ForEach(events) { e in
                RallyScheduleRow(
                  event: e, action: { navigate(.eventDetail(eventId: e.id)) },
                  remind: { store.reminder(e.id) })
              }
            }
            if !teams.isEmpty {
              RallySectionHeader(title: "Teams")
              ForEach(teams) { t in
                RallyTeamRow(
                  team: t, followed: store.container.settings.favoriteTeamKeys.contains(t.key),
                  open: { navigate(.teamHub(league: t.league, teamId: t.teamId)) },
                  follow: { store.follow(t) })
              }
            }
            if !channels.isEmpty {
              RallySectionHeader(title: "Channels")
              ForEach(channels) { c in
                RallyAction(title: c.name, bare: true) {
                  navigate(.player(target: c.id, eventId: nil))
                }
              }
            }
            if !streams.isEmpty {
              RallySectionHeader(title: "Addon Streams")
              ForEach(streams, id: \.streamUrl) { stream in
                RallyAction(title: stream.title, icon: "play.fill", bare: true) {
                  navigate(.playerSource(candidate: StreamCandidate(addon: stream), eventId: nil))
                }
              }
            }
            if indexing {
              Text("Searching team and channel catalogs…").font(RallyDesign.font(10))
                .foregroundStyle(RallyDesign.muted)
            }
            if !indexing && events.isEmpty && teams.isEmpty && channels.isEmpty && streams.isEmpty
              && leagues.isEmpty
            {
              RallyEmptyState(title: "No results", message: "Try a team name, league or channel.")
            }
          }.padding(.vertical, RallyDesign.pt(8))
        }.focusSection()
      }
    }.padding(.horizontal, RallyDesign.pt(60)).padding(.top, RallyDesign.pt(12)).task(id: query) {
      await search()
    }

  }
  private func search() async {
    let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !q.isEmpty else { return }
    loading = true
    do {
      try await Task.sleep(for: .milliseconds(250))
      events = try await store.container.sports.searchEvents(query: q)
      error = nil
    } catch {
      if Task.isCancelled { return }
      self.error = "Some search results could not be loaded."
    }
    guard !Task.isCancelled else { return }
    loading = false
    indexing = true
    teams = []
    channels = []
    streams = []
    let repository = store.container.sports
    let order = store.container.settings.sportsOrder
    async let channelResults = store.container.iptv.searchChannels(query: q, limit: 50)
    async let addonResults = store.container.stremio.searchStreams(query: q)
    for offset in stride(from: 0, to: order.count, by: 3) {
      let batch = Array(order.dropFirst(offset).prefix(3))
      let found = await withTaskGroup(of: [FavoriteTeam].self) { group in
        for league in batch {
          group.addTask {
            let directory = (try? await repository.teams(league: league)) ?? []
            return directory.filter {
              ($0.name + " " + $0.abbreviation).localizedCaseInsensitiveContains(q)
            }
          }
        }
        var found: [FavoriteTeam] = []
        for await result in group { found += result }
        return found
      }
      guard !Task.isCancelled else { return }
      teams += found.sorted { $0.name < $1.name }
    }
    let foundChannels = (try? await channelResults) ?? []
    let foundStreams = await addonResults
    guard !Task.isCancelled else { return }
    channels = foundChannels
    streams = foundStreams.filter(\.isDirectPlayable)
    indexing = false
  }
}
