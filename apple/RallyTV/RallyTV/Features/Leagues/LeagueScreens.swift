import SwiftUI

struct LeaguesScreen: View {
  @Environment(RallyStore.self) private var store
  let navigate: (RallyRoute) -> Void
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(20)) {
      RallySectionHeader(title: "Leagues")
      ScrollView {
        LazyVGrid(
          columns: Array(repeating: GridItem(.flexible(), spacing: RallyDesign.pt(18)), count: 5),
          spacing: RallyDesign.pt(20)
        ) {
          ForEach(
            store.container.settings.sportsOrder.filter {
              store.container.settings.enabledLeagues.isEmpty
                || store.container.settings.enabledLeagues.contains($0)
            }, id: \.self
          ) { league in
            Button {
              navigate(.leagueHub(league: league))
            } label: {
              VStack(spacing: RallyDesign.pt(12)) {
                RallyLeagueMark(league: league, size: 52)
                Text(league).font(RallyDesign.font(14, .medium))
              }.frame(maxWidth: .infinity).frame(height: RallyDesign.pt(116)).background(
                RallyDesign.surface.opacity(0.5),
                in: RoundedRectangle(cornerRadius: RallyDesign.pt(8)))
            }.buttonStyle(RallyMediaFocus()).focusEffectDisabled()
          }
        }.padding(.vertical, RallyDesign.pt(10))
      }.focusSection()
    }.padding(.horizontal, RallyDesign.pt(60)).padding(.top, RallyDesign.pt(12))
  }
}
struct LeagueScreen: View {
  @Environment(RallyStore.self) private var store
  let league: String
  let navigate: (RallyRoute) -> Void
  @State private var hub: LeagueHub?
  @State private var teams: [FavoriteTeam] = []
  @State private var tab = "Games"
  @State private var loading = true
  @State private var error: String?
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(16)) {
      HStack {
        RallyLeagueMark(league: league, size: 38)
        Text(league).font(RallyDesign.font(25, .semibold))
        Spacer()
        if league == "NFL" {
          RallyAction(title: "NFL RedZone") {
            Task {
              let channels = (try? await store.container.iptv.channels()) ?? []
              if let c = channels.first(where: {
                TextMatching.normalize($0.name).contains("redzone")
              }) {
                navigate(.player(target: c.id, eventId: nil))
              } else {
                navigate(.iptvBrowser)
              }
            }
          }
        }
      }
      RallyTabs(tabs: ["Games", "Standings", "Playoffs", "Teams"], selection: $tab)
      if loading {
        RallyLoading()
      } else if let error {
        RallyEmptyState(title: "League unavailable", message: error, actionTitle: "Retry") {
          Task { await load() }
        }
      } else {
        ScrollView {
          LazyVStack(alignment: .leading, spacing: RallyDesign.pt(8)) {
            if tab == "Games" {
              if hub?.events.isEmpty != false {
                RallyEmptyState(
                  title: "No games scheduled", message: "Check back for the next slate.")
              }
              ForEach(hub?.events ?? []) { e in
                RallyScheduleRow(
                  event: e, action: { navigate(.eventDetail(eventId: e.id)) },
                  remind: { store.reminder(e.id) })
              }
            } else if tab == "Teams" {
              ForEach(teams) { t in
                RallyTeamRow(
                  team: t, followed: store.container.settings.favoriteTeamKeys.contains(t.key),
                  open: { navigate(.teamHub(league: league, teamId: t.teamId)) },
                  follow: { store.follow(t) })
              }
            } else {
              let rows = tab == "Standings" ? (hub?.standings ?? []) : (hub?.playoffPicture ?? [])
              if rows.isEmpty {
                RallyEmptyState(
                  title: tab == "Standings"
                    ? "Standings aren’t published yet" : "Playoff seeds aren’t published yet",
                  message: "Rally shows the league’s published records and seeds.")
              }
              ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                RallyAction(title: row.0 + "   ·   " + row.1, bare: true) {
                  if let team = teams.first(where: { $0.name == row.0 }) {
                    navigate(.teamHub(league: league, teamId: team.teamId))
                  }
                }.frame(maxWidth: .infinity, alignment: .leading)
              }
              if tab == "Playoffs" {
                ForEach(hub?.postseasonEvents ?? []) { e in
                  RallyScheduleRow(
                    event: e, action: { navigate(.eventDetail(eventId: e.id)) },
                    remind: { store.reminder(e.id) })
                }
              }
            }
          }.padding(.vertical, RallyDesign.pt(8))
        }.focusSection()
      }
    }.padding(.horizontal, RallyDesign.pt(60)).padding(.top, RallyDesign.pt(12)).padding(
      .bottom, RallyDesign.pt(18)
    ).task { await load() }

  }
  private func load() async {
    loading = true
    do {
      if league == "Soccer" {
        hub = LeagueHub(
          league: league, events: try await store.container.sports.events(forLeague: league))
      } else {
        hub = try await store.container.sports.leagueHub(league: league)
        teams = try await store.container.sports.teams(league: league)
      }
      error = nil
    } catch { self.error = "Check your connection and try again." }
    loading = false
  }
}
struct RallyTabs: View {
  let tabs: [String]
  @Binding var selection: String
  var compact = false
  var body: some View {
    HStack(spacing: RallyDesign.pt(compact ? 3 : 14)) {
      ForEach(tabs, id: \.self) { tab in
        Button {
          selection = tab
        } label: {
          Text(tab).font(
            RallyDesign.font(compact ? 10 : 12, selection == tab ? .semibold : .regular)
          )
          .foregroundStyle(selection == tab ? .white : RallyDesign.muted).padding(
            .horizontal, RallyDesign.pt(compact ? 3 : 8)
          )
          .padding(.vertical, RallyDesign.pt(compact ? 5 : 7)).background(
            selection == tab ? .white.opacity(0.1) : .clear,
            in: RoundedRectangle(cornerRadius: RallyDesign.pt(8))
          )
        }.buttonStyle(RallyButtonStyle(bare: true)).focusEffectDisabled().accessibilityIdentifier(
          "tab-\(tab)")
      }
    }.focusSection()
  }
}
struct RallyTeamRow: View {
  let team: FavoriteTeam
  let followed: Bool
  let open: () -> Void
  let follow: () -> Void
  var body: some View {
    HStack {
      Button(action: open) {
        HStack(spacing: RallyDesign.pt(12)) {
          RallyRemoteImage(url: team.logoUrl, fit: true).frame(
            width: RallyDesign.pt(32), height: RallyDesign.pt(32))
          Text(team.name).font(RallyDesign.font(13, .medium))
          Text(team.league).foregroundStyle(RallyDesign.muted).font(RallyDesign.font(10))
          Spacer()
        }.frame(maxWidth: .infinity)
      }.buttonStyle(RallyButtonStyle(bare: true)).focusEffectDisabled()
      RallyAction(
        title: followed ? "Following" : "Follow", icon: followed ? "checkmark" : "plus",
        action: follow)
    }.padding(RallyDesign.pt(8)).background(
      RallyDesign.surface.opacity(0.4), in: RoundedRectangle(cornerRadius: RallyDesign.pt(8)))
  }
}
struct TeamScreen: View {
  @Environment(RallyStore.self) private var store
  let league: String
  let teamId: String
  let navigate: (RallyRoute) -> Void
  @State private var hub: TeamHub?
  @State private var loading = true
  @State private var error: String?
  @State private var tab = "Overview"
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(14)) {
      if let hub {
        HStack(spacing: RallyDesign.pt(18)) {
          RallyRemoteImage(url: hub.team.logoUrl, fit: true).frame(
            width: RallyDesign.pt(58), height: RallyDesign.pt(58))
          VStack(alignment: .leading, spacing: RallyDesign.pt(5)) {
            Text(hub.team.name).font(RallyDesign.font(25, .bold))
            Text(league + "  ·  " + (hub.standing?.summary ?? "Record not published"))
              .foregroundStyle(RallyDesign.muted)
          }
          Spacer()
          RallyAction(
            title: store.container.settings.favoriteTeamKeys.contains(hub.team.key)
              ? "Following" : "Follow Team", icon: "plus"
          ) { store.follow(hub.team) }
        }
      }
      RallyTabs(tabs: ["Overview", "Games", "Roster", "Injuries"], selection: $tab)
      if loading {
        RallyLoading()
      } else if let error {
        RallyEmptyState(title: "Team unavailable", message: error, actionTitle: "Retry") {
          Task { await load() }
        }
      } else if let hub {
        ScrollView {
          LazyVStack(alignment: .leading, spacing: RallyDesign.pt(10)) {
            if tab == "Overview" || tab == "Games" {
              if hub.schedule.isEmpty {
                RallyEmptyState(
                  title: "No scheduled games",
                  message: "The team’s schedule hasn’t been published yet.")
              }
              ForEach(hub.schedule) { e in
                RallyScheduleRow(
                  event: e, reminder: store.container.settings.reminderIds.contains(e.id),
                  action: { navigate(.eventDetail(eventId: e.id)) },
                  remind: { store.reminder(e.id) })
              }
            } else if tab == "Roster" {
              if hub.roster.isEmpty {
                RallyEmptyState(
                  title: "Roster unavailable", message: "Player information has not been published."
                )
              }
              ForEach(hub.roster) { p in
                RallyAction(
                  title: "\(p.jersey.map { "#"+$0 } ?? "")  \(p.name) · \(p.position ?? "Player")",
                  bare: true
                ) { selectedPlayer = p }.frame(maxWidth: .infinity, alignment: .leading)
              }
            } else {
              if hub.injuries.isEmpty {
                RallyEmptyState(
                  title: "No reported injuries",
                  message: "Availability updates appear here when published.")
              }
              ForEach(Array(hub.injuries.enumerated()), id: \.offset) { _, i in
                RallyPanel(i.playerName) {
                  Text(i.status).foregroundStyle(RallyDesign.muted)
                  Text(i.detail ?? "No additional details")
                }
              }
            }
          }.padding(.vertical, RallyDesign.pt(8))
        }.focusSection()
      }
    }.padding(.horizontal, RallyDesign.pt(60)).padding(.top, RallyDesign.pt(12)).task {
      await load()
    }.sheet(item: $selectedPlayer) { p in
      RallyCanvas {
        ZStack {
          RallyBackdrop()
          VStack(spacing: RallyDesign.pt(16)) {
            RallyRemoteImage(url: p.headshotUrl, fit: true).frame(
              width: RallyDesign.pt(110), height: RallyDesign.pt(110))
            Text(p.name).font(RallyDesign.font(24, .bold))
            Text("\(p.position ?? "Player") · #\(p.jersey ?? "—")")
            RallyAction(title: "Done") { selectedPlayer = nil }
          }
        }
      }.presentationBackground(.black)
    }
  }
  @State private var selectedPlayer: TeamPlayer?
  private func load() async {
    loading = true
    do {
      hub = try await store.container.sports.teamHub(league: league, teamId: teamId)
      error = hub == nil ? "Team information could not be found." : nil
    } catch { self.error = "Check your connection and try again." }
    loading = false
  }
}
