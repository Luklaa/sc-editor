// Временная заглушка для Android (напишем её полноценно на шаге порта)
actual fun openFilePicker(onFileSelected: (String?) -> Unit) {
    onFileSelected(null)
}
