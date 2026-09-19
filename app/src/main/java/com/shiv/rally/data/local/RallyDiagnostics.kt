package com.shiv.rally.data.local

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RallyDiagnosticEntry(
    val kind: String,
    val message: String,
    val detail: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

/** Local-only diagnostics. No account, stream URL, token, or device identifier is uploaded. */
@Singleton
class RallyDiagnostics @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "rally_diagnostics.json"))

    @Synchronized
    fun record(kind: String, message: String, detail: String? = null) {
        val entries = read().takeLast(19).toMutableList()
        entries += RallyDiagnosticEntry(kind, message.take(240), detail?.take(1_500))
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("kind", entry.kind)
                put("message", entry.message)
                put("detail", entry.detail)
                put("timestamp", entry.timestampMs)
            })
        }
        var output: java.io.FileOutputStream? = null
        runCatching {
            output = file.startWrite()
            output!!.write(array.toString().toByteArray())
            file.finishWrite(output)
        }.onFailure { file.failWrite(output) }
    }

    @Synchronized
    fun read(): List<RallyDiagnosticEntry> = runCatching {
        if (!file.baseFile.exists()) return@runCatching emptyList()
        val array = JSONArray(file.openRead().bufferedReader().use { it.readText() })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    RallyDiagnosticEntry(
                        kind = item.optString("kind", "Issue"),
                        message = item.optString("message", "Unknown issue"),
                        detail = item.optString("detail").takeIf { it.isNotBlank() && it != "null" },
                        timestampMs = item.optLong("timestamp")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun clear() = runCatching { file.delete() }.let { Unit }

    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is RallyCrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(RallyCrashHandler(this, previous))
    }

    private class RallyCrashHandler(
        private val diagnostics: RallyDiagnostics,
        private val previous: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            diagnostics.record(
                kind = "Crash",
                message = error.javaClass.simpleName + ": " + error.message.orEmpty(),
                detail = error.stackTraceToString().lineSequence().take(18).joinToString("\n")
            )
            previous?.uncaughtException(thread, error)
        }
    }
}
