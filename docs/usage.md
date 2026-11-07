# Usage guide

## Getting started

The first screen offers two ways to begin. **Sign in to TIDAL** is the main one. **Use local music only** opens the app without an account, so you can play your own audio files. You can sign in at any time from **Settings** or from the **Connect TIDAL** card.

With an account, OpenTune shows **Home**, **Search**, **Library** and **On device**. Without one, it shows **On device** alone, with a text filter for the music stored on the phone. Settings that only apply to TIDAL appear once an account is connected.

## Signing in

1. Choose **Sign in to TIDAL**.
2. Sign in on TIDAL's page in your browser.
3. If your browser asks to open OpenTune, allow it. OpenTune finishes signing in automatically.

If the official TIDAL app is also installed, Android may ask which app should open the sign-in link. Choose OpenTune. To sign out, open **Settings** and choose **TIDAL connected**. Music stored on the device stays in your library.

OpenTune receives a one-time authorization code and checks the state value before it exchanges the code with TIDAL. The exchange includes a PKCE verifier that OpenTune generates on your device for each sign-in. A pending sign-in expires after 15 minutes. Browser authentication follows [RFC 8252](https://www.rfc-editor.org/rfc/rfc8252.html).

## Home, Search and Library

**Home** shows your TIDAL mixes, radio stations and new releases below your recent plays. Pull down to refresh.

**Search** opens on genres, moods and decades. Typing shows a top result and tabs for each kind of result. Recent searches stay on the device and can be cleared.

**Library** lists your saved tracks, albums, followed artists, playlists and mixes. Filter and sort the lists, and use **Refresh library** to update them. **Playlists** shows your TIDAL playlists and the playlists made on the device in one list. A playlist made on the device carries the **Local** mark. TIDAL playlist folders mirror the folders in your account. Tap a folder to open it. **New folder** creates a folder at the top level, and a playlist's folder button moves it into a folder or back to the top level. Deleting a folder moves its playlists to the top level first, because TIDAL hides the playlists of a folder that is removed with them inside.

Artist, album and mix pages list the music with the biography, review and credits that TIDAL provides. **Follow** adds an artist to your library. **Save album** and **Save playlist** add a release or a playlist from someone else. The track menu has **Play similar music**, which plays the track and continues with similar tracks, and **Credits**.

## On device

**On device** holds everything that is stored on the phone: downloaded tracks and your own audio files. Use the filters to show **All**, **Downloads** or **Local files**, filter by text, and sort by date added, title, artist or album.

- **Add files** adds audio files. OpenTune plays them from where they are and does not copy them.
- **Add folder** opens Android's folder picker. OpenTune reads the audio files in that folder and its subfolders, skips hidden folders, and keeps the folder up to date. A folder is scanned again when you open the app a day after its last scan, and whenever you choose **Scan again**. A scan that cannot read a folder, for example because a memory card is missing, keeps what it found before. **Remove** takes a folder and its files out of the library and leaves your files alone.
- A local file carries the **Local** mark. Its cover comes from the file, or from a `cover`, `folder`, `front` or `album` picture in the same folder. The file is known by its document, so the same file added by hand and by a folder is one track.
- **Details** in the menu of a track shows its format, bit depth, sample rate, bitrate, size and where it is stored.
- A file that a folder holds leaves with that folder. **Remove from library** applies to files that you added by hand and to downloads.

Android decides which formats play. FLAC, MP3, AAC and WAV work on supported devices.

## Playing music

Tap the track title in the player to open its album, or the artist name to open the artist page. The row below the controls opens **Lyrics**, the **Sleep timer**, **Download** and the **Queue**. A swipe sideways on the mini player stops playback.

- **Lyrics** highlight the current line. Drag to read ahead, and the view follows the song again after a few seconds. Tap a line to jump to it. Tracks without timing show the plain text, and some tracks have no lyrics. Lyrics are provided by Musixmatch through TIDAL and stay on the device after the first load.
- The **sleep timer** pauses playback after 15 to 90 minutes or at the end of the track.
- The **queue** shows what plays next. Drag a row by its handle to move it, and swipe a row sideways to remove it. **Shuffle** puts the tracks after the current one in random order in the queue itself, so the queue always shows the real order, and turning shuffle off restores the earlier order.
- **Avoid repeats**, under **Settings**, **Playback**, puts tracks that played within the chosen time last in shuffle and leaves them out of radio and autoplay. Its page lists the avoided tracks with the time of their last play and the time that remains. **Allow** lets a track play again at once, and its next play starts the time anew.
- **Autoplay** adds similar tracks from TIDAL when the queue ends. Turn it off under **Settings**, **TIDAL**.
- Tap the format line in the player to see the codec, bit depth and sample rate of the source. These values describe the file, not the output of your audio device.

## Audio

Choose the quality for Wi-Fi and mobile data under **Settings**, **TIDAL**.

| Setting | Delivered audio |
| --- | --- |
| Max | The best lossless source available, up to 24-bit/192 kHz. |
| Lossless | CD-quality FLAC. |
| High | AAC 320 kbps. |
| Low | AAC 96 kbps. |

Max and Lossless accept FLAC or ALAC and reject lossy responses. A FLAC stream carried in MP4 or DASH is still lossless. New installs default to lossless on both networks. TIDAL's own apps also set the quality for each network and not for each track.

OpenTune keeps up to 90 seconds of the track ahead of the playing position. A short loss of connection passes unheard, and after a longer one playback resumes by itself when the network returns.

- **Volume normalization** evens out the level between tracks using ReplayGain values. Loud tracks are turned down and quiet tracks keep their level. It is off by default so that tracks play unchanged.

## Experimental features

**Settings**, **Experimental** is a section of the list for features that are new or depend on the device.

- **Visualizer** shows the spectrum of the music over the artwork in the player. It needs Android 11 or later.
- **Equalizer** adjusts the bands of Android's built-in equalizer and offers its presets. The equalizer changes the audio, so leave it off for unaltered playback.
- **ListenBrainz** sends your listens to a ListenBrainz server, see below.
- **Bit-perfect USB output**, on Android 14 and later, asks Android to send the audio to a USB DAC without mixing or resampling.
- **Download saved tracks** downloads a track when you save it with the heart button. Tracks that were saved earlier, or in another app, are not downloaded, and removing a track from saved tracks keeps its download.
- **Download played tracks** downloads each track that counts as played, with the same rule as the statistics. It asks for confirmation because it uses a large amount of storage and data. The two download options exclude each other, so turning one on turns the other off.

Both download options need an account, follow **Download over mobile data**, stop when offline mode is on or when less than 2 GB of storage is free, and skip local files and tracks that are downloaded already.

## Themes

**Settings**, **Appearance**, **Theme** opens the theme picker. Swipe through the themes and the whole app shows each one at once. Choose **Use** to keep a theme. Closing the picker brings back the saved theme. **System** switches between Light and Dark with the device and is the default on a new install. Dark, Light, Obsidian, Gold, Cyberpunk, Neon, Ocean, Ember, Forest, Paper, Sky and Rose each have their own colours, shapes and type.

## Offline listening and downloads

Choose **Download** in a track's menu or in the player. Finished downloads appear in **On device** and play without a connection or an account. Downloads wait for Wi-Fi by default. **Download over mobile data**, under **Settings**, **TIDAL**, lets them use your data plan after a confirmation. The notification of a download shows its progress and a button to cancel it. The experimental options **Download saved tracks** and **Download played tracks** start downloads by themselves, see above.

Turn on **Offline mode**, under **Settings**, **TIDAL**, to play only audio stored on the device. OpenTune sends no requests to TIDAL in this mode.

Downloads are stored in OpenTune's private storage. Android removes them when you uninstall the app or clear its storage. Use **Export audio file** in the menu of a downloaded track to keep a copy. Lossless downloads become tagged `.flac` files without re-encoding. A local file is a file already, so it has no export.

## Cars, Bluetooth and the widget

Cars and Bluetooth devices can browse **Recently played**, **Saved tracks**, **Albums**, **Playlists** and **On device**, and start a track from there. Lists are served from the device first, so browsing works without a connection, but streaming a track needs one unless it is downloaded. Android Auto needs its developer setting for apps from unknown sources, as for any app that is not installed from Google Play. A voice request such as "play Nujabes" searches your library and TIDAL.

Add the **OpenTune** widget from your launcher's widget list. It shows the current track with previous, play or pause, and next buttons. A tap on the cover or the title opens the app.

## Statistics, ListenBrainz and backup

**Listening statistics** counts a listen after half of the track or 4 minutes, whichever comes first, and never before 30 seconds. The numbers are calculated on the device, and **Clear listening history** removes them.

[ListenBrainz](https://listenbrainz.org) is an open source service that keeps a long-term history of what you listen to. It is not part of TIDAL. It is off by default. Add a ListenBrainz user token, or the address of your own ListenBrainz-compatible server, under **Settings**, **Experimental**, **ListenBrainz**, and OpenTune sends the artist, title and album of each finished track. Listens that cannot be sent wait on the device until the server answers. The token is stored encrypted.

**Back up data** writes a file with your settings, device playlists and listening history. **Restore data** adds such a file to the current install. TIDAL data stays in your account. The file holds no tokens, downloads or local audio files. Playlist entries for local files are left out.

## Language

OpenTune is in English. Text that TIDAL supplies, such as section titles on Home and Search, follows the language of the device. Contributors can add translations, see [translating.md](translating.md).

## Limits

- **Dolby Atmos:** TIDAL sends this client the stereo mix of tracks that also have an Atmos mix, whatever quality or format is requested, so OpenTune cannot play Atmos. Tracks that exist only as Atmos mixes may not play at all.
- **Crossfade:** not offered. Tracks play gapless, with no overlap.
