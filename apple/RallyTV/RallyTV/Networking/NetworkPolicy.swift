import Foundation

/// HTTP API exceptions are confined to the exact hosts entered in Sources/Addons.
/// All other API and image connections require HTTPS. Redirects cannot broaden this list.
final class NetworkPolicy: @unchecked Sendable {
  static let shared = NetworkPolicy()
  private let lock = NSLock()
  private var hosts = Set<String>()
  private var temporary: [String: Int] = [:]
  func configure(_ settings: SettingsStore) {
    let configured =
      [settings.portalUrl, settings.xtreamServerUrl, settings.m3uPlaylistUrl]
      + settings.stremioAddonUrls.map(\.absoluteString)
    let values = Set(
      configured.compactMap { value -> String? in
        guard let url = URL(string: value), url.scheme == "http" else { return nil }
        return url.host?.lowercased()
      })
    lock.lock()
    hosts = values
    lock.unlock()
  }
  func allowTemporarily(_ url: URL) {
    guard let host = url.host?.lowercased() else { return }
    lock.lock()
    temporary[host, default: 0] += 1
    lock.unlock()
  }
  func revokeTemporary(_ url: URL) {
    guard let host = url.host?.lowercased() else { return }
    lock.lock()
    let count = temporary[host, default: 0] - 1
    temporary[host] = count > 0 ? count : nil
    lock.unlock()
  }
  func permits(_ url: URL) -> Bool {
    guard let host = url.host?.lowercased(), !host.isEmpty else { return false }
    if url.scheme?.lowercased() == "https" { return true }
    guard url.scheme?.lowercased() == "http" else { return false }
    if host == "127.0.0.1" || host == "localhost" { return true }
    lock.lock()
    defer { lock.unlock() }
    return hosts.contains(host) || temporary[host] != nil
  }
  func isConfigured(_ url: URL) -> Bool {
    guard let host = url.host?.lowercased() else { return false }
    lock.lock()
    defer { lock.unlock() }
    return hosts.contains(host) || temporary[host] != nil
  }
}
final class ScopedRedirectDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
  func urlSession(
    _ session: URLSession, task: URLSessionTask,
    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
    completionHandler: @escaping (URLRequest?) -> Void
  ) {
    guard let url = request.url, NetworkPolicy.shared.permits(url) else {
      completionHandler(nil)
      return
    }
    // Portal/API credentials must never follow a redirect to a different host.
    if let original = task.originalRequest?.url, NetworkPolicy.shared.isConfigured(original),
      original.host?.lowercased() != url.host?.lowercased()
    {
      completionHandler(nil)
      return
    }
    var safe = request
    if task.originalRequest?.url?.host?.lowercased() != url.host?.lowercased() {
      safe.setValue(nil, forHTTPHeaderField: "Authorization")
      safe.setValue(nil, forHTTPHeaderField: "Cookie")
    }
    completionHandler(safe)
  }
}

/// A selected channel in a user-configured playlist can name a different HTTP
/// media host. Permit that exact host only while its playback session exists.
/// This never allows arbitrary hosts discovered by redirects or HLS playlists.
final class PlaylistMediaPermission {
  private let url: URL
  init(_ url: URL) {
    self.url = url
    NetworkPolicy.shared.allowTemporarily(url)
  }
  deinit { NetworkPolicy.shared.revokeTemporary(url) }
}
