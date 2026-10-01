import Foundation

/// Builds Xtream URLs without string concatenation. Ports Android `XtreamUrlBuilder`.
public enum XtreamUrlBuilder {
  public static func playerApi(
    server: String, username: String, password: String, action: String? = nil
  ) -> URL? {
    guard var components = URLComponents(string: server) else { return nil }
    components.path = (components.path as NSString).appendingPathComponent("player_api.php")
    var items = [
      URLQueryItem(name: "username", value: username),
      URLQueryItem(name: "password", value: password),
    ]
    if let action, !action.isEmpty { items.append(URLQueryItem(name: "action", value: action)) }
    components.queryItems = items
    return components.url
  }

  public static func liveStream(
    server: String, username: String, password: String, streamId: String,
    fileExtension: String = "m3u8"
  ) -> URL? {
    guard var components = URLComponents(string: server) else { return nil }
    let base = components.path
    let id = streamId.trimmingCharacters(in: .whitespacesAndNewlines)
    components.path =
      ((base as NSString).appendingPathComponent("live") as NSString)
      .appendingPathComponent(username) as NSString as String
    components.path = (components.path as NSString).appendingPathComponent(password)
    components.path = (components.path as NSString).appendingPathComponent("\(id).\(fileExtension)")
    return components.url
  }
}
