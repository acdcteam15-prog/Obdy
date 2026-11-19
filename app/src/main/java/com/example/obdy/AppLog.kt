package com.example.obdy

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Everything the app sends, receives, shows and does. Two lists in memory: [lines] is the last few hundred lines (live polling
 * fills it in about half a minute); [kept] only holds the one-off conversations (scans, self-tests, probes). The complete record
 * is the file on disk: every line carries its full date and time, is written the moment it happens, and "Delete the log" archives
 * the old file instead of erasing it.
 *
 * Line tags: >> sent to the adapter, << received from it, [YOU] what you did, [APP]/[APP SAYS] what the app did or said,
 * [SHOWN] text that appeared on screen, [VALUE] every decoded reading, [SETTING] a changed setting, [NET] internet requests,
 * [LINK] Bluetooth link events, [SCAN] results, [CRASH] an uncaught error with its stack trace.
 */
object AppLog {
    private val lines = ConcurrentLinkedDeque<String>()
    private val kept = ConcurrentLinkedDeque<String>()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val full = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private const val MAX = 600
    private const val MAX_KEPT = 1500
    private const val MAX_FILE = 300L * 1024 * 1024  // above this the oldest half is dropped (a long day is about 20 MB)

    // ---- complete log on disk: every line since the first start, until the owner archives it
    private var file: File? = null
    private var writer: BufferedWriter? = null
    private var crashHandlerSet = false

    @Synchronized fun init(dir: File) {
        if (writer != null) return
        try {
            dir.mkdirs()
            val f = File(dir, "obdy_full_log.txt")
            if (f.length() > MAX_FILE) {
                val keep = f.readBytes().let { it.copyOfRange(it.size / 2, it.size) }
                f.writeBytes(keep)
            }
            file = f
            writer = BufferedWriter(FileWriter(f, true))
            writer?.write("\n===== app started " + stampFull() + " =====\n"); writer?.flush()
        } catch (_: Exception) { writer = null }
        installCrashHandler()
    }

    private fun stampFull(): String = synchronized(full) { full.format(Date()) }

    /** An uncaught error must leave its stack trace in the log before the process dies. */
    private fun installCrashHandler() {
        if (crashHandlerSet) return
        crashHandlerSet = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { i("[CRASH] uncaught error in thread ${t.name}: " + android.util.Log.getStackTraceString(e)); flush() } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }
    }

    // Every line is flushed at once: whatever happened right before a crash, a kill or a lost link must be in the file.
    @Synchronized private fun toDisk(l: String) {
        val w = writer ?: return
        try { w.write(l); w.write("\n"); w.flush() } catch (_: Exception) {}
    }

    fun i(msg: String, keep: Boolean = false) {
        val l = synchronized(fmt) { fmt.format(Date()) } + "  " + msg
        lines.addLast(l)
        while (lines.size > MAX) lines.pollFirst()
        if (keep) { kept.addLast(l); while (kept.size > MAX_KEPT) kept.pollFirst() }
        toDisk(l)
    }

    /** Bytes that came from the adapter but were not part of any answer (arrived late, or thrown away before the next request). */
    fun late(text: String, note: String = "unread bytes thrown away before the next request") {
        val t = text.replace('\r', ' ').replace('\n', ' ').trim()
        if (t.isNotEmpty()) i("<< ($note) $t", true)
    }

    fun dump(): String = lines.joinToString("\n")
    fun dumpKept(): String = kept.joinToString("\n")
    fun recent(n: Int): String = lines.toList().takeLast(n).joinToString("\n")

    /** Clears the on-screen buffers and ARCHIVES the file on disk (it is never erased); a new file starts. */
    @Synchronized fun clear() {
        lines.clear(); kept.clear()
        try { writer?.flush(); writer?.close() } catch (_: Exception) {}
        writer = null
        val f = file ?: return
        try {
            var note = "no earlier log"
            if (f.exists() && f.length() > 0) {
                val arch = File(f.parentFile, "obdy_log_" + synchronized(stamp) { stamp.format(Date()) } + ".txt")
                note = if (f.renameTo(arch)) "earlier log archived as ${arch.name}" else { f.copyTo(arch, overwrite = true); "earlier log copied to ${arch.name}" }
            }
            writer = BufferedWriter(FileWriter(f, false))
            writer?.write("===== new log started " + stampFull() + " (" + note + ") =====\n"); writer?.flush()
        } catch (_: Exception) { writer = null }
    }

    @Synchronized fun flush() { try { writer?.flush() } catch (_: Exception) {} }

    /** The complete log (archived files first, then the current one), flushed; or null when nothing can be read. */
    fun fileForShare(): File? {
        flush()
        val cur = file?.takeIf { it.exists() && it.length() > 0 }
        val dir = file?.parentFile ?: return cur
        val arch = dir.listFiles { x -> x.name.startsWith("obdy_log_") && x.name.endsWith(".txt") && x.name != "obdy_log_all.txt" }?.sortedBy { it.name } ?: emptyList()
        if (arch.isEmpty()) return cur
        return try {
            val all = File(dir, "obdy_log_all.txt")
            all.outputStream().use { o ->
                for (a in arch) a.inputStream().use { it.copyTo(o) }
                cur?.inputStream()?.use { it.copyTo(o) }
            }
            all
        } catch (_: Exception) { cur }
    }

    fun fileSize(): Long { flush(); return file?.length() ?: 0L }
}
