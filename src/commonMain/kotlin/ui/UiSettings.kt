package com.luklaaa.sceditor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class PanelSide { LEFT, RIGHT }

/** Состояние UI-настроек. Сохранение на диск добавь сам (SharedPreferences / файл). */
class UiSettings {
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var panelSide by mutableStateOf(PanelSide.LEFT)
    var panelExpanded by mutableStateOf(true)
}
