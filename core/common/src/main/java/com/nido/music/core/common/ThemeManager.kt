package com.nido.music.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val title: String) {
    SYSTEM("System Default"),
    DARK("Liquid Glass (Dark)"),
    LIGHT("Liquid Glass (Light)"),
    OLED("OLED Black"),
    EMERALD("Emerald Aurora"),
    CYBERPUNK("Cyberpunk Neon"),
    SUNSET("Sunset Velvet"),
    AMETHYST("Royal Amethyst");

    companion object {
        fun fromTitle(title: String): ThemeMode =
            entries.firstOrNull { it.title.equals(title, ignoreCase = true) } ?: SYSTEM
    }
}

@Singleton
class ThemeManager @Inject constructor() {
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun setThemeMode(title: String) {
        _themeMode.value = ThemeMode.fromTitle(title)
    }
}
