import SwiftUI

struct MyRallyScreen: View {
  @Environment(RallyStore.self) private var store
  let navigate: (RallyRoute) -> Void
  @State private var events: [SportEvent] = []
  @State private var error: String?
  @State private var loading = true
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(16)) {
      RallySectionHeader(title: "My Rally", actionTitle: "Manage Teams") { navigate(.settings) }
      if loading {
        RallyLoading()
      } else {
        ScrollView {
          LazyVStack(alignment: .leading, spacing: RallyDesign.pt(14)) {
            if let error {
              RallyEmptyState(
                title: "Couldn’t load your games", message: error, actionTitle: "Retry"
              ) { Task { await load() } }
            }
            RallySectionHeader(title: "Your Teams")
            if store.settings.followedTeams.isEmpty {
              RallyEmptyState(
                title: "Follow your teams",
                message: "Your teams, their games and score updates will appear here.",
                actionTitle: "Browse Leagues"
              ) { navigate(.leagues) }
            }
            ForEach(store.settings.followedTeams) { t in
              RallyTeamRow(
                team: t, followed: true,
                open: { navigate(.teamHub(league: t.league, teamId: t.teamId)) },
                follow: { store.follow(t) })
            }
            RallySectionHeader(title: "Your Games")
            let games = events.filter { e in
              store.settings.favoriteTeamKeys.contains(
                "\(e.league):\(e.homeTeam?.id ?? "")")
                || store.settings.favoriteTeamKeys.contains(
                  "\(e.league):\(e.awayTeam?.id ?? "")")
                || store.settings.savedEventIds.contains(e.id)
            }
            if games.isEmpty {
              RallyEmptyState(
                title: "No saved games", message: "Save an event or follow a team to keep it here.",
                actionTitle: "Schedule"
              ) { navigate(.schedule) }
            }
            ForEach(games) { e in
              HStack {
                RallyScheduleRow(
                  event: e, reminder: store.settings.reminderIds.contains(e.id),
                  action: { navigate(.eventDetail(eventId: e.id)) },
                  remind: { store.reminder(e.id) })
                if store.settings.savedEventIds.contains(e.id) {
                  RallyAction(title: "Remove", bare: true) { store.save(e.id) }
                }
              }
            }
          }.padding(.vertical, RallyDesign.pt(8))
        }.scrollClipDisabled().focusSection()
      }
    }.padding(.horizontal, RallyDesign.pt(60)).padding(.top, RallyDesign.pt(12)).task(
      id: store.settingsRevision
    ) { await load() }

  }
  private func load() async {
    loading = events.isEmpty
    let missing = store.settings.favoriteTeamKeys.subtracting(
      Set(store.settings.followedTeams.map(\.key)))
    for league in Set(missing.compactMap { $0.split(separator: ":").first.map(String.init) }) {
      let teams = (try? await store.container.sports.teams(league: league)) ?? []
      store.settings.mergeTeamProfiles(teams)
    }
    do {
      events = try await store.container.sports.recentEvents()
      error = nil
    } catch { self.error = "Check your connection and try again." }
    loading = false
  }
}
