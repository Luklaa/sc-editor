package com.luklaaa.sceditor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
class AppColors(
    val accent: Color,
    val selected: Color,
    val selectedBorder: Color,
    val onSelected: Color,
    val boardLight: Color,
    val boardDark: Color,
)

val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("AppColors not provided")
}

object AppThemeColors {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

@Composable
fun AppTheme(mode: ThemeMode, accentOverride: Color? = null, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = accentOverride ?: systemAccentColor(dark)
    val scheme = if (dark) darkColorScheme(
        primary = accent,
        secondary = Color(0xFF16161A),
        background = Color(0xFF1B1B21),
        errorContainer = Color(0xFF656565), // checkboard white
        error = Color(0xFF424242), // checkboard grey
        surface = Color(0xFF272731),
        surfaceVariant = Color(0xFF232328),
        onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFE2E8F0),
        onSurfaceVariant = Color(0xFFCBD5E1),
        outline = Color(0xFF94A3B8), //
        outlineVariant = Color(0xFF64748B), // font in menu
        primaryContainer = Color(0xA8095B72),
        secondaryContainer = Color(0xFF075985)
    ) else lightColorScheme(
        primary = accent,
        secondary = Color(0xFFDFE7F5), // top
        background = Color(0xFFF8FAFC),
        errorContainer = Color(0xFFE8E8E8), // checkboard white
        error = Color(0xFFC7C7C7), // checkboard grey
        surface = Color.White,
        surfaceVariant = Color(0xFFE2E8F0),
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF1E293B),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFF64748B),
        outlineVariant = Color(0xFF94A3B8), // font in menu
        primaryContainer = Color(0xFFE0F2FE),
        secondaryContainer = Color(0xFFBAE6FD)
    )
    val appColors = if (dark) AppColors(
        accent = accent,
        selected = Color(0xFF293644), // selected object
        selectedBorder = Color(0xFF2F5168), // selected object border
        onSelected = Color(0xffffffff), // text (not using)
        boardLight = Color(0xFF656565), // checkboard white
        boardDark = Color(0xFF424242), // checkboard grey
    ) else AppColors(
        accent = accent,
        selected = Color(0xFFE0F2FE), // selected object
        selectedBorder = Color(0xFFBAE6FD), // selected object border
        onSelected = Color(0xffffffff),
        boardLight = Color(0xFFE8E8E8), // checkboard white
        boardDark = Color(0xFFC7C7C7), // checkboard grey
    )
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
