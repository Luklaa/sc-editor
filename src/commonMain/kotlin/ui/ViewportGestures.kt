package ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

private const val MIN_ZOOM = 0.1f
private const val MAX_ZOOM = 20f
private const val DOUBLE_TAP_TIMEOUT_MS = 300L
private const val DOUBLE_TAP_SLOP_DP = 48f

fun Modifier.viewportTouchGestures(camera: ViewportCameraState): Modifier = pointerInput(camera) {
    var lastTapUptime = 0L
    var lastTapPosition = Offset.Zero
    val doubleTapSlopPx = DOUBLE_TAP_SLOP_DP * density

    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        var maxPointers = 0
        val touchSlop = viewConfiguration.touchSlop

        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.type != PointerType.Touch) return@awaitEachGesture

        var lastUptime = down.uptimeMillis
        var canceled = false
        do {
            val event = awaitPointerEvent()
            canceled = event.changes.any { it.isConsumed }
            maxPointers = maxOf(maxPointers, event.changes.count { it.pressed })
            lastUptime = event.changes.firstOrNull()?.uptimeMillis ?: lastUptime

            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1f - zoom) * centroidSize
                    val panMotion = pan.getDistance()
                    if (zoomMotion > touchSlop || panMotion > touchSlop) pastTouchSlop = true
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        val newZoom = (camera.zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        val ratio = newZoom / camera.zoom
                        val center = Offset(size.width / 2f, size.height / 2f)
                        camera.panX = (centroid.x - center.x) * (1f - ratio) + ratio * camera.panX + panChange.x
                        camera.panY = (centroid.y - center.y) * (1f - ratio) + ratio * camera.panY + panChange.y
                        camera.zoom = newZoom
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })

        // Двойной тап (один палец, без движения): сбрасываем камеру.
        val isTap = !canceled && !pastTouchSlop && maxPointers == 1 && lastUptime - down.uptimeMillis < DOUBLE_TAP_TIMEOUT_MS
        if (isTap) {
            val isDouble = lastTapUptime != 0L &&
                down.uptimeMillis - lastTapUptime < DOUBLE_TAP_TIMEOUT_MS &&
                (down.position - lastTapPosition).getDistance() < doubleTapSlopPx
            if (isDouble) {
                camera.zoom = 1f
                camera.panX = 0f
                camera.panY = 0f
                lastTapUptime = 0L
            } else {
                lastTapUptime = lastUptime
                lastTapPosition = down.position
            }
        } else {
            lastTapUptime = 0L
        }
    }
}
