import Observation
import SwiftUI

@MainActor @Observable final class RallyStore {
  let container: AppContainer
  var homeReset = 0
  var settingsRevision = 0
  var lastInteraction = Date()
  var isPlaying = false
  var alert: GameAlert?
  var errorMessage: String?
  var previousEvents: [SportEvent] = []
  private var delivered = Set<String>()
  var alertChannelId: String?
  private var redZoneChecked = Date.distantPast
  func checkRedZone() async {
    guard container.settings.redZoneAlertsEnabled, Date().timeIntervalSince(redZoneChecked) > 300
    else { return }
    redZoneChecked = Date()
    let channels = (try? await container.iptv.searchChannels(query: "redzone", limit: 20)) ?? []
    guard
      let channel = channels.first(where: {
        TextMatching.normalize($0.name).replacingOccurrences(of: " ", with: "").contains("redzone")
      }),
      let title = await container.iptv.guide(forChannelId: channel.id)?.now?.title,
      ["redzone", "red zone", "live"].contains(where: { title.localizedCaseInsensitiveContains($0) }
      )
    else { return }
    let id = "redzone:\(channel.id):\(title)"
    guard delivered.insert(id).inserted else { return }
    alertChannelId = channel.id
    alert = GameAlert(
      id: id, eventId: nil, type: .redZone, title: "NFL RedZone is live", message: title)
  }
  init(container: AppContainer) { self.container = container }
  func interacted() { lastInteraction = Date() }
  func applySettings() {
    container.applyProvider()
    settingsRevision += 1
  }
  func follow(_ team: FavoriteTeam) {
    container.settings.toggleTeam(team)
    settingsRevision += 1
  }
  func save(_ id: String) {
    container.settings.toggleEvent(id)
    settingsRevision += 1
  }
  func reminder(_ id: String) {
    container.settings.toggleReminder(id)
    settingsRevision += 1
  }
  func evaluate(_ events: [SportEvent]) {
    let ids = container.settings.favoriteTeamKeys
    let alerts = GameAlertEvaluator.evaluate(
      previous: previousEvents, current: events, favoriteTeamIds: ids)
    previousEvents = events
    if container.settings.liveGameAlertsEnabled,
      let next = alerts.first(where: { delivered.insert($0.id).inserted })
    {
      alertChannelId = nil
      alert = next
    }
    for e in events where container.settings.reminderIds.contains(e.id) && e.status.isLive {
      let id = "reminder:\(e.id)"
      if delivered.insert(id).inserted {
        alert = GameAlert(
          id: id, eventId: e.id, type: .kickoff, title: "Starting now", message: e.name)
      }
    }
  }
}
