package team.nulls.ntengine.assets

data class KhronosTexture(
    val glType: Int,
    val glTypeSize: Int,
    val glFormat: Int,
    val glInternalFormat: Int,
    val glBaseInternalFormat: Int,
    val width: Int,
    val height: Int,
    val levels: Array<ByteArray>
) {
    // В Kotlin массивы требуют переопределения equals/hashCode для корректного сравнения контента
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as KhronosTexture

        if (glType != other.glType) return false
        if (glTypeSize != other.glTypeSize) return false
        if (glFormat != other.glFormat) return false
        if (glInternalFormat != other.glInternalFormat) return false
        if (glBaseInternalFormat != other.glBaseInternalFormat) return false
        if (width != other.width) return false
        if (height != other.height) return false
        return levels.contentDeepEquals(other.levels)
    }

    override fun hashCode(): Int {
        var result = glType
        result = 31 * result + glTypeSize
        result = 31 * result + glFormat
        result = 31 * result + glInternalFormat
        result = 31 * result + glBaseInternalFormat
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + levels.contentDeepHashCode()
        return result
    }
}
