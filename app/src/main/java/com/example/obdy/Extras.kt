package com.example.obdy

import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

// ====================================================================================== Mode 06: the car's own self-test results
class TestResult(val mid: Int, val tid: Int, val uas: Int, val value: Int, val min: Int, val max: Int) {
    val pass: Boolean get() = value in min..max
    val name: String get() = Mode06.midName(mid)
    val what: String get() = when (tid) { 0x0B -> "average of the last 10 drives"; 0x0C -> "this drive"; else -> "test ${"%02X".format(tid)}" }
}

object Mode06 {
    fun midName(mid: Int): String = when {
        mid == 0x01 -> "Oxygen sensor B1S1"
        mid == 0x02 -> "Oxygen sensor B1S2"
        mid == 0x21 -> "Catalyst bank 1"
        mid == 0x22 -> "Catalyst bank 2"
        mid == 0x31 -> "EGR system"
        mid == 0x39 -> "EVAP leak test 0.150 in"
        mid == 0x3A -> "EVAP leak test 0.090 in"
        mid == 0x3B -> "EVAP leak test 0.040 in"
        mid == 0x3C -> "EVAP leak test 0.020 in"
        mid == 0x41 -> "Oxygen sensor heater B1S1"
        mid == 0x42 -> "Oxygen sensor heater B1S2"
        mid == 0xA1 -> "Misfire, all cylinders"
        mid in 0xA2..0xAD -> "Misfire, cylinder ${mid - 0xA1}"
        else -> "Test group %02X".format(mid)
    }

    /** 46 <MID> then groups of 8 bytes: TID, scaling, value(2), min(2), max(2). */
    fun parse(f: Frame): List<TestResult> {
        if (f.data.size < 10 || f.u(0) != 0x46) return emptyList()
        val mid = f.u(1)
        val out = ArrayList<TestResult>()
        var i = 2
        while (i + 7 < f.data.size) {
            out.add(TestResult(mid, f.u(i), f.u(i + 1), (f.u(i + 2) shl 8) or f.u(i + 3), (f.u(i + 4) shl 8) or f.u(i + 5), (f.u(i + 6) shl 8) or f.u(i + 7)))
            i += 8
        }
        return out
    }

    /** Answer to 0600, 0620 ...: 46 <base> and a 4-byte mask. */
    fun supported(f: Frame): Set<Int> {
        if (f.data.size < 6 || f.u(0) != 0x46) return emptySet()
        val base = f.u(1)
        val out = HashSet<Int>()
        for (i in 0 until 32) if (((f.u(2 + i / 8) shr (7 - i % 8)) and 1) == 1) out.add(base + 1 + i)
        return out
    }
}

// ====================================================================================== Inspection (registration test) pre-check
object Inspection {
    fun tip(monitor: String): String = when (monitor) {
        "Catalyst", "Heated catalyst" -> "Drive 5 to 10 minutes at a steady 80 to 90 km/h after the engine is fully warm."
        "Oxygen sensor", "Oxygen sensor heater" -> "Cold start, idle 2 minutes, then cruise steadily for 5 minutes."
        "Evaporative system" -> "Let the car sit 8 hours or more, fuel between a quarter and three quarters, then start cold and drive 10 to 15 minutes with varied speed."
        "EGR system" -> "Mixed driving including gentle acceleration and coasting."
        "A/C refrigerant" -> "Run the A/C on cold for 2 minutes."
        "Secondary air" -> "Start the engine cold and let it idle."
        else -> "Normal mixed driving for a few days."
    }

    fun evaluate(mil: Boolean?, faults: List<Fault>, monitors: MonitorStatus?, warmups: Int?, distKm: Int?): List<Verdict> {
        val out = ArrayList<Verdict>()
        if (mil == null && monitors == null) { out.add(Verdict("No scan yet", "-", Level.Info, "Run a scan first (Faults tab).")); return out }
        val milOn = mil ?: monitors?.milOn ?: false
        out.add(if (milOn) Verdict("Warning light", "ON", Level.Bad, "The check-engine light is on: the car will not pass until the cause is fixed.") else Verdict("Warning light", "off", Level.Good, "Off."))
        val engine = faults.filter { it.code.startsWith("P") }
        val perm = engine.filter { it.kind == "permanent" }
        val stored = engine.filter { it.kind == "stored" }
        val pending = engine.filter { it.kind == "pending" }
        out.add(when {
            perm.isNotEmpty() -> Verdict("Permanent codes", perm.joinToString { it.code }, Level.Bad, "Permanent codes only clear after the repair is verified by the car's own tests; clearing them with a scanner does not work.")
            stored.isNotEmpty() -> Verdict("Stored codes", stored.joinToString { it.code }, Level.Bad, "Fix the cause, clear the codes, then drive until the monitors finish.")
            pending.isNotEmpty() -> Verdict("Pending codes", pending.joinToString { it.code }, Level.Warn, "A problem was seen once and may become a stored code.")
            else -> Verdict("Engine codes", "none", Level.Good, "No engine or transmission codes.")
        })
        if (monitors != null) {
            val inc = monitors.monitors.filter { it.supported && !it.complete }
            out.add(when {
                inc.isEmpty() -> Verdict("Readiness monitors", "all complete", Level.Good, "The car has finished its self-tests.")
                inc.size == 1 -> Verdict("Readiness monitors", "1 not complete", Level.Warn, "${inc[0].name}: " + tip(inc[0].name) + " Many tests accept one incomplete monitor, but not all.")
                else -> Verdict("Readiness monitors", "${inc.size} not complete", Level.Bad, "Not ready yet: " + inc.joinToString(" ") { "${it.name}: ${tip(it.name)}" })
            })
        }
        if (distKm != null || warmups != null) {
            out.add(Verdict("Since codes were cleared", listOfNotNull(distKm?.let { "$it km" }, warmups?.let { "$it warm-ups" }).joinToString(", "), Level.Info, "Monitors usually need about 50 km of mixed driving after a clear or a battery disconnect."))
        }
        out.add(Verdict("Remember", "-", Level.Info, "This is a pre-check. Test rules differ by place, so ask the test centre what they read."))
        return out
    }
}

// ====================================================================================== Health check (idle test) and trends
object HealthCheck {
    val pids = listOf(0x0C, 0x05, 0x06, 0x07, 0x14, 0x42, 0x10, 0x04, 0x0B, 0x0F)

    private fun mean(l: List<Double>) = if (l.isEmpty()) Double.NaN else l.average()
    private fun sd(l: List<Double>): Double { if (l.size < 2) return 0.0; val m = l.average(); return sqrt(l.sumOf { (it - m) * (it - m) } / (l.size - 1)) }

    /** How many times the signal crosses the middle (0.45 V) with a small margin: a healthy sensor swings often. */
    fun crossings(l: List<Double>): Int {
        var state = 0; var n = 0
        for (v in l) {
            val s = if (v > 0.5) 1 else if (v < 0.4) -1 else 0
            if (s != 0) { if (state != 0 && s != state) n++; state = s }
        }
        return n
    }

    fun evaluate(s: Map<Int, List<Double>>): List<Verdict> {
        val out = ArrayList<Verdict>()
        val rpm = s[0x0C] ?: emptyList()
        if (rpm.size < 5) return listOf(Verdict("Not enough data", "-", Level.Info, "No readings came back. Is the ignition on and the engine running?"))
        val rm = mean(rpm)
        if (rm < 400) return listOf(Verdict("Engine not running", "${Calc.fmt(rm, 0)} rpm", Level.Info, "Start the engine, let it idle, and run the check again."))
        val deviation = sd(rpm)
        out.add(when {
            rm < 550 || rm > 1000 -> Verdict("Idle speed", "${Calc.fmt(rm, 0)} rpm", Level.Warn, "Outside the usual 600 to 900 rpm when warm. Cold engines idle higher; also check throttle body and vacuum leaks.")
            deviation < 25 -> Verdict("Idle stability", "±${Calc.fmt(deviation, 0)} rpm", Level.Good, "Steady idle.")
            deviation < 50 -> Verdict("Idle stability", "±${Calc.fmt(deviation, 0)} rpm", Level.Warn, "Slightly unsteady: early sign of a misfire, dirty throttle body or vacuum leak.")
            else -> Verdict("Idle stability", "±${Calc.fmt(deviation, 0)} rpm", Level.Bad, "Rough idle: check plugs, coils, vacuum leaks and the throttle body.")
        })
        val cool = s[0x05] ?: emptyList()
        if (cool.isNotEmpty()) {
            val c = cool.last()
            out.add(when {
                c >= 108 -> Verdict("Coolant", "${Calc.fmt(c, 0)} °C", Level.Bad, "Too hot: stop, check the coolant level and the fans.")
                c >= 100 -> Verdict("Coolant", "${Calc.fmt(c, 0)} °C", Level.Warn, "High for idle: check the fans and the coolant level.")
                c >= 80 -> Verdict("Coolant", "${Calc.fmt(c, 0)} °C", Level.Good, "Warm and in the normal range.")
                else -> Verdict("Coolant", "${Calc.fmt(c, 0)} °C", Level.Info, "Not fully warm yet: results that depend on warm-up (fuel trims, oxygen sensor) may be unreliable. Warm up and retest.")
            })
        }
        val lt = s[0x07] ?: emptyList()
        val st = s[0x06] ?: emptyList()
        if (lt.isNotEmpty()) {
            val l = mean(lt)
            out.add(when {
                abs(l) <= 5 -> Verdict("Long fuel trim", "${Calc.fmt(l, 1)} %", Level.Good, "The engine is not compensating.")
                abs(l) <= 10 -> Verdict("Long fuel trim", "${Calc.fmt(l, 1)} %", Level.Warn, if (l > 0) "Adding fuel: slight lean condition (air leak, dirty MAF, weak fuel pressure)." else "Removing fuel: slight rich condition (leaking injector, purge valve, MAF).")
                else -> Verdict("Long fuel trim", "${Calc.fmt(l, 1)} %", Level.Bad, if (l > 0) "Strongly lean: check for vacuum or intake leaks, MAF sensor and fuel pressure." else "Strongly rich: check injectors, purge valve, MAF and fuel pressure.")
            })
        }
        if (st.isNotEmpty()) out.add(Verdict("Short fuel trim", "${Calc.fmt(mean(st), 1)} % (swing ±${Calc.fmt(sd(st), 1)})", if (abs(mean(st)) > 10) Level.Warn else Level.Good, "Moves up and down around zero when the oxygen sensor and mixture are healthy."))
        val o2 = s[0x14] ?: emptyList()
        if (o2.size >= 10) {
            val n = crossings(o2)
            out.add(when {
                n >= 6 -> Verdict("Oxygen sensor B1S1", "$n swings", Level.Good, "Switching actively.")
                n >= 2 -> Verdict("Oxygen sensor B1S1", "$n swings", Level.Warn, "Slow to switch: ageing sensor, or the engine is not in closed loop yet.")
                else -> Verdict("Oxygen sensor B1S1", "$n swings", Level.Bad, "Not switching: sensor stuck, or the car is still in open loop (not warm). Warm up and retest.")
            })
        }
        val v = s[0x42] ?: emptyList()
        if (v.isNotEmpty()) {
            val m = mean(v)
            out.add(when {
                m in 13.2..14.9 -> Verdict("Charging voltage", "${Calc.fmt(m, 2)} V", Level.Good, "Alternator is charging.")
                m < 13.0 -> Verdict("Charging voltage", "${Calc.fmt(m, 2)} V", Level.Bad, "Not charging enough: alternator, belt or wiring. Run the Battery test.")
                m > 15.0 -> Verdict("Charging voltage", "${Calc.fmt(m, 2)} V", Level.Bad, "Overcharging: faulty regulator.")
                else -> Verdict("Charging voltage", "${Calc.fmt(m, 2)} V", Level.Warn, "Slightly outside the normal range. Retest with lights and A/C off.")
            })
        }
        val maf = s[0x10] ?: emptyList()
        if (maf.isNotEmpty()) {
            val m = mean(maf)
            out.add(if (m in 1.5..6.5) Verdict("Air flow at idle", "${Calc.fmt(m, 1)} g/s", Level.Good, "Plausible for a 2 to 2.5 litre engine.") else Verdict("Air flow at idle", "${Calc.fmt(m, 1)} g/s", Level.Warn, "Unusual for idle: dirty MAF sensor, air leak, or a high idle."))
        }
        val map = s[0x0B] ?: emptyList()
        if (map.isNotEmpty()) {
            val m = mean(map)
            out.add(if (m in 22.0..50.0) Verdict("Manifold pressure at idle", "${Calc.fmt(m, 0)} kPa", Level.Good, "Normal vacuum.") else Verdict("Manifold pressure at idle", "${Calc.fmt(m, 0)} kPa", Level.Warn, if (m > 50) "Weak vacuum: vacuum leak, or a worn engine." else "Unusually strong vacuum: restricted intake."))
        }
        return out
    }

    fun overall(v: List<Verdict>): Level = when {
        v.any { it.level == Level.Bad } -> Level.Bad
        v.any { it.level == Level.Warn } -> Level.Warn
        v.any { it.level == Level.Good } -> Level.Good
        else -> Level.Info
    }

    /** One number per measurement, kept for the trend graphs. */
    fun summary(s: Map<Int, List<Double>>): Map<String, Double> {
        val m = LinkedHashMap<String, Double>()
        fun put(k: String, id: Int) { val l = s[id]; if (!l.isNullOrEmpty()) m[k] = l.average() }
        put("rpm", 0x0C); put("coolant", 0x05); put("stft", 0x06); put("ltft", 0x07); put("volt", 0x42); put("maf", 0x10); put("map", 0x0B)
        return m
    }
}

class HealthRecord(val timeMs: Long, val values: Map<String, Double>)

object Trends {
    val labels = mapOf("volt" to "Charging voltage (V)", "ltft" to "Long fuel trim (%)", "stft" to "Short fuel trim (%)", "rpm" to "Idle speed (rpm)", "coolant" to "Coolant (°C)", "maf" to "Air flow at idle (g/s)", "map" to "Manifold pressure (kPa)")

    fun series(list: List<HealthRecord>, key: String): List<Pair<Long, Double>> =
        list.sortedBy { it.timeMs }.mapNotNull { r -> r.values[key]?.let { r.timeMs to it } }

    /** A plain warning when the recent readings moved away from the earlier ones. */
    fun drift(list: List<HealthRecord>, key: String): String? {
        val s = series(list, key).map { it.second }
        if (s.size < 4) return null
        val recent = s.takeLast(2).average()
        val before = s.dropLast(2).average()
        return when (key) {
            "volt" -> if (before - recent >= 0.3) "Charging voltage is falling (${Calc.fmt(before, 2)} V to ${Calc.fmt(recent, 2)} V): test the battery and alternator." else null
            "ltft" -> if (abs(recent) - abs(before) >= 3) "Long fuel trim is drifting away from zero (${Calc.fmt(before, 1)} to ${Calc.fmt(recent, 1)} %): a leak or sensor is getting worse." else null
            "rpm" -> if (abs(recent - before) >= 100) "Idle speed has changed by about ${Calc.fmt(abs(recent - before), 0)} rpm: check the throttle body and vacuum hoses." else null
            "map" -> if (recent - before >= 5) "Idle vacuum is getting weaker: look for a vacuum leak." else null
            else -> null
        }
    }
}

// ====================================================================================== Repairs, expenses, documents
class RepairRecord(val id: String, val timeMs: Long, val title: String, val category: String, val cost: Double, val km: Int, val workshop: String, val notes: String, val receipt: String)

object Expenses {
    val categories = listOf("Service", "Repair", "Parts", "Tyres", "Fuel", "Insurance", "Registration", "Other")

    fun byCategory(list: List<RepairRecord>): List<Pair<String, Double>> =
        list.groupBy { it.category }.map { it.key to it.value.sumOf { r -> r.cost } }.sortedByDescending { it.second }

    fun total(list: List<RepairRecord>) = list.sumOf { it.cost }
}

class DocReminder(val id: String, val name: String, val dueMs: Long) {
    fun daysLeft(nowMs: Long): Int = Math.floorDiv(dueMs - nowMs, 86_400_000L).toInt()
}

object Docs {
    /** "2027-03-15" to milliseconds (UTC, noon so every time zone shows the same day). Null when it is not a date. */
    fun parseDate(s: String): Long? {
        val p = s.trim().split('-')
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null; val m = p[1].toIntOrNull() ?: return null; val d = p[2].toIntOrNull() ?: return null
        if (y !in 2000..2100 || m !in 1..12 || d !in 1..31) return null
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        c.clear(); c.set(y, m - 1, d, 12, 0, 0)
        return c.timeInMillis
    }

    fun formatDate(ms: Long): String {
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); c.timeInMillis = ms
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }

    fun defaults(): List<DocReminder> = listOf(DocReminder("registration", "Car registration", 0L), DocReminder("insurance", "Insurance", 0L))
}

// ====================================================================================== Route file
class RoutePoint(val lat: Double, val lon: Double, val timeMs: Long, val speedMs: Double)

object Gpx {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun build(name: String, points: List<RoutePoint>): String {
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Obdy\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <trk><name>").append(esc(name)).append("</name><trkseg>\n")
        for (p in points) {
            sb.append(String.format(Locale.US, "    <trkpt lat=\"%.6f\" lon=\"%.6f\"><time>%s</time><extensions><speed>%.1f</speed></extensions></trkpt>\n", p.lat, p.lon, iso.format(java.util.Date(p.timeMs)), p.speedMs))
        }
        sb.append("  </trkseg></trk>\n</gpx>\n")
        return sb.toString()
    }

    fun distanceKm(points: List<RoutePoint>): Double {
        var d = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]; val b = points[i]
            val r = 6371.0
            val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
            val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
            d += 2 * r * Math.asin(Math.sqrt(h))
        }
        return d
    }
}
