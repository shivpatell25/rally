# Rally Privacy Policy

Last updated: September 19, 2026

Rally is an Android TV sports interface. It does not operate an IPTV service, provide television subscriptions, or bundle streaming sources.

## Data stored on the TV

Rally stores app preferences, favorites, provider configuration, and playback health locally. IPTV credentials and related provider identifiers are held in Android encrypted preferences. They are excluded from Android cloud backup and device transfer.

Rally keeps a small local diagnostic history containing crash categories, playback recovery events, memory-pressure events, and source-selection reasons. Diagnostic exports automatically redact URLs, authorization values, tokens, MAC addresses, credentials, and device identifiers. Reports are never uploaded automatically.

## Network connections

Rally connects only as needed to:

- public sports schedule, score, statistic, artwork, and highlight services;
- IPTV portals configured by the user;
- Stremio addon manifests and streams configured by the user; and
- GitHub pages opened explicitly from the Support screen.

When the user selects **Check for updates**, Rally requests the public release list for `shivpatell25/rally` from GitHub. Selecting **Download update** retrieves the chosen APK from GitHub Releases. Rally verifies the package name, version, checksum when available, and Rally release-signing certificate before opening Android's system installer. Updates are never installed silently.

Rally has no advertising SDK, analytics SDK, account system, or background telemetry service. Release builds disable HTTP body logging.

## Backups

The in-app preferences backup includes sports order, favorites, alerts, playback preferences, and accessibility settings. It excludes IPTV addresses, addon addresses, MAC addresses, serial numbers, device IDs, authentication tokens, and playback URLs.

## User control

Users can clear local diagnostic records from Settings → Support. Android’s app-data controls or uninstalling Rally remove locally stored data. Provider configuration can be removed from Settings → Sources.

## Children

Rally is not directed to children and does not knowingly collect personal information from children.

## Contact

Privacy questions and security reports can be submitted through the project’s [GitHub issue tracker](https://github.com/shivpatell25/rally/issues).
