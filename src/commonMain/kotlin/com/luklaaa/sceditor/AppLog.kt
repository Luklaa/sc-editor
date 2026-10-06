package com.luklaaa.sceditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Простой журнал приложения: хранится в памяти (для экрана логов) и дублируется в файл,
 * чтобы после вылета приложения можно было прочитать, что было в прошлом запуске.
 * Можно вызывать из любого потока.
 */
object AppLog {
    private const val MAX_LINES = 3000
    private const val MAX_PREVIOUS_CHARS = 100_000

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var file: File? = null
    private var previousSession: String = ""

    /** Растёт при каждой новой записи - по нему экран логов понимает, что пора обновиться. */
    var revision by mutableStateOf(0)
        private set

    /** Вызывается один раз при старте приложения. [logFile] - куда дублировать записи. */
    fun init(logFile: File) {
        synchronized(lock) {
            file = logFile
            previousSession = try {
                if (logFile.isFile) logFile.readText().takeLast(MAX_PREVIOUS_CHARS) else ""
            } catch (e: Throwable) {
                ""
            }
            try {
                logFile.parentFile?.mkdirs()
                logFile.writeText("")
            } catch (e: Throwable) {
                // Логи не должны ронять приложение.
            }
        }
        i("=== SC Editor started | ${System.getProperty("os.name") ?: "?"} ${System.getProperty("os.version") ?: ""} | ${memory()} ===")
    }

    fun i(message: String) = add("I", message)

    fun w(message: String) = add("W", message)

    fun e(message: String, throwable: Throwable? = null) {
        val trace = throwable?.let { "\n" + it.stackTraceToString().lineSequence().take(40).joinToString("\n") } ?: ""
        add("E", message + trace)
    }

    /** Текущее использование кучи Java, например "heap 120 / 512 MB". */
    fun memory(): String {
        val rt = Runtime.getRuntime()
        val mb = 1024L * 1024L
        return "heap ${(rt.totalMemory() - rt.freeMemory()) / mb} / ${rt.maxMemory() / mb} MB"
    }

    /** Весь журнал: прошлый запуск (если был) и текущий. */
    fun text(): String = synchronized(lock) {
        val current = lines.joinToString("\n")
        if (previousSession.isBlank()) current
        else "--- previous session ---\n${previousSession.trimEnd()}\n--- current session ---\n$current"
    }

    fun clear() {
        synchronized(lock) {
            lines.clear()
            previousSession = ""
            try { file?.writeText("") } catch (e: Throwable) { /* ignore */ }
        }
        revision++
    }

    private fun add(level: String, message: String) {
        val line = "${synchronized(lock) { timeFormat.format(Date()) }} $level $message"
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
            try { file?.appendText(line + "\n") } catch (e: Throwable) { /* ignore */ }
        }
        revision++
    }
}
