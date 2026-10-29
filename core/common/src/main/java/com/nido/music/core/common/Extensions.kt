package com.nido.music.core.common

import android.net.Uri

/**
 * Extension utilities used across modules.
 */

/** Format milliseconds to mm:ss or h:mm:ss display string. */
fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

/** Parse a filename into a best-guess (title, artist) pair when tags are missing. */
fun String.parseFilenameToTitleArtist(): Pair<String, String> {
    val nameWithoutExt = substringBeforeLast('.')
    // Common patterns: "Artist - Title", "01 - Title", "01. Title"
    val dashSplit = nameWithoutExt.split(" - ", limit = 2)
    if (dashSplit.size == 2) {
        val first = dashSplit[0].trim()
        val second = dashSplit[1].trim()
        // If first part is a track number, treat second as title
        if (first.all { it.isDigit() }) {
            return Pair(second, "Unknown Artist")
        }
        return Pair(second, first)
    }
    // Fallback: entire filename is the title
    return Pair(
        nameWithoutExt.replace("_", " ").trim(),
        "Unknown Artist"
    )
}

/** Supported audio file extensions for library scanning. */
val AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "alac"
)

/** Check if a filename has a supported audio extension. */
fun String.isAudioFile(): Boolean {
    val ext = substringAfterLast('.', "").lowercase()
    return ext in AUDIO_EXTENSIONS
}
