# Rally for Apple TV

Native tvOS port of the Android app in this repository. SwiftUI owns navigation,
focus and presentation. AVPlayer handles standard HLS; KSPlayer's FFmpeg/Metal
engine handles sources identified as 4K/HDR and file-based media.
The current Android composition and the user's later design refinements take
precedence over older mockup examples. See [the parity inventory](PARITY.md).

## Open and run

Open `RallyTV.xcodeproj`, select the **RallyTV** scheme and an Apple TV simulator,
then Run. The deployment target is tvOS 17. The implementation was built and
tested using Xcode 27 and the tvOS 27 simulator.

For a physical Apple TV, choose your Apple development team in Signing &
Capabilities, set a bundle identifier available to that team, and run on the
paired device. No signing identity, App Store application, or TestFlight
destination is configured by this repository.

```sh
xcodebuild build -project RallyTV.xcodeproj -scheme RallyTV \
  -destination 'generic/platform=tvOS Simulator' \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=-

xcodebuild test -project RallyTV.xcodeproj -scheme RallyTV \
  -destination 'platform=tvOS Simulator,name=RallyTV-Test' \
  -parallel-testing-enabled NO -collect-test-diagnostics never \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=-
```

Keep simulator ad-hoc signing enabled: disabling signing also prevents native
Keychain writes. The physical-device compile check can use
`CODE_SIGNING_ALLOWED=NO`; running on an Apple TV requires the owner's signing.

Scores, schedules, team information and published highlights load from the same
ESPN endpoints as Android. Connect your Stalker/Ministra, Xtream or M3U provider,
or add Stremio manifest URLs, in **Settings** to obtain live TV sources. Rally does
not bundle a provider account or invent playable sports streams.

## Product behavior

- Persistent top navigation includes Highlights, Search and Settings.
- Home presents one three-card media rail. When no games are live, real recent
  highlights replace it. Right past the third card advances the group; Down past
  the four upcoming events animates the same preview into the guide composition.
  Home resets this view. There are no duplicate Live Now or schedule sections.
- Event details include Overview, Stats, Lineups, Plays, Sources and Highlights.
  Published player categories and rows are preserved, with player details and
  following. Overview uses player leaders and win probability; team injuries
  remain in the Team hub.
- Game View and fullscreen share Pause/Play, Restart, source, audio, captions,
  quality caps and Multiview actions. Fullscreen adds diagnostics and Game View
  for associated events; unmatched linear channels open directly in fullscreen.
  The selected quality cap survives source changes. Restart seeks to
  the beginning of the source's available range; seeking and live edge depend on
  the source. Highlight titles are retained and live games can be reopened. Returning
  from an inactive scene resumes playback while preserving an intentional pause.
- Multiview supports up to four streams or a player-stats tile, split/focus/grid
  compositions, immersive viewing, source changes, swaps, promotion and removal.
  Audio follows the focused stream or stays pinned. Stats are grouped by event,
  deduplicated across duplicate feeds, and include the NFL afternoon slate when
  a RedZone stream is selected. Selected stream URLs and required headers survive
  entry. Returning from fullscreen restores existing tiles.
- Schedule supports dates, leagues, event states, reminders and event navigation.
  My Rally includes followed teams and saved events. Settings include source and
  addon diagnostics, league ordering, favorites, alerts and accessibility options.
- M3U/M3U8 supports channel catalogs and single HLS URLs, groups, relative logos
  and stream URLs, stable channel identities, VLC options and URL request headers.
  A single HLS manifest remains one channel with its original variants and tracks.
  **Settings → Sources → M3U / M3U8 → Import Playlist File** opens a local transfer
  panel for a phone or computer; select **Done** after importing to apply it.

## Architecture

| Directory | Responsibility |
|---|---|
| `App` | Composition root, typed destinations, navigation, alerts, score saver |
| `DesignSystem` | Official assets, display metrics, focus, shared TV components |
| `Features` | Home, Schedule, Event, Game View, Multiview and all other routes |
| `Models`, `Networking`, `Repositories` | Codable feed parsing, bounded requests, caching |
| `IPTV`, `Addons` | Provider authentication/catalogs, addon discovery and source resolution |
| `Player` | AVPlayer and KSPlayer lifecycles, guarded header proxy, track selection, Now Playing |
| `Storage`, `Services` | Keychain, preferences, caches, matching, ranking, diagnostics |
| `Tests`, `UITests` | Data/playback regressions and actual Siri Remote UI navigation |

`gen_project.py` regenerates the Xcode project deterministically after adding or
removing sources. `package_brand_assets.swift` packages the unchanged official
Rally mark, wordmark and background into native layered app icons and top-shelf
assets; it does not generate new branding. Apple's format is documented in
[Brand Assets](https://developer.apple.com/library/archive/documentation/Xcode/Reference/xcode_ref-Asset_Catalog_Format/BrandAssetsType.html).

## Networking and preferences

HTTPS is the default. HTTP API/media requests are allowed only for exact HTTP
hosts explicitly configured in Sources/Addons, plus loopback for local playback
and transfer. A selected channel from a configured M3U catalog temporarily permits
its exact HTTP media host, and releases that permission when playback stops.
Redirects cannot add HTTP hosts; credentials are stripped on cross-host
HTTPS redirects. HTTP media and header-dependent HLS use a guarded local proxy,
including playlists, segments, keys and range requests. Provider passwords,
authorization tokens, MAC identity and playlist addresses are stored in Keychain.

Preference transfer starts only when its Settings panel opens. Its random-token
local address supports Android's schema-1 preferences, backup downloads and
redacted support reports. It also accepts M3U catalog files, validated before
application, with a 16 MB / 20,000-channel limit. Imported catalogs stay in Rally's
private directory and are excluded from backups. Backups exclude provider
credentials and addresses. Close the panel to stop accepting transfers.

## Platform differences and remaining device checks

- AVPlayer supports native Apple media formats. Providers exposing only formats
  or codecs unavailable to AVFoundation need a compatible HLS source. HTML watch
  pages and YouTube links are not passed to AVPlayer as media streams.
- Audio normalization maps to Apple TV's native **Reduce Loud Sounds** guidance;
  AVPlayer does not provide Android's custom live-audio limiter API.
- Android APK installation maps to release information and native distribution.
  App Store/TestFlight publishing requires the owner's Apple configuration.
- Four-stream hardware decoder capacity, physical Siri Remote behavior, provider
  credentials, regional availability and real RedZone EPG require testing on the
  target Apple TV with configured accounts. Simulator checks do not prove those.

## Validation

The UI suite uses opt-in Debug-only `--fixtures` with deterministic scores and
real [Apple sample HLS](https://developer.apple.com/streaming/examples/). These
fixtures are compiled out of Release. Shipping repositories are exercised
separately with real ESPN data and published clips. Screenshots and the exact
verification record are in [QA](QA/README.md).
Set `TEST_RUNNER_RALLY_LIVE_QA=1` when running the test command to include that
network-dependent shipping-data walkthrough.

## KSPlayer beta licensing

The KSPlayer beta release uses KSPlayer 2.3.4 and FFmpegKit 6.1.4 under GPLv3.
This beta's Rally source is distributed under GPLv3; see the repository's
`LICENSE` file. Third-party notices and license texts are bundled in Settings →
Playback Licenses. This release is for testing and still needs 4K/HDR validation
on a physical Apple TV.
