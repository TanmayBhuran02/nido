package com.nido.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nido.music.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsList(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY title COLLATE NOCASE ASC")
    fun getSongsByAlbum(album: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title COLLATE NOCASE ASC")
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE folderPath = :folderPath ORDER BY title COLLATE NOCASE ASC")
    fun getSongsByFolder(folderPath: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE liked = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getLikedSongs(): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%' 
           OR album LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun searchSongs(query: String): Flow<List<SongEntity>>

    /** Distinct album names with one representative song per album (for cover art). */
    @Query("""
        SELECT DISTINCT album FROM songs 
        WHERE album != '' 
        ORDER BY album COLLATE NOCASE ASC
    """)
    fun getDistinctAlbums(): Flow<List<String>>

    /** Distinct artist names. */
    @Query("""
        SELECT DISTINCT artist FROM songs 
        WHERE artist != '' AND artist != 'Unknown Artist'
        ORDER BY artist COLLATE NOCASE ASC
    """)
    fun getDistinctArtists(): Flow<List<String>>

    /** Distinct folder paths for folder-tree browsing. */
    @Query("SELECT DISTINCT folderPath FROM songs ORDER BY folderPath ASC")
    fun getDistinctFolders(): Flow<List<String>>

    /** Count songs per album. */
    @Query("SELECT COUNT(*) FROM songs WHERE album = :album")
    suspend fun getSongCountByAlbum(album: String): Int

    /** Count songs per artist. */
    @Query("SELECT COUNT(*) FROM songs WHERE artist = :artist")
    suspend fun getSongCountByArtist(artist: String): Int

    @Query("UPDATE songs SET liked = NOT liked WHERE id = :id")
    suspend fun toggleLike(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: SongEntity): Long

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<Long>)

    @Query("UPDATE songs SET dateAdded = :dateAdded WHERE id = :id")
    suspend fun updateSongDateAdded(id: Long, dateAdded: Long)

    @Transaction
    suspend fun deduplicateSongs(): Int {
        val allSongs = getAllSongsList()
        if (allSongs.isEmpty()) return 0

        val seenUris = mutableMapOf<String, Long>()
        val seenMeta = mutableMapOf<String, Long>()
        val idsToDelete = mutableListOf<Long>()

        for (song in allSongs) {
            val uriKey = song.uri.trim()
            val normTitle = song.title.trim().lowercase()
            val normArtist = song.artist.trim().lowercase()
            val durSec = song.durationMs / 1000L
            val metaKey = if (normTitle.isNotBlank() && normTitle != "unknown") {
                "$normTitle|$normArtist|$durSec"
            } else null

            val existingUriId = seenUris[uriKey]
            val existingMetaId = if (metaKey != null) seenMeta[metaKey] else null
            val canonicalId = existingUriId ?: existingMetaId

            if (canonicalId != null && canonicalId != song.id) {
                idsToDelete.add(song.id)
            } else {
                seenUris[uriKey] = song.id
                if (metaKey != null && !seenMeta.containsKey(metaKey)) {
                    seenMeta[metaKey] = song.id
                }
            }
        }

        if (idsToDelete.isNotEmpty()) {
            idsToDelete.chunked(400).forEach { chunk ->
                deleteSongsByIds(chunk)
            }
        }
        return idsToDelete.size
    }

    @Query("DELETE FROM songs WHERE uri IN (:uris)")
    suspend fun deleteSongsByUris(uris: List<String>)

    @Query("DELETE FROM songs WHERE uri NOT IN (:validUris)")
    suspend fun deleteStaleNotIn(validUris: List<String>)

    @Query("DELETE FROM songs WHERE uri LIKE 'content://com.android.externalstorage%'")
    suspend fun deleteSafSongs()

    @Query("SELECT uri FROM songs")
    suspend fun getAllUris(): List<String>

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCount(): Flow<Int>

    /** Get the first song in an album (for cover art lookup). */
    @Query("SELECT * FROM songs WHERE uri = :uri LIMIT 1")
    suspend fun getSongByUri(uri: String): SongEntity?

    @Query("SELECT * FROM songs WHERE album = :album LIMIT 1")
    suspend fun getFirstSongInAlbum(album: String): SongEntity?

    @Query("SELECT * FROM songs WHERE artist = :artist LIMIT 1")
    suspend fun getFirstSongByArtist(artist: String): SongEntity?
}
