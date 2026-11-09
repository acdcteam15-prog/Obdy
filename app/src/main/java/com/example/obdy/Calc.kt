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

/** Adds up one drive: distance, fuel, time, idle time. Call [add] every time new live values arrive. */
class TripAcc {
    var seconds = 0.0; private set
    var km = 0.0; private set
    var liters = 0.0; private set
    var idleSeconds = 0.0; private set
    var maxKmh = 0.0; private set

    fun add(dtSeconds: Double, speedKmh: Double, litersPerHour: Double) {
        if (dtSeconds <= 0 || dtSeconds > 10) return          // a gap (connection lost): do not invent distance
        seconds += dtSeconds
        km += speedKmh * dtSeconds / 3600.0
        liters += litersPerHour * dtSeconds / 3600.0
        if (speedKmh < 1.0) idleSeconds += dtSeconds
        if (speedKmh > maxKmh) maxKmh = speedKmh
    }

    val avgKmh: Double get() = if (seconds > 0) km / (seconds / 3600.0) else 0.0
    val litersPer100: Double? get() = if (km >= 0.2) liters / km * 100.0 else null
    val kmPerLiter: Double? get() = if (liters > 0.01 && km >= 0.2) km / liters else null
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
    var lastKm: Int = 0, var lastDateMs: Long = 0L,
)

class Due(
    val item: MaintItem, val kmLeft: Int?, val daysLeft: Int?,
    val recorded: Boolean = true, val nextDueMs: Long? = null, val nextDueKm: Int? = null,
) {
    val overdue: Boolean get() = recorded && ((kmLeft != null && kmLeft < 0) || (daysLeft != null && daysLeft < 0))
    val soon: Boolean get() = recorded && !overdue && ((kmLeft != null && kmLeft <= 500) || (daysLeft != null && daysLeft <= 14))
}

object Maintenance {
    /** Starting intervals: the harder end of the maker's range, because heat and dust shorten oil, filters, fluids and batteries. */
    fun defaults(): List<MaintItem> = listOf(
        MaintItem("oil", "Engine oil and filter", 5000, 6),
        MaintItem("air", "Engine air filter", 15000, 12),
        MaintItem("cabin", "Cabin (A/C) filter", 10000, 6),
        MaintItem("rotate", "Tyre rotation and balance", 10000, 0),
        MaintItem("brakes", "Brake pads and discs check", 15000, 12),
        MaintItem("brakefluid", "Brake fluid", 0, 24),
        MaintItem("coolant", "Coolant", 100000, 60),
        MaintItem("atf", "Automatic transmission fluid", 60000, 0),
        MaintItem("plugs", "Spark plugs", 60000, 0),
        MaintItem("battery", "Battery test", 0, 12),
        MaintItem("ac", "A/C service", 0, 24),
        MaintItem("wipers", "Wiper blades", 0, 12),
    )

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
