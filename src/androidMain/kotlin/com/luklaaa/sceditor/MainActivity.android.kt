package com.luklaaa.sceditor

import App
import android.content.Context
import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFDFE7F5))
                            .padding(horizontal = 4.dp),
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

    private fun enterImmersive() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    private companion object {
        const val KEY_MENU_ON_RIGHT = "menu_on_right"
    }
}
