package com.nido.music.core.network

import com.nido.music.core.network.model.SearchResult
import com.nido.music.core.network.model.TrackDetails

/**
 * Pluggable interface for online music sources.
 *
 * Implementations must use **licensed / openly-licensed catalogs** only
 * (e.g., Jamendo, Internet Archive, Audius).
 *
 * To add a new provider:
 * 1. Create a class implementing this interface
 * 2. Register it in Hilt using @IntoSet multibinding
 * 3. It will automatically appear in the Discover screen's provider picker
 *
 * Stage 2 note: The "Find full song" feature in chat clip bubbles
 * will also search through registered providers.
 */
interface MusicSourceProvider {

    /** Human-readable name shown in the provider picker. */
    val name: String

    /** Icon resource name or vector description. */
    val iconDescription: String

    /**
     * Search for tracks matching the query.
     * @param query Search terms
     * @param page Zero-indexed page number for pagination
     * @return List of search results, empty if no results
     */
    suspend fun search(query: String, page: Int = 0): Result<List<SearchResult>>

    /**
     * Get a streamable URL for 30-second preview.
     * @param trackId Provider-specific track identifier
     */
    suspend fun getStreamUrl(trackId: String): Result<String>

    /**
     * Get a downloadable URL for the full track.
     * @param trackId Provider-specific track identifier
     */
    suspend fun getDownloadUrl(trackId: String): Result<String>

    /**
     * Get full track details including tags and cover art URL.
     * @param trackId Provider-specific track identifier
     */
    suspend fun getTrackDetails(trackId: String): Result<TrackDetails>
}
