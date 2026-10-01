import Foundation

/// Display-name helpers. Ports `formatLeagueDisplayName` / `formatTeamDisplayName` /
/// `leagueCardMark` from HomeScreen.kt.
public enum RallyFormatting {
  public static func leagueDisplayName(_ league: String?) -> String {
    guard let league, !league.isBlank else { return "Sports" }
    switch league.lowercased().trimmingCharacters(in: .whitespaces) {
    case "all": return "All"
    case "live": return "Live"
    case "epl", "premierleague", "premier league", "eng.1": return "Premier League"
    case "laliga", "la liga", "esp.1": return "La Liga"
    case "mls", "usa.1": return "MLS"
    case "champions", "uefa.champions", "uefa champions league": return "Champions League"
    case "ncaaf": return "College Football"
    case "ncaab": return "College Basketball"
    default: return league
    }
  }

  public static func teamDisplayName(_ rawName: String?) -> String {
    guard let rawName, !rawName.isBlank else { return "TBD" }
    let acronyms: Set<String> = [
      "BYU", "UCLA", "USC", "TCU", "LSU", "SMU", "UCF", "UNLV",
      "UTEP", "UTSA", "NYCFC", "LAFC", "PSG", "FC", "CF", "SC", "AFC",
    ]
    return rawName.split(separator: " ").map { word in
      let upper = word.uppercased()
      if acronyms.contains(upper) { return upper }
      if word.count > 1, word.allSatisfy({ !$0.isLetter || $0.isUppercase }) {
        return word.lowercased().capitalized
      }
      return String(word)
    }.joined(separator: " ")
  }

  /// Text monogram for league marks (matches Android's vector fallback).
  public static func leagueCardMark(_ league: String) -> String {
    switch league.uppercased() {
    case "NCAAF": return "CFB"
    case "NCAAB": return "CBB"
    case "CHAMPIONS LEAGUE": return "UCL"
    case "LA LIGA": return "LIGA"
    case "SERIE A": return "SERIE A"
    default: return String(league.uppercased().prefix(5))
    }
  }

  public static let gameTime: DateFormatter = {
    let f = DateFormatter()
    f.timeStyle = .short
    return f
  }()

  public static let gameDate: DateFormatter = {
    let f = DateFormatter()
    f.dateFormat = "MMM d"
    return f
  }()
}

extension String {
  fileprivate var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}
