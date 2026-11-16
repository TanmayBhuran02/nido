package com.nido.music.feature.music.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nido.music.core.common.isAudioFile
import com.nido.music.core.common.parseFilenameToTitleArtist
import com.nido.music.core.database.dao.LibraryFolderDao
import com.nido.music.core.database.dao.SongDao
import com.nido.music.core.database.entity.SongEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val songDao: SongDao,
    private val folderDao: LibraryFolderDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Remove stale/revoked SAF links that cause Permission Denial
        songDao.deleteSafSongs()
        // Deduplicate existing records
        try {
            songDao.deduplicateSongs()
        } catch (_: Exception) {}

        val coversDir = File(appContext.filesDir, "covers").apply { mkdirs() }
        val allScannedUris = mutableListOf<String>()
        val batch = mutableListOf<SongEntity>()
        val allExisting = try {
            songDao.getAllSongsList()
        } catch (_: Exception) {
            emptyList()
        }
        val existingSongs = allExisting.associateBy { it.uri }
        val existingByMeta = allExisting.associateBy {
            "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}|${it.durationMs / 1000L}"
        }

        // 1. Scan device MediaStore audio files
        scanMediaStore(batch, allScannedUris, existingSongs, existingByMeta)
        if (batch.isNotEmpty()) {
            songDao.upsertSongs(batch)
            batch.clear()
        }

        // 2. Scan configured SAF folders if still valid
        val treeUriStr = inputData.getString(KEY_TREE_URI)
        val folders = if (treeUriStr != null) {
            listOf(treeUriStr)
        } else {
            folderDao.getAllFoldersList().map { it.treeUri }
        }

        for (uriStr in folders) {
            try {
                val root = DocumentFile.fromTreeUri(appContext, Uri.parse(uriStr)) ?: continue
                if (root.canRead()) {
                    scanDirectoryRecursive(root, root.name ?: "", coversDir, batch, allScannedUris, existingSongs)
                }
            } catch (_: Exception) {
            }
        }

        if (batch.isNotEmpty()) {
            songDao.upsertSongs(batch)
            batch.clear()
        }

        folders.forEach { uri ->
            folderDao.updateLastScanTimestamp(uri, System.currentTimeMillis())
        }

        try {
            songDao.deduplicateSongs()
        } catch (_: Exception) {}

        Result.success()
    }

    private fun scanMediaStore(
        batch: MutableList<SongEntity>,
        allScannedUris: MutableList<String>,
        existingSongs: Map<String, SongEntity>,
        existingByMeta: Map<String, SongEntity>
    ) {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val seenBatchUris = mutableSetOf<String>()
        val seenBatchMeta = mutableSetOf<String>()

        try {
            appContext.contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dateModCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(uri, id).toString()
                    val title = cursor.getString(titleCol)?.trim() ?: "Unknown"
                    val artist = cursor.getString(artistCol)?.trim() ?: "Unknown Artist"
                    val album = cursor.getString(albumCol)?.trim() ?: "Unknown Album"
                    val duration = cursor.getLong(durCol)

                    val dateAddedSec = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val dateModSec = if (dateModCol != -1) cursor.getLong(dateModCol) else 0L
                    val bestDateSec = when {
                        dateAddedSec > 0L -> dateAddedSec
                        dateModSec > 0L -> dateModSec
                        else -> 0L
                    }
                    val dateAddedMs = if (bestDateSec > 0L) bestDateSec * 1000L else System.currentTimeMillis()

                    val cleanArtist = if (artist.contains("<unknown>", ignoreCase = true)) "Unknown Artist" else artist
                    val cleanAlbum = if (album.contains("<unknown>", ignoreCase = true)) "Unknown Album" else album

                    val normTitle = title.lowercase()
                    val normArtist = cleanArtist.lowercase()
                    val durSec = duration / 1000L
                    val metaKey = if (normTitle.isNotBlank() && normTitle != "unknown") {
                        "$normTitle|$normArtist|$durSec"
                    } else null

                    if (!seenBatchUris.add(contentUri)) {
                        continue
                    }
                    if (metaKey != null && !seenBatchMeta.add(metaKey)) {
                        continue
                    }

                    allScannedUris.add(contentUri)
                    val existing = existingSongs[contentUri] ?: (if (metaKey != null) existingByMeta[metaKey] else null)
                    batch.add(
                        if (existing != null) {
                            existing.copy(
                                title = title,
                                artist = cleanArtist,
                                album = cleanAlbum,
                                durationMs = duration,
                                dateAdded = if (bestDateSec > 0L) dateAddedMs else existing.dateAdded
                            )
                        } else {
                            SongEntity(
                                uri = contentUri,
                                title = title,
                                artist = cleanArtist,
                                album = cleanAlbum,
                                durationMs = duration,
                                folderPath = "Internal Storage",
                                dateAdded = dateAddedMs
                            )
                        }
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ScanWorker", "Failed to query MediaStore", e)
        }
    }

    private suspend fun scanDirectoryRecursive(
        dir: DocumentFile,
        currentPath: String,
        coversDir: File,
        batch: MutableList<SongEntity>,
        allScannedUris: MutableList<String>,
        existingSongs: Map<String, SongEntity>
    ) {
        val files = dir.listFiles()
        var scannedCount = 0

        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryRecursive(
                    file,
                    "$currentPath/${file.name ?: ""}",
                    coversDir,
                    batch,
                    allScannedUris,
                    existingSongs
                )
            } else if (file.isFile && (file.name?.isAudioFile() == true)) {
                val uriStr = file.uri.toString()
                allScannedUris.add(uriStr)

                val song = extractSongMetadata(file, currentPath, coversDir, existingSongs[uriStr])
                batch.add(song)
                scannedCount++

                if (batch.size >= 100) {
                    songDao.upsertSongs(batch)
                    batch.clear()
                    setProgress(workDataOf(PROGRESS_COUNT to scannedCount))
                }
            }
        }
    }

    private fun extractSongMetadata(
        file: DocumentFile,
        folderPath: String,
        coversDir: File,
        existing: SongEntity? = null
    ): SongEntity {
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        var coverPath: String? = existing?.coverArtPath

        try {
            appContext.contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                if (coverPath == null) {
                    val pic = retriever.embeddedPicture
                    if (pic != null) {
                        val coverFile = File(coversDir, "cover_${file.uri.hashCode()}.jpg")
                        if (!coverFile.exists()) {
                            FileOutputStream(coverFile).use { it.write(pic) }
                        }
                        coverPath = coverFile.absolutePath
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val filename = file.name ?: "Unknown"
        val fallback = filename.parseFilenameToTitleArtist()
        val finalTitle = if (!title.isNullOrBlank()) title!! else fallback.first
        val finalArtist = if (!artist.isNullOrBlank()) artist!! else fallback.second
        val finalAlbum = if (!album.isNullOrBlank()) album!! else "Unknown Album"

        return if (existing != null) {
            existing.copy(
                title = finalTitle,
                artist = finalArtist,
                album = finalAlbum,
                durationMs = durationMs,
                folderPath = folderPath,
                lastModified = file.lastModified(),
                coverArtPath = coverPath
            )
        } else {
            SongEntity(
                uri = file.uri.toString(),
                title = finalTitle,
                artist = finalArtist,
                album = finalAlbum,
                durationMs = durationMs,
                folderPath = folderPath,
                dateAdded = System.currentTimeMillis(),
                lastModified = file.lastModified(),
                coverArtPath = coverPath
            )
        }
    }

    companion object {
        const val KEY_TREE_URI = "key_tree_uri"
        const val PROGRESS_COUNT = "progress_count"
    }
}
