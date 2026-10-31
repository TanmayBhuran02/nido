package com.nido.music.feature.music.data.dsp

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerManager @Inject constructor() {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _presets = MutableStateFlow<List<String>>(emptyList())
    val presets: StateFlow<List<String>> = _presets.asStateFlow()

    private val _currentPreset = MutableStateFlow<String>("Flat")
    val currentPreset: StateFlow<String> = _currentPreset.asStateFlow()

    private val _bandLevels = MutableStateFlow<Map<Int, Short>>(emptyMap())
    val bandLevels: StateFlow<Map<Int, Short>> = _bandLevels.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow<Short>(0)
    val bassBoostStrength: StateFlow<Short> = _bassBoostStrength.asStateFlow()

    fun bindAudioSession(audioSessionId: Int) {
        if (audioSessionId == 0) return
        try {
            release()
            val eq = Equalizer(0, audioSessionId).apply {
                enabled = _isEnabled.value
            }
            val bb = BassBoost(0, audioSessionId).apply {
                enabled = _isEnabled.value
            }

            equalizer = eq
            bassBoost = bb

            val numPresets = eq.numberOfPresets.toInt()
            val presetList = if (numPresets > 0) (0 until numPresets).map { eq.getPresetName(it.toShort()) } else emptyList()
            _presets.value = presetList

            // Restore previous band levels or read current defaults
            if (_bandLevels.value.isNotEmpty()) {
                val range = eq.bandLevelRange
                val minLevel = range?.getOrNull(0) ?: Short.MIN_VALUE
                val maxLevel = range?.getOrNull(1) ?: Short.MAX_VALUE
                _bandLevels.value.forEach { (band, level) ->
                    if (band in 0 until eq.numberOfBands && level in minLevel..maxLevel) {
                        eq.setBandLevel(band.toShort(), level)
                    }
                }
            } else {
                val map = mutableMapOf<Int, Short>()
                for (b in 0 until eq.numberOfBands) {
                    map[b] = eq.getBandLevel(b.toShort())
                }
                _bandLevels.value = map
            }

            // Restore previous bass boost strength
            if (_bassBoostStrength.value > 0) {
                bb.setStrength(_bassBoostStrength.value)
            }
        } catch (e: Exception) {
            // AudioFx might not be supported on all emulators/devices; release and clear both instances
            release()
        }
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
        } catch (e: Exception) {}
    }

    fun setPreset(presetIndex: Short, presetName: String) {
        _currentPreset.value = presetName
        try {
            equalizer?.usePreset(presetIndex)
            equalizer?.let { eq ->
                val map = mutableMapOf<Int, Short>()
                for (b in 0 until eq.numberOfBands) {
                    map[b] = eq.getBandLevel(b.toShort())
                }
                _bandLevels.value = map
            }
        } catch (e: Exception) {}
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
            _bandLevels.value = _bandLevels.value.toMutableMap().apply {
                put(band.toInt(), level)
            }
        } catch (e: Exception) {}
    }

    fun setBassBoost(strength: Short) {
        _bassBoostStrength.value = strength
        try {
            bassBoost?.setStrength(strength)
        } catch (e: Exception) {}
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {}
        try {
            bassBoost?.release()
        } catch (e: Exception) {}
        equalizer = null
        bassBoost = null
    }
}
