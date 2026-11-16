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
import com.nido.music.core.common.UserPreferencesRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val queueDao: QueueDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var player: MediaController? = null
    private var pendingPlayRequest: (() -> Unit)? = null

    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<SongEntity>>(emptyList())
    val currentQueue: StateFlow<List<SongEntity>> = _currentQueue.asStateFlow()

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

    private var progressTick = 0
    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            player?.let { p ->
                if (p.isPlaying) {
                    val pos = p.currentPosition
                    val dur = if (p.duration > 0) p.duration else _currentSong.value?.durationMs ?: 0L
                    _currentPosition.value = pos
                    _duration.value = dur
                    if (++progressTick % 6 == 0) {
                        scope.launch(Dispatchers.IO) {
                            userPreferencesRepository.savePosition(pos)
                        }
                    }
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private var sleepTimerRunnable: Runnable? = null

    init {
        restoreLastSession()
        initController()
    }

    private fun restoreLastSession() {
        scope.launch(Dispatchers.IO) {
            try {
                val lastUri = userPreferencesRepository.lastSongUriFlow.first()
                val lastPos = userPreferencesRepository.lastPositionMsFlow.first()
                val lastShuffle = userPreferencesRepository.lastShuffleEnabledFlow.first()
                val lastRepeat = userPreferencesRepository.lastRepeatModeFlow.first()
                _shuffleEnabled.value = lastShuffle
                _repeatMode.value = lastRepeat

                val savedQueue = queueDao.getQueueSongs().first()
                val song = if (!lastUri.isNullOrBlank()) {
                    savedQueue.find { it.uri == lastUri } ?: songDao.getSongByUri(lastUri)
                } else {
                    savedQueue.firstOrNull()
                }

                if (song != null) {
                    val queue = if (savedQueue.isNotEmpty()) savedQueue else listOf(song)
                    _currentSong.value = song
                    _currentQueue.value = queue
                    _currentPosition.value = lastPos
                    _duration.value = song.durationMs
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackController", "Failed to restore playback session", e)
            }
        }
    }

    private fun applySessionToPlayer() {
        val p = player ?: return
        val song = _currentSong.value ?: return
        val queue = _currentQueue.value.ifEmpty { listOf(song) }

        p.shuffleModeEnabled = _shuffleEnabled.value
        p.repeatMode = _repeatMode.value

        if (p.mediaItemCount == 0) {
            val mediaItems = queue.map { s ->
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
            val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            p.setMediaItems(mediaItems, startIndex, _currentPosition.value)
            p.prepare()
        }
    }

    private fun initController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, NidoPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            handler.post {
                try {
                    player = controllerFuture?.get()
                    setupPlayerListener()
                    applySessionToPlayer()
                    handler.post(progressRunnable)
                    pendingPlayRequest?.invoke()
                    pendingPlayRequest = null
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackController", "Failed to connect to MediaController", e)
                }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player?.duration?.coerceAtLeast(0L) ?: _currentSong.value?.durationMs ?: 0L
                } else if (playbackState == Player.STATE_ENDED) {
                    _isPlaying.value = false
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("PlaybackController", "Playback error: ${error.errorCodeName}", error)
                _isPlaying.value = false
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val uriStr = mediaItem?.mediaId
                if (uriStr != null) {
                    // Fast sync with current queue first
                    val matchInQueue = _currentQueue.value.find { it.uri == uriStr }
                    if (matchInQueue != null) {
                        _currentSong.value = matchInQueue
                    }
                    scope.launch(Dispatchers.IO) {
                        val dbSong = songDao.getSongByUri(uriStr)
                        if (dbSong != null) {
                            _currentSong.value = dbSong
                        }
                        userPreferencesRepository.savePlaybackState(
                            songUri = uriStr,
                            positionMs = 0L,
                            shuffleEnabled = _shuffleEnabled.value,
                            repeatMode = _repeatMode.value
                        )
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffle: Boolean) {
                _shuffleEnabled.value = shuffle
                scope.launch(Dispatchers.IO) {
                    userPreferencesRepository.saveShuffleEnabled(shuffle)
                }
            }

            override fun onRepeatModeChanged(repeat: Int) {
                _repeatMode.value = repeat
                scope.launch(Dispatchers.IO) {
                    userPreferencesRepository.saveRepeatMode(repeat)
                }
            }
        })
    }

    fun playSong(song: SongEntity, fullList: List<SongEntity> = listOf(song)) {
        _currentSong.value = song
        _currentQueue.value = fullList
        val p = player
        if (p == null) {
            pendingPlayRequest = { playSong(song, fullList) }
            return
        }
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

        // Update queue in database & save state
        scope.launch(Dispatchers.IO) {
            userPreferencesRepository.savePlaybackState(
                songUri = song.uri,
                positionMs = 0L,
                shuffleEnabled = _shuffleEnabled.value,
                repeatMode = _repeatMode.value
            )
            val queueItems = fullList.mapIndexed { index, s ->
                QueueItemEntity(position = index, songId = s.id)
            }
            queueDao.replaceQueue(queueItems)
        }
    }

    fun shufflePlay(songs: List<SongEntity>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        _shuffleEnabled.value = true
        player?.shuffleModeEnabled = true
        scope.launch(Dispatchers.IO) {
            userPreferencesRepository.saveShuffleEnabled(true)
        }
        playSong(shuffled[startIndex.coerceIn(0, shuffled.lastIndex)], shuffled)
    }

    fun togglePlayPause() {
        player?.let { p ->
            if (p.isPlaying) {
                p.pause()
            } else {
                if (p.mediaItemCount == 0 && _currentSong.value != null) {
                    _currentSong.value?.let { playSong(it, _currentQueue.value.ifEmpty { listOf(it) }) }
                } else {
                    p.play()
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _currentPosition.value = positionMs
        scope.launch(Dispatchers.IO) {
            userPreferencesRepository.savePosition(positionMs)
        }
    }

    fun skipNext() {
        player?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        player?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        val nextState = !_shuffleEnabled.value
        player?.shuffleModeEnabled = nextState
        _shuffleEnabled.value = nextState
        scope.launch(Dispatchers.IO) {
            userPreferencesRepository.saveShuffleEnabled(nextState)
        }
    }

    fun toggleRepeat() {
        val current = _repeatMode.value
        val nextMode = when (current) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player?.repeatMode = nextMode
        _repeatMode.value = nextMode
        scope.launch(Dispatchers.IO) {
            userPreferencesRepository.saveRepeatMode(nextMode)
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

