# Android in-app update correction — October 3, 2026

## Reproduced failure

Beta 10/11's shipping updater accepts every release asset ending in `.apk`.
When multiple APKs share a release tag, its version comparator keeps the first
one in GitHub's response. GitHub orders these assets by filename; Beta 12's
`rally-v1.0-beta12-android-tv-legacy.apk` appeared before the migration APK.
That production-only Android 8 package has no legacy-debug signing predecessor.

On the Android TV emulator (API 36), the real Beta 11 Settings updater downloaded
and verified this APK, then Android's on-screen installer reported a package
conflict. The system log recorded `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and
“Existing package com.shiv.spatelorts signatures do not match newer version”.
This explains why installing the separately selected migration APK through ADB
worked while the older in-app update failed. It does not establish the cause of
every manual-install failure on devices whose installed certificate is unknown.

## Correction

The published Beta 12 release now also contains `rally-android-tv.apk`, a
byte-identical copy of the Android 9+ migration download. This filename precedes
the legacy APK, so the already-installed Beta 10/11 updater selects the correct
package. Both original tagged downloads and their bytes remain available.

The compatibility and tagged migration APKs have SHA-256:
`3ae18f58b71deee7576364e1a0a4e5545a538ae4d7ef909d0720b02c3c35839d`.
The compatibility filename is also included in the release's `SHA256SUMS`;
the existing Android and tvOS checksum entries were preserved.

Release automation now publishes the same compatibility asset and checks its
digest against the tagged migration APK. It verifies the actual GitHub API
ordering and rejects a release that an older updater would resolve to a legacy,
debug, incomplete or different APK. Checks run from the workflow revision even
when rebuilding a previously tagged app. Six regression tests cover these cases.
No Android application code or version number needed changing to repair the
download selected by an already-installed updater.

## Validation

- Reproduced the failure from the original Beta 11 build through Settings and
  Android's installer before adding the compatibility download.
- After correction, the original Beta 11 and original Beta 10 builds each checked
  the real public GitHub feed, downloaded the release, opened the on-screen
  installer, and completed the update to Beta 12, version code 13.
- Both updates retained the same UID, a private sentinel file and both original
  preference-file hashes. A separate native verifier confirmed the production
  certificate, legacy signing history and non-debuggable release flags.
- All six publication-check regression tests passed. The live release check
  confirmed the checksums and compatible asset selection.
- Emulator state was saved before testing and restored afterward. Neither test
  uninstalled Rally. No change was made to the connected Chromecast.

Evidence on the validation machine is in `/tmp/rally-android-update-fix/`:
`beta11-installer-failure.png`, both installer-success screenshots, both data
verification logs and `beta10-updater-test.log`.

## User recovery

On Android 9+ with Beta 10/11, force-stop Rally and reopen it (or restart the TV),
then check for updates, download again, and confirm Android's update prompt.
This resets the older updater's in-memory ready/download selection; its Ready
state otherwise only offers Install update. Reusing the previously downloaded
legacy APK will still fail.
Keep the app installed to retain its data. Android 8 installs with the original
production certificate continue to use the explicitly labeled legacy download.
