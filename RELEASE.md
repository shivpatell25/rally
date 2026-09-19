# Rally Release Process

GitHub Releases is Rally’s distribution channel for signed Android TV beta packages.

## Required repository secrets

- `RALLY_KEYSTORE_BASE64`: base64-encoded contents of the Rally release keystore
- `RALLY_KEYSTORE_PASSWORD`: keystore password
- `RALLY_KEY_ALIAS`: release alias (`rally` for the current key)
- `RALLY_KEY_PASSWORD`: private-key password

The keystore itself must also be kept in a separate encrypted backup. Every update to the installed app must be signed by this same key.

## Publish

1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Merge a green Android CI build to `main`.
3. Create a GitHub prerelease and tag such as `v1.0-beta2`.
4. The tag workflow builds, tests, signs, optimizes, and uploads `rally-v1.0-beta2.apk` to that release.
5. Verify the APK signature and SHA-256 digest before announcing the release.

Never commit a keystore, password, provider credential, local properties file, or APK.
