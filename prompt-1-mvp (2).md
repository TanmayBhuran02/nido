# Prompt 1 — MVP: Nido Music Player (offline, no server)

## Project summary
Android app named **Nido** (Spanish for "nest" — a safe place for loved ones). Final product = Signal-protocol end-to-end encrypted messenger + full offline music player. Standout feature: pick a segment of a local song and send it as a playable clip inside chat.

**This stage builds only the music half.** No server, no crypto, no accounts. Goal: a complete, solid, standalone music app that later stages extend with chat.

## Assumptions (decided, not asked)
- Native Android, Kotlin, Jetpack Compose. minSdk 26, targetSdk 35.
- Modular from day one so chat modules slot in later without refactor.
- Online music search/download uses a **pluggable `MusicSourceProvider` interface** backed by **licensed / openly-licensed catalogs** (Jamendo, Internet Archive, Audius). **Do NOT build scrapers for pagalworld.com, pagalnew.com, djpunjab.is, pagalworlds.com or similar sites** — they distribute copyrighted music without license. Interface stays open so the user can add any source they have rights to.

## High-level architecture
```
Android app (Compose, MVVM, Hilt)
 ├─ :app                  navigation, DI, theme
 ├─ :core:database        Room (plain for now; SQLCipher added in Stage 2)
 ├─ :core:network         Retrofit/OkHttp (used by discover now, chat later)
 ├─ :core:common          result types, dispatchers, utils
 ├─ :feature:music        library scanner, player, playlists, queue
 ├─ :feature:discover     online search via MusicSourceProvider
 └─ :feature:clipshare    waveform trimmer + local clip export (send comes in Stage 2)
Reserved for later: :core:crypto, :feature:chat
```

## Tech stack
- Kotlin 2.x, Compose Material 3, Hilt, Coroutines/Flow, Navigation-Compose
- Media3 (ExoPlayer + MediaSessionService + Transformer)
- Room, DataStore (settings), Coil (cover art)
- WorkManager (scans, downloads), Retrofit, OkHttp, kotlinx.serialization

## Folder structure
```
app/
core/{database,network,common}/
feature/{music,discover,clipshare,settings}/
  each: data/ domain/ ui/
```

## Data models (Room)
`Song(id, uri, title, artist, album, durationMs, folderPath, dateAdded, liked)`, `Playlist(id, name, createdAt)`, `PlaylistSong(playlistId, songId, position)`, `QueueItem(position, songId)`, `LibraryFolder(treeUri)`.

## MVP feature list
1. Pick one or more music folders (Storage Access Framework). **Recursive scan including subfolders** via WorkManager; incremental rescans by mtime; progress indicator. Fall back to filename when tags are missing.
2. Library views: all songs, albums, artists, folder-tree browse; search; fast scroll.
3. Player: play/pause/seek/next/prev, background playback, notification + lock-screen + Bluetooth controls, audio focus, mini-player + full-screen player.
4. Playlists: create, rename, delete, add/remove songs, reorder.
5. Queue: play next, add to queue, reorder, persisted across restarts.
6. Shuffle (no immediate repeats) and repeat off/one/all.
7. Liked songs (auto-playlist).
8. Sleep timer: 15/30/45/60 min, "end of song", custom, 10 s fade-out, visible countdown.
9. Lyrics viewer: embedded USLT tags and sidecar `.lrc` (synced highlight, tap-to-seek); "no lyrics" empty state.
10. Discover: search bar → results (cover, title, artist, duration, license badge) → 30 s preview → "Download full" into a chosen library subfolder with tags + cover art. One working provider (Jamendo).
11. Clip trimmer v0: waveform with start/end handles (max 30 s), preview loop, export clip as local audio file (no re-encode for AAC/M4A/MP3; transcode others to AAC). Sending comes in Stage 2.

## Instruction
**Build this project end-to-end as described. Prioritize correctness over polish. Deliver a working MVP.** Include a README with setup steps and how to add a new `MusicSourceProvider`.
