import Foundation

/// Last-known-good schedule cache. Ports Android `SportsEventDiskCache`.
public actor ScheduleDiskCache {
  private let fileURL: URL
  private let maxAge: TimeInterval
  private var lastSignature: Int?

  public init(fileURL: URL? = nil, maxAge: TimeInterval = 6 * 3600) {
    let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
    self.fileURL = fileURL ?? base.appendingPathComponent("sports_schedule_cache_v1.json")
    self.maxAge = maxAge
  }

  public func read() -> [SportEvent] {
    guard let data = try? Data(contentsOf: fileURL),
      let root = try? JSONDecoder().decode(CacheRoot.self, from: data),
      Date().timeIntervalSince(root.cachedAt) < maxAge
    else { return [] }
    lastSignature = Self.signature(root.events)
    return root.events
  }

  public func write(_ events: [SportEvent]) {
    guard !events.isEmpty else { return }
    let signature = Self.signature(events)
    guard signature != lastSignature else { return }
    let root = CacheRoot(cachedAt: Date(), events: Array(events.uniqued(by: \.id)))
    guard let data = try? JSONEncoder().encode(root) else { return }
    try? data.write(to: fileURL, options: .atomic)
    lastSignature = signature
  }

  private static func signature(_ events: [SportEvent]) -> Int {
    events.map {
      "\($0.id):\($0.status):\($0.scoreAway ?? -1):\($0.scoreHome ?? -1):\($0.startTime.timeIntervalSince1970)"
    }
    .sorted().joined(separator: "|").hashValue
  }

  private struct CacheRoot: Codable {
    let cachedAt: Date
    let events: [SportEvent]
  }
}

extension Array {
  fileprivate func uniqued<T: Hashable>(by key: (Element) -> T) -> [Element] {
    var seen = Set<T>()
    return filter { seen.insert(key($0)).inserted }
  }
}
