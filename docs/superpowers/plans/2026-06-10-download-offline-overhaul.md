# Download & Offline Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make downloads sequential, observable, and durable; make offline mode trustworthy and correctly ordered; consolidate download logic into `DownloadRepository`; move storage/server settings to one settings screen.

**Architecture:** A `DownloadQueueEntity` Room table is the download queue. A single `DownloadWorker` (unique work, foreground) drains it one track at a time, writing progress back to the row. Library sync switches to an insert-or-update that never touches `isDownloaded`/`localPath` (the root cause of vanishing downloads). Offline mode reads dedicated SQL queries (track-ordered) instead of in-memory filters. Spec: `docs/superpowers/specs/2026-06-10-download-offline-overhaul-design.md`.

**Tech Stack:** Kotlin 2.0.20, Room 2.6.1, WorkManager 2.9.0 (+ work-testing), OkHttp 4.12.0 (+ mockwebserver), Hilt 2.52, Media3 1.3.1, Compose + Mudita MMD, Robolectric 4.12.2.

**Build/test commands** (always prefix env vars; verify `../MMD` exists first):

```sh
JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew testDebugUnitTest          # unit tests

JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2" \
ANDROID_HOME="$HOME/Android/Sdk" \
./gradlew assembleDebug              # APK build
```

Run a single test class: `./gradlew testDebugUnitTest --tests "com.navink.data.local.dao.DownloadQueueDaoTest"` (same env prefix).

**Commit rule:** no Co-Authored-By trailers. One concern per commit.

---

## File Structure

Create:
- `app/src/main/java/com/navink/data/local/entity/DownloadQueueEntity.kt` — queue row
- `app/src/main/java/com/navink/data/local/dao/DownloadQueueDao.kt` — queue operations
- `app/src/test/java/com/navink/data/local/dao/DownloadQueueDaoTest.kt`
- `app/src/test/java/com/navink/data/repository/SyncRepositoryTest.kt`
- `app/src/test/java/com/navink/download/DownloadWorkerTest.kt`
- `app/src/test/java/com/navink/data/repository/DownloadRepositoryTest.kt`

Modify:
- `app/src/main/java/com/navink/data/local/dao/SongDao.kt` — offline queries, preserve-downloads upsert
- `app/src/main/java/com/navink/data/local/dao/AlbumDao.kt`, `ArtistDao.kt` — withDownloads queries
- `app/src/main/java/com/navink/data/local/NavinkDatabase.kt` — v2 + new entity
- `app/src/main/java/com/navink/di/DatabaseModule.kt` — migration + new DAO provider
- `app/src/main/java/com/navink/data/repository/SyncRepository.kt` — preserve download flags
- `app/src/main/java/com/navink/data/repository/MusicRepository.kt` — expose new queries
- `app/src/main/java/com/navink/data/repository/DownloadRepository.kt` — full consolidation
- `app/src/main/java/com/navink/download/DownloadWorker.kt` — queue drainer rework
- `app/src/main/java/com/navink/NavinkApp.kt` — queue recovery on start
- `app/src/main/java/com/navink/player/PlayerController.kt` — full-album queue
- `app/src/test/java/com/navink/player/PlayerControllerTest.kt` — update for new behaviour
- `app/src/main/java/com/navink/ui/player/PlayerViewModel.kt` — offline-aware play, delete download
- `app/src/main/java/com/navink/ui/player/NowPlayingScreen.kt` — remove-download button
- `app/src/main/java/com/navink/ui/browse/BrowseViewModel.kt` — drop WorkManager, use repository
- `app/src/main/java/com/navink/ui/browse/ArtistsScreen.kt` — gear button, offline via VM
- `app/src/main/java/com/navink/ui/browse/AlbumsScreen.kt` — downloaded counts, offline via VM
- `app/src/main/java/com/navink/ui/browse/SongsScreen.kt` — track order offline, download/remove actions
- `app/src/main/java/com/navink/ui/downloads/DownloadsScreen.kt` — queue with progress, no storage toggle
- `app/src/main/java/com/navink/ui/settings/SettingsViewModel.kt`, `SettingsScreen.kt` — storage/offline/usage
- `app/src/main/java/com/navink/NavGraph.kt` — settings wiring
- `app/src/main/AndroidManifest.xml` — dataSync foreground service
- `app/build.gradle.kts` — test deps

---

### Task 0: Commit current WIP and add test deps

The working tree has uncommitted changes from the previous session (download tags, offline album counts, search button tweaks). Commit them as the baseline before refactoring.

**Files:**
- Modify: `app/build.gradle.kts:133` (after `testImplementation(kotlin("test"))`)

- [ ] **Step 1: Commit existing WIP**

```sh
git add app/src/main
git commit -m "wip: download queue tags and offline UI tweaks"
```

(Do not add `app/build/` or `.claude/` — they are build output and local config.)

- [ ] **Step 2: Add test dependencies**

In `app/build.gradle.kts`, after the line `testImplementation(kotlin("test"))` add:

```kotlin
    testImplementation("androidx.work:work-testing:2.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

- [ ] **Step 3: Verify the build still configures**

Run: `JAVA_HOME=... ANDROID_HOME=... ./gradlew :app:compileDebugUnitTestKotlin` (env prefix as above)
Expected: BUILD SUCCESSFUL (kapt 1.9 fallback warning is normal).

- [ ] **Step 4: Commit**

```sh
git add app/build.gradle.kts
git commit -m "build: add work-testing and mockwebserver test deps"
```

---

### Task 1: DownloadQueueEntity + DownloadQueueDao + DB v2

**Files:**
- Create: `app/src/main/java/com/navink/data/local/entity/DownloadQueueEntity.kt`
- Create: `app/src/main/java/com/navink/data/local/dao/DownloadQueueDao.kt`
- Modify: `app/src/main/java/com/navink/data/local/NavinkDatabase.kt`
- Modify: `app/src/main/java/com/navink/di/DatabaseModule.kt`
- Test: `app/src/test/java/com/navink/data/local/dao/DownloadQueueDaoTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/navink/data/local/dao/DownloadQueueDaoTest.kt`:

```kotlin
package com.navink.data.local.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.entity.DownloadQueueEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadQueueDaoTest {
    private lateinit var db: NavinkDatabase
    private lateinit var dao: DownloadQueueDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NavinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.downloadQueueDao()
    }

    @After
    fun tearDown() { db.close() }

    private fun item(id: String, at: Long, status: String = DownloadQueueEntity.STATUS_QUEUED) =
        DownloadQueueEntity(songId = id, title = "T$id", status = status, enqueuedAt = at)

    @Test
    fun `nextQueued returns oldest queued item`() = runTest {
        dao.insertAll(listOf(item("s2", at = 2), item("s1", at = 1)))
        assertEquals("s1", dao.nextQueued()!!.songId)
    }

    @Test
    fun `insert ignores duplicate songId`() = runTest {
        dao.insertAll(listOf(item("s1", at = 1)))
        dao.markRunning("s1")
        dao.insertAll(listOf(item("s1", at = 99)))
        val rows = dao.queue().first()
        assertEquals(1, rows.size)
        assertEquals(DownloadQueueEntity.STATUS_RUNNING, rows[0].status)
    }

    @Test
    fun `markFailed stores error and is skipped by nextQueued`() = runTest {
        dao.insertAll(listOf(item("s1", at = 1)))
        dao.markFailed("s1", "HTTP 500")
        assertNull(dao.nextQueued())
        assertEquals("HTTP 500", dao.queue().first()[0].errorMessage)
    }

    @Test
    fun `requeueFailed resets failed rows to queued`() = runTest {
        dao.insertAll(listOf(item("s1", at = 1)))
        dao.markFailed("s1", "boom")
        dao.requeueFailed()
        assertEquals("s1", dao.nextQueued()!!.songId)
        assertNull(dao.queue().first()[0].errorMessage)
    }

    @Test
    fun `resetRunning returns orphaned running rows to queued`() = runTest {
        dao.insertAll(listOf(item("s1", at = 1)))
        dao.markRunning("s1")
        dao.resetRunning()
        assertEquals("s1", dao.nextQueued()!!.songId)
    }

    @Test
    fun `clearFailed removes only failed rows`() = runTest {
        dao.insertAll(listOf(item("s1", at = 1), item("s2", at = 2)))
        dao.markFailed("s1", "boom")
        dao.clearFailed()
        val rows = dao.queue().first()
        assertEquals(1, rows.size)
        assertEquals("s2", rows[0].songId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.navink.data.local.dao.DownloadQueueDaoTest"` (env prefix)
Expected: COMPILATION FAILURE — `DownloadQueueEntity`/`downloadQueueDao` unresolved.

- [ ] **Step 3: Create the entity**

Create `app/src/main/java/com/navink/data/local/entity/DownloadQueueEntity.kt`:

```kotlin
package com.navink.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class DownloadQueueEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val status: String,
    val progressPercent: Int = 0,
    val errorMessage: String? = null,
    val enqueuedAt: Long,
) {
    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_FAILED = "FAILED"
    }
}
```

- [ ] **Step 4: Create the DAO**

Create `app/src/main/java/com/navink/data/local/dao/DownloadQueueDao.kt`:

```kotlin
package com.navink.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.navink.data.local.entity.DownloadQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<DownloadQueueEntity>)

    @Query("SELECT * FROM DownloadQueueEntity ORDER BY enqueuedAt ASC")
    fun queue(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM DownloadQueueEntity WHERE status = 'QUEUED' ORDER BY enqueuedAt ASC LIMIT 1")
    suspend fun nextQueued(): DownloadQueueEntity?

    @Query("UPDATE DownloadQueueEntity SET status = 'RUNNING', progressPercent = 0 WHERE songId = :songId")
    suspend fun markRunning(songId: String)

    @Query("UPDATE DownloadQueueEntity SET progressPercent = :pct WHERE songId = :songId")
    suspend fun updateProgress(songId: String, pct: Int)

    @Query("UPDATE DownloadQueueEntity SET status = 'FAILED', errorMessage = :error WHERE songId = :songId")
    suspend fun markFailed(songId: String, error: String)

    @Query("DELETE FROM DownloadQueueEntity WHERE songId = :songId")
    suspend fun delete(songId: String)

    @Query("DELETE FROM DownloadQueueEntity WHERE status = 'FAILED'")
    suspend fun clearFailed()

    @Query("UPDATE DownloadQueueEntity SET status = 'QUEUED', errorMessage = NULL, progressPercent = 0 WHERE status = 'FAILED'")
    suspend fun requeueFailed()

    @Query("UPDATE DownloadQueueEntity SET status = 'QUEUED', progressPercent = 0 WHERE status = 'RUNNING'")
    suspend fun resetRunning()
}
```

- [ ] **Step 5: Bump the database**

Replace `app/src/main/java/com/navink/data/local/NavinkDatabase.kt` with:

```kotlin
package com.navink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.DownloadQueueDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.DownloadQueueEntity
import com.navink.data.local.entity.SongEntity

@Database(
    entities = [ArtistEntity::class, AlbumEntity::class, SongEntity::class, DownloadQueueEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class NavinkDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun songDao(): SongDao
    abstract fun downloadQueueDao(): DownloadQueueDao
}
```

- [ ] **Step 6: Register migration + DAO provider**

Replace `app/src/main/java/com/navink/di/DatabaseModule.kt` with:

```kotlin
package com.navink.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.DownloadQueueDao
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
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `DownloadQueueEntity` (
                    `songId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `progressPercent` INTEGER NOT NULL DEFAULT 0,
                    `errorMessage` TEXT,
                    `enqueuedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`songId`)
                )
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NavinkDatabase =
        Room.databaseBuilder(context, NavinkDatabase::class.java, "navink.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun provideArtistDao(db: NavinkDatabase): ArtistDao = db.artistDao()
    @Provides fun provideAlbumDao(db: NavinkDatabase): AlbumDao = db.albumDao()
    @Provides fun provideSongDao(db: NavinkDatabase): SongDao = db.songDao()
    @Provides fun provideDownloadQueueDao(db: NavinkDatabase): DownloadQueueDao = db.downloadQueueDao()
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.navink.data.local.dao.DownloadQueueDaoTest"`
Expected: 6 tests PASS.

- [ ] **Step 8: Commit**

```sh
git add app/src/main/java/com/navink/data/local app/src/main/java/com/navink/di/DatabaseModule.kt app/src/test/java/com/navink/data/local/dao/DownloadQueueDaoTest.kt
git commit -m "feat: download queue table with sequential-drain DAO"
```

---

### Task 2: SongDao offline queries + download-preserving upsert

**Files:**
- Modify: `app/src/main/java/com/navink/data/local/dao/SongDao.kt`
- Test: `app/src/test/java/com/navink/data/local/dao/SongDaoTest.kt` (append tests)

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/java/com/navink/data/local/dao/SongDaoTest.kt` (inside the class):

```kotlin
    @Test
    fun `upsertPreservingDownloads keeps download flags but updates metadata`() = runTest {
        dao.upsertAll(listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "Old", duration = 100)
        ))
        dao.setDownloaded("s1", "/m/s1.mp3")
        dao.upsertPreservingDownloads(listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "New", trackNumber = 3, duration = 120, isStarred = true),
            SongEntity(id = "s2", albumId = "a1", artistId = "ar1", title = "Fresh", duration = 90),
        ))
        val s1 = dao.songById("s1")!!
        assertEquals("New", s1.title)
        assertEquals(3, s1.trackNumber)
        assertTrue(s1.isStarred)
        assertTrue(s1.isDownloaded)
        assertEquals("/m/s1.mp3", s1.localPath)
        val s2 = dao.songById("s2")!!
        assertEquals("Fresh", s2.title)
        assertTrue(!s2.isDownloaded)
    }

    @Test
    fun `downloadedSongsForAlbum is ordered by track number`() = runTest {
        dao.upsertAll(listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "Zeta", trackNumber = 1, duration = 1),
            SongEntity(id = "s2", albumId = "a1", artistId = "ar1", title = "Alpha", trackNumber = 2, duration = 1),
            SongEntity(id = "s3", albumId = "a1", artistId = "ar1", title = "Beta", trackNumber = 3, duration = 1),
        ))
        dao.setDownloaded("s1", "/m/s1.mp3")
        dao.setDownloaded("s3", "/m/s3.mp3")
        val result = dao.downloadedSongsForAlbum("a1").first()
        assertEquals(listOf("s1", "s3"), result.map { it.id })
    }

    @Test
    fun `clearDownloaded resets flag and path`() = runTest {
        dao.upsertAll(listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "T", duration = 1)
        ))
        dao.setDownloaded("s1", "/m/s1.mp3")
        dao.clearDownloaded("s1")
        val s = dao.songById("s1")!!
        assertTrue(!s.isDownloaded)
        assertEquals(null, s.localPath)
    }

    @Test
    fun `downloadedCountByAlbum groups counts`() = runTest {
        dao.upsertAll(listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "1", duration = 1),
            SongEntity(id = "s2", albumId = "a1", artistId = "ar1", title = "2", duration = 1),
            SongEntity(id = "s3", albumId = "a2", artistId = "ar1", title = "3", duration = 1),
        ))
        dao.setDownloaded("s1", "/m/s1.mp3")
        dao.setDownloaded("s2", "/m/s2.mp3")
        dao.setDownloaded("s3", "/m/s3.mp3")
        val counts = dao.downloadedCountByAlbum().first().associate { it.albumId to it.cnt }
        assertEquals(2, counts["a1"])
        assertEquals(1, counts["a2"])
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.navink.data.local.dao.SongDaoTest"`
Expected: COMPILATION FAILURE — new DAO methods unresolved.

- [ ] **Step 3: Implement SongDao additions**

Replace `app/src/main/java/com/navink/data/local/dao/SongDao.kt` with:

```kotlin
package com.navink.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.navink.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

data class AlbumDownloadCount(val albumId: String, val cnt: Int)

data class ArtistAlbumDownloadCount(val artistId: String, val cnt: Int)

@Dao
interface SongDao {
    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId ORDER BY trackNumber ASC, title ASC")
    fun songsForAlbum(albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM SongEntity WHERE id = :id")
    suspend fun songById(id: String): SongEntity?

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(songs: List<SongEntity>): List<Long>

    @Query(
        """UPDATE SongEntity SET albumId = :albumId, artistId = :artistId, title = :title,
           trackNumber = :trackNumber, duration = :duration, coverArtId = :coverArtId,
           isStarred = :isStarred WHERE id = :id"""
    )
    suspend fun updateMetadata(
        id: String,
        albumId: String,
        artistId: String,
        title: String,
        trackNumber: Int?,
        duration: Int,
        coverArtId: String?,
        isStarred: Boolean,
    )

    /** Insert new songs, update metadata of existing ones; never touches isDownloaded/localPath. */
    @Transaction
    suspend fun upsertPreservingDownloads(songs: List<SongEntity>) {
        val inserted = insertIgnore(songs)
        songs.forEachIndexed { i, s ->
            if (inserted[i] == -1L) {
                updateMetadata(s.id, s.albumId, s.artistId, s.title, s.trackNumber, s.duration, s.coverArtId, s.isStarred)
            }
        }
    }

    @Query("UPDATE SongEntity SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("UPDATE SongEntity SET isDownloaded = 1, localPath = :path WHERE id = :id")
    suspend fun setDownloaded(id: String, path: String)

    @Query("UPDATE SongEntity SET isDownloaded = 0, localPath = NULL WHERE id = :id")
    suspend fun clearDownloaded(id: String)

    @Query("SELECT * FROM SongEntity WHERE isStarred = 1 ORDER BY title ASC")
    fun starredSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId ORDER BY trackNumber ASC, title ASC")
    suspend fun songsForAlbumOnce(albumId: String): List<SongEntity>

    @Query("SELECT * FROM SongEntity WHERE isDownloaded = 1 ORDER BY title ASC")
    fun downloadedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM SongEntity WHERE isDownloaded = 1")
    suspend fun downloadedSongsOnce(): List<SongEntity>

    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId AND isDownloaded = 1 ORDER BY trackNumber ASC, title ASC")
    fun downloadedSongsForAlbum(albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId AND isDownloaded = 1 ORDER BY trackNumber ASC, title ASC")
    suspend fun downloadedSongsForAlbumOnce(albumId: String): List<SongEntity>

    @Query("SELECT albumId, COUNT(*) AS cnt FROM SongEntity WHERE isDownloaded = 1 GROUP BY albumId")
    fun downloadedCountByAlbum(): Flow<List<AlbumDownloadCount>>

    @Query("SELECT artistId, COUNT(DISTINCT albumId) AS cnt FROM SongEntity WHERE isDownloaded = 1 GROUP BY artistId")
    fun downloadedAlbumCountByArtist(): Flow<List<ArtistAlbumDownloadCount>>
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.navink.data.local.dao.SongDaoTest"`
Expected: all PASS (3 old + 4 new).

- [ ] **Step 5: Commit**

```sh
git add app/src/main/java/com/navink/data/local/dao/SongDao.kt app/src/test/java/com/navink/data/local/dao/SongDaoTest.kt
git commit -m "feat: track-ordered offline queries and download-preserving upsert"
```

---

### Task 3: AlbumDao/ArtistDao withDownloads queries

**Files:**
- Modify: `app/src/main/java/com/navink/data/local/dao/AlbumDao.kt`
- Modify: `app/src/main/java/com/navink/data/local/dao/ArtistDao.kt`
- Test: `app/src/test/java/com/navink/data/local/dao/SongDaoTest.kt` (append — same DB fixture has all DAOs)

- [ ] **Step 1: Write the failing test**

Append to `SongDaoTest.kt` class body:

```kotlin
    @Test
    fun `albumsWithDownloadsForArtist and artistsWithDownloads return only downloaded`() = runTest {
        db.artistDao().upsertAll(listOf(
            com.navink.data.local.entity.ArtistEntity(id = "ar1", name = "A"),
            com.navink.data.local.entity.ArtistEntity(id = "ar2", name = "B"),
        ))
        db.albumDao().upsertAll(listOf(
            com.navink.data.local.entity.AlbumEntity(id = "a1", artistId = "ar1", name = "Down"),
            com.navink.data.local.entity.AlbumEntity(id = "a2", artistId = "ar1", name = "NotDown"),
        ))
        dao.upsertAll(listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "T", duration = 1),
            SongEntity(id = "s2", albumId = "a2", artistId = "ar1", title = "U", duration = 1),
        ))
        dao.setDownloaded("s1", "/m/s1.mp3")
        val albums = db.albumDao().albumsWithDownloadsForArtist("ar1").first()
        assertEquals(listOf("a1"), albums.map { it.id })
        val artists = db.artistDao().artistsWithDownloads().first()
        assertEquals(listOf("ar1"), artists.map { it.id })
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.navink.data.local.dao.SongDaoTest"`
Expected: COMPILATION FAILURE — `albumsWithDownloadsForArtist`/`artistsWithDownloads` unresolved.

- [ ] **Step 3: Add the queries**

In `AlbumDao.kt`, add inside the interface:

```kotlin
    @Query(
        """SELECT * FROM AlbumEntity WHERE artistId = :artistId
           AND id IN (SELECT DISTINCT albumId FROM SongEntity WHERE isDownloaded = 1)
           ORDER BY year ASC, name ASC"""
    )
    fun albumsWithDownloadsForArtist(artistId: String): Flow<List<AlbumEntity>>
```

In `ArtistDao.kt`, add inside the interface:

```kotlin
    @Query(
        """SELECT * FROM ArtistEntity
           WHERE id IN (SELECT DISTINCT artistId FROM SongEntity WHERE isDownloaded = 1)
           ORDER BY name ASC"""
    )
    fun artistsWithDownloads(): Flow<List<ArtistEntity>>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.navink.data.local.dao.SongDaoTest"`
Expected: all PASS.

- [ ] **Step 5: Commit**

```sh
git add app/src/main/java/com/navink/data/local/dao app/src/test/java/com/navink/data/local/dao/SongDaoTest.kt
git commit -m "feat: offline album/artist queries backed by download flags"
```

---

### Task 4: SyncRepository preserves download state

**Files:**
- Modify: `app/src/main/java/com/navink/data/repository/SyncRepository.kt:26,36`
- Test: Create `app/src/test/java/com/navink/data/repository/SyncRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/navink/data/repository/SyncRepositoryTest.kt`:

```kotlin
package com.navink.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.remote.SubsonicService
import com.navink.data.remote.dto.AlbumDetailDto
import com.navink.data.remote.dto.SongDto
import com.navink.data.remote.dto.SubsonicResponse
import com.navink.data.remote.dto.SubsonicResponseBody
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SyncRepositoryTest {
    private lateinit var db: NavinkDatabase
    private lateinit var repo: SyncRepository
    private val service: SubsonicService = mockk()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NavinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = SyncRepository(service, db.artistDao(), db.albumDao(), db.songDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `syncAlbumSongs updates metadata but preserves download flags`() = runTest {
        db.albumDao().upsertAll(listOf(AlbumEntity(id = "a1", artistId = "ar1", name = "Album")))
        db.songDao().upsertAll(listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "Old Title", duration = 100)
        ))
        db.songDao().setDownloaded("s1", "/m/s1.mp3")

        coEvery { service.getAlbum("a1") } returns SubsonicResponse(
            SubsonicResponseBody(
                status = "ok",
                version = "1.16.1",
                album = AlbumDetailDto(
                    id = "a1",
                    song = listOf(SongDto(id = "s1", title = "New Title", track = 1, duration = 120)),
                ),
            )
        )

        repo.syncAlbumSongs("a1")

        val s1 = db.songDao().songById("s1")!!
        assertEquals("New Title", s1.title)
        assertTrue(s1.isDownloaded)
        assertEquals("/m/s1.mp3", s1.localPath)
    }
}
```

(Mocking `SubsonicService` is mocking the HTTP boundary — allowed.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.navink.data.repository.SyncRepositoryTest"`
Expected: FAIL — `s1.isDownloaded` is false (the `@Upsert` wiped it). This proves the bug.

- [ ] **Step 3: Fix SyncRepository**

In `SyncRepository.kt`, replace both song upsert calls:

Line 26 (`syncAlbumSongs`):
```kotlin
        songDao.upsertPreservingDownloads(albumDetail.song.map {
            it.toEntity(albumId = albumId, artistId = album.artistId)
        })
```

Line 36 (`syncArtist`):
```kotlin
            songDao.upsertPreservingDownloads(albumDetail.song.map { it.toEntity(albumId = albumDto.id, artistId = artistId) })
```

(Album/artist upserts stay as-is — those entities carry no download state.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.navink.data.repository.SyncRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```sh
git add app/src/main/java/com/navink/data/repository/SyncRepository.kt app/src/test/java/com/navink/data/repository/SyncRepositoryTest.kt
git commit -m "fix: library sync no longer wipes download flags"
```

---

### Task 5: Rework DownloadWorker as queue drainer

**Files:**
- Modify: `app/src/main/java/com/navink/download/DownloadWorker.kt` (full replacement)
- Test: Create `app/src/test/java/com/navink/download/DownloadWorkerTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/navink/download/DownloadWorkerTest.kt`:

```kotlin
package com.navink.download

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.entity.DownloadQueueEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadWorkerTest {
    private lateinit var db: NavinkDatabase
    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private val settingsRepository: SettingsRepository = mockk()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NavinkDatabase::class.java)
            .allowMainThreadQueries().build()
        server = MockWebServer()
        server.start()
        coEvery { settingsRepository.getCredentials() } returns
            SettingsRepository.Credentials(server.url("/").toString().trimEnd('/'), "u", "p")
        coEvery { settingsRepository.getStorageLocation() } returns "internal"
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
        File(context.filesDir, "music").deleteRecursively()
    }

    private fun buildWorker(): DownloadWorker =
        TestListenableWorkerBuilder<DownloadWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = DownloadWorker(
                    appContext, workerParameters, settingsRepository,
                    db.songDao(), db.downloadQueueDao(), OkHttpClient(),
                )
            })
            .build() as DownloadWorker

    private suspend fun seedSong(id: String) {
        db.songDao().upsertAll(listOf(
            SongEntity(id = id, albumId = "a1", artistId = "ar1", title = "T$id", duration = 1)
        ))
        db.downloadQueueDao().insertAll(listOf(
            DownloadQueueEntity(songId = id, title = "T$id", status = DownloadQueueEntity.STATUS_QUEUED, enqueuedAt = System.currentTimeMillis())
        ))
    }

    @Test
    fun `drains queue sequentially and marks songs downloaded`() = runTest {
        seedSong("s1"); seedSong("s2")
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody("AUDIO1"))
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody("AUDIO2"))

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val s1 = db.songDao().songById("s1")!!
        assertTrue(s1.isDownloaded)
        assertEquals("AUDIO1", File(s1.localPath!!).readText())
        assertTrue(db.songDao().songById("s2")!!.isDownloaded)
        assertTrue(db.downloadQueueDao().queue().first().isEmpty())
    }

    @Test
    fun `json error body marks item failed without writing audio`() = runTest {
        seedSong("s1")
        repeat(DownloadWorker.MAX_ATTEMPTS) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "application/json")
                    .setBody("""{"subsonic-response":{"status":"failed"}}""")
            )
        }

        buildWorker().doWork()

        val rows = db.downloadQueueDao().queue().first()
        assertEquals(DownloadQueueEntity.STATUS_FAILED, rows[0].status)
        assertTrue(!db.songDao().songById("s1")!!.isDownloaded)
        assertTrue(!File(context.filesDir, "music/s1.mp3").exists())
    }

    @Test
    fun `failed item does not block later items`() = runTest {
        seedSong("s1"); seedSong("s2")
        repeat(DownloadWorker.MAX_ATTEMPTS) { server.enqueue(MockResponse().setResponseCode(500)) }
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody("AUDIO2"))

        buildWorker().doWork()

        val rows = db.downloadQueueDao().queue().first()
        assertEquals(listOf("s1"), rows.map { it.songId })
        assertEquals(DownloadQueueEntity.STATUS_FAILED, rows[0].status)
        assertTrue(db.songDao().songById("s2")!!.isDownloaded)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.navink.download.DownloadWorkerTest"`
Expected: COMPILATION FAILURE — worker constructor has no `queueDao` param, `MAX_ATTEMPTS` unresolved.

- [ ] **Step 3: Replace DownloadWorker**

Replace `app/src/main/java/com/navink/download/DownloadWorker.kt` with:

```kotlin
package com.navink.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.navink.data.local.dao.DownloadQueueDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.DownloadQueueEntity
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
    private val queueDao: DownloadQueueDao,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Foreground keeps the drain alive past JobScheduler's ~10 min execution cap
        try { setForeground(foregroundInfo("Downloading…", 0)) } catch (_: Exception) {}
        while (true) {
            val item = queueDao.nextQueued() ?: break
            queueDao.markRunning(item.songId)
            var error: String? = null
            for (attempt in 1..MAX_ATTEMPTS) {
                error = try {
                    downloadOne(item)
                } catch (e: Exception) {
                    e.message ?: e.javaClass.simpleName
                }
                if (error == null) break
            }
            if (error == null) queueDao.delete(item.songId) else queueDao.markFailed(item.songId, error)
        }
        Result.success()
    }

    /** Returns null on success, or an error message. */
    private suspend fun downloadOne(item: DownloadQueueEntity): String? {
        val creds = settingsRepository.getCredentials()
        // Auth params added by SubsonicAuthInterceptor — don't duplicate them here
        val url = "${creds.serverUrl}/rest/download.view?id=${item.songId}"
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return "HTTP ${response.code}"
            val contentType = response.header("Content-Type").orEmpty()
            // Subsonic reports errors as HTTP 200 with a JSON/XML body
            if ("json" in contentType || "xml" in contentType) return "Server returned an error"
            val body = response.body ?: return "Empty response"
            val dir = resolveStorageDir() ?: return "Storage unavailable"
            dir.mkdirs()
            val part = File(dir, "${item.songId}.mp3.part")
            val total = body.contentLength()
            var lastPct = 0
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        copied += n
                        if (total > 0) {
                            val pct = (copied * 100 / total).toInt()
                            if (pct >= lastPct + 5) {
                                lastPct = pct
                                queueDao.updateProgress(item.songId, pct)
                                notifyProgress(item.title, pct)
                            }
                        }
                    }
                }
            }
            val final = File(dir, "${item.songId}.mp3")
            if (final.exists()) final.delete()
            if (!part.renameTo(final)) return "Could not finalise file"
            songDao.setDownloaded(item.songId, final.absolutePath)
            return null
        }
    }

    private suspend fun resolveStorageDir(): File? {
        val location = settingsRepository.getStorageLocation()
        return if (location == "internal") {
            applicationContext.filesDir.resolve("music")
        } else {
            // "external": use last external dir — on devices with SD card this is the card
            val dirs = applicationContext.getExternalFilesDirs("music")
            dirs.filterNotNull().lastOrNull() ?: applicationContext.getExternalFilesDir("music")
        }
    }

    private fun foregroundInfo(text: String, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Navink downloads")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyProgress(title: String, pct: Int) {
        try { setForegroundAsync(foregroundInfo("$title — $pct%", pct)) } catch (_: Exception) {}
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 2001
        const val WORK_NAME = "navink-download-drain"
    }
}
```

Note: `KEY_SONG_ID`, `KEY_SONG_TITLE`, and `TAG` constants are deleted — Task 6/10 removes their last usages (`DownloadRepository.downloadSong`, `BrowseViewModel`, `DownloadsScreen`). The project will not compile between Steps 3 and Task 6's repository replacement, so do Task 6 Step 3 (repository) immediately after this step if compilation is needed; the test run in Step 4 only needs these test classes, which compile against the whole module — therefore **apply Task 6 Step 3's `DownloadRepository` replacement together with this step**, then continue. (Task 6 still owns its own tests/commit.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.navink.download.DownloadWorkerTest"`
Expected: 3 tests PASS. (If `BrowseViewModel`/`DownloadsScreen` fail compilation due to removed constants, finish Task 6 Step 3 and Task 10/12 main-source replacements first — order of commits below still applies.)

- [ ] **Step 5: Commit** (only once the module compiles — see note above; if needed, fold this commit into Task 6's)

```sh
git add app/src/main/java/com/navink/download/DownloadWorker.kt app/src/test/java/com/navink/download/DownloadWorkerTest.kt
git commit -m "feat: sequential queue-drain download worker with progress and retry cap"
```

---

### Task 6: Consolidated DownloadRepository

**Files:**
- Modify: `app/src/main/java/com/navink/data/repository/DownloadRepository.kt` (full replacement)
- Test: Create `app/src/test/java/com/navink/data/repository/DownloadRepositoryTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/navink/data/repository/DownloadRepositoryTest.kt`:

```kotlin
package com.navink.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadRepositoryTest {
    private lateinit var db: NavinkDatabase
    private lateinit var repo: DownloadRepository
    private lateinit var context: Context
    private lateinit var musicDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context, Configuration.Builder().build()
        )
        db = Room.inMemoryDatabaseBuilder(context, NavinkDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DownloadRepository(context, db.songDao(), db.albumDao(), db.downloadQueueDao())
        musicDir = File(context.filesDir, "music").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        musicDir.deleteRecursively()
    }

    private suspend fun seed(id: String, downloaded: Boolean = false): File {
        val f = File(musicDir, "$id.mp3")
        if (downloaded) f.writeText("AUDIO-$id")
        db.songDao().upsertAll(listOf(
            SongEntity(
                id = id, albumId = "a1", artistId = "ar1", title = "T$id", duration = 1,
                isDownloaded = downloaded, localPath = if (downloaded) f.absolutePath else null,
            )
        ))
        return f
    }

    @Test
    fun `enqueueAlbum queues only non-downloaded songs`() = runTest {
        db.albumDao().upsertAll(listOf(AlbumEntity(id = "a1", artistId = "ar1", name = "A")))
        seed("s1", downloaded = true)
        seed("s2")
        val queued = repo.enqueueAlbum("a1")
        assertEquals(1, queued)
        assertEquals(listOf("s2"), db.downloadQueueDao().queue().first().map { it.songId })
    }

    @Test
    fun `verifyDownloads clears flags for missing files`() = runTest {
        seed("s1", downloaded = true)
        val gone = seed("s2", downloaded = true)
        gone.delete()
        val repaired = repo.verifyDownloads()
        assertEquals(1, repaired)
        assertTrue(db.songDao().songById("s1")!!.isDownloaded)
        assertTrue(!db.songDao().songById("s2")!!.isDownloaded)
    }

    @Test
    fun `deleteAlbumDownloads removes files and clears flags`() = runTest {
        val f1 = seed("s1", downloaded = true)
        seed("s2")
        repo.deleteAlbumDownloads("a1")
        assertTrue(!f1.exists())
        assertTrue(!db.songDao().songById("s1")!!.isDownloaded)
    }

    @Test
    fun `storageUsedBytes sums file sizes`() = runTest {
        seed("s1", downloaded = true)  // 8 bytes: "AUDIO-s1"
        seed("s2", downloaded = true)
        assertEquals(16, repo.storageUsedBytes())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.navink.data.repository.DownloadRepositoryTest"`
Expected: COMPILATION FAILURE — constructor/methods don't exist yet.

- [ ] **Step 3: Replace DownloadRepository**

Replace `app/src/main/java/com/navink/data/repository/DownloadRepository.kt` with:

```kotlin
package com.navink.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.DownloadQueueDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.DownloadQueueEntity
import com.navink.data.local.entity.SongEntity
import com.navink.download.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val queueDao: DownloadQueueDao,
) {
    fun queue(): Flow<List<DownloadQueueEntity>> = queueDao.queue()

    suspend fun enqueueSong(songId: String): Int {
        val song = songDao.songById(songId) ?: return 0
        return enqueueSongs(listOf(song))
    }

    suspend fun enqueueAlbum(albumId: String): Int =
        enqueueSongs(songDao.songsForAlbumOnce(albumId))

    suspend fun enqueueArtist(artistId: String): Int {
        val albums = albumDao.albumsForArtistOnce(artistId)
        return albums.sumOf { enqueueAlbum(it.id) }
    }

    private suspend fun enqueueSongs(songs: List<SongEntity>): Int {
        val pending = songs.filter { !it.isDownloaded }
        if (pending.isEmpty()) return 0
        val now = System.currentTimeMillis()
        queueDao.insertAll(pending.mapIndexed { i, s ->
            DownloadQueueEntity(
                songId = s.id,
                title = s.title,
                status = DownloadQueueEntity.STATUS_QUEUED,
                enqueuedAt = now + i,
            )
        })
        kickWorker()
        return pending.size
    }

    suspend fun deleteSongDownload(songId: String) {
        val song = songDao.songById(songId) ?: return
        song.localPath?.let { File(it).delete() }
        songDao.clearDownloaded(songId)
    }

    suspend fun deleteAlbumDownloads(albumId: String) {
        songDao.downloadedSongsForAlbumOnce(albumId).forEach { deleteSongDownload(it.id) }
    }

    /** Clears download flags whose files are missing. Returns number repaired. */
    suspend fun verifyDownloads(): Int {
        var repaired = 0
        songDao.downloadedSongsOnce().forEach { s ->
            if (s.localPath == null || !File(s.localPath).exists()) {
                songDao.clearDownloaded(s.id)
                repaired++
            }
        }
        return repaired
    }

    suspend fun storageUsedBytes(): Long =
        songDao.downloadedSongsOnce().sumOf { s -> s.localPath?.let { File(it).length() } ?: 0L }

    suspend fun clearFailed() = queueDao.clearFailed()

    suspend fun retryFailed() {
        queueDao.requeueFailed()
        kickWorker()
    }

    /** Called on app start: requeue items orphaned in RUNNING by a process kill, resume draining. */
    suspend fun recoverQueue() {
        queueDao.resetRunning()
        if (queueDao.nextQueued() != null) kickWorker()
    }

    private fun kickWorker() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            DownloadWorker.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build(),
        )
    }
}
```

(`APPEND_OR_REPLACE`: if a drain is running, the new request runs after it — closing the race where a row is inserted just as the worker exits. The extra drain pass exits immediately when the queue is empty.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.navink.data.repository.DownloadRepositoryTest"`
Expected: 4 tests PASS. (Module-wide compile still broken in `PlayerViewModel`/`BrowseViewModel`/`DownloadsScreen` until Tasks 9/10/12 — run with `--tests` filters only; full compile is restored by Task 10/12.)

- [ ] **Step 5: Commit** (with Task 5's worker if deferred)

```sh
git add app/src/main/java/com/navink/data/repository/DownloadRepository.kt app/src/test/java/com/navink/data/repository/DownloadRepositoryTest.kt
git commit -m "feat: consolidate download enqueue/delete/verify into DownloadRepository"
```

> **Sequencing note for Tasks 5–12:** the worker/repository API change breaks `PlayerViewModel`, `BrowseViewModel`, and `DownloadsScreen` compilation until their tasks land. If the executor prefers always-green builds, apply Tasks 5, 6, 9, 10, 11, 12 code changes in one working session but keep the commits separated as written (`git add` the listed files per commit).

---

### Task 7: Queue recovery on app start

**Files:**
- Modify: `app/src/main/java/com/navink/NavinkApp.kt`

- [ ] **Step 1: Replace NavinkApp**

```kotlin
package com.navink

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.navink.data.repository.DownloadRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NavinkApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var downloadRepository: DownloadRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { downloadRepository.recoverQueue() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

- [ ] **Step 2: Commit**

```sh
git add app/src/main/java/com/navink/NavinkApp.kt
git commit -m "feat: resume pending downloads on app start"
```

---

### Task 8: PlayerController full-album queue

**Files:**
- Modify: `app/src/main/java/com/navink/player/PlayerController.kt`
- Test: `app/src/test/java/com/navink/player/PlayerControllerTest.kt` (replace tests)

- [ ] **Step 1: Replace the test file**

Replace `app/src/test/java/com/navink/player/PlayerControllerTest.kt` with:

```kotlin
package com.navink.player

import com.navink.data.local.entity.SongEntity
import org.junit.Test
import kotlin.test.assertEquals

class PlayerControllerTest {
    private val songs = listOf(
        SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "T1", duration = 100),
        SongEntity(id = "s2", albumId = "a1", artistId = "ar1", title = "T2", duration = 100),
        SongEntity(id = "s3", albumId = "a1", artistId = "ar1", title = "T3", duration = 100),
    )

    @Test
    fun `startIndex returns position of the chosen song`() {
        assertEquals(1, PlayerController.startIndex(songs, "s2"))
    }

    @Test
    fun `startIndex defaults to 0 for unknown song`() {
        assertEquals(0, PlayerController.startIndex(songs, "unknown"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.navink.player.PlayerControllerTest"`
Expected: COMPILATION FAILURE — `startIndex` unresolved.

- [ ] **Step 3: Fix PlayerController**

In `PlayerController.kt`, replace the `playAlbum` function and the `companion object`:

```kotlin
    fun playAlbum(songs: List<SongEntity>, startSongId: String) {
        val creds = runBlocking { settingsRepository.getCredentials() }
        val items = songs.map { song ->
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
        val start = startIndex(songs, startSongId)
        controller?.apply {
            setMediaItems(items, start, 0L)
            prepare()
            play()
        }
        val current = songs.getOrNull(start)
        _state.value = _state.value.copy(
            currentSongId = current?.id,
            currentCoverArtId = current?.coverArtId,
            hasQueue = items.isNotEmpty(),
        )
    }
```

```kotlin
    companion object {
        fun startIndex(songs: List<SongEntity>, startSongId: String): Int =
            songs.indexOfFirst { it.id == startSongId }.coerceAtLeast(0)
    }
```

(`buildQueue` is deleted — the whole album is now the queue, so ⏮/⏭ reach every track.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.navink.player.PlayerControllerTest"`
Expected: 2 tests PASS.

- [ ] **Step 5: Commit**

```sh
git add app/src/main/java/com/navink/player/PlayerController.kt app/src/test/java/com/navink/player/PlayerControllerTest.kt
git commit -m "fix: play full album queue so previous/next reach all tracks"
```

---

### Task 9: PlayerViewModel offline-aware play + delete download; Now Playing button

**Files:**
- Modify: `app/src/main/java/com/navink/ui/player/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/navink/ui/player/NowPlayingScreen.kt`
- Modify: `app/src/main/java/com/navink/data/repository/MusicRepository.kt`

- [ ] **Step 1: Extend MusicRepository**

Replace `app/src/main/java/com/navink/data/repository/MusicRepository.kt` with:

```kotlin
package com.navink.data.repository

import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    suspend fun albumsForArtistOnce(artistId: String): List<AlbumEntity> = albumDao.albumsForArtistOnce(artistId)
    suspend fun songById(id: String): SongEntity? = songDao.songById(id)
    fun downloadedSongs(): Flow<List<SongEntity>> = songDao.downloadedSongs()

    fun artistsWithDownloads(): Flow<List<ArtistEntity>> = artistDao.artistsWithDownloads()
    fun albumsWithDownloadsForArtist(artistId: String): Flow<List<AlbumEntity>> =
        albumDao.albumsWithDownloadsForArtist(artistId)
    fun downloadedSongsForAlbum(albumId: String): Flow<List<SongEntity>> =
        songDao.downloadedSongsForAlbum(albumId)
    suspend fun downloadedSongsForAlbumOnce(albumId: String): List<SongEntity> =
        songDao.downloadedSongsForAlbumOnce(albumId)
    fun downloadedCountByAlbum(): Flow<Map<String, Int>> =
        songDao.downloadedCountByAlbum().map { list -> list.associate { it.albumId to it.cnt } }
    fun downloadedAlbumCountByArtist(): Flow<Map<String, Int>> =
        songDao.downloadedAlbumCountByArtist().map { list -> list.associate { it.artistId to it.cnt } }
}
```

- [ ] **Step 2: Update PlayerViewModel**

In `PlayerViewModel.kt`:

Replace `playSongFromAlbum`:

```kotlin
    fun playSongFromAlbum(songId: String, albumId: String) {
        viewModelScope.launch {
            val offline = settingsRepository.getOfflineMode()
            val songs = if (offline) {
                musicRepository.downloadedSongsForAlbumOnce(albumId)
            } else {
                musicRepository.songsForAlbumOnce(albumId)
            }
            playerController.playAlbum(songs, songId)
        }
    }
```

Replace `downloadCurrentSong` and add `deleteCurrentSongDownload`:

```kotlin
    fun downloadCurrentSong() {
        val songId = state.value.currentSongId ?: return
        _isDownloadingCurrentSong.value = true
        viewModelScope.launch { downloadRepository.enqueueSong(songId) }
    }

    fun deleteCurrentSongDownload() {
        val songId = state.value.currentSongId ?: return
        viewModelScope.launch { downloadRepository.deleteSongDownload(songId) }
    }
```

- [ ] **Step 3: Update NowPlayingScreen**

In `NowPlayingScreen.kt`, replace the download button block (the `if (state.currentSongId != null && !isDownloaded)` block) with:

```kotlin
        if (state.currentSongId != null) {
            if (!isDownloaded) {
                OutlinedButtonMMD(
                    onClick = { if (!isDownloading) viewModel.downloadCurrentSong() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(if (isDownloading) "Queued…" else "Download")
                }
            } else {
                OutlinedButtonMMD(
                    onClick = { viewModel.deleteCurrentSongDownload() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("Remove download")
                }
            }
        }
```

- [ ] **Step 4: Compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: still failing only in `BrowseViewModel`/`DownloadsScreen` (Tasks 10/12) if those aren't done yet; no errors in player/music files.

- [ ] **Step 5: Commit**

```sh
git add app/src/main/java/com/navink/ui/player app/src/main/java/com/navink/data/repository/MusicRepository.kt
git commit -m "feat: offline-aware playback queue and per-song download removal"
```

---

### Task 10: BrowseViewModel rewire

**Files:**
- Modify: `app/src/main/java/com/navink/ui/browse/BrowseViewModel.kt` (full replacement)

- [ ] **Step 1: Replace BrowseViewModel**

```kotlin
package com.navink.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.DownloadQueueEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.repository.DownloadRepository
import com.navink.data.repository.MusicRepository
import com.navink.data.repository.SettingsRepository
import com.navink.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val artists: List<ArtistEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val songs: List<SongEntity> = emptyList(),
    val downloadQueue: List<DownloadQueueEntity> = emptyList(),
    val downloadedCountByAlbum: Map<String, Int> = emptyMap(),
    val downloadedAlbumCountByArtist: Map<String, Int> = emptyMap(),
    val isSyncing: Boolean = false,
    val isLoadingAlbums: Boolean = false,
    val syncError: String? = null,
    val albumSyncError: String? = null,
    val downloadMessage: String? = null,
    val isOfflineMode: Boolean = false,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val syncRepository: SyncRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(isOfflineMode = settingsRepository.getOfflineMode())
        }
        viewModelScope.launch {
            downloadRepository.queue().collect { q ->
                _state.value = _state.value.copy(downloadQueue = q)
            }
        }
        viewModelScope.launch {
            musicRepository.downloadedCountByAlbum().collect { counts ->
                _state.value = _state.value.copy(downloadedCountByAlbum = counts)
            }
        }
        viewModelScope.launch {
            musicRepository.downloadedAlbumCountByArtist().collect { counts ->
                _state.value = _state.value.copy(downloadedAlbumCountByArtist = counts)
            }
        }
    }

    fun toggleOfflineMode() {
        viewModelScope.launch {
            val next = !_state.value.isOfflineMode
            if (next) downloadRepository.verifyDownloads()
            settingsRepository.saveOfflineMode(next)
            _state.value = _state.value.copy(isOfflineMode = next)
            observeArtists()
        }
    }

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

    private var artistsJob: Job? = null
    fun observeArtists() {
        artistsJob?.cancel()
        artistsJob = viewModelScope.launch {
            val flow = if (_state.value.isOfflineMode) {
                musicRepository.artistsWithDownloads()
            } else {
                musicRepository.allArtists()
            }
            flow.collect { list -> _state.value = _state.value.copy(artists = list) }
        }
    }

    private var albumsJob: Job? = null
    fun observeAlbums(artistId: String) {
        albumsJob?.cancel()
        _state.value = _state.value.copy(albums = emptyList())
        albumsJob = viewModelScope.launch {
            val flow = if (_state.value.isOfflineMode) {
                musicRepository.albumsWithDownloadsForArtist(artistId)
            } else {
                musicRepository.albumsForArtist(artistId)
            }
            flow.collect { list -> _state.value = _state.value.copy(albums = list) }
        }
    }

    fun syncArtistAlbums(artistId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingAlbums = true, albumSyncError = null)
            try {
                syncRepository.syncArtist(artistId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(albumSyncError = e.message ?: e.javaClass.simpleName)
            } finally {
                _state.value = _state.value.copy(isLoadingAlbums = false)
            }
        }
    }

    private var songsJob: Job? = null
    fun observeSongs(albumId: String) {
        songsJob?.cancel()
        _state.value = _state.value.copy(songs = emptyList())
        songsJob = viewModelScope.launch {
            val flow = if (_state.value.isOfflineMode) {
                musicRepository.downloadedSongsForAlbum(albumId)
            } else {
                musicRepository.songsForAlbum(albumId)
            }
            flow.collect { list -> _state.value = _state.value.copy(songs = list) }
        }
    }

    fun downloadAlbum(albumId: String) {
        viewModelScope.launch {
            try {
                syncRepository.syncAlbumSongs(albumId)
            } catch (_: Exception) {}
            val queued = downloadRepository.enqueueAlbum(albumId)
            _state.value = _state.value.copy(
                downloadMessage = if (queued == 0) "Already downloaded" else "Queued $queued tracks"
            )
        }
    }

    fun downloadArtist(artistId: String) {
        viewModelScope.launch {
            try {
                syncRepository.syncArtist(artistId)
            } catch (_: Exception) {}
            val queued = downloadRepository.enqueueArtist(artistId)
            _state.value = _state.value.copy(
                downloadMessage = if (queued == 0) "Already downloaded" else "Queued $queued tracks"
            )
        }
    }

    fun deleteAlbumDownloads(albumId: String) {
        viewModelScope.launch {
            downloadRepository.deleteAlbumDownloads(albumId)
            _state.value = _state.value.copy(downloadMessage = "Downloads removed")
        }
    }

    fun retryFailedDownloads() {
        viewModelScope.launch { downloadRepository.retryFailed() }
    }

    fun clearFailedDownloads() {
        viewModelScope.launch { downloadRepository.clearFailed() }
    }

    fun clearDownloadMessage() {
        _state.value = _state.value.copy(downloadMessage = null)
    }
}
```

(Removed: `Context`/WorkManager imports, `downloadedSongs` state, `storageLocation` state, `loadStorageLocation`, `setStorageLocation` — storage now lives in `SettingsViewModel`, Task 13.)

- [ ] **Step 2: Commit** (after Tasks 11–12 restore screen compilation; or stage now and commit with them if preferred — keep this file's changes its own commit)

```sh
git add app/src/main/java/com/navink/ui/browse/BrowseViewModel.kt
git commit -m "refactor: BrowseViewModel uses DownloadRepository and SQL offline queries"
```

---

### Task 11: Browse screens — indicators, offline via VM, settings gear

**Files:**
- Modify: `app/src/main/java/com/navink/ui/browse/ArtistsScreen.kt`
- Modify: `app/src/main/java/com/navink/ui/browse/AlbumsScreen.kt`
- Modify: `app/src/main/java/com/navink/ui/browse/SongsScreen.kt`
- Modify: `app/src/main/java/com/navink/NavGraph.kt`

- [ ] **Step 1: ArtistsScreen**

Changes:
1. Add `onNavigateToSettings: () -> Unit` parameter (after `onNavigateToDownloads`).
2. Delete the `displayedArtists` offline-filter block; use `state.artists` directly (VM picks the query).
3. In the `TopAppBar` `actions`, the downloads button stays visible in offline mode too (the queue is reachable any time), and add a gear:

```kotlin
                actions = {
                    TextButton(onClick = { viewModel.toggleOfflineMode() }) {
                        Text(if (state.isOfflineMode) "Online" else "Offline")
                    }
                    TextButton(onClick = onNavigateToDownloads) { Text("↓") }
                    TextButton(onClick = onNavigateToSettings) { Text("⚙") }
                    if (!state.isOfflineMode) {
                        TextButton(onClick = onNavigateToSearch) { Text("🔍") }
                        if (!state.isSyncing) {
                            TextButton(onClick = { viewModel.syncOnLaunch() }) { Text("↻") }
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
```

4. Replace the `offlineAlbumCountByArtist` in-memory computation with the VM-provided map; the `items` block becomes:

```kotlin
                items(state.artists, key = { it.id }) { artist ->
                    ArtistRow(
                        artist = artist,
                        albumCount = if (state.isOfflineMode) {
                            state.downloadedAlbumCountByArtist[artist.id] ?: 0
                        } else {
                            artist.albumCount
                        },
                        onClick = { onArtistClick(artist.id) },
                        onLongClick = { if (!state.isOfflineMode) viewModel.downloadArtist(artist.id) },
                    )
                    HorizontalDivider()
                }
```

(`ArtistRow` itself is unchanged.)

- [ ] **Step 2: AlbumsScreen**

Changes:
1. Delete the `displayedAlbums` offline-filter block; use `state.albums`.
2. `AlbumRow` gains a `downloadedCount: Int` parameter and shows it; the `items` block and row become:

```kotlin
                    items(state.albums, key = { it.id }) { album ->
                        AlbumRow(
                            album = album,
                            downloadedCount = state.downloadedCountByAlbum[album.id] ?: 0,
                            onClick = { onAlbumClick(album.id) },
                            onLongClick = { viewModel.downloadAlbum(album.id) },
                        )
                        HorizontalDivider()
                    }
```

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRow(album: AlbumEntity, downloadedCount: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = album.name, style = MaterialTheme.typography.bodyLarge)
            album.year?.let { Text(text = it.toString(), style = MaterialTheme.typography.bodySmall) }
        }
        if (downloadedCount > 0) {
            Text(
                text = "$downloadedCount/${album.songCount} ↓",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

3. Also update the empty/error condition `displayedAlbums.isEmpty()` → `state.albums.isEmpty()`.

- [ ] **Step 3: SongsScreen**

Replace `app/src/main/java/com/navink/ui/browse/SongsScreen.kt` with:

```kotlin
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    albumId: String,
    onSongClick: (songId: String, albumId: String) -> Unit,
    onBack: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(albumId) {
        viewModel.observeSongs(albumId)
    }

    LaunchedEffect(state.downloadMessage) {
        if (state.downloadMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearDownloadMessage()
        }
    }

    val downloadedCount = state.downloadedCountByAlbum[albumId] ?: 0

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove downloads?") },
            text = { Text("Deletes $downloadedCount downloaded tracks of this album from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAlbumDownloads(albumId)
                    confirmRemove = false
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.downloadMessage ?: "Songs") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    if (!state.isOfflineMode && downloadedCount < state.songs.size) {
                        TextButton(onClick = { viewModel.downloadAlbum(albumId) }) { Text("↓ All") }
                    }
                    if (downloadedCount > 0) {
                        TextButton(onClick = { confirmRemove = true }) { Text("Remove ↓") }
                    }
                },
            )
        },
        bottomBar = miniPlayer,
    ) { padding ->
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
            Spacer(Modifier.width(8.dp))
            Text(text = "↓", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
```

- [ ] **Step 4: NavGraph — wire the gear**

In `NavGraph.kt`, the `browse/artists` composable gains the settings callback:

```kotlin
        composable("browse/artists") {
            ArtistsScreen(
                onArtistClick = { artistId -> navController.navigate("browse/albums/$artistId") },
                onNavigateToSearch = { navController.navigate("search") },
                onNavigateToDownloads = { navController.navigate("downloads") },
                onNavigateToSettings = { navController.navigate("settings/edit") },
                miniPlayer = miniPlayer,
                viewModel = browseViewModel,
            )
        }
```

And `settings/edit` passes a back handler (Task 13 adds the parameter):

```kotlin
        composable("settings/edit") {
            SettingsScreen(
                onConnected = { navController.navigateUp() },
                onBack = { navController.navigateUp() },
            )
        }
```

- [ ] **Step 5: Commit**

```sh
git add app/src/main/java/com/navink/ui/browse app/src/main/java/com/navink/NavGraph.kt
git commit -m "feat: download indicators, album download/remove actions, settings entry"
```

---

### Task 12: DownloadsScreen — live queue, no storage toggle

**Files:**
- Modify: `app/src/main/java/com/navink/ui/downloads/DownloadsScreen.kt` (full replacement)

- [ ] **Step 1: Replace DownloadsScreen**

```kotlin
package com.navink.ui.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.navink.data.local.entity.DownloadQueueEntity
import com.navink.ui.browse.BrowseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onSongClick: (songId: String, albumId: String) -> Unit,
    onBack: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val hasFailed = state.downloadQueue.any { it.status == DownloadQueueEntity.STATUS_FAILED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
            )
        },
        bottomBar = miniPlayer,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (state.downloadQueue.isEmpty()) {
                item {
                    Text(
                        text = "No active downloads. Long-press an artist or album to queue downloads, or use ↓ All on an album.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(state.downloadQueue, key = { it.songId }) { item ->
                    QueueItemRow(item = item)
                    HorizontalDivider()
                }
                if (hasFailed) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButtonMMD(
                                onClick = { viewModel.retryFailedDownloads() },
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) { Text("Retry failed") }
                            OutlinedButtonMMD(
                                onClick = { viewModel.clearFailedDownloads() },
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) { Text("Clear failed") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(item: DownloadQueueEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
            if (item.status == DownloadQueueEntity.STATUS_FAILED && item.errorMessage != null) {
                Text(text = item.errorMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            text = when (item.status) {
                DownloadQueueEntity.STATUS_RUNNING -> "${item.progressPercent}%"
                DownloadQueueEntity.STATUS_FAILED -> "Failed"
                else -> "Queued"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (all main sources now consistent), except `SettingsScreen` `onBack` param pending Task 13 — if NavGraph already references it, do Task 13 Step 1–2 before this compile.

- [ ] **Step 3: Commit**

```sh
git add app/src/main/java/com/navink/ui/downloads/DownloadsScreen.kt
git commit -m "feat: downloads screen shows live per-track progress with retry/clear"
```

---

### Task 13: Settings screen — server + storage + offline + usage

**Files:**
- Modify: `app/src/main/java/com/navink/ui/settings/SettingsViewModel.kt` (full replacement)
- Modify: `app/src/main/java/com/navink/ui/settings/SettingsScreen.kt` (full replacement)

- [ ] **Step 1: Replace SettingsViewModel**

```kotlin
package com.navink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.remote.SubsonicService
import com.navink.data.repository.DownloadRepository
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
    val storageLocation: String = "external",
    val offlineMode: Boolean = false,
    val storageUsedMb: Long = 0,
    val verifyMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val service: SubsonicService,
    private val downloadRepository: DownloadRepository,
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
                storageLocation = settingsRepository.getStorageLocation(),
                offlineMode = settingsRepository.getOfflineMode(),
            )
            refreshStorageUsed()
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
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    fun setStorageLocation(location: String) {
        viewModelScope.launch {
            settingsRepository.saveStorageLocation(location)
            _state.value = _state.value.copy(storageLocation = location)
        }
    }

    fun toggleOfflineMode() {
        viewModelScope.launch {
            val next = !_state.value.offlineMode
            if (next) downloadRepository.verifyDownloads()
            settingsRepository.saveOfflineMode(next)
            _state.value = _state.value.copy(offlineMode = next)
        }
    }

    fun verifyDownloads() {
        viewModelScope.launch {
            val repaired = downloadRepository.verifyDownloads()
            _state.value = _state.value.copy(
                verifyMessage = if (repaired == 0) "All downloads OK" else "$repaired stale entries repaired"
            )
            refreshStorageUsed()
        }
    }

    private suspend fun refreshStorageUsed() {
        _state.value = _state.value.copy(
            storageUsedMb = downloadRepository.storageUsedBytes() / (1024 * 1024)
        )
    }
}
```

**Caveat:** the existing `connect()` error branch must match the current file — the replacement above mirrors it; if the actual file differs (e.g. exact error literal), keep the existing literal.

**Note for executor:** `BrowseViewModel` reads offline mode once at init. After toggling offline in settings and navigating back, the artists screen's `LaunchedEffect` calls `observeArtists()` but `BrowseViewModel._state.isOfflineMode` is stale. Fix: in `BrowseViewModel.observeArtists()`, refresh the flag first:

```kotlin
    fun observeArtists() {
        artistsJob?.cancel()
        artistsJob = viewModelScope.launch {
            val offline = settingsRepository.getOfflineMode()
            _state.value = _state.value.copy(isOfflineMode = offline)
            val flow = if (offline) {
                musicRepository.artistsWithDownloads()
            } else {
                musicRepository.allArtists()
            }
            flow.collect { list -> _state.value = _state.value.copy(artists = list) }
        }
    }
```

Apply this variant in Task 10 (it replaces the simpler `observeArtists` shown there).

- [ ] **Step 2: Replace SettingsScreen**

```kotlin
package com.navink.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

@Composable
fun SettingsScreen(
    onConnected: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onBack != null) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start),
            ) { Text("← Back") }
        }
        Text(text = "Navink", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))

        Text(
            text = "Server",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start),
        )
        Spacer(Modifier.height(8.dp))

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
        Spacer(Modifier.height(16.dp))

        if (state.error != null) {
            Text(text = state.error!!, color = Color.Black)
            Spacer(Modifier.height(8.dp))
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            ButtonMMD(
                onClick = { viewModel.connect(onConnected) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            ) {
                Text("Connect")
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start),
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val external = state.storageLocation == "external"
            if (external) {
                ButtonMMD(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("SD Card") }
                OutlinedButtonMMD(
                    onClick = { viewModel.setStorageLocation("internal") },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Internal") }
            } else {
                OutlinedButtonMMD(
                    onClick = { viewModel.setStorageLocation("external") },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("SD Card") }
                ButtonMMD(
                    onClick = {},
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Internal") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "New downloads go to the selected storage. Existing downloads stay where they are.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedButtonMMD(
            onClick = { viewModel.toggleOfflineMode() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (state.offlineMode) "Offline mode: On" else "Offline mode: Off")
        }
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Storage used: ${state.storageUsedMb} MB",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.Start),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedButtonMMD(
            onClick = { viewModel.verifyDownloads() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Verify downloads") }
        state.verifyMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
    }
}
```

(The filled button marks the active storage choice; tapping it is a no-op.)

- [ ] **Step 3: Compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```sh
git add app/src/main/java/com/navink/ui/settings
git commit -m "feat: settings screen with storage location, offline toggle, storage usage"
```

---

### Task 14: Manifest — dataSync foreground service

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add permission and service override**

After line 8 (`POST_NOTIFICATIONS` permission) add:

```xml
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
```

Inside `<application>`, after the existing `InitializationProvider` block, add:

```xml
        <service
            android:name="androidx.work.impl.foreground.SystemForegroundService"
            android:foregroundServiceType="dataSync"
            tools:node="merge" />
```

- [ ] **Step 2: Build APK to validate manifest merge**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Commit**

```sh
git add app/src/main/AndroidManifest.xml
git commit -m "feat: declare dataSync foreground service for download worker"
```

---

### Task 15: Full verification

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: all tests pass (existing `MusicRepositoryTest`, `SongDaoTest`, `PlayerControllerTest`, plus new `DownloadQueueDaoTest`, `SyncRepositoryTest`, `DownloadWorkerTest`, `DownloadRepositoryTest`).

- [ ] **Step 2: Build the APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Check spec acceptance criteria**

Walk `docs/superpowers/specs/2026-06-10-download-offline-overhaul-design.md` acceptance criteria 1–10; each is covered by a test or a code path above. Criteria needing on-device confirmation (progress display, e-ink rendering, ⏮/⏭ feel): note in the final summary that the APK must be sideloaded via FastMail per CLAUDE.md deployment flow.

- [ ] **Step 4: Request code review**

Use the code-reviewer pass (per repo workflow) on the full branch diff before shipping.

---

## Self-Review Notes

- Spec §1–§9 → Tasks 1/5 (queue+worker), 2/4 (sync preservation), 2/3/10/11 (offline queries+order), 8/9 (player), 11/12 (indicators+queue UI), 13 (settings), 6/9/11 (delete), 6/7 (recovery/consolidation), 14 (foreground). Spec's "DONE" queue status is realised as immediate row deletion on success — equivalent observable behaviour, fewer states.
- Known compile-order coupling between Tasks 5/6/9/10/12/13 is flagged inline; commits stay one-concern.
- `downloadedSongs()` (title-ordered) is retained solely for `PlayerViewModel.isCurrentSongDownloaded`; no UI lists it anymore.
