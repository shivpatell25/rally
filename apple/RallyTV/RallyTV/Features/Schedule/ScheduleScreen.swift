import SwiftUI

struct ScheduleScreen: View {
  @Environment(RallyStore.self) private var store
  let navigate: (RallyRoute) -> Void
  @State private var events: [SportEvent] = []
  @State private var loading = true
  @State private var error: String?
  @State private var day = 0
  @State private var league = "All"
  @State private var state = "All"
  private var dates: [Date] {
    (-2...7).map {
      Calendar.current.startOfDay(
        for: Calendar.current.date(byAdding: .day, value: $0, to: Date())!)
    }
  }
  private var visible: [SportEvent] {
    events.filter {
      Calendar.current.isDate($0.startTime, inSameDayAs: dates[day + 2])
        && (league == "All" || $0.league == league)
        && (state == "All" || state == "Live" && $0.status.isLive
          || state == "Upcoming" && $0.status == .notStarted
          || state == "Final" && $0.status == .finished)
    }.sorted { $0.startTime < $1.startTime }
  }
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(14)) {
      RallySectionHeader(title: "Schedule", actionTitle: "Refresh") {
        Task { await load(refresh: true) }
      }
      ScrollView(.horizontal) {
        HStack(spacing: RallyDesign.pt(8)) {
          ForEach(-2...7, id: \.self) { offset in
            RallyAction(
              title: offset == 0
                ? "Today"
                : dates[offset + 2].formatted(
                  .dateTime.weekday(.abbreviated).month(.abbreviated).day()), primary: day == offset
            ) { day = offset }
          }
        }
      }.scrollIndicators(.hidden).scrollClipDisabled().focusSection()
      HStack(spacing: RallyDesign.pt(10)) {
        Menu {
          Button("All leagues") { league = "All" }
          ForEach(store.settings.sportsOrder, id: \.self) { l in Button(l) { league = l }
          }
        } label: {
          Label(league == "All" ? "All leagues" : league, systemImage: "line.3.horizontal.decrease")
        }.buttonStyle(RallyButtonStyle())
        ForEach(["All", "Live", "Upcoming", "Final"], id: \.self) { s in
          RallyAction(title: s, primary: state == s) { state = s }
        }
        Spacer()
        RallyAction(title: "Clear Filters", bare: true) {
          league = "All"
          state = "All"
          day = 0
        }
      }.focusSection()
      if loading {
        RallyLoading(title: "Loading schedule…")
      } else if let error {
        RallyEmptyState(title: "Schedule unavailable", message: error, actionTitle: "Retry") {
          Task { await load(refresh: true) }
        }
      } else if visible.isEmpty {
        RallyEmptyState(
          title: "No games match these filters",
          message: "Choose another date or clear the filters.", actionTitle: "Clear Filters"
        ) {
          league = "All"
          state = "All"
        }
      } else {
        ScrollView {
          LazyVStack(spacing: RallyDesign.pt(3)) {
            ForEach(visible) { event in
              RallyScheduleRow(
                event: event, reminder: store.settings.reminderIds.contains(event.id),
                action: { navigate(.eventDetail(eventId: event.id)) },
                remind: { store.reminder(event.id) })
            }
          }
        }.scrollClipDisabled().focusSection()
      }
    }.padding(.horizontal, RallyDesign.pt(60)).padding(.top, RallyDesign.pt(12)).padding(
      .bottom, RallyDesign.pt(18)
    ).task(id: day) { await load() }

  }
  private func load(refresh: Bool = false) async {
    loading = events.isEmpty
    if refresh { await store.container.sports.refresh() }
    do {
      let date = dates[day + 2]
      let repository = store.container.sports
      let leagues = store.settings.sportsOrder.filter {
        store.settings.enabledLeagues.contains($0)
      }
      var result: [SportEvent] = []
      var succeeded = false
      for offset in stride(from: 0, to: leagues.count, by: 3) {
        let batch = Array(leagues.dropFirst(offset).prefix(3))
        let values = await withTaskGroup(of: [SportEvent]?.self) { group in
          for item in batch {
            group.addTask { try? await repository.events(forDate: date, league: item) }
          }
          var values: [[SportEvent]] = []
          for await value in group { if let value { values.append(value) } }
          return values
        }
        guard !Task.isCancelled else { return }
        succeeded = succeeded || !values.isEmpty
        result += values.flatMap { $0 }
      }
      guard succeeded else { throw URLError(.notConnectedToInternet) }
      events = result
      error = nil
    } catch { self.error = "Check your connection and try again." }
    loading = false
  }
}
