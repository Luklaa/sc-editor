package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class ToastMessage(val id: Long, val text: String, val persistent: Boolean)

/** Простые тосты для общего кода (Compose Multiplatform не имеет своего Toast). */
@Stable
class ToastState {
    var current by mutableStateOf<ToastMessage?>(null)
        private set
    private var counter = 0L

    /** [persistent] = true: тост висит, пока его не заменят или не вызовут [dismiss] (например, "Opening..."). */
    fun show(text: String, persistent: Boolean = false) {
        current = ToastMessage(++counter, text, persistent)
    }

    fun dismiss() {
        current = null
    }
}

@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val message = state.current
    LaunchedEffect(message?.id) {
        if (message != null && !message.persistent) {
            delay(3500)
            if (state.current?.id == message.id) state.dismiss()
        }
    }
    if (message != null) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(text = message.text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}
