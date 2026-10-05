package com.luklaaa.sceditor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Системный акцентный цвет (Android 12+ — Material You, иначе запасной). */
@Composable
expect fun systemAccentColor(dark: Boolean): Color
