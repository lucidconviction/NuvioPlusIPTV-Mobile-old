<div align="center">

# Nuvio + IPTV Mobile

**A community fork of Nuvio + IPTV Mobile, with a full music hub, YouTube sports replays, and a smoother everyday player.**

A Live TV guide, a Sports hub that finds every game on your own channels, catch-up, recordings, your provider's movies and series, and now a complete music experience — in one app for Android phones and tablets.

[![License](https://img.shields.io/badge/license-GPL--3.0-blue?style=for-the-badge)](LICENSE)
[![Based on Nuvio Mobile](https://img.shields.io/badge/based%20on-NuvioMedia%2FNuvioMobile-8A2BE2?style=for-the-badge)](https://github.com/NuvioMedia/NuvioMobile)

[Music](#-music-hub) · [Sports replays](#-sports-replays) · [Player](#-player-and-live-tv-improvements) · [Features](#-everything-from-nuvio--iptv) · [Build](#-build-from-source) · [Support](#-support-this-fork) · [Credits](#-credits)

</div>

---

## ✨ What this fork adds

This fork keeps everything from Nuvio + IPTV Mobile and adds:

- **A complete Music hub** — browse, search, build a library, follow artists, make playlists, read synced lyrics, download songs for offline listening, and watch music videos or listen in audio-only mode.
- **YouTube sports replays** — a Replays tab on Sports, plus pre-event and post-event videos on every game page.
- **A smoother player** — the player follows the device's current orientation, stream validation and resilient playlist updates, a draggable in-app PiP, and multi-view.
- **Crossfading music transitions** — audio overlaps between tracks and the picture transitions cleanly instead of cutting.
- **YouTube in Continue Watching** — a YouTube video you were watching on Home reopens and plays through the app's YouTube engine.

---

## 🎵 Music hub

Open **Music** from the navigation bar, just before Settings.

### Browse and discover
- **Home** with a featured track, trending songs, new releases, artists, playlists and genres.
- **Search** songs, artists, albums and playlists from one box, with filters for each type.
- **Artist pages** with top songs, albums and related artists.
- **Album pages** with artwork, release date, track list and play/save actions.
- **Playlist pages** with the full track list and one-tap playback.
- **Genre browsing** for charts and mood-based discovery.

### Your library
- **Like songs** and save artists and albums.
- **Create local playlists**, add songs from anywhere, and keep them on the device.
- **Queue** view with jump-to-track and remove.
- **Shuffle** and **repeat off / all / one**.
- **Downloads** for offline listening, with a Downloads tab and per-song removal.

### Playback
- **Video / Audio switch** on both the Music page and the player, sharing one state.
- **Centred video window** with the app's own player — no full-screen route and no external app.
- **Audio mode** shows the cover art while the song keeps playing; downloaded songs show their cover and play locally.
- **Synced lyrics** with automatic scrolling to the current line.
- **Crossfade** between tracks: the outgoing audio fades out while the next fades in, the picture transitions smoothly, repeat-one restarts softly, and pausing during a transition stops the outgoing side.
- **Music videos** are resolved through the app's YouTube engine, so the same player, captions and quality controls are used everywhere.

---

## 🏆 Sports replays

Sports already shows scores, schedules and which of your own channels are showing each game. This fork adds video:

- **Replays tab** directly after Following, with a sport filter and YouTube rows for the sports you follow or the region you selected.
- **Game pages** get a **Pre-event videos** row for upcoming and live games (commentary, previews, press conferences, weigh-ins) and a **Post-event videos** row for finished games (highlights, interviews, reaction).
- Everything plays in the app's existing in-app YouTube player, with the same controls, quality options and autoplay behaviour.

---

## 📺 Player and Live TV improvements

- **Orientation follows the device.** The player no longer forces landscape; rotate only when you want to.
- **Stream validation and resilient playlist updates** — bad or stale streams are handled without losing the guide, and large Xtream lists stream in reliably.
- **In-app PiP** with a reliable press-and-hold drag, plus **Multi-view** for watching more than one stream at once.
- **Quick Channels** in the player overlay for fast switching.
- **Direct add-on playback** and safer add-on handling.
- **Channel management** — hide, reorder and rename channels and groups; pick favorites; change channel logos; search rows and Xtream switches.
- **Matching copies across sound layouts**, so the same channel is grouped sensibly when a provider lists several audio variants.

---

## 📦 Everything from Nuvio + IPTV

- **Live TV guide** with channels down the side, a timeline across, now/next highlighting, progress and a hideable "now" line.
- **Multiple M3U playlists** and **multiple XMLTV guides**, merged into one guide.
- **Xtream logins**, with your provider's movies and series if you enable them.
- **Catch-up**, reminders and **recordings**.
- **On Demand** with categories, search, sorting and source quality tags.
- **Parental controls** with PIN locks for groups and categories.
- **Sports hub** with My Sports, All Events, day tabs, team and league following, tables, game pages, reminders, scoring alerts, hide scores and record-a-game.
- **Nuvio account sign-in** so your library, profiles and Live TV setup follow you across devices.
- **Addons, library, Continue Watching, Trakt, Simkl, MDBList, subtitles and the Nuvio player** — all still here.

---

## 🚀 Build from source

Requirements:

- **JDK 17**
- **Android SDK** with the compile SDK from `gradle/libs.versions.toml`
- `local.properties` containing `sdk.dir=/path/to/Android/sdk`

Build the full debug APK:

```bash
./gradlew :androidApp:assembleFullDebug -Pnuvio.android.distribution=full
```

The APK is written to:

```text
androidApp/build/outputs/apk/full/debug/androidApp-full-debug.apk
```

Install it on a connected device:

```bash
adb install -r androidApp/build/outputs/apk/full/debug/androidApp-full-debug.apk
```

Requires **Android 7.0 or newer**. The app installs alongside the official Nuvio app and other Nuvio-based clients.

---

## ☕ Support this fork

If you like the app, feel free to buy me some tea — it's completely optional and very appreciated.

[![Buy me some tea](https://img.shields.io/badge/Buy%20me%20some%20tea-PayPal-00457C?style=for-the-badge&logo=paypal&logoColor=white)](https://www.paypal.me/Robbdeeze)

**[paypal.me/Robbdeeze](https://www.paypal.me/Robbdeeze)**

---

## 🙏 Credits

This fork would not exist without the people who built the projects underneath it:

- **[NuvioMedia / Nuvio Mobile](https://github.com/NuvioMedia/NuvioMobile)** — the upstream Nuvio client, its player, library, addons, sync and Compose Multiplatform foundation.
- **[Nuvio + IPTV](https://github.com/homelessbrian/NuvioPlusIPTV)** and **[Nuvio + IPTV Mobile](https://github.com/homelessbrian/NuvioPlusIPTV-Mobile)** — the main Nuvio + IPTV projects, Live TV, Sports, On Demand, recordings and the IPTV setup this fork builds on.
- **[Nuvio One](https://github.com/zoga2o3-ops/Nuvio-One)** — for the inspiration of bringing IPTV, YouTube and music together in one app.
- **[ghbarker / NuvioMobile-IPTV](https://github.com/ghbarker/NuvioMobile-IPTV)** — the original IPTV port of Nuvio Mobile, used with permission and gratitude.

Thank you to every developer, tester and contributor in those projects. This fork is unofficial and is **not affiliated with or endorsed by NuvioMedia**. Please report issues with this fork here, not to the upstream teams.

Data and content sources include public sports feeds, YouTube, public music catalogue metadata, and TMDB artwork. All names, logos and trademarks belong to their owners.

## ⚖️ Legal

This app is a client-side player. It does not host, store or distribute any media, and it ships with no channels, playlists or content sources. Only use sources you own or are authorized to access.

Licensed under the **GNU General Public License v3.0**, like upstream Nuvio. See [LICENSE](LICENSE).
