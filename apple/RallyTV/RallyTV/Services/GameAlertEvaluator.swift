import Foundation

/// Pure alert derivation. Ports `GameAlertManager.evaluate` without UIKit —
/// tvOS surfaces these as in-app banners.
public enum GameAlertEvaluator {
  public static func evaluate(
    previous: [SportEvent],
    current: [SportEvent],
    favoriteTeamIds: Set<String>
  ) -> [GameAlert] {
    guard !previous.isEmpty else { return [] }
    let oldById = Dictionary(
      previous.map { ($0.id, $0) }, uniquingKeysWith: { _, latest in latest })
    var alerts: [GameAlert] = []
    for event in current {
      guard let old = oldById[event.id] else { continue }
      let favorite =
        favoriteTeamIds.contains("\(event.league):\(event.homeTeam?.id ?? "")")
        || favoriteTeamIds.contains("\(event.league):\(event.awayTeam?.id ?? "")")
      guard favorite else { continue }
      if old.status == .notStarted, event.status.isLive {
        alerts.append(alert(event: event, type: .kickoff, title: "Kickoff", message: event.name))
      }
      if scoreChanged(old: old, event: event) {
        alerts.append(
          alert(event: event, type: .score, title: "Score update", message: scoreLine(event)))
      }
      let detail = event.gameStatusDetail?.lowercased() ?? ""
      let oldDetail = old.gameStatusDetail?.lowercased() ?? ""
      if detail.contains("overtime") || detail.contains(" ot"),
        !(oldDetail.contains("overtime") || oldDetail.contains(" ot"))
      {
        alerts.append(
          alert(event: event, type: .overtime, title: "Overtime", message: scoreLine(event)))
      }
      if isCloseLateGame(event), !isCloseLateGame(old) {
        alerts.append(
          alert(event: event, type: .closeGame, title: "Close game", message: scoreLine(event)))
      }
      if old.status != .finished, event.status == .finished {
        alerts.append(alert(event: event, type: .final, title: "Final", message: scoreLine(event)))
      }
    }
    return alerts
  }

  public static func scoreLine(_ event: SportEvent) -> String {
    "\(event.awayTeam?.abbreviation ?? "Away") \(event.scoreAway.map(String.init) ?? "–") · \(event.homeTeam?.abbreviation ?? "Home") \(event.scoreHome.map(String.init) ?? "–")"
  }

  static func isCloseLateGame(_ event: SportEvent) -> Bool {
    guard let home = event.scoreHome, let away = event.scoreAway else { return false }
    let detail = event.gameStatusDetail?.lowercased() ?? ""
    let late = ["4th", "final minute", "2:00", "1:00", "9th", "3rd period", "90'"].contains {
      detail.contains($0)
    }
    guard late else { return false }
    let margin = abs(home - away)
    let sport = event.sport.lowercased()
    if sport.contains("basket") { return margin <= 5 }
    if sport.contains("base") { return margin <= 1 }
    if sport.contains("hock") || sport.contains("socc") { return margin <= 1 }
    return margin <= 3
  }

  private static func scoreChanged(old: SportEvent, event: SportEvent) -> Bool {
    old.scoreHome != nil && old.scoreAway != nil
      && (old.scoreHome != event.scoreHome || old.scoreAway != event.scoreAway)
  }

  private static func alert(event: SportEvent, type: GameAlertType, title: String, message: String)
    -> GameAlert
  {
    let scoreKey =
      "\(event.scoreAway.map(String.init) ?? "-"):\(event.scoreHome.map(String.init) ?? "-"):\(event.gameStatusDetail ?? "")"
    return GameAlert(
      id: "\(event.id):\(type):\(scoreKey)", eventId: event.id, type: type, title: title,
      message: message)
  }
}
