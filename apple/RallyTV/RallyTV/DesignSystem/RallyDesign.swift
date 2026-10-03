import CoreText
import SwiftUI
import UIKit

/// Shared composition grid in the same 960×540 design units as Android TV.
/// Convert design units to native points; fonts and video render at display resolution.
enum RallyDesign {
  static let black = Color(hex: "050507"), surface = Color(hex: "101317"),
    edge = Color(hex: "252B30"), muted = Color(hex: "AEB4BD")
  static let margin: CGFloat = 60, gap: CGFloat = 12, radius: CGFloat = 8
  static let accent = LinearGradient(
    colors: [Color(hex: "E5EE92"), Color(hex: "AAE7CE"), Color(hex: "5EDBF0")],
    startPoint: .leading, endPoint: .trailing)
  static let fontName: String = {
    guard let url = Bundle.main.url(forResource: "inter_variable", withExtension: "ttf") else {
      return "HelveticaNeue"
    }
    CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL)
      as? [CTFontDescriptor], let d = descriptors.first,
      let name = CTFontDescriptorCopyAttribute(d, kCTFontNameAttribute) as? String
    {
      return name
    }
    return "Inter-Regular"
  }()
  static var displayUnit: CGFloat {
    min(UIScreen.main.bounds.width / 960, UIScreen.main.bounds.height / 540)
  }
  static func pt(_ value: CGFloat) -> CGFloat { value * displayUnit }
  static func font(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
    .custom(fontName, size: pt(size)).weight(weight)
  }
}
extension Color {
  init(hex: String) {
    let raw = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
    let v = UInt64(raw, radix: 16) ?? 0x333333
    self.init(
      red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255,
      blue: Double(v & 255) / 255)
  }
}
struct RallyCanvas<Content: View>: View {
  let content: Content
  init(@ViewBuilder content: () -> Content) { self.content = content() }
  var body: some View {
    content.frame(width: RallyDesign.pt(960), height: RallyDesign.pt(540))
      .ignoresSafeArea().preferredColorScheme(.dark)
  }
}
/// Keep video geometry stable; tvOS's plain style lifts/scales the entire stream.
struct RallyFlatButtonStyle: ButtonStyle {
  func makeBody(configuration: Configuration) -> some View { configuration.label }
}
struct RallyBackdrop: View {
  var body: some View {
    GeometryReader { geo in
      ZStack(alignment: .topLeading) {
        RallyDesign.black
        BundleArt.image("rally_tv_background_v8.png").resizable().scaledToFill()
          .frame(width: geo.size.width, height: geo.size.height).clipped().opacity(0.42)
      }
    }.allowsHitTesting(false).ignoresSafeArea()
  }
}
struct RallyButtonStyle: ButtonStyle {
  var primary = false
  var bare = false
  var selected = false
  func makeBody(configuration: Configuration) -> some View {
    RallyButtonSurface(
      label: configuration.label, pressed: configuration.isPressed, primary: primary, bare: bare,
      selected: selected)
  }
}
private struct RallyButtonSurface<Label: View>: View {
  let label: Label
  let pressed: Bool
  let primary: Bool
  let bare: Bool
  let selected: Bool
  @Environment(\.isFocused) private var focused
  @Environment(RallyStore.self) private var store
  var body: some View {
    label.font(RallyDesign.font(12, .medium)).foregroundStyle(primary ? .black : .white)
      .padding(.horizontal, RallyDesign.pt(bare ? 4 : 14)).padding(
        .vertical, RallyDesign.pt(bare ? 3 : 10)
      )
      .background {
        if primary {
          RoundedRectangle(cornerRadius: RallyDesign.pt(8)).fill(.white.opacity(focused ? 1 : 0.94))
        } else if !bare || focused || selected {
          RoundedRectangle(cornerRadius: RallyDesign.pt(8)).fill(
            focused
              ? .white.opacity(0.12)
              : selected ? .white.opacity(0.08) : RallyDesign.surface.opacity(0.8))
        }
      }
      .overlay {
        if focused {
          RoundedRectangle(cornerRadius: RallyDesign.pt(8)).stroke(
            .white.opacity(store.settings.highContrastFocus ? 1 : 0.45), lineWidth: 0.8)
        }
      }
      .scaleEffect(pressed ? 0.99 : (focused ? 1.035 : 1))
      .shadow(
        color: .black.opacity(focused ? 0.25 : 0), radius: RallyDesign.pt(8), y: RallyDesign.pt(3)
      )
      .animation(
        store.settings.reducedMotion ? nil : .easeOut(duration: 0.16), value: focused)
  }
}
struct RallyAction: View {
  let title: String
  var icon: String?
  var primary = false
  var bare = false
  var action: () -> Void
  var trailingIcon = false
  var body: some View {
    Button(action: action) {
      HStack(spacing: RallyDesign.pt(8)) {
        if let icon, !trailingIcon { Image(systemName: icon) }
        Text(title)
        if let icon, trailingIcon { Image(systemName: icon) }
      }
    }
    .buttonStyle(RallyButtonStyle(primary: primary, bare: bare)).focusEffectDisabled()
    .accessibilityIdentifier(title)
  }
}
struct RallySectionHeader: View {
  let title: String
  var actionTitle: String?
  var action: (() -> Void)?
  var body: some View {
    HStack {
      Text(title.uppercased()).font(RallyDesign.font(11, .regular)).tracking(RallyDesign.pt(1.4))
        .foregroundStyle(RallyDesign.muted)
        .padding(
          .leading, RallyDesign.pt(10))
      Spacer()
      if let actionTitle, let action {
        Button(action: action) {
          HStack(spacing: RallyDesign.pt(7)) {
            Text(actionTitle.uppercased()).font(RallyDesign.font(11)).tracking(RallyDesign.pt(1.4))
              .foregroundStyle(RallyDesign.muted)
            Image(systemName: "chevron.right").font(RallyDesign.font(10))
          }
        }.buttonStyle(RallyButtonStyle(bare: true)).focusEffectDisabled().accessibilityIdentifier(
          actionTitle.uppercased())
      }
    }.frame(height: RallyDesign.pt(22))
  }
}
struct RallyPanel<Content: View>: View {
  @Environment(\.isFocused) private var focused
  let title: String
  let content: Content
  var compact: Bool
  var minimumHeight: CGFloat?
  init(
    _ title: String, compact: Bool = false, minimumHeight: CGFloat? = nil,
    @ViewBuilder content: () -> Content
  ) {
    self.title = title
    self.compact = compact
    self.minimumHeight = minimumHeight
    self.content = content()
  }
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(compact ? 4 : 10)) {
      if !title.isEmpty { Text(title).font(RallyDesign.font(compact ? 11 : 13, .semibold)) }
      content
    }.frame(minHeight: minimumHeight.map(RallyDesign.pt), alignment: .topLeading)
      .padding(RallyDesign.pt(compact ? 8 : 12)).frame(maxWidth: .infinity, alignment: .topLeading)
      .background(
        RallyDesign.surface.opacity(focused ? 0.95 : 0.58),
        in: RoundedRectangle(cornerRadius: RallyDesign.pt(8))
      )
      .overlay(
        RoundedRectangle(cornerRadius: RallyDesign.pt(8)).stroke(
          focused ? .white.opacity(0.4) : RallyDesign.edge, lineWidth: 0.6))
  }
}
struct RallyEmptyState: View {
  let title: String
  let message: String
  var actionTitle: String?
  var action: (() -> Void)?
  var body: some View {
    VStack(alignment: .leading, spacing: RallyDesign.pt(12)) {
      Text(title).font(RallyDesign.font(20, .semibold))
      Text(message).font(RallyDesign.font(13)).foregroundStyle(RallyDesign.muted)
      if let actionTitle, let action { RallyAction(title: actionTitle, action: action) }
    }.frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, RallyDesign.pt(16))
  }
}
struct RallyLoading: View {
  var title = "Loading Rally…"
  var body: some View {
    HStack(spacing: RallyDesign.pt(14)) {
      ProgressView()
      Text(title).font(RallyDesign.font(14))
    }.frame(maxWidth: .infinity, maxHeight: .infinity)
  }
}
struct RallyRemoteImage: View {
  let url: URL?
  var fit = false
  @State private var image: UIImage?
  var body: some View {
    GeometryReader { geo in
      Group {
        if let image {
          Image(uiImage: image).resizable().aspectRatio(contentMode: fit ? .fit : .fill)
        } else {
          Color.clear
        }
      }
      .frame(width: geo.size.width, height: geo.size.height).clipped()
    }.task(id: url) {
      image = nil
      if let url { image = await RallyImageCache.shared.load(url) }
    }
  }
}
actor RallyImageCache {
  static let shared = RallyImageCache()
  private let cache = NSCache<NSURL, UIImage>()
  private let http = HTTPClient()
  private var pending: [URL: Task<UIImage?, Never>] = [:]
  func load(_ url: URL) async -> UIImage? {
    guard NetworkPolicy.shared.permits(url) else { return nil }
    if let image = cache.object(forKey: url as NSURL) { return image }
    if let task = pending[url] { return await task.value }
    let task = Task<UIImage?, Never> {
      guard let (data, response) = try? await http.data(url: url),
        (200..<300).contains(response.statusCode),
        let source = CGImageSourceCreateWithData(data as CFData, nil),
        let image = CGImageSourceCreateThumbnailAtIndex(
          source, 0,
          [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: 1600,
            kCGImageSourceCreateThumbnailWithTransform: true,
          ] as CFDictionary)
      else { return nil }
      return UIImage(cgImage: image)
    }
    pending[url] = task
    let image = await task.value
    pending[url] = nil
    if let image {
      cache.setObject(
        image, forKey: url as NSURL, cost: Int(image.size.width * image.size.height * 4))
      cache.totalCostLimit = 80 * 1024 * 1024
    }
    return image
  }
}
struct RallyTeamLogo: View {
  let team: Team?
  var size: CGFloat = 34
  var body: some View {
    ZStack {
      if team?.logoUrl != nil {
        RallyRemoteImage(url: team?.logoUrl, fit: true)
      } else {
        Text(team?.abbreviation ?? "—").font(RallyDesign.font(size / 3, .bold))
      }
    }.frame(width: RallyDesign.pt(size), height: RallyDesign.pt(size))
  }
}
struct RallyLeagueMark: View {
  let league: String
  var size: CGFloat = 30
  private var asset: String? {
    [
      "NFL": "nfl", "NBA": "nba", "MLB": "mlb", "NHL": "nhl", "MLS": "mls", "EPL": "epl",
      "Champions League": "ucl", "La Liga": "laliga", "Serie A": "seriea",
    ][league].map { "league_mark_\($0).png" }
  }
  var body: some View {
    Group {
      if let asset {
        BundleArt.image(asset).resizable().scaledToFit()
      } else if league == "NCAAF" || league == "NCAAB" {
        ZStack {
          Circle().fill(Color(hex: "005EB8"))
          Text("NCAA").italic().font(RallyDesign.font(8, .bold))
        }
      } else if league == "UFC" {
        Text("UFC").italic().font(RallyDesign.font(18, .bold)).minimumScaleFactor(0.5)
      } else {
        Image(
          systemName: league == "Tennis"
            ? "tennisball.fill" : league == "UFC" ? "figure.boxing" : "soccerball"
        ).resizable().scaledToFit().foregroundStyle(
          league == "Tennis" ? Color(hex: "B7D36D") : .white)
      }
    }.frame(width: RallyDesign.pt(size), height: RallyDesign.pt(size))
  }
}
extension SportEvent {
  var matchup: String {
    if let awayTeam, let homeTeam { return "\(awayTeam.name) vs \(homeTeam.name)" }
    return name
  }
  var compactMatchup: String {
    func name(_ t: Team?) -> String {
      guard let t else { return "" }
      if sport == "soccer" { return t.name }
      return t.shortName ?? t.name
    }
    return [name(awayTeam), name(homeTeam)].filter { !$0.isEmpty }.joined(separator: " vs ")
  }
  var scoreLine: String {
    "\(awayTeam?.abbreviation ?? "AWAY") \(scoreAway.map(String.init) ?? "—") — \(homeTeam?.abbreviation ?? "HOME") \(scoreHome.map(String.init) ?? "—")"
  }
  var statusLabel: String {
    switch status {
    case .live: return "LIVE"
    case .halftime: return "HALFTIME"
    case .finished: return "FINAL"
    case .notStarted: return startTime.formatted(date: .omitted, time: .shortened)
    case .delayed: return "DELAYED"
    case .canceled: return "CANCELED"
    }
  }
  var metadata: String { "\(league) · \(gameStatusDetail ?? statusLabel)" }
}
