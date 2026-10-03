# tvOS Stremio source discovery verification

Verified October 3, 2026, on the tvOS 27 simulator. The physical tvOS Release configuration also builds successfully with signing disabled.

Sports Streams' public configuration returned **two native HLS candidates** for North Carolina–Notre Dame through the shipping Stremio repository. This validates discovery and source conversion; playback on the owner's Apple TV and any private addon configuration have not been exercised.

The college-football failure had two concrete causes: ESPN's full team names with mascots did not match the addon's school-name listings, and the repository discarded HTTP/HLS streams marked `notWebReady`. That flag describes browser readiness rather than native AVPlayer eligibility. Locked upgrade placeholders are excluded.

The resolver now preserves catalog content types and configured URL paths/queries, supports search-only catalogs, searches both teams, and fetches relevant catalogs concurrently. Completed sources survive a slow catalog's timeout. Failure diagnostics record stage/status without URLs or credentials. The source screens expose Refresh sources, distinguish browser-only results, and reload when addon settings change.

Validation:

- 20 existing native data/playback regressions passed.
- 8 addon tests passed, including live Sports Streams NCAAF discovery; deterministic cases cover custom types, description matching, school names, native headers, search-only catalogs, encoded IDs, configured query preservation, isolated addon failure, timeout preservation, and locked/browser-only sources.
- 1 remote UI regression passed: Refresh sources is reachable and selectable in Event Sources and the Game View source picker, and Back returns to Game View.
- Debug simulator and physical-target Release builds passed; the latter is unsigned.

Result bundles on the verification machine:

- `/tmp/rally-tvos-stremio-all-tests.xcresult`
- `/tmp/rally-tvos-stremio-live-remote.xcresult`

The opt-in live integration test requires `RALLY_LIVE_STREMIO_QA=1`. Regular test runs skip it to avoid depending on a third-party service or a currently available game.
