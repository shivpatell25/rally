import Foundation

public enum RallyNetworkError: Error, Sendable {
  case invalidResponse
  case httpStatus(Int, URL)
  case emptyBody(URL)
  case decoding(Error, URL)
}

/// Thin URLSession wrapper: timeouts, UA, redacted logging. No third-party dependency.
public struct HTTPClient: Sendable {
  private static let redacted: Set<String> = ["authorization", "cookie", "set-cookie"]

  public let session: URLSession

  public init(session: URLSession? = nil, timeout: TimeInterval = 12) {
    if let session {
      self.session = session
      return
    }
    let config = URLSessionConfiguration.ephemeral
    config.timeoutIntervalForRequest = timeout
    config.timeoutIntervalForResource = timeout * 2
    config.requestCachePolicy = .reloadIgnoringLocalCacheData
    config.httpAdditionalHeaders = ["User-Agent": "Rally-tvOS/1.0"]
    self.session = URLSession(
      configuration: config, delegate: ScopedRedirectDelegate(), delegateQueue: nil)
  }

  public func data(
    url: URL,
    headers: [String: String] = [:],
    timeout: TimeInterval? = nil
  ) async throws -> (Data, HTTPURLResponse) {
    guard NetworkPolicy.shared.permits(url) else {
      throw URLError(.appTransportSecurityRequiresSecureConnection)
    }
    var request = URLRequest(url: url)
    request.httpMethod = "GET"
    if let timeout { request.timeoutInterval = timeout }
    for (k, v) in StreamHeaders.sanitized(headers) {
      request.setValue(v, forHTTPHeaderField: k)
    }
    RallyLogger.net.debug("GET \(url.host ?? "", privacy: .public)")
    let (data, response): (Data, URLResponse) =
      url.scheme == "http"
      ? try await ScopedHTTPTransport.data(request, timeout: timeout ?? 12)
      : try await session.data(for: request)
    guard let http = response as? HTTPURLResponse else {
      throw RallyNetworkError.invalidResponse
    }
    guard (200..<300).contains(http.statusCode) else {
      RallyLogger.net.error(
        "HTTP \(http.statusCode, privacy: .public) for \(url.host ?? "", privacy: .public)")
      RallyDiagnostics.shared.record("Network", code: "HTTP \(http.statusCode)")
      throw RallyNetworkError.httpStatus(http.statusCode, url)
    }
    return (data, http)
  }

  public func json<T: Decodable>(
    url: URL,
    headers: [String: String] = [:],
    decoder: JSONDecoder? = nil,
    timeout: TimeInterval? = nil
  ) async throws -> T {
    let (data, _) = try await data(url: url, headers: headers, timeout: timeout)
    guard !data.isEmpty else { throw RallyNetworkError.emptyBody(url) }
    do {
      return try (decoder ?? JSONDecoder()).decode(T.self, from: data)
    } catch {
      throw RallyNetworkError.decoding(error, url)
    }
  }

  /// HEAD-style preflight: returns content-type + latency without downloading the body.
  public func preflight(url: URL, headers: [String: String] = [:], timeout: TimeInterval = 6) async
    -> (latencyMs: Int?, contentType: String?, passed: Bool)
  {
    guard NetworkPolicy.shared.permits(url) else { return (nil, nil, false) }
    var request = URLRequest(url: url)
    request.httpMethod = "HEAD"
    request.timeoutInterval = timeout
    for (k, v) in StreamHeaders.sanitized(headers) {
      request.setValue(v, forHTTPHeaderField: k)
    }
    let start = Date()
    do {
      let (_, response): (Data, URLResponse) =
        url.scheme == "http"
        ? try await ScopedHTTPTransport.data(request, timeout: timeout)
        : try await session.data(for: request)
      let ms = Int(Date().timeIntervalSince(start) * 1000)
      let type = (response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Type")
      let code = (response as? HTTPURLResponse)?.statusCode ?? 0
      // A provider without HEAD support is inconclusive; AVPlayer validates the source.
      // Do not accidentally download an unbounded live stream during a probe.
      if code == 405 || code == 501 { return (ms, type, true) }
      return (
        ms, type, (200..<400).contains(code) && !(type?.lowercased().contains("text/html") ?? false)
      )
    } catch {
      return (nil, nil, false)
    }
  }
}
