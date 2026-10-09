import Foundation

/// Event→channel match. Mirrors Android `MatchResult`.
public struct MatchResult: Sendable, Hashable {
  public let sportEvent: SportEvent
  public let iptvChannel: IptvChannel
  /// 0.0 … 1.0
  public let confidenceScore: Float

  public init(sportEvent: SportEvent, iptvChannel: IptvChannel, confidenceScore: Float) {
    self.sportEvent = sportEvent
    self.iptvChannel = iptvChannel
    self.confidenceScore = confidenceScore
  }
}

/// Stremio add-on stream. Mirrors Android `StremioStreamOption`.
public struct StremioStreamOption: Sendable, Hashable, Codable, Identifiable {
  public var id: String { streamUrl.absoluteString }
  public let title: String
  public let description: String?
  public let streamUrl: URL
  public let quality: String?
  public let bitrate: String?
  public let addonName: String?
  public let headers: [String: String]?
  /// False when the add-on returned an HTML watch page rather than media.
  public let isDirectPlayable: Bool

  public init(
    title: String, description: String? = nil, streamUrl: URL,
    quality: String? = nil, bitrate: String? = nil, addonName: String? = nil,
    headers: [String: String]? = nil, isDirectPlayable: Bool = true
  ) {
    self.title = title
    self.description = description
    self.streamUrl = streamUrl
    self.quality = quality
    self.bitrate = bitrate
    self.addonName = addonName
    self.headers = headers
    self.isDirectPlayable = isDirectPlayable
  }
}

public enum StreamSourceKind: String, Sendable, Hashable, Codable {
  case stremio
  case iptv
  case direct
}

public struct StreamQualityInfo: Sendable, Hashable, Codable {
  public let resolution: String?
  public let fps: String?
  public let is4K: Bool
  public let is60Fps: Bool
  public let isHdr: Bool

  public init(
    resolution: String? = nil, fps: String? = nil, is4K: Bool = false, is60Fps: Bool = false,
    isHdr: Bool = false
  ) {
    self.resolution = resolution
    self.fps = fps
    self.is4K = is4K
    self.is60Fps = is60Fps
    self.isHdr = isHdr
  }
}

/// Ranked playback option. Mirrors Android `StreamCandidate`.
public struct StreamCandidate: Sendable, Hashable, Identifiable {
  public let id: String
  public let playbackTarget: URL
  public let title: String
  public let sourceKind: StreamSourceKind
  public let quality: StreamQualityInfo
  public let qualityRank: Int
  public let exactGameMatch: Bool
  public let matchConfidence: Float
  public let matchEvidence: String
  public let headers: [String: String]?
  public let channel: IptvChannel?
  public let stremioStream: StremioStreamOption?
  public let preflightPassed: Bool?
  public let preflightLatencyMs: Int?
  public let preflightContentType: String?

  public init(
    id: String, playbackTarget: URL, title: String, sourceKind: StreamSourceKind,
    quality: StreamQualityInfo, qualityRank: Int,
    exactGameMatch: Bool, matchConfidence: Float, matchEvidence: String,
    headers: [String: String]? = nil, channel: IptvChannel? = nil,
    stremioStream: StremioStreamOption? = nil,
    preflightPassed: Bool? = nil, preflightLatencyMs: Int? = nil,
    preflightContentType: String? = nil
  ) {
    self.id = id
    self.playbackTarget = playbackTarget
    self.title = title
    self.sourceKind = sourceKind
    self.quality = quality
    self.qualityRank = qualityRank
    self.exactGameMatch = exactGameMatch
    self.matchConfidence = matchConfidence
    self.matchEvidence = matchEvidence
    self.headers = headers
    self.channel = channel
    self.stremioStream = stremioStream
    self.preflightPassed = preflightPassed
    self.preflightLatencyMs = preflightLatencyMs
    self.preflightContentType = preflightContentType
  }
  public init(addon: StremioStreamOption) {
    let parsedQuality = QualityParsing.parseQuality(
      fromChannelName: [addon.quality, addon.title].compactMap { $0 }.joined(separator: " "))
    self.init(
      id: addon.id, playbackTarget: addon.streamUrl, title: addon.title,
      sourceKind: .stremio,
      quality: StreamQualityInfo(
        resolution: addon.quality ?? parsedQuality.resolution,
        fps: parsedQuality.fps,
        is4K: parsedQuality.is4K,
        is60Fps: parsedQuality.is60Fps,
        isHdr: parsedQuality.isHdr),
      qualityRank: 0, exactGameMatch: false, matchConfidence: 0,
      matchEvidence: addon.addonName ?? "Addon", headers: addon.headers, stremioStream: addon)
  }
  public init(channel: IptvChannel) {
    let quality = QualityParsing.parseQuality(fromChannelName: channel.name)
    self.init(
      id: "iptv:" + channel.id,
      playbackTarget: channel.streamUrl ?? URL(
        string: "rally-channel://play/" + channel.id.addingPercentEncoding(
          withAllowedCharacters: .urlPathAllowed)!)!,
      title: channel.name, sourceKind: .iptv, quality: quality,
      qualityRank: QualityParsing.qualityRank(quality),
      exactGameMatch: false, matchConfidence: 0, matchEvidence: channel.category,
      headers: channel.streamHeaders, channel: channel)
  }
  public static func direct(url: URL, title: String, headers: [String: String]) -> StreamCandidate {
    StreamCandidate(
      id: url.absoluteString, playbackTarget: url, title: title, sourceKind: .direct,
      quality: StreamQualityInfo(), qualityRank: 0, exactGameMatch: false, matchConfidence: 0,
      matchEvidence: "Current stream", headers: headers)
  }
}

/// Full selection set. Mirrors Android `StreamSelection`.
public struct StreamSelection: Sendable, Hashable {
  public let primary: StreamCandidate?
  public let candidates: [StreamCandidate]
  public let relevantChannels: [RelevantChannel]
  public let stremioStreams: [StremioStreamOption]

  public init(
    primary: StreamCandidate? = nil, candidates: [StreamCandidate] = [],
    relevantChannels: [RelevantChannel] = [], stremioStreams: [StremioStreamOption] = []
  ) {
    self.primary = primary
    self.candidates = candidates
    self.relevantChannels = relevantChannels
    self.stremioStreams = stremioStreams
  }
}

public struct RelevantChannel: Sendable, Hashable {
  public let channel: IptvChannel
  public let likelihoodScore: Float
  public let matchBadge: String?
  public let isOfficialBroadcast: Bool

  public init(
    channel: IptvChannel, likelihoodScore: Float, matchBadge: String? = nil,
    isOfficialBroadcast: Bool = false
  ) {
    self.channel = channel
    self.likelihoodScore = likelihoodScore
    self.matchBadge = matchBadge
    self.isOfficialBroadcast = isOfficialBroadcast
  }
}

public struct BroadcastQualityInfo: Sendable, Hashable {
  public let badgeText: String
  public let fullLabel: String
  public let network: String?
  public let is4K: Bool
  public let isHdr: Bool
  public let is1080p: Bool
  public let evidenceSource: String

  public init(
    badgeText: String, fullLabel: String, network: String? = nil, is4K: Bool = false,
    isHdr: Bool = false, is1080p: Bool = false, evidenceSource: String = "Official broadcaster"
  ) {
    self.badgeText = badgeText
    self.fullLabel = fullLabel
    self.network = network
    self.is4K = is4K
    self.isHdr = isHdr
    self.is1080p = is1080p
    self.evidenceSource = evidenceSource
  }
}

public enum MultiViewLayoutMode: Sendable, Hashable, Codable {
  case auto
  case dualSplit
  case dualFocus
  case tripleFocus
  case tripleColumns
  case quadGrid
  case quadFocus
}

public struct MultiViewSlot: Sendable, Hashable, Identifiable {
  public let id: String
  public let event: SportEvent?
  public let channel: IptvChannel?
  public let streamUrl: URL?
  public let streamHeaders: [String: String]?
  public let title: String
  public let subtitle: String?
  public let scoreText: String?
  public let statusText: String?
  public let resolution: String?
  public let fps: String?
  public let selectedSourceId: String?
  public let sourcePlaybackTarget: URL?
  public let sourceTitle: String?
  public let sourceQuality: String?
  public let playbackRevision: Int
  public let isLoading: Bool
  public let error: String?

  public init(
    id: String = UUID().uuidString, event: SportEvent? = nil, channel: IptvChannel? = nil,
    streamUrl: URL? = nil, streamHeaders: [String: String]? = nil,
    title: String = "", subtitle: String? = nil, scoreText: String? = nil,
    statusText: String? = nil, resolution: String? = nil, fps: String? = nil,
    selectedSourceId: String? = nil, sourcePlaybackTarget: URL? = nil,
    sourceTitle: String? = nil, sourceQuality: String? = nil,
    playbackRevision: Int = 0, isLoading: Bool = false, error: String? = nil
  ) {
    self.id = id
    self.event = event
    self.channel = channel
    self.streamUrl = streamUrl
    self.streamHeaders = streamHeaders
    self.title = title
    self.subtitle = subtitle
    self.scoreText = scoreText
    self.statusText = statusText
    self.resolution = resolution
    self.fps = fps
    self.selectedSourceId = selectedSourceId
    self.sourcePlaybackTarget = sourcePlaybackTarget
    self.sourceTitle = sourceTitle
    self.sourceQuality = sourceQuality
    self.playbackRevision = playbackRevision
    self.isLoading = isLoading
    self.error = error
  }
}
