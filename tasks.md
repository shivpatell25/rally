# Implementation status

## Completed stabilization pass

- [x] Preserve backdrop-led home presentation and portrait game cards
- [x] Apply a consistent Apple TV+-inspired dark, glass, typography, and focus system
- [x] Reduce focus scaling and animation cost for low-end TV hardware
- [x] Render bundled backdrops directly instead of through the image network pipeline
- [x] Reduce image memory and disk cache pressure
- [x] Make StateFlow collection lifecycle-aware across primary screens
- [x] Publish sports content before the IPTV catalog finishes loading
- [x] Coalesce sports, channel, authentication, and addon requests
- [x] Add sports refresh, event live-update polling, and stale-data fallback
- [x] Scope cached IPTV channels to the configured portal and MAC address
- [x] Move background refresh work into an application-scoped coroutine
- [x] Cache Stremio manifests and event results; limit concurrent addon requests
- [x] Implement Stremio stream search and sanitize playback headers
- [x] Cancel stale player, metadata, search, and filtering work
- [x] Bound single-player memory and enforce low-resource Multi-View tracks/buffers
- [x] Replace guessed 4K/HDR claims with evidence-based stream quality labels
- [x] Validate and normalize portal, MAC, and addon settings
- [x] Remove token logging and disable release HTTP logging
- [x] Remove dormant iframe scraping code that could not produce playable Media3 streams
- [x] Use a real TV banner and production-safe unsigned release configuration
- [x] Add URL normalization and broadcast-quality regression tests
- [x] Pass unit tests, Android lint, debug packaging, and minified release packaging

## Verification

```text
testDebugUnitTest  PASS
lintDebug          PASS
assembleDebug      PASS
assembleRelease    PASS (unsigned)
```

Runtime frame timing still needs to be profiled on the target Chromecast because no Android device is connected to this development environment.
