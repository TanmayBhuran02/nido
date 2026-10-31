package com.nido.music.feature.music.data.lyrics

import java.util.regex.Pattern

object LrcParser {

    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\]")

    /**
     * Parses standard LRC formatted lyrics into a chronologically sorted list of [LyricsLine].
     * Supports multiple timestamps per line (e.g. `[00:10.00][00:20.00] Repeated line`).
     */
    fun parse(lrcContent: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()

        lrcContent.lineSequence().forEach { line ->
            val matcher = TIME_TAG_PATTERN.matcher(line)
            val timestamps = mutableListOf<Long>()
            var lastEnd = 0

            while (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val fractionStr = matcher.group(3)
                val fractionMs = when (fractionStr?.length) {
                    2 -> fractionStr.toLong() * 10
                    3 -> fractionStr.toLong()
                    else -> 0L
                }
                val timestampMs = (min * 60 + sec) * 1000 + fractionMs
                timestamps.add(timestampMs)
                lastEnd = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val text = line.substring(lastEnd).trim()
                if (text.isNotEmpty()) {
                    timestamps.forEach { ts ->
                        lines.add(LyricsLine(ts, text))
                    }
                }
            }
        }

        return lines.sortedBy { it.timestampMs }
    }
}
