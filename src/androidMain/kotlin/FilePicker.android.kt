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
        launcher = activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val callback = pendingCallback
            pendingCallback = null
            callback?.invoke(if (uris.isNullOrEmpty()) null else copyToSessionDir(uris))
        }
    }

    fun pick(onFileSelected: (String?) -> Unit) {
        pendingCallback = onFileSelected
        launcher.launch(arrayOf("*/*"))
    }

    private fun copyToSessionDir(uris: List<Uri>): String? {
        return try {
            val root = File(activity.cacheDir, "opened")
            root.listFiles()?.forEach { it.deleteRecursively() } // чистим прошлые сессии
            val dir = File(root, System.currentTimeMillis().toString()).apply { mkdirs() }

            val copied = uris.mapNotNull { uri ->
                val name = (queryName(uri) ?: "picked_file").substringAfterLast('/').substringAfterLast('\\')
                val out = File(dir, name)
                activity.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                } ?: return@mapNotNull null
                out
            }
            pickPrimary(copied)?.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun pickPrimary(files: List<File>): File? =
        files.firstOrNull { f ->
            val n = f.name.lowercase()
            (n.endsWith(".sctx") || n.endsWith(".sc2") || n.endsWith(".sc")) && !n.endsWith("_tex.sc")
        } ?: files.firstOrNull()

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
