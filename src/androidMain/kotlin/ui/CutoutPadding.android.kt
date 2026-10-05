package com.luklaaa.sceditor.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

actual fun Modifier.cutoutPadding(): Modifier = composed {
    Modifier.windowInsetsPadding(WindowInsets.displayCutout)
}