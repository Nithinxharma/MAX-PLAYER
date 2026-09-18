package xyz.mpv.rex.cinehub.diagnostic

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class DiagnosticLogLevel {
    ALL,
    INFO,
    WARNING,
    ERROR,
    DEBUG
}

data class DiagnosticLogEntry(
    val id: Long,
    val timestamp: Long,
    val level: DiagnosticLogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}

object DiagnosticLogger {
    private val idCounter = AtomicLong(0)
    private val maxLogs = 500
    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    fun info(tag: String, message: String) {
        Log.i(tag, message)
        append(DiagnosticLogLevel.INFO, tag, message, null)
    }

    fun warn(tag: String, message: String, t: Throwable? = null) {
        Log.w(tag, message, t)
        append(DiagnosticLogLevel.WARNING, tag, message, t?.let { getStackTraceString(it) })
    }

    fun error(tag: String, message: String, t: Throwable? = null) {
        Log.e(tag, message, t)
        append(DiagnosticLogLevel.ERROR, tag, message, t?.let { getStackTraceString(it) })
    }

    fun debug(tag: String, message: String) {
        Log.d(tag, message)
        append(DiagnosticLogLevel.DEBUG, tag, message, null)
    }

    private fun append(level: DiagnosticLogLevel, tag: String, message: String, stackTrace: String?) {
        val entry = DiagnosticLogEntry(
            id = idCounter.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            stackTrace = stackTrace
        )
        synchronized(this) {
            val current = _logs.value
            val updated = if (current.size >= maxLogs) {
                current.drop(current.size - maxLogs + 1) + entry
            } else {
                current + entry
            }
            _logs.value = updated
        }
    }

    fun clear() {
        synchronized(this) {
            _logs.value = emptyList()
        }
    }

    fun getFormattedLogs(
        levelFilter: DiagnosticLogLevel = DiagnosticLogLevel.ALL,
        tagFilter: String? = null,
        query: String? = null
    ): String {
        val currentLogs = _logs.value.filter { log ->
            val matchLevel = (levelFilter == DiagnosticLogLevel.ALL || log.level == levelFilter)
            val matchTag = (tagFilter.isNullOrBlank() || log.tag.equals(tagFilter, ignoreCase = true))
            val matchQuery = (query.isNullOrBlank() || log.message.contains(query, ignoreCase = true) || log.tag.contains(query, ignoreCase = true) || (log.stackTrace?.contains(query, ignoreCase = true) == true))
            matchLevel && matchTag && matchQuery
        }

        val sb = StringBuilder()
        sb.appendLine("=== CLOUDSTREAM DIAGNOSTIC LOGS (${currentLogs.size} ENTRIES) ===")
        for (log in currentLogs) {
            sb.append("[${log.formattedTime}] [${log.level.name}] [${log.tag}] ${log.message}\n")
            if (!log.stackTrace.isNullOrBlank()) {
                sb.append("Stacktrace:\n${log.stackTrace}\n")
            }
        }
        return sb.toString()
    }

    private fun getStackTraceString(t: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
}
