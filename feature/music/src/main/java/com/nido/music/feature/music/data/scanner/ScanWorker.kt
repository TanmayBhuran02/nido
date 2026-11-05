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

        val coversDir = File(appContext.filesDir, "covers").apply { mkdirs() }
        val allScannedUris = mutableListOf<String>()
        val batch = mutableListOf<SongEntity>()

        // 1. Scan device MediaStore audio files
        scanMediaStore(batch, allScannedUris)
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
                    scanDirectoryRecursive(root, root.name ?: "", coversDir, batch, allScannedUris)
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

        Result.success()
    }

    private fun scanMediaStore(
        batch: MutableList<SongEntity>,
        allScannedUris: MutableList<String>
    ) {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            appContext.contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(uri, id).toString()
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val duration = cursor.getLong(durCol)

                    allScannedUris.add(contentUri)
                    batch.add(
                        SongEntity(
                            uri = contentUri,
                            title = title,
                            artist = if (artist.contains("<unknown>")) "Unknown Artist" else artist,
                            album = if (album.contains("<unknown>")) "Unknown Album" else album,
                            durationMs = duration,
                            folderPath = "Internal Storage",
                            dateAdded = System.currentTimeMillis()
                        )
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
        allScannedUris: MutableList<String>
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
                    allScannedUris
                )
            } else if (file.isFile && (file.name?.isAudioFile() == true)) {
                val uriStr = file.uri.toString()
                allScannedUris.add(uriStr)

                val song = extractSongMetadata(file, currentPath, coversDir)
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
        coversDir: File
    ): SongEntity {
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        var coverPath: String? = null

        try {
            appContext.contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                val pic = retriever.embeddedPicture
                if (pic != null) {
                    val coverFile = File(coversDir, "cover_${file.uri.hashCode()}.jpg")
                    if (!coverFile.exists()) {
                        FileOutputStream(coverFile).use { it.write(pic) }
                    }
                    coverPath = coverFile.absolutePath
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

        return SongEntity(
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

    companion object {
        const val KEY_TREE_URI = "key_tree_uri"
        const val PROGRESS_COUNT = "progress_count"
    }
}
