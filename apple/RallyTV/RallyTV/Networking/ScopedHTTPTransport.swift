import Foundation
import Network

/// Plain HTTP API transport for an explicitly configured HTTP portal/addon only.
/// ATS stays enabled globally. Every connection and redirect is checked against
/// NetworkPolicy, and credentials cannot be redirected to another host.
enum ScopedHTTPTransport {
  static func data(_ request: URLRequest, timeout: TimeInterval = 12, redirects: Int = 0)
    async throws -> (Data, HTTPURLResponse)
  {
    guard let url = request.url, url.scheme == "http", NetworkPolicy.shared.permits(url),
      let host = url.host, let portNumber = UInt16(exactly: url.port ?? 80), portNumber > 0,
      let port = NWEndpoint.Port(rawValue: portNumber),
      redirects < 5
    else { throw URLError(.appTransportSecurityRequiresSecureConnection) }
    let box = HTTPConnectionBox(
      connection: NWConnection(host: NWEndpoint.Host(host), port: port, using: .tcp),
      request: request, timeout: timeout)
    let response = try await withTaskCancellationHandler(
      operation: { try await box.load() }, onCancel: { box.cancel() })
    if (300..<400).contains(response.1.statusCode),
      let location = response.1.value(forHTTPHeaderField: "Location"),
      let next = URL(string: location, relativeTo: url)?.absoluteURL
    {
      guard next.host?.lowercased() == url.host?.lowercased(), NetworkPolicy.shared.permits(next)
      else { throw URLError(.redirectToNonExistentLocation) }
      var follow = request
      follow.url = next
      if next.scheme == "http" {
        return try await data(follow, timeout: timeout, redirects: redirects + 1)
      }
      return try await HTTPS(follow)
    }
    return response
  }
  private static func HTTPS(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
    let session = URLSession(
      configuration: .ephemeral, delegate: ScopedRedirectDelegate(), delegateQueue: nil)
    defer { session.finishTasksAndInvalidate() }
    let (data, response) = try await session.data(for: request)
    guard let response = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
    return (data, response)
  }
  static func decodeChunked(_ data: Data) throws -> Data {
    var cursor = data.startIndex
    var output = Data()
    while cursor < data.endIndex {
      guard let end = data.range(of: Data("\r\n".utf8), in: cursor..<data.endIndex),
        let text = String(data: data[cursor..<end.lowerBound], encoding: .utf8),
        let size = Int(text.split(separator: ";").first ?? "", radix: 16)
      else { throw URLError(.badServerResponse) }
      cursor = end.upperBound
      if size == 0 { return output }
      guard size >= 0, size <= data.endIndex - cursor, cursor + size + 2 <= data.endIndex else {
        throw URLError(.badServerResponse)
      }
      guard data[cursor + size] == 13, data[cursor + size + 1] == 10 else {
        throw URLError(.badServerResponse)
      }
      output.append(data[cursor..<cursor + size])
      cursor += size + 2
    }
    throw URLError(.badServerResponse)
  }
}
private final class HTTPConnectionBox: @unchecked Sendable {
  let connection: NWConnection
  let request: URLRequest
  let timeout: TimeInterval
  private let queue = DispatchQueue(label: "rally.scoped.http")
  private var continuation: CheckedContinuation<(Data, HTTPURLResponse), Error>?
  private var buffer = Data(), finished = false, started = false
  init(connection: NWConnection, request: URLRequest, timeout: TimeInterval) {
    self.connection = connection
    self.request = request
    self.timeout = timeout
  }
  func load() async throws -> (Data, HTTPURLResponse) {
    try await withCheckedThrowingContinuation { continuation in
      queue.async {
        guard !self.finished else {
          continuation.resume(throwing: CancellationError())
          return
        }
        self.continuation = continuation
        self.connection.stateUpdateHandler = { state in
          switch state {
          case .ready: self.send()
          case .failed(let error): self.finish(.failure(error))
          default: break
          }
        }
        self.connection.start(queue: self.queue)
        self.queue.asyncAfter(deadline: .now() + self.timeout) {
          self.finish(.failure(URLError(.timedOut)))
        }
      }
    }
  }
  func cancel() { queue.async { self.finish(.failure(CancellationError())) } }
  private func send() {
    guard !started, let url = request.url,
      let c = URLComponents(url: url, resolvingAgainstBaseURL: false)
    else { return }
    started = true
    let path =
      (c.percentEncodedPath.isEmpty ? "/" : c.percentEncodedPath)
      + (c.percentEncodedQuery.map { "?" + $0 } ?? "")
    var headers = request.allHTTPHeaderFields ?? [:]
    headers["Host"] = url.host! + (url.port.map { ":\($0)" } ?? "")
    headers["Connection"] = "close"
    headers["Accept-Encoding"] = "identity"
    let wire =
      "\(request.httpMethod ?? "GET") \(path) HTTP/1.1\r\n"
      + headers.map { "\($0.key): \($0.value)\r\n" }.joined() + "\r\n"
    connection.send(
      content: Data(wire.utf8),
      completion: .contentProcessed { error in
        if let error { self.finish(.failure(error)) } else { self.receive() }
      })
  }
  private func receive() {
    connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) {
      data, _, complete, error in
      if let data { self.buffer.append(data) }
      guard self.buffer.count <= 32 * 1024 * 1024 else {
        self.finish(.failure(URLError(.dataLengthExceedsMaximum)))
        return
      }
      if let error {
        self.finish(.failure(error))
        return
      }
      if complete {
        self.parse()
        return
      }
      self.receive()
    }
  }
  private func parse() {
    do {
      guard let split = buffer.range(of: Data("\r\n\r\n".utf8)),
        let head = String(data: buffer[..<split.lowerBound], encoding: .isoLatin1),
        let url = request.url
      else { throw URLError(.badServerResponse) }
      let lines = head.components(separatedBy: "\r\n")
      let status = Int(lines.first?.split(separator: " ").dropFirst().first ?? "") ?? 0
      var headers: [String: String] = [:]
      for line in lines.dropFirst() {
        guard let index = line.firstIndex(of: ":") else { continue }
        headers[String(line[..<index])] = line[line.index(after: index)...].trimmingCharacters(
          in: .whitespaces)
      }
      guard
        let response = HTTPURLResponse(
          url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers)
      else { throw URLError(.badServerResponse) }
      var body = Data(buffer[split.upperBound...])
      if response.value(forHTTPHeaderField: "Transfer-Encoding")?.lowercased().contains("chunked")
        == true
      {
        body = try ScopedHTTPTransport.decodeChunked(body)
      }
      finish(.success((body, response)))
    } catch { finish(.failure(error)) }
  }
  private func finish(_ result: Result<(Data, HTTPURLResponse), Error>) {
    guard !finished else { return }
    finished = true
    connection.cancel()
    continuation?.resume(with: result)
    continuation = nil
  }
}
