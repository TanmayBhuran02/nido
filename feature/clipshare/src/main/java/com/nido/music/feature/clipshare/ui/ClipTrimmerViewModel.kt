package com.nido.music.feature.clipshare.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.nido.music.core.database.dao.SongDao
import com.nido.music.core.database.entity.SongEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ClipTrimmerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao
) : ViewModel() {

    private val _song = MutableStateFlow<SongEntity?>(null)
    val song: StateFlow<SongEntity?> = _song.asStateFlow()

    private val _startMs = MutableStateFlow(0L)
    val startMs: StateFlow<Long> = _startMs.asStateFlow()

    private val _endMs = MutableStateFlow(30000L)
    val endMs: StateFlow<Long> = _endMs.asStateFlow()

    private val _isPlayingPreview = MutableStateFlow(false)
    val isPlayingPreview: StateFlow<Boolean> = _isPlayingPreview.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportSuccess = MutableStateFlow<String?>(null)
    val exportSuccess: StateFlow<String?> = _exportSuccess.asStateFlow()

    private val previewPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build()
    }

    fun loadSong(songId: Long) {
        viewModelScope.launch {
            val item = songDao.getSongById(songId)
            _song.value = item
            item?.let {
                val maxEnd = minOf(it.durationMs, 30000L).coerceAtLeast(1000L)
                _startMs.value = 0L
                _endMs.value = maxEnd
            }
        }
    }

    fun updateRange(start: Long, end: Long) {
        _startMs.value = start
        _endMs.value = end
    }

    fun togglePreview() {
        val s = _song.value ?: return
        if (_isPlayingPreview.value) {
            previewPlayer.pause()
            _isPlayingPreview.value = false
        } else {
            val mediaItem = MediaItem.Builder()
                .setUri(s.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(_startMs.value)
                        .setEndPositionMs(_endMs.value)
                        .build()
                )
                .build()
            previewPlayer.setMediaItem(mediaItem)
            previewPlayer.prepare()
            previewPlayer.play()
            _isPlayingPreview.value = true
        }
    }

    fun exportClip() {
        val s = _song.value ?: return
        _isExporting.value = true

        val outputDir = File(context.filesDir, "clips").apply { mkdirs() }
        val outputFile = File(outputDir, "clip_${System.currentTimeMillis()}.m4a")

        val mediaItem = MediaItem.Builder()
            .setUri(s.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(_startMs.value)
                    .setEndPositionMs(_endMs.value)
                    .build()
            )
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)  // audio-only → WhatsApp-compatible
            .build()

        val transformer = Transformer.Builder(context)
            .setAudioMimeType("audio/mp4a-latm") // Force AAC codec
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    _isExporting.value = false
                    _exportSuccess.value = outputFile.absolutePath
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    _isExporting.value = false
                }
            })
            .build()

        transformer.start(editedMediaItem, outputFile.absolutePath)
    }

    override fun onCleared() {
        previewPlayer.release()
        super.onCleared()
    }
}

