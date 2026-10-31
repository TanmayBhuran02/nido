package com.nido.music.feature.discover.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.nido.music.core.network.MusicSourceProvider
import com.nido.music.core.network.downloader.MusicDownloader
import com.nido.music.core.network.model.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicSourceProvider: MusicSourceProvider,
    private val musicDownloader: MusicDownloader
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _playingTrackId = MutableStateFlow<String?>(null)
    val playingTrackId: StateFlow<String?> = _playingTrackId.asStateFlow()

    private val _downloadingTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingTrackIds: StateFlow<Set<String>> = _downloadingTrackIds.asStateFlow()

    private val _downloadSuccessTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadSuccessTrackIds: StateFlow<Set<String>> = _downloadSuccessTrackIds.asStateFlow()

    private val previewPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build()
    }

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            musicSourceProvider.search(query)
                .onSuccess { list ->
                    _results.value = list
                }
                .onFailure {
                    _results.value = emptyList()
                }
            _isLoading.value = false
        }
    }

    fun downloadTrack(track: SearchResult) {
        viewModelScope.launch {
            _downloadingTrackIds.value = _downloadingTrackIds.value + track.id
            musicSourceProvider.getDownloadUrl(track.id)
                .onSuccess { url ->
                    musicDownloader.downloadSong(
                        downloadUrl = url,
                        title = track.title,
                        artist = track.artist
                    ).onSuccess {
                        _downloadSuccessTrackIds.value = _downloadSuccessTrackIds.value + track.id
                    }
                }
            _downloadingTrackIds.value = _downloadingTrackIds.value - track.id
        }
    }

    fun togglePreview(result: SearchResult) {
        if (_playingTrackId.value == result.id) {
            previewPlayer.stop()
            _playingTrackId.value = null
        } else {
            viewModelScope.launch {
                musicSourceProvider.getStreamUrl(result.id)
                    .onSuccess { url ->
                        _playingTrackId.value = result.id
                        previewPlayer.setMediaItem(MediaItem.fromUri(url))
                        previewPlayer.prepare()
                        previewPlayer.play()
                    }
            }
        }
    }

    override fun onCleared() {
        previewPlayer.release()
        super.onCleared()
    }
}

