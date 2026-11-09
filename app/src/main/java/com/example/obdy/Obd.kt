package com.example.obdy

/** One answer from one ECU: [header] is the CAN id it answered from (for example 7E8 = engine, 7EA = transmission). */
class Frame(val header: String, val data: ByteArray) {
    fun u(i: Int): Int = if (i in data.indices) data[i].toInt() and 0xFF else 0
    fun hex(): String = data.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    override fun toString() = "$header: ${hex()}"
}

/** What an ELM327 sent back for one command, already split into one [Frame] per ECU. */
class ElmReply(val raw: String, val frames: List<Frame>, val error: String?) {
    val ok: Boolean get() = error == null && frames.isNotEmpty()

    /** The first frame that is a positive answer to [service] (answer byte = service + 0x40). */
    fun positive(service: Int): Frame? = frames.firstOrNull { it.data.isNotEmpty() && (it.u(0) == service + 0x40) }

    /** Negative response frames ("7F service code"). */
    fun negatives(): List<Frame> = frames.filter { it.data.size >= 3 && it.u(0) == 0x7F }
}

object ObdParse {
    private val errorWords = listOf(
        "NO DATA", "UNABLE TO CONNECT", "CAN ERROR", "BUFFER FULL", "BUS BUSY", "DATA ERROR", "STOPPED",
        "FB ERROR", "LV RESET", "BUS ERROR", "ACT ALERT", "ERR", "?"
    )
    private val hexRe = Regex("^[0-9A-Fa-f]+$")

    private fun isHexLine(tokens: List<String>) = tokens.isNotEmpty() && tokens.all { hexRe.matches(it) }

    fun parse(raw: String): ElmReply {
        val lines = raw.split('\r', '\n').map { it.replace(">", "").trim() }.filter { it.isNotEmpty() }
        var error: String? = null
        val headered = LinkedHashMap<String, MutableList<List<Int>>>()
        val plain = ArrayList<List<Int>>()
        val numbered = ArrayList<Int>()
        var sawNumbered = false

        for (line in lines) {
            val up = line.uppercase()
            if (up.startsWith("SEARCHING") || up.startsWith("BUS INIT") || up.startsWith("ELM") || up.startsWith("OK") || up.startsWith("AT")) continue
            val tokens = up.split(Regex("\\s+"))
            // "0: 49 02 01 ..." numbered continuation lines (ATH0 multi-frame answers)
            if (tokens[0].length == 2 && tokens[0].endsWith(":") && hexRe.matches(tokens[0].dropLast(1)) && isHexLine(tokens.drop(1))) {
                sawNumbered = true
                tokens.drop(1).forEach { numbered.add(it.toInt(16)) }
                continue
            }
            if (isHexLine(tokens)) {
                if (tokens.size == 1 && tokens[0].length == 3) continue          // "014": total length line of a numbered answer
                if (tokens[0].length == 3 && tokens.size >= 2 && tokens.drop(1).all { it.length == 2 }) {
                    headered.getOrPut(tokens[0]) { ArrayList() }.add(tokens.drop(1).map { it.toInt(16) })
                } else if (tokens.all { it.length == 2 }) {
                    plain.add(tokens.map { it.toInt(16) })
                } else if (tokens.size == 1 && tokens[0].length % 2 == 0) {
                    // spaces switched off (ATS0): "7E80641000000" cannot be told apart reliably, treat as plain bytes
                    plain.add(tokens[0].chunked(2).map { it.toInt(16) })
                }
                continue
            }
            val hit = errorWords.firstOrNull { up.contains(it) }
            if (hit != null) error = hit
        }

        val frames = ArrayList<Frame>()
        for ((h, list) in headered) for (f in assemble(list)) frames.add(Frame(h, f))
        for (p in plain) frames.add(Frame("", ByteArray(p.size) { p[it].toByte() }))
        if (sawNumbered && numbered.isNotEmpty()) frames.add(Frame("", ByteArray(numbered.size) { numbered[it].toByte() }))
        return ElmReply(raw, frames, if (frames.isEmpty()) (error ?: if (lines.isEmpty()) "NO REPLY" else null) else null)
    }

    /**
     * Turns the raw CAN frames of ONE ECU into complete messages. With headers on the ELM shows the ISO-TP length byte(s):
     * 0x0N = single frame of N bytes, 0x1L LL = first frame of a long message, 0x2n = continuation.
     */
    fun assemble(rows: List<List<Int>>): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var buf = ArrayList<Int>()
        var expect = 0
        fun flush() {
            if (buf.isNotEmpty()) {
                val n = if (expect > 0) minOf(expect, buf.size) else buf.size
                out.add(ByteArray(n) { buf[it].toByte() })
            }
            buf = ArrayList(); expect = 0
        }
        for (r in rows) {
            if (r.isEmpty()) continue
            val t = r[0] shr 4
            val n = r[0] and 0x0F
            when {
                t == 0 && n in 1..7 && n <= r.size - 1 -> { flush(); out.add(ByteArray(n) { r[1 + it].toByte() }) }
                t == 1 && r.size >= 2 -> { flush(); expect = (n shl 8) or r[1]; buf.addAll(r.drop(2)) }
                t == 2 && expect > 0 -> { buf.addAll(r.drop(1)) }
                else -> { flush(); out.add(ByteArray(r.size) { r[it].toByte() }) }   // already without length byte
            }
        }
        flush()
        return out
    }

    fun hexToBytes(s: String): ByteArray {
        val t = s.replace(Regex("[^0-9A-Fa-f]"), "")
        return ByteArray(t.length / 2) { t.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

/** The text of the answer for a "Mode 09 PID 02" request: 17 characters. */
object Vin {
    fun decode(f: Frame?): String? {
        if (f == null || f.data.size < 4 || f.u(0) != 0x49 || f.u(1) != 0x02) return null
        val chars = f.data.drop(3).filter { it.toInt() in 0x30..0x5A }.map { it.toInt().toChar() }.joinToString("")
        return chars.takeLast(17).ifEmpty { null }
    }

    /** Model year from the 10th character. 2013 = D (letters start at 2010, digits are 2001 to 2009). */
    fun modelYear(vin: String): Int? {
        if (vin.length != 17) return null
        val letters = "ABCDEFGHJKLMNPRSTVWXY"
        val c = vin[9]
        val i = letters.indexOf(c)
        if (i >= 0) return 2010 + i
        if (c in '1'..'9') return 2000 + (c - '0')
        return null
    }
}
