package com.luklaaa.sceditor

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidFilePicker.init(this)
        setContent {
            var triggerOpenFile by remember { mutableStateOf(false) }
            var triggerCloseFile by remember { mutableStateOf(false) }
            var triggerCloseAllFiles by remember { mutableStateOf(false) }

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