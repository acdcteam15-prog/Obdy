package com.example.obdy

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
    private const val MAX = 600
    private const val MAX_KEPT = 1500

    fun i(msg: String, keep: Boolean = false) {
        val l = fmt.format(Date()) + "  " + msg
        lines.addLast(l)
        while (lines.size > MAX) lines.pollFirst()
        if (keep) { kept.addLast(l); while (kept.size > MAX_KEPT) kept.pollFirst() }
    }

    fun dump(): String = lines.joinToString("\n")
    fun dumpKept(): String = kept.joinToString("\n")
    fun recent(n: Int): String = lines.toList().takeLast(n).joinToString("\n")
    fun clear() { lines.clear(); kept.clear() }
}
