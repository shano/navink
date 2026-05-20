# Navink — Claude Context

Navink is a Navidrome/Subsonic music player for the Mudita Compact (e-ink Android device).
Sister project to Remink (reminder app) — built by the same owner using the same stack and tooling.

---

## Build Environment

```
JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
ANDROID_HOME=$HOME/Android/Sdk
```

Always prefix Gradle commands with both vars:

```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew assembleDebug
```

Build output: `app/build/outputs/apk/debug/app-debug.apk`
Deployment: sideload via ADB or upload to FastMail and install manually on device.

---

## Tech Stack

| Layer | Choice | Version |
|---|---|---|
| Language | Kotlin | 2.0.20 |
| Android Gradle Plugin | AGP | 8.3.2 |
| Gradle | Gradle wrapper | 8.5 |
| UI | Jetpack Compose + Material3 | BOM 2024.06.00 |
| Architecture | MVVM + StateFlow | — |
| DI | Hilt | 2.52 |
| DB | Room | 2.6.1 |
| Network | Retrofit + OkHttp | 2.9.0 / 4.12.0 |
| Playback | Media3 (ExoPlayer) | 1.3.1 |
| Offline downloads | WorkManager | 2.9.0 |
| Preferences | DataStore | 1.0.0 |
| Album art | Coil | 2.6.0 |
| e-ink UI | Mudita MMD | 1.0.1 |

**Critical Kotlin 2.0+ rule:** Must use `org.jetbrains.kotlin.plugin.compose` plugin.
Do NOT add `composeOptions { kotlinCompilerExtensionVersion }` — the plugin handles this.

---

## Mudita MMD Library

MMD provides e-ink-optimised Compose components. It is NOT published to Maven Central or JFrog.
It is consumed via **Gradle composite build** from a local clone.

### Setup

1. Clone MMD source to `../MMD` (sibling of this project directory).
2. The `settings.gradle.kts` already wires the composite build.

### Available components

| Component | Import | Notes |
|---|---|---|
| `ThemeMMD` | `com.mudita.mmd.ui.theme` | Root theme wrapper — use instead of MaterialTheme |
| `ButtonMMD` | `com.mudita.mmd.ui.components` | Primary button, no ripple |
| `OutlinedButtonMMD` | `com.mudita.mmd.ui.components` | Secondary/cancel button |
| `FloatingActionButtonMMD` | `com.mudita.mmd.ui.components` | FAB |
| `TextFieldMMD` | `com.mudita.mmd.ui.components` | Single-line text input |
| `DatePickerMMD` | `com.mudita.mmd.ui.components` | Date picker, needs `rememberDatePickerState()` |
| `TimeInputMMD` | `com.mudita.mmd.ui.components` | Time input, needs `rememberTimePickerState()` |

### Theme wiring

```kotlin
@Composable
fun NavinkTheme(content: @Composable () -> Unit) {
    ThemeMMD(content = content)
}
```

`ThemeMMD` handles: e-ink greyscale palette, ripple suppression, typography.
The XML theme (`themes.xml`) only needs `android:Theme.Material.NoActionBar` — do NOT use
`Theme.MaterialComponents` or `Theme.AppCompat` (those require explicit deps that conflict).

### E-ink design principles (learned from Remink)

- **No animations.** E-ink refresh is slow; crossfades cause ghosting.
  Coil: `ImageRequest.Builder(...).crossfade(false)`.
- **No ripple/touch feedback.** Use `clickable(indication = null, interactionSource = ...)`.
- **High contrast only.** Black on white or white on black. No grey text on grey background.
- **Large touch targets.** Mudita Compact screen is small; buttons ≥ 80dp tall where practical.
- **Minimal state transitions.** Prefer full-screen redraws over partial updates.
- **Inverted rows for emphasis.** Black bg + white text used in Remink for overdue items.
  Repeat this pattern for "now playing" or active/selected states.

---

## Project Architecture

```
com.navink/
├── NavinkApp.kt              # @HiltAndroidApp Application
├── MainActivity.kt           # single activity, hosts NavGraph
├── NavGraph.kt               # Compose Navigation graph
│
├── data/
│   ├── model/                # data classes (Song, Album, Artist, Playlist)
│   ├── local/                # Room DAOs, database, entities
│   ├── remote/               # Retrofit service, Subsonic API DTOs
│   └── repository/           # Repository interfaces + impls
│
├── player/
│   ├── PlaybackService.kt    # Media3 MediaSessionService (foreground)
│   └── PlayerController.kt   # wrapper around MediaController for ViewModels
│
├── download/
│   └── DownloadWorker.kt     # WorkManager worker for offline sync
│
├── ui/
│   ├── theme/
│   │   └── Theme.kt          # NavinkTheme wrapping ThemeMMD
│   ├── browse/               # Library browse screens (artists, albums, songs)
│   ├── search/               # Search screen + ViewModel
│   ├── player/               # Now playing screen
│   ├── favourites/           # Starred/favourites screen
│   └── settings/             # Server URL + credentials entry
│
└── di/
    ├── NetworkModule.kt      # Retrofit, OkHttp, SubsonicService
    ├── DatabaseModule.kt     # Room DB + DAOs
    └── PlayerModule.kt       # MediaSession binding
```

---

## Subsonic API

Navidrome implements the Subsonic REST API. Base URL pattern:

```
https://<host>/rest/<method>.view?u=<user>&p=<password>&v=1.16.1&c=navink&f=json
```

Key endpoints:
- `getMusicFolders` — list libraries
- `getArtists` — all artists (ID3 tags)
- `getArtist(id)` — artist + albums
- `getAlbum(id)` — album + songs
- `search3(query)` — search artists/albums/songs
- `stream(id)` — audio stream URL (pass directly to ExoPlayer)
- `download(id)` — full file download for offline
- `star(id)` / `unstar(id)` — favourites
- `getStarred2` — starred items
- `getCoverArt(id)` — album art (pass as image URL to Coil)

Auth: either `p=<cleartext>` or `p=enc:<hex>` or token auth (`t` + `s` params).
Start with cleartext in debug; add token auth for release.

---

## Hilt patterns (copy from Remink)

```kotlin
// Application
@HiltAndroidApp
class NavinkApp : Application()

// Activity / Service
@AndroidEntryPoint
class MainActivity : ComponentActivity()

// ViewModel
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: MusicRepository,
) : ViewModel()

// Inject in Composable via hiltViewModel()
val vm: BrowseViewModel = hiltViewModel()
```

---

## Room patterns

```kotlin
@Entity
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val albumId: String,
    val artistId: String,
    val duration: Int,
    val isStarred: Boolean = false,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
)

@Dao
interface SongDao {
    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId")
    fun songsForAlbum(albumId: String): Flow<List<SongEntity>>

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)
}
```

---

## Media3 playback notes

- `PlaybackService` extends `MediaSessionService` — runs as foreground service.
- ViewModels interact via `MediaController` (bound to service), NOT directly.
- For offline: swap stream URL for local file URI when `localPath != null`.
- E-ink: suppress Media3 default UI animations; build a custom Now Playing screen.

---

## Known build gotchas (learned from Remink)

1. **AGP 8.3.2 + Hilt 2.52:** Must use Hilt 2.52+ exactly. Earlier versions hit ASM
   instrumentation path bug (`javac/debug/classes/` vs `javac/debug/compileDebugJavaWithJavac/classes/`).

2. **Kotlin 2.0+:** kapt falls back to language version 1.9 (warning, not error — safe to ignore).
   `w: Kapt currently doesn't support language version 2.0+. Falling back to 1.9.`

3. **compileSdk 35 only:** The machine only has android-35 installed. Do not set compileSdk/targetSdk to 34.

4. **XML theme:** Must be `android:Theme.Material.NoActionBar`. MaterialComponents and AppCompat
   are not reliably available via AAPT without explicit deps.

5. **Robolectric tests:** Any test touching Android APIs needs:
   - `@RunWith(RobolectricTestRunner::class)`
   - `testOptions { unitTests { isIncludeAndroidResources = true } }` in build.gradle.kts
   - Use `ApplicationProvider.getApplicationContext()` for real Context — mocked Context fails
     for statics like `PendingIntent`.

6. **MMD composite build:** If `../MMD` is missing, Gradle fails at configuration time.
   Check it exists before running any build command.

---

## Deployment

No ADB connection to Mudita Compact in this environment. Workflow:
1. Build APK: `./gradlew assembleDebug`
2. Upload `app/build/outputs/apk/debug/app-debug.apk` to FastMail (shane's account)
3. Open FastMail on device, download APK, install (allow unknown sources if prompted)

---

## Agent orchestration notes

Remink was built using multi-agent orchestration:
- **spec-writer + UI designer** → wrote spec and design doc
- **architect** → wrote design doc from spec
- **test-writer (RED)** → wrote failing tests first (TDD red phase)
- **Kotlin engineer (GREEN)** → implemented code to pass tests
- **code-reviewer** → quality pass before shipping

Recommended same flow for Navink. Start with brainstorming skill, then spec-writer,
then architect, then TDD red-green cycles per feature area.

Feature areas to tackle independently:
1. Settings (server URL + credentials) — prerequisite for everything
2. Browse (artists → albums → songs) — core navigation
3. Playback (stream + Now Playing screen)
4. Search
5. Favourites (star/unstar, starred list)
6. Offline (download + local playback)
