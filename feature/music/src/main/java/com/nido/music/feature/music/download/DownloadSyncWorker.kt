package com.nido.music.feature.music.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.nido.music.core.network.sync.MusicSyncApi
import com.nido.music.core.network.sync.SyncedTrackRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DownloadSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncApi: MusicSyncApi
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val artist = inputData.getString(KEY_ARTIST) ?: return Result.failure()
        val durationMs = inputData.getLong(KEY_DURATION, 0L)

        return try {
            syncApi.syncDownloadedTrack(
                SyncedTrackRequest(
                    track_id = trackId,
                    title = title,
                    artist = artist,
                    duration_ms = durationMs
                )
            )
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_TRACK_ID = "track_id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_DURATION = "duration"

        fun scheduleSync(
            context: Context,
            trackId: String,
            title: String,
            artist: String,
            durationMs: Long
        ) {
            val data = workDataOf(
                KEY_TRACK_ID to trackId,
                KEY_TITLE to title,
                KEY_ARTIST to artist,
                KEY_DURATION to durationMs
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<DownloadSyncWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
