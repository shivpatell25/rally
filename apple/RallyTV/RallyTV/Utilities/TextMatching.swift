import Foundation

/// Ports Android `textMatchesEvent` / `normalizeMatchText` from SelectBestStreamUseCase.
public enum TextMatching {
  public static func textMatchesEvent(_ text: String, event: SportEvent) -> Bool {
    guard !text.isBlank else { return false }
    guard let home = event.homeTeam, let away = event.awayTeam else { return false }
    let normalized = normalize(text)
    return teamMatches(normalized, teamName: home.name, abbreviation: home.abbreviation)
      && teamMatches(normalized, teamName: away.name, abbreviation: away.abbreviation)
  }

  public static func normalize(_ value: String) -> String {
    value.lowercased()
      .replacingOccurrences(of: "[^a-z0-9]+", with: " ", options: .regularExpression)
      .trimmingCharacters(in: .whitespaces)
  }

  private static func teamMatches(_ normalizedText: String, teamName: String, abbreviation: String)
    -> Bool
  {
    let normalizedName = normalize(teamName)
    let nickname = normalizedName.split(separator: " ").last.map(String.init) ?? ""
    let city = normalizedName.split(separator: " ").dropLast().joined(separator: " ")
    let abbr = normalize(abbreviation)
    return (normalizedName.count > 3 && normalizedText.contains(normalizedName))
      || (nickname.count > 3 && containsWord(normalizedText, nickname))
      || (city.count > 4 && normalizedText.contains(city))
      || (abbr.count >= 3 && containsWord(normalizedText, abbr))
  }

  private static func containsWord(_ text: String, _ value: String) -> Bool {
    text.split(separator: " ").contains { $0 == value }
  }
}

extension String {
  fileprivate var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}
