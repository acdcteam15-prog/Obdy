package com.example.obdy

/** One line of the car health score: what it is, how many points it takes away, and a short reason. */
class HealthFactor(val name: String, val penalty: Int, val text: String, val level: Level)
class CarHealth(val score: Int, val level: Level, val headline: String, val factors: List<HealthFactor>)

/**
 * One 0..100 score for the Home screen, built only from data Obdy really has: codes (and how serious), warning light,
 * battery health, last charging reading, service due, readiness, self-test failures. Nothing known = factor left out.
 */
object CarHealthScore {
    fun compute(
        faults: List<Fault>, milOn: Boolean?, battery: BatteryHealth?, charging: Double?, due: List<Due>,
        monitors: MonitorStatus?, testsFailed: Int, scanned: Boolean,
    ): CarHealth {
        val f = ArrayList<HealthFactor>()
        if (scanned) {
            val active = faults.filter { it.kind != "history" && it.kind != "permanent" }
            val urgent = active.count { DtcDb.severity(it.code) == 2 }
            val other = active.size - urgent
            val p = minOf(45, urgent * 18 + other * 8 + faults.count { it.kind == "permanent" } * 3)
            f.add(HealthFactor("Fault codes", p,
                if (faults.isEmpty()) "No codes in the modules read" else "${faults.size} code(s), $urgent urgent",
                if (p == 0) Level.Good else if (urgent > 0) Level.Bad else Level.Warn))
            if (milOn == true) f.add(HealthFactor("Warning light", 15, "Check-engine light is ON", Level.Bad))
        } else f.add(HealthFactor("Fault codes", 0, "Not scanned yet", Level.Info))
        battery?.let { b -> f.add(HealthFactor("Battery", ((100 - b.score) / 4).coerceIn(0, 20), b.summary, b.level)) }
        charging?.let { v ->
            val (p, l, t) = when {
                v in 13.2..14.9 -> Triple(0, Level.Good, "Charging ${Calc.fmt(v, 2)} V")
                v < 12.8 -> Triple(15, Level.Bad, "Only ${Calc.fmt(v, 2)} V with the engine running")
                else -> Triple(6, Level.Warn, "Charging ${Calc.fmt(v, 2)} V (borderline)")
            }
            f.add(HealthFactor("Charging", p, t, l))
        }
        val overdue = due.count { it.overdue }; val soon = due.count { it.soon }
        if (due.any { it.recorded }) f.add(HealthFactor("Service", minOf(15, overdue * 6 + soon * 2),
            if (overdue + soon == 0) "Nothing due" else "$overdue overdue, $soon soon", if (overdue > 0) Level.Bad else if (soon > 0) Level.Warn else Level.Good))
        monitors?.let { m ->
            val sup = m.monitors.filter { it.supported }; val open = sup.count { !it.complete }
            f.add(HealthFactor("Self-tests (readiness)", minOf(6, open * 2), "${sup.size - open} of ${sup.size} finished", if (open == 0) Level.Good else Level.Info))
        }
        if (testsFailed > 0) f.add(HealthFactor("Engine self-test results", minOf(12, testsFailed * 4), "$testsFailed test(s) outside limits (Mode 06)", Level.Warn))
        val score = (100 - f.sumOf { it.penalty }).coerceIn(0, 100)
        val level = when { score >= 85 -> Level.Good; score >= 60 -> Level.Warn; else -> Level.Bad }
        val head = when { !scanned -> "Scan the car for a full score"; score >= 85 -> "Car is in good shape"; score >= 60 -> "A few things to look at"; else -> "Needs attention" }
        return CarHealth(score, level, head, f.sortedByDescending { it.penalty })
    }
}

/** The story of one code across all saved scans. */
class CodeStory(
    val code: String, val module: String, val firstMs: Long, val firstKm: Int, val lastMs: Long, val seen: Int,
    val returns: Int, val nowPresent: Boolean, val events: List<Triple<Long, Int, String>>,
)

object CodeTimeline {
    /** [scans] in any order. A "return" = the code was missing from at least one scan and then came back. */
    fun build(scans: List<ScanRecord>): List<CodeStory> {
        val sorted = scans.sortedBy { it.timeMs }
        if (sorted.isEmpty()) return emptyList()
        val keys = sorted.flatMap { s -> s.faults.map { it.code.take(5) to it.module } }.distinct()
        return keys.map { (code, module) ->
            val ev = ArrayList<Triple<Long, Int, String>>()
            var present = false; var seen = 0; var returns = 0; var first: ScanRecord? = null; var last: ScanRecord? = null; var everGone = false
            for (s in sorted) {
                val has = s.faults.any { it.code.take(5) == code && it.module == module }
                // A quick scan (no module notes) cannot see module codes: do not count it as "gone".
                val couldSee = module.startsWith("Engine") || module.startsWith("Transmission") || s.notes.contains("Modules that answered")
                if (has) {
                    seen++; last = s; if (first == null) first = s
                    if (!present) { if (everGone) { returns++; ev.add(Triple(s.timeMs, s.odometerKm, "came back")) } else ev.add(Triple(s.timeMs, s.odometerKm, "first seen")) }
                    present = true
                } else if (present && couldSee) { present = false; everGone = true; ev.add(Triple(s.timeMs, s.odometerKm, "gone")) }
            }
            CodeStory(code, module, first!!.timeMs, first.odometerKm, last!!.timeMs, seen, returns, present, ev)
        }.sortedByDescending { it.lastMs }
    }
}

/**
 * Looks for the odometer inside module answers: any number in a positive 22/1A answer that matches the known mileage
 * (as km, km/10, km/100, km/1000 or miles) within 3 % is a candidate. Read only; the owner confirms before it is used.
 */
class OdoCandidate(val module: String, val req: String, val resp: String, val request: String, val offset: Int, val len: Int, val scale: Double, val miles: Boolean, val value: Double)

object OdoFinder {
    /** Read-only requests worth trying in each module (GM data identifiers seen on GMLAN cars; most will be refused). */
    val requests = listOf("22000D", "220030", "2200A6", "22131F", "221981", "22199C", "22F40D", "22F4A6", "224002", "22002E", "2201A6", "1AA6", "1ACA")

    fun candidates(module: String, req: String, resp: String, request: String, data: IntArray, knownKm: Int): List<OdoCandidate> {
        if (knownKm <= 0) return emptyList()
        val out = ArrayList<OdoCandidate>()
        for (len in listOf(3, 4, 2)) for (o in 0..data.size - len) {
            var raw = 0L
            for (i in 0 until len) raw = (raw shl 8) or data[o + i].toLong()
            if (raw <= 0) continue
            for (sc in listOf(1.0, 0.1, 0.01, 0.001, 0.125)) for (mi in listOf(false, true)) {
                val km = raw * sc * (if (mi) 1.609344 else 1.0)
                if (Math.abs(km - knownKm) <= knownKm * 0.03) out.add(OdoCandidate(module, req, resp, request, o, len, sc, mi, km))
            }
        }
        return out.distinctBy { "${it.offset}/${it.len}" }
    }

    fun decode(c: OdoCandidate, data: IntArray): Double? {
        if (c.offset + c.len > data.size) return null
        var raw = 0L
        for (i in 0 until c.len) raw = (raw shl 8) or data[c.offset + i].toLong()
        return raw * c.scale * (if (c.miles) 1.609344 else 1.0)
    }
}

/** How to finish each readiness self-test with normal driving (GM-style drive cycle, simplified). */
object DriveCycle {
    fun tip(name: String): String {
        val n = name.lowercase()
        return when {
            "heater" in n -> "Runs by itself within a few minutes after a cold start: start the car after it stood overnight and idle 2 minutes."
            "oxygen" in n || "o2" in n -> "After the engine is warm, drive steadily at 60–90 km/h for 5 minutes, then lift off and coast down without braking for 10 seconds. Repeat twice."
            "catalyst" in n -> "Warm engine, steady 70–90 km/h for 5–10 minutes on a flat road (light throttle, no stops)."
            "evap" in n -> "Needs a cold start after the car stood 8 hours or more, fuel tank between 1/4 and 3/4, outside temperature 4–35 °C. Then drive normally for 20 minutes including some idling."
            "egr" in n -> "Warm engine, several gentle decelerations from 80 to 50 km/h without braking."
            "misfire" in n || "fuel" in n || "component" in n -> "These run all the time while driving; a normal 15-minute mixed drive completes them."
            else -> "Usually completes after a few days of mixed driving (town + 10 minutes steady road speed)."
        }
    }
}
