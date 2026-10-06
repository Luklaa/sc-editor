package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

//@Composable
//fun TabsHandleButton(collapsed: Boolean, onRight: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
//    val pointsLeft = if (collapsed) onRight else !onRight
//    Box(
//        modifier = modifier
//            .size(40.dp)
//            .clip(CircleShape)
//            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
//            .clickable(onClick = onClick),
//        contentAlignment = Alignment.Center
//    ) {
//        Icon(
//            imageVector = if (pointsLeft) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
//            contentDescription = if (collapsed) "Show open files" else "Hide open files",
//            tint = MaterialTheme.colorScheme.onSurface
//        )
//    }
//}
