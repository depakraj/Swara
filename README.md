# Swara

**Every note, in full detail.**

Swara is a black-and-white music and video player for Android, built on the VLC engine (libVLC).
It's made for people who listen to FLAC and ALAC hi-res files. It finds your music by itself, sorts
it by music director, keeps call recordings out of your library and plays the next song
automatically.

## Features

### A library that sorts itself
- **No folders to add.** Swara scans your phone storage and SD card automatically. New downloads
  show up on their own.
- **Grouped by music director.** Songs are sorted by the composer tag. When that's empty, Swara
  uses the album artist if it differs from the singer, which is common in Indian film music. You
  can also group by singer or album.
- **Default thumbnails.** Songs without artwork get a grey tile with initials, such as "AR" for
  A. R. Rahman. The same name always gets the same tile.

### Only real music
These are kept out of your library automatically, and each one has its own switch:
- Call recordings (call recorder folders and files named like call recordings)
- Voice recorder notes
- WhatsApp and Telegram voice notes
- Ringtones, alarms and notification sounds
- Clips under 30 seconds

You can review everything that was hidden, and hide any other folder.

### Playback
- **Autoplay.** When a song ends, the next one starts. When the list ends, Swara can carry on with
  more songs by the same music director.
- Resume where you left off, shuffle, repeat one or all, a queue and a sleep timer.
- Favourites, recently added, most played and search.
- Lock-screen, notification and Galaxy Buds button controls. Music pauses when you take your
  earbuds out.

### Hi-res audio
- Every song shows its format, bit depth and sample rate, such as **FLAC 24/96** or **ALAC 24/48**.
- 24-bit and high sample-rate files get a **HI-RES** badge.
- Tips for the best Bluetooth quality, such as Samsung's SSC UHQ codec on Galaxy Buds.

### Videos
- A video list with thumbnails, a full-screen player and continue watching.

## Install

1. Open the **Actions** tab of this repository and pick the latest successful
   **Build Swara APK** run.
2. Under **Artifacts**, download **Swara-apk** and unzip it.
3. Copy the `.apk` to your phone and open it. Allow **Install unknown apps** when Android asks.
4. When Swara opens, allow access to music and videos.

## Build it yourself

- **Android Studio:** open this folder and press Run.
- **Command line:** run `./gradlew assembleRelease`. You need JDK 17 and the Android SDK.

Every push to GitHub builds a new APK automatically
(see [`.github/workflows/build.yml`](.github/workflows/build.yml)).

The APK is signed with a debug key so it installs directly. Use your own signing key before
publishing it anywhere.

## Tech

- Kotlin and Jetpack Compose
- libVLC 3.7 for audio and video playback
- MediaStore for automatic scanning, MediaSession for system and earbud controls
- Minimum Android 10, built for 64-bit ARM phones

## Licence

Swara uses libVLC, which is licensed under the LGPL 2.1 or later and includes GPL components. This
app's source is released under the GPL (GPL-3.0-or-later).
