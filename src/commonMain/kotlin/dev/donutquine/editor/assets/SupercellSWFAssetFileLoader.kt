package dev.donutquine.editor.assets

import dev.donutquine.editor.assets.exceptions.AssetLoadingException
import dev.donutquine.swf.SupercellSWF
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class TextureFileMissingException(val expectedName: String) :
    Exception("Texture file is not found. Expected: $expectedName")

class SupercellSWFAssetFileLoader(private val filePath: String) {
    fun load(): SupercellSWF {
        return loadInternal(filePath)
    }

    companion object {
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
                if (e.javaClass.simpleName == "TextureFileNotFound") {
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

        fun loadWithTexture(path: String, texturePath: String, expectedTextureName: String): SupercellSWF {
            return withTextureCopy(path, texturePath, expectedTextureName) { copiedPath -> loadInternal(copiedPath) }
        }

        fun <T> withTextureCopy(path: String, texturePath: String, expectedTextureName: String, block: (String) -> T): T {
            val source = File(path)
            val dir = Files.createTempDirectory("sceditor_load").toFile()
            try {
                Files.copy(source.toPath(), File(dir, source.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
                Files.copy(File(texturePath).toPath(), File(dir, expectedTextureName).toPath(), StandardCopyOption.REPLACE_EXISTING)
                return block(File(dir, source.name).absolutePath)
            } finally {
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
