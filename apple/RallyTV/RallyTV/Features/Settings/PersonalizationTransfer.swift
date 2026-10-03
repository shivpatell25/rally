import Darwin
import Network
import SwiftUI

/// User-started local transfer, protected by an unguessable URL. No cloud upload.
@MainActor @Observable final class PersonalizationTransfer {
  var address = ""
  var status = "Starting local transfer…"
  private var listener: NWListener?
  private let token = UUID().uuidString
  private let queue = DispatchQueue(label: "rally.settings.transfer")
  func start(settings: SettingsStore) {
    stop()
    do {
      let listener = try NWListener(using: .tcp, on: .any)
      self.listener = listener
      listener.stateUpdateHandler = { state in
        Task { @MainActor in
          switch state {
          case .ready:
            self.address =
              "http://\(Self.localAddress()):\(listener.port?.rawValue ?? 0)/\(self.token)"
            self.status =
              "Open this address on your phone or computer. Transfer closes when this panel closes."
          case .failed:
            self.status = "Local transfer could not start. Check Local Network permission."
          default: break
          }
        }
      }
      listener.newConnectionHandler = { connection in
        connection.start(queue: self.queue)
        self.queue.asyncAfter(deadline: .now() + 30) { connection.cancel() }
        self.read(connection, settings: settings, buffer: Data())
      }
      listener.start(queue: queue)
    } catch { status = "Local transfer could not start." }
  }
  nonisolated private func read(_ connection: NWConnection, settings: SettingsStore, buffer: Data) {
    connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) {
      data, _, complete, error in
      var buffer = buffer
      if let data { buffer.append(data) }
      guard buffer.count <= M3uPlaylistParser.maximumBytes + 8192 else {
        connection.cancel()
        return
      }
      guard let split = buffer.range(of: Data("\r\n\r\n".utf8)),
        let header = String(data: buffer[..<split.lowerBound], encoding: .utf8)
      else {
        if buffer.count > 8192 {
          connection.cancel()
        } else if !complete && error == nil {
          self.read(connection, settings: settings, buffer: buffer)
        }
        return
      }
      let body = buffer[split.upperBound...]
      let lines = header.components(separatedBy: "\r\n")
      guard header.utf8.count <= 8192 else {
        connection.cancel()
        return
      }
      let path = lines.first?.split(separator: " ").dropFirst().first.map(String.init) ?? ""
      let maximum = path.hasSuffix("/playlist") ? M3uPlaylistParser.maximumBytes : 1_000_000
      let length =
        Int(
          lines.first(where: { $0.lowercased().hasPrefix("content-length:") })?.split(
            separator: ":"
          ).last?.trimmingCharacters(in: .whitespaces) ?? "0") ?? 0
      guard length >= 0 && length <= maximum else {
        connection.cancel()
        return
      }
      if body.count < length {
        if !complete && error == nil {
          self.read(connection, settings: settings, buffer: buffer)
        } else {
          connection.cancel()
        }
        return
      }
      Task { @MainActor in
        await self.respond(
          connection, header: header, body: Data(body.prefix(length)), settings: settings)
      }
    }
  }
  private func respond(
    _ connection: NWConnection, header: String, body: Data, settings: SettingsStore
  ) async {
    guard listener != nil else {
      connection.cancel()
      return
    }
    let first =
      header.components(separatedBy: "\r\n").first?.split(separator: " ").map(String.init) ?? []
    guard first.count >= 2, first[1] == "/\(token)" || first[1].hasPrefix("/\(token)/") else {
      send(connection, body: Data("Forbidden".utf8), type: "text/plain", code: 403)
      return
    }
    if first[0] == "POST", first[1] == "/\(token)/playlist" {
      do {
        let encodedName =
          header.components(separatedBy: "\r\n").first(where: {
            $0.lowercased().hasPrefix("x-rally-playlist-name:")
          })?.split(separator: ":", maxSplits: 1).last.map(String.init)?.trimmingCharacters(
            in: .whitespaces) ?? "Imported Playlist"
        let name = String((encodedName.removingPercentEncoding ?? "Imported Playlist").prefix(100))
        let count = try await Task.detached(priority: .userInitiated) {
          try M3uPlaylistFiles.importCatalog(body, name: name, settings: settings)
        }.value
        status = "Playlist imported · \(count) channels. Select Done to apply."
        send(
          connection, body: Data("Playlist imported. Return to Rally and select Done.".utf8),
          type: "text/plain")
      } catch {
        send(
          connection,
          body: Data(
            (error as? M3uPlaylistError)?.localizedDescription.utf8
              ?? "The playlist could not be imported.".utf8), type: "text/plain", code: 400)
      }
      return
    }
    if first[0] == "POST", first[1] == "/\(token)/import" {
      do {
        try settings.importPersonalization(String(decoding: body, as: UTF8.self))
        status = "Preferences imported successfully."
        send(
          connection, body: Data("Preferences imported. Return to Rally.".utf8), type: "text/plain")
      } catch {
        send(
          connection,
          body: Data("Invalid or unsupported Rally backup. No credentials can be imported.".utf8),
          type: "text/plain", code: 400)
      }
      return
    }
    guard first[0] == "GET" else {
      send(connection, body: Data(), type: "text/plain", code: 405)
      return
    }
    if first[1] == "/\(token)/backup" {
      send(
        connection, body: Data(((try? settings.exportPersonalization()) ?? "{}").utf8),
        type: "application/json")
      return
    }
    if first[1] == "/\(token)/report" {
      send(connection, body: Data(settings.supportReport.utf8), type: "text/plain")
      return
    }
    let html = """
      <!doctype html><meta name="viewport" content="width=device-width"><title>Rally Settings Transfer</title>
      <style>body{background:#050507;color:#fff;font:18px system-ui;max-width:650px;margin:40px auto;padding:20px}a{color:#fff}button,input{padding:14px;margin:12px 0}</style>
      <h1>Rally Settings Transfer</h1><p>This connection stays on your local network. Backups exclude provider credentials and addresses.</p>
      <p><a href="/\(token)/backup" download="rally-preferences.json">Download preferences</a></p><p><a href="/\(token)/report" download="rally-support.txt">Download support report</a></p>
      <h2>Import preferences</h2><input type="file" id="file" accept="application/json"><button id="import">Import</button><p id="status"></p>
      <h2>Import M3U/M3U8 playlist</h2><p>Choose a channel playlist (up to 16 MB). Single HLS streams must be entered by URL in Rally Settings.</p><input type="file" id="playlist" accept=".m3u,.m3u8"><button id="upload">Import playlist</button><p id="playlist-status"></p>
      <script>document.getElementById('import').onclick=async()=>{const f=document.getElementById('file').files[0];if(!f)return;const r=await fetch('/\(token)/import',{method:'POST',body:await f.text()});document.getElementById('status').textContent=await r.text();};</script>
      <script>document.getElementById('upload').onclick=async()=>{const f=document.getElementById('playlist').files[0];if(!f)return;if(f.size>16777216){document.getElementById('playlist-status').textContent='Playlist exceeds 16 MB.';return;}try{const r=await fetch('/\(token)/playlist',{method:'POST',headers:{'X-Rally-Playlist-Name':encodeURIComponent(f.name)},body:await f.text()});document.getElementById('playlist-status').textContent=await r.text();}catch(e){document.getElementById('playlist-status').textContent='Transfer closed. Reopen the panel in Rally.';}};</script>
      """
    send(connection, body: Data(html.utf8), type: "text/html; charset=utf-8")
  }
  private func send(_ connection: NWConnection, body: Data, type: String, code: Int = 200) {
    var data = Data(
      "HTTP/1.1 \(code) Response\r\nContent-Type: \(type)\r\nContent-Length: \(body.count)\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        .utf8)
    data.append(body)
    connection.send(content: data, completion: .contentProcessed { _ in connection.cancel() })
  }
  func stop() {
    listener?.cancel()
    listener = nil
  }
  private static func localAddress() -> String {
    var list: UnsafeMutablePointer<ifaddrs>?
    guard getifaddrs(&list) == 0 else { return "127.0.0.1" }
    defer { freeifaddrs(list) }
    var ptr = list
    while let node = ptr {
      if let address = node.pointee.ifa_addr, address.pointee.sa_family == UInt8(AF_INET),
        String(cString: node.pointee.ifa_name).hasPrefix("en")
      {
        var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
        if getnameinfo(
          address, socklen_t(address.pointee.sa_len), &host, socklen_t(host.count), nil, 0,
          NI_NUMERICHOST) == 0
        {
          return String(cString: host)
        }
      }
      ptr = node.pointee.ifa_next
    }
    return "127.0.0.1"
  }
}
struct PersonalizationTransferSheet: View {
  @Environment(RallyStore.self) private var store
  let dismiss: () -> Void
  @State private var transfer = PersonalizationTransfer()
  var body: some View {
    RallyCanvas {
      ZStack {
        RallyBackdrop()
        VStack(spacing: RallyDesign.pt(18)) {
          Text("Rally Settings Transfer").font(RallyDesign.font(25, .bold))
          if !transfer.address.isEmpty {
            QRImage(value: transfer.address).frame(
              width: RallyDesign.pt(150), height: RallyDesign.pt(150))
            Text(transfer.address).font(RallyDesign.font(13)).accessibilityIdentifier(
              "Local transfer address")
          }
          Text(transfer.status).font(RallyDesign.font(12)).foregroundStyle(RallyDesign.muted).frame(
            width: RallyDesign.pt(600)
          ).multilineTextAlignment(.center)
          RallyAction(title: "Done", action: dismiss)
        }
      }
    }.task { transfer.start(settings: store.settings) }.onDisappear { transfer.stop() }
      .onExitCommand(perform: dismiss).presentationBackground(.black)
  }
}
