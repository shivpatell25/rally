import Foundation

/// Per-target stream health. Ports `PreferencesManager.StreamHealth` scoring.
public struct StreamHealth: Sendable, Hashable, Codable {
  public var successes: Int
  public var failures: Int
  public var stalls: Int
  public var averageStartupMs: Int
  public var lastUpdated: Date

  public init(
    successes: Int = 0, failures: Int = 0, stalls: Int = 0, averageStartupMs: Int = 0,
    lastUpdated: Date = Date()
  ) {
    self.successes = successes
    self.failures = failures
    self.stalls = stalls
    self.averageStartupMs = averageStartupMs
    self.lastUpdated = lastUpdated
  }

  /// Higher is healthier. Mirrors the Android weighting.
  public var score: Int {
    (successes * 24 - failures * 55 - stalls * 12 - averageStartupMs / 750).clamped(to: -240...120)
  }

  public mutating func recordSuccess(startupMs: Int) {
    successes = min(successes + 1, 100)
    averageStartupMs =
      successes == 1 ? startupMs : (averageStartupMs * (successes - 1) + startupMs) / successes
    lastUpdated = Date()
  }

  public mutating func recordFailure() {
    failures = min(failures + 1, 100)
    lastUpdated = Date()
  }

  public mutating func recordStall() {
    stalls = min(stalls + 1, 200)
    lastUpdated = Date()
  }
}

extension Int {
  fileprivate func clamped(to range: ClosedRange<Int>) -> Int {
    Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
  }
}
