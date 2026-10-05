package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * Затемнённая область вокруг панели закрывает меню по нажатию.
 */
@Composable
fun BoxScope.CompactMenuDrawer(
    onRight: Boolean,
    open: Boolean,
    onDismiss: () -> Unit,
    drawerWidth: Dp,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }

    AnimatedVisibility(
        visible = open,
        enter = slideInHorizontally { full -> if (onRight) full else -full },
        exit = slideOutHorizontally { full -> if (onRight) full else -full },
        modifier = Modifier.align(if (onRight) Alignment.CenterEnd else Alignment.CenterStart)
    ) {
        // Край, прижатый к краю экрана, остаётся прямым; скругляется только внутренний.
        val shape = if (onRight)
            RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
        else
            RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
        Box(
            Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .clip(shape)
                // Сайдбар полупрозрачный (стекло), поверх затемнённого вьюпорта
                // нужна плотная подложка, иначе текст плохо читается.
                .background(Color(0xFFF1F5F9))
        ) {
            content()
        }
    }
}
