# Swara

A black-and-white music and video player for Android, built on the VLC engine (libVLC).
Made for listening to FLAC and ALAC hi-res files.

## What it does

- **Finds your music by itself.** Phone storage and SD card are scanned automatically, and new
  downloads appear on their own. There are no folders to add.
- **Keeps unwanted audio out.** Call recordings, voice recorder notes, WhatsApp and Telegram
  voice notes, ringtones and alarms, and clips under 30 seconds are hidden. You can switch each
  filter, review what was hidden and hide extra folders.
- **Sorts songs by music director.** It uses the composer tag, and falls back to the album artist
  when that differs from the singer. You can also group by singer or album.
- **Autoplay.** When a song ends, the next one plays. When the list ends, it can keep going with
  more songs by the same music director.
- **Hi-res details.** Each song shows its format, bit depth and sample rate, such as FLAC 24/96.
  24-bit and high sample-rate files are marked HI-RES.
- **Everyday features.** Resume where you left off, favourites, most played, recently added,
  search, shuffle and repeat, a sleep timer, a queue, lock-screen and notification controls,
  Galaxy Buds button controls, and pausing when the Buds come out.
- **Videos.** A video list with thumbnails, a full-screen player and continue watching.

## Build

Open the folder in Android Studio and press Run. Or push it to GitHub, where the included
workflow (`.github/workflows/build.yml`) builds an installable APK. You'll find it under
**Actions › latest run › Artifacts › Swara-apk**.

The APK is signed with a debug key so it installs directly. Use your own signing key before
publishing it anywhere.

## Licence

Swara uses libVLC, which is licensed under the LGPL 2.1+ and includes GPL components.
Release this app's source under the GPL as well (GPL-3.0-or-later is recommended), and add the
full licence text as `LICENSE` before you share it.
