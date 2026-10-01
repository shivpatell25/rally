import SwiftUI

struct HighlightsScreen: View {
  @Environment(RallyStore.self) private var store
  let navigate: (RallyRoute) -> Void
  @State private var items: [HighlightItem] = []
  @State private var league = "All"
  @State private var loading = true
  @State private var error: String?
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(16)) {
      RallySectionHeader(title: "Highlights", actionTitle: "Refresh") {
        Task {
          await store.container.sports.refresh()
          await load()
        }
      }
      HStack {
        RallyAction(title: "All", primary: league == "All") { league = "All" }
        ForEach(Array(Set(items.map(\.league))).sorted(), id: \.self) { l in
          RallyAction(title: l, primary: league == l) { league = l }
        }
      }.focusSection()
      if loading {
        RallyLoading(title: "Loading highlights…")
      } else if let error {
        RallyEmptyState(title: "Highlights unavailable", message: error, actionTitle: "Retry") {
          Task { await load() }
        }
      } else if items.isEmpty {
        RallyEmptyState(
          title: "No recent highlights", message: "New clips appear when the leagues publish them.",
          actionTitle: "Browse Games"
        ) { navigate(.schedule) }
      } else {
        ScrollView {
          LazyVGrid(
            columns: Array(
              repeating: GridItem(.fixed(RallyDesign.pt(272)), spacing: RallyDesign.pt(12)),
              count: 3), spacing: RallyDesign.pt(24)
          ) {
            ForEach(items.filter { league == "All" || $0.league == league }) { item in
              VStack(alignment: .leading, spacing: RallyDesign.pt(6)) {
                RallyHighlightCard(item: item) {
                  if let url = item.clip.streamUrl {
                    navigate(.playerClip(url: url, title: item.clip.title, eventId: item.event?.id))
                  }
                }
                if let id = item.event?.id {
                  RallyAction(title: "Open Game", bare: true) {
                    navigate(.eventDetail(eventId: id))
                  }
                }
              }
            }
          }.padding(.vertical, RallyDesign.pt(8))
        }.focusSection()
      }
    }.padding(.horizontal, RallyDesign.pt(60)).padding(.top, RallyDesign.pt(12)).task {
      await load()
    }
  }
  private func load() async {
    loading = true
    do {
      items = try await store.container.sports.recentHighlights()
      error = nil
    } catch { self.error = "Check your connection and try again." }
    loading = false
  }
}
