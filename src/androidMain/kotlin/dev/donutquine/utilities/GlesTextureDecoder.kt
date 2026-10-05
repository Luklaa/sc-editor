package dev.donutquine.utilities

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Декодирует сжатые текстуры (ETC1/ETC2/EAC/ASTC) в RGBA через GPU:
 * загружаем данные в GL-текстуру, рисуем её в FBO и читаем пиксели обратно.
 * На desktop для этого используются внешние утилиты ktx2ktx2/ktx, на Android их нет.
 *
 * Требует, чтобы GPU поддерживал формат (ETC2 есть в любом GLES 3.0, ASTC LDR - почти везде).
 */
internal object GlesTextureDecoder {
    private const val TAG = "GlesTextureDecoder"
    private const val EGL_OPENGL_ES3_BIT = 0x0040

    private const val VERTEX_SHADER = """#version 300 es
out vec2 vUv;
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    vUv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}"""

    private const val FRAGMENT_SHADER = """#version 300 es
precision highp float;
uniform sampler2D uTex;
in vec2 vUv;
out vec4 outColor;
void main() {
    outColor = texture(uTex, vUv);
}"""

    /** Возвращает ARGB-пиксели (сверху вниз) или null, если декодировать не удалось. */
    fun decode(width: Int, height: Int, glInternalFormat: Int, data: ByteArray): IntArray? {
        if (width <= 0 || height <= 0) return null

        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE

        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return fail("eglGetDisplay")
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return fail("eglInitialize")

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
                return fail("eglChooseConfig")
            }
            val config = configs[0] ?: return fail("no EGL config")

            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0
            )
            if (context == EGL14.EGL_NO_CONTEXT) return fail("eglCreateContext (GLES 3.0)")

            surface = EGL14.eglCreatePbufferSurface(
                display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
            )
            if (surface == EGL14.EGL_NO_SURFACE) return fail("eglCreatePbufferSurface")
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return fail("eglMakeCurrent")

            return render(width, height, glInternalFormat, data)
        } catch (e: Throwable) {
            Log.e(TAG, "GPU decode failed", e)
            return null
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            }
        }
    }

    private fun render(width: Int, height: Int, format: Int, data: ByteArray): IntArray? {
        val ids = IntArray(1)

        // --- исходная сжатая текстура
        GLES30.glGenTextures(1, ids, 0)
        val srcTex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
        setNearestClamp()
        val expected = compressedSize(format, width, height)
        val size = if (expected in 1..data.size) expected else data.size
        val src = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        src.put(data, 0, size).position(0)
        GLES30.glCompressedTexImage2D(GLES30.GL_TEXTURE_2D, 0, format, width, height, 0, size, src)
        val uploadError = GLES30.glGetError()
        if (uploadError != GLES30.GL_NO_ERROR) {
            return fail("glCompressedTexImage2D error 0x${uploadError.toString(16)} " +
                "(format 0x${format.toString(16)}, ${width}x$height, $size bytes) - GPU не поддерживает формат?")
        }

        // --- целевой FBO
        GLES30.glGenTextures(1, ids, 0)
        val dstTex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dstTex)
        setNearestClamp()
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glGenFramebuffers(1, ids, 0)
        val fbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, dstTex, 0
        )
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            return fail("framebuffer incomplete (${width}x$height)")
        }

        // --- рисуем полноэкранный треугольник с текстурой
        val program = buildProgram() ?: return null
        GLES30.glViewport(0, 0, width, height)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uTex"), 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)

        // Строка 0 в буфере = v=0 = первая строка данных текстуры = верх изображения,
        // поэтому дополнительный flipY не нужен.
        val out = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, out)
        val readError = GLES30.glGetError()
        if (readError != GLES30.GL_NO_ERROR) return fail("glReadPixels error 0x${readError.toString(16)}")

        val rgba = ByteArray(width * height * 4)
        out.position(0)
        out.get(rgba)

        GLES30.glDeleteProgram(program)
        GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        GLES30.glDeleteTextures(2, intArrayOf(srcTex, dstTex), 0)

        return rgbaBytesToArgbInts(width, height, rgba)
    }

    private fun setNearestClamp() {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    private fun buildProgram(): Int? {
        val vs = compile(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER) ?: return null
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER) ?: return null
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (status[0] == 0) {
            Log.e(TAG, "program link: ${GLES30.glGetProgramInfoLog(program)}")
            GLES30.glDeleteProgram(program)
            return null
        }
        return program
    }

    private fun compile(type: Int, source: String): Int? {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "shader compile: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            return null
        }
        return shader
    }

    /** Точный размер сжатого изображения для известных форматов, иначе -1. */
    private fun compressedSize(format: Int, width: Int, height: Int): Int {
        val (bw, bh, bytes) = when (format) {
            0x8D64, 0x9270, 0x9271, 0x9274, 0x9275, 0x9276, 0x9277 -> Triple(4, 4, 8)   // ETC1 / ETC2 RGB / EAC R11
            0x9272, 0x9273, 0x9278, 0x9279 -> Triple(4, 4, 16)                          // EAC RG11 / ETC2 RGBA
            in 0x93B0..0x93BD -> astcBlock(format - 0x93B0)
            in 0x93D0..0x93DD -> astcBlock(format - 0x93D0)
            else -> return -1
        }
        return ((width + bw - 1) / bw) * ((height + bh - 1) / bh) * bytes
    }

    private fun astcBlock(index: Int): Triple<Int, Int, Int> {
        val blocks = arrayOf(
            4 to 4, 5 to 4, 5 to 5, 6 to 5, 6 to 6, 8 to 5, 8 to 6,
            8 to 8, 10 to 5, 10 to 6, 10 to 8, 10 to 10, 12 to 10, 12 to 12
        )
        val (w, h) = blocks[index]
        return Triple(w, h, 16)
    }

    private fun fail(message: String): IntArray? {
        Log.e(TAG, message)
        return null
    }
}
