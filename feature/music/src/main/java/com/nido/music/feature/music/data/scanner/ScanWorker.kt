package com.nido.music.feature.music.data.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
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
        val treeUriStr = inputData.getString(KEY_TREE_URI)
        val folders = if (treeUriStr != null) {
            listOf(treeUriStr)
        } else {
            folderDao.getAllFoldersList().map { it.treeUri }
        }

        if (folders.isEmpty()) {
            return@withContext Result.success()
        }

        val coversDir = File(appContext.filesDir, "covers").apply { mkdirs() }
        val allScannedUris = mutableListOf<String>()
        val batch = mutableListOf<SongEntity>()

        for (uriStr in folders) {
            val root = DocumentFile.fromTreeUri(appContext, Uri.parse(uriStr)) ?: continue
            scanDirectoryRecursive(root, root.name ?: "", coversDir, batch, allScannedUris)
        }

        if (batch.isNotEmpty()) {
            songDao.upsertSongs(batch)
            batch.clear()
        }

        // Clean stale songs if scanning all configured folders
        if (treeUriStr == null) {
            songDao.deleteStaleNotIn(allScannedUris)
        }

        folders.forEach { uri ->
            folderDao.updateLastScanTimestamp(uri, System.currentTimeMillis())
        }

        Result.success()
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
