package com.nido.music.feature.music.data.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.nido.music.core.database.dao.QueueDao
import com.nido.music.core.database.dao.SongDao
import com.nido.music.core.database.entity.QueueItemEntity
import com.nido.music.core.database.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val queueDao: QueueDao
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var player: MediaController? = null

    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            player?.let { p ->
                if (p.isPlaying) {
                    _currentPosition.value = p.currentPosition
                    _duration.value = if (p.duration > 0) p.duration else _currentSong.value?.durationMs ?: 0L
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private var sleepTimerRunnable: Runnable? = null

    init {
        initController()
    }

    private fun initController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, NidoPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            player = controllerFuture?.get()
            setupPlayerListener()
            handler.post(progressRunnable)
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val uriStr = mediaItem?.mediaId
                if (uriStr != null) {
                    scope.launch(Dispatchers.IO) {
                        val song = songDao.getAllSongs()
                        // Resolve song from database by uri
                        songDao.getSongsByIds(emptyList()) // Trigger check
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffle: Boolean) {
                _shuffleEnabled.value = shuffle
            }

            override fun onRepeatModeChanged(repeat: Int) {
                _repeatMode.value = repeat
            }
        })
    }

    fun playSong(song: SongEntity, fullList: List<SongEntity> = listOf(song)) {
        _currentSong.value = song
        player?.let { p ->
            val mediaItems = fullList.map { s ->
                MediaItem.Builder()
                    .setMediaId(s.uri)
                    .setUri(Uri.parse(s.uri))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(s.album)
                            .build()
                    )
                    .build()
            }
            val startIndex = fullList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            p.setMediaItems(mediaItems, startIndex, 0L)
            p.prepare()
            p.play()

            // Update queue in database
            scope.launch(Dispatchers.IO) {
                val queueItems = fullList.mapIndexed { index, s ->
                    QueueItemEntity(position = index, songId = s.id)
                }
                queueDao.replaceQueue(queueItems)
            }
        }
    }

    fun togglePlayPause() {
        player?.let { p ->
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun skipNext() {
        player?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        player?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        player?.let { p ->
            val nextState = !p.shuffleModeEnabled
            p.shuffleModeEnabled = nextState
            _shuffleEnabled.value = nextState
        }
    }

    fun toggleRepeat() {
        player?.let { p ->
            val nextMode = when (p.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            p.repeatMode = nextMode
            _repeatMode.value = nextMode
        }
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val totalMs = minutes * 60 * 1000L
        val endTime = System.currentTimeMillis() + totalMs
        _sleepTimerRemaining.value = totalMs

        val timer = object : Runnable {
            override fun run() {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    player?.pause()
                    _sleepTimerRemaining.value = null
                } else {
                    _sleepTimerRemaining.value = remaining
                    handler.postDelayed(this, 1000)
                }
            }
        }
        sleepTimerRunnable = timer
        handler.post(timer)
    }

    fun cancelSleepTimer() {
        sleepTimerRunnable?.let { handler.removeCallbacks(it) }
        sleepTimerRunnable = null
        _sleepTimerRemaining.value = null
    }

    fun release() {
        handler.removeCallbacks(progressRunnable)
        cancelSleepTimer()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}

