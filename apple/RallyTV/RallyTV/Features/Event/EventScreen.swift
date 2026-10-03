import SwiftUI

struct EventScreen: View {
  @Environment(RallyStore.self) private var store
  let eventId: String
  let navigate: (RallyRoute) -> Void
  @State private var event: SportEvent?
  @State private var loading = true
  @State private var error: String?
  @State private var tab = "Overview"
  @State private var picker = false
  @State private var sources = SourceModel()
  var body: some View {
    Group {
      if loading {
        RallyLoading(title: "Loading game…")
      } else if let event {
        VStack(alignment: .leading, spacing: RallyDesign.pt(12)) {
          RallyHero(
            event: event, detailed: true,
            watch: { navigate(.player(target: "auto", eventId: event.id)) }, info: {},
            saved: store.settings.savedEventIds.contains(event.id),
            save: { store.save(event.id) }
          ).frame(height: RallyDesign.pt(178))
          RallyTabs(
            tabs: ["Overview", "Stats", "Lineups", "Plays", "Sources", "Highlights"],
            selection: $tab)
          ScrollView {
            VStack(alignment: .leading, spacing: RallyDesign.pt(12)) {
              switch tab {
              case "Overview": overview(event)
              case "Stats":
                RallyTeamStats(event: event)
                RallyPlayerTables(event: event)
              case "Lineups": RallyPlayerTables(event: event)
              case "Plays": RallyPlayList(event: event)
              case "Sources":
                if sources.loading {
                  RallyLoading(title: "Finding sources…")
                } else if sources.candidates.isEmpty {
                  RallyEmptyState(
                    title: "No matching sources",
                    message: "Add your provider or sports addons in Settings.",
                    actionTitle: "Settings"
                  ) { navigate(.settings) }
                } else {
                  ForEach(sources.candidates) { c in
                    RallyAction(
                      title: c.title + "  ·  " + (c.quality.resolution ?? "Auto"), icon: "play.fill"
                    ) {
                      navigate(.playerSource(candidate: c, eventId: event.id))
                    }
                  }
                }
              case "Highlights":
                if event.highlightClips.isEmpty {
                  RallyEmptyState(
                    title: "No highlights yet",
                    message: "Clips appear as the league publishes them.")
                } else {
                  LazyVGrid(
                    columns: Array(
                      repeating: GridItem(.fixed(RallyDesign.pt(272)), spacing: RallyDesign.pt(12)),
                      count: 3),
                    spacing: RallyDesign.pt(18)
                  ) {
                    ForEach(event.highlightClips) { clip in
                      RallyHighlightCard(
                        item: HighlightItem(clip: clip, event: event, league: event.league)
                      ) {
                        if let url = clip.streamUrl {
                          navigate(.playerClip(url: url, title: clip.title, eventId: event.id))
                        }
                      }
                    }
                  }
                }
              default: EmptyView()
              }
            }.padding(.vertical, RallyDesign.pt(8))
          }.scrollClipDisabled().focusSection()
        }.padding(.horizontal, RallyDesign.pt(60)).padding(.bottom, RallyDesign.pt(16))
      } else {
        RallyEmptyState(
          title: "Game unavailable", message: error ?? "This event could not be found.",
          actionTitle: "Retry"
        ) { Task { await load() } }.padding(RallyDesign.pt(60))
      }
    }.task {
      await load()
      if let event { await sources.load(event, container: store.container) }
      while event?.status.isLive == true && !Task.isCancelled {
        do { try await Task.sleep(for: .seconds(30)) } catch { return }
        if let current = event, let next = try? await store.container.sports.eventSummary(current) {
          event = next
        }
      }
    }
  }
  private func load() async {
    loading = event == nil
    do {
      if let base = try await store.container.sports.event(id: eventId) {
        event = (try? await store.container.sports.eventSummary(base)) ?? base
      }
      error = nil
    } catch { self.error = "Check your connection and try again." }
    loading = false
  }
  private func overview(_ e: SportEvent) -> some View {
    HStack(alignment: .top, spacing: RallyDesign.pt(12)) {
      RallyPanel("Game Info", minimumHeight: 168) {
        Label(e.venue ?? "Venue not published", systemImage: "sportscourt")
        if let city = e.liveStats["Venue City"] {
          Text(city).foregroundStyle(RallyDesign.muted).font(RallyDesign.font(9))
        }
        Label(e.liveStats["TV Broadcast"] ?? "Broadcast not published", systemImage: "tv")
        Label(e.startTime.formatted(date: .abbreviated, time: .shortened), systemImage: "calendar")
        Text(e.scoreLine).font(RallyDesign.font(12, .semibold))
        if let weather = e.liveStats["Weather"] {
          Label(
            [e.liveStats["Temperature"], weather].compactMap { $0 }.joined(separator: " · "),
            systemImage: "cloud.sun")
        }
      }.font(RallyDesign.font(11)).frame(maxWidth: .infinity)
      RallyPanel("Team Form", minimumHeight: 168) {
        HStack {
          VStack(spacing: RallyDesign.pt(5)) {
            RallyTeamLogo(team: e.awayTeam, size: 45)
            Text(e.awayTeam?.abbreviation ?? "Away")
            Text(e.liveStats["\(e.awayTeam?.abbreviation ?? "") Record"] ?? "—").foregroundStyle(
              RallyDesign.muted)
          }
          Spacer()
          Text("VS").foregroundStyle(RallyDesign.muted)
          Spacer()
          VStack(spacing: RallyDesign.pt(5)) {
            RallyTeamLogo(team: e.homeTeam, size: 45)
            Text(e.homeTeam?.abbreviation ?? "Home")
            Text(e.liveStats["\(e.homeTeam?.abbreviation ?? "") Record"] ?? "—").foregroundStyle(
              RallyDesign.muted)
          }
        }
        if let p = e.winProbability.last {
          Text("Win Probability").font(RallyDesign.font(10)).foregroundStyle(RallyDesign.muted)
          HStack {
            Text(
              "\(e.awayTeam?.abbreviation ?? "Away") \(Int((1-p.homeWinPercentage-p.tiePercentage)*100))%"
            )
            Spacer()
            Text("\(e.homeTeam?.abbreviation ?? "Home") \(Int(p.homeWinPercentage*100))%")
          }
          GeometryReader { geo in
            HStack(spacing: RallyDesign.pt(0)) {
              Color(hex: e.awayTeam?.colors.first ?? "555555").frame(
                width: geo.size.width * max(0, 1 - p.homeWinPercentage))
              Color(hex: e.homeTeam?.colors.first ?? "999999")
            }
          }.frame(height: RallyDesign.pt(5)).clipShape(Capsule())
        } else {
          Text("Win probability not published").font(RallyDesign.font(9)).foregroundStyle(
            RallyDesign.muted)
        }
      }.font(RallyDesign.font(11)).frame(maxWidth: .infinity)
      RallyPanel("Player Stats", minimumHeight: 168) {
        if e.playerLeaders.isEmpty {
          Text("Stats will appear when published.").foregroundStyle(RallyDesign.muted).font(
            RallyDesign.font(11))
        }
        RallyLeadersGrid(event: e)
        RallyAction(title: "All Player Stats", bare: true) { tab = "Stats" }
      }.frame(maxWidth: .infinity)
    }
  }
}
struct RallyTeamStats: View {
  let event: SportEvent
  var body: some View {
    RallyPanel("Team Stats") {
      HStack {
        Text(event.awayTeam?.name ?? "Away")
        Spacer()
        Text(event.homeTeam?.name ?? "Home")
      }.font(RallyDesign.font(12, .semibold))
      if event.teamStats.isEmpty {
        Text("Team stats have not been published.").foregroundStyle(RallyDesign.muted)
      }
      ForEach(Array(event.teamStats.enumerated()), id: \.offset) { _, s in
        HStack {
          Text(s.awayValue).frame(width: RallyDesign.pt(60), alignment: .leading)
          Spacer()
          Text(s.label).foregroundStyle(RallyDesign.muted)
          Spacer()
          Text(s.homeValue).frame(width: RallyDesign.pt(60), alignment: .trailing)
        }.font(RallyDesign.font(11)).padding(.vertical, RallyDesign.pt(3))
      }
    }
  }
}
struct RallyPlayerTables: View {
  @Environment(RallyStore.self) private var store
  let event: SportEvent
  var compact = false
  @State private var selected: PlayerStatRow?
  @State private var selectedLabels: [String] = []
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(14)) {
      if event.playerStatTables.isEmpty {
        RallyEmptyState(
          title: "Player stats aren’t published yet",
          message: "Full lineups and stats will appear here when available.")
      }
      ForEach(Array(event.playerStatTables.enumerated()), id: \.offset) { _, table in
        RallyPanel(
          (compact
            ? (table.teamAbbreviation.isEmpty ? table.teamName : table.teamAbbreviation)
            : table.teamName) + " · " + (table.category?.capitalized ?? "Players"), compact: compact
        ) {
          if !compact {
            HStack {
              Text("PLAYER").frame(width: RallyDesign.pt(230), alignment: .leading)
              ForEach(Array(table.labels.enumerated()), id: \.offset) { _, label in
                Text(label).frame(maxWidth: .infinity)
              }
            }.font(RallyDesign.font(9, .semibold)).foregroundStyle(RallyDesign.muted)
          }
          ForEach(table.rows) { row in
            Button {
              selectedLabels = table.labels
              selected = row
            } label: {
              if compact {
                VStack(alignment: .leading, spacing: RallyDesign.pt(5)) {
                  HStack(spacing: RallyDesign.pt(6)) {
                    RallyRemoteImage(url: row.headshotUrl, fit: true).frame(
                      width: RallyDesign.pt(24), height: RallyDesign.pt(24))
                    Text(row.shortName ?? row.displayName).font(RallyDesign.font(10, .semibold))
                  }
                  LazyVGrid(
                    columns: [
                      GridItem(.adaptive(minimum: RallyDesign.pt(42)), spacing: RallyDesign.pt(4))
                    ], alignment: .leading, spacing: RallyDesign.pt(5)
                  ) {
                    ForEach(Array(zip(table.labels, row.stats).enumerated()), id: \.offset) {
                      _, pair in
                      VStack(alignment: .leading, spacing: RallyDesign.pt(2)) {
                        Text(pair.0).font(RallyDesign.font(7)).foregroundStyle(RallyDesign.muted)
                        Text(pair.1).font(RallyDesign.font(9, .medium))
                      }
                    }
                  }
                }.frame(maxWidth: .infinity, alignment: .leading).padding(
                  .vertical, RallyDesign.pt(4))
              } else {
                HStack(spacing: RallyDesign.pt(10)) {
                  RallyRemoteImage(url: row.headshotUrl, fit: true).frame(
                    width: RallyDesign.pt(23), height: RallyDesign.pt(23))
                  VStack(alignment: .leading, spacing: RallyDesign.pt(3)) {
                    Text(row.shortName ?? row.displayName).font(RallyDesign.font(11, .medium))
                    Text("\(row.position ?? "")  #\(row.jersey ?? "—")").font(RallyDesign.font(8))
                      .foregroundStyle(RallyDesign.muted)
                  }.frame(width: RallyDesign.pt(197), alignment: .leading)
                  ForEach(Array(row.stats.enumerated()), id: \.offset) { _, value in
                    Text(value).font(RallyDesign.font(10)).frame(maxWidth: .infinity)
                  }
                }.padding(.vertical, RallyDesign.pt(3))
              }
            }.buttonStyle(RallyButtonStyle(bare: true)).focusEffectDisabled()
          }
        }
      }
    }.sheet(item: $selected) { row in
      RallyCanvas {
        ZStack {
          RallyBackdrop()
          VStack(spacing: RallyDesign.pt(16)) {
            RallyRemoteImage(url: row.headshotUrl, fit: true).frame(
              width: RallyDesign.pt(100), height: RallyDesign.pt(100))
            Text(row.displayName).font(RallyDesign.font(25, .bold))
            Text("\(row.position ?? "Player") · #\(row.jersey ?? "—")")
            ForEach(Array(zip(selectedLabels, row.stats).enumerated()), id: \.offset) { _, pair in
              HStack {
                Text(pair.0)
                Spacer()
                Text(pair.1)
              }.frame(width: RallyDesign.pt(330))
            }
            RallyAction(
              title: store.settings.favoritePlayerIds.contains(row.id)
                ? "Unfollow Player" : "Follow Player"
            ) {
              var ids = store.settings.favoritePlayerIds
              if !ids.insert(row.id).inserted { ids.remove(row.id) }
              store.settings.favoritePlayerIds = ids
              store.settingsRevision += 1
            }
            RallyAction(title: "Done") { selected = nil }
          }.padding(RallyDesign.pt(30))
        }
      }.presentationBackground(.black)
    }
  }
}
struct RallyPlayList: View {
  let event: SportEvent
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(8)) {
      if event.plays.isEmpty {
        RallyEmptyState(
          title: "No play-by-play yet", message: "Live plays will appear here when available.")
      }
      ForEach(event.plays) { p in
        RallyPanel("\(p.period.map { "Period \($0)" } ?? "Play") · \(p.clock ?? "")") {
          Text(p.text).font(RallyDesign.font(12))
          if p.isScoringPlay {
            Text("Scoring play · \(p.awayScore ?? 0) – \(p.homeScore ?? 0)").font(
              RallyDesign.font(10, .semibold))
          }
        }.focusable().focusEffectDisabled()
      }
    }
  }
}
