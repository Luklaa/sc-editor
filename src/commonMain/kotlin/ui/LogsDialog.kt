package com.luklaaa.sceditor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.luklaaa.sceditor.AppLog
import kotlinx.coroutines.delay

/**
 * Окно с логами: обычный текст, который можно читать, выделять и копировать кнопкой.
 * Цвета берутся из текущей темы. [extra] вызывается внутри содержимого диалога (на Android сюда
 * ставится скрытие системных панелей).
 */
@Composable
fun LogsDialog(onDismiss: () -> Unit, extra: @Composable () -> Unit = {}) {
    val clipboard = LocalClipboardManager.current
    val revision = AppLog.revision
    val text = remember(revision) { AppLog.text().ifBlank { "(log is empty)" } }
    val scroll = rememberScrollState()
    var copied by remember { mutableStateOf(false) }

    // Новые записи появляются внизу - прокручиваем к ним.
    LaunchedEffect(text) {
        delay(50)
        scroll.scrollTo(scroll.maxValue)
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text("Logs") },
        text = {
            Column {
                extra()
                Column(modifier = Modifier.verticalScroll(scroll)) {
                    SelectionContainer {
                        Text(
                            text = text,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { AppLog.clear() }) { Text("Clear") }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(AppLog.text()))
                    copied = true
                }) { Text(if (copied) "Copied" else "Copy") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
