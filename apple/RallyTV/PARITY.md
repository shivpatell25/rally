# Rally tvOS parity inventory

Source: the current Android application in this repository, including Multiview and the player stats tile. The "Initial tvOS state" column records the inventory made before this port. Every listed route now has an implementation; the validation record below distinguishes simulator verification from remaining physical-device and account checks.

## Specification precedence

Current Android behavior and the user's latest changes take precedence over older examples in the port prompt. In particular, preserve Highlights in navigation; the compact-to-guide Home transition; three recent highlights when no games are live; centered live scores with team-color artwork; Restart/Fullscreen/Game View controls; and the game-grouped Multiview stats tile. Do not restore previously removed Recommended/Featured rails or duplicate schedule/Live Now sections. Event Overview uses player stats and win probability; team injuries remain available in the Team hub. Preserve the exact official wordmark, r mark, current background flare, league marks, and Inter font assets.

## Execution and acceptance

1. Inventory routes, actions, data, settings and platform differences (this document).
2. Complete typed navigation, shared shell, assets, focus styles and persistent settings.
3. Home, then Schedule, Event and Player/Game View.
4. Live/channel browser, Leagues/Team hub, My Rally, Highlights, Search and Settings.
5. Multiview video geometry/audio/lifecycle and deduplicated player stats/RedZone slate.
6. Build Debug and Release. Exercise remote navigation, playback, sources and data in the Apple TV simulator. Capture paired Android/tvOS screenshots and fix visible composition differences.

An item is complete only after implementation and meaningful verification. Compilation alone is not parity. Fixture data is confined to tests; shipping screens must use real repositories. Preserve the user's existing Android changes and provider configuration. No physical Chromecast operations are part of this port.

## Screens, states and controls

| Android source/route | tvOS parity requirements | Initial tvOS state |
|---|---|---|
| MainActivity / global shell | Home, Live, Schedule, Leagues, Highlights, My Rally, Search, Settings; stable header; typed deep links; back behavior; focus restoration; Home returns to top | Partial shell; missing Schedule and persistent destination chrome |
| onboarding | Official branding, setup explanation, Continue to setup, initial focus | Missing |
| home | Venue hero/status/score/context/actions; three-card paged live rail; no-live three-highlight fallback; four compact upcoming events; Down past preview smoothly expands same events to guide rows; Live remains above guide; reminder bells; See All/See Full Schedule; symmetric sport rail including NCAAF/NCAAB/MLS/Soccer; Home reset; loading/error/retry; live alerts | Partial, older composition |
| schedule | Feed-window date selection; All leagues/league filter; All/Live/Upcoming/Final filters; clear filters; refresh; correctly ordered events; local time; status, team names/logos, score, venue; event navigation; loading/error/empty states | Missing |
| live-games | Current live/halftime events; landscape cards; Browse Live TV; no-live message; event navigation | Missing |
| iptv | Configured provider authentication; category filtering; channel search; logos/numbers; channel playback; retry/configuration errors; empty states | Repository exists, screen missing |
| leagues | Enabled league directory; league marks/labels; Games/Standings/Playoffs; published records/conference seeds; NFL RedZone action; team navigation where present; retry/empty | Missing |
| team/{league}/{id} | Overview/Games/Roster/Injuries; follow/unfollow; record/logo; event navigation; category-specific tables and availability; back/focus | Missing |
| watchlist / My Rally | Followed teams; scoped league+team IDs; team schedule; saved event rail; Manage Teams; removal/follow actions; empty/error/retry; no cross-sport ID collisions | Missing |
| search | Query events, teams, leagues, channels; alias-aware league search; indexing/loading; keyboard input; grouped results and corresponding destinations; empty/error | Missing |
| highlights | Recent game clips, metadata, league filtering, real thumbnails, play clip/open game, refresh/loading/error/empty | Missing |
| event/{id} | Venue hero; league/status/score/venue; Watch Live; source selection; watchlist toggle; Overview/Stats/Lineups/Plays/Sources/Highlights; game info; team form; win probability; labeled player stats/photos; complete team/player tables and play list; sources; real highlights; live summary refresh; loading/error/retry | Partial |
| player/{target}?eventId&clipTitle | Resolve direct URL/channel; candidate ranking/preflight/fallback; headers; AVPlayer lifecycle; retry/errors; game view and fullscreen; score/status/source/quality/r mark; Play/Pause, Restart, Fullscreen/Game View, Multiview, Audio, Captions, Pick Source, Diagnostics; live edge; allowed seeking; clip titles | State/profile types only |
| game view panels | Focusable Stats/Plays/Lineups/Sources tabs; team leaders/photos; team comparison; current drive; latest play; complete player/plays data; Key Moments/Other Live Games; clip playback and return to live; alternate game/fullscreen/multiview actions | Missing |
| multiview | Up to four tiles; stable stream identities; 16:9 split/focus/grid layouts; immersive zero internal gaps; audio follows focused stream or pins chosen stream; audio focus/lifecycle; add/change/remove/swap/promote; source picker/retry/fullscreen; compare; return/back; no truncated controls | Missing |
| multiview stats | Add stats instead of stream; game-grouped all published player tables; photos/labeled values/player detail; deduplicate games across duplicate feeds; RedZone day's afternoon NFL slate independent of enabled filters; refresh/error/empty; retain audio while focusing stats; remove tile | Missing |
| score saver | Five-minute idle view; current scores/upcoming events; shared background; clock; any remote action returns; disabled during playback | Missing |

## Settings inventory

| Domain | Options/actions to preserve |
|---|---|
| Sources | Stalker/Ministra, Xtream Codes or M3U/M3U8; portal URL/MAC/serial/device ID; Xtream server/user/password; playlist URL/name or catalog file import; optional identity fields; save/apply validation; provider diagnostics; HTTP-provider explanation |
| Addons | Custom Stremio manifest URL; installed addons; add/remove/reset defaults; per-addon diagnostics; resolve direct playable streams and rank candidates |
| Sports | Enabled leagues (retain at least one); favorite sports; move sport up/down; persisted canonical ordering |
| My Rally | Followed teams; add team from catalog/search; remove; saved events/reminders; league-scoped identities |
| Alerts | Live game alerts; RedZone alerts; deduplicated notices and event navigation |
| Viewing | Low latency; reduce motion; high contrast focus; larger text; spoken score descriptions; score saver; audio normalization; adaptive quality |
| Support | Run/clear diagnostics; export support report; export/import personalization excluding credentials; check releases; privacy/release links; version information |
| Updates | Android APK download/install must map to native Apple distribution behavior. tvOS cannot install Android APKs; show release information and supported App Store/TestFlight destination rather than a nonfunctional install button. Record the platform difference explicitly. |

## Shared data and platform mapping

| Android implementation | tvOS implementation |
|---|---|
| EspnApi/EspnRepositoryImpl | Same league endpoints and scoreboards, summary enrichment, teams/roster/injuries, standings and published playoff seeds; Codable/URLSession; bounded parallel requests; live refresh and disk fallback |
| Stalker API/auth/channel store | Preserve handshake, identity parameters, authorization/cookies, category/channel pagination and create_link; Keychain-backed credentials |
| Xtream API | Preserve player_api authentication/categories/live streams and provider-specific playback URLs; choose native-playable HLS where supported |
| M3U/M3U8 catalog | Preserve extended-M3U metadata/groups/logos, relative URLs, stable identities, duplicate-feed handling and required headers. HLS remains one channel. Native local transfer replaces Android's document picker; private catalog files are excluded from backup. Catalog refresh expires after 15 minutes; limits match Android. |
| Stremio manifests/catalog/streams | Preserve installed addons, sports catalog search, direct stream compatibility, stream headers, descriptions and source ranking |
| Matcher/SelectBestStream/StreamHealth | Preserve team/broadcast matching, quality ranking, stream preflight and fallback ordering |
| PreferencesManager/Room/cache | UserDefaults for personalization; Keychain for secrets; disk caches for schedules/channels; shared typed models |
| ExoPlayer | AVPlayer/AVPlayerLayer and native media selection; isolate compatibility handling for providers that expose codecs/containers unsupported by AVFoundation; no silent missing actions |
| Compose FocusRequester/key handlers | SwiftUI focus state/sections/default focus; Siri Remote movement/play-pause/Menu; explicit restoration after sheets/routes; stable item identities |
| Android backup document picker | Native tvOS-supported transfer/import presentation with useful status; preserve backup format and exclude credentials |

## Required verification record

- [x] Debug and Release compile for tvOS; native simulator signing is required for Keychain checks.
- [x] Every route above has an actual implementation; the UI suite opens the navigation destinations and all Event/Settings tabs.
- [x] Actions have implemented destinations or effects, with source errors and unavailable source capabilities presented explicitly.
- [x] Loading/error/retry controls are implemented; no-live fallback, channel empty/configuration states, overlay dismissal and restored focus were inspected. Provider-specific failures need configured-account testing.
- [x] Home top/guide, three-card paging, four-event compact-to-list transition, Home reset, and header actions exercised using remote input.
- [x] Home, Schedule, Event and Game View screenshots compared with Android and the approved mockups. The user's later refinements explain intentional differences from the original mockups.
- [x] Real HLS playback, headers, pause/restart/seek, fullscreen, audio/captions and source switching exercised with Apple sample streams; published ESPN video also plays through shipping repositories.
- [x] Multiview 2/3/4 tiles, immersive geometry, focused/pinned audio, stats tiles and return from fullscreen tested.
- [x] RedZone date/slate and duplicate-event behavior covered by regressions. Real provider RedZone EPG remains an account-dependent device check.
- [x] Preference persistence, Keychain reads/writes, backup credential exclusion, actual local backup/import, token rejection and listener shutdown verified. Provider changes rebuild dependent services.
- [x] M3U URL loading, HLS variant preservation, file upload/import, channel browsing, required headers and AVPlayer playback verified; malformed catalogs and unsafe header values rejected.
- [x] Remote Menu/back and focus restoration tested across details, source selection, controls and overlays.
- [x] External dependencies and physically unverified behavior documented in [QA](QA/README.md) and [README](README.md).

## Validation scope

The simulator suite and screenshot comparisons verify the implemented experience;
they do not establish bit-for-bit image parity or physical Apple TV performance.
Fixtures are Debug-only and use public HLS for reproducible playback checks.
An additional opt-in walkthrough exercises real ESPN dates, venue images,
published player tables, schedules, Home highlights and actual published video.
Minute-precision dates, nested baseball team stats and `type`-named player
categories have regression coverage because they differed from the initial
synthetic fixtures.

Remaining release gates are owner signing/distribution, configured Stalker and
Xtream accounts/addons, physical Siri Remote and decoder capacity, and live
provider DVR/quality/caption availability. No credentials or provider accounts
are included in the tvOS application.
