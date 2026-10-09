import Observation
import SwiftUI

@MainActor @Observable final class HomeModel {
  var live: [SportEvent] = []
  var upcoming: [SportEvent] = []
  var highlights: [HighlightItem] = []
  var hero: SportEvent?
  var loading = true
  var error: String?
  func run(_ store: RallyStore) async {
    repeat {
      do {
        let events = try await store.container.sports.recentEvents()
        store.evaluate(events)
        live = events.filter(\.status.isLive).sorted { $0.startTime < $1.startTime }
        upcoming = events.filter {
          $0.status == .notStarted && $0.startTime > Date().addingTimeInterval(-3600)
        }.sorted { $0.startTime < $1.startTime }
        let next =
          live.first ?? upcoming.first
          ?? events.filter { $0.status == .finished }.max { $0.startTime < $1.startTime }
        if hero?.id != next?.id { hero = next }
        loading = false
        error = nil
        if let next { hero = (try? await store.container.sports.eventSummary(next)) ?? next }
        if live.isEmpty {
          highlights = (try? await store.container.sports.recentHighlights()) ?? highlights
        }
        await store.checkRedZone()
      } catch {
        loading = false
        self.error = "Couldn’t load sports. Check your connection and try again."
      }
      do { try await Task.sleep(for: .seconds(30)) } catch { return }
    } while !Task.isCancelled
  }
}
struct HomeScreen: View {
  @Environment(RallyStore.self) private var store
  let navigate: (RallyRoute) -> Void
  @State private var model = HomeModel()
  @State private var guide = false
  @State private var page = 0
  @State private var retryRevision = 0
  @FocusState private var focus: String?
  private var railCount: Int { model.live.isEmpty ? model.highlights.count : model.live.count }
  var body: some View {
    Group {
      if model.loading {
        RallyLoading()
      } else if let error = model.error, model.hero == nil {
        RallyEmptyState(title: "Home is unavailable", message: error, actionTitle: "Retry") {
          Task {
            await store.container.sports.refresh()
            retryRevision += 1
          }
        }.padding(.horizontal, RallyDesign.pt(60))
      } else {
        VStack(alignment: .leading, spacing: RallyDesign.pt(0)) {
          if let hero = model.hero {
            RallyHero(
              event: hero, watch: { navigate(.player(target: "auto", eventId: hero.id)) },
              info: { navigate(.eventDetail(eventId: hero.id)) }, schedule: { navigate(.schedule) }
            )
            .focused($focus, equals: "hero").frame(height: RallyDesign.pt(190))
          } else {
            RallyEmptyState(
              title: "Welcome to Rally",
              message: "Browse live TV or choose a league to get started.",
              actionTitle: "Browse Live TV"
            ) { navigate(.iptvBrowser) }.frame(height: RallyDesign.pt(190))
          }
          mediaRail.frame(height: RallyDesign.pt(178), alignment: .top)
          Color.clear.frame(height: RallyDesign.pt(8))
          upcomingSection.frame(height: RallyDesign.pt(guide ? 170 : 94), alignment: .top)
          Color.clear.frame(height: RallyDesign.pt(14))
          VStack(alignment: .leading, spacing: RallyDesign.pt(8)) {
            RallySectionHeader(title: "Browse by Sport")
            HStack(spacing: RallyDesign.pt(10)) {
              ForEach(
                ["NFL", "NBA", "MLB", "NHL", "NCAAF", "NCAAB", "MLS", "UFC", "Soccer", "Tennis"],
                id: \.self
              ) { sport in
                RallyLeagueShortcut(league: sport) { navigate(.leagueHub(league: sport)) }
              }
            }.focusSection()
          }.opacity(guide ? 1 : 0).disabled(!guide).frame(
            height: RallyDesign.pt(82), alignment: .top)
        }.padding(.horizontal, RallyDesign.pt(60)).frame(
          width: RallyDesign.pt(960), alignment: .topLeading
        )
        .offset(y: RallyDesign.pt(guide ? -190 : 0)).frame(
          height: RallyDesign.pt(470), alignment: .top
        ).clipped()
        .animation(
          store.settings.reducedMotion
            ? nil : .timingCurve(0.2, 0.65, 0.25, 1, duration: 0.3), value: guide)
      }
    }.task(id: retryRevision) { await model.run(store) }
      .onChange(of: railCount) { _, count in page = min(page, max(0, (count - 1) / 3)) }
      .onChange(of: model.live.isEmpty) { _, _ in page = 0 }
      .onChange(of: store.homeReset) { _, _ in
        guide = false
        page = 0
        focus = "hero"
      }
  }
  private var mediaRail: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(8)) {
      RallySectionHeader(
        title: model.live.isEmpty ? "Recent Highlights" : "Live Now", actionTitle: "See All"
      ) { navigate(model.live.isEmpty ? .highlights : .live) }
      if railCount == 0 {
        RallyEmptyState(
          title: "No games are live right now.",
          message: "Recent highlights will appear when they’re available.",
          actionTitle: "Browse Live TV"
        ) { navigate(.iptvBrowser) }.frame(height: RallyDesign.pt(128))
      } else {
        HStack(alignment: .top, spacing: RallyDesign.pt(18)) {
          if model.live.isEmpty {
            ForEach(
              Array(model.highlights.dropFirst(page * 3).prefix(3).enumerated()), id: \.element.id
            ) { i, item in
              RallyHighlightCard(item: item) {
                if let url = item.clip.streamUrl {
                  navigate(.playerClip(url: url, title: item.clip.title, eventId: item.event?.id))
                }
              }
              .focused($focus, equals: "media-\(i)").onMoveCommand { d in railMove(d, index: i) }
            }
          } else {
            ForEach(Array(model.live.dropFirst(page * 3).prefix(3).enumerated()), id: \.element.id)
            { i, event in
              RallyLiveCard(event: event) { navigate(.eventDetail(eventId: event.id)) }.focused(
                $focus, equals: "media-\(i)"
              ).onMoveCommand { d in railMove(d, index: i) }
            }
          }
          Spacer(minLength: RallyDesign.pt(0))
        }.focusSection()
          .task {
            // Loading replaces a non-focusable placeholder. Request focus after
            // the rail has entered the tree, rather than leaving the remote
            // without a destination when real network data arrives.
            if focus == nil {
              await Task.yield()
              focus = "media-0"
            }
          }
      }
    }
  }
  private func railMove(_ direction: MoveCommandDirection, index: Int) {
    if direction == .right && index == 2 && (page + 1) * 3 < railCount {
      page += 1
      focus = "media-0"
    } else if direction == .left && index == 0 && page > 0 {
      page -= 1
      focus = "media-2"
    } else if direction == .up && guide {
      guide = false
      focus = "hero"
    }
  }
  private var upcomingSection: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(8)) {
      RallySectionHeader(
        title: guide ? (model.upcoming.prefix(4).allSatisfy { Calendar.current.isDateInToday($0.startTime) } ? "Tonight’s Schedule" : "Upcoming Schedule") : "Starting Soon",
        actionTitle: "See Full Schedule",
        action: { navigate(.schedule) })
      if model.upcoming.isEmpty {
        RallyEmptyState(
          title: "No upcoming games", message: "See the full schedule for other dates.",
          actionTitle: "See Full Schedule"
        ) { navigate(.schedule) }.onMoveCommand { d in if d == .down { guide = true } }
      } else {
        RallyUpcomingLayout(progress: guide ? 1 : 0) {
          ForEach(Array(model.upcoming.prefix(4).enumerated()), id: \.element.id) { index, event in
            upcoming(event, index: index)
            .focused($focus, equals: "upcoming-\(index)")
            .onMoveCommand { d in
              if d == .down && !guide {
                guide = true
                focus = "upcoming-0"
              }
            }
          }
        }.frame(
          width: RallyDesign.pt(840), height: RallyDesign.pt(guide ? 139 : 60),
          alignment: .topLeading
        ).focusSection()
          .background(
            guide ? RallyDesign.surface.opacity(0.3) : .clear,
            in: RoundedRectangle(cornerRadius: RallyDesign.pt(8))
          )
          .overlay(
            RoundedRectangle(cornerRadius: RallyDesign.pt(8)).stroke(
              guide ? RallyDesign.edge : .clear, lineWidth: 0.6))
      }
    }
  }
  private func upcoming(_ event: SportEvent, index: Int) -> some View {
    ZStack(alignment: .topLeading) {
      Button {
        navigate(.eventDetail(eventId: event.id))
      } label: {
        ZStack(alignment: .topLeading) {
          Text(event.startTime.formatted(date: .omitted, time: .shortened)).font(
            RallyDesign.font(guide ? 11 : 10)
          ).foregroundStyle(RallyDesign.muted).offset(
            x: RallyDesign.pt(guide ? 12 : 4), y: RallyDesign.pt(guide ? 7 : 0))
          RallyTeamLogo(team: event.awayTeam, size: 28).offset(
            x: RallyDesign.pt(guide ? 124 : 4), y: RallyDesign.pt(guide ? 0 : 19))
          RallyTeamLogo(team: event.homeTeam, size: 28).offset(
            x: RallyDesign.pt(guide ? 167 : 37), y: RallyDesign.pt(guide ? 0 : 19))
          Text(event.compactMatchup).font(RallyDesign.font(guide ? 11 : 9.5, .medium)).lineLimit(
            guide ? 1 : 2
          ).frame(width: RallyDesign.pt(guide ? 430 : 124), alignment: .leading).offset(
            x: RallyDesign.pt(guide ? 225 : 72), y: RallyDesign.pt(guide ? 7 : 19))
          Text(event.league).font(RallyDesign.font(guide ? 10 : 8)).foregroundStyle(
            RallyDesign.muted
          ).offset(x: RallyDesign.pt(guide ? 690 : 72), y: RallyDesign.pt(guide ? 7 : 38))
        }.frame(
          width: RallyDesign.pt(guide ? 770 : 193), height: RallyDesign.pt(guide ? 28 : 54),
          alignment: .topLeading
        )
        .background(
          guide ? .clear : RallyDesign.surface.opacity(0.5),
          in: RoundedRectangle(cornerRadius: RallyDesign.pt(8)))
      }.buttonStyle(RallyButtonStyle(bare: true)).focusEffectDisabled().accessibilityIdentifier(
        "upcoming-\(index)")
      if guide {
        RallyAction(
          title: "",
          icon: store.settings.reminderIds.contains(event.id) ? "bell.fill" : "bell",
          bare: true
        ) { store.reminder(event.id) }.accessibilityLabel(
          store.settings.reminderIds.contains(event.id) ? "Remove reminder" : "Set reminder"
        ).offset(
          x: RallyDesign.pt(802), y: RallyDesign.pt(5))
      }
    }.overlay(alignment: .bottom) {
      if guide { Rectangle().fill(RallyDesign.edge).frame(height: RallyDesign.pt(0.5)) }
    }
  }
}
