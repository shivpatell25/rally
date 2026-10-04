# Apple TV multiview audit — Beta 13

Validated October 4, 2026 on tvOS 27 simulators. Apple TV needed fixes, so Beta 13 includes a newly built device IPA, not a renamed previous build.

## Findings and fixes

- Existing `MultiTile` objects already owned stable AVPlayer sessions. Layout/focus changes did not have Android's tile-owned decoder allocation problem.
- Addon retries reused their original signed URL. Automatic recovery and manual Retry now bypass event stream cache, renew the selected broadcast, and use current headers.
- Live streams that ended stopped permanently. Unexpected live endings now use the existing bounded reconnect path.
- Retrying or backgrounding retained connections and player items. Sessions now clear the failed/inactive item, stop its media proxy, cancel requests, and reload on return. Intentional pause state and finite-video position are preserved.
- Stalker actors were reentrant across network awaits, allowing overlapping link negotiations and authentication refreshes. Link requests now use a cancellation-aware gate and shared authentication task. Invalid/non-HTTP/local portal commands are rejected.
- The header proxy retained every live segment URL and did not track/cancel plain-HTTP requests. It now prunes expired playlist resources using the current master/variant/key graph and cancels active connections and tasks on stop.
- Async SwiftUI source discovery could capture an inactive `scenePhase` and suspend newly created streams after the app was already active. A State-backed current phase now controls completion of async source loads. Strengthened remote tests reproduced the black tiles before this fix and passed afterward.
- A ready item/advancing clock did not prove visible video. Multiview's Playing accessibility state now also requires AVPlayerLayer readiness. The remote tests wait for every tile to have a displayable video frame.

## Verification

- Five native playback/transport regression tests: four simultaneous streams, expired addon URLs and updated headers, repeated retries, frozen portal feeds, three background/return cycles with pause preserved, concurrent portal requests, invalid links, bounded failure recovery, live-window resource pruning, and cancellation of pending HTTP.
- Full hosted unit suite: 33 tests, 32 passed, one optional public-addon network test skipped, zero failures.
- Two remote tests passed for stats tiles and two/three/four-stream layouts, immersive mode, promotion, audio controls, fullscreen navigation and restoration. Both require actual layer readiness in every stream tile.
- Inspected post-fix screenshots in [multiview/apple-tv/](multiview/apple-tv/): stats composition and four-stream normal/immersive playback.
- Release build targets physical Apple TV, build 16, bundle ID `com.shiv.rally.tv`, minimum tvOS 17. Its IPA archive contains no test bundles or test video assets.
- Native fixtures use production repositories, AVPlayer and the authenticated media proxy against a loopback HLS/addon/Stalker server. Separate remote tests use Apple's public unencrypted HLS sample.

No physical Apple TV or the user's addon/portal accounts were connected. Simulator coverage does not prove every provider, codec, connection limit or hardware combination. Persistent provider failures remain bounded and expose Retry / source selection.

## Reproduce

```sh
xcodebuild -project apple/RallyTV/RallyTV.xcodeproj -scheme RallyTV \
  -destination 'platform=tvOS Simulator,id=SIMULATOR_UUID' \
  -only-testing:RallyTVTests \
  -only-testing:RallyTVUITests/RallyParityUITests/testMultiviewStatsAndImmersive \
  -only-testing:RallyTVUITests/RallyParityUITests/testMultiviewTwoThreeFourStreamBoundsAndManagement test

xcodebuild -project apple/RallyTV/RallyTV.xcodeproj -scheme RallyTV \
  -configuration Release -destination 'generic/platform=tvOS' CODE_SIGNING_ALLOWED=NO build
```
