import AVFoundation
import Foundation
import UIKit

/// Device capability tiers. Ports Android `choosePlaybackProfile` / `TvDeviceClass`.
public struct PlaybackProfile: Sendable, Hashable {
  public var maxWidth: Int
  public var maxHeight: Int
  public var maxBitrate: Int
  public var maxFrameRate: Int
  public var supportsHDR: Bool
  public var supportsHardware4K: Bool
  public var deviceClass: TVDeviceClass
  public var multiViewMaxTiles: Int

  public init(
    maxWidth: Int, maxHeight: Int, maxBitrate: Int, maxFrameRate: Int,
    supportsHDR: Bool, supportsHardware4K: Bool,
    deviceClass: TVDeviceClass, multiViewMaxTiles: Int = 4
  ) {
    self.maxWidth = maxWidth
    self.maxHeight = maxHeight
    self.maxBitrate = maxBitrate
    self.maxFrameRate = maxFrameRate
    self.supportsHDR = supportsHDR
    self.supportsHardware4K = supportsHardware4K
    self.deviceClass = deviceClass
    self.multiViewMaxTiles = multiViewMaxTiles
  }
}

public enum TVDeviceClass: String, Sendable, Hashable {
  case lowPower
  case standard
  case premium
}

public enum PlaybackProfiles {
  /// Resolves a profile from the display gamut. Wide-gamut (P3) Apple TV
  /// output implies the 4K HDR pipeline; conservative otherwise.
  /// Mirrors Android's gate: 4K only with capable display + headroom.
  public static func current() -> PlaybackProfile {
    let supportsHDR = currentDisplaySupportsHDR()
    return choose(supportsHDR: supportsHDR, lowMemory: false)
  }

  private static func currentDisplaySupportsHDR() -> Bool {
    UIScreen.main.traitCollection.displayGamut == .P3
  }

  public static func choose(supportsHDR: Bool, lowMemory: Bool) -> PlaybackProfile {
    let allow4K = supportsHDR && !lowMemory
    let deviceClass: TVDeviceClass = lowMemory ? .lowPower : (allow4K ? .premium : .standard)
    return PlaybackProfile(
      maxWidth: allow4K ? 3840 : 1920,
      maxHeight: allow4K ? 2160 : 1080,
      maxBitrate: allow4K ? 35_000_000 : 15_000_000,
      maxFrameRate: 60,
      supportsHDR: supportsHDR,
      supportsHardware4K: allow4K,
      deviceClass: deviceClass,
      multiViewMaxTiles: deviceClass == .lowPower ? 2 : 4
    )
  }

  /// Per-slot AVPlayer caps for MultiView. Mirrors the Android
  /// 720p / 30fps / 2.5Mbps per-slot constraint.
  public static func applyMultiViewCaps(_ player: AVPlayer) {
    player.currentItem?.preferredPeakBitRate = 2_500_000
    player.currentItem?.preferredMaximumResolution = CGSize(width: 1280, height: 720)
    player.currentItem?.preferredForwardBufferDuration = 8
  }
}
