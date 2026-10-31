package com.nido.music.core.network.downloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun downloadSong(
        downloadUrl: String,
        title: String,
        artist: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val sanitizedTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val sanitizedArtist = artist.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val fileName = if (sanitizedArtist.isNotBlank()) {
                "$sanitizedArtist - $sanitizedTitle.mp3"
            } else {
                "$sanitizedTitle.mp3"
            }

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("$sanitizedTitle - $sanitizedArtist")
                setDescription("Downloading track...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Nido/$fileName")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            downloadManager.enqueue(request)
        }
    }
}
