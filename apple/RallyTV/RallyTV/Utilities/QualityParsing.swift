import Foundation

/// Ports Android `parseQualityFromChannelName`, `resolveMaxBroadcastQuality`, `qualityRank`.
public enum QualityParsing {
  public static func parseQuality(fromChannelName name: String) -> StreamQualityInfo {
    let upper = name.uppercased()
    let resolution: String? =
      if upper.contains("4K") || upper.contains("UHD") || upper.contains("2160P") {
        "4K"
      } else if upper.contains("1080P") || upper.contains("1080I") || upper.contains("FHD") {
        "1080p"
      } else if upper.contains("720P") {
        "720p"
      } else if upper.contains(" HD") || upper.hasSuffix("HD") || upper.contains("| HD")
        || upper.contains(": HD")
      {
        "HD"
      } else {
        nil
      }
    let fps: String? =
      if upper.contains("60FPS") || upper.contains("60 FPS") || upper.contains(" 60P")
        || upper.contains(" 60 ") || upper.hasSuffix(" 60")
      {
        "60 fps"
      } else if upper.contains("50FPS") || upper.contains("50 FPS") || upper.contains(" 50P")
        || upper.contains(" 50 ") || upper.hasSuffix(" 50")
      {
        "50 fps"
      } else if upper.contains("30FPS") || upper.contains("30 FPS") {
        "30 fps"
      } else if upper.contains("25FPS") || upper.contains("25 FPS") {
        "25 fps"
      } else {
        nil
      }
    let isHdr =
      upper.contains("HDR") || upper.contains("HLG")
      || upper.contains("DOLBY VISION") || upper.contains(" DV")
      || (upper.contains("DV") && upper.contains("HDR"))
    return StreamQualityInfo(
      resolution: resolution,
      fps: fps,
      is4K: resolution == "4K",
      is60Fps: fps == "60 fps",
      isHdr: isHdr
    )
  }

  public static func qualityRank(_ quality: StreamQualityInfo) -> Int {
    let resolution: Int =
      if quality.is4K {
        700
      } else if quality.resolution?.localizedCaseInsensitiveContains("1080") == true {
        500
      } else if quality.resolution?.localizedCaseInsensitiveContains("720") == true
        || quality.resolution == "HD"
      {
        300
      } else {
        100
      }
    return resolution + (quality.isHdr ? 60 : 0) + (quality.is60Fps ? 30 : 0)
  }

  /// Maximum verified quality badge. Provider channel names are deliberately excluded —
  /// only official broadcaster metadata and direct Stremio metadata count.
  public static func resolveMaxBroadcastQuality(
    event: SportEvent,
    broadcastStations: [String] = [],
    relevantChannels: [RelevantChannel] = [],
    stremioStreams: [StremioStreamOption] = []
  ) -> BroadcastQualityInfo {
    _ = relevantChannels
    let rawStations = (broadcastStations + [event.liveStats["TV Broadcast"]].compactMap { $0 })
      .flatMap {
        $0.split(whereSeparator: { ",/&+".contains($0) }).map {
          $0.trimmingCharacters(in: .whitespaces)
        }
      }
      .filter { !$0.isEmpty }
    let primaryNetwork = rawStations.first

    let officialEvidence =
      (rawStations
      + [
        event.liveStats["TV Broadcast"],
        event.liveStats["Broadcast Quality"],
        event.liveStats["Video Format"],
        event.eventContextTitle,
      ].compactMap { $0 }).joined(separator: " ").uppercased()
    let stremioEvidence =
      stremioStreams
      .filter(\.isDirectPlayable)
      .flatMap { [$0.title, $0.description ?? "", $0.quality ?? "", $0.bitrate ?? ""] }
      .joined(separator: " ").uppercased()

    let official4k =
      matches4K(officialEvidence) || espn4K(event: event, stations: rawStations)
      || nbc4K(event: event, stations: rawStations)
    let officialHdr =
      matchesHdr(officialEvidence) || espn4K(event: event, stations: rawStations)
      || nbc4K(event: event, stations: rawStations)
    let official1080 = matches1080(officialEvidence)
    let stremio4k = matches4K(stremioEvidence)
    let stremioHdr = matchesHdr(stremioEvidence)
    let stremio1080 = matches1080(stremioEvidence)

    let is4k = official4k || stremio4k
    let isHdr = officialHdr || stremioHdr
    let is1080p = !is4k && (official1080 || stremio1080)
    let evidenceSource: String =
      if (stremio4k && !official4k) || (stremioHdr && !officialHdr)
        || (stremio1080 && !official1080)
      {
        "Verified stream metadata"
      } else {
        "Official broadcaster"
      }
    let label: String =
      if is4k && isHdr {
        "4K HDR"
      } else if is4k {
        "4K UHD"
      } else if is1080p && isHdr {
        "1080p HDR"
      } else if is1080p {
        "1080p"
      } else {
        "HD"
      }
    let fullLabel =
      if let primaryNetwork {
        "\(label) · \(primaryNetwork) · \(evidenceSource)"
      } else {
        "\(label) · \(evidenceSource)"
      }
    return BroadcastQualityInfo(
      badgeText: label, fullLabel: fullLabel, network: primaryNetwork,
      is4K: is4k, isHdr: isHdr, is1080p: is1080p, evidenceSource: evidenceSource
    )
  }

  private static func matches4K(_ s: String) -> Bool {
    s.range(of: #"\b(4K|UHD|2160P?)\b"#, options: .regularExpression) != nil
  }
  private static func matchesHdr(_ s: String) -> Bool {
    s.range(of: #"\b(HDR10\+?|HDR|HLG|DOLBY\s+VISION)\b"#, options: .regularExpression) != nil
  }
  private static func matches1080(_ s: String) -> Bool {
    s.range(of: #"\b(1080P?|FHD)\b"#, options: .regularExpression) != nil
  }
  private static func espn4K(event: SportEvent, stations: [String]) -> Bool {
    let espn = stations.contains {
      $0.caseInsensitiveCompare("ESPN") == .orderedSame
        || $0.caseInsensitiveCompare("ESPN2") == .orderedSame
    }
    let league = event.league.uppercased()
    return espn && (league.contains("NFL") || league.contains("NBA") || league.contains("NHL"))
  }
  private static func nbc4K(event: SportEvent, stations: [String]) -> Bool {
    let nbc = stations.contains {
      $0.caseInsensitiveCompare("NBC") == .orderedSame
        || $0.caseInsensitiveCompare("PEACOCK") == .orderedSame
    }
    let identity = "\(event.name) \(event.eventContextTitle ?? "")".uppercased()
    return nbc
      && (identity.contains("SUPER BOWL LX") || identity.contains("MILAN CORTINA")
        || identity.contains("WINTER OLYMPIC"))
  }
}
