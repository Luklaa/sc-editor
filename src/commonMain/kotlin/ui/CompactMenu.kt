package ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Кнопка-«бургер», открывающая выезжающее меню (Objects / Textures / Info). */
@Composable
fun BurgerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.85f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFF1E293B))
    }
}

/**
 * Выезжающая панель поверх вьюпорта. Должна лежать в Box, который занимает весь экран.
 *
 * Содержимое всегда остаётся в композиции (просто за краем экрана), а анимация идёт только
 * через graphicsLayer. Раньше панель создавалась заново при каждом открытии
 * (AnimatedVisibility), и тяжёлая композиция списков с blur давала задержку после нажатия.
 */
@Composable
fun BoxScope.CompactMenuDrawer(
    onRight: Boolean,
    open: Boolean,
    onDismiss: () -> Unit,
    drawerWidth: Dp,
    content: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "drawerProgress"
    )
    val widthPx = with(LocalDensity.current) { drawerWidth.toPx() }

    // Затемнение существует только пока меню открыто или закрывается.
    if (progress > 0f) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }

    // Край, прижатый к краю экрана, остаётся прямым; скругляется только внутренний.
    val shape = if (onRight)
        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
    else
        RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)

    Box(
        Modifier
            .align(if (onRight) Alignment.CenterEnd else Alignment.CenterStart)
            .width(drawerWidth)
            .fillMaxHeight()
            .graphicsLayer { translationX = (1f - progress) * widthPx * (if (onRight) 1f else -1f) }
            .clip(shape)
            // Плотная подложка вместо полупрозрачного стекла: текст читается поверх затемнения.
            .background(Color(0xFFF1F5F9))
    ) {
        CompositionLocalProvider(LocalGlassBlur provides false) {
            content()
        }
    }
}
