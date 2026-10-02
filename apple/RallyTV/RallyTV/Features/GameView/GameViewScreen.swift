import AVFoundation
import SwiftUI

private enum PlayerOverlay: String, Identifiable {
  case source, audio, captions, quality, diagnostics
  var id: String { rawValue }
}
struct GameViewScreen: View {
  @Environment(RallyStore.self) private var store
  @Environment(\.dismiss) private var dismiss
  @Environment(\.scenePhase) private var scenePhase
  let target: String
  let eventId: String?
  var initialCandidate: StreamCandidate? = nil
  var initialClipTitle: String? = nil
  var startFullscreen = false
  let navigate: (RallyRoute) -> Void
  @State private var selectedMoment: GamePlay?
  @State private var session = PlaybackSession()
  @State private var sources = SourceModel()
  @State private var event: SportEvent?
  @State private var live: [SportEvent] = []
  @State private var full = false
  @State private var controls = true
  @State private var controlActivity = 0
  @State private var nowPlaying: RallyNowPlaying?
  @State private var tab = "Stats"
  @State private var rail = "Key Moments"
  @State private var overlay: PlayerOverlay?
  @State private var selected: StreamCandidate?
  @State private var attempted = Set<String>()
  @State private var clipTitle: String?
  @FocusState private var controlFocus: String?
  private var showsSeekControl: Bool {
    session.seekable && session.duration.isFinite && session.duration > 0
  }
  var body: some View {
    ZStack {
      if full {
        RallyVideoSurface(player: session.player).frame(
          width: RallyDesign.pt(960), height: RallyDesign.pt(540)
        ).background(.black)
          .focusable(!controls).focusEffectDisabled().onTapGesture { controls = true }.onMoveCommand
        { d in
          recordControlInteraction()
          if d == .left { session.seek(-10) }
          if d == .right { session.seek(10) }
          if d == .down || d == .up { controlFocus = "play-pause" }
        }
        if controls { fullOverlay.transition(.opacity) }
      } else {
        gameView
      }
      if session.loading {
        VStack {
          ProgressView()
          Text("Opening source…").font(RallyDesign.font(12))
        }.padding(RallyDesign.pt(20)).background(
          .black.opacity(0.7), in: RoundedRectangle(cornerRadius: RallyDesign.pt(8)))
      }
      if let error = session.error {
        VStack(spacing: RallyDesign.pt(14)) {
          Text(error).font(RallyDesign.font(14)).multilineTextAlignment(.center).frame(
            width: RallyDesign.pt(420))
          HStack {
            RallyAction(title: "Retry", primary: true) { Task { await start() } }
            RallyAction(title: "Pick Source") { overlay = .source }
            RallyAction(title: "Back") { dismiss() }
          }
        }.padding(RallyDesign.pt(24)).background(
          RallyDesign.black, in: RoundedRectangle(cornerRadius: RallyDesign.pt(10)))
      }
    }.onPlayPauseCommand {
      session.remoteTransport(.toggle)
      controls = true
      controlActivity += 1
    }
    .onExitCommand {
      if overlay != nil {
        overlay = nil
      } else if full {
        if startFullscreen || eventId == nil {
          dismiss()
          return
        }
        full = false
        controls = true
      } else {
        dismiss()
      }
    }
    .onMoveCommand { _ in
      recordControlInteraction()
    }
    .animation(
      store.container.settings.reducedMotion ? nil : .easeOut(duration: 0.18), value: controls
    )
    .task {
      store.isPlaying = true
      await PlaybackAudio.activate()
      await load()
      nowPlaying = RallyNowPlaying(session: session)
      while !Task.isCancelled {
        do { try await Task.sleep(for: .seconds(30)) } catch { return }
        if let event {
          self.event = (try? await store.container.sports.eventSummary(event)) ?? event
        }
      }
    }
    .task(id: "\(full):\(controls):\(controlActivity)") {
      if controls && full {
        do { try await Task.sleep(for: .seconds(8)) } catch { return }
        if overlay == nil { controls = false }
      }
    }
    .sheet(item: $selectedMoment) { play in
      RallyCanvas {
        ZStack {
          RallyBackdrop()
          VStack(alignment: .leading, spacing: RallyDesign.pt(16)) {
            Text("Scoring Play").font(RallyDesign.font(22, .bold))
            Text([play.period.map { "Q\($0)" }, play.clock].compactMap { $0 }.joined(separator: " · "))
              .foregroundStyle(RallyDesign.muted)
            if let event {
              Text("\(event.awayTeam?.abbreviation ?? "Away") \(play.awayScore ?? 0) · \(event.homeTeam?.abbreviation ?? "Home") \(play.homeScore ?? 0)")
            }
            Text(play.text).font(RallyDesign.font(15))
            HStack {
              RallyAction(title: "Play-by-Play") { tab = "Plays"; selectedMoment = nil }
              RallyAction(title: "Done") { selectedMoment = nil }
            }
          }.frame(width: RallyDesign.pt(540))
        }
      }
    }
    .onDisappear {
      nowPlaying?.stop()
      nowPlaying = nil
      session.stop()
      store.isPlaying = false
    }
    .onChange(of: scenePhase, initial: true) { _, phase in
      if phase == .active { session.restoreForScene() } else { session.suspendForScene() }
    }
    .onChange(of: session.error) { _, error in if error != nil { Task { await fallback() } } }
    .onChange(of: full) { _, _ in controlFocus = "play-pause" }
    .onChange(of: controls) { _, visible in
      if full && visible { controlFocus = "play-pause" }
    }
    .onChange(of: controlFocus) { _, _ in
      if full && controls { controlActivity += 1 }
    }
    .onChange(of: overlay) { _, _ in recordControlInteraction() }
    .onChange(of: session.playing) { _, _ in
      controls = true
      controlActivity += 1
    }
    .sheet(item: $overlay) { mode in overlayView(mode) }
  }
  private func recordControlInteraction() {
    controls = true
    controlActivity += 1
    store.interacted()
  }
  private var gameView: some View {
    HStack(alignment: .top, spacing: RallyDesign.pt(16)) {
      VStack(alignment: .leading, spacing: RallyDesign.pt(5)) {
        scoreHeader.frame(height: RallyDesign.pt(34))
        Button {
          full = true
          controls = true
        } label: {
          ZStack(alignment: .topLeading) {
            RallyVideoSurface(player: session.player).allowsHitTesting(false)
            BundleArt.image("rally_mark_ui.png").resizable().scaledToFit()
              .frame(width: RallyDesign.pt(16), height: RallyDesign.pt(18))
              .padding(RallyDesign.pt(12)).opacity(0.6)
          }.frame(width: RallyDesign.pt(608), height: RallyDesign.pt(342)).clipShape(
            RoundedRectangle(cornerRadius: RallyDesign.pt(8)))
        }.buttonStyle(RallyFlatButtonStyle()).focusEffectDisabled().focused(
          $controlFocus, equals: "Video player"
        )
        .onMoveCommand { direction in if direction == .up { controlFocus = "header-source" } }
        .accessibilityLabel("Video player").accessibilityIdentifier("Video player")
        playerControls(fullscreen: false).frame(
          width: RallyDesign.pt(608), height: RallyDesign.pt(33))
        HStack {
          RallyTabs(tabs: ["Key Moments", "Other Live Games"], selection: $rail)
          Spacer()
        }.frame(height: RallyDesign.pt(22))
        ScrollView(.horizontal) {
          HStack(alignment: .top, spacing: RallyDesign.pt(10)) {
            if rail == "Key Moments", let event {
              let clips = event.highlightClips.filter { $0.streamUrl != nil }
              let scoring = event.plays.filter(\.isScoringPlay).prefix(max(0, 4 - clips.count))
              if clips.isEmpty && scoring.isEmpty {
                Text("Key moments appear as the league publishes them.").font(RallyDesign.font(10))
                  .foregroundStyle(RallyDesign.muted).padding(.vertical, RallyDesign.pt(12))
              }
              ForEach(Array(clips.prefix(4))) { clip in
                Button {
                  if let url = clip.streamUrl {
                    navigate(.playerClip(url: url, title: clip.title, eventId: event.id))
                  }
                } label: {
                  HStack(alignment: .top, spacing: RallyDesign.pt(7)) {
                    RallyRemoteImage(url: clip.thumbnailUrl).frame(
                      width: RallyDesign.pt(67), height: RallyDesign.pt(44)
                    ).clipShape(
                      RoundedRectangle(cornerRadius: RallyDesign.pt(6)))
                    VStack(alignment: .leading, spacing: RallyDesign.pt(4)) {
                      Text(clip.title).font(RallyDesign.font(8, .semibold)).lineLimit(2)
                      Text(clip.durationSeconds.map { "\($0)s" } ?? "Highlight").font(
                        RallyDesign.font(8)
                      ).foregroundStyle(RallyDesign.muted)
                    }
                  }.frame(width: RallyDesign.pt(144.5), height: RallyDesign.pt(45))
                }.buttonStyle(RallyMediaFocus()).focusEffectDisabled().accessibilityIdentifier(
                  "moment-\(clip.id)")
              }
              ForEach(Array(scoring)) { play in
                Button { selectedMoment = play } label: {
                  HStack(alignment: .top, spacing: RallyDesign.pt(7)) {
                    HStack(spacing: RallyDesign.pt(3)) {
                      RallyTeamLogo(team: event.awayTeam, size: 25)
                      RallyTeamLogo(team: event.homeTeam, size: 25)
                    }.frame(width: RallyDesign.pt(67), height: RallyDesign.pt(44))
                      .background(RallyDesign.surface, in: RoundedRectangle(cornerRadius: RallyDesign.pt(6)))
                    VStack(alignment: .leading, spacing: RallyDesign.pt(4)) {
                      Text(play.text).font(RallyDesign.font(8, .semibold)).lineLimit(2)
                      Text([play.period.map { "Q\($0)" }, play.clock].compactMap { $0 }.joined(separator: " · "))
                        .font(RallyDesign.font(8)).foregroundStyle(RallyDesign.muted)
                    }
                  }.frame(width: RallyDesign.pt(144.5), height: RallyDesign.pt(45))
                }.buttonStyle(RallyMediaFocus()).focusEffectDisabled().accessibilityIdentifier("moment-play-" + play.id)
              }
            } else {
              ForEach(live) { e in
                Button {
                  navigate(.player(target: "auto", eventId: e.id))
                } label: {
                  HStack(spacing: RallyDesign.pt(7)) {
                    RallyMatchArtwork(event: e, compact: true).frame(
                      width: RallyDesign.pt(60), height: RallyDesign.pt(38)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: RallyDesign.pt(6)))
                    VStack(alignment: .leading, spacing: RallyDesign.pt(4)) {
                      Text(e.compactMatchup).font(RallyDesign.font(8, .semibold)).lineLimit(2)
                      Text(e.league + " · " + e.statusLabel).font(RallyDesign.font(7))
                        .foregroundStyle(RallyDesign.muted).lineLimit(1)
                    }
                  }.frame(width: RallyDesign.pt(144.5), height: RallyDesign.pt(49))
                }.buttonStyle(RallyMediaFocus()).focusEffectDisabled().accessibilityIdentifier(
                  "other-game-" + e.id)
              }
            }
          }.padding(.vertical, RallyDesign.pt(2))
        }.scrollIndicators(.hidden).focusSection().frame(height: RallyDesign.pt(49))
      }.frame(width: RallyDesign.pt(608), height: RallyDesign.pt(500), alignment: .top)
      VStack(alignment: .leading, spacing: RallyDesign.pt(10)) {
        HStack(spacing: RallyDesign.pt(6)) {
          sourceControls
          Spacer(minLength: 0)
          BundleArt.image("rally_mark_ui.png").resizable().scaledToFit().frame(
            width: RallyDesign.pt(25), height: RallyDesign.pt(26))
        }.frame(height: RallyDesign.pt(24))
        RallyTabs(tabs: ["Stats", "Plays", "Players", "Sources"], selection: $tab, compact: true)
          .font(
            RallyDesign.font(10)
          ).frame(height: RallyDesign.pt(27))
        if let event {
          if tab == "Stats" {
            compactStats(event).frame(maxHeight: .infinity, alignment: .top)
          } else {
            ScrollView {
              VStack(alignment: .leading, spacing: RallyDesign.pt(10)) {
                if tab == "Plays" {
                  RallyPlayList(event: event)
                } else if tab == "Players" {
                  RallyGamePlayers(event: event)
                } else {
                  ForEach(sources.candidates) { c in
                    RallyAction(title: c.title + " · " + (c.quality.resolution ?? "Auto")) {
                      Task {
                        selected = c
                        await start(c)
                      }
                    }
                  }
                  if sources.candidates.isEmpty {
                    RallyEmptyState(
                      title: "No sources", message: "Connect a provider or addon.",
                      actionTitle: "Settings"
                    ) { navigate(.settings) }
                  }
                }
              }
            }.focusSection()
          }
        } else {
          RallyPanel("Live TV") {
            Text(session.sourceTitle)
            Text("Game stats appear when a stream is associated with an event.").font(
              RallyDesign.font(11)
            ).foregroundStyle(RallyDesign.muted)
            RallyAction(title: "Other Live Games") { navigate(.live) }
          }
          Spacer()
        }
      }.padding(RallyDesign.pt(10)).frame(
        width: RallyDesign.pt(284), height: RallyDesign.pt(500), alignment: .top
      ).background(
        RallyDesign.surface.opacity(0.35), in: RoundedRectangle(cornerRadius: RallyDesign.pt(8))
      ).overlay(
        RoundedRectangle(cornerRadius: RallyDesign.pt(8)).stroke(RallyDesign.edge, lineWidth: 0.6))
    }.padding(.horizontal, RallyDesign.pt(26)).padding(.top, RallyDesign.pt(20))
  }
  private var scoreHeader: some View {
    HStack(spacing: RallyDesign.pt(10)) {
      if let event {
        RallyTeamLogo(team: event.awayTeam, size: 32)
        Text(event.awayTeam?.abbreviation ?? "AWAY").font(RallyDesign.font(11, .semibold))
        Text(event.scoreAway.map(String.init) ?? "—").font(RallyDesign.font(26, .bold))
        VStack(spacing: RallyDesign.pt(3)) {
          Text(event.statusLabel).foregroundStyle(event.status.isLive ? .red : .white).font(
            RallyDesign.font(10, .semibold))
          Text(event.gameStatusDetail ?? "").font(RallyDesign.font(9)).foregroundStyle(
            RallyDesign.muted)
        }.frame(width: RallyDesign.pt(86))
        Text(event.scoreHome.map(String.init) ?? "—").font(RallyDesign.font(26, .bold))
        Text(event.homeTeam?.abbreviation ?? "HOME").font(RallyDesign.font(11, .semibold))
        RallyTeamLogo(team: event.homeTeam, size: 32)
      } else {
        Text(session.sourceTitle).font(RallyDesign.font(16, .semibold)).lineLimit(1)
      }
    }.frame(maxWidth: .infinity, alignment: .center)
  }
  private var sourceControls: some View {
    HStack(spacing: RallyDesign.pt(6)) {
      if clipTitle != nil, let event, event.status.isLive {
        RallyAction(title: "Return to Live", bare: true) {
          navigate(.player(target: "auto", eventId: event.id))
        }
      }
      RallyAction(
        title: selected.flatMap { candidate in
          sources.candidates.firstIndex(where: { $0.id == candidate.id }).map { "Source \($0 + 1)" }
        } ?? "Source 1", icon: "chevron.down", bare: true, action: { overlay = .source },
        trailingIcon: true
      ).accessibilityIdentifier("header-source").focused(
        $controlFocus, equals: "header-source")
      RallyAction(title: session.quality, bare: true) { overlay = .quality }
    }
  }
  private func playerControls(fullscreen: Bool) -> some View {
    HStack(spacing: RallyDesign.pt(6)) {
      control(
        session.playbackRequested ? "Pause" : "Play", icon: session.playbackRequested ? "pause.fill" : "play.fill"
      ) { session.toggle() }
      control("Restart", icon: "arrow.counterclockwise") {
        if session.seekable {
          session.restart()
        } else {
          Task {
            await start()
            if let clipTitle { session.sourceTitle = clipTitle }
          }
        }
      }.disabled(session.player.currentItem?.status != .readyToPlay)
      if !fullscreen || eventId != nil {
        control(
          fullscreen ? "Game View" : "Fullscreen",
          icon: fullscreen ? "rectangle.inset.filled" : "arrow.up.left.and.arrow.down.right"
        ) {
          full.toggle()
          controls = true
        }
      }
      control("Pick Source", icon: "antenna.radiowaves.left.and.right") { overlay = .source }
      control("Audio", icon: "speaker.wave.2") { overlay = .audio }
      control("Captions", icon: "captions.bubble") { overlay = .captions }
      control("Multiview", icon: "square.grid.2x2") {
        if let candidate = selected ?? initialCandidate {
          navigate(.multiViewSource(candidate: candidate, eventId: eventId))
        } else if let url = URL(string: target), ["http", "https"].contains(url.scheme ?? "") {
          navigate(
            .multiViewSource(
              candidate: StreamCandidate.direct(
                url: url, title: session.sourceTitle, headers: session.headers), eventId: eventId))
        } else {
          navigate(
            .multiView(channelId: eventId == nil ? target : nil, eventId: eventId, eventIds: []))
        }
      }
      if fullscreen { control("Diagnostics", icon: "waveform.path.ecg") { overlay = .diagnostics } }
    }.focusSection().onMoveCommand { direction in
      recordControlInteraction()
      if fullscreen && direction == .up {
        controlFocus = showsSeekControl ? "fullscreen-seek" : "fullscreen-quality"
      }
    }
  }
  private func control(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
    Button(action: action) {
      HStack(spacing: RallyDesign.pt(5)) {
        Image(systemName: icon)
        Text(title).lineLimit(1).minimumScaleFactor(0.6)
      }.font(RallyDesign.font(9, .medium)).frame(maxWidth: .infinity).frame(
        height: RallyDesign.pt(27))
    }.buttonStyle(RallyButtonStyle(bare: true)).focusEffectDisabled()
      .focused($controlFocus, equals: title == "Play" || title == "Pause" ? "play-pause" : title)
      .accessibilityIdentifier(title)
  }
  private var fullOverlay: some View {
    VStack {
      HStack {
        if let event {
          Text(event.compactMatchup + "  ·  " + event.scoreLine).font(
            RallyDesign.font(16, .semibold))
        }
        Spacer()
        BundleArt.image("rally_mark_ui.png").resizable().scaledToFit().frame(
          width: RallyDesign.pt(28), height: RallyDesign.pt(28))
      }.padding(RallyDesign.pt(30)).background(
        LinearGradient(colors: [.black.opacity(0.8), .clear], startPoint: .top, endPoint: .bottom))
      Spacer()
      VStack(spacing: RallyDesign.pt(12)) {
        HStack {
          Text(session.sourceTitle).font(RallyDesign.font(11))
          Spacer()
          Text(session.resolution).font(RallyDesign.font(10))
          RallyAction(title: session.quality, bare: true) { overlay = .quality }
            .focused($controlFocus, equals: "fullscreen-quality")
          if session.isLive {
            RallyAction(title: "Go Live", icon: "dot.radiowaves.left.and.right", bare: true) {
              session.liveEdge()
            }
          }
        }.focusSection().onMoveCommand { direction in
          recordControlInteraction()
          if direction == .down {
            controlFocus = showsSeekControl ? "fullscreen-seek" : "play-pause"
          }
        }
        if showsSeekControl {
          Button {
            session.toggle()
          } label: {
            GeometryReader { geo in
              ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.25))
                Capsule().fill(.white).frame(
                  width: geo.size.width * min(1, max(0, session.elapsed / max(1, session.duration)))
                )
              }
            }.frame(height: RallyDesign.pt(5)).padding(.vertical, RallyDesign.pt(6))
          }.buttonStyle(RallyButtonStyle(bare: true)).focusEffectDisabled()
            .focused($controlFocus, equals: "fullscreen-seek").onMoveCommand {
              direction in
              recordControlInteraction()
              if direction == .left || direction == .right {
                session.seek(direction == .left ? -10 : 10)
                controlFocus = "fullscreen-seek"
              }
              if direction == .up { controlFocus = "fullscreen-quality" }
              if direction == .down { controlFocus = "play-pause" }
            }.accessibilityLabel("Seek").accessibilityValue(
              "\(Int(session.elapsed)) of \(Int(session.duration)) seconds")
        }
        playerControls(fullscreen: true).frame(height: RallyDesign.pt(38))
      }.padding(RallyDesign.pt(22)).background(
        .black.opacity(0.72), in: RoundedRectangle(cornerRadius: RallyDesign.pt(8))
      ).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: RallyDesign.pt(8)))
        .padding(
          .horizontal, RallyDesign.pt(32)
        ).padding(.bottom, RallyDesign.pt(24))
    }
  }
  private func compactStats(_ e: SportEvent) -> some View {
    let stats = e.teamStats.filter { stat in
      !["spread", "moneyline", "over/under", "odds", "prediction", "games played"].contains {
        stat.label.localizedCaseInsensitiveContains($0)
      }
    }.prefix(8)
    return VStack(alignment: .leading, spacing: RallyDesign.pt(7)) {
      RallyPanel("Team Leaders", compact: true, minimumHeight: 112) {
        if e.playerLeaders.isEmpty {
          Text("Player stats have not been published.").font(RallyDesign.font(9)).foregroundStyle(
            RallyDesign.muted)
        }
        RallyLeadersGrid(event: e)
      }.fixedSize(horizontal: false, vertical: true).accessibilityElement(children: .combine)
        .focusable().focusEffectDisabled().focused(
          $controlFocus, equals: "stats-leaders"
        )
        .accessibilityIdentifier("stats-leaders")
      RallyPanel("Team Stats", compact: true) {
        HStack {
          Text(e.awayTeam?.abbreviation ?? "Away")
          Spacer()
          Text(e.homeTeam?.abbreviation ?? "Home")
        }.font(RallyDesign.font(9, .semibold))
        VStack(spacing: 0) {
          ForEach(Array(stats.enumerated()), id: \.offset) { _, s in
            HStack(spacing: RallyDesign.pt(4)) {
              Text(s.awayValue).frame(width: RallyDesign.pt(30), alignment: .leading)
              Text(s.label).foregroundStyle(RallyDesign.muted).lineLimit(1)
                .frame(maxWidth: .infinity)
              Text(s.homeValue).frame(width: RallyDesign.pt(30), alignment: .trailing)
            }.font(RallyDesign.font(8)).frame(height: RallyDesign.pt(12))
          }
          if e.teamStats.isEmpty {
            Text("Waiting for published stats.").font(RallyDesign.font(9)).foregroundStyle(
              RallyDesign.muted)
          }
        }
      }.fixedSize(horizontal: false, vertical: true).accessibilityElement(children: .combine)
        .focusable().focusEffectDisabled().focused(
          $controlFocus, equals: "stats-team"
        )
        .accessibilityIdentifier("stats-team")
      RallyPanel(
        e.liveStats["Current Drive"] != nil
          ? "Current Drive" : e.status == .finished ? "Game Summary" : "Live Situation",
        compact: true
      ) {
        Text(e.liveStats["Current Drive"] ?? e.gameStatusDetail ?? e.statusLabel).font(
          RallyDesign.font(9))
        if let yards = e.liveStats["Drive Yards"] {
          Text(yards + " yards").font(RallyDesign.font(8)).foregroundStyle(RallyDesign.muted)
        }
        if let yard = e.liveStats["Drive Yard Line"].flatMap(Double.init) {
          Canvas { context, size in
            for index in 0...10 {
              let x = size.width * CGFloat(index) / 10
              var line = Path()
              line.move(to: CGPoint(x: x, y: 0))
              line.addLine(to: CGPoint(x: x, y: size.height))
              context.stroke(line, with: .color(.white.opacity(0.18)), lineWidth: 0.5)
            }
            let x = size.width * CGFloat(min(0.98, max(0.02, yard / 100)))
            context.fill(
              Path(ellipseIn: CGRect(x: x - 3, y: size.height / 2 - 3, width: 6, height: 6)),
              with: .color(.white))
          }.frame(height: RallyDesign.pt(10)).background(
            .white.opacity(0.04), in: RoundedRectangle(cornerRadius: RallyDesign.pt(3)))
        }
        if let p = e.winProbability.last {
          Text(
            "Win probability · \(e.homeTeam?.abbreviation ?? "Home") \(Int(p.homeWinPercentage*100))%"
          ).font(RallyDesign.font(8)).foregroundStyle(RallyDesign.muted)
        }
      }.fixedSize(horizontal: false, vertical: true).accessibilityElement(children: .combine)
        .focusable().focusEffectDisabled().focused(
          $controlFocus, equals: "stats-situation"
        )
        .accessibilityIdentifier("stats-situation")
      GeometryReader { geo in
        let tight = geo.size.height / RallyDesign.displayUnit < 70
        let rowHeight: CGFloat = tight ? 13 : 23
        let count = max(
          1, min(6, Int((geo.size.height / RallyDesign.displayUnit - 34) / (rowHeight + 2))))
        RallyPanel("Latest Play-by-Play", compact: true) {
          VStack(alignment: .leading, spacing: RallyDesign.pt(2)) {
            if e.plays.isEmpty {
              Text(e.liveStats["Latest Play"] ?? "Live updates will appear here.").font(
                RallyDesign.font(9)
              ).lineLimit(3)
            } else {
              ForEach(Array(e.plays.prefix(count))) { play in
                HStack(alignment: .top, spacing: RallyDesign.pt(5)) {
                  Text(
                    [play.period.map { "P\($0)" }, play.clock].compactMap { $0 }.joined(
                      separator: " · ")
                  )
                  .font(RallyDesign.font(7)).foregroundStyle(RallyDesign.muted).frame(
                    width: RallyDesign.pt(35), alignment: .leading)
                  Text(play.text).font(RallyDesign.font(8)).lineLimit(tight ? 1 : 2).frame(
                    maxWidth: .infinity, alignment: .leading)
                }.frame(height: RallyDesign.pt(rowHeight), alignment: .top)
              }
            }
            Spacer(minLength: 0)
          }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }.accessibilityElement(children: .combine).focusable().focusEffectDisabled().focused(
          $controlFocus, equals: "stats-plays"
        )
        .accessibilityIdentifier("stats-plays")
      }
    }.onMoveCommand { direction in if direction == .left { controlFocus = "Audio" } }
  }
  @ViewBuilder private func overlayView(_ mode: PlayerOverlay) -> some View {
    if mode == .source {
      SourcePicker(
        event: event, model: sources,
        select: { c in
          overlay = nil
          clipTitle = nil
          selected = c
          Task { await start(c) }
        }, dismiss: { overlay = nil },
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
              Text(mode.rawValue.capitalized).font(RallyDesign.font(24, .bold))
              Spacer()
              RallyAction(title: "Done") { overlay = nil }
            }
            switch mode {
            case .audio:
              if session.audioTracks.isEmpty {
                Text("This source has one default audio track.").foregroundStyle(RallyDesign.muted)
              }
              ForEach(Array(session.audioTracks.enumerated()), id: \.offset) { _, option in
                RallyAction(
                  title: option.displayName,
                  icon: session.selectedAudio == option.displayName ? "checkmark" : nil
                ) {
                  session.selectAudio(option)
                  overlay = nil
                }
              }
            case .captions:
              RallyAction(title: "Off", icon: session.selectedCaption == nil ? "checkmark" : nil) {
                session.selectCaption(nil)
                overlay = nil
              }
              if session.captionTracks.isEmpty {
                Text("This source does not include captions.").foregroundStyle(RallyDesign.muted)
              }
              ForEach(Array(session.captionTracks.enumerated()), id: \.offset) { _, option in
                RallyAction(title: option.displayName) {
                  session.selectCaption(option)
                  overlay = nil
                }
              }
            case .quality:
              ForEach(["Auto", "2160p", "1080p", "720p", "480p"], id: \.self) { q in
                RallyAction(title: q, icon: session.quality == q ? "checkmark" : nil) {
                  session.setQuality(q)
                  overlay = nil
                }
              }
              Text(
                "Resolution caps apply to adaptive streams. Available quality depends on the source."
              ).font(RallyDesign.font(11)).foregroundStyle(RallyDesign.muted)
            case .diagnostics:
              Text(
                "AVPlayer · \(session.resolution)\n\(session.bitrate) · \(session.fps)\nTime \(Int(session.elapsed))s · \(session.isLive ? "Live" : "On demand")\nBuffered \(Int(session.bufferedSeconds))s\nCodec \(session.codecs.isEmpty ? "Unavailable" : session.codecs)\nQuality \(session.quality)\nAudio \(session.selectedAudio ?? "Default")\nCaptions \(session.selectedCaption ?? "Off")"
              ).font(RallyDesign.font(14)).lineSpacing(RallyDesign.pt(10))
              Text(SelectBestStream.trace(candidates: sources.candidates, selectedId: selected?.id))
                .font(RallyDesign.font(10)).foregroundStyle(RallyDesign.muted)
            default: EmptyView()
            }
            Spacer()
          }.padding(RallyDesign.pt(60))
        }
      }.onExitCommand { overlay = nil }
        .onPlayPauseCommand {
          session.remoteTransport(.toggle)
          controls = true
          controlActivity += 1
        }
        .presentationBackground(.black)
    }
  }
  private func load() async {
    full = startFullscreen || eventId == nil
    clipTitle = initialClipTitle
    if target != "auto" {
      await start()
      if let clipTitle { session.sourceTitle = clipTitle }
    }
    if let eventId, let base = try? await store.container.sports.event(id: eventId) {
      event = (try? await store.container.sports.eventSummary(base)) ?? base
      await sources.load(base, container: store.container)
    } else if eventId == nil {
      await sources.loadChannels(container: store.container)
      if let initialCandidate, !sources.candidates.contains(where: { $0.id == initialCandidate.id })
      {
        sources.candidates.insert(initialCandidate, at: 0)
      }
    }
    live = (try? await store.container.sports.liveEvents()) ?? []
    if target == "auto" {
      await start()
    } else if initialCandidate == nil && clipTitle == nil, let event {
      session.sourceTitle = event.compactMatchup
    }
  }
  private func start(_ candidate: StreamCandidate? = nil) async {
    let candidate =
      candidate ?? selected ?? initialCandidate
      ?? sources.candidates.first {
        target == "auto" || $0.channel?.id == target || $0.playbackTarget.absoluteString == target
      }
    if target == "auto" && candidate == nil {
      session.loading = false
      session.error =
        "No matching source is configured. Choose Pick Source to connect your provider."
      return
    }
    selected = candidate
    if let candidate { attempted.insert(candidate.id) }
    await session.open(
      candidate?.channel?.id ?? candidate?.playbackTarget.absoluteString ?? target, event: event,
      candidate: candidate, container: store.container)
  }
  private func fallback() async {
    guard clipTitle == nil else { return }
    guard let candidate = sources.candidates.first(where: { !attempted.contains($0.id) }) else {
      return
    }
    await start(candidate)
  }
}
enum PlaybackAudio {
  static func activate() async {
    await Task.detached(priority: .userInitiated) {
      let session = AVAudioSession.sharedInstance()
      try? session.setCategory(.playback, mode: .moviePlayback)
      try? session.setActive(true)
    }.value
  }
}


private struct RallyGamePlayers: View {
  let event: SportEvent
  @State private var selected: GamePlayerBoxScore?
  var body: some View {
    LazyVStack(alignment: .leading, spacing: RallyDesign.pt(10)) {
      if event.allGamePlayers.isEmpty {
        Text("Player stats have not been published yet.").font(RallyDesign.font(11)).foregroundStyle(RallyDesign.muted)
      }
      ForEach(event.allGamePlayers) { team in
        HStack(spacing: RallyDesign.pt(6)) {
          RallyRemoteImage(url: team.logo, fit: true).frame(width: RallyDesign.pt(20), height: RallyDesign.pt(20))
          Text(team.name).font(RallyDesign.font(11, .semibold))
        }
        ForEach(team.players) { entry in
          Button { selected = entry } label: {
            VStack(alignment: .leading, spacing: RallyDesign.pt(5)) {
              HStack(spacing: RallyDesign.pt(6)) {
                RallyRemoteImage(url: entry.player.headshotUrl ?? team.logo, fit: true).frame(width: RallyDesign.pt(28), height: RallyDesign.pt(28))
                VStack(alignment: .leading, spacing: RallyDesign.pt(3)) {
                  Text(entry.player.displayName).font(RallyDesign.font(10, .semibold))
                  Text([entry.player.position, entry.player.jersey.map { "#" + $0 }].compactMap { $0 }.joined(separator: " · "))
                    .font(RallyDesign.font(8)).foregroundStyle(RallyDesign.muted)
                }
              }
              ForEach(entry.categories) { category in
                Text(category.id).font(RallyDesign.font(8, .semibold))
                Text(category.values.map { $0.0 + ": " + ($0.1.isEmpty ? "—" : $0.1) }.joined(separator: " · "))
                  .font(RallyDesign.font(9)).foregroundStyle(RallyDesign.muted).fixedSize(horizontal: false, vertical: true)
              }
            }.frame(maxWidth: .infinity, alignment: .leading).padding(RallyDesign.pt(7))
          }.buttonStyle(RallyButtonStyle(bare: true)).focusEffectDisabled()
            .accessibilityIdentifier("game-player-" + entry.id)
        }
      }
    }.sheet(item: $selected) { entry in
      RallyCanvas {
        ZStack {
          RallyBackdrop()
          VStack(alignment: .leading, spacing: RallyDesign.pt(14)) {
            Text(entry.player.displayName).font(RallyDesign.font(24, .bold))
            ScrollView {
              VStack(alignment: .leading, spacing: RallyDesign.pt(14)) {
                ForEach(entry.categories) { category in
                  Text(category.id).font(RallyDesign.font(14, .semibold))
                  ForEach(Array(category.values.enumerated()), id: \.offset) { _, value in
                    HStack { Text(value.0); Spacer(); Text(value.1.isEmpty ? "—" : value.1) }
                  }
                }
              }
            }.frame(maxHeight: RallyDesign.pt(330))
            RallyAction(title: "Done") { selected = nil }
          }.frame(width: RallyDesign.pt(450))
        }
      }
    }
  }
}
