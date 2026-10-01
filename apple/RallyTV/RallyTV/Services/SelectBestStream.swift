import Foundation

/// Merges IPTV + Stremio options into a ranked selection.
/// Ports `SelectBestStreamUseCase` ranking (preflight/health wiring lands with Player).
public struct SelectBestStream: Sendable {
  private let matcher: any MatcherService

  public init(matcher: any MatcherService) {
    self.matcher = matcher
  }

  public func select(
    event: SportEvent,
    channels: [IptvChannel],
    stremioStreams: [StremioStreamOption]
  ) async -> StreamSelection {
    async let relevant = matcher.relevantChannels(for: event, channels: channels)
    let stremioCandidates =
      stremioStreams
      .filter(\.isDirectPlayable)

      .map { option in
        let quality = QualityParsing.parseQuality(
          fromChannelName: [
            option.title, option.description ?? "", option.quality ?? "", option.bitrate ?? "",
          ].joined(separator: " ")
        )
        return StreamCandidate(
          id: "stremio:\(option.streamUrl.absoluteString)",
          playbackTarget: option.streamUrl,
          title: option.title,
          sourceKind: .stremio,
          quality: quality,
          qualityRank: QualityParsing.qualityRank(quality),
          exactGameMatch: true,
          matchConfidence: 0.98,
          matchEvidence: "Exact event match",
          headers: option.headers,
          stremioStream: option
        )
      }
    let relevantChannels = await relevant
    let iptvCandidates: [StreamCandidate] = relevantChannels.compactMap { relevant in
      guard
        let target = relevant.channel.streamUrl
          ?? URL(
            string: "rally-channel://play/" + relevant.channel.id.addingPercentEncoding(
              withAllowedCharacters: .urlPathAllowed)!)
      else { return nil }
      let quality = QualityParsing.parseQuality(fromChannelName: relevant.channel.name)
      return StreamCandidate(
        id: "iptv:\(relevant.channel.id)",
        playbackTarget: target,
        title: relevant.channel.name,
        sourceKind: .iptv,
        quality: quality,
        qualityRank: QualityParsing.qualityRank(quality),
        exactGameMatch: relevant.likelihoodScore >= 0.5,
        matchConfidence: relevant.likelihoodScore,
        matchEvidence: relevant.matchBadge ?? "Channel match",
        headers: relevant.channel.streamHeaders,
        channel: relevant.channel
      )
    }
    let candidates = (stremioCandidates + iptvCandidates)
      .sorted { a, b in
        if a.exactGameMatch != b.exactGameMatch { return a.exactGameMatch && !b.exactGameMatch }
        if a.qualityRank != b.qualityRank { return a.qualityRank > b.qualityRank }
        return a.matchConfidence > b.matchConfidence
      }
    return StreamSelection(
      primary: candidates.first(where: \.exactGameMatch) ?? candidates.first,
      candidates: candidates,
      relevantChannels: relevantChannels,
      stremioStreams: stremioStreams
    )
  }

  public static func trace(candidates: [StreamCandidate], selectedId: String? = nil) -> String {
    guard !candidates.isEmpty else { return "No direct-playable candidates were returned." }
    return candidates.prefix(8).enumerated().map { index, candidate in
      var quality = [candidate.quality.resolution, candidate.quality.fps].compactMap { $0 }.joined(
        separator: " · ")
      if candidate.quality.isHdr { quality += quality.isEmpty ? "HDR" : " · HDR" }
      if quality.isEmpty { quality = "quality unknown" }
      let eligibility: String =
        if !candidate.exactGameMatch {
          "rejected: game not verified"
        } else if candidate.preflightPassed == false {
          "rejected: preflight failed"
        } else if candidate.id == selectedId {
          "selected"
        } else {
          "fallback"
        }
      return
        "\(index + 1). \(candidate.sourceKind) · \(quality) · \(eligibility) · \(candidate.matchEvidence)"
    }.joined(separator: "\n")
  }
}
