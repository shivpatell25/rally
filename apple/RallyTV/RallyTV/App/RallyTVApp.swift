import SwiftUI

/// Entry point. Owns the container and the navigation path; chrome visibility
/// mirrors MainActivity (no top bar on player Pico fullscreen — lands with Player).
@main
struct RallyTVApp: App {
  @State private var container: AppContainer = {
    #if DEBUG
      if ProcessInfo.processInfo.arguments.contains("--fixtures") {
        return TVOSFixtures.container()
      }
    #endif
    return AppContainer()
  }()
  @State private var path: [RallyRoute] = []

  var body: some Scene {
    WindowGroup {
      RootView(container: container, path: $path)
    }
  }
}
