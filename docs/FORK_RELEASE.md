# Publish this fork

Repository: https://github.com/Duffed/tidal-app. No upstream pull request is required.
Keep the original LICENSE and THIRD_PARTY_NOTICES, including the Smart Shuffle license.

## Signed releases

The fork Actions secrets `RELEASE_KEYSTORE_BASE64` and `RELEASE_KEYSTORE_PASSWORD` are configured. The checked-in certificate fingerprint identifies this fork's key.

1. Prepare a new version on a branch with `python scripts/release.py prepare <version>`; commit VERSION, CHANGELOG.md and fastlane, test and merge into the fork's main.
2. Open the fork's Actions tab, select **Release**, and run it on `main` with that same version. Dry run builds/tests without creating a release.
3. The workflow checks the prepared version, signs the APK with this fork's key, and tests installation over the previous published fork release on Android 8 and 16. Without a previous release, only fresh installation can be checked.
4. Download and test the APK from the resulting draft, then publish the draft. Versions starting with `0.` are marked as prereleases.

The in-app update checker points to Duffed/tidal-app. Release 0.7.1 was published from a locally signed build because the hosted workflow failed downloading dependencies before its Android 36 tests. Subsequent releases use the draft/upgrade workflow. No main-branch protection setting is assumed by this guide.

## Preserve the signing key

Local key: `%USERPROFILE%\.opentune-signing\duffed-release.p12`. The password is protected by Windows DPAPI in `password.dpapi`, tied to this Windows user/profile. Keep a secure independent backup of both the key and its recovered password in a password manager; copying the DPAPI file alone to another PC is not a recovery strategy. GitHub cannot show saved Actions secret values again. Never commit signing keys or passwords, and never replace the key for an update.

To build locally with JDK 21 and ANDROID_HOME set, run `./scripts/build-release.ps1`. It uses the existing key and creates `opentune-<version>.apk` in the release output directory.

The release application ID matches upstream. A differently signed upstream release cannot be updated in place. The debug app uses a separate application ID; switching to release does not automatically migrate its data. Export a backup first.
