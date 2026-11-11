# Publish this fork

Repository: https://github.com/Duffed/tidal-app. No upstream pull request is required.
Keep the original LICENSE and THIRD_PARTY_NOTICES, including the Smart Shuffle license.

## Signed releases

The fork Actions secrets `RELEASE_KEYSTORE_BASE64` and `RELEASE_KEYSTORE_PASSWORD` are configured. The checked-in certificate fingerprint identifies this fork's key.

1. Open the fork's Actions tab and select **Release**.
2. Choose **Run workflow**, branch `main`, and a new version (for example `0.7.1`; existing tags cannot be reused).
3. The workflow runs checks, builds and verifies the signed APK, commits the version/changelog, tags and publishes in this fork. Versions starting with `0.` are marked as prereleases.
4. Download `opentune-<version>.apk` from the release assets. It is a release build without a debug suffix.

The in-app update checker points to Duffed/tidal-app. A public release has not yet been published by this setup. The local signed build and its certificate have been verified; the hosted Release workflow has not yet been run.

## Preserve the signing key

Local key: `%USERPROFILE%\.opentune-signing\duffed-release.p12`. The password is protected by Windows DPAPI in `password.dpapi`, tied to this Windows user/profile. Keep a secure independent backup of both the key and its recovered password in a password manager; copying the DPAPI file alone to another PC is not a recovery strategy. GitHub cannot show saved Actions secret values again. Never commit signing keys or passwords, and never replace the key for an update.

To build locally with JDK 21 and ANDROID_HOME set, run `./scripts/build-release.ps1`. It uses the existing key and creates `opentune-<version>.apk` in the release output directory.

The release application ID matches upstream. A differently signed upstream release cannot be updated in place. The debug app uses a separate application ID; switching to release does not automatically migrate its data. Export a backup first.
