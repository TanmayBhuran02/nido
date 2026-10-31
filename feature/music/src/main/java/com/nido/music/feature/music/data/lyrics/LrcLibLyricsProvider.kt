package com.nido.music.feature.music.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LrcLibLyricsProvider @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LyricsProvider {

    override suspend fun getLyrics(title: String, artist: String, durationSec: Long?): LyricsResult? = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            var url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
            if (durationSec != null && durationSec > 0) {
                url += "&duration=$durationSec"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NidoMusicPlayer/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val syncedLyrics = json.optString("syncedLyrics", "")
            if (syncedLyrics.isNotBlank()) {
                val lines = LrcParser.parse(syncedLyrics)
                return@withContext LyricsResult(isSynced = true, lines = lines)
            }

            val plainLyrics = json.optString("plainLyrics", "")
            if (plainLyrics.isNotBlank()) {
                val lines = plainLyrics.lines().map { LyricsLine(0L, it) }
                return@withContext LyricsResult(isSynced = false, lines = lines, plainLyrics = plainLyrics)
            }

            null
        } catch (e: Exception) {
            null
        }
    }
}
