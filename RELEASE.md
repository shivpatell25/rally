# Rally Release Process

GitHub Releases is Rally’s distribution channel for signed Android TV beta packages.

## Required repository secrets

- `RALLY_KEYSTORE_BASE64`: base64-encoded contents of the Rally release keystore
- `RALLY_KEYSTORE_PASSWORD`: keystore password
- `RALLY_KEY_ALIAS`: release alias (`rally` for the current key)
- `RALLY_KEY_PASSWORD`: private-key password
- `RALLY_LEGACY_KEYSTORE_BASE64`: the **specific recovered legacy debug keystore**
- `RALLY_LEGACY_KEYSTORE_PASSWORD`, `RALLY_LEGACY_KEY_ALIAS`, `RALLY_LEGACY_KEY_PASSWORD`

Keep both private keystores in encrypted backups. A newly generated debug key is
not compatible with the installed builds. All eight signing secrets were
provisioned in `shivpatell25/rally` on October 3, 2026, after verifying both
certificate identities. The workflow deliberately fails when
credentials are absent instead of falling back to debug signing.

## Signing identities and migration

The installed Beta 10 Chromecast and published Beta 11 hotfix APK were signed
with Android Debug, SHA-256:
`211711b801aa9c4ec006b57254ff4e8a0e623dedee4baa8ab1d1d63f9be1425b`.
The recovered original production identity (alias `rally`) is:
`79ffff57b611ec7fbbc690007196df16dfd658432dd452458c56108bae55df73`.

The authorized Android 9+ migration uses
`signing/rally-debug-to-production.lineage`. This is a public, cryptographically
verified rotation proof, not a private key. Preserve it unchanged in every future
release. Do not create a replacement key or a new lineage for each release.

- `rally-<tag>-android-tv.apk`: migration APK. Android 9+ sees the production
  signer and verifies the legacy debug predecessor. Keep this lineage on future
  releases so both existing production installs and the debug-signed population
  retain update compatibility. The APK is optimized and non-debuggable.
- `rally-<tag>-android-tv-legacy.apk`: production-only APK for Android 8/8.1
  installs that already have the original production certificate. Older Android
  cannot perform this signing migration. A debug-signed Android 8 installation
  cannot be migrated to production while preserving in-place update compatibility.

The updater chooses the appropriate suffix for the device API, pins the current
production signer, checks the verified history against the installed identity,
and requires a higher version code. `com.shiv.spatelorts` remains unchanged.

For local packaging, build `assembleRelease` with the four production signing
environment variables, then run `scripts/sign_android_release.py --migration
--output rally-<tag>-android-tv.apk` with both sets of credentials. Run it without
`--migration` for the legacy artifact. The script rejects debuggable APKs and
checks certificates for Android 9, Android 14 and Android 8. Never publish the
intermediate Gradle APK instead of these verified final artifacts.

## Publish

1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Merge a green Android CI build to `main`.
3. Create a GitHub prerelease and tag such as `v1.0-beta2`.
4. Upload both verified artifacts with the exact suffixes above, or manually run
   the signed-release workflow for that tag after configuring all repository secrets.
5. Verify the APK signature and SHA-256 digest before announcing the release.

Never commit a keystore, password, provider credential, local properties file, or APK.
