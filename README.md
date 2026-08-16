<p align="center"><img src="assets/icon.svg" width="96" height="96" alt="Navink icon"></p>
<h1 align="center">Navink</h1>
<p align="center">A Navidrome / Subsonic music player for the Mudita Kompact.</p>

<p align="center">
  <a href="https://github.com/shano/navink/actions/workflows/release.yml"><img src="https://github.com/shano/navink/actions/workflows/release.yml/badge.svg" alt="Build status"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue.svg" alt="License: GPL-3.0"></a>
</p>

---

Navink streams and downloads music from your own Navidrome or Subsonic-compatible
server. Built for the Mudita Kompact's e-ink screen: grayscale album art, no
crossfades, no animations, high contrast.

## Features

- Browse by artist, album, and song
- Search across your library
- Background playback with media session controls
- Download albums for offline listening
- Favourites
- Works with any Subsonic/Navidrome-compatible server

## Requirements

- A Mudita Kompact, or any Android 7.0+ (API 24) device
- A running Navidrome or Subsonic server

## Installation

Grab the latest APK from [Releases](https://github.com/shano/navink/releases) and
sideload it — Navink isn't on the Play Store.

Or track updates automatically with [Obtainium](https://github.com/ImranR98/Obtainium):

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/shano/navink)

## Building from source

Navink consumes Mudita's [MMD](https://github.com/mudita/MMD) e-ink component library
as a Gradle composite build. Clone it as a sibling directory before building:

```sh
git clone https://github.com/mudita/MMD ../MMD
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## License

GPL-3.0 — see [LICENSE](LICENSE).

## Other apps for the Kompact

| [Navink](https://github.com/shano/navink) | [Remink](https://github.com/shano/remink) | Zendo |
|:---:|:---:|:---:|
| <a href="https://github.com/shano/navink"><img src="assets/icon.svg" width="64" height="64"></a> | <a href="https://github.com/shano/remink"><img src="https://raw.githubusercontent.com/shano/remink/main/assets/icon.svg" width="64" height="64"></a> | 🚧 |
| Navidrome / Subsonic player | Reminders with alarms | Meditation timer — in progress |
