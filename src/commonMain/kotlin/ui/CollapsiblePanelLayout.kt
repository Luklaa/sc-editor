package com.luklaaa.sceditor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CollapsiblePanelLayout(
    settings: UiSettings,
    filesPanel: @Composable () -> Unit,
    preview: @Composable () -> Unit,
) {
    val side = settings.panelSide
    val expanded = settings.panelExpanded

    @Composable
    fun Panel() {
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            Box(Modifier.width(240.dp).fillMaxHeight()) { filesPanel() }
        }
    }

    @Composable
    fun Handle() {
        val icon = when {
            side == PanelSide.LEFT && expanded -> Icons.Default.KeyboardArrowUp
            side == PanelSide.LEFT -> Icons.Default.KeyboardArrowUp
            expanded -> Icons.Default.KeyboardArrowUp
            else -> Icons.Default.KeyboardArrowDown
        }
        Box(
            Modifier.fillMaxHeight().width(24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { settings.panelExpanded = !settings.panelExpanded },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = "Свернуть/развернуть панель файлов") }
    }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).cutoutPadding()) {
        if (side == PanelSide.LEFT) { Panel(); Handle() }
        Box(Modifier.weight(1f).fillMaxHeight()) { preview() }
        if (side == PanelSide.RIGHT) { Handle(); Panel() }
    }
}
