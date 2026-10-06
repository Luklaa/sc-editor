package dev.donutquine.editor.assets

import dev.donutquine.swf.Export
import dev.donutquine.swf.ScMatrixBank
import dev.donutquine.swf.SupercellSWF
import dev.donutquine.swf.movieclips.MovieClipModifierOriginal
import dev.donutquine.swf.movieclips.MovieClipOriginal
import dev.donutquine.swf.shapes.ShapeOriginal
import dev.donutquine.swf.textfields.TextFieldOriginal

class TextureSource(
    val width: Int,
    val height: Int,
    val typeName: String?,
    val pixels: Any?,
    val ktxData: ByteArray?,
    val release: () -> Unit = {}
)

class SwfData(
    val containerVersion: Int,
    val textureCount: Int,
    val openTexture: (Int) -> TextureSource,
    val shapes: List<ShapeOriginal>?,
    val movieClipCount: Int,
    val movieClipAt: (Int) -> MovieClipOriginal,
    val textFields: List<TextFieldOriginal>?,
    val exports: List<Export>?,
    val movieClipModifiers: List<MovieClipModifierOriginal>?,
    val matrixBanks: List<ScMatrixBank>?
) {
    companion object {
        fun fromLibrary(swf: SupercellSWF): SwfData {
            val textures = try { swf.textures } catch (e: NullPointerException) { null } ?: emptyList()
            val clips = nullIfNpe { swf.movieClips } ?: emptyList()
            return SwfData(
                containerVersion = swf.containerVersion,
                textureCount = textures.size,
                openTexture = { i ->
                    val tex = textures[i]
                    TextureSource(
                        width = tex.width,
                        height = tex.height,
                        typeName = tex.type?.toString(),
                        pixels = tex.getPixels(),
                        ktxData = tex.getKtxData(),
                        release = { releaseTextureData(tex) }
                    )
                },
                shapes = nullIfNpe { swf.shapes },
                movieClipCount = clips.size,
                movieClipAt = { i -> clips[i] },
                textFields = nullIfNpe { swf.textFields },
                exports = nullIfNpe { swf.exports },
                movieClipModifiers = nullIfNpe { swf.movieClipModifiers },
                matrixBanks = nullIfNpe { swf.matrixBanks }
            )
        }

        private inline fun <T> nullIfNpe(block: () -> T?): T? =
            try { block() } catch (e: NullPointerException) { null }
    }
}

internal fun releaseTextureData(texture: Any) {
    for (fieldName in listOf("pixels", "ktxData")) {
        try {
            val field = texture.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(texture, null)
        } catch (_: Throwable) {
        }
    }
}
