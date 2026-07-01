package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight

@Composable
fun GlassFileTabBar(
    openedTabs: List<OpenedTab>,
    activeTabIndex: Int,
    onTabSelect: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier.fillMaxWidth().height(40.dp).horizontalScroll(scrollState),
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
}
