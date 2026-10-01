import Foundation
import os

/// Central logger. Replaces Android `RallyDiagnostics` file log for the foundation slice.
/// MetricKit crash reporting attaches later without changing call sites.
public enum RallyLogger {
  private static let subsystem = Bundle.main.bundleIdentifier ?? "com.shiv.rally.tv"
  public static let net = Logger(subsystem: subsystem, category: "network")
  public static let espn = Logger(subsystem: subsystem, category: "espn")
  public static let iptv = Logger(subsystem: subsystem, category: "iptv")
  public static let stremio = Logger(subsystem: subsystem, category: "stremio")
  public static let playback = Logger(subsystem: subsystem, category: "playback")
  public static let cache = Logger(subsystem: subsystem, category: "cache")
}
