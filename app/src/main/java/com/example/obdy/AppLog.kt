package com.example.obdy

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/** The last few hundred lines of everything the app sent to the adapter and got back. Shared from the Console screen. */
object AppLog {
    private val lines = ConcurrentLinkedDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private const val MAX = 600

    fun i(msg: String) {
        lines.addLast(fmt.format(Date()) + "  " + msg)
        while (lines.size > MAX) lines.pollFirst()
    }

    fun dump(): String = lines.joinToString("\n")
    fun clear() = lines.clear()
}
