import Observation
import SwiftUI

@MainActor @Observable final class SourceModel {
  var candidates: [StreamCandidate] = []
  var loading = false
  var error: String?
  private var requestID = UUID()
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
    let request = UUID()
    requestID = request
    loading = true
    error = nil
    async let channels = container.iptv.channels()
    async let streams = container.stremio.streams(for: event)
    let catalog = (try? await channels) ?? []
    let addonStreams = await streams
    let result = await container.selectBestStream.select(
      event: event, channels: catalog, stremioStreams: addonStreams)
    guard requestID == request else { return }
    guard !Task.isCancelled else { loading = false; return }
    candidates = result.candidates.sorted { a, b in
      if a.exactGameMatch != b.exactGameMatch { return a.exactGameMatch }
      let ah = container.settings.health(a.id).score
      let bh = container.settings.health(b.id).score
      let left = a.qualityRank + ah
      let right = b.qualityRank + bh
      return left == right ? a.id < b.id : left > right
    }
    loading = false
    if candidates.isEmpty {
      if !addonStreams.isEmpty {
        error = "The addon returned sources that require a browser or a player format unavailable on Apple TV."
      } else if !container.settings.stremioAddonUrls.isEmpty {
        error = "Your configured addons returned no matching sources for this game. Try Refresh sources, or check the addon connection in Settings."
      } else {
        error = "Connect an authorized IPTV provider or addon manifest in Settings."
      }
    }
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
            if let event, !model.loading {
              RallyAction(title: "Refresh sources", icon: "arrow.clockwise") {
                Task { await model.load(event, container: store.container) }
              }
            }
            RallyAction(title: "Done", action: dismiss)
          }
          if let event { Text(event.compactMatchup).foregroundStyle(RallyDesign.muted) }
          if model.loading {
            RallyLoading(title: "Finding sources…")
          } else if model.candidates.isEmpty {
            RallyEmptyState(
              title: "No matching sources",
              message: model.error
                ?? "Connect an authorized IPTV provider or addon manifest in Settings.",
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
            }.scrollClipDisabled().focusSection()
          }
        }.padding(RallyDesign.pt(60))
      }
    }.onExitCommand(perform: dismiss).presentationBackground(.black)
  }
}
