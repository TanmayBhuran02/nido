package com.nido.music.feature.music.data.lyrics

data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

data class LyricsResult(
    val isSynced: Boolean,
    val lines: List<LyricsLine>,
    val plainLyrics: String? = null
)

interface LyricsProvider {
    suspend fun getLyrics(title: String, artist: String, durationSec: Long? = null): LyricsResult?
}
