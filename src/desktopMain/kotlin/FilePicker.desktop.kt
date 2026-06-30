import com.formdev.flatlaf.util.SystemFileChooser
import com.formdev.flatlaf.util.SystemFileChooser.FileNameExtensionFilter
import java.awt.KeyboardFocusManager

actual fun openFilePicker(onFileSelected: (String?) -> Unit) {

    val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow

    val chooser = SystemFileChooser()
    chooser.dialogTitle = "Choose Files"

    val filter = FileNameExtensionFilter("Файлы Supercell (.sc, .sctx)", "sc", "sctx")
    chooser.fileFilter = filter
    chooser.isAcceptAllFileFilterUsed = false

    val result = chooser.showOpenDialog(activeWindow)
    if (result == SystemFileChooser.APPROVE_OPTION) {
        onFileSelected(chooser.selectedFile.absolutePath)
    } else {
        onFileSelected(null)
    }
}
