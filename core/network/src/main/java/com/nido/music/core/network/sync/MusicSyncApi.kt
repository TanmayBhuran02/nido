package com.nido.music.core.network.sync

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable
data class SyncedTrackRequest(
    val track_id: String,
    val title: String,
    val artist: String,
    val duration_ms: Long
)

@Serializable
data class SyncedTrackResponse(
    val id: String,
    val track_id: String,
    val title: String,
    val artist: String,
    val duration_ms: Long,
    val downloaded_at: String
)

interface MusicSyncApi {
    @POST("/api/v1/sync/library")
    suspend fun syncDownloadedTrack(@Body track: SyncedTrackRequest): SyncedTrackResponse

    @GET("/api/v1/sync/library")
    suspend fun getSyncedLibrary(): List<SyncedTrackResponse>
}
