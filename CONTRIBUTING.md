# Contributing

## Reporting a bug

Open an issue with the bug report form. Include the app version, Android version, device, steps to reproduce, the expected result and what happened. Screenshots help with layout problems. Remove account details, access tokens and signed audio URLs from logs before you attach them.

For security problems, follow [SECURITY.md](SECURITY.md) instead.

## Setting up

You need JDK 21, Android SDK 36 and Android build tools 35.0.0. Set `ANDROID_HOME` or put the SDK path in an untracked `local.properties` file.

```sh
./gradlew :core:test :app:lintDebug :app:assembleDebug
```

The `core` module holds the TIDAL client, models and stream parsing. The `app` module holds storage, playback, downloads and the interface.

## Testing

Unit tests run without a TIDAL account:

```sh
./gradlew :core:test :app:testDebugUnitTest
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Device tests need an emulator or a connected device. They play an original 24-bit/96 kHz FLAC fixture, exercise the interface and capture screenshots:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Gradle retrieves the screenshots under `app/build/outputs/connected_android_test_additional_output/`. `PlayerLayoutTest` changes the display size and density of the device while it runs and restores them afterwards. `PseudoLocaleTest` runs the screens in a pseudo-language to find text that is not translatable.

`ScreenTourTest` saves a picture of the main screens for review. It runs only with `-e screenTour true`, and the pictures land in the `tour` folder of the app's external files. Live account checks are opt-in and do not run in CI. Install the debug app and the instrumentation APK, sign in normally in **OpenTune Debug**, then run:

```sh
adb shell am instrument -w -r -e liveAccount true \
  -e class io.github.yyan115.opentune.LiveServiceTest \
  io.github.yyan115.opentune.debug.test/androidx.test.runner.AndroidJUnitRunner
```

`LiveServiceTest` downloads a track, changes and restores a saved-track entry, and creates and removes a temporary playlist and folder. `PlaybackSoakLiveTest` plays a long track for several minutes and checks the start time, the buffer, outages of the network and autoplay. `LibraryArtworkLiveTest` checks that covers load again after an outage. Add `-e keepSession true` to leave the account signed in on a development device. Add `-e networkToggle true` to `LiveServiceTest` only on a dedicated test device, because it turns Wi-Fi and mobile data off and on. Never put account credentials or tokens in source files or CI secrets. `PlaylistsLiveTest` adds songs to a temporary playlist from its page and checks that the queue and the playback order of a shuffled queue agree. `PagesLiveTest` checks that the title and the artist in the player open the album and the artist page.

## Text and translation

All text for the user belongs in `app/src/main/res/values/strings.xml`, not in code. `python3 scripts/check_strings.py` rejects text that is written in code, and CI runs it. [docs/translating.md](docs/translating.md) explains how to add a language and how to check it with the pseudo-languages.

## Pull requests

- Keep each change focused.
- Add a test at the level that exercises the failure. Run the device tests when you change playback, persistence, permissions or navigation.
- For interface changes, check the screen at a small phone size and with larger text. Include a screenshot.
- Add a line under `Unreleased` in [CHANGELOG.md](CHANGELOG.md) for changes users can see.
- Describe the change and how you tested it in the pull request.

The project follows the official Kotlin code style, described in `.editorconfig`. Contributions are licensed under Apache-2.0.
