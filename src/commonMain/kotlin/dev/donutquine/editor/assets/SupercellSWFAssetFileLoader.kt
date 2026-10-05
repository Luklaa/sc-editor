package dev.donutquine.editor.assets

import dev.donutquine.editor.assets.exceptions.AssetLoadingException
import dev.donutquine.swf.SupercellSWF
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * .sc (старые версии) ссылается на внешний _tex.sc, но рядом такого файла нет.
 * [expectedName] - имя файла, под которым библиотека ожидает увидеть текстуры.
 */
class TextureFileMissingException(val expectedName: String) :
    Exception("Texture file is not found. Expected: $expectedName")

class SupercellSWFAssetFileLoader(private val filePath: String) {
    fun load(): SupercellSWF {
        return loadInternal(filePath)
    }

    companion object {
        /**
         * @throws TextureFileMissingException если .sc требует внешний _tex.sc, а его рядом нет.
         */
        fun loadInternal(path: String): SupercellSWF {
            val swf = SupercellSWF()
            val file = File(path)
            if (!file.isFile) {
                throw AssetLoadingException(IllegalStateException("File \"${file.name}\" does not exist"))
            }
            try {
                checkLoaded(swf.load(file.absolutePath, file.name, false), file)
            } catch (e: AssetLoadingException) {
                throw e
            } catch (e: Exception) {
                // Проверяем по имени класса, а не через catch (e: TextureFileNotFound), чтобы не зависеть от
                // версии supercell-swf: исключение есть не во всех версиях API.
                if (e.javaClass.simpleName == "TextureFileNotFound") {
                    // Библиотека пишет в сообщении путь, под которым ожидала _tex.sc (для файлов с нестандартным
                    // разрешением имя отличается от "<name>_tex.sc").
                    val expected = e.message?.substringAfter("Expected filepath: ", "")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { File(it).name }
                        ?: (file.nameWithoutExtension + "_tex.sc")
                    throw TextureFileMissingException(expected)
                }
                throw AssetLoadingException(e)
            }
            return swf
        }

        /**
         * Загрузка .sc вместе с вручную выбранным файлом текстур. Библиотека ищет текстуры строго рядом с .sc
         * под определённым именем, поэтому кладём оба файла во временную папку (оригинальную папку
         * пользователя не трогаем - на Android там вообще только копии из кэша).
         */
        fun loadWithTexture(path: String, texturePath: String, expectedTextureName: String): SupercellSWF {
            val source = File(path)
            val dir = Files.createTempDirectory("sceditor_load").toFile()
            try {
                Files.copy(source.toPath(), File(dir, source.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
                Files.copy(File(texturePath).toPath(), File(dir, expectedTextureName).toPath(), StandardCopyOption.REPLACE_EXISTING)
                return loadInternal(File(dir, source.name).absolutePath)
            } finally {
                // swf уже целиком в памяти, временные копии больше не нужны
                dir.deleteRecursively()
            }
        }

        private fun checkLoaded(loaded: Boolean, file: File) {
            if (!loaded) {
                throw AssetLoadingException(
                    IllegalStateException(
                        "Не удалось распарсить файл \"${file.name}\". " +
                                "Подробности в логах (SLF4J LOGGER.error из supercell-swf) — " +
                                "обычно это неизвестная версия контейнера или повреждённый тег."
                    )
                )
            }
        }
    }
}
