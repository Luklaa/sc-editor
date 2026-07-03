package ui

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

actual fun resizeCursor(): PointerIcon =
    PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))