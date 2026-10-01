import Foundation

/// Bounded, redacted diagnostic history. Never accepts URLs, headers or credentials.
final class RallyDiagnostics: @unchecked Sendable {
  static let shared = RallyDiagnostics()
  private let lock = NSLock()
  private var entries: [String] = []
  func record(_ category: String, code: String) {
    let line = "\(Date().formatted(date: .omitted, time: .standard)) · \(category) · \(code)"
    lock.lock()
    entries.append(line)
    if entries.count > 30 { entries.removeFirst(entries.count - 30) }
    lock.unlock()
  }
  func report() -> String {
    lock.lock()
    defer { lock.unlock() }
    return entries.joined(separator: "\n")
  }
  func clear() {
    lock.lock()
    entries.removeAll()
    lock.unlock()
  }
}
