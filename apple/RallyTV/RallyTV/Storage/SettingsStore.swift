import CryptoKit
import Foundation

/// Non-secret preferences + secret-backed provider credentials.
/// Secrets live in Keychain; everything else in UserDefaults.
/// Mirrors the `PreferencesManager` key surface without the God-object shape —
/// each domain reads through this single type, injected via AppContainer.
public final class SettingsStore: @unchecked Sendable {
  private let defaults: UserDefaults
  private let secrets: KeychainSecrets

  public init(defaults: UserDefaults = .standard, secrets: KeychainSecrets = KeychainSecrets()) {
    self.defaults = defaults
    self.secrets = secrets
  }

  // MARK: Provider

  public var provider: IptvProvider {
    get { IptvProvider(rawValue: defaults.string(forKey: Keys.provider) ?? "") ?? .stalker }
    set { defaults.set(newValue.rawValue, forKey: Keys.provider) }
  }

  public var portalUrl: String {
    get { PortalUrlNormalizer.normalizePortal(defaults.string(forKey: Keys.portalUrl) ?? "") }
    set { defaults.set(newValue, forKey: Keys.portalUrl) }
  }

  public var xtreamServerUrl: String {
    get {
      PortalUrlNormalizer.normalizeXtreamServer(defaults.string(forKey: Keys.xtreamServer) ?? "")
    }
    set { defaults.set(newValue, forKey: Keys.xtreamServer) }
  }

  public var xtreamUsername: String {
    get { defaults.string(forKey: Keys.xtreamUsername) ?? "" }
    set { defaults.set(newValue.trimmingCharacters(in: .whitespaces), forKey: Keys.xtreamUsername) }
  }

  /// Playlist URLs can carry provider tokens; keep them out of defaults and backups.
  public var m3uPlaylistUrl: String {
    get { secrets.string(for: "m3uPlaylistUrl") }
    set {
      secrets.set(newValue.trimmingCharacters(in: .whitespacesAndNewlines), for: "m3uPlaylistUrl")
    }
  }
  public var m3uPlaylistName: String {
    get { defaults.string(forKey: "m3uPlaylistName") ?? "" }
    set {
      defaults.set(
        newValue.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "m3uPlaylistName")
    }
  }

  // MARK: Secrets (Keychain)

  public var xtreamPassword: String {
    get { secrets.string(for: Keys.xtreamPassword) }
    set { secrets.set(newValue, for: Keys.xtreamPassword) }
  }

  public var authToken: String {
    get { secrets.string(for: Keys.authToken) }
    set { secrets.set(newValue, for: Keys.authToken) }
  }

  public var macAddress: String {
    get {
      let saved = secrets.string(for: Keys.macAddress)
      if !saved.isEmpty { return saved }
      let generated = "00:1A:79:\(Self.hex()):\(Self.hex()):\(Self.hex())"
      secrets.set(generated, for: Keys.macAddress)
      return generated
    }
    set { secrets.set(newValue, for: Keys.macAddress) }
  }

  // MARK: Content

  public var stremioAddonUrls: [URL] {
    get {
      guard let raw = defaults.string(forKey: Keys.addons),
        let data = raw.data(using: .utf8),
        let list = try? JSONDecoder().decode([String].self, from: data)
      else { return [] }
      return list.compactMap { PortalUrlNormalizer.normalizeAddon($0) }
    }
    set {
      let strings = newValue.map(\.absoluteString)
      let raw = (try? JSONEncoder().encode(strings)).flatMap { String(data: $0, encoding: .utf8) }
      defaults.set(raw, forKey: Keys.addons)
    }
  }

  public var enabledLeagues: Set<String> {
    get {
      Set(defaults.stringArray(forKey: Keys.enabledLeagues) ?? Array(EspnEndpoints.leagues.keys))
    }
    set { defaults.set(Array(newValue), forKey: Keys.enabledLeagues) }
  }

  /// Canonical league order. Mirrors Android `sportsOrder` defaults.
  public var sportsOrder: [String] {
    get {
      let defaultsOrder = [
        "NFL", "NCAAF", "NBA", "NCAAB", "MLB", "NHL", "MLS", "EPL", "La Liga", "Champions League",
        "Serie A",
      ]
      guard let raw = defaults.string(forKey: Keys.sportsOrder), !raw.isEmpty else {
        return defaultsOrder
      }
      var seen = Set<String>()
      let saved = raw.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter {
        defaultsOrder.contains($0) && seen.insert($0).inserted
      }
      return saved + defaultsOrder.filter { !saved.contains($0) }
    }
    set { defaults.set(newValue.joined(separator: ","), forKey: Keys.sportsOrder) }
  }

  public var favoriteSports: Set<String> {
    get { Set(defaults.stringArray(forKey: Keys.favoriteSports) ?? []) }
    set { defaults.set(Array(newValue), forKey: Keys.favoriteSports) }
  }

  public var favoriteTeamKeys: Set<String> {
    get { Set(defaults.stringArray(forKey: Keys.favoriteTeams) ?? []) }
    set { defaults.set(Array(newValue), forKey: Keys.favoriteTeams) }
  }

  // MARK: Playback toggles (mirror Android defaults)

  public var liveGameAlertsEnabled: Bool {
    get { defaults.object(forKey: Keys.liveAlerts) as? Bool ?? true }
    set { defaults.set(newValue, forKey: Keys.liveAlerts) }
  }

  public var redZoneAlertsEnabled: Bool {
    get { defaults.object(forKey: Keys.redZoneAlerts) as? Bool ?? true }
    set { defaults.set(newValue, forKey: Keys.redZoneAlerts) }
  }

  public var lowLatencyMode: Bool {
    get { defaults.object(forKey: Keys.lowLatency) as? Bool ?? true }
    set { defaults.set(newValue, forKey: Keys.lowLatency) }
  }

  public var audioNormalizationEnabled: Bool {
    get { defaults.object(forKey: Keys.audioNorm) as? Bool ?? true }
    set { defaults.set(newValue, forKey: Keys.audioNorm) }
  }

  public var adaptiveQualityEnabled: Bool {
    get { defaults.object(forKey: Keys.adaptive) as? Bool ?? true }
    set { defaults.set(newValue, forKey: Keys.adaptive) }
  }

  // MARK: Accessibility

  public var reducedMotion: Bool {
    get { defaults.bool(forKey: Keys.reducedMotion) }
    set { defaults.set(newValue, forKey: Keys.reducedMotion) }
  }

  public var highContrastFocus: Bool {
    get { defaults.bool(forKey: Keys.highContrast) }
    set { defaults.set(newValue, forKey: Keys.highContrast) }
  }

  public var largeText: Bool {
    get { defaults.bool(forKey: Keys.largeText) }
    set { defaults.set(newValue, forKey: Keys.largeText) }
  }

  // MARK: Setup

  public var setupComplete: Bool {
    get { defaults.bool(forKey: Keys.setupComplete) }
    set { defaults.set(newValue, forKey: Keys.setupComplete) }
  }

  public var hasCredentials: Bool {
    setupComplete || !portalUrl.isEmpty || (!xtreamServerUrl.isEmpty && !xtreamUsername.isEmpty)
      || !m3uPlaylistUrl.isEmpty || !stremioAddonUrls.isEmpty
  }

  public func clearCredentials() {
    M3uPlaylistFiles.removeOwned(URL(string: m3uPlaylistUrl))
    defaults.removeObject(forKey: "m3uPlaylistName")
    for key in [Keys.provider, Keys.portalUrl, Keys.xtreamServer, Keys.xtreamUsername] {
      defaults.removeObject(forKey: key)
    }
    secrets.clearAll()
  }

  public var supportReport: String {
    get { defaults.string(forKey: "supportReport") ?? "" }
    set { defaults.set(newValue, forKey: "supportReport") }
  }
  public var serialNumber: String {
    get { defaults.string(forKey: "serial_number") ?? "" }
    set { defaults.set(newValue, forKey: "serial_number") }
  }
  public var deviceId: String {
    get { defaults.string(forKey: "device_id") ?? "" }
    set { defaults.set(newValue, forKey: "device_id") }
  }
  public var spokenScoreSummaries: Bool {
    get { defaults.bool(forKey: "spokenScoreSummaries") }
    set { defaults.set(newValue, forKey: "spokenScoreSummaries") }
  }
  public var scoreSaverEnabled: Bool {
    get { defaults.object(forKey: "scoreSaverEnabled") as? Bool ?? true }
    set { defaults.set(newValue, forKey: "scoreSaverEnabled") }
  }
  public var savedEventIds: Set<String> {
    get { Set(defaults.stringArray(forKey: "savedEvents") ?? []) }
    set { defaults.set(Array(newValue), forKey: "savedEvents") }
  }
  public var reminderIds: Set<String> {
    get { Set(defaults.stringArray(forKey: "reminders") ?? []) }
    set { defaults.set(Array(newValue), forKey: "reminders") }
  }
  public var favoritePlayerIds: Set<String> {
    get { Set(defaults.stringArray(forKey: "favoritePlayerIds") ?? []) }
    set { defaults.set(Array(newValue), forKey: "favoritePlayerIds") }
  }
  public var followedTeams: [FavoriteTeam] {
    get {
      defaults.data(forKey: "followedTeamProfiles").flatMap {
        try? JSONDecoder().decode([FavoriteTeam].self, from: $0)
      } ?? []
    }
    set {
      defaults.set(try? JSONEncoder().encode(newValue), forKey: "followedTeamProfiles")
      favoriteTeamKeys = Set(newValue.map(\.key))
    }
  }
  /// Merge profiles without discarding imported team keys whose catalog is temporarily offline.
  public func mergeTeamProfiles(_ profiles: [FavoriteTeam]) {
    let keys = favoriteTeamKeys
    let combined = Dictionary(
      (followedTeams + profiles).map { ($0.key, $0) }, uniquingKeysWith: { _, latest in latest })
    defaults.set(
      try? JSONEncoder().encode(
        combined.values.filter { keys.contains($0.key) }.sorted { $0.key < $1.key }),
      forKey: "followedTeamProfiles")
  }
  public func toggleTeam(_ team: FavoriteTeam) {
    var keys = favoriteTeamKeys
    if !keys.insert(team.key).inserted { keys.remove(team.key) }
    favoriteTeamKeys = keys
    mergeTeamProfiles([team])
  }
  public func toggleEvent(_ id: String) {
    var ids = savedEventIds
    if !ids.insert(id).inserted { ids.remove(id) }
    savedEventIds = ids
  }
  public func toggleReminder(_ id: String) {
    var ids = reminderIds
    if !ids.insert(id).inserted { ids.remove(id) }
    reminderIds = ids
  }
  private static func healthKey(_ target: String) -> String {
    "health:" + SHA256.hash(data: Data(target.utf8)).map { String(format: "%02x", $0) }.joined()
  }
  public func health(_ target: String) -> StreamHealth {
    defaults.data(forKey: Self.healthKey(target)).flatMap {
      try? JSONDecoder().decode(StreamHealth.self, from: $0)
    } ?? StreamHealth()
  }
  public func recordHealth(_ target: String, success: Bool, startup: Int = 0, stalled: Bool = false)
  {
    var value = health(target)
    if stalled {
      value.recordStall()
    } else if success {
      value.recordSuccess(startupMs: startup)
    } else {
      value.recordFailure()
    }
    defaults.set(try? JSONEncoder().encode(value), forKey: Self.healthKey(target))
  }
  public func exportPersonalization() throws -> String {
    let json: [String: Any] = [
      "schema": 1, "createdAt": Int(Date().timeIntervalSince1970 * 1000),
      "enabledLeagues": Array(enabledLeagues).sorted(),
      "favoriteSports": Array(favoriteSports).sorted(),
      "favoriteTeams": Array(favoriteTeamKeys).sorted(), "sportsOrder": sportsOrder,
      "favoritePlayerIds": Array(favoritePlayerIds).sorted(),
      "liveGameAlertsEnabled": liveGameAlertsEnabled, "redZoneAlertsEnabled": redZoneAlertsEnabled,
      "lowLatencyMode": lowLatencyMode, "audioNormalizationEnabled": audioNormalizationEnabled,
      "adaptiveQualityEnabled": adaptiveQualityEnabled, "reducedMotion": reducedMotion,
      "highContrastFocus": highContrastFocus, "largeText": largeText,
      "spokenScoreSummaries": spokenScoreSummaries, "scoreSaverEnabled": scoreSaverEnabled,
    ]
    return String(
      decoding: try JSONSerialization.data(
        withJSONObject: json, options: [.prettyPrinted, .sortedKeys]), as: UTF8.self)
  }
  public func importPersonalization(_ text: String) throws {
    let json = try JSONDecoder().decode(JSONValue.self, from: Data(text.utf8))
    guard json["schema"].int == 1 else { throw CocoaError(.fileReadCorruptFile) }
    let leagues = Set(json["enabledLeagues"].array.compactMap(\.string))
    guard !leagues.isEmpty, leagues.isSubset(of: Set(EspnEndpoints.leagues.keys)) else {
      throw CocoaError(.fileReadCorruptFile)
    }
    enabledLeagues = leagues
    favoriteSports = Set(json["favoriteSports"].array.compactMap(\.string))
    favoriteTeamKeys = Set(json["favoriteTeams"].array.compactMap(\.string))
    let order = json["sportsOrder"].array.compactMap(\.string)
    if !order.isEmpty { sportsOrder = order }
    favoritePlayerIds = Set(json["favoritePlayerIds"].array.compactMap(\.string))
    for key in [
      "liveGameAlertsEnabled", "redZoneAlertsEnabled", "lowLatencyMode",
      "audioNormalizationEnabled", "adaptiveQualityEnabled", "reducedMotion", "highContrastFocus",
      "largeText", "spokenScoreSummaries", "scoreSaverEnabled",
    ] {
      guard json[key] != .null else { continue }
      let mapping = [
        "liveGameAlertsEnabled": Keys.liveAlerts, "redZoneAlertsEnabled": Keys.redZoneAlerts,
        "lowLatencyMode": Keys.lowLatency, "audioNormalizationEnabled": Keys.audioNorm,
        "adaptiveQualityEnabled": Keys.adaptive, "reducedMotion": Keys.reducedMotion,
        "highContrastFocus": Keys.highContrast, "largeText": Keys.largeText,
      ]
      defaults.set(json[key].bool, forKey: mapping[key] ?? key)
    }
  }

  // MARK: -

  private static func hex() -> String {
    String(format: "%02X", Int.random(in: 0...255))
  }

  private enum Keys {
    static let provider = "iptv_provider"
    static let portalUrl = "portal_url"
    static let xtreamServer = "xtream_server_url"
    static let xtreamUsername = "xtream_username"
    static let xtreamPassword = "xtream_password"
    static let authToken = "auth_token"
    static let macAddress = "mac_address"
    static let addons = "stremio_addon_urls_json"
    static let enabledLeagues = "enabled_leagues"
    static let sportsOrder = "sports_order"
    static let favoriteSports = "favorite_sports"
    static let favoriteTeams = "favorite_teams"
    static let liveAlerts = "live_game_alerts_enabled"
    static let redZoneAlerts = "redzone_alerts_enabled"
    static let lowLatency = "low_latency_mode"
    static let audioNorm = "audio_normalization_enabled"
    static let adaptive = "adaptive_quality_enabled"
    static let reducedMotion = "reduced_motion"
    static let highContrast = "high_contrast_focus"
    static let largeText = "large_text"
    static let setupComplete = "setup_complete"
  }
}
