# Download & Offline Overhaul — Design

Date: 2026-06-10
Status: Approved

## Problem

Download and offline functionality is unreliable:

1. No download progress visibility; unclear whether downloads happen at all.
2. Downloads run in parallel (one WorkManager job per song, plain `enqueue`).
3. Offline mode lists are filtered in memory from a title-ordered query; accuracy not trusted.
4. Downloaded albums "disappear": library sync uses Room `@Upsert` with fresh entities whose
   `isDownloaded`/`localPath` default to false/null, so every album/artist sync wipes download
   flags. Files remain on disk; the DB forgets them.
5. No downloaded indicator on album rows; song indicator is a faint "↓".
6. Player ⏮/⏭ inconsistent: `buildQueue` truncates the album from the clicked track onward
   (`subList(startIndex, size)`), so previous tracks are never in the queue.
7. Offline album browse order is alphabetical (`downloadedSongs()` is `ORDER BY title`) while
   playback uses track order — browse and play disagree.
8. Storage location (internal/SD) is asked on the Downloads screen per visit instead of being a
   one-time setting; server settings are only reachable on first run.

Secondary reliability bugs found during analysis:

- Worker treats any HTTP 200 as success; Subsonic returns 200 with a JSON error body, so a
  "downloaded" file can be JSON garbage marked as complete.
- Infinite `Result.retry()` with no attempt cap.
- Files are written directly to the final path; a crash mid-write leaves a partial file.

## Design

### 1. DB-backed download queue

Replace per-song WorkManager jobs with a queue table drained by a single worker.

- New `DownloadQueueEntity`: `songId` (PK), `title`, `status` (QUEUED / RUNNING / DONE / FAILED),
  `progressPercent`, `errorMessage`, `enqueuedAt`.
- `DownloadWorker` becomes a drainer enqueued as unique work (`ExistingWorkPolicy.KEEP`):
  take oldest QUEUED row → download → mark DONE or FAILED → next, until queue empty.
  Strictly one track at a time. A failed item does not stop the rest (WorkManager `APPEND`
  chains are rejected because one failure cancels the remaining chain).
- Worker calls `setForeground()` with a progress notification, avoiding the ~10-minute
  JobScheduler execution cap during long album downloads.
- Pending queue rows re-kick the worker on app start.

### 2. Worker reliability

- Download to `<songId>.mp3.part`, rename to final name only on completion.
- Reject responses whose content-type is JSON/XML → FAILED with the error message.
- Write `progressPercent` to the queue row, throttled to ~5% steps (e-ink friendly).
- Maximum 3 attempts per track, then FAILED and move on.
- Songs already downloaded are skipped at enqueue time.

### 3. Sync preserves download state

Replace blind `@Upsert` for songs with `@Insert(onConflict = IGNORE)` plus a metadata-only
`UPDATE` (title, trackNumber, duration, coverArtId, isStarred, albumId, artistId) inside a
`@Transaction`. `isDownloaded` and `localPath` are never written by sync.

### 4. Offline queries in SQL

- `downloadedSongsForAlbum(albumId)` — `WHERE isDownloaded = 1 ORDER BY trackNumber, title`.
- `albumsWithDownloads()` / `artistsWithDownloads()` — join against downloaded songs.
- `downloadedCountByAlbum()` — `GROUP BY albumId` count for album-row indicators.
- Offline mode UI uses these queries; the in-memory `downloadedSongs.filter { ... }` paths are
  removed.
- Entering offline mode runs verify-and-repair: for each song with `isDownloaded = 1`, check
  `localPath` exists on disk; clear the flag if not.

### 5. Player queue

`playAlbum` keeps the full track list and uses `setMediaItems(items, startIndex, 0)`.
`buildQueue` truncation is deleted. In offline mode the queue is the downloaded-songs-in-track-
order list, so browse order equals play order.

### 6. Indicators

- Album rows show `n/m ↓` (downloaded / total tracks) when n > 0.
- Song rows keep ↓ but rendered bolder.
- Downloads screen queue rows show status and percent per track.

### 7. Settings screen

Single settings screen, reachable via a gear button on the Artists (top-level) screen:

- Server URL / username / password with test-connection (reuses existing settings UI).
- Storage location (internal / SD), set once. New downloads go to the chosen location;
  previously downloaded files keep playing via their stored absolute `localPath`.
- Offline mode toggle (the existing quick toggle in the browse UI stays as well).
- Storage used (MB) and a "Verify downloads" button (same repair as the offline toggle).
- The Downloads screen keeps only the queue; its storage toggle is removed.

### 8. Delete downloads

- Album level: "Remove downloads" on the album's song list deletes files and clears flags.
- Song level: delete option for the current song on Now Playing.

### 9. Consolidation

All enqueue / delete / verify / storage logic moves into `DownloadRepository`. `BrowseViewModel`
loses its direct WorkManager usage and title-tag hacks. Room schema bump with an additive
migration (new queue table only; existing data untouched).

## Error handling

- Per-track failure: row marked FAILED with message, visible on Downloads screen; queue
  continues.
- Server unreachable mid-queue: attempts fail per track (3 tries each); remaining items stay
  QUEUED for the next worker run.
- Storage dir unavailable (SD removed): item FAILED with a clear message.

## Testing

- Characterisation tests first (current sync/DAO behaviour) before refactoring.
- Unit tests (Robolectric + in-memory Room): upsert preserves download columns; ordering of
  `downloadedSongsForAlbum`; verify-and-repair clears flags for missing files; queue drain
  marks DONE/FAILED correctly and processes sequentially.
- Mock only at boundaries (HTTP via OkHttp MockWebServer, filesystem via temp dirs).

## Acceptance criteria

1. Downloading an album shows per-track progress percent on the Downloads screen, and tracks
   download strictly one at a time.
2. Browsing/syncing an album whose songs are downloaded leaves `isDownloaded`/`localPath`
   unchanged (test-verified).
3. Offline mode lists exactly the albums/artists/songs whose files exist on disk after
   verify-and-repair.
4. Offline album track listing is in track-number order and matches playback order.
5. Album rows show a downloaded count; song rows show a downloaded marker.
6. From any track in an album, ⏮ reaches earlier tracks and ⏭ reaches later tracks.
7. A Subsonic JSON error response marks the track FAILED and writes no audio file.
8. Storage location and server settings are editable from one settings screen; the Downloads
   screen no longer asks where to save.
9. Album downloads can be removed, freeing disk space and clearing flags.
10. A track failing three times is marked FAILED and the queue continues with the next track.
