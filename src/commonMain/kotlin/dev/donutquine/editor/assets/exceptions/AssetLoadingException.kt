package dev.donutquine.editor.assets.exceptions

class AssetLoadingException : Exception {
    constructor(cause: Throwable) : super(cause)
    constructor(message: String) : super(message)
}
