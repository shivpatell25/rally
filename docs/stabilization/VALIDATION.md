# Rally cross-platform stabilization — October 3, 2026

This pass preserves the existing app structure and features. Settings is the only redesigned screen. This report records the validation completed before publication. Releases are distributed through the Rally and Rally Desktop GitHub repositories.

## macOS

- Window-scoped input and activation tracking replaces route-only idle detection. Keyboard, pointer, click and scroll activity reset idle time; returning to Rally wakes it without stealing focus from another app.
- The AFK overlay covers the complete window, including the titlebar area, without a fixed geometry frame or expensive blur. Its responsive score layout fits the minimum window, standard window and fullscreen.
- A wake click is consumed before it can activate a hidden action. Navigation and underlying view identity remain intact.
- Verified idle entry, continued activity, switching to another app, return/wake, resizing while inactive, minimum 820×600, standard 1440×900, fullscreen and returning to windowed mode.
- 98 macOS tests passed. Debug and Release builds passed. The local 0.7.1 development DMG passed checksum verification and its app passed strict code-signature verification. This is an ad-hoc development build, not a notarized distribution.

Evidence: [macOS AFK report](https://github.com/shivpatell25/rally-desktop/blob/v0.7.1/docs/macos/STABILIZATION_VALIDATION.md).

## tvOS

### Root causes repaired

- Focus overflow was clipped by scroll parents and compact row bounds. Containers now reserve focus padding and allow the existing focus scale to render without resizing neighbors.
- Home upcoming items previously used visual offsets that disagreed with the native focus engine's layout positions. A stable, animatable `Layout` now expands the same four elements into guide rows at their actual positions.
- Game View's parent focus handler could read the focus destination instead of the originating control, skipping panels. Each panel now handles its own directional boundary. Restart, video, source/quality, controls, tabs, moments and all four Stats panels have explicit connections where geometry alone is insufficient.
- Background refresh removed loaded views while returning from details, destroying native focus restoration. Loaded content now stays mounted during refresh.
- Settings stored outside SwiftUI observation did not redraw reminder, follow and preference controls. Feature reads now register the store's settings revision.
- Team roster sheets explicitly restore the originating player. Search retains results when returning with the same query.
- Settings uses a section selector and one content column: Account, Playback, Appearance, Sources/Streaming, Notifications and App/About. Providers, addons, team/sports ordering, accessibility, alerts, transfer, diagnostics and support actions remain available.
- Settings retains viewport clipping with internal focus padding so scrolled content cannot cover its heading.
- The Account Sports tabs now have a directional connection to the first enabled-league control because its geometric focus frame begins to the right of the tab. Sports rows also supply focus sections across their noninteractive label area.

### Executed checks

The remote-only suite covers onboarding; all main destinations; Event tabs; return focus from Home, Live, Schedule, My Rally, league and team events; roster sheets; native search keyboard and result restoration; channel playback; M3U file transfer/import; provider fields/actions and keyboard cancellation; multiview with two, three and four real HLS streams; immersive mode; Game View restart, audio, captions, source switching, fullscreen, diagnostics, rail toggles and Stats panels; and Settings sections/preferences/support menus. Focus bounds are checked against screen margins.

All 15 runnable UI checks passed across the main 1080p run and focused repair reruns; 20 core checks passed. One optional production-feed check was skipped. The expanded Settings inventory exposed a Sports focus failure, which was repaired and passed afterward at both resolutions. Final 4K Home, Game View, Settings preferences and backups passed (4 tests); after correcting Settings viewport clipping, provider action bounds and all preference/support controls passed again at 1080p (2 tests) and 4K (3 tests). Device Release compilation also passed.

Core checks include actual public HLS playback, seeking/tracks/watchdog behavior, network scoping, M3U parsing, transfer sockets, RedZone game aggregation and statistics identity mapping.

Screenshots: [Home top](screenshots/4k-home-top.png), [Home guide](screenshots/4k-home-guide.png), [Game View](screenshots/4k-game-view.png), [Settings](screenshots/1080p-settings-sources.png).

Local device artifact: [unsigned tvOS IPA](../../brag-output/stabilization/Rally-tvOS-stabilization-unsigned.ipa). It requires tvOS signing before device installation.

Simulator validation includes 1920×1080 and 3840×2160 rendering. No physical Apple TV was available in this pass; actual remote hardware timing, decoder performance and private subscription providers remain device/account checks. A public HLS test pattern in screenshots is intentional test content.

## Android TV signing and update

| Item | Verified value |
| --- | --- |
| Application ID | `com.shiv.spatelorts` — unchanged |
| Release version | `1.0-beta12`, versionCode `13` |
| Old installed/published identity | `211711b801aa9c4ec006b57254ff4e8a0e623dedee4baa8ab1d1d63f9be1425b` |
| Recovered production identity | `79ffff57b611ec7fbbc690007196df16dfd658432dd452458c56108bae55df73` |
| Chromecast | Android 14 / API 34 |
| Data preservation | Same UID, private sentinel, two original preference-file hashes |

The original production key was recovered. The installed Beta 10 Chromecast and latest published Beta 11 APK were debug-signed. The user authorized verified signing-lineage migration. A public immutable lineage now connects that exact old identity to production; private keys are excluded from source control.

- Release builds require intentional credentials and cannot silently use debug signing.
- CI restores both keys from encrypted secrets, builds optimized non-debuggable release output, signs and verifies migration/legacy assets, then removes private keys.
- All eight encrypted secrets were configured in `shivpatell25/rally`, with both certificate identities checked before transfer. Secret names and update dates were verified afterward; secret values were not logged.
- The updater selects the exact Android asset suffix for the device API level, pins the production certificate, validates signing history for migration, and still validates package, higher version, trusted URL and download digest.
- Android 9+ uses the migration APK. A separate production-signed legacy APK supports the original production-key Android 8 population. Android 8 debug-signed installs cannot migrate their identity in place.
- 110 Android unit tests passed. Both release APKs passed signing and metadata checks.
- The old shipping updater validated the staged migration APK and opened Android's real installer on the Chromecast. The update succeeded over the existing app without uninstalling. A platform-only verifier checked the optimized installed release, certificate/history, non-debuggable flags, unchanged UID and retained private data/preferences. Installed APK bytes match the verified release artifact exactly. Rally then launched successfully.
- The native verifier avoids an instrumentation dependency stripped by release optimization; the test-harness issue did not represent an app crash.

Artifacts:

- [Android 9+ migration APK](/Users/shiv/Desktop/rallytv-beta/brag-output/stabilization/rally-v1.0-beta12-android-tv.apk): SHA-256 `3ae18f58b71deee7576364e1a0a4e5545a538ae4d7ef909d0720b02c3c35839d`.
- [Production legacy APK](/Users/shiv/Desktop/rallytv-beta/brag-output/stabilization/rally-v1.0-beta12-android-tv-legacy.apk): SHA-256 `c048fd6137e0452726d1502774c4e3c79f91b5de6d8eba6c2f47919de836eb3c`.
- [Installed-data verification](../../brag-output/stabilization/android-native-data-verification.log).
- [Installed Home screenshot](screenshots/android-production-home.png).

At the initial stabilization handoff no release had been published. Publication uses these same verified Android APK bytes; the tvOS distribution package increments its build number to 14. Shipping installer validation and data preservation were exercised on the Chromecast; GitHub asset checks are performed during publication. See [release process](../../RELEASE.md).
