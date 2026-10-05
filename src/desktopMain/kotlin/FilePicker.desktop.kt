import com.formdev.flatlaf.util.SystemFileChooser
import com.formdev.flatlaf.util.SystemFileChooser.FileNameExtensionFilter
import java.awt.KeyboardFocusManager

actual fun openFilePicker(hint: String, allowMultiple: Boolean, onFilesSelected: (List<String>) -> Unit) {

    val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow

    val chooser = SystemFileChooser()
    chooser.dialogTitle = hint
    chooser.isMultiSelectionEnabled = allowMultiple

    val filter = FileNameExtensionFilter("Supercell SWF", "sc", "sc2", "sctx")
    chooser.fileFilter = filter
    chooser.isAcceptAllFileFilterUsed = false

    val result = chooser.showOpenDialog(activeWindow)
    if (result == SystemFileChooser.APPROVE_OPTION) {
        val files = if (allowMultiple) chooser.selectedFiles.toList() else listOfNotNull(chooser.selectedFile)
        onFilesSelected(files.map { it.absolutePath })
    } else {
        onFilesSelected(emptyList())
    }
}
