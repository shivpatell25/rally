# Rally tvOS verification

Verified on **October 1, 2026**, using Xcode 27 and the tvOS 27 simulator
(Apple TV 4K, third generation, 1920 × 1080). The deployment target is tvOS 17.

Open [the visual comparison](comparison.html) to compare Android, tvOS and
the original approved mockups. The latest Android composition and the user's
subsequent refinements take precedence over older mockup content.

## Build and test record

| Gate | Result |
|---|---|
| Complete native acceptance suite | 27 passed: 9 remote UI tests + 18 data/playback tests; no skips or failures |
| Foreground restoration follow-up | 5 passed, including published ESPN playback, sample HLS, user pause preservation and multiview return |
| Final control follow-up | 2 passed: fullscreen seek/quality focus, sustained seeking beyond the idle timeout, idle hiding/restoration, quality preservation on source changes, retained Multiview source, and Game View controls |
| Debug simulator | Builds with native ad-hoc signing; native Keychain verified |
| Release, physical tvOS target | Compiles successfully; unsigned build, not a device deployment |
| Release, simulator | Builds and signs successfully |
| Shipping data | Real ESPN Home, schedule, venue artwork, event/player tables and published HLS video exercised |
| Release content | Debug fixture flags/synthetic event markers absent; official assets retained |

Machine-readable acceptance results are in [test-results.json](test-results.json).
The final control follow-up is recorded in [control-results.json](control-results.json).
Capture provenance is in [captures.json](captures.json).

The full acceptance result bundle is available on this machine at
`/tmp/rally-tvos-native-acceptance.xcresult`.
The final control result bundle is `/tmp/rally-tvos-playback-final.xcresult`.
The final fixes explicitly connect fullscreen transport controls, seeking and
quality selection in remote focus order, and reset the idle timer when focus,
seeking or playback menus change.

## Coverage

### Settings input follow-up — October 2, 2026

Direct Down navigation from Addons previously skipped the manifest input because
the input began to the right of the tab and action buttons. Each settings input
row now defines a native focus section spanning its label and text box, including
the secure password field. URL fields use the native URL keyboard without
autocorrection or capitalization.

The remote test opened all 11 settings inputs, verified Back restores focus and
preserves values, and entered text with Done in the manifest field. Release also
builds successfully. See [settings-input-results.json](settings-input-results.json)
and `/tmp/rally-settings-entry-all.xcresult`.

### Settings actions follow-up — October 2, 2026

The input-row fix alone left compact action buttons outside the input's vertical
focus path. Action rows now also span a native focus section, while the visible
buttons retain their existing size. This covers Add Addon, Reset Addons, installed
addon actions, provider actions, and playlist import.

Remote regressions cover direct Down from Addons to Manifest URL to Add Addon,
Select validation, Up back to the input, keyboard dismissal, Down to Reset and Up
to Add. Provider regressions cover input-to-action navigation for Stalker, Xtream,
and M3U, every provider action, and return to the last input.
Both action regressions passed on the simulator; see
[settings-action-results.json](settings-action-results.json). The 11-input
keyboard regression also passed with these action-row changes, and the tvOS
Release build succeeded.

### Main acceptance coverage

- **Navigation:** Home, Live, Schedule, Leagues/league/team hubs, Highlights,
  My Rally, Search, Settings, all Event tabs and all Settings tabs.
- **Home:** persistent navigation, cohesive venue hero, three-card paging,
  four compact upcoming events, expansion into the guide, reminder actions,
  See All/See Full Schedule, Home reset and symmetric sport cards including NCAAB.
  Three recent highlights replace live games in both compositions when none are live.
- **Player:** real HLS decoding, 16:9 bounds, pause/restart/seek, fullscreen,
  source changes with required headers, audio/caption selection, quality caps,
  diagnostics, compact stats bounds and directional access to all stats cards/tabs.
  Returning from an inactive scene resumes playback while preserving a user pause.
  Unmatched linear channels open fullscreen, as on Android.
- **Multiview:** two/three/four-stream bounds, immersive edges, native remote
  management, focused/pinned audio state, grouped player stats, RedZone slate
  selection and deduplication. Selected sources and headers survive entry;
  existing tiles resume when returning from fullscreen.
- **Sources and storage:** M3U URL/HLS loading, actual local catalog file import,
  groups/logos/relative URLs, header sanitization, stable IDs, channel browsing,
  source changes and channel playback. Native Keychain, backup credential
  exclusion, local import, invalid-token rejection and transfer shutdown verified.
- **Real feed regressions:** minute-precision timestamps, nested baseball team
  statistics, category names published as `type`, team identity matching,
  player tables/headshots, win probability and broadcast data.

## Screenshot guide

| View | Captures |
|---|---|
| Shipping Home | [Top](real-home-top.png), [guide](real-home-guide.png) |
| Fixture Home | [Top](home-top.png), [guide](home-guide.png), [no live](home-no-live.png), [no-live guide](home-no-live-guide.png) |
| Schedule | [Shipping](real-schedule.png), [fixture](schedule.png) |
| Event | [Shipping Overview](real-event-overview.png), [shipping Stats](real-event-stats.png); fixture Overview/Stats/Lineups/Plays/Sources/Highlights |
| Player | [Published ESPN video](real-highlight-playing.png), [fullscreen](real-highlight-fullscreen.png), [fixture game view](game-view.png), [other live games](player-other-games.png), audio/captions/diagnostics |
| Multiview | [Four streams](multiview-4.png), [immersive](multiview-4-immersive.png), [stats tile](multiview-stats.png); two/three-stream captures also included |
| M3U | [Settings](settings-m3u.png), [import](settings-m3u-import.png), [channels](iptv-m3u.png), [playback](m3u-channel-playing.png), [retained stream in Multiview](m3u-channel-multiview.png) |
| Other routes | Live, Leagues, NFL league/team hubs, Highlights, My Rally, Search and each Settings tab |

`real-*` captures use shipping repositories and published media. Other tvOS
captures use opt-in Debug fixtures with deterministic game metadata and real
Apple sample HLS. Repeated Chiefs/Ravens games and sample-video test patterns
belong to these fixtures; they are not shipping fallback content.
Fixture alerts may temporarily overlay a Home capture. Scores and dates differ
between Android and tvOS captures because the feeds were captured at different times.

The official wordmark and background are byte-identical to Android's assets.
Native display metrics, focus lift, font rendering and Apple media controls mean
these checks establish close composition parity, not identical pixels.

## Checks requiring the owner's hardware or accounts

Simulator verification cannot establish physical Siri Remote behavior,
four-stream hardware decoder capacity, provider-specific DVR windows, regional
stream availability or real RedZone EPG. Stalker/Xtream credentials and private
addons were not supplied; those integrations are implemented but need configured
account checks. AVFoundation requires compatible media/codec sources.

Physical deployment requires an Apple development team and paired Apple TV.
App Store/TestFlight distribution is not configured or published. Audio
normalization maps to Apple TV's native Reduce Loud Sounds setting. These are
remaining release gates; this record does not certify production readiness.
