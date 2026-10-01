
<img src="Rally_Brand_Kit/02_Wordmark/rally_wordmark_color_transparent_1024.png" alt="Rally wordmark" width="320">

Rally is a sports-first Android TV app. It combines ESPN schedules and live data with the user's Stalker/Ministra or Xtream IPTV subscription and configured Stremio addons, then presents matching streams in a cinematic, remote-first interface.

## Features

- Near-black Rally interface with the official wordmark and ambient flare, contained pill navigation, and restrained D-pad focus
- Home, Live, Schedule, Leagues, Highlights, My Rally, Search, and Settings; Search and Settings use the separate header actions
- Home combines a stadium-backed team-and-score hero, live matchup cards or recent highlights, and four Starting Soon events that expand into schedule rows as you move down to Browse by Sport
- Schedule presents date and sport filters with couch-readable event rows
- Event detail uses a stadium-backed matchup hero, Overview/Stats/Lineups/Plays/Highlights/Sources tabs, team/game/player panels, and a watchlist action
- My Rally combines followed teams, saved events, and upcoming games
- ESPN-backed scores, box scores, player data, play-by-play, scoring moments, and available highlight clips
- Automatic matching between events and IPTV channels
- Stalker/Ministra and Xtream Codes IPTV provider support
- Stremio addon stream discovery and source switching
- Split Game View pairs live video with score, status, source/quality metadata, current drive, stats, and leaders
- Game View controls include play/pause, restart, full screen, source selection, audio, captions, and Multi-View; the full-screen overlay also provides diagnostics
- Full-screen Media3 playback, audio and caption track selection, and up to four-stream Multi-View
- Multi-View supports focused or pinned audio, gap-free four-stream immersive playback, and a Player Stats tile grouped by game, including the RedZone afternoon slate with duplicate-game removal
- ESPN highlight playback when the feed provides a playable clip URL; scoring-moment timestamps do not seek IPTV broadcasts
- Searchable live channels, events, teams, leagues, and configured streams
- Local caching for channels, manifests, streams, and ESPN sports data
- Signed in-app update checks backed by GitHub Releases and Android's system installer

## Architecture

- Presentation: Jetpack Compose, Compose for TV, MVVM, lifecycle-aware StateFlow collection
- Domain: sports, stream, channel, quality, and matching models/use cases
- Data: ESPN APIs, Stalker/Ministra and Xtream Codes middleware, Stremio addon APIs, Room, encrypted preferences
- Playback: AndroidX Media3 ExoPlayer with low-memory load controls and constrained Multi-View tracks
- Dependency injection: Hilt

## Build

Requirements:

- Android Studio with JDK 17
- Android SDK 34

From the project root:

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug
```

For the shrunk and obfuscated release package:

```shell
./gradlew assembleRelease
```

Release signing is configured through the `RALLY_KEYSTORE_PATH`, `RALLY_KEYSTORE_PASSWORD`, `RALLY_KEY_ALIAS`, and `RALLY_KEY_PASSWORD` environment variables. Private signing material is never stored in Git. See [RELEASE.md](RELEASE.md) for the GitHub Releases process.

## Setup

Open the app and choose the IPTV provider in Settings. For Stalker/Ministra, enter the portal URL and MAC address supplied by the provider. For Xtream Codes, enter the server URL, username, and password supplied by the provider. Provider details are runtime settings and do not require source edits. Stremio addon manifest URLs can be added from the same screen.

HTTP portals are supported because some legacy Stalker providers do not offer TLS. The settings screen warns when a portal is unencrypted. Prefer HTTPS whenever the provider supports it because HTTP credentials and viewing traffic can be intercepted on the network.

## Performance profile

The application is tuned for memory-constrained TV devices:

- Home content appears without waiting for the IPTV catalog
- Duplicate network loads are coalesced and short-lived caches reduce repeated requests
- Local backdrops bypass the network image pipeline
- Image, player, and Multi-View buffers are bounded
- Background work is lifecycle-aware or application-scoped
- Adaptive Multi-View streams are capped at 720p and 2.5 Mbps for two streams, or 480p and 1.2 Mbps for three or four streams, with a 30 fps limit when suitable source variants are available
- Release builds enable R8 code shrinking and resource shrinking

## Verification

The redesign passes 88 unit tests and six playback/navigation tests on both the emulator and a physical Chromecast. No crashes were recorded during those checks. Home animation performance still needs work: the Chromecast recorded 21.28% delayed frames across six warmed Home transitions. See the [audit summary and screenshots](docs/qa/2026-09-30/README.md) for coverage and remaining release checks.

## Data and privacy and credits

The app does not ship IPTV credentials. Tokens are not written to logs, release HTTP logging is disabled, and request headers from third-party stream addons are allowlisted before playback. Users are responsible for using subscriptions and addons they are authorized to access.

Credit to Jacob Halladay for testing (alpha) .ipa on Apple tvOS

See the full [privacy policy](PRIVACY.md) and [content/provider disclosure](CONTENT_SOURCES.md).
