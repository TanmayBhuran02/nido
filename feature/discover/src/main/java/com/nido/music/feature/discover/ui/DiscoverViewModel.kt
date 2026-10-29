package com.nido.music.feature.discover.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.nido.music.core.network.MusicSourceProvider
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
    private val musicSourceProvider: MusicSourceProvider
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _playingTrackId = MutableStateFlow<String?>(null)
    val playingTrackId: StateFlow<String?> = _playingTrackId.asStateFlow()

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

