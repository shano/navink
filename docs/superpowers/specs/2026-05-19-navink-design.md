# Navink — Design Spec

**Date:** 2026-05-19
**Device:** Mudita Compact (e-ink Android)
**Status:** Approved

---

## Problem

Mudita Compact has no native music player. Navidrome is Shane's self-hosted music server. Navink bridges the two: a Subsonic-protocol music player built specifically for e-ink constraints.

---

## User Stories

- As a user, I can enter my Navidrome server URL and credentials so that Navink can access my library.
- As a user, I can browse artists → albums → songs so that I can find music to play.
- As a user, I can tap a song to play the whole album from that track so that listening flows naturally.
- As a user, I can see a Now Playing screen with title, artist, album art and prev/play-pause/next controls.
- As a user, I can star and unstar songs, albums and artists so that I can track favourites.
- As a user, I can view all starred items in a Favourites screen.
- As a user, I can search for artists, albums and songs by name.
- As a user, I can download an album for offline playback so that music works without network.
- As a user, offline-downloaded tracks play from local storage with no network required.

---

## Out of Scope (MVP)

- Playlists (create/edit — read-only display acceptable if trivially achievable)
- Podcast support
- Multiple server profiles
- Scrobbling / Last.fm
- Shuffle / repeat
- Seek bar (e-ink refresh makes scrubbing unusable)
- Volume control (device hardware buttons sufficient)
- Token auth (cleartext password used in debug build)

---

## Acceptance Criteria

1. App connects to a Navidrome instance via URL + username + plaintext password.
2. `ping` call succeeds before any library fetch; failure shows error message.
3. Browse loads artists from Room after first sync; works offline after that.
4. Tapping a song starts playback; queue is remainder of album.
5. Prev/Next navigate the album queue. Play/Pause toggles.
6. Starring a song calls `star(id)`; unstarring calls `unstar(id)`. Room updated immediately.
7. Favourites screen loads starred songs/albums/artists via `getStarred2`.
8. Search calls `search3`; results shown for artists, albums, songs.
9. Downloading an album enqueues one `DownloadWorker` per song via WorkManager.
10. Downloaded tracks play from `localPath` with no network.
11. App does not ANR or crash during normal navigation between all screens.
12. All UI uses `ThemeMMD` root theme and MMD components where available.

---

## Architecture

### Overview

Single Activity (`MainActivity`) hosting a Compose Navigation graph. MVVM + StateFlow. Hilt for DI. Room as single source of truth for all library metadata.

### Data Flow

```
Navidrome (Subsonic REST)
        │  Retrofit (SubsonicService)
        ▼
  Repository layer  ──── write ──→  Room DB
        │                ◀── read ── (Flows)
        ▼
   ViewModels (StateFlow)
        ▼
   Compose screens
```

Sync strategy: on app launch (after credentials confirmed), `SyncRepository` fetches `getArtists`, `getAlbum` for each artist, writes entities to Room. ViewModels observe Room Flows — browse works offline after first sync.

### Navigation Graph

```
NavGraph
├── settings          (SetupScreen — if no credentials)
├── browse/artists    (ArtistsScreen)
│   └── browse/albums/{artistId}   (AlbumsScreen)
│       └── browse/songs/{albumId} (SongsScreen)
├── search            (SearchScreen)
├── favourites        (FavouritesScreen)
├── nowplaying        (NowPlayingScreen — full-screen overlay)
└── settings          (SettingsScreen — accessible from any screen)
```

Bottom navigation bar: Browse | Search | Favourites. Now Playing launched on song tap; back returns to browse. Persistent mini-player bar shown on Browse, Search, and Favourites screens when music is playing. Taps to full Now Playing. Hidden on Settings and Now Playing screens.

### Package Structure

```
com.navink/
├── NavinkApp.kt
├── MainActivity.kt
├── NavGraph.kt
├── data/
│   ├── model/           Artist, Album, Song (domain models)
│   ├── local/           Room DB, DAOs, entities (ArtistEntity, AlbumEntity, SongEntity)
│   ├── remote/          SubsonicService (Retrofit), response DTOs
│   └── repository/      MusicRepository, SyncRepository, FavouritesRepository, DownloadRepository
├── player/
│   ├── PlaybackService.kt    (MediaSessionService)
│   └── PlayerController.kt  (MediaController wrapper)
├── download/
│   └── DownloadWorker.kt
├── ui/
│   ├── theme/Theme.kt
│   ├── browse/           ArtistsScreen, AlbumsScreen, SongsScreen + ViewModels
│   ├── search/           SearchScreen + ViewModel
│   ├── player/           NowPlayingScreen + MiniPlayer + ViewModel
│   ├── favourites/       FavouritesScreen + ViewModel
│   └── settings/         SettingsScreen + ViewModel
└── di/
    ├── NetworkModule.kt
    ├── DatabaseModule.kt
    └── PlayerModule.kt
```

---

## Feature Specifications

### Settings

- Screen shown on first launch if no credentials in DataStore.
- Fields: Server URL (`TextFieldMMD`), Username (`TextFieldMMD`), Password (`TextFieldMMD`, masked).
- "Connect" button (`ButtonMMD`): calls `ping`, navigates to Browse on success, shows error on failure.
- Accessible from Browse via settings icon in top bar.
- Credentials stored in DataStore Preferences (not Room).

### Browse

- **Artists screen:** List of `ArtistEntity` from Room, alphabetical. Each row: name + album count.
- **Albums screen:** Albums for selected artist from Room. Each row: cover art (Coil, crossfade=false) + title + year.
- **Songs screen:** Songs for selected album from Room. Each row: track number + title + duration. Tap → start playback (queue = album from that track).
- Pull-to-refresh on Artists screen triggers background sync.
- Loading state shown while initial sync in progress.

### Playback

- `PlaybackService` extends `MediaSessionService`, runs as foreground service.
- On song tap: `PlayerController.playAlbumFrom(albumId, startSongId)` — builds `MediaItem` list, passes to `MediaController`.
- Stream URL: `{baseUrl}/rest/stream.view?id={id}&...auth params...`
- For downloaded songs: swap stream URL for `file://` URI when `localPath != null`.
- `PlayerController` exposes `StateFlow<PlayerState>` (current song, isPlaying, position).

### Now Playing Screen

- Full-screen. Black background, white text (inverted for emphasis — matches Remink "overdue" pattern).
- Album art: full-width, Coil, `crossfade(false)`, grayscale rendering.
- Song title (large), album name, artist name below art.
- Controls row: Prev | Play/Pause | Next — `ButtonMMD` / `OutlinedButtonMMD`, ≥80dp tall.
- No seek bar.
- Download button: triggers `DownloadWorker` for current song. Shows "Downloaded" when `localPath != null`.

### Search

- Single text input (`TextFieldMMD`). Submit on IME action.
- Calls `search3(query, artistCount=20, albumCount=20, songCount=20)`.
- Results split into three sections: Artists, Albums, Songs.
- Tapping a result: artist → AlbumsScreen, album → SongsScreen, song → plays immediately (queue = just that song, acceptable deviation for search results).
- No Room caching for search results.

### Favourites

- Tabbed or sectioned list: Artists | Albums | Songs.
- Data from `getStarred2` synced into Room on screen open.
- Star/unstar: tap star icon on any song/album/artist row → `star`/`unstar` API + update Room entity.

### Offline / Downloads

- `DownloadRepository.downloadAlbum(albumId)` or `downloadSong(songId)` enqueues `DownloadWorker` via WorkManager.
- `DownloadWorker`: streams `download.view?id=...` to app-private external storage. Updates `SongEntity.localPath` on completion.
- Download state shown in SongsScreen row and Now Playing screen.
- On `PlaybackService`, `PlayerController.playAlbumFrom()` checks `localPath` — uses local URI if present, stream URL if not.

---

## Data Model

### Room Entities

```kotlin
@Entity data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val albumCount: Int,
    val isStarred: Boolean = false,
)

@Entity data class AlbumEntity(
    @PrimaryKey val id: String,
    val artistId: String,
    val name: String,
    val year: Int?,
    val coverArtId: String?,
    val songCount: Int,
    val isStarred: Boolean = false,
)

@Entity data class SongEntity(
    @PrimaryKey val id: String,
    val albumId: String,
    val artistId: String,
    val title: String,
    val trackNumber: Int?,
    val duration: Int,
    val coverArtId: String?,
    val isStarred: Boolean = false,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
)
```

### Subsonic API Parameters

Auth params appended to every request:
```
u={username}&p={password}&v=1.16.1&c=navink&f=json
```

Cover art URL: `{baseUrl}/rest/getCoverArt.view?id={coverArtId}&{auth}`
Stream URL: `{baseUrl}/rest/stream.view?id={songId}&{auth}`

---

## E-ink Design Rules

These apply to every screen without exception:

- No animations. No crossfade. No transitions.
- `clickable(indication = null, interactionSource = remember { MutableInteractionSource() })` — no ripple anywhere.
- `ThemeMMD` as root theme wrapper. `ButtonMMD`, `OutlinedButtonMMD`, `TextFieldMMD` for interactive elements.
- High contrast only: black on white or white on black. No grey text on grey background.
- Touch targets ≥ 80dp tall.
- Now Playing and selected/active rows use inverted style (black bg, white text).
- Full-screen redraws preferred over partial updates.

---

## Error Handling

- Network failure on browse: show last-known Room data + "Offline" indicator.
- `ping` failure on Settings: show error message inline.
- Download failure: WorkManager retries (default policy). Show failed state in UI.
- No global crash dialogs — individual screens handle their own error states.

---

## Testing

- Unit tests: `MusicRepository` (mock `SubsonicService`), `PlayerController` state logic.
- Room tests: DAO queries with in-memory Room DB (no mock).
- Characterisation tests written by `test-author` before any refactor.
- No mock internals — mock only at system boundaries (HTTP, DB).
- Robolectric for any test requiring Android `Context`.

---

## Build & Deployment

```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`
Deploy: upload to FastMail, download and install on device.

---

## Implementation Order

1. **Settings** — prerequisite: without credentials nothing else works
2. **Data layer** — Room entities + DAOs + Subsonic Retrofit service + Repository + Sync
3. **Browse** — ArtistsScreen → AlbumsScreen → SongsScreen (read-only, no playback yet)
4. **Playback** — PlaybackService, PlayerController, NowPlayingScreen, MiniPlayer
5. **Search** — SearchScreen
6. **Favourites** — star/unstar, FavouritesScreen
7. **Offline** — DownloadWorker, localPath swap in playback
