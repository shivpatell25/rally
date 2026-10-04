import Foundation

/// Add-on stream discovery. Mirrors Android `StremioRepository`.
public protocol StremioRepository: Sendable {
  func streams(for event: SportEvent) async -> [StremioStreamOption]
  func refreshStreams(for event: SportEvent) async -> [StremioStreamOption]
  func searchStreams(query: String) async -> [StremioStreamOption]
}

extension StremioRepository {
  public func refreshStreams(for event: SportEvent) async -> [StremioStreamOption] {
    await streams(for: event)
  }
}
