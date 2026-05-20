# Navink — Full App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Navink — a Navidrome/Subsonic music player for the Mudita Compact e-ink Android device, from zero Kotlin files to a working APK.

**Architecture:** Room as single source of truth for library metadata. Subsonic REST API synced into Room on launch. MVVM + StateFlow. Hilt DI. Media3 MediaSessionService for playback. E-ink design rules (no animations, no ripple, high contrast, large touch targets) enforced throughout.

**Tech Stack:** Kotlin 2.0.20, Jetpack Compose + Material3, Hilt 2.52, Room 2.6.1, Retrofit 2.9.0 + OkHttp 4.12.0, Media3 1.3.1, WorkManager 2.9.0, DataStore 1.0.0, Coil 2.6.0, Mudita MMD 1.0.1 (composite build from `../MMD`)

**CRITICAL build rule:** All Gradle commands must be prefixed:
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew <task>
```

**compileSdk MUST be 35** — only android-35 is installed on this machine.

---

## File Map

```
app/src/main/java/com/navink/
├── NavinkApp.kt
├── MainActivity.kt
├── NavGraph.kt
├── data/
│   ├── local/
│   │   ├── NavinkDatabase.kt
│   │   ├── dao/ArtistDao.kt
│   │   ├── dao/AlbumDao.kt
│   │   ├── dao/SongDao.kt
│   │   ├── entity/ArtistEntity.kt
│   │   ├── entity/AlbumEntity.kt
│   │   └── entity/SongEntity.kt
│   ├── remote/
│   │   ├── SubsonicService.kt
│   │   ├── SubsonicAuthInterceptor.kt
│   │   └── dto/SubsonicDtos.kt
│   └── repository/
│       ├── SettingsRepository.kt
│       ├── MusicRepository.kt
│       ├── SyncRepository.kt
│       ├── FavouritesRepository.kt
│       └── DownloadRepository.kt
├── di/
│   ├── DataStoreModule.kt
│   ├── NetworkModule.kt
│   ├── DatabaseModule.kt
│   └── PlayerModule.kt
├── player/
│   ├── PlayerState.kt
│   ├── PlaybackService.kt
│   └── PlayerController.kt
├── download/
│   └── DownloadWorker.kt
└── ui/
    ├── theme/Theme.kt
    ├── settings/
    │   ├── SettingsScreen.kt
    │   └── SettingsViewModel.kt
    ├── browse/
    │   ├── BrowseViewModel.kt
    │   ├── ArtistsScreen.kt
    │   ├── AlbumsScreen.kt
    │   └── SongsScreen.kt
    ├── player/
    │   ├── PlayerViewModel.kt
    │   ├── NowPlayingScreen.kt
    │   └── MiniPlayer.kt
    ├── search/
    │   ├── SearchViewModel.kt
    │   └── SearchScreen.kt
    └── favourites/
        ├── FavouritesViewModel.kt
        └── FavouritesScreen.kt

app/src/test/java/com/navink/
├── data/local/dao/SongDaoTest.kt
├── data/repository/MusicRepositoryTest.kt
└── player/PlayerControllerTest.kt
```

---

## Task 1: Git Init + Manifest Permissions

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Init git**
```sh
cd /var/home/shane/Documents/Projects/navink
git init
git add .
git commit -m "chore: initial project scaffold"
```

- [ ] **Step 2: Read current manifest**

Read `app/src/main/AndroidManifest.xml`. It currently has a minimal template. Replace its contents with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

    <application
        android:name=".NavinkApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Navink">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <service
            android:name=".player.PlaybackService"
            android:exported="true"
            android:foregroundServiceType="mediaPlayback">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService"/>
            </intent-filter>
        </service>

    </application>

</manifest>
```

- [ ] **Step 3: Verify themes.xml has correct base theme**

Read `app/src/main/res/values/themes.xml`. It must use `android:Theme.Material.NoActionBar` — NOT MaterialComponents or AppCompat. If not:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Navink" parent="android:Theme.Material.NoActionBar"/>
</resources>
```

- [ ] **Step 4: Commit**
```sh
git add app/src/main/AndroidManifest.xml app/src/main/res/values/themes.xml
git commit -m "feat: manifest permissions and base theme"
```

---

## Task 2: Settings Data Layer

**Files:**
- Create: `app/src/main/java/com/navink/data/repository/SettingsRepository.kt`
- Create: `app/src/main/java/com/navink/di/DataStoreModule.kt`

- [ ] **Step 1: Create SettingsRepository**

```kotlin
// app/src/main/java/com/navink/data/repository/SettingsRepository.kt
package com.navink.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    data class Credentials(
        val serverUrl: String = "",
        val username: String = "",
        val password: String = "",
    ) {
        val hasCredentials: Boolean get() =
            serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }

    suspend fun getCredentials(): Credentials {
        val prefs = dataStore.data.first()
        return Credentials(
            serverUrl = prefs[SERVER_URL_KEY] ?: "",
            username = prefs[USERNAME_KEY] ?: "",
            password = prefs[PASSWORD_KEY] ?: "",
        )
    }

    suspend fun saveCredentials(serverUrl: String, username: String, password: String) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL_KEY] = serverUrl.trimEnd('/')
            prefs[USERNAME_KEY] = username
            prefs[PASSWORD_KEY] = password
        }
    }

    companion object {
        val SERVER_URL_KEY = stringPreferencesKey("server_url")
        val USERNAME_KEY = stringPreferencesKey("username")
        val PASSWORD_KEY = stringPreferencesKey("password")
    }
}
```

- [ ] **Step 2: Create DataStoreModule**

```kotlin
// app/src/main/java/com/navink/di/DataStoreModule.kt
package com.navink.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.navinkDataStore by preferencesDataStore(name = "navink_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.navinkDataStore
}
```

- [ ] **Step 3: Compile check**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**
```sh
git add app/src/main/java/com/navink/
git commit -m "feat: settings data layer (DataStore + SettingsRepository)"
```

---

## Task 3: Subsonic API Client

**Files:**
- Create: `app/src/main/java/com/navink/data/remote/dto/SubsonicDtos.kt`
- Create: `app/src/main/java/com/navink/data/remote/SubsonicService.kt`
- Create: `app/src/main/java/com/navink/data/remote/SubsonicAuthInterceptor.kt`

- [ ] **Step 1: Create Subsonic DTOs**

```kotlin
// app/src/main/java/com/navink/data/remote/dto/SubsonicDtos.kt
package com.navink.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SubsonicResponse(
    @SerializedName("subsonic-response") val response: SubsonicResponseBody,
)

data class SubsonicResponseBody(
    val status: String,
    val version: String,
    val artists: ArtistsResult? = null,
    val artist: ArtistDetailDto? = null,
    val album: AlbumDetailDto? = null,
    val searchResult3: SearchResult3Dto? = null,
    val starred2: Starred2Dto? = null,
    val error: ErrorDto? = null,
)

data class ErrorDto(val code: Int, val message: String)

data class ArtistsResult(val index: List<ArtistIndexDto> = emptyList())

data class ArtistIndexDto(
    val name: String,
    val artist: List<ArtistDto> = emptyList(),
)

data class ArtistDto(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val starred: String? = null,
)

data class ArtistDetailDto(
    val id: String,
    val name: String,
    val album: List<AlbumDto> = emptyList(),
    val starred: String? = null,
)

data class AlbumDto(
    val id: String,
    val artistId: String = "",
    val name: String,
    val year: Int? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val starred: String? = null,
)

data class AlbumDetailDto(
    val id: String,
    val artistId: String = "",
    val name: String,
    val coverArt: String? = null,
    val starred: String? = null,
    val song: List<SongDto> = emptyList(),
)

data class SongDto(
    val id: String,
    val albumId: String = "",
    val artistId: String = "",
    val title: String,
    val track: Int? = null,
    val duration: Int = 0,
    val coverArt: String? = null,
    val starred: String? = null,
)

data class SearchResult3Dto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList(),
)

data class Starred2Dto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList(),
)
```

- [ ] **Step 2: Create SubsonicService**

```kotlin
// app/src/main/java/com/navink/data/remote/SubsonicService.kt
package com.navink.data.remote

import com.navink.data.remote.dto.SubsonicResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicService {
    @GET("rest/ping.view")
    suspend fun ping(): SubsonicResponse

    @GET("rest/getArtists.view")
    suspend fun getArtists(): SubsonicResponse

    @GET("rest/getArtist.view")
    suspend fun getArtist(@Query("id") id: String): SubsonicResponse

    @GET("rest/getAlbum.view")
    suspend fun getAlbum(@Query("id") id: String): SubsonicResponse

    @GET("rest/search3.view")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 20,
        @Query("albumCount") albumCount: Int = 20,
        @Query("songCount") songCount: Int = 20,
    ): SubsonicResponse

    @GET("rest/star.view")
    suspend fun star(
        @Query("id") songId: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null,
    ): SubsonicResponse

    @GET("rest/unstar.view")
    suspend fun unstar(
        @Query("id") songId: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null,
    ): SubsonicResponse

    @GET("rest/getStarred2.view")
    suspend fun getStarred2(): SubsonicResponse
}
```

- [ ] **Step 3: Create SubsonicAuthInterceptor**

The interceptor rewrites the host from the placeholder base URL to the real server URL (read from SettingsRepository on each request), and appends auth query params.

```kotlin
// app/src/main/java/com/navink/data/remote/SubsonicAuthInterceptor.kt
package com.navink.data.remote

import com.navink.data.repository.SettingsRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class SubsonicAuthInterceptor @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val creds = runBlocking { settingsRepository.getCredentials() }
        val serverBase = creds.serverUrl.trimEnd('/')
        val original = chain.request()

        val rewrittenBase = "$serverBase/".toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val newUrl = original.url.newBuilder()
            .scheme(rewrittenBase.scheme)
            .host(rewrittenBase.host)
            .port(rewrittenBase.port)
            .addQueryParameter("u", creds.username)
            .addQueryParameter("p", creds.password)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "navink")
            .addQueryParameter("f", "json")
            .build()

        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}
```

- [ ] **Step 4: Compile check**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**
```sh
git add app/src/main/java/com/navink/data/remote/
git commit -m "feat: Subsonic API DTOs, service interface, and auth interceptor"
```

---

## Task 4: Room Entities + DAOs

**Files:**
- Create: `app/src/main/java/com/navink/data/local/entity/ArtistEntity.kt`
- Create: `app/src/main/java/com/navink/data/local/entity/AlbumEntity.kt`
- Create: `app/src/main/java/com/navink/data/local/entity/SongEntity.kt`
- Create: `app/src/main/java/com/navink/data/local/dao/ArtistDao.kt`
- Create: `app/src/main/java/com/navink/data/local/dao/AlbumDao.kt`
- Create: `app/src/main/java/com/navink/data/local/dao/SongDao.kt`
- Create: `app/src/main/java/com/navink/data/local/NavinkDatabase.kt`

- [ ] **Step 1: Write failing DAO test**

```kotlin
// app/src/test/java/com/navink/data/local/dao/SongDaoTest.kt
package com.navink.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.entity.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SongDaoTest {
    private lateinit var db: NavinkDatabase
    private lateinit var dao: SongDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NavinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.songDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `upsert and query songs for album`() = runTest {
        val songs = listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "Track 1", trackNumber = 1, duration = 240),
            SongEntity(id = "s2", albumId = "a1", artistId = "ar1", title = "Track 2", trackNumber = 2, duration = 180),
        )
        dao.upsertAll(songs)
        val result = dao.songsForAlbum("a1").first()
        assertEquals(2, result.size)
        assertEquals("Track 1", result[0].title)
    }

    @Test
    fun `setDownloaded updates localPath`() = runTest {
        dao.upsertAll(listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "Track 1", duration = 240)
        ))
        dao.setDownloaded("s1", "/data/music/track1.mp3")
        val song = dao.songById("s1")
        assertTrue(song!!.isDownloaded)
        assertEquals("/data/music/track1.mp3", song.localPath)
    }

    @Test
    fun `setStarred updates isStarred flag`() = runTest {
        dao.upsertAll(listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "Track 1", duration = 240)
        ))
        dao.setStarred("s1", true)
        val starred = dao.starredSongs().first()
        assertEquals(1, starred.size)
        assertEquals("s1", starred[0].id)
    }
}
```

- [ ] **Step 2: Run test — expect it to fail (classes don't exist yet)**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew testDebugUnitTest --tests "com.navink.data.local.dao.SongDaoTest" 2>&1 | tail -30
```
Expected: compilation error (SongEntity, SongDao, NavinkDatabase not found)

- [ ] **Step 3: Create entities**

```kotlin
// app/src/main/java/com/navink/data/local/entity/ArtistEntity.kt
package com.navink.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val albumCount: Int = 0,
    val isStarred: Boolean = false,
)
```

```kotlin
// app/src/main/java/com/navink/data/local/entity/AlbumEntity.kt
package com.navink.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class AlbumEntity(
    @PrimaryKey val id: String,
    val artistId: String,
    val name: String,
    val year: Int? = null,
    val coverArtId: String? = null,
    val songCount: Int = 0,
    val isStarred: Boolean = false,
)
```

```kotlin
// app/src/main/java/com/navink/data/local/entity/SongEntity.kt
package com.navink.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SongEntity(
    @PrimaryKey val id: String,
    val albumId: String,
    val artistId: String,
    val title: String,
    val trackNumber: Int? = null,
    val duration: Int = 0,
    val coverArtId: String? = null,
    val isStarred: Boolean = false,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
)
```

- [ ] **Step 4: Create DAOs**

```kotlin
// app/src/main/java/com/navink/data/local/dao/ArtistDao.kt
package com.navink.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.navink.data.local.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Query("SELECT * FROM ArtistEntity ORDER BY name ASC")
    fun allArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM ArtistEntity WHERE id = :id")
    suspend fun artistById(id: String): ArtistEntity?

    @Upsert
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("UPDATE ArtistEntity SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("SELECT * FROM ArtistEntity WHERE isStarred = 1 ORDER BY name ASC")
    fun starredArtists(): Flow<List<ArtistEntity>>
}
```

```kotlin
// app/src/main/java/com/navink/data/local/dao/AlbumDao.kt
package com.navink.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.navink.data.local.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM AlbumEntity WHERE artistId = :artistId ORDER BY year ASC, name ASC")
    fun albumsForArtist(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM AlbumEntity WHERE id = :id")
    suspend fun albumById(id: String): AlbumEntity?

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("UPDATE AlbumEntity SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("SELECT * FROM AlbumEntity WHERE isStarred = 1 ORDER BY name ASC")
    fun starredAlbums(): Flow<List<AlbumEntity>>
}
```

```kotlin
// app/src/main/java/com/navink/data/local/dao/SongDao.kt
package com.navink.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.navink.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId ORDER BY trackNumber ASC, title ASC")
    fun songsForAlbum(albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM SongEntity WHERE id = :id")
    suspend fun songById(id: String): SongEntity?

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("UPDATE SongEntity SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("UPDATE SongEntity SET isDownloaded = 1, localPath = :path WHERE id = :id")
    suspend fun setDownloaded(id: String, path: String)

    @Query("SELECT * FROM SongEntity WHERE isStarred = 1 ORDER BY title ASC")
    fun starredSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId ORDER BY trackNumber ASC, title ASC")
    suspend fun songsForAlbumOnce(albumId: String): List<SongEntity>
}
```

- [ ] **Step 5: Create NavinkDatabase**

```kotlin
// app/src/main/java/com/navink/data/local/NavinkDatabase.kt
package com.navink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.SongEntity

@Database(
    entities = [ArtistEntity::class, AlbumEntity::class, SongEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NavinkDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun songDao(): SongDao
}
```

- [ ] **Step 6: Run tests — expect PASS**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew testDebugUnitTest --tests "com.navink.data.local.dao.SongDaoTest" 2>&1 | tail -20
```
Expected: `3 tests completed, 0 failures`

- [ ] **Step 7: Commit**
```sh
git add app/src/main/java/com/navink/data/local/ app/src/test/java/com/navink/data/local/
git commit -m "feat: Room entities, DAOs, and database (TDD green)"
```

---

## Task 5: DI Modules — Network + Database

**Files:**
- Create: `app/src/main/java/com/navink/di/NetworkModule.kt`
- Create: `app/src/main/java/com/navink/di/DatabaseModule.kt`

- [ ] **Step 1: Create NetworkModule**

```kotlin
// app/src/main/java/com/navink/di/NetworkModule.kt
package com.navink.di

import com.navink.data.remote.SubsonicAuthInterceptor
import com.navink.data.remote.SubsonicService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // Placeholder base URL — SubsonicAuthInterceptor rewrites it per-request.
    private const val PLACEHOLDER_URL = "http://localhost/"

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: SubsonicAuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideSubsonicService(retrofit: Retrofit): SubsonicService =
        retrofit.create(SubsonicService::class.java)
}
```

- [ ] **Step 2: Create DatabaseModule**

```kotlin
// app/src/main/java/com/navink/di/DatabaseModule.kt
package com.navink.di

import android.content.Context
import androidx.room.Room
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NavinkDatabase =
        Room.databaseBuilder(context, NavinkDatabase::class.java, "navink.db").build()

    @Provides fun provideArtistDao(db: NavinkDatabase): ArtistDao = db.artistDao()
    @Provides fun provideAlbumDao(db: NavinkDatabase): AlbumDao = db.albumDao()
    @Provides fun provideSongDao(db: NavinkDatabase): SongDao = db.songDao()
}
```

- [ ] **Step 3: Compile check**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**
```sh
git add app/src/main/java/com/navink/di/
git commit -m "feat: Hilt DI modules for network and database"
```

---

## Task 6: Application Entry Points

**Files:**
- Create: `app/src/main/java/com/navink/NavinkApp.kt`
- Create: `app/src/main/java/com/navink/MainActivity.kt`

- [ ] **Step 1: Create NavinkApp**

```kotlin
// app/src/main/java/com/navink/NavinkApp.kt
package com.navink

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NavinkApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

- [ ] **Step 2: Create MainActivity (skeleton — NavGraph added in Task 9)**

```kotlin
// app/src/main/java/com/navink/MainActivity.kt
package com.navink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.navink.ui.theme.NavinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NavinkTheme {
                // NavGraph wired in Task 9
            }
        }
    }
}
```

- [ ] **Step 3: Compile check**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -20
```
Expected: error on `NavinkTheme` (not created yet) — that's fine, will resolve in Task 8.

If you see other errors, fix them before proceeding.

- [ ] **Step 4: Commit**
```sh
git add app/src/main/java/com/navink/NavinkApp.kt app/src/main/java/com/navink/MainActivity.kt
git commit -m "feat: application entry points (NavinkApp, MainActivity)"
```

---

## Task 7: MusicRepository + SyncRepository

**Files:**
- Create: `app/src/main/java/com/navink/data/repository/MusicRepository.kt`
- Create: `app/src/main/java/com/navink/data/repository/SyncRepository.kt`
- Create: `app/src/test/java/com/navink/data/repository/MusicRepositoryTest.kt`

- [ ] **Step 1: Write failing repository test**

```kotlin
// app/src/test/java/com/navink/data/repository/MusicRepositoryTest.kt
package com.navink.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.remote.SubsonicService
import com.navink.data.remote.dto.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class MusicRepositoryTest {
    private lateinit var db: NavinkDatabase
    private lateinit var service: SubsonicService
    private lateinit var syncRepo: SyncRepository
    private lateinit var musicRepo: MusicRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NavinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        service = mockk()
        syncRepo = SyncRepository(service, db.artistDao(), db.albumDao(), db.songDao())
        musicRepo = MusicRepository(db.artistDao(), db.albumDao(), db.songDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `sync writes artists to Room and allArtists flow returns them`() = runTest {
        coEvery { service.getArtists() } returns SubsonicResponse(
            SubsonicResponseBody(
                status = "ok", version = "1.16.1",
                artists = ArtistsResult(index = listOf(
                    ArtistIndexDto("A", listOf(ArtistDto(id = "1", name = "ABBA", albumCount = 3)))
                ))
            )
        )
        coEvery { service.getArtist("1") } returns SubsonicResponse(
            SubsonicResponseBody(
                status = "ok", version = "1.16.1",
                artist = ArtistDetailDto(id = "1", name = "ABBA", album = listOf(
                    AlbumDto(id = "al1", artistId = "1", name = "Gold", year = 1992, songCount = 19)
                ))
            )
        )
        coEvery { service.getAlbum("al1") } returns SubsonicResponse(
            SubsonicResponseBody(
                status = "ok", version = "1.16.1",
                album = AlbumDetailDto(id = "al1", artistId = "1", name = "Gold",
                    song = listOf(SongDto(id = "s1", albumId = "al1", artistId = "1", title = "Dancing Queen", track = 1, duration = 230))
                )
            )
        )

        syncRepo.syncAll()

        val artists = musicRepo.allArtists().first()
        assertEquals(1, artists.size)
        assertEquals("ABBA", artists[0].name)
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew testDebugUnitTest --tests "com.navink.data.repository.MusicRepositoryTest" 2>&1 | tail -20
```

- [ ] **Step 3: Create MusicRepository**

```kotlin
// app/src/main/java/com/navink/data/repository/MusicRepository.kt
package com.navink.data.repository

import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
) {
    fun allArtists(): Flow<List<ArtistEntity>> = artistDao.allArtists()
    fun albumsForArtist(artistId: String): Flow<List<AlbumEntity>> = albumDao.albumsForArtist(artistId)
    fun songsForAlbum(albumId: String): Flow<List<SongEntity>> = songDao.songsForAlbum(albumId)
    fun starredSongs(): Flow<List<SongEntity>> = songDao.starredSongs()
    fun starredAlbums(): Flow<List<AlbumEntity>> = albumDao.starredAlbums()
    fun starredArtists(): Flow<List<ArtistEntity>> = artistDao.starredArtists()
    suspend fun songsForAlbumOnce(albumId: String): List<SongEntity> = songDao.songsForAlbumOnce(albumId)
    suspend fun songById(id: String): SongEntity? = songDao.songById(id)
}
```

- [ ] **Step 4: Create SyncRepository**

```kotlin
// app/src/main/java/com/navink/data/repository/SyncRepository.kt
package com.navink.data.repository

import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.remote.SubsonicService
import com.navink.data.remote.dto.AlbumDto
import com.navink.data.remote.dto.ArtistDto
import com.navink.data.remote.dto.SongDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val service: SubsonicService,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
) {
    suspend fun syncAll() {
        val artistsResponse = service.getArtists()
        val allArtistDtos = artistsResponse.response.artists?.index
            ?.flatMap { it.artist } ?: return

        artistDao.upsertAll(allArtistDtos.map { it.toEntity() })

        for (artistDto in allArtistDtos) {
            val artistDetail = service.getArtist(artistDto.id).response.artist ?: continue
            albumDao.upsertAll(artistDetail.album.map { it.toEntity(artistId = artistDto.id) })

            for (albumDto in artistDetail.album) {
                val albumDetail = service.getAlbum(albumDto.id).response.album ?: continue
                songDao.upsertAll(albumDetail.song.map { it.toEntity(albumId = albumDto.id, artistId = artistDto.id) })
            }
        }
    }

    private fun ArtistDto.toEntity() = ArtistEntity(
        id = id,
        name = name,
        albumCount = albumCount,
        isStarred = starred != null,
    )

    private fun AlbumDto.toEntity(artistId: String) = AlbumEntity(
        id = id,
        artistId = this.artistId.ifBlank { artistId },
        name = name,
        year = year,
        coverArtId = coverArt,
        songCount = songCount,
        isStarred = starred != null,
    )

    private fun SongDto.toEntity(albumId: String, artistId: String) = SongEntity(
        id = id,
        albumId = this.albumId.ifBlank { albumId },
        artistId = this.artistId.ifBlank { artistId },
        title = title,
        trackNumber = track,
        duration = duration,
        coverArtId = coverArt,
        isStarred = starred != null,
    )
}
```

- [ ] **Step 5: Run test — expect PASS**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew testDebugUnitTest --tests "com.navink.data.repository.MusicRepositoryTest" 2>&1 | tail -20
```
Expected: `1 test completed, 0 failures`

- [ ] **Step 6: Commit**
```sh
git add app/src/main/java/com/navink/data/repository/MusicRepository.kt \
        app/src/main/java/com/navink/data/repository/SyncRepository.kt \
        app/src/test/java/com/navink/data/repository/
git commit -m "feat: MusicRepository and SyncRepository (TDD green)"
```

---

## Task 8: Theme + NavinkApp compile fix

**Files:**
- Create: `app/src/main/java/com/navink/ui/theme/Theme.kt`

- [ ] **Step 1: Create NavinkTheme**

```kotlin
// app/src/main/java/com/navink/ui/theme/Theme.kt
package com.navink.ui.theme

import androidx.compose.runtime.Composable
import com.mudita.mmd.ui.theme.ThemeMMD

@Composable
fun NavinkTheme(content: @Composable () -> Unit) {
    ThemeMMD(content = content)
}
```

- [ ] **Step 2: Compile check (NavinkApp + MainActivity should now compile)**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**
```sh
git add app/src/main/java/com/navink/ui/theme/
git commit -m "feat: NavinkTheme wrapping ThemeMMD"
```

---

## Task 9: Settings UI

**Files:**
- Create: `app/src/main/java/com/navink/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/navink/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create SettingsViewModel**

```kotlin
// app/src/main/java/com/navink/ui/settings/SettingsViewModel.kt
package com.navink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.remote.SubsonicService
import com.navink.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val service: SubsonicService,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val creds = settingsRepository.getCredentials()
            _state.value = _state.value.copy(
                serverUrl = creds.serverUrl,
                username = creds.username,
                password = creds.password,
            )
        }
    }

    fun onServerUrlChange(v: String) { _state.value = _state.value.copy(serverUrl = v) }
    fun onUsernameChange(v: String) { _state.value = _state.value.copy(username = v) }
    fun onPasswordChange(v: String) { _state.value = _state.value.copy(password = v) }

    fun connect(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.serverUrl.isBlank() || s.username.isBlank() || s.password.isBlank()) {
            _state.value = s.copy(error = "All fields required")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                settingsRepository.saveCredentials(s.serverUrl, s.username, s.password)
                val response = service.ping()
                if (response.response.status == "ok") {
                    _state.value = _state.value.copy(isLoading = false, connected = true)
                    onSuccess()
                } else {
                    val msg = response.response.error?.message ?: "Connection failed"
                    _state.value = _state.value.copy(isLoading = false, error = msg)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Cannot reach server: ${e.message}")
            }
        }
    }
}
```

- [ ] **Step 2: Create SettingsScreen**

```kotlin
// app/src/main/java/com/navink/ui/settings/SettingsScreen.kt
package com.navink.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mudita.mmd.ui.components.ButtonMMD
import com.mudita.mmd.ui.components.TextFieldMMD

@Composable
fun SettingsScreen(
    onConnected: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Navink", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))

        TextFieldMMD(
            value = state.serverUrl,
            onValueChange = viewModel::onServerUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("https://music.example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))

        TextFieldMMD(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))

        TextFieldMMD(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(24.dp))

        if (state.error != null) {
            Text(text = state.error!!, color = androidx.compose.ui.graphics.Color.Red)
            Spacer(Modifier.height(8.dp))
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            ButtonMMD(
                onClick = { viewModel.connect(onConnected) },
                modifier = Modifier.fillMaxWidth().height(80.dp),
            ) {
                Text("Connect")
            }
        }
    }
}
```

- [ ] **Step 3: Compile check**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -20
```

- [ ] **Step 4: Commit**
```sh
git add app/src/main/java/com/navink/ui/settings/
git commit -m "feat: settings screen and view model"
```

---

## Task 10: BrowseViewModel + Browse Screens

**Files:**
- Create: `app/src/main/java/com/navink/ui/browse/BrowseViewModel.kt`
- Create: `app/src/main/java/com/navink/ui/browse/ArtistsScreen.kt`
- Create: `app/src/main/java/com/navink/ui/browse/AlbumsScreen.kt`
- Create: `app/src/main/java/com/navink/ui/browse/SongsScreen.kt`

- [ ] **Step 1: Create BrowseViewModel**

```kotlin
// app/src/main/java/com/navink/ui/browse/BrowseViewModel.kt
package com.navink.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.repository.MusicRepository
import com.navink.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val artists: List<ArtistEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val songs: List<SongEntity> = emptyList(),
    val isSyncing: Boolean = false,
    val syncError: String? = null,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init { syncOnLaunch() }

    fun syncOnLaunch() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true, syncError = null)
            try {
                syncRepository.syncAll()
            } catch (e: Exception) {
                _state.value = _state.value.copy(syncError = e.message)
            } finally {
                _state.value = _state.value.copy(isSyncing = false)
            }
        }
    }

    fun observeArtists() {
        viewModelScope.launch {
            musicRepository.allArtists().collect { list ->
                _state.value = _state.value.copy(artists = list)
            }
        }
    }

    fun observeAlbums(artistId: String) {
        viewModelScope.launch {
            musicRepository.albumsForArtist(artistId).collect { list ->
                _state.value = _state.value.copy(albums = list)
            }
        }
    }

    fun observeSongs(albumId: String) {
        viewModelScope.launch {
            musicRepository.songsForAlbum(albumId).collect { list ->
                _state.value = _state.value.copy(songs = list)
            }
        }
    }
}
```

- [ ] **Step 2: Create ArtistsScreen**

```kotlin
// app/src/main/java/com/navink/ui/browse/ArtistsScreen.kt
package com.navink.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.navink.data.local.entity.ArtistEntity

@Composable
fun ArtistsScreen(
    onArtistClick: (String) -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.observeArtists() }

    // Pull-to-refresh: re-call syncOnLaunch() via swipe. Use Compose's pullRefresh modifier.
    // Import: androidx.compose.material3.pulltorefresh (or rememberPullToRefreshState from material3)
    // For simplicity here, the Refresh icon in the top app bar calls viewModel.syncOnLaunch().

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Library") },
                actions = {
                    if (!state.isSyncing) {
                        TextButton(onClick = { viewModel.syncOnLaunch() }) { Text("↻") }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            )
        },
        bottomBar = miniPlayer,
    ) { padding ->
        if (state.isSyncing && state.artists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                if (state.syncError != null && state.artists.isEmpty()) {
                    item {
                        Text(
                            text = "Cannot sync: ${state.syncError}",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(state.artists, key = { it.id }) { artist ->
                    ArtistRow(artist = artist, onClick = { onArtistClick(artist.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ArtistRow(artist: ArtistEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column {
            Text(text = artist.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = "${artist.albumCount} albums", style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

- [ ] **Step 3: Create AlbumsScreen**

```kotlin
// app/src/main/java/com/navink/ui/browse/AlbumsScreen.kt
package com.navink.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.navink.data.local.entity.AlbumEntity

@Composable
fun AlbumsScreen(
    artistId: String,
    coverArtUrl: (String?) -> String?,
    onAlbumClick: (String) -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(artistId) { viewModel.observeAlbums(artistId) }

    Scaffold(bottomBar = miniPlayer) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(state.albums, key = { it.id }) { album ->
                AlbumRow(
                    album = album,
                    coverArtUrl = coverArtUrl(album.coverArtId),
                    onClick = { onAlbumClick(album.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AlbumRow(album: AlbumEntity, coverArtUrl: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverArtUrl)
                .crossfade(false)
                .build(),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = album.name, style = MaterialTheme.typography.bodyLarge)
            album.year?.let { Text(text = it.toString(), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
```

- [ ] **Step 4: Create SongsScreen**

```kotlin
// app/src/main/java/com/navink/ui/browse/SongsScreen.kt
package com.navink.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.navink.data.local.entity.SongEntity

@Composable
fun SongsScreen(
    albumId: String,
    onSongClick: (songId: String, albumId: String) -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(albumId) { viewModel.observeSongs(albumId) }

    Scaffold(bottomBar = miniPlayer) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(state.songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    onClick = { onSongClick(song.id, albumId) },
                )
                HorizontalDivider()
            }
        }
    }
}

private fun Int.toMinSec(): String {
    val m = this / 60
    val s = this % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

@Composable
private fun SongRow(song: SongEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        song.trackNumber?.let {
            Text(
                text = it.toString().padStart(2),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(28.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(text = song.title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(text = song.duration.toMinSec(), style = MaterialTheme.typography.bodySmall)
        if (song.isDownloaded) {
            Spacer(Modifier.width(4.dp))
            Text(text = "↓", style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

- [ ] **Step 5: Compile check**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -20
```

- [ ] **Step 6: Commit**
```sh
git add app/src/main/java/com/navink/ui/browse/
git commit -m "feat: browse screens (artists, albums, songs) and BrowseViewModel"
```

---

## Task 11: PlayerState + PlaybackService + PlayerController

**Files:**
- Create: `app/src/main/java/com/navink/player/PlayerState.kt`
- Create: `app/src/main/java/com/navink/player/PlaybackService.kt`
- Create: `app/src/main/java/com/navink/player/PlayerController.kt`
- Create: `app/src/main/java/com/navink/di/PlayerModule.kt`
- Create: `app/src/test/java/com/navink/player/PlayerControllerTest.kt`

- [ ] **Step 1: Write failing PlayerController test**

```kotlin
// app/src/test/java/com/navink/player/PlayerControllerTest.kt
package com.navink.player

import com.navink.data.local.entity.SongEntity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerControllerTest {
    @Test
    fun `buildQueue returns songs from startIndex onwards`() {
        val songs = listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "T1", duration = 100),
            SongEntity(id = "s2", albumId = "a1", artistId = "ar1", title = "T2", duration = 100),
            SongEntity(id = "s3", albumId = "a1", artistId = "ar1", title = "T3", duration = 100),
        )
        val queue = PlayerController.buildQueue(songs = songs, startSongId = "s2")
        assertEquals(2, queue.size)
        assertEquals("s2", queue[0].id)
        assertEquals("s3", queue[1].id)
    }

    @Test
    fun `buildQueue with unknown startSongId returns full list`() {
        val songs = listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "T1", duration = 100),
        )
        val queue = PlayerController.buildQueue(songs = songs, startSongId = "unknown")
        assertEquals(1, queue.size)
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew testDebugUnitTest --tests "com.navink.player.PlayerControllerTest" 2>&1 | tail -20
```

- [ ] **Step 3: Create PlayerState**

```kotlin
// app/src/main/java/com/navink/player/PlayerState.kt
package com.navink.player

data class PlayerState(
    val currentSongId: String? = null,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val currentAlbum: String = "",
    val currentCoverArtId: String? = null,
    val isPlaying: Boolean = false,
    val hasQueue: Boolean = false,
)
```

- [ ] **Step 4: Create PlaybackService**

```kotlin
// app/src/main/java/com/navink/player/PlaybackService.kt
package com.navink.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
```

- [ ] **Step 5: Create PlayerController**

```kotlin
// app/src/main/java/com/navink/player/PlayerController.kt
package com.navink.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.navink.data.local.entity.SongEntity
import com.navink.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    suspend fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controller = MediaController.Builder(context, token).buildAsync().await()
        controller?.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                _state.value = _state.value.copy(
                    currentTitle = metadata.title?.toString() ?: "",
                    currentArtist = metadata.artist?.toString() ?: "",
                    currentAlbum = metadata.albumTitle?.toString() ?: "",
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                _state.value = _state.value.copy(
                    currentSongId = item?.mediaId,
                    hasQueue = (controller?.mediaItemCount ?: 0) > 0,
                )
            }
        })
    }

    fun playAlbum(songs: List<SongEntity>, startSongId: String) {
        val queue = buildQueue(songs, startSongId)
        val creds = runBlocking { settingsRepository.getCredentials() }
        val items = queue.map { song ->
            val uri = song.localPath?.let { "file://$it" }
                ?: "${creds.serverUrl}/rest/stream.view?id=${song.id}&u=${creds.username}&p=${creds.password}&v=1.16.1&c=navink"
            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .build()
                )
                .build()
        }
        controller?.apply {
            setMediaItems(items)
            prepare()
            play()
        }
        val first = queue.firstOrNull()
        _state.value = _state.value.copy(
            currentSongId = first?.id,
            currentCoverArtId = first?.coverArtId,
            hasQueue = items.isNotEmpty(),
        )
    }

    fun playPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }

    fun disconnect() { controller?.release() }

    companion object {
        fun buildQueue(songs: List<SongEntity>, startSongId: String): List<SongEntity> {
            val startIndex = songs.indexOfFirst { it.id == startSongId }
            return if (startIndex < 0) songs else songs.subList(startIndex, songs.size)
        }
    }
}
```

Note: `kotlinx-coroutines-guava` is needed for `await()` on `ListenableFuture`. Add to `app/build.gradle.kts`:
```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")
```

- [ ] **Step 6: Create PlayerModule**

```kotlin
// app/src/main/java/com/navink/di/PlayerModule.kt
package com.navink.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context).build()
}
```

- [ ] **Step 7: Run PlayerController tests — expect PASS**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew testDebugUnitTest --tests "com.navink.player.PlayerControllerTest" 2>&1 | tail -20
```
Expected: `2 tests completed, 0 failures`

- [ ] **Step 8: Compile check**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -20
```

- [ ] **Step 9: Commit**
```sh
git add app/src/main/java/com/navink/player/ \
        app/src/main/java/com/navink/di/PlayerModule.kt \
        app/src/test/java/com/navink/player/ \
        app/build.gradle.kts
git commit -m "feat: PlaybackService, PlayerController, and PlayerModule (TDD green)"
```

---

## Task 12: PlayerViewModel + NowPlayingScreen + MiniPlayer

**Files:**
- Create: `app/src/main/java/com/navink/ui/player/PlayerViewModel.kt`
- Create: `app/src/main/java/com/navink/ui/player/NowPlayingScreen.kt`
- Create: `app/src/main/java/com/navink/ui/player/MiniPlayer.kt`

- [ ] **Step 1: Create PlayerViewModel**

```kotlin
// app/src/main/java/com/navink/ui/player/PlayerViewModel.kt
package com.navink.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.repository.MusicRepository
import com.navink.data.repository.SettingsRepository
import com.navink.player.PlayerController
import com.navink.player.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<PlayerState> = playerController.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, playerController.state.value)

    fun playSongFromAlbum(songId: String, albumId: String) {
        viewModelScope.launch {
            val songs = musicRepository.songsForAlbumOnce(albumId)
            playerController.playAlbum(songs, songId)
        }
    }

    fun coverArtUrl(coverArtId: String?): String? {
        if (coverArtId == null) return null
        return viewModelScope.run {
            var url: String? = null
            launch {
                val creds = settingsRepository.getCredentials()
                url = "${creds.serverUrl}/rest/getCoverArt.view?id=$coverArtId&u=${creds.username}&p=${creds.password}&v=1.16.1&c=navink"
            }
            url
        }
    }

    fun playPause() = playerController.playPause()
    fun next() = playerController.next()
    fun previous() = playerController.previous()
}
```

Note: `coverArtUrl` above is simplified — in screens use a separate helper that reads credentials synchronously from the ViewModel's `settingsRepository`. See NowPlayingScreen for the clean usage pattern.

- [ ] **Step 2: Create NowPlayingScreen**

```kotlin
// app/src/main/java/com/navink/ui/player/NowPlayingScreen.kt
package com.navink.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mudita.mmd.ui.components.ButtonMMD
import com.mudita.mmd.ui.components.OutlinedButtonMMD

@Composable
fun NowPlayingScreen(
    coverArtUrl: String?,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        OutlinedButtonMMD(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text("← Back", color = Color.White)
        }
        Spacer(Modifier.height(24.dp))

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverArtUrl)
                .crossfade(false)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(24.dp))

        Text(
            text = state.currentTitle.ifBlank { "—" },
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        Text(
            text = state.currentArtist,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        Text(
            text = state.currentAlbum,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButtonMMD(
                onClick = { viewModel.previous() },
                modifier = Modifier.weight(1f).height(80.dp),
            ) { Text("⏮", color = Color.White) }

            ButtonMMD(
                onClick = { viewModel.playPause() },
                modifier = Modifier.weight(1f).height(80.dp),
            ) { Text(if (state.isPlaying) "⏸" else "▶") }

            OutlinedButtonMMD(
                onClick = { viewModel.next() },
                modifier = Modifier.weight(1f).height(80.dp),
            ) { Text("⏭", color = Color.White) }
        }
        Spacer(Modifier.height(16.dp))
    }
}
```

- [ ] **Step 3: Create MiniPlayer**

```kotlin
// app/src/main/java/com/navink/ui/player/MiniPlayer.kt
package com.navink.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MiniPlayer(
    onTap: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    if (!state.hasQueue) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onTap,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = state.currentTitle.ifBlank { "—" }, color = Color.White, maxLines = 1)
            Text(text = state.currentArtist, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        TextButton(onClick = { viewModel.playPause() }) {
            Text(text = if (state.isPlaying) "⏸" else "▶", color = Color.White)
        }
    }
}
```

- [ ] **Step 4: Compile check**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -20
```

- [ ] **Step 5: Commit**
```sh
git add app/src/main/java/com/navink/ui/player/
git commit -m "feat: PlayerViewModel, NowPlayingScreen, MiniPlayer"
```

---

## Task 13: NavGraph + MainActivity wiring

**Files:**
- Create: `app/src/main/java/com/navink/NavGraph.kt`
- Modify: `app/src/main/java/com/navink/MainActivity.kt`

- [ ] **Step 1: Create NavGraph**

```kotlin
// app/src/main/java/com/navink/NavGraph.kt
package com.navink

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.navink.data.repository.SettingsRepository
import com.navink.ui.browse.AlbumsScreen
import com.navink.ui.browse.ArtistsScreen
import com.navink.ui.browse.SongsScreen
import com.navink.ui.favourites.FavouritesScreen
import com.navink.ui.player.MiniPlayer
import com.navink.ui.player.NowPlayingScreen
import com.navink.ui.player.PlayerViewModel
import com.navink.ui.search.SearchScreen
import com.navink.ui.settings.SettingsScreen
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@Composable
fun NavGraph(
    settingsRepository: SettingsRepository,
) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val creds = remember { runBlocking { settingsRepository.getCredentials() } }

    fun coverArtUrl(id: String?): String? {
        if (id == null || creds.serverUrl.isBlank()) return null
        return "${creds.serverUrl}/rest/getCoverArt.view?id=$id&u=${creds.username}&p=${creds.password}&v=1.16.1&c=navink"
    }

    val miniPlayer: @Composable () -> Unit = {
        MiniPlayer(onTap = { navController.navigate("nowplaying") }, viewModel = playerViewModel)
    }

    val startDestination = if (creds.hasCredentials) "browse/artists" else "settings"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("settings") {
            SettingsScreen(onConnected = {
                navController.navigate("browse/artists") {
                    popUpTo("settings") { inclusive = true }
                }
            })
        }

        composable("browse/artists") {
            ArtistsScreen(
                onArtistClick = { artistId -> navController.navigate("browse/albums/$artistId") },
                miniPlayer = miniPlayer,
            )
        }

        composable(
            "browse/albums/{artistId}",
            arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
        ) { back ->
            val artistId = back.arguments!!.getString("artistId")!!
            AlbumsScreen(
                artistId = artistId,
                coverArtUrl = ::coverArtUrl,
                onAlbumClick = { albumId -> navController.navigate("browse/songs/$albumId") },
                miniPlayer = miniPlayer,
            )
        }

        composable(
            "browse/songs/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
        ) { back ->
            val albumId = back.arguments!!.getString("albumId")!!
            SongsScreen(
                albumId = albumId,
                onSongClick = { songId, aId ->
                    playerViewModel.playSongFromAlbum(songId, aId)
                    navController.navigate("nowplaying")
                },
                miniPlayer = miniPlayer,
            )
        }

        composable("search") {
            SearchScreen(
                onSongClick = { songId, albumId ->
                    playerViewModel.playSongFromAlbum(songId, albumId)
                    navController.navigate("nowplaying")
                },
                onAlbumClick = { albumId -> navController.navigate("browse/songs/$albumId") },
                onArtistClick = { artistId -> navController.navigate("browse/albums/$artistId") },
                miniPlayer = miniPlayer,
            )
        }

        composable("favourites") {
            FavouritesScreen(
                onSongClick = { songId, albumId ->
                    playerViewModel.playSongFromAlbum(songId, albumId)
                    navController.navigate("nowplaying")
                },
                miniPlayer = miniPlayer,
            )
        }

        composable("nowplaying") {
            val playerState by playerViewModel.state.collectAsState()
            NowPlayingScreen(
                coverArtUrl = coverArtUrl(playerState.currentCoverArtId),
                onBack = { navController.popBackStack() },
                viewModel = playerViewModel,
            )
        }

        composable("settings/edit") {
            SettingsScreen(onConnected = { navController.popBackStack() })
        }
    }
}
```

- [ ] **Step 2: Wire NavGraph into MainActivity**

```kotlin
// app/src/main/java/com/navink/MainActivity.kt
package com.navink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.navink.data.repository.SettingsRepository
import com.navink.player.PlayerController
import com.navink.ui.theme.NavinkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { playerController.connect() }
        setContent {
            NavinkTheme {
                NavGraph(settingsRepository = settingsRepository)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerController.disconnect()
    }
}
```

- [ ] **Step 3: Compile check**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -30
```

Note: This will fail if SearchScreen or FavouritesScreen don't exist yet (they're referenced in NavGraph). Create stub files:

```kotlin
// app/src/main/java/com/navink/ui/search/SearchScreen.kt
package com.navink.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SearchScreen(
    onSongClick: (songId: String, albumId: String) -> Unit,
    onAlbumClick: (albumId: String) -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    miniPlayer: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Search — coming soon")
    }
}
```

```kotlin
// app/src/main/java/com/navink/ui/favourites/FavouritesScreen.kt
package com.navink.ui.favourites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun FavouritesScreen(
    onSongClick: (songId: String, albumId: String) -> Unit,
    miniPlayer: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Favourites — coming soon")
    }
}
```

Re-run compile check after adding stubs.

- [ ] **Step 4: Run all unit tests**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew testDebugUnitTest 2>&1 | tail -20
```
Expected: all green.

- [ ] **Step 5: Commit**
```sh
git add app/src/main/java/com/navink/NavGraph.kt \
        app/src/main/java/com/navink/MainActivity.kt \
        app/src/main/java/com/navink/ui/search/SearchScreen.kt \
        app/src/main/java/com/navink/ui/favourites/FavouritesScreen.kt
git commit -m "feat: NavGraph wiring and MainActivity (app navigable)"
```

---

## Task 14: Search

**Files:**
- Create: `app/src/main/java/com/navink/ui/search/SearchViewModel.kt`
- Modify: `app/src/main/java/com/navink/ui/search/SearchScreen.kt` (replace stub)

- [ ] **Step 1: Create SearchViewModel**

```kotlin
// app/src/main/java/com/navink/ui/search/SearchViewModel.kt
package com.navink.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.remote.SubsonicService
import com.navink.data.remote.dto.AlbumDto
import com.navink.data.remote.dto.ArtistDto
import com.navink.data.remote.dto.SongDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val artists: List<ArtistDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
    val songs: List<SongDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val service: SubsonicService,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun onQueryChange(q: String) { _state.value = _state.value.copy(query = q) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = service.search3(q)
                val result = response.response.searchResult3
                _state.value = _state.value.copy(
                    artists = result?.artist ?: emptyList(),
                    albums = result?.album ?: emptyList(),
                    songs = result?.song ?: emptyList(),
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
```

- [ ] **Step 2: Replace SearchScreen stub with full implementation**

```kotlin
// app/src/main/java/com/navink/ui/search/SearchScreen.kt
package com.navink.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mudita.mmd.ui.components.TextFieldMMD

@Composable
fun SearchScreen(
    onSongClick: (songId: String, albumId: String) -> Unit,
    onAlbumClick: (albumId: String) -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(bottomBar = miniPlayer) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            TextFieldMMD(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            )
            Spacer(Modifier.height(8.dp))

            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                LazyColumn {
                    if (state.artists.isNotEmpty()) {
                        item { Text("Artists", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(state.artists) { artist ->
                            Row(
                                Modifier.fillMaxWidth().height(56.dp)
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onArtistClick(artist.id) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) { Text(artist.name) }
                            HorizontalDivider()
                        }
                    }
                    if (state.albums.isNotEmpty()) {
                        item { Text("Albums", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(state.albums) { album ->
                            Row(
                                Modifier.fillMaxWidth().height(56.dp)
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onAlbumClick(album.id) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) { Text(album.name) }
                            HorizontalDivider()
                        }
                    }
                    if (state.songs.isNotEmpty()) {
                        item { Text("Songs", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(state.songs) { song ->
                            Row(
                                Modifier.fillMaxWidth().height(56.dp)
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                        onSongClick(song.id, song.albumId)
                                    }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) { Text(song.title) }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Compile check**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -20
```

- [ ] **Step 4: Commit**
```sh
git add app/src/main/java/com/navink/ui/search/
git commit -m "feat: search screen and view model"
```

---

## Task 15: Favourites

**Files:**
- Create: `app/src/main/java/com/navink/data/repository/FavouritesRepository.kt`
- Create: `app/src/main/java/com/navink/ui/favourites/FavouritesViewModel.kt`
- Modify: `app/src/main/java/com/navink/ui/favourites/FavouritesScreen.kt` (replace stub)

- [ ] **Step 1: Create FavouritesRepository**

```kotlin
// app/src/main/java/com/navink/data/repository/FavouritesRepository.kt
package com.navink.data.repository

import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.remote.SubsonicService
import com.navink.data.remote.dto.AlbumDto
import com.navink.data.remote.dto.ArtistDto
import com.navink.data.remote.dto.SongDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavouritesRepository @Inject constructor(
    private val service: SubsonicService,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
) {
    fun starredArtists(): Flow<List<ArtistEntity>> = artistDao.starredArtists()
    fun starredAlbums(): Flow<List<AlbumEntity>> = albumDao.starredAlbums()
    fun starredSongs(): Flow<List<SongEntity>> = songDao.starredSongs()

    suspend fun syncStarred() {
        val response = service.getStarred2().response.starred2 ?: return
        response.artist.forEach { artistDao.setStarred(it.id, true) }
        response.album.forEach { albumDao.setStarred(it.id, true) }
        response.song.forEach { songDao.setStarred(it.id, true) }
    }

    suspend fun starSong(id: String) {
        service.star(songId = id)
        songDao.setStarred(id, true)
    }

    suspend fun unstarSong(id: String) {
        service.unstar(songId = id)
        songDao.setStarred(id, false)
    }

    suspend fun starAlbum(id: String) {
        service.star(albumId = id)
        albumDao.setStarred(id, true)
    }

    suspend fun unstarAlbum(id: String) {
        service.unstar(albumId = id)
        albumDao.setStarred(id, false)
    }

    suspend fun starArtist(id: String) {
        service.star(artistId = id)
        artistDao.setStarred(id, true)
    }

    suspend fun unstarArtist(id: String) {
        service.unstar(artistId = id)
        artistDao.setStarred(id, false)
    }
}
```

- [ ] **Step 2: Create FavouritesViewModel**

```kotlin
// app/src/main/java/com/navink/ui/favourites/FavouritesViewModel.kt
package com.navink.ui.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.local.entity.SongEntity
import com.navink.data.repository.FavouritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavouritesUiState(
    val songs: List<SongEntity> = emptyList(),
    val isSyncing: Boolean = false,
)

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FavouritesUiState())
    val state: StateFlow<FavouritesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            favouritesRepository.starredSongs().collect { list ->
                _state.value = _state.value.copy(songs = list)
            }
        }
        syncStarred()
    }

    private fun syncStarred() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true)
            try { favouritesRepository.syncStarred() } catch (_: Exception) {}
            _state.value = _state.value.copy(isSyncing = false)
        }
    }

    fun toggleStar(songId: String, isCurrentlyStarred: Boolean) {
        viewModelScope.launch {
            if (isCurrentlyStarred) favouritesRepository.unstarSong(songId)
            else favouritesRepository.starSong(songId)
        }
    }
}
```

- [ ] **Step 3: Replace FavouritesScreen stub**

```kotlin
// app/src/main/java/com/navink/ui/favourites/FavouritesScreen.kt
package com.navink.ui.favourites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.navink.data.local.entity.SongEntity

@Composable
fun FavouritesScreen(
    onSongClick: (songId: String, albumId: String) -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: FavouritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(bottomBar = miniPlayer) { padding ->
        if (state.songs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No starred songs yet")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.songs, key = { it.id }) { song ->
                    StarredSongRow(
                        song = song,
                        onTap = { onSongClick(song.id, song.albumId) },
                        onStarToggle = { viewModel.toggleStar(song.id, song.isStarred) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StarredSongRow(song: SongEntity, onTap: () -> Unit, onStarToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onTap)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = song.title, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onStarToggle) {
            Text(if (song.isStarred) "★" else "☆")
        }
    }
}
```

- [ ] **Step 4: Compile check**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -20
```

- [ ] **Step 5: Commit**
```sh
git add app/src/main/java/com/navink/data/repository/FavouritesRepository.kt \
        app/src/main/java/com/navink/ui/favourites/
git commit -m "feat: favourites (star/unstar, starred songs list)"
```

---

## Task 16: Offline Downloads

**Files:**
- Create: `app/src/main/java/com/navink/data/repository/DownloadRepository.kt`
- Create: `app/src/main/java/com/navink/download/DownloadWorker.kt`

- [ ] **Step 1: Create DownloadWorker**

```kotlin
// app/src/main/java/com/navink/download/DownloadWorker.kt
package com.navink.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.navink.data.local.dao.SongDao
import com.navink.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val songDao: SongDao,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString(KEY_SONG_ID) ?: return@withContext Result.failure()
        val creds = settingsRepository.getCredentials()

        val url = "${creds.serverUrl}/rest/download.view?id=$songId" +
            "&u=${creds.username}&p=${creds.password}&v=1.16.1&c=navink"

        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) return@withContext Result.retry()

            val dir = applicationContext.getExternalFilesDir("music")
                ?: return@withContext Result.failure()
            dir.mkdirs()
            val file = File(dir, "$songId.mp3")

            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }

            songDao.setDownloaded(songId, file.absolutePath)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_SONG_ID = "song_id"
    }
}
```

- [ ] **Step 2: Create DownloadRepository**

```kotlin
// app/src/main/java/com/navink/data/repository/DownloadRepository.kt
package com.navink.data.repository

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.SongEntity
import com.navink.download.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
) {
    fun downloadSong(songId: String) {
        val data = Data.Builder().putString(DownloadWorker.KEY_SONG_ID, songId).build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    suspend fun downloadAlbum(albumId: String) {
        val songs = songDao.songsForAlbumOnce(albumId)
        songs.filter { !it.isDownloaded }.forEach { downloadSong(it.id) }
    }
}
```

- [ ] **Step 3: Add download button to NowPlayingScreen**

In `NowPlayingScreen.kt`, add to the ViewModel and inject `DownloadRepository`. Add a download button below the controls:

In `PlayerViewModel.kt`, add:
```kotlin
// Add to PlayerViewModel constructor:
private val downloadRepository: DownloadRepository,

// Add method:
fun downloadCurrentSong() {
    val songId = state.value.currentSongId ?: return
    downloadRepository.downloadSong(songId)
}
```

In `NowPlayingScreen.kt`, add below the controls Row:
```kotlin
Spacer(Modifier.height(8.dp))
val currentSongId = state.currentSongId
if (currentSongId != null) {
    OutlinedButtonMMD(
        onClick = { viewModel.downloadCurrentSong() },
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text("Download", color = Color.White)
    }
}
```

- [ ] **Step 4: Compile check**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew compileDebugKotlin 2>&1 | tail -30
```

- [ ] **Step 5: Run all unit tests**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew testDebugUnitTest 2>&1 | tail -20
```
Expected: all green.

- [ ] **Step 6: Commit**
```sh
git add app/src/main/java/com/navink/download/ \
        app/src/main/java/com/navink/data/repository/DownloadRepository.kt \
        app/src/main/java/com/navink/ui/player/
git commit -m "feat: offline downloads (DownloadWorker + DownloadRepository)"
```

---

## Task 17: Final Build

- [ ] **Step 1: Full assembleDebug**
```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew assembleDebug 2>&1 | tail -40
```
Expected: `BUILD SUCCESSFUL` and APK at `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 2: If build fails, diagnose**

Common issues and fixes:
- `Kapt error` on Hilt: ensure all `@AndroidEntryPoint` classes have the annotation, all `@HiltViewModel` VMs use `@Inject constructor`
- `NavinkDatabase_Impl not found`: clean and rebuild: `./gradlew clean assembleDebug`
- `Unresolved reference: ThemeMMD`: confirm `../MMD` exists and `settings.gradle.kts` has the `includeBuild("../MMD")` composite build block
- `compileSdk 35 not found`: check `ANDROID_HOME` points to the correct SDK

- [ ] **Step 3: Verify APK exists**
```sh
ls -lh /var/home/shane/Documents/Projects/navink/app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 4: Final commit**
```sh
git add .
git commit -m "feat: complete Navink MVP — browse, search, play, favourites, offline"
```

---

## Notes for Agentic Execution

- Tasks 1–13 are strictly sequential (each builds on the previous).
- Tasks 14 (Search) and 15 (Favourites) can be executed in parallel after Task 13.
- Task 16 (Offline) depends on Tasks 11 and 13 (needs PlayerViewModel and DownloadWorker).
- Task 17 must be last.
- Always run `compileDebugKotlin` after each task before committing.
- The kapt warning `falling back to 1.9` is expected and harmless.
- Run `testDebugUnitTest` after Tasks 4, 7, 11 — these are the TDD tasks with real tests.
- If a screen's Composable signature changes, update NavGraph.kt accordingly.
