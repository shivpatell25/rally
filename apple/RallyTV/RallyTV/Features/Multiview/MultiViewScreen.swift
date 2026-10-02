import Observation
import SwiftUI

@MainActor @Observable final class MultiTile: Identifiable {
  let id = UUID().uuidString
  let session = PlaybackSession()
  var event: SportEvent?
  var channel: IptvChannel?
  var candidate: StreamCandidate?
  var stats = false
  var title: String {
    stats ? "Player Stats" : event?.compactMatchup ?? channel?.name ?? candidate?.title ?? "Stream"
  }
  init(
    event: SportEvent? = nil, channel: IptvChannel? = nil, candidate: StreamCandidate? = nil,
    stats: Bool = false
  ) {
    self.event = event
    self.channel = channel
    self.candidate = candidate
    self.stats = stats
  }
}
enum MultiViewGeometry {
  static func bounds(width: CGFloat, height: CGFloat, count: Int, focus: Bool, gap: CGFloat)
    -> [CGRect]
  {
    guard count > 0, width > 0, height > 0 else { return [] }
    let aspect: CGFloat = 16 / 9
    let g = max(0, gap)
    let n = min(count, 4)
    var cells: [CGRect] = []
    if n == 1 {
      cells = [CGRect(x: 0, y: 0, width: width, height: width / aspect)]
    } else if n == 2 {
      let main = (width - g) * (focus ? 0.7 : 0.5)
      let side = width - g - main
      cells = [
        CGRect(x: 0, y: 0, width: main, height: main / aspect),
        CGRect(x: main + g, y: (main - side) / aspect / 2, width: side, height: side / aspect),
      ]
    } else if n == 3 || focus {
      let side = (width - g - CGFloat(n - 2) * g * aspect) / CGFloat(n)
      let main = width - g - side
      cells = [CGRect(x: 0, y: 0, width: main, height: main / aspect)]
      for row in 0..<n - 1 {
        cells.append(
          CGRect(
            x: main + g, y: CGFloat(row) * (side / aspect + g), width: side, height: side / aspect))
      }
    } else {
      let w = (width - g) / 2
      let h = w / aspect
      cells = (0..<4).map {
        CGRect(x: CGFloat($0 % 2) * (w + g), y: CGFloat($0 / 2) * (h + g), width: w, height: h)
      }
    }
    let contentHeight = cells.map(\.maxY).max() ?? 0
    let scale = min(1, height / contentHeight)
    let left = (width - width * scale) / 2
    let top = (height - contentHeight * scale) / 2
    return cells.map {
      CGRect(
        x: left + $0.minX * scale, y: top + $0.minY * scale, width: $0.width * scale,
        height: $0.height * scale)
    }
  }
  static func redZoneSlate(_ events: [SportEvent], date: Date) -> [SportEvent] {
    var eastern = Calendar(identifier: .gregorian)
    eastern.timeZone = TimeZone(identifier: "America/New_York")!
    return events.filter { event in
      let hour = eastern.component(.hour, from: event.startTime)
      return event.league == "NFL" && eastern.isDate(event.startTime, inSameDayAs: date)
        && hour >= 13 && hour < 17
    }
  }
  @MainActor static func statsEvents(_ tiles: [MultiTile], slate: [SportEvent]) -> [SportEvent] {
    var ids = Set<String>()
    return (tiles.compactMap { $0.event } + slate).filter { ids.insert($0.id).inserted }.sorted {
      $0.startTime < $1.startTime
    }
  }
  @MainActor static func routeAudio(_ tiles: [MultiTile], pinned: String?, focused: String?) {
    let streams = tiles.filter { !$0.stats }
    let audible =
      [pinned, focused].compactMap { $0 }.first { id in streams.contains { $0.id == id } }
      ?? streams.first?.id
    for tile in tiles { tile.session.player.isMuted = tile.stats || tile.id != audible }
  }
}
private enum MultiOverlay: String, Identifiable {
  case add, manage, source
  var id: String { rawValue }
}
struct MultiViewScreen: View {
  @Environment(RallyStore.self) private var store
  @Environment(\.dismiss) private var dismiss
  @Environment(\.scenePhase) private var scenePhase
  let channelId: String?
  let eventId: String?
  let eventIds: [String]
  var initialSource: StreamCandidate? = nil
  let navigate: (RallyRoute) -> Void
  @State private var tiles: [MultiTile] = []
  @State private var live: [SportEvent] = []
  @State private var channels: [IptvChannel] = []
  @State private var statsEvents: [SportEvent] = []
  @State private var pinned: String?
  @State private var lastAudio: String?
  @State private var selectedId: String?
  @State private var immersive = false
  @State private var focusLayout = false
  @State private var overlay: MultiOverlay?
  @State private var loading = true
  @State private var error: String?
  @State private var sourceModel = SourceModel()
  @State private var pendingEvent: SportEvent?
  @State private var changeId: String?
  @FocusState private var focused: String?
  private var selected: MultiTile? {
    tiles.first { $0.id == selectedId } ?? tiles.first { $0.id == focused } ?? tiles.first
  }
  var body: some View {
    ZStack(alignment: .topLeading) {
      if !immersive { RallyBackdrop() }
      let width: CGFloat = immersive ? 960 : 908
      let height: CGFloat = immersive ? 540 : 430
      let bounds = MultiViewGeometry.bounds(
        width: width, height: height, count: tiles.count, focus: focusLayout, gap: immersive ? 0 : 8
      )
      ForEach(Array(tiles.enumerated()), id: \.element.id) { index, tile in
        if index < bounds.count {
          tileView(tile, bounds: bounds[index]).offset(
            x: RallyDesign.pt(bounds[index].minX + (immersive ? 0 : 26)),
            y: RallyDesign.pt(bounds[index].minY + (immersive ? 0 : 66)))
        }
      }
      if !immersive {
        toolbar.padding(.horizontal, RallyDesign.pt(26)).padding(.top, RallyDesign.pt(18))
      }
      if loading { RallyLoading(title: "Opening Multiview…") }
      if tiles.isEmpty && !loading {
        RallyEmptyState(
          title: "Build your Multiview",
          message: error ?? "Choose live games or channels, then add a player stats tile.",
          actionTitle: "Add Stream"
        ) { overlay = .add }.padding(RallyDesign.pt(60))
      }
    }.frame(width: RallyDesign.pt(960), height: RallyDesign.pt(540), alignment: .topLeading)
      .background(.black).onChange(of: focused) { _, id in
        guard let tile = tiles.first(where: { $0.id == id }), !tile.stats else { return }
        lastAudio = tile.id
        routeAudio()
      }
      .onExitCommand {
        if overlay != nil {
          overlay = nil
        } else if immersive {
          immersive = false
        } else {
          dismiss()
        }
      }
      .onPlayPauseCommand { if let selected, !selected.stats { selected.session.toggle() } }
      .task {
        store.isPlaying = true
        await PlaybackAudio.activate()
        await initial()
        while !Task.isCancelled {
          do { try await Task.sleep(for: .seconds(30)) } catch { return }
          await refreshStats()
        }
      }
      .onDisappear {
        tiles.forEach { $0.session.suspendForScene() }
        store.isPlaying = false
      }
      .onChange(of: scenePhase, initial: true) { _, phase in
        if phase == .active {
          tiles.forEach { $0.session.restoreForScene() }
        } else {
          tiles.forEach { $0.session.suspendForScene() }
        }
      }
      .sheet(item: $overlay) { mode in overlayView(mode) }
  }
  private var toolbar: some View {
    HStack(spacing: RallyDesign.pt(10)) {
      Text("Multiview").font(RallyDesign.font(20, .bold))
      Spacer()
      RallyAction(title: "Add Stream", icon: "plus") { overlay = .add }.disabled(tiles.count >= 4)
      RallyAction(title: "Add Stats", icon: "chart.bar") { addStats() }.disabled(
        tiles.count >= 4 || tiles.contains { $0.stats })
      RallyAction(title: focusLayout ? "Grid" : "Focus", icon: "rectangle.split.2x2") {
        focusLayout.toggle()
      }
      RallyAction(
        title: pinned == nil ? "Audio Follows Focus" : "Audio Pinned", icon: "speaker.wave.2"
      ) {
        if pinned != nil {
          pinned = nil
        } else {
          pinned = selected?.stats == false ? selected?.id : lastAudio
        }
        routeAudio()
      }
      RallyAction(title: "Immersive", icon: "arrow.up.left.and.arrow.down.right") {
        immersive = true
        focused = selected?.id
      }
    }.focusSection()
  }
  private func tileView(_ tile: MultiTile, bounds: CGRect) -> some View {
    Group {
      if tile.stats {
        VStack(alignment: .leading, spacing: RallyDesign.pt(5)) {
          HStack {
            Text("PLAYER STATS").font(RallyDesign.font(12, .semibold)).tracking(RallyDesign.pt(1.5))
            Spacer()
            RallyAction(title: "Manage", bare: true) {
              selectedId = tile.id
              overlay = .manage
            }
          }
          ScrollView {
            LazyVStack(alignment: .leading, spacing: RallyDesign.pt(8)) {
              if statsEvents.isEmpty {
                Text("Player stats appear when published for your games.").font(
                  RallyDesign.font(10)
                ).foregroundStyle(RallyDesign.muted)
              }
              ForEach(statsEvents) { e in
                VStack(alignment: .leading, spacing: RallyDesign.pt(6)) {
                  Text(e.compactMatchup + " · " + e.scoreLine).font(RallyDesign.font(10, .semibold))
                  RallyPlayerTables(event: e, compact: true)
                }
              }
            }
          }.focusSection()
        }.padding(RallyDesign.pt(10)).background(RallyDesign.black).frame(
          width: RallyDesign.pt(bounds.width), height: RallyDesign.pt(bounds.height),
          alignment: .top
        ).focused($focused, equals: tile.id)
      } else {
        Button {
          selectedId = tile.id
          overlay = .manage
        } label: {
          ZStack(alignment: .bottomLeading) {
            RallyVideoSurface(player: tile.session.player).allowsHitTesting(false)
            if tile.session.loading {
              ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            if let error = tile.session.error {
              Text(error).font(RallyDesign.font(11)).multilineTextAlignment(.center).padding(
                RallyDesign.pt(20)
              )
              .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            if !immersive || focused == tile.id {
              HStack(spacing: RallyDesign.pt(6)) {
                if (pinned ?? lastAudio) == tile.id { Image(systemName: "speaker.wave.2.fill") }
                Text(tile.title).lineLimit(1)
                Spacer()
                if let e = tile.event {
                  Text("\(e.scoreAway ?? 0) – \(e.scoreHome ?? 0)").monospacedDigit()
                }
              }.font(RallyDesign.font(10, .semibold)).padding(RallyDesign.pt(9)).background(
                LinearGradient(
                  colors: [.clear, .black.opacity(0.82)], startPoint: .top, endPoint: .bottom))
            }
          }.frame(width: RallyDesign.pt(bounds.width), height: RallyDesign.pt(bounds.height))
            .background(.black)
        }.buttonStyle(RallyFlatButtonStyle()).focusEffectDisabled().focused(
          $focused, equals: tile.id
        )
        .overlay(Rectangle().stroke(.white.opacity(focused == tile.id ? 0.45 : 0), lineWidth: 1))
        .accessibilityIdentifier("multiview-tile-\(tile.id)")
        .accessibilityValue(
          tile.session.error != nil
            ? "Playback error"
            : tile.session.loading ? "Opening" : tile.session.playing ? "Playing" : "Paused")
      }
    }.frame(width: RallyDesign.pt(bounds.width), height: RallyDesign.pt(bounds.height)).clipped()
  }
  @ViewBuilder private func overlayView(_ mode: MultiOverlay) -> some View {
    if mode == .source {
      SourcePicker(
        event: pendingEvent, model: sourceModel,
        select: { candidate in
          overlay = nil
          Task {
            await add(event: pendingEvent, candidate: candidate, replacing: changeId)
            changeId = nil
          }
        },
        dismiss: {
          overlay = nil
          changeId = nil
        },
        settings: {
          overlay = nil
          navigate(.settings)
        })
    } else {
      RallyCanvas {
        ZStack {
          RallyBackdrop()
          VStack(alignment: .leading, spacing: RallyDesign.pt(16)) {
            HStack {
              Text(mode == .add ? "Add to Multiview" : selected?.title ?? "Stream").font(
                RallyDesign.font(24, .bold))
              Spacer()
              RallyAction(title: "Done") { overlay = nil }
            }
            ScrollView {
              LazyVStack(alignment: .leading, spacing: RallyDesign.pt(10)) {
                if mode == .add {
                  RallyAction(title: "Player Stats", icon: "chart.bar") {
                    overlay = nil
                    addStats()
                  }.disabled(tiles.contains { $0.stats } || tiles.count >= 4 && changeId == nil)
                  Text("LIVE GAMES").font(RallyDesign.font(12, .semibold)).tracking(
                    RallyDesign.pt(2))
                  if live.isEmpty {
                    Text("No games are live right now.").foregroundStyle(RallyDesign.muted)
                  }
                  ForEach(live) { e in
                    RallyAction(title: e.compactMatchup) {
                      pendingEvent = e
                      overlay = .source
                      Task { await sourceModel.load(e, container: store.container) }
                    }
                  }
                  Text("YOUR CHANNELS").font(RallyDesign.font(12, .semibold)).tracking(
                    RallyDesign.pt(2))
                  if channels.isEmpty {
                    RallyAction(title: "Configure Sources") {
                      overlay = nil
                      navigate(.settings)
                    }
                  }
                  ForEach(channels) { c in
                    RallyAction(title: c.name) {
                      overlay = nil
                      Task {
                        await add(channel: c, replacing: changeId)
                        changeId = nil
                      }
                    }
                  }
                } else if let tile = selected {
                  if !tile.stats {
                    RallyAction(title: tile.session.playbackRequested ? "Pause" : "Play") {
                      tile.session.toggle()
                    }
                    RallyAction(title: "Fullscreen") {
                      overlay = nil
                      navigate(
                        .playerFull(
                          target: tile.channel?.id ?? tile.candidate?.playbackTarget.absoluteString
                            ?? "auto", candidate: tile.candidate, eventId: tile.event?.id))
                    }
                    RallyAction(title: "Promote to Main") {
                      if let i = tiles.firstIndex(where: { $0.id == tile.id }) {
                        tiles.swapAt(0, i)
                        focusLayout = true
                      }
                      overlay = nil
                    }
                    RallyAction(title: pinned == tile.id ? "Unpin Audio" : "Pin Audio") {
                      pinned = pinned == tile.id ? nil : tile.id
                      lastAudio = tile.id
                      routeAudio()
                    }
                    RallyAction(title: "Change Stream") {
                      changeId = tile.id
                      overlay = .add
                    }
                    if let event = tile.event {
                      RallyAction(title: "Pick Source") {
                        pendingEvent = event
                        changeId = tile.id
                        overlay = .source
                        Task { await sourceModel.load(event, container: store.container) }
                      }
                    }
                    RallyAction(title: "Retry") {
                      Task {
                        await tile.session.open(
                          tile.channel?.id ?? tile.candidate?.playbackTarget.absoluteString ?? "",
                          event: tile.event, candidate: tile.candidate, container: store.container)
                      }
                    }
                    if let next = tiles.first(where: { $0.id != tile.id }) {
                      RallyAction(title: "Swap with \(next.title)") {
                        if let a = tiles.firstIndex(where: { $0.id == tile.id }),
                          let b = tiles.firstIndex(where: { $0.id == next.id })
                        {
                          tiles.swapAt(a, b)
                        }
                        overlay = nil
                      }
                    }
                    if tile.event != nil {
                      RallyAction(title: "Compare Player Stats") {
                        overlay = nil
                        addStats()
                      }.disabled(tiles.count >= 4 || tiles.contains { $0.stats })
                    }
                  }
                  RallyAction(title: "Remove Tile", icon: "minus.circle") {
                    remove(tile)
                    overlay = nil
                  }
                }
              }.padding(.vertical, RallyDesign.pt(8))
            }.focusSection()
          }.padding(RallyDesign.pt(60))
        }
      }.onExitCommand { overlay = nil }.presentationBackground(.black)
    }
  }
  private func initial() async {
    live = (try? await store.container.sports.liveEvents()) ?? []
    channels = (try? await store.container.iptv.channels()) ?? []
    if !tiles.isEmpty {
      for tile in tiles where !tile.stats {
        if tile.session.player.currentItem != nil && tile.session.error == nil {
          tile.session.restoreForScene()
        } else {
          await tile.session.open(
            tile.channel?.id ?? tile.candidate?.playbackTarget.absoluteString ?? "auto",
            event: tile.event, candidate: tile.candidate, container: store.container)
        }
      }
      capPlayers()
      await refreshStats()
      loading = false
      focused = selectedId ?? lastAudio ?? tiles.first?.id
      return
    }
    let ids = Array(([eventId].compactMap { $0 } + eventIds).prefix(4))
    if let initialSource {
      let event: SportEvent?
      if let eventId {
        event = try? await store.container.sports.event(id: eventId)
      } else {
        event = nil
      }
      await add(event: event, candidate: initialSource)
    }
    for id in ids {
      if initialSource != nil && id == eventId { continue }
      if let event = try? await store.container.sports.event(id: id) {
        await sourceModel.load(event, container: store.container)
        if let candidate = sourceModel.candidates.first {
          await add(event: event, candidate: candidate)
        }
      }
    }
    if let channelId, let channel = channels.first(where: { $0.id == channelId }), tiles.count < 4 {
      await add(channel: channel)
    }
    if tiles.isEmpty && ids.isEmpty && channelId == nil && initialSource == nil { overlay = .add }
    loading = false
    focused = tiles.first?.id
  }
  private func add(
    event: SportEvent? = nil, channel: IptvChannel? = nil, candidate: StreamCandidate? = nil,
    replacing: String? = nil
  ) async {
    guard tiles.count < 4 || replacing != nil else { return }
    let tile = MultiTile(event: event, channel: channel ?? candidate?.channel, candidate: candidate)
    if let replacing, let index = tiles.firstIndex(where: { $0.id == replacing }) {
      let old = tiles[index]
      old.session.stop()
      tiles[index] = tile
      if pinned == old.id { pinned = tile.id }
    } else {
      tiles.append(tile)
    }
    lastAudio = tile.id
    routeAudio()
    if scenePhase != .active { tile.session.suspendForScene() }
    await tile.session.open(
      tile.channel?.id ?? candidate?.playbackTarget.absoluteString ?? "", event: event,
      candidate: candidate, container: store.container)
    capPlayers()
    focused = tile.id
    await refreshStats()
  }
  private func addStats() {
    guard tiles.count < 4, !tiles.contains(where: { $0.stats }) else { return }
    tiles.append(MultiTile(stats: true))
    Task { await refreshStats() }
    capPlayers()
  }
  private func remove(_ tile: MultiTile) {
    tile.session.stop()
    tiles.removeAll { $0.id == tile.id }
    if pinned == tile.id { pinned = nil }
    if lastAudio == tile.id { lastAudio = tiles.first { !$0.stats }?.id }
    routeAudio()
    capPlayers()
    focused = tiles.first?.id
  }
  private func routeAudio() {
    MultiViewGeometry.routeAudio(tiles, pinned: pinned, focused: lastAudio)
  }
  private func capPlayers() {
    let count = tiles.filter { !$0.stats }.count
    tiles.filter { !$0.stats }.forEach { $0.session.setMultiViewCaps(count: count) }
    routeAudio()
  }
  private func refreshStats() async {
    guard tiles.contains(where: { $0.stats }) else { return }
    var slate: [SportEvent] = []
    if tiles.contains(where: {
      TextMatching.normalize($0.channel?.name ?? $0.candidate?.title ?? "").replacingOccurrences(
        of: " ", with: ""
      ).contains("redzone")
    }) {
      let events = (try? await store.container.sports.events(forDate: Date(), league: "NFL")) ?? []
      slate = MultiViewGeometry.redZoneSlate(events, date: Date())
    }
    let unique = MultiViewGeometry.statsEvents(tiles, slate: slate)
    var result: [SportEvent] = []
    let repository = store.container.sports
    for offset in stride(from: 0, to: unique.count, by: 2) {
      let batch = Array(unique.dropFirst(offset).prefix(2))
      let summaries = await withTaskGroup(of: SportEvent.self) { group in
        for event in batch {
          group.addTask { (try? await repository.eventSummary(event)) ?? event }
        }
        var values: [SportEvent] = []
        for await event in group { values.append(event) }
        return values
      }
      guard !Task.isCancelled else { return }
      result += summaries
      for summary in summaries {
        for tile in tiles where tile.event?.id == summary.id { tile.event = summary }
      }
    }
    result.sort { $0.startTime < $1.startTime }
    statsEvents = result
  }
}
