import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

object AndroidFilePicker {
    private lateinit var activity: ComponentActivity
    private lateinit var launcher: ActivityResultLauncher<Array<String>>
    private var pendingCallback: ((List<String>) -> Unit)? = null
    private var pendingAllowMultiple = true

    fun init(activity: ComponentActivity) {
        this.activity = activity
        launcher = activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val callback = pendingCallback
            val allowMultiple = pendingAllowMultiple
            pendingCallback = null
            if (callback == null) return@registerForActivityResult
            if (uris.isNullOrEmpty()) {
                callback(emptyList())
                return@registerForActivityResult
            }
            // Копирование больших файлов (ui.sc может весить десятки МБ) - не на главном потоке, иначе ANR.
            Thread {
                val files = copyToSessionDir(if (allowMultiple) uris else uris.take(1))
                activity.runOnUiThread { callback(files) }
            }.start()
        }
    }

    fun pick(hint: String, allowMultiple: Boolean, onFilesSelected: (List<String>) -> Unit) {
        pendingCallback = onFilesSelected
        pendingAllowMultiple = allowMultiple
        // Системный пикер - отдельная Activity, поэтому Compose-тост под ним не виден; обычный Toast виден.
        Toast.makeText(activity, hint, Toast.LENGTH_LONG).show()
        launcher.launch(arrayOf("*/*"))
    }

    private fun copyToSessionDir(uris: List<Uri>): List<String> {
        return try {
            val root = File(activity.cacheDir, "opened")
            // Чистим старые сессии, но оставляем самую свежую: между выбором .sc и выбором _tex.sc
            // первый файл ещё нужен.
            root.listFiles()?.sortedBy { it.name }?.dropLast(1)?.forEach { it.deleteRecursively() }
            val dir = File(root, System.currentTimeMillis().toString()).apply { mkdirs() }

            uris.mapNotNull { uri ->
                val name = (queryName(uri) ?: "picked_file").substringAfterLast('/').substringAfterLast('\\')
                val out = File(dir, name)
                activity.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                } ?: return@mapNotNull null
                out.absolutePath
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            emptyList()
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

actual fun openFilePicker(hint: String, allowMultiple: Boolean, onFilesSelected: (List<String>) -> Unit) {
    AndroidFilePicker.pick(hint, allowMultiple, onFilesSelected)
}
