package com.nido.music.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long,
    val coverUrl: String? = null,
    /** License identifier, e.g. "CC-BY", "CC-BY-SA", "CC-BY-NC". */
    val license: String? = null,
    /** Provider name for attribution. */
    val provider: String
)

@Serializable
data class TrackDetails(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long,
    val coverUrl: String? = null,
    val license: String? = null,
    val provider: String,
    val downloadUrl: String? = null,
    val streamUrl: String? = null,
    /** Genre tags if available. */
    val tags: List<String> = emptyList()
)
