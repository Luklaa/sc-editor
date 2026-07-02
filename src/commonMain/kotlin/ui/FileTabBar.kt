package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Тонкий горизонтальный скроллбар-индикатор под рядом вкладок. Не показывается, если
// все вкладки помещаются целиком. Бегунок можно перетаскивать курсором — реальная зона
// захвата выше (10dp), чем видимая полоска (3dp), по аналогии с VerticalListScrollbar
// в сайдбаре.
@Composable
private fun HorizontalTabScrollbar(scrollState: androidx.compose.foundation.ScrollState, viewportWidthPx: Int) {
    if (scrollState.maxValue <= 0 || viewportWidthPx <= 0) return

    val totalWidthPx = scrollState.maxValue + viewportWidthPx
    val thumbFraction = (viewportWidthPx.toFloat() / totalWidthPx.toFloat()).coerceIn(0.08f, 1f)
    val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()

    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp, start = 2.dp, end = 2.dp)
            .height(10.dp)
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()
        val trackWidth = maxWidth
        val thumbWidth = trackWidth * thumbFraction
        val thumbOffset = (trackWidth - thumbWidth) * progress
        val thumbWidthPx = trackWidthPx * thumbFraction
        val draggableRangePx = (trackWidthPx - thumbWidthPx).coerceAtLeast(1f)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.05f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .width(thumbWidth)
                .fillMaxHeight()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.22f))
                .pointerInput(scrollState.maxValue, draggableRangePx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaScroll = (dragAmount.x / draggableRangePx) * scrollState.maxValue
                        val newValue = (scrollState.value + deltaScroll).roundToInt().coerceIn(0, scrollState.maxValue)
                        coroutineScope.launch { scrollState.scrollTo(newValue) }
                    }
                }
        )
    }
}

@Composable
fun GlassFileTabBar(
    openedTabs: List<OpenedTab>,
    activeTabIndex: Int,
    onTabSelect: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var viewportWidthPx by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .onSizeChanged { viewportWidthPx = it.width }
                // Мышиное колесо на десктопе даёт вертикальную дельту скролла (scrollDelta.y),
                // а этот ряд скроллится горизонтально — .horizontalScroll() сам такое не ловит
                // (он реагирует только на scrollDelta.x, т.е. Shift+колесо/трекпад). Ловим
                // вертикальную дельту сами и прокручиваем ScrollState вручную.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val change = event.changes.firstOrNull() ?: continue
                                val delta = change.scrollDelta
                                val amount = if (delta.x != 0f) delta.x else delta.y
                                if (amount != 0f) {
                                    change.consume()
                                    coroutineScope.launch { scrollState.scrollBy(amount * 50f) }
                                }
                            }
                        }
                    }
                }
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            openedTabs.forEachIndexed { index, tab ->
                val isActive = index == activeTabIndex
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .width(IntrinsicSize.Min)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFFE0F2FE).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.2f))
                        .border(1.dp, if (isActive) Color(0xFFBAE6FD) else Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable { onTabSelect(index) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tab.name,
                            modifier = Modifier.offset(y = (-3).dp),
                            color = Color(0xFF1E293B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.35f))
                                .clickable { onTabClose(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalTabScrollbar(scrollState, viewportWidthPx)
    }
}