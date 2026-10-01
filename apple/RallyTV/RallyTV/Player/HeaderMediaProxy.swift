import CryptoKit
import Foundation
import Network

/// Loopback-only transport adapter for providers requiring arbitrary HTTP headers.
/// AVPlayer uses ordinary HLS; playlists, variants, segments and AES keys are rewritten
/// through the same authenticated URLSession. Credentials never appear in local URLs.
final class HeaderMediaProxy: @unchecked Sendable {
  private let queue = DispatchQueue(label: "rally.media.transport", qos: .userInitiated)
  private var listener: NWListener?
  private let session: URLSession
  private let headers: [String: String]
  private let token = UUID().uuidString
  private var port: UInt16 = 0
  private let resourceLock = NSLock()
  private var resources: [String: URL] = [:]
  init(headers: [String: String]) {
    self.headers = StreamHeaders.sanitized(headers)
    let config = URLSessionConfiguration.ephemeral
    config.timeoutIntervalForRequest = 15
    config.timeoutIntervalForResource = 30
    session = URLSession(
      configuration: config, delegate: ScopedRedirectDelegate(), delegateQueue: nil)
  }
  func start(_ upstream: URL) async throws -> URL {
    let parameters = NWParameters.tcp
    parameters.requiredLocalEndpoint = .hostPort(host: .ipv4(.loopback), port: .any)
    let listener = try NWListener(using: parameters)
    self.listener = listener
    listener.newConnectionHandler = { [weak self] connection in self?.receive(connection) }
    try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
      let ready = ReadyLatch()
      listener.stateUpdateHandler = { [weak self] state in
        guard ready.claim(state) else { return }
        switch state {
        case .ready:
          self?.port = listener.port?.rawValue ?? 0
          continuation.resume()
        case .failed(let error): continuation.resume(throwing: error)
        case .cancelled: continuation.resume(throwing: CancellationError())
        default: break
        }
      }
      listener.start(queue: queue)
    }
    return localURL(upstream)
  }
  func stop() {
    listener?.cancel()
    listener = nil
    session.invalidateAndCancel()
  }
  deinit {
    listener?.cancel()
    session.invalidateAndCancel()
  }
  private func localURL(_ upstream: URL) -> URL {
    let encoded = SHA256.hash(data: Data(upstream.absoluteString.utf8)).map {
      String(format: "%02x", $0)
    }.joined()
    resourceLock.lock()
    resources[encoded] = upstream
    resourceLock.unlock()
    return URL(string: "http://127.0.0.1:\(port)/\(token)/\(encoded)")!
  }
  private func resource(_ id: String) -> URL? {
    resourceLock.lock()
    defer { resourceLock.unlock() }
    return resources[id]
  }
  private func receive(_ connection: NWConnection) {
    connection.start(queue: queue)
    read(connection, buffer: Data())
  }
  private func read(_ connection: NWConnection, buffer: Data) {
    connection.receive(minimumIncompleteLength: 1, maximumLength: 16384) {
      [weak self] data, _, complete, error in
      guard let self else {
        connection.cancel()
        return
      }
      var buffer = buffer
      if let data { buffer.append(data) }
      guard buffer.count <= 65536 else {
        connection.cancel()
        return
      }
      if let text = String(data: buffer, encoding: .utf8), text.contains("\r\n\r\n") {
        Task { await self.respond(connection, text: text) }
      } else if !complete && error == nil {
        self.read(connection, buffer: buffer)
      } else {
        connection.cancel()
      }
    }
  }
  private func respond(_ connection: NWConnection, text: String) async {
    let lines = text.components(separatedBy: "\r\n")
    let requestLine = lines.first?.split(separator: " ").map(String.init) ?? []
    guard requestLine.count >= 2, requestLine[0] == "GET" || requestLine[0] == "HEAD" else {
      send(connection, status: 405, body: Data())
      return
    }
    let parts = requestLine[1].split(separator: "/").map(String.init)
    guard parts.count == 2, parts[0] == token else {
      send(connection, status: 403, body: Data())
      return
    }
    guard let url = resource(parts[1]), ["http", "https"].contains(url.scheme ?? "") else {
      send(connection, status: 400, body: Data())
      return
    }
    var request = URLRequest(url: url)
    request.httpMethod = requestLine[0]
    headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
    for line in lines.dropFirst() {
      if line.lowercased().hasPrefix("range:") {
        request.setValue(
          String(line.dropFirst(6)).trimmingCharacters(in: .whitespaces),
          forHTTPHeaderField: "Range")
      }
    }
    do {
      guard NetworkPolicy.shared.permits(url) else {
        throw URLError(.appTransportSecurityRequiresSecureConnection)
      }
      let (upstream, response): (Data, URLResponse) =
        url.scheme == "http"
        ? try await ScopedHTTPTransport.data(request, timeout: 15)
        : try await session.data(for: request)
      guard let http = response as? HTTPURLResponse else { throw RallyNetworkError.invalidResponse }
      var body = upstream
      var type = http.value(forHTTPHeaderField: "Content-Type") ?? "application/octet-stream"
      if let playlist = String(data: upstream, encoding: .utf8), playlist.hasPrefix("#EXTM3U") {
        let base = http.url ?? url
        let rewritten = playlist.components(separatedBy: "\n").map { line -> String in
          if !line.hasPrefix("#"), !line.trimmingCharacters(in: .whitespaces).isEmpty,
            let absolute = URL(
              string: line.trimmingCharacters(in: .whitespacesAndNewlines), relativeTo: base)?
              .absoluteURL
          {
            return localURL(absolute).absoluteString
          }
          guard let regex = try? NSRegularExpression(pattern: "URI=\"([^\"]+)\"") else {
            return line
          }
          var output = line
          for match in regex.matches(in: line, range: NSRange(line.startIndex..., in: line))
            .reversed()
          {
            if let range = Range(match.range(at: 1), in: output),
              let absolute = URL(string: String(output[range]), relativeTo: base)?.absoluteURL
            {
              output.replaceSubrange(range, with: localURL(absolute).absoluteString)
            }
          }
          return output
        }.joined(separator: "\n")
        body = Data(rewritten.utf8)
        type = "application/vnd.apple.mpegurl"
      }
      var extra: [String: String] = [:]
      if let range = http.value(forHTTPHeaderField: "Content-Range") {
        extra["Content-Range"] = range
      }
      if let range = http.value(forHTTPHeaderField: "Accept-Ranges") {
        extra["Accept-Ranges"] = range
      }
      send(connection, status: http.statusCode, body: body, type: type, extra: extra)
    } catch { send(connection, status: 502, body: Data()) }
  }
  private func send(
    _ connection: NWConnection, status: Int, body: Data, type: String = "text/plain",
    extra: [String: String] = [:]
  ) {
    let header =
      "HTTP/1.1 \(status) Response\r\nContent-Type: \(type)\r\nContent-Length: \(body.count)\r\nConnection: close\r\n"
      + extra.map { "\($0.key): \($0.value)\r\n" }.joined() + "\r\n"
    var payload = Data(header.utf8)
    payload.append(body)
    connection.send(content: payload, completion: .contentProcessed { _ in connection.cancel() })
  }
}

private final class ReadyLatch: @unchecked Sendable {
  private let lock = NSLock()
  private var resumed = false
  func claim(_ state: NWListener.State) -> Bool {
    switch state {
    case .ready, .failed, .cancelled: break
    default: return false
    }
    lock.lock()
    defer { lock.unlock() }
    guard !resumed else { return false }
    resumed = true
    return true
  }
}
