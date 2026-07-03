package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class ViewportCameraState {
    var zoom by mutableStateOf(1f)
    var panX by mutableStateOf(0f)
    var panY by mutableStateOf(0f)
}

@Composable
fun rememberViewportCameraState(key: Any?): ViewportCameraState =
    remember(key) { ViewportCameraState() }
