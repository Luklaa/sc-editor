import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.formdev.flatlaf.FlatLightLaf // Светлая тема
import com.formdev.flatlaf.util.SystemInfo
import javax.swing.*
import java.awt.Color
import java.awt.Dimension

fun main() = application {
    FlatLightLaf.setup()

    if (SystemInfo.isWindows_10_orLater) {
        UIManager.put("TitlePane.useWindowDecorations", true)
        UIManager.put("TitlePane.menuBarEmbedded", true)

        UIManager.put("TitlePane.background", Color(223, 231, 245))
        UIManager.put("TitlePane.inactiveBackground", Color(223, 231, 245))
        UIManager.put("TitlePane.foreground", Color(30, 41, 59))

        UIManager.put("PopupMenu.background", Color(255, 255, 255, 110))
        UIManager.put("PopupMenu.borderColor", Color(226, 232, 240))

        UIManager.put("MenuBar.hoverBackground", Color(233, 236, 242))
        UIManager.put("MenuBar.selectionBackground", Color(229, 234, 242))
        UIManager.put("MenuBar.selectionForeground", Color(30, 41, 59))

        UIManager.put("Menu.selectionBackground", Color(229, 234, 242))
        UIManager.put("Menu.selectionForeground", Color(30, 41, 59))
        UIManager.put("MenuItem.selectionBackground", Color(229, 234, 242, 150))
        UIManager.put("MenuItem.selectionForeground", Color(30, 41, 59))
    }

    val windowState = rememberWindowState(size = DpSize(1024.dp, 720.dp))
    var windowTitle by remember { mutableStateOf("SC Editor 1.6.2") }
    var triggerOpenFile by remember { mutableStateOf(false) }
    var triggerCloseFile by remember { mutableStateOf(false) }
    var triggerCloseAllFiles by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = windowTitle,
        undecorated = false
    ) {
        LaunchedEffect(window) {
            window.minimumSize = Dimension(800, 600)
        }

        DisposableEffect(window) {
            val menuBar = JMenuBar()

            val fileMenu = JMenu("File")
            val openItem = JMenuItem("Open").apply { addActionListener { triggerOpenFile = true } }
            val saveItem = JMenuItem("Save").apply { isEnabled = false }
            val saveAsItem = JMenuItem("Save As...").apply { isEnabled = false }
            val closeItem = JMenuItem("Close").apply { addActionListener { triggerCloseFile = true } }
            val closeAllItem = JMenuItem("Close All").apply { addActionListener { triggerCloseAllFiles = true } }
            val screenshotsItem = JMenuItem("Open Screenshots folder").apply { isEnabled = false }
            val exitItem = JMenuItem("Exit").apply { addActionListener { java.lang.System.exit(0) } }

            fileMenu.add(openItem)
            fileMenu.add(saveItem)
            fileMenu.add(saveAsItem)
            fileMenu.add(closeItem)
            fileMenu.add(closeAllItem)
            fileMenu.add(JSeparator())
            fileMenu.add(screenshotsItem)
            fileMenu.add(JSeparator())
            fileMenu.add(exitItem)

            menuBar.add(fileMenu)
            menuBar.add(JMenu("Edit"))
            menuBar.add(JMenu("View"))
            menuBar.add(JMenu("Options"))
            menuBar.add(JMenu("Help"))

            window.jMenuBar = menuBar

            onDispose {
                window.jMenuBar = null
            }
        }

        App(
            onTitleChanged = { windowTitle = it },
            onExit = ::exitApplication,
            triggerOpenFile = triggerOpenFile,
            onOpenFileHandled = { triggerOpenFile = false },
            triggerCloseFile = triggerCloseFile,
            onCloseFileHandled = { triggerCloseFile = false },
            triggerCloseAllFiles = triggerCloseAllFiles,
            onCloseAllFilesHandled = { triggerCloseAllFiles = false }
        )
    }
}
