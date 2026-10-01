import Observation
import SwiftUI

@MainActor @Observable final class SourceModel {
  var candidates: [StreamCandidate] = []
  var loading = false
  var error: String?
  func loadChannels(container: AppContainer) async {
    loading = true
    do {
      candidates = try await container.iptv.channels().map { StreamCandidate(channel: $0) }
      error = nil
    } catch {
      candidates = []
      self.error = "The channel catalog could not be loaded. Check your provider connection."
    }
    loading = false
  }
  func load(_ event: SportEvent, container: AppContainer) async {
    loading = true
    async let channels = container.iptv.channels()
    async let streams = container.stremio.streams(for: event)
    let catalog = (try? await channels) ?? []
    let result = await container.selectBestStream.select(
      event: event, channels: catalog, stremioStreams: await streams)
    candidates = result.candidates.sorted { a, b in
      if a.exactGameMatch != b.exactGameMatch { return a.exactGameMatch }
      let ah = container.settings.health(a.id).score
      let bh = container.settings.health(b.id).score
      let left = a.qualityRank + ah
      let right = b.qualityRank + bh
      return left == right ? a.id < b.id : left > right
    }
    loading = false
    error = nil
  }
}
struct SourcePicker: View {
  @Environment(RallyStore.self) private var store
  let event: SportEvent?
  let model: SourceModel
  let select: (StreamCandidate) -> Void
  let dismiss: () -> Void
  let settings: () -> Void
  var body: some View {
    RallyCanvas {
      ZStack {
        RallyBackdrop()
        VStack(alignment: .leading, spacing: RallyDesign.pt(16)) {
          HStack {
            Text("Pick Source").font(RallyDesign.font(24, .semibold))
            Spacer()
            RallyAction(title: "Done", action: dismiss)
          }
          if let event { Text(event.compactMatchup).foregroundStyle(RallyDesign.muted) }
          if model.loading {
            RallyLoading(title: "Finding sources…")
          } else if model.candidates.isEmpty {
            RallyEmptyState(
              title: "No matching sources",
              message: model.error
                ?? "Connect your IPTV provider or add a sports addon in Settings.",
              actionTitle: "Open Settings", action: settings)
          } else {
            ScrollView {
              LazyVStack(spacing: RallyDesign.pt(10)) {
                ForEach(Array(model.candidates.enumerated()), id: \.element.id) { i, c in
                  Button {
                    select(c)
                  } label: {
                    HStack {
                      VStack(alignment: .leading, spacing: RallyDesign.pt(6)) {
                        Text(c.title).font(RallyDesign.font(15, .medium))
                        Text(
                          "\(c.sourceKind.rawValue.uppercased()) · \(c.quality.resolution ?? "Auto") · \(c.matchEvidence)"
                        ).font(RallyDesign.font(10)).foregroundStyle(RallyDesign.muted)
                      }
                      Spacer()
                      Text("Source \(i+1)").font(RallyDesign.font(10))
                      Image(systemName: "play.fill")
                    }.padding(RallyDesign.pt(14)).frame(maxWidth: .infinity).background(
                      RallyDesign.surface, in: RoundedRectangle(cornerRadius: RallyDesign.pt(8)))
                  }.buttonStyle(RallyMediaFocus()).focusEffectDisabled().accessibilityIdentifier(
                    "source-\(i)")
                }
              }
            }.focusSection()
          }
        }.padding(RallyDesign.pt(60))
      }
    }.onExitCommand(perform: dismiss).presentationBackground(.black)
  }
}
