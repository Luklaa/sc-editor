package com.luklaaa.sceditor

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidFilePicker.init(this)
        setContent {
            var triggerOpenFile by remember { mutableStateOf(false) }
            var triggerCloseFile by remember { mutableStateOf(false) }
            var triggerCloseAllFiles by remember { mutableStateOf(false) }

            BackHandler { finish() }

            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFDFE7F5))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { triggerOpenFile = true }) { Text("Open") }
                    TextButton(onClick = { triggerCloseFile = true }) { Text("Close") }
                    TextButton(onClick = { triggerCloseAllFiles = true }) { Text("Close all") }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    App(
                        onTitleChanged = {},
                        onExit = { finish() },
                        triggerOpenFile = triggerOpenFile,
                        onOpenFileHandled = { triggerOpenFile = false },
                        triggerCloseFile = triggerCloseFile,
                        onCloseFileHandled = { triggerCloseFile = false },
                        triggerCloseAllFiles = triggerCloseAllFiles,
                        onCloseAllFilesHandled = { triggerCloseAllFiles = false }
                    )
                }
            }
        }
    }
}
