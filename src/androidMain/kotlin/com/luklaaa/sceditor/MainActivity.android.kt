package com.luklaaa.sceditor

import App
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.ViewCompat
import kotlin.math.max
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupFullscreenWindow()
        enterImmersive()
        AndroidFilePicker.init(this)
        setContent {
            var triggerOpenFile by remember { mutableStateOf(false) }

            var menuOpen by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }
            var menuOnRight by remember { mutableStateOf(prefs.getBoolean(KEY_MENU_ON_RIGHT, false)) }

            // Назад: сначала закрываем настройки / бургер-меню, потом выходим.
            BackHandler {
                when {
                    showSettings -> showSettings = false
                    menuOpen -> menuOpen = false
                    else -> finish()
                }
            }

            // Отступы по бокам под вырез камеры (в горизонтали вырез слева/справа).
            val cutoutInsets = Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))

            App(
                onTitleChanged = {},
                onExit = { finish() },
                triggerOpenFile = triggerOpenFile,
                onOpenFileHandled = { triggerOpenFile = false },
                triggerCloseFile = false,
                onCloseFileHandled = {},
                triggerCloseAllFiles = false,
                onCloseAllFilesHandled = {},
                compactLayout = true,
                menuOnRight = menuOnRight,
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                contentInsets = cutoutInsets,
                topBar = {
                    val shift = rememberCutoutIconShift(barHeight = 48.dp, iconZone = 60.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFDFE7F5))
                            // высота панели не меняется, иконки сдвигаются по горизонтали от выреза
                            .padding(start = 4.dp + shift.first, end = 4.dp + shift.second),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { triggerOpenFile = true }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = "Open file", tint = Color(0xFF1E293B))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color(0xFF1E293B))
                        }
                    }
                }
            )

            if (showSettings) {
                AlertDialog(
                    onDismissRequest = { showSettings = false },
                    title = { Text("Settings") },
                    text = {
                        HideSystemBarsInDialog()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Burger menu on the right", modifier = Modifier.weight(1f))
                            Switch(
                                checked = menuOnRight,
                                onCheckedChange = { checked ->
                                    menuOnRight = checked
                                    prefs.edit().putBoolean(KEY_MENU_ON_RIGHT, checked).apply()
                                }
                            )
                        }
                    },
                    confirmButton = { TextButton(onClick = { showSettings = false }) { Text("OK") } }
                )
            }
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onResume() {
        super.onResume()
        enterImmersive()
    }

    // configChanges в манифесте перехватывает поворот, поэтому Activity не пересоздаётся
    // и бары нужно скрыть повторно вручную (post - после того, как система применит новую ориентацию).
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enterImmersive()
        window.decorView.post { enterImmersive() }
    }

    private fun setupFullscreenWindow() {
        window.statusBarColor = 0
        window.navigationBarColor = 0
        if (Build.VERSION.SDK_INT >= 29) {
            // Без этого на жестовой навигации система рисует полупрозрачную плашку под жестовой полосой.
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        // Рисуем под вырезом камеры в любой ориентации (ALWAYS - с Android 11, SHORT_EDGES - с Android 9).
        val cutoutMode = when {
            Build.VERSION.SDK_INT >= 30 -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            Build.VERSION.SDK_INT >= 28 -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            else -> null
        }
        if (cutoutMode != null) {
            window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = cutoutMode }
        }
    }

    private fun enterImmersive() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    private companion object {
        const val KEY_MENU_ON_RIGHT = "menu_on_right"
    }
}

/** Диалог - отдельное окно, и при его показе системные бары появляются заново. Прячем их и там. */
@Composable
private fun HideSystemBarsInDialog() {
    val view = LocalView.current
    SideEffect {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
        WindowInsetsControllerCompat(dialogWindow, dialogWindow.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * Насколько сдвинуть левую/правую иконки верхней панели, чтобы они не попали под вырез камеры.
 * Берём реальные прямоугольники выреза: если вырез задевает полосу панели (barHeight) в зоне иконки
 * (iconZone от края), иконка сдвигается за его границу. Центральная «чёлка» иконки не трогает.
 * Работает в любой ориентации: в ландшафте вырез слева/справа - сдвиг идёт по тому же правилу.
 */
@Composable
private fun rememberCutoutIconShift(barHeight: Dp, iconZone: Dp): Pair<Dp, Dp> {
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val orientation = LocalConfiguration.current.orientation
    val cutout = WindowInsets.displayCutout
    // Ключ: при смене выреза/ориентации пересчитываем (чтение инсетов подписывает на их изменение).
    val key = listOf(
        cutout.getLeft(density, layoutDirection), cutout.getTop(density),
        cutout.getRight(density, layoutDirection), cutout.getBottom(density), orientation
    )
    return remember(key) {
        val rects = ViewCompat.getRootWindowInsets(view)?.displayCutout?.boundingRects.orEmpty()
        val width = view.width
        val bandPx = with(density) { barHeight.roundToPx() }
        val zonePx = with(density) { iconZone.roundToPx() }
        var startPx = 0
        var endPx = 0
        for (r in rects) {
            if (r.top >= bandPx || r.bottom <= 0) continue // вырез не заходит на полосу панели
            if (r.left < zonePx) startPx = max(startPx, r.right)
            if (width > 0 && r.right > width - zonePx) endPx = max(endPx, width - r.left)
        }
        with(density) { startPx.toDp() to endPx.toDp() }
    }
}
