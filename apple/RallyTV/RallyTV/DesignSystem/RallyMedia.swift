import SwiftUI

struct RallyHero: View {
  let event: SportEvent
  var detailed = false
  let watch: () -> Void
  let info: () -> Void
  var saved = false
  var save: (() -> Void)?
  var schedule: (() -> Void)?
  var body: some View {
    ZStack(alignment: .leading) {
      Group {
        if event.venueImageUrl != nil {
          RallyRemoteImage(url: event.venueImageUrl)
        } else {
          BundleArt.image(
            "hero_landscape_\(event.sport == "basketball" ? "basketball" : event.sport == "hockey" ? "hockey" : event.sport == "soccer" ? "soccer" : event.sport == "baseball" ? "baseball" : "football")_rally.jpg"
          ).resizable().scaledToFill()
        }
      }.frame(width: RallyDesign.pt(detailed ? 768 : 749), height: RallyDesign.pt(detailed ? 270 : 190))
        .clipped()
        // Fade the visible artwork bounds, so navigation never slices through
        // the middle of an oversized image's top fade.
        .frame(height: RallyDesign.pt(detailed ? 230 : 178))
        .clipped().opacity(detailed ? 0.66 : 0.72)
        .mask(
          LinearGradient(
            stops: [
              .init(color: .clear, location: 0), .init(color: .white.opacity(0.3), location: 0.34),
              .init(color: .white, location: 0.68), .init(color: .white.opacity(0.82), location: 1),
            ], startPoint: .leading, endPoint: .trailing)
        )
        .mask(
          LinearGradient(
            stops: [
              .init(color: .clear, location: 0), .init(color: .white.opacity(0.72), location: 0.18),
              .init(color: .white, location: 0.62),
              .init(color: .clear, location: 1),
            ], startPoint: .top, endPoint: .bottom)
        )
        .frame(maxWidth: .infinity, alignment: .trailing)
      VStack(alignment: .leading, spacing: RallyDesign.pt(7)) {
        HStack(spacing: RallyDesign.pt(8)) {
          Text(event.league)
          Text("·")
          if event.status.isLive {
            Circle().fill(.red).frame(width: RallyDesign.pt(6), height: RallyDesign.pt(6))
          }
          Text(event.status == .notStarted ? "UPCOMING" : event.statusLabel).foregroundStyle(
            event.status.isLive ? Color(hex: "EAFB78") : RallyDesign.muted)
        }.font(RallyDesign.font(12, .medium))
        Text(event.compactMatchup.isEmpty ? event.name : event.compactMatchup).font(
          RallyDesign.font(detailed ? 30 : 31, .bold)
        ).tracking(RallyDesign.pt(-0.45)).lineLimit(1).minimumScaleFactor(0.75).frame(
          maxWidth: RallyDesign.pt(500), alignment: .leading)
        if event.status == .notStarted {
          Text(
            event.startTime.formatted(
              .dateTime.weekday(.abbreviated).month(.abbreviated).day().hour().minute())
          )
          .font(RallyDesign.font(14, .medium)).foregroundStyle(RallyDesign.muted)
        } else {
          Text(event.scoreLine + "  ·  " + (event.gameStatusDetail ?? event.statusLabel)).font(
            RallyDesign.font(14, .medium)
          ).foregroundStyle(RallyDesign.muted)
        }
        if let venue = event.venue {
          Text(venue).font(RallyDesign.font(11)).foregroundStyle(RallyDesign.muted)
        }
        HStack(spacing: RallyDesign.pt(10)) {
          if event.status.isLive {
            RallyAction(title: "Watch Live", icon: "play.fill", primary: true, action: watch)
          }
          if let save {
            RallyAction(
              title: saved ? "In Watchlist" : "Add to Watchlist",
              icon: saved ? "checkmark" : "plus", action: save)
          } else {
            RallyAction(title: "More Info", primary: !event.status.isLive, action: info)
            if !event.status.isLive, let schedule {
              RallyAction(title: "Full Schedule", action: schedule)
            }
          }
        }.padding(.top, RallyDesign.pt(5)).focusSection()
      }.frame(maxWidth: RallyDesign.pt(510), alignment: .leading).padding(
        .leading, RallyDesign.pt(10))
    }.frame(height: RallyDesign.pt(detailed ? 230 : 178))
  }
}
struct RallyMatchArtwork: View {
  let event: SportEvent
  var compact = false
  var body: some View {
    let away = Color(hex: event.awayTeam?.colors.first ?? "303A45")
    let home = Color(hex: event.homeTeam?.colors.first ?? "33343C")
    ZStack {
      LinearGradient(
        stops: [
          .init(color: away.opacity(0.9), location: 0),
          .init(color: away.opacity(0.34), location: 0.42),
          .init(color: Color(hex: "071116"), location: 0.5),
          .init(color: home.opacity(0.34), location: 0.58),
          .init(color: home.opacity(0.9), location: 1),
        ], startPoint: .leading, endPoint: .trailing)
      HStack {
        RallyTeamLogo(team: event.awayTeam, size: compact ? 13 : 58)
        Spacer()
        HStack(spacing: RallyDesign.pt(compact ? 2 : 8)) {
          Text(event.scoreAway.map(String.init) ?? "—")
          Text("–").font(RallyDesign.font(compact ? 5 : 16))
          Text(event.scoreHome.map(String.init) ?? "—")
        }.font(RallyDesign.font(compact ? 7 : 14, .bold)).monospacedDigit().fixedSize()
          .foregroundStyle(.white)
        Spacer()
        RallyTeamLogo(team: event.homeTeam, size: compact ? 13 : 58)
      }.padding(.horizontal, RallyDesign.pt(compact ? 4 : 34))
      VStack {
        Spacer()
        HStack {
          Circle().fill(.red).frame(
            width: RallyDesign.pt(compact ? 2 : 6), height: RallyDesign.pt(compact ? 2 : 6))
          Text(event.statusLabel).fixedSize().font(RallyDesign.font(compact ? 4 : 10, .semibold))
            .foregroundStyle(
              .red)
          Spacer()
        }.padding(RallyDesign.pt(compact ? 3 : 10))
      }
    }
  }
}
struct RallyLiveCard: View {
  let event: SportEvent
  var width: CGFloat = 268
  let action: () -> Void
  var body: some View {
    Button(action: action) {
      VStack(alignment: .leading, spacing: RallyDesign.pt(4)) {
        RallyMatchArtwork(event: event).frame(
          width: RallyDesign.pt(width), height: RallyDesign.pt(width * 0.36)
        ).clipShape(
          RoundedRectangle(cornerRadius: RallyDesign.pt(10))).padding(.bottom, RallyDesign.pt(10))
        Text(event.compactMatchup).font(RallyDesign.font(15, .semibold)).lineLimit(1)
        Text(event.metadata).font(RallyDesign.font(11)).foregroundStyle(RallyDesign.muted)
          .lineLimit(1)
      }.frame(width: RallyDesign.pt(width), alignment: .leading)
    }.buttonStyle(RallyMediaFocus()).focusEffectDisabled().accessibilityIdentifier(
      "event-\(event.id)")
  }
}
struct RallyMediaFocus: ButtonStyle {
  func makeBody(configuration: Configuration) -> some View {
    MediaSurface(label: configuration.label)
  }
  private struct MediaSurface<Label: View>: View {
    let label: Label
    @Environment(\.isFocused) private var focused
    @Environment(RallyStore.self) private var store
    var body: some View {
      label.foregroundStyle(.white).brightness(focused ? 0.04 : 0)
        .overlay(
          RoundedRectangle(cornerRadius: RallyDesign.pt(10)).stroke(
            .white.opacity(focused ? 0.45 : 0), lineWidth: 0.7)
        ).scaleEffect(focused ? 1.025 : 1).zIndex(focused ? 1 : 0).animation(
          store.settings.reducedMotion ? nil : .easeOut(duration: 0.16), value: focused)
    }
  }
}
struct RallyHighlightCard: View {
  let item: HighlightItem
  var width: CGFloat = 268
  let action: () -> Void
  var body: some View {
    Button(action: action) {
      VStack(alignment: .leading, spacing: RallyDesign.pt(8)) {
        ZStack(alignment: .bottomLeading) {
          RallyRemoteImage(url: item.clip.thumbnailUrl).background(RallyDesign.surface)
          Text(
            item.clip.durationSeconds.map { "\($0/60):" + String(format: "%02d", $0 % 60) }
              ?? "HIGHLIGHT"
          ).font(RallyDesign.font(9, .semibold)).padding(RallyDesign.pt(5)).background(
            .black.opacity(0.7), in: RoundedRectangle(cornerRadius: RallyDesign.pt(4))
          ).padding(RallyDesign.pt(7))
        }.frame(width: RallyDesign.pt(width), height: RallyDesign.pt(width * 0.36)).clipShape(
          RoundedRectangle(cornerRadius: RallyDesign.pt(10)))
        Text(item.clip.title).font(RallyDesign.font(13, .medium)).lineLimit(1)
        Text(item.league + " · Highlights").font(RallyDesign.font(10)).foregroundStyle(
          RallyDesign.muted)
      }.frame(width: RallyDesign.pt(width), alignment: .leading)
    }.buttonStyle(RallyMediaFocus()).focusEffectDisabled().accessibilityIdentifier(
      "clip-\(item.id)")
  }
}
struct RallyScheduleRow: View {
  let event: SportEvent
  var reminder = false
  let action: () -> Void
  let remind: () -> Void
  var body: some View {
    HStack(spacing: RallyDesign.pt(12)) {
      Button(action: action) {
        HStack(spacing: RallyDesign.pt(16)) {
          Text(
            event.status == .notStarted
              ? event.startTime.formatted(date: .omitted, time: .shortened) : event.statusLabel
          ).frame(width: RallyDesign.pt(80), alignment: .leading)
          RallyTeamLogo(team: event.awayTeam, size: 27)
          RallyTeamLogo(team: event.homeTeam, size: 27)
          Text(event.compactMatchup).frame(maxWidth: .infinity, alignment: .leading).lineLimit(1)
          if event.status != .notStarted {
            Text(
              "\(event.scoreAway.map(String.init) ?? "—") – \(event.scoreHome.map(String.init) ?? "—")"
            ).monospacedDigit()
          }
          Text(event.league).foregroundStyle(RallyDesign.muted).frame(
            width: RallyDesign.pt(55), alignment: .leading)
        }.font(RallyDesign.font(11)).frame(maxWidth: .infinity).padding(
          .horizontal, RallyDesign.pt(14)
        ).frame(
          height: RallyDesign.pt(28))
      }.buttonStyle(RallyButtonStyle(bare: true)).focusEffectDisabled().accessibilityIdentifier(
        "schedule-\(event.id)")
      RallyAction(title: "", icon: reminder ? "bell.fill" : "bell", bare: true, action: remind)
        .accessibilityLabel(reminder ? "Remove reminder" : "Set reminder").frame(
          width: RallyDesign.pt(32))
    }.frame(height: RallyDesign.pt(38)).overlay(alignment: .bottom) {
      Rectangle().fill(RallyDesign.edge).frame(height: RallyDesign.pt(0.5))
    }
  }
}
struct RallyLeagueShortcut: View {
  let league: String
  let action: () -> Void
  var body: some View {
    Button(action: action) {
      VStack(spacing: RallyDesign.pt(4)) {
        RallyLeagueMark(league: league, size: 29)
        Text(league == "Champions League" ? "UCL" : league == "Serie A" ? "Serie A" : league).font(
          RallyDesign.font(8.5, .semibold)
        ).lineLimit(1).minimumScaleFactor(0.75)
      }
      .frame(maxWidth: .infinity).frame(height: RallyDesign.pt(57)).background(
        RallyDesign.surface.opacity(0.35), in: RoundedRectangle(cornerRadius: RallyDesign.pt(8))
      ).overlay(
        RoundedRectangle(cornerRadius: RallyDesign.pt(8)).stroke(
          RallyDesign.edge.opacity(0.55), lineWidth: 0.5))
    }.buttonStyle(RallyMediaFocus()).focusEffectDisabled().accessibilityIdentifier(
      "sport-\(league)")
  }
}

struct RallyLeadersGrid: View {
  let event: SportEvent
  var body: some View {
    HStack(alignment: .top, spacing: RallyDesign.pt(10)) {
      ForEach(Array([event.awayTeam, event.homeTeam].enumerated()), id: \.offset) { _, team in
        VStack(alignment: .leading, spacing: RallyDesign.pt(4)) {
          Text(team?.abbreviation ?? "Team").font(RallyDesign.font(9, .semibold))
          ForEach(
            Array(
              event.playerLeaders.filter { $0.teamAbbreviation == team?.abbreviation }.prefix(3)
                .enumerated()), id: \.offset
          ) { _, p in
            HStack(spacing: RallyDesign.pt(5)) {
              RallyRemoteImage(url: p.headshotUrl, fit: true).frame(
                width: RallyDesign.pt(22), height: RallyDesign.pt(26))
              VStack(alignment: .leading, spacing: RallyDesign.pt(1)) {
                Text(p.playerShortName).font(RallyDesign.font(8, .semibold)).lineLimit(1)
                Text(p.statDisplay).font(RallyDesign.font(8)).lineLimit(1)
                Text(p.category).font(RallyDesign.font(7)).foregroundStyle(RallyDesign.muted)
              }
            }.frame(height: RallyDesign.pt(29))
          }
        }.frame(maxWidth: .infinity, alignment: .leading)
      }
    }
  }
}
