import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

object AndroidFilePicker {
    private lateinit var activity: ComponentActivity
    private lateinit var launcher: ActivityResultLauncher<Array<String>>
    private var pendingCallback: ((String?) -> Unit)? = null

    fun init(activity: ComponentActivity) {
        this.activity = activity
        launcher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val callback = pendingCallback
            pendingCallback = null
            callback?.invoke(uri?.let { copyToCache(it) })
        }
    }

    fun pick(onFileSelected: (String?) -> Unit) {
        pendingCallback = onFileSelected
        launcher.launch(arrayOf("*/*"))
    }

    private fun copyToCache(uri: Uri): String? {
        return try {
            val name = queryName(uri) ?: "picked_file"
            val outFile = File(activity.cacheDir, name)
            activity.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun queryName(uri: Uri): String? {
        var name: String? = null
        activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
        }
        return name
    }
}

actual fun openFilePicker(onFileSelected: (String?) -> Unit) {
    AndroidFilePicker.pick(onFileSelected)
}