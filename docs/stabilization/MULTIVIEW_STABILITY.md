# Android TV multiview stabilization

Validated October 4, 2026. Windows work remains paused.

## Problems addressed

- Players belonged to individual Compose tiles. Reparenting tiles between layouts could recreate decoders and connections; source changes could allocate a replacement before releasing the old player. A screen-owned controller now preserves sessions by stable slot identity and completes removals before allocations.
- Portal token changes could restart every tile. Token refresh now affects the next connection instead of interrupting healthy streams.
- Frozen streams had no automatic recovery. A per-tile watchdog checks rendered frames and playback progress, including startup stalls and streams that unexpectedly end. Recovery releases only the failed session, renews its source and retries after 1, 3 and 6 seconds. Thirty seconds of advancing video is required to reset its retry budget.
- Addon retries reused cached, expired URLs. Recovery now bypasses event discovery cache, reselects the chosen broadcast where possible, and carries its current playback headers.
- Stalker link negotiation could return a channel ID or local command after a failure. Negotiations are serialized, invalid links cause one authentication refresh, and unrecoverable negotiation produces a clear error. Actual video downloads remain concurrent.
- Cancelled or superseded resolution could affect replaced tiles. Resolution generations and playback revisions reject obsolete callbacks; cancellation propagates through discovery and negotiation.
- Background transitions and navigation retained decoder resources or failed to resume after state updates coalesced. Multiview releases sessions while inactive and explicitly synchronizes on each resume. The outgoing single player stops before navigation and prepares again on return, preserving user pause state.
- On the tested Android 14 Chromecast, embedded SurfaceView sessions could report advancing frames while tiles remained black after layout changes. Android 14 now uses the existing embedded TextureView resource; other Android versions retain SurfaceView. Screenshot assertions verify actual visible video in addition to playback state.
- Startup allocation is staggered. Buffer budgets are bounded, only the audible tile enables audio decoding, and track budgets account for device class. Shield devices retain a Full HD budget. Adaptive streams can select smaller renditions during recovery; fixed streams cannot be transcoded by a track-selection cap.

## Validation

| Check | Result |
| --- | --- |
| JVM unit tests | 122 passed; 0 failures or errors |
| Chromecast, Android 14 / API 34 | 8 native tests passed, 278.836 seconds |
| Television_4K emulator, API 36 | 8 native tests passed, 147.150 seconds |
| Release compilation and R8 optimization | Passed |
| Whitespace / patch check | Passed |

Native tests use actual ExoPlayer decoders and the production Stremio and Stalker repositories against a local HTTP fault server. The test asset is a generated H.264/AAC transport stream, packaged only in the instrumentation APK. The server supplies rolling live HLS playlists, required request headers, Stremio discovery and rotating portal links.

Covered scenarios:

- Four simultaneous addon and portal streams.
- Expired signed links, invalid/missing portal responses, and correct stream headers.
- A frozen live playlist; only the affected tile restarts.
- A permanently unavailable stream exhausts its retry budget while the other three continue; manual retry restores it once available.
- Repeated focus, audio, layout, immersive, source retry, swap and promotion changes.
- Removing a stream, adding a stats tile, and restoring four video streams.
- Three background/foreground cycles for multiview and single playback; user-paused playback stays paused.
- Player identity preservation, release ordering, stale error rejection and cancellation.
- Visible video in every tile after immersive layout changes and recovery.

Screenshots are in [multiview/](multiview/), and native test output is saved alongside them. They contain synthetic test patterns, not customer streams.

## Reproduce

Use JDK 17 and an Android TV emulator or connected device. Build an isolated QA package so tests cannot change production settings:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest -PrallyQa=true
adb -s DEVICE install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE shell am instrument -w -e class com.shiv.rally.presentation.player.MultiViewStabilityTest,com.shiv.rally.presentation.player.MultiViewSessionOwnershipTest,com.shiv.rally.presentation.player.MultiViewEmulatorTest,com.shiv.rally.presentation.player.PlayerForegroundRecoveryTest,com.shiv.rally.presentation.player.PlaybackSourceSwitchTest com.shiv.spatelorts.qa.test/androidx.test.runner.AndroidJUnitRunner
./gradlew :app:minifyReleaseWithR8
```

The failure-injection suite refuses to run against a package without the `.qa` suffix. This Gradle property affects debug builds only; release signing and the production application ID remain unchanged.

## Remaining validation boundaries

The reported device is a Shield TV Pro, but it was not connected for this run. Its rendition policy has unit coverage; physical Shield decoding and the user's actual addon/portal accounts remain unverified. No customer crash trace was available to confirm a single crash signature.

Provider outages, expired subscriptions, and account connection limits can still make a stream unavailable. Recovery is bounded and exposes Retry / source selection when the provider remains unavailable; these tests do not establish universal reliability across every codec and provider.

Prepared for Beta 13. Signing identity remains unchanged, and validation used an isolated QA installation without replacing the installed production application. GitHub's signed release workflow produces the production and migration APKs.
