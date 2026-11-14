package com.example.obdy

import java.util.Locale

/** What the owner wants Claude to look at. */
class LabGoals(val diagnose: Boolean = true, val bugs: Boolean = true, val ideas: Boolean = true, val discover: Boolean = true)

/** One row of the live-data trail: seconds since the first row, and the raw values the car sent (before the app hides "no signal" numbers). */
class LiveSample(val tSec: Double, val values: Map<Int, Double>)

/**
 * The "Claude Lab" bundle: everything Obdy knows about one session, as text, plus the instructions for the AI.
 * Pure text building, no Android classes, so it is easy to test.
 */
object Dossier {
    const val NOTES_START = "=== DEVELOPER NOTES ==="
    const val NOTES_END = "=== END DEVELOPER NOTES ==="
    private const val MAX_CHARS = 110_000

    fun instructions(goals: LabGoals, appVersion: String): String {
        val sb = StringBuilder()
        sb.append("You are helping the owner of a 2013 Chevrolet Malibu LT (GCC, 2.5 L, automatic, UAE) improve and use his own Android OBD-II app called Obdy ($appVersion). ")
        sb.append("Obdy talks to an ELM327-style adapter over CAN (ISO 15765-4, 11 bit, 500 kbit). Below is a session bundle written by the app: car and adapter, ")
        sb.append("live values (RAW as the car sent them), a short live-data trail, faults, readiness, self-tests (Mode 06), module scan notes, an optional discovery probe, ")
        sb.append("and the raw adapter log (lines starting >> are what the app sent, << what the car answered; 7E8 = engine, 7EA = transmission, other ids are other modules). ")
        sb.append("Work only from the data in the bundle. Quote the log line that supports every claim. Say clearly when something is a guess. Do not invent readings.\n\n")
        sb.append("Answer in plain text with these sections, short and concrete:\n")
        var n = 1
        if (goals.diagnose) sb.append("${n++}. CAR: what the data says about the car's health. Most likely causes ranked with evidence, what to check first (cheap steps first), is it safe to drive, what to avoid.\n")
        if (goals.bugs) sb.append("${n++}. DATA PROBLEMS IN THE APP: any value the app shows or decodes wrongly or that the car sends as 'not available' (for example 255 km/h speed, 0 V voltage), wrong units or scaling, parsing mistakes, requests that got no answer, anything the log shows that the app ignores. Give the raw log line and the correct decoding.\n")
        if (goals.ideas) sb.append("${n++}. IDEAS FOR THE APP: new screens, tests, checks, warnings or automations that would be useful for this car, ranked by value for effort. Think reading, fixing/repair help, maintenance, trip use, and making the numbers easier to understand.\n")
        if (goals.discover) sb.append("${n++}. WHAT ELSE THIS CAR CAN TELL US: from the discovery probe and the module answers, which modules, standard values, Mode 09 items, Mode 06 tests or manufacturer (Mode 22 / GMLAN) requests exist and are worth adding. Separate what the log proves from what is only likely for a GM Epsilon-II car, and list the exact request to try next (read-only requests only: never suggest anything that writes, clears, actuates, programs or changes a setting).\n")
        sb.append("${n}. DEVELOPER NOTES: end with exactly this block so the owner can paste it to the developer. One line per item, format `- [bug|decode|idea|test|probe] short title: detail and evidence`, at most 15 lines, most important first:\n")
        sb.append(NOTES_START).append("\n- ...\n").append(NOTES_END).append("\n")
        return sb.toString()
    }

    /** The live trail as CSV (seconds, then one column per value). */
    fun trailCsv(trail: List<LiveSample>): String {
        if (trail.isEmpty()) return "(no live data was collected: open the Live screen with the car connected first)"
        val ids = trail.flatMap { it.values.keys }.distinct().sorted()
        val sb = StringBuilder("t_s")
        for (id in ids) { val p = Pids.byId(id); sb.append(",").append(p?.let { it.name + " [" + it.unit + "]" } ?: "PID %02X".format(id)) }
        sb.append("\n")
        for (s in trail) {
            sb.append(String.format(Locale.US, "%.1f", s.tSec))
            for (id in ids) { sb.append(","); s.values[id]?.let { sb.append(String.format(Locale.US, "%.2f", it)) } }
            sb.append("\n")
        }
        return sb.toString().trimEnd()
    }

    /** Decoded Mode 06 records, one per line, with the raw numbers so the decoding can be checked. */
    fun testsText(tests: List<TestResult>): String {
        if (tests.isEmpty()) return "(self-tests not read: open Engine self-tests and press Read the tests)"
        return tests.joinToString("\n") {
            "MID %02X %s | TID %02X | UAS %02X | value %d | min %d | max %d | shown: %s (%s to %s) | %s".format(
                it.mid, it.name, it.tid, it.uas, it.value, it.min, it.max, it.fmt(it.value), it.fmt(it.min), it.fmt(it.max), if (it.pass) "inside limits" else "OUTSIDE limits")
        }
    }

    /**
     * Puts the instructions and the sections together. When the bundle is too long the OLDEST lines of the long sections
     * (names starting with "LOG") are cut first, so the facts are never lost.
     */
    fun assemble(goals: LabGoals, appVersion: String, sections: List<Pair<String, String>>, maxChars: Int = MAX_CHARS): String {
        val head = instructions(goals, appVersion)
        val secs = sections.filter { it.second.isNotBlank() }.map { it.first to it.second }.toMutableList()
        fun total() = head.length + secs.sumOf { it.first.length + it.second.length + 12 }
        var guard = 0
        while (total() > maxChars && guard++ < 40) {
            val i = secs.indices.filter { secs[it].first.startsWith("LOG") }.maxByOrNull { secs[it].second.length } ?: break
            val lines = secs[i].second.lines()
            if (lines.size <= 20) break
            val cut = (lines.size / 4).coerceAtLeast(10)
            secs[i] = secs[i].first.replace(Regex(" \\(oldest .*\\)$"), "") + " (oldest lines cut)" to lines.drop(cut).joinToString("\n")
        }
        val sb = StringBuilder(head).append("\n")
        for ((t, b) in secs) sb.append("\n##### ").append(t).append("\n").append(b.trimEnd()).append("\n")
        return sb.toString()
    }

    /** The block between the markers (or null). */
    fun extractNotes(reply: String): String? {
        val a = reply.lastIndexOf(NOTES_START)
        if (a < 0) return null
        val from = a + NOTES_START.length
        val b = reply.indexOf(NOTES_END, from)
        val body = (if (b >= 0) reply.substring(from, b) else reply.substring(from)).trim()
        return body.ifBlank { null }
    }
}
