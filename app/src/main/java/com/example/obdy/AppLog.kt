package com.example.obdy

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Everything the app sent to the adapter and got back. Two lists: [lines] is the last few hundred lines of all traffic (live
 * polling fills it in about half a minute); [kept] only holds the one-off conversations (scans, self-tests, probes) so they
 * are still there when you share the log minutes later.
 */
object AppLog {
    private val lines = ConcurrentLinkedDeque<String>()
    private val kept = ConcurrentLinkedDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val full = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private const val MAX = 600
    private const val MAX_KEPT = 1500
    private const val MAX_FILE = 300L * 1024 * 1024  // above this the oldest half is dropped (a long day is about 20 MB)

    // ---- complete log on disk: every line since the first start, until the owner deletes it
    private var file: File? = null
    private var writer: BufferedWriter? = null
    private var lastFlush = 0L

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
            writer?.write("\n===== app started " + full.format(Date()) + " =====\n"); writer?.flush()
        } catch (_: Exception) { writer = null }
    }

    @Synchronized private fun toDisk(l: String) {
        val w = writer ?: return
        try {
            w.write(l); w.write("\n")
            val now = System.currentTimeMillis()
            if (now - lastFlush > 700) { w.flush(); lastFlush = now }
        } catch (_: Exception) {}
    }

    fun i(msg: String, keep: Boolean = false) {
        val l = fmt.format(Date()) + "  " + msg
        lines.addLast(l)
        while (lines.size > MAX) lines.pollFirst()
        if (keep) { kept.addLast(l); while (kept.size > MAX_KEPT) kept.pollFirst() }
        toDisk(l)
        if (msg.startsWith("[YOU]") || msg.startsWith("[APP]") || msg.startsWith("!!")) flush()   // what you did must survive a crash
    }

    fun dump(): String = lines.joinToString("\n")
    fun dumpKept(): String = kept.joinToString("\n")
    fun recent(n: Int): String = lines.toList().takeLast(n).joinToString("\n")
    /** Clears the on-screen buffers and the file on disk. */
    @Synchronized fun clear() {
        lines.clear(); kept.clear()
        try { writer?.close() } catch (_: Exception) {}
        val f = file
        if (f != null) { try { f.delete(); writer = BufferedWriter(FileWriter(f, true)); writer?.write("===== log deleted " + full.format(Date()) + " =====\n"); writer?.flush() } catch (_: Exception) { writer = null } }
    }
    @Synchronized fun flush() { try { writer?.flush() } catch (_: Exception) {} }
    /** The complete log file (flushed), or null when it cannot be written. */
    fun fileForShare(): File? { flush(); return file?.takeIf { it.exists() && it.length() > 0 } }
    fun fileSize(): Long { flush(); return file?.length() ?: 0L }
}
