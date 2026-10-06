package dev.donutquine.editor.assets

import dev.donutquine.swf.Export
import dev.donutquine.swf.ScMatrixBank
import dev.donutquine.swf.SupercellSWF
import dev.donutquine.swf.movieclips.MovieClipModifierOriginal
import dev.donutquine.swf.movieclips.MovieClipOriginal
import dev.donutquine.swf.shapes.ShapeOriginal
import dev.donutquine.swf.textfields.TextFieldOriginal

/** Одна текстура в виде, удобном для декодирования в bitmap (из SC1 через библиотеку или из SC2 напрямую). */
class TextureSource(
    val width: Int,
    val height: Int,
    val typeName: String?,
    /** Сырые пиксели (старые форматы) или null. */
    val pixels: Any?,
    /** Данные KTX (сжатые текстуры) или null. */
    val ktxData: ByteArray?,
    /** Освободить данные сразу после декодирования, не дожидаясь конца загрузки файла. */
    val release: () -> Unit = {}
)

/**
 * Результат разбора .sc: то, что нужно TabLoader. Текстуры отдаются по одной ([openTexture]),
 * чтобы исходные данные всех текстур не лежали в памяти одновременно.
 */
class SwfData(
    val containerVersion: Int,
    val textureCount: Int,
    val openTexture: (Int) -> TextureSource,
    val shapes: List<ShapeOriginal>?,
    /** Мувиклипы отдаём по одному: у больших файлов (ui.sc) их объекты в библиотеке занимают сотни МБ. */
    val movieClipCount: Int,
    val movieClipAt: (Int) -> MovieClipOriginal,
    val textFields: List<TextFieldOriginal>?,
    val exports: List<Export>?,
    val movieClipModifiers: List<MovieClipModifierOriginal>?,
    val matrixBanks: List<ScMatrixBank>?
) {
    companion object {
        /** Обёртка над объектом библиотеки (SC1 и всё, что не умеет наш загрузчик). */
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

/** Обнуляет сырые данные текстуры в объекте библиотеки (публичного способа освободить их там нет). */
internal fun releaseTextureData(texture: Any) {
    for (fieldName in listOf("pixels", "ktxData")) {
        try {
            val field = texture.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(texture, null)
        } catch (_: Throwable) {
            // Другая версия библиотеки - не страшно, просто не освободим раньше времени.
        }
    }
}
