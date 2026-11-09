package com.example.obdy

import java.util.Locale

object Calc {
    private const val GASOLINE_G_PER_L = 745.0

    /** Fuel use in litres per hour from the air flow (grams/second) and the commanded air/fuel ratio (lambda, 1.0 = ideal). */
    fun fuelLitersPerHour(mafGs: Double, lambda: Double = 1.0): Double {
        val lam = if (lambda in 0.5..2.0) lambda else 1.0
        return mafGs * 3600.0 / (14.7 * lam * GASOLINE_G_PER_L)
    }

    fun fmt(v: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f", v)
}

/** Adds up one drive: distance, fuel, time, idle time, and how smoothly it was driven. Call [add] every time new live values arrive. */
class TripAcc {
    var seconds = 0.0; private set
    var km = 0.0; private set
    var liters = 0.0; private set
    var idleSeconds = 0.0; private set
    var maxKmh = 0.0; private set
    var hardAccel = 0; private set
    var hardBrake = 0; private set
    var highRpmSeconds = 0.0; private set
    private var prevSpeed: Double? = null

    fun add(dtSeconds: Double, speedKmh: Double, litersPerHour: Double, rpm: Double? = null) {
        if (dtSeconds <= 0 || dtSeconds > 10) { prevSpeed = null; return }          // a gap (connection lost): do not invent distance
        seconds += dtSeconds
        km += speedKmh * dtSeconds / 3600.0
        liters += litersPerHour * dtSeconds / 3600.0
        if (speedKmh < 1.0) idleSeconds += dtSeconds
        if (speedKmh > maxKmh) maxKmh = speedKmh
        if (rpm != null && rpm > 4500) highRpmSeconds += dtSeconds
        val p = prevSpeed
        if (p != null && dtSeconds <= 3.0) {
            val a = (speedKmh - p) / 3.6 / dtSeconds           // m/s^2
            if (a > 2.8) hardAccel++ else if (a < -3.2) hardBrake++
        }
        prevSpeed = speedKmh
    }

    val avgKmh: Double get() = if (seconds > 0) km / (seconds / 3600.0) else 0.0
    val litersPer100: Double? get() = if (km >= 0.2) liters / km * 100.0 else null
    val kmPerLiter: Double? get() = if (liters > 0.01 && km >= 0.2) km / liters else null

    /** 100 = smooth. Null until the drive is long enough to judge (3 km). */
    val score: Int? get() {
        if (km < 3.0 || seconds < 120) return null
        val events = hardAccel + hardBrake * 1.5
        var penalty = minOf(50.0, events / km * 8.0)
        penalty += minOf(20.0, highRpmSeconds / seconds * 100.0 * 0.5)
        val idleFrac = idleSeconds / seconds
        if (idleFrac > 0.3) penalty += minOf(10.0, (idleFrac - 0.3) * 20.0)
        return (100 - penalty).toInt().coerceIn(0, 100)
    }

    fun tip(): String {
        val s = score ?: return "Drive at least 3 km for a score."
        val kmv = maxOf(km, 1.0)
        return when {
            hardBrake / kmv > 0.6 -> "Hard braking is the main cost: look further ahead and ease off early."
            hardAccel / kmv > 0.6 -> "Quick starts use fuel and wear parts: accelerate gently."
            highRpmSeconds / seconds > 0.1 -> "The engine spent a lot of time above 4,500 rpm: shift up earlier or ease the pedal."
            idleSeconds / seconds > 0.4 -> "Much of the time was idling: switch off when waiting longer than a minute (where safe)."
            s >= 85 -> "Smooth driving. Good for fuel, brakes and tyres."
            else -> "A little smoother braking and acceleration would save fuel."
        }
    }
}

enum class Level { Good, Warn, Bad, Info }
class Verdict(val title: String, val value: String, val level: Level, val text: String)

object BatteryTest {
    /** Engine off voltage, lowest voltage while cranking, voltage at idle and at about 2000 rpm. Any of them can be missing. */
    fun evaluate(rest: Double?, crankMin: Double?, idle: Double?, rev: Double?): List<Verdict> {
        val out = ArrayList<Verdict>()
        if (rest != null) {
            val v = Calc.fmt(rest, 2) + " V"
            out.add(
                when {
                    rest >= 12.5 -> Verdict("Resting voltage", v, Level.Good, "Battery is well charged.")
                    rest >= 12.2 -> Verdict("Resting voltage", v, Level.Warn, "Partly discharged. Short trips, a parasitic drain or an ageing battery. Drive 30 minutes and test again.")
                    else -> Verdict("Resting voltage", v, Level.Bad, "Discharged or failing. Hot climates shorten battery life to about 2 to 3 years: have it load-tested.")
                }
            )
        }
        if (crankMin != null) {
            val v = Calc.fmt(crankMin, 2) + " V"
            out.add(
                when {
                    crankMin >= 9.6 -> Verdict("Lowest voltage while cranking", v, Level.Good, "Battery and starter circuit hold up under load.")
                    crankMin >= 9.0 -> Verdict("Lowest voltage while cranking", v, Level.Warn, "Borderline. Battery is getting weak, or terminals/cables have resistance.")
                    else -> Verdict("Lowest voltage while cranking", v, Level.Bad, "Voltage collapses under load: weak battery, corroded terminals or a worn starter. Many U-codes and warning lights come from this.")
                }
            )
        }
        if (idle != null) {
            val v = Calc.fmt(idle, 2) + " V"
            out.add(
                when {
                    idle in 13.2..14.9 -> Verdict("Charging at idle", v, Level.Good, "Alternator is charging.")
                    idle < 13.0 -> Verdict("Charging at idle", v, Level.Bad, "Not charging enough: alternator, drive belt, or its wiring/fuse. Battery will run flat.")
                    idle > 15.0 -> Verdict("Charging at idle", v, Level.Bad, "Overcharging: faulty regulator. This cooks the battery and electronics.")
                    else -> Verdict("Charging at idle", v, Level.Warn, "Slightly outside the normal range. Retest with lights and A/C off.")
                }
            )
        }
        if (rev != null) {
            val v = Calc.fmt(rev, 2) + " V"
            out.add(
                when {
                    rev in 13.5..14.9 -> Verdict("Charging at 2000 rpm", v, Level.Good, "Charging system is healthy.")
                    rev < 13.2 -> Verdict("Charging at 2000 rpm", v, Level.Bad, "Alternator output too low even at higher rpm.")
                    rev > 15.0 -> Verdict("Charging at 2000 rpm", v, Level.Bad, "Overcharging: faulty regulator.")
                    else -> Verdict("Charging at 2000 rpm", v, Level.Warn, "Slightly outside the normal range.")
                }
            )
        }
        if (out.isEmpty()) out.add(Verdict("No data", "-", Level.Info, "Run the steps first."))
        return out
    }
}

/** One service item with its interval. 0 means "not used". */
class MaintItem(
    val id: String, var name: String, var everyKm: Int, var everyMonths: Int,
    var lastKm: Int = 0, var lastDateMs: Long = 0L, var note: String = "",
)

class Due(
    val item: MaintItem, val kmLeft: Int?, val daysLeft: Int?,
    val recorded: Boolean = true, val nextDueMs: Long? = null, val nextDueKm: Int? = null,
) {
    val overdue: Boolean get() = recorded && ((kmLeft != null && kmLeft < 0) || (daysLeft != null && daysLeft < 0))
    val soon: Boolean get() = recorded && !overdue && ((kmLeft != null && kmLeft <= 500) || (daysLeft != null && daysLeft <= 14))
}

object Maintenance {
    /** The schedule from the owner's manual, severe use (hot weather and city traffic). */
    fun defaults(): List<MaintItem> = Manual.schedule(true)

    /** A job counts only once you have said when (or at what mileage) it was last done: otherwise it is "not recorded", never "overdue by the whole odometer". */
    fun due(item: MaintItem, currentKm: Int, nowMs: Long): Due {
        val kmKnown = item.everyKm > 0 && item.lastKm > 0
        val dateKnown = item.everyMonths > 0 && item.lastDateMs > 0
        val kmLeft = if (kmKnown) item.lastKm + item.everyKm - currentKm else null
        val nextMs = if (dateKnown) (item.lastDateMs + item.everyMonths * 30.4375 * 86_400_000.0).toLong() else null
        val daysLeft = if (nextMs != null) Math.floorDiv(nextMs - nowMs, 86_400_000L).toInt() else null
        return Due(item, kmLeft, daysLeft, kmKnown || dateKnown, nextMs, if (kmKnown) item.lastKm + item.everyKm else null)
    }

    /** When the kilometre limit will be reached if you keep driving at [kmPerDay]. Null when it is already passed or unknown. */
    fun projectedKmDate(kmLeft: Int, kmPerDay: Double, nowMs: Long): Long? =
        if (kmLeft > 0 && kmPerDay > 0.0) nowMs + (kmLeft / kmPerDay * 86_400_000.0).toLong() else null
}

object Usage {
    /** Average kilometres per day from (time, odometer) points. Needs two points at least 14 days apart. */
    fun kmPerDay(points: List<Pair<Long, Int>>): Double? {
        val p = points.filter { it.second > 0 && it.first > 0 }.sortedBy { it.first }
        if (p.size < 2) return null
        val days = (p.last().first - p.first().first) / 86_400_000.0
        val km = p.last().second - p.first().second
        if (days < 14 || km <= 0) return null
        return km / days
    }
}

/** A recorded set of live values (for graphs and comparing two drives). */
class Recording(val name: String, val startedMs: Long, val columns: List<String>, val units: List<String>) {
    val times = ArrayList<Double>()
    val rows = ArrayList<DoubleArray>()

    fun add(t: Double, values: DoubleArray) { times.add(t); rows.add(values) }

    fun series(col: Int): List<Pair<Double, Double>> =
        rows.indices.mapNotNull { i -> val v = rows[i].getOrNull(col); if (v == null || v.isNaN()) null else times[i] to v }

    fun stats(col: Int): Triple<Double, Double, Double>? {
        val s = series(col).map { it.second }
        if (s.isEmpty()) return null
        return Triple(s.min(), s.average(), s.max())
    }

    fun toCsv(): String {
        val sb = StringBuilder()
        sb.append("#obdy,").append(startedMs).append(',').append(name.replace(',', ' ').replace('\n', ' ')).append('\n')
        sb.append("time_s,").append(columns.indices.joinToString(",") { columns[it].replace(',', ' ') + "|" + units[it].replace(',', ' ') }).append('\n')
        for (i in rows.indices) {
            sb.append(String.format(Locale.US, "%.2f", times[i]))
            for (v in rows[i]) { sb.append(','); if (!v.isNaN()) sb.append(String.format(Locale.US, "%.3f", v)) }
            sb.append('\n')
        }
        return sb.toString()
    }

    companion object {
        fun fromCsv(text: String): Recording? {
            val lines = text.lines().filter { it.isNotBlank() }
            if (lines.size < 2 || !lines[0].startsWith("#obdy,")) return null
            val meta = lines[0].split(',', limit = 3)
            val started = meta.getOrNull(1)?.toLongOrNull() ?: 0L
            val name = meta.getOrNull(2) ?: "Recording"
            val head = lines[1].split(',').drop(1)
            val cols = head.map { it.substringBefore('|') }
            val units = head.map { it.substringAfter('|', "") }
            val rec = Recording(name, started, cols, units)
            for (l in lines.drop(2)) {
                val p = l.split(',')
                val t = p[0].toDoubleOrNull() ?: continue
                rec.add(t, DoubleArray(cols.size) { i -> p.getOrNull(i + 1)?.toDoubleOrNull() ?: Double.NaN })
            }
            return rec
        }
    }
}
