
<img src="Rally_Brand_Kit/02_Wordmark/rally_wordmark_color_transparent_1024.png" alt="Rally wordmark" width="320">

Rally is a sports-first Android TV app. It combines ESPN schedules and live data with the user's Stalker/Ministra IPTV subscription and configured Stremio addons, then presents matching streams in a cinematic, remote-first interface.

## Features

- Live and upcoming NFL, NBA, MLB, NHL, soccer, and college events
- Automatic matching between events and IPTV channels
- Stremio addon stream discovery and source switching
- Full-screen playback, statistics Game View, and up to four-stream Multi-View
- Searchable IPTV channel browser
- Local caching for channels, manifests, streams, and sports data
- D-pad navigation designed for Android TV and Google TV
- Signed in-app update checks backed by GitHub Releases and Android's system installer

## Architecture

- Presentation: Jetpack Compose, Compose for TV, MVVM, lifecycle-aware StateFlow collection
- Domain: sports, stream, channel, quality, and matching models/use cases
- Data: ESPN APIs, Stalker/Ministra middleware, Stremio addon APIs, Room, encrypted preferences
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

Open the app and enter the portal URL and MAC address supplied by the IPTV provider. Provider details are runtime settings and do not require source edits. Stremio addon manifest URLs can be added from the same screen.

HTTP portals are supported because some legacy Stalker providers do not offer TLS. The settings screen warns when a portal is unencrypted. Prefer HTTPS whenever the provider supports it because HTTP credentials and viewing traffic can be intercepted on the network.

## Performance profile

The application is tuned for memory-constrained TV devices:

- Home content appears without waiting for the IPTV catalog
- Duplicate network loads are coalesced and short-lived caches reduce repeated requests
- Local backdrops bypass the network image pipeline
- Image, player, and Multi-View buffers are bounded
- Background work is lifecycle-aware or application-scoped
- Multi-View streams are capped at 720p, 30 fps, and 2.5 Mbps per slot
- Release builds enable R8 code shrinking and resource shrinking

## Data and privacy and credits

The app does not ship IPTV credentials. Tokens are not written to logs, release HTTP logging is disabled, and request headers from third-party stream addons are allowlisted before playback. Users are responsible for using subscriptions and addons they are authorized to access.

Credit to Jacob Halladay for testing (alpha) .ipa on Apple tvOS

See the full [privacy policy](PRIVACY.md) and [content/provider disclosure](CONTENT_SOURCES.md).
