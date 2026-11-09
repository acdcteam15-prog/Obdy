package com.example.obdy

import kotlin.math.abs
import kotlin.math.sqrt

// ====================================================================================== Fuel log
class FuelEntry(val id: String, val timeMs: Long, val km: Int, val liters: Double, val cost: Double, val full: Boolean, val station: String)

object FuelLog {
    /** Fuel use between two full tanks. Part fills in between add to the litres. */
    class Segment(val fromKm: Int, val toKm: Int, val liters: Double, val cost: Double, val timeMs: Long) {
        val km: Int get() = toKm - fromKm
        val l100: Double get() = liters / km * 100.0
    }

    fun segments(entries: List<FuelEntry>): List<Segment> {
        val out = ArrayList<Segment>()
        var lastFullKm = -1
        var liters = 0.0
        var cost = 0.0
        for (e in entries.sortedBy { it.km }) {
            if (lastFullKm >= 0) { liters += e.liters; cost += e.cost }
            if (e.full) {
                if (lastFullKm >= 0 && e.km > lastFullKm && liters > 0) out.add(Segment(lastFullKm, e.km, liters, cost, e.timeMs))
                lastFullKm = e.km; liters = 0.0; cost = 0.0
            }
        }
        return out
    }

    fun averageL100(seg: List<Segment>): Double? {
        val km = seg.sumOf { it.km }
        return if (km > 0) seg.sumOf { it.liters } / km * 100.0 else null
    }

    fun costPerKm(seg: List<Segment>): Double? {
        val km = seg.sumOf { it.km }
        return if (km > 0) seg.sumOf { it.cost } / km else null
    }

    /** Total spent per month ("2026-10" to amount), oldest first. */
    fun monthly(entries: List<FuelEntry>): List<Pair<String, Double>> =
        entries.groupBy { Docs.formatDate(it.timeMs).substring(0, 7) }.map { it.key to it.value.sumOf { e -> e.cost } }.sortedBy { it.first }

    /** What to multiply Obdy's air-flow fuel estimate by so it matches the pump. Null when there is too little to compare. */
    fun calibration(actualLiters: Double, estimatedLiters: Double): Double? =
        if (estimatedLiters < 5.0 || actualLiters < 5.0) null else (actualLiters / estimatedLiters).coerceIn(0.7, 1.4)
}

// ====================================================================================== Parts register and warranty
class PartRecord(val id: String, val name: String, val installedMs: Long, val km: Int, val warrantyMonths: Int, val warrantyKm: Int, val notes: String) {
    val warrantyEndMs: Long? get() = if (warrantyMonths > 0 && installedMs > 0) installedMs + (warrantyMonths * 30.4375 * 86_400_000.0).toLong() else null
    fun daysLeft(nowMs: Long): Int? = warrantyEndMs?.let { Math.floorDiv(it - nowMs, 86_400_000L).toInt() }
    fun kmLeft(currentKm: Int): Int? = if (warrantyKm > 0 && km > 0) km + warrantyKm - currentKm else null
    fun covered(nowMs: Long, currentKm: Int): Boolean {
        val d = daysLeft(nowMs); val k = kmLeft(currentKm)
        return (d != null && d >= 0 && (k == null || k >= 0)) || (d == null && k != null && k >= 0)
    }
}

// ====================================================================================== Battery health
class BatteryHealth(val score: Int, val level: Level, val summary: String, val details: List<String>, val replaceByMs: Long?)

object BatteryScore {
    /** [tests] newest first. [installedMs] = when the battery was fitted (from the parts register), or null. Hot climates shorten life to about 2 to 3 years. */
    fun compute(tests: List<BatteryRecord>, installedMs: Long?, nowMs: Long): BatteryHealth? {
        if (tests.isEmpty() && installedMs == null) return null
        var score = 100
        val d = ArrayList<String>()
        val t = tests.firstOrNull()
        if (t != null) {
            t.rest?.let { v ->
                val p = if (v >= 12.6) 0 else if (v >= 12.4) 10 else if (v >= 12.2) 25 else 45
                score -= p; d.add("Resting voltage ${Calc.fmt(v, 2)} V" + if (p == 0) " (good)" else " (low)")
            }
            t.crank?.let { v ->
                val p = if (v >= 10.0) 0 else if (v >= 9.6) 5 else if (v >= 9.0) 20 else 40
                score -= p; d.add("Lowest voltage while starting ${Calc.fmt(v, 2)} V" + if (p == 0) " (good)" else " (weak)")
            }
            val ch = t.idle ?: t.rev
            if (ch != null) {
                val p = if (ch in 13.2..14.9) 0 else 15
                score -= p; d.add("Charging ${Calc.fmt(ch, 2)} V" + if (p == 0) " (good)" else " (check the alternator)")
            }
        }
        val rests = tests.mapNotNull { r -> r.rest?.let { r.timeMs to it } }.sortedBy { it.first }
        if (rests.size >= 3) {
            val months = (rests.last().first - rests.first().first) / (30.4375 * 86_400_000.0)
            if (months >= 0.5) {
                val perMonth = (rests.last().second - rests.first().second) / months
                if (perMonth < -0.05) { score -= 10; d.add("Resting voltage is falling by about ${Calc.fmt(-perMonth, 2)} V a month") }
            }
        }
        var replace: Long? = null
        if (installedMs != null && installedMs > 0) {
            val age = (nowMs - installedMs) / (30.4375 * 86_400_000.0)
            val p = if (age > 36) 30 else if (age > 30) 20 else if (age > 24) 10 else 0
            score -= p
            d.add("Battery age about ${age.toInt()} months" + if (p > 0) " (hot climates shorten life to 2 to 3 years)" else "")
            replace = installedMs + (36 * 30.4375 * 86_400_000.0).toLong()
        }
        score = score.coerceIn(0, 100)
        if (score < 60 && t != null) replace = nowMs + (60 * 86_400_000L)
        val level = if (score >= 75) Level.Good else if (score >= 50) Level.Warn else Level.Bad
        val summary = when (level) {
            Level.Good -> "Battery looks healthy."
            Level.Warn -> "Battery is ageing or weak. Test it again in a month and plan a replacement."
            else -> "Battery is likely to fail soon. Replace it before it strands you."
        }
        return BatteryHealth(score, level, summary, d, replace)
    }
}

// ====================================================================================== Flight recorder
class FlightSample(val tMs: Long, val values: Map<Int, Double>)

/** Keeps the last minute of live values. When something goes wrong the minute before it can be saved. */
class FlightRecorder(private val windowMs: Long = 60_000L) {
    private val buf = ArrayDeque<FlightSample>()

    fun add(tMs: Long, values: Map<Int, Double>) {
        if (values.isEmpty()) return
        buf.addLast(FlightSample(tMs, HashMap(values)))
        while (buf.isNotEmpty() && tMs - buf.first().tMs > windowMs) buf.removeFirst()
    }

    val size: Int get() = buf.size
    fun clear() = buf.clear()
    fun recentRpm(n: Int): List<Double> = buf.toList().takeLast(n).mapNotNull { it.values[0x0C] }

    fun snapshot(name: String): Recording? {
        if (buf.size < 3) return null
        val list = buf.toList()
        val ids = list.flatMap { it.values.keys }.toSortedSet().filter { Pids.byId(it) != null }
        if (ids.isEmpty()) return null
        val pids = ids.mapNotNull { Pids.byId(it) }
        val rec = Recording(name, list.last().tMs, pids.map { it.name }, pids.map { it.unit })
        val t0 = list.first().tMs
        for (s in list) rec.add((s.tMs - t0) / 1000.0, DoubleArray(pids.size) { s.values[pids[it].id] ?: Double.NaN })
        return rec
    }
}

object Triggers {
    class Alert(val key: String, val text: String)

    /** Values that deserve a saved snapshot. Obvious ranges only, so it does not cry wolf. */
    fun check(v: Map<Int, Double>, recentRpm: List<Double>): List<Alert> {
        val out = ArrayList<Alert>()
        val rpm = v[0x0C] ?: 0.0
        v[0x05]?.let { if (it >= 105) out.add(Alert("hot", "Coolant temperature ${Calc.fmt(it, 0)} °C")) }
        v[0x42]?.let { if (rpm > 400 && it < 11.8) out.add(Alert("lowv", "Low system voltage ${Calc.fmt(it, 1)} V")); if (rpm > 400 && it > 15.2) out.add(Alert("highv", "High system voltage ${Calc.fmt(it, 1)} V")) }
        v[0x07]?.let { if (abs(it) >= 20) out.add(Alert("ltft", "Long fuel trim ${Calc.fmt(it, 0)} %")) }
        v[0x5C]?.let { if (it >= 150) out.add(Alert("oilhot", "Oil temperature ${Calc.fmt(it, 0)} °C")) }
        if (recentRpm.size >= 10) {
            val m = recentRpm.average()
            val sd = sqrt(recentRpm.sumOf { (it - m) * (it - m) } / (recentRpm.size - 1))
            if (m in 450.0..1100.0 && sd > 90) out.add(Alert("rough", "Rough idle (±${Calc.fmt(sd, 0)} rpm)"))
        }
        return out
    }

    /** The engine computer's code counter went up or the warning light came on. */
    fun faultAppeared(prevCount: Int?, prevMil: Boolean?, count: Int, mil: Boolean): Boolean =
        (prevCount != null && count > prevCount) || (prevMil == false && mil)
}

// ====================================================================================== Checklists
class CheckItem(val id: String, val title: String, val how: String)

object Checklist {
    val preTrip = listOf(
        CheckItem("pt_tyres", "Tyres: look and pressure", "Look for nails, bulges and low tyres. Check pressure cold against the label on the driver's door."),
        CheckItem("pt_oil", "Engine oil level", "Dipstick on level ground, level between the marks. Add dexos1 5W-30 only if low."),
        CheckItem("pt_coolant", "Coolant level (engine cold)", "Visible in the surge tank at or above the mark. Never open a hot system."),
        CheckItem("pt_dash", "Dashboard messages", "No warning lights after start. Read any message in the display."),
        CheckItem("pt_lights", "Lights and indicators", "Headlamps, brake lights and indicators work."),
        CheckItem("pt_wash", "Washer fluid and wipers", "Reservoir has fluid; blades clean the glass without smearing."),
        CheckItem("pt_fuel", "Fuel", "Enough for the trip plus a reserve."),
    )

    val summer = listOf(
        CheckItem("su_battery", "Battery test", "Run Tools > Battery test. Heat is the main killer of batteries here."),
        CheckItem("su_coolant", "Coolant level and fans", "Surge tank level when cold; both cooling fans run when the A/C is on."),
        CheckItem("su_tyres", "Tyre pressure (cold) and age", "Heat raises pressure: check before driving. Cracked sidewalls or tyres older than about 5 years need replacing."),
        CheckItem("su_ac", "A/C cooling and cabin filter", "Cold air within a minute. Replace the cabin filter if airflow is weak (every 36,000 km or 2 years)."),
        CheckItem("su_oil", "Oil life and level", "Hot, stop-start driving uses oil life faster. Change when the car asks, and no later than 7,500 km or 6 months."),
        CheckItem("su_belts", "Drive belt and hoses", "No cracks, glazing or squeal; hoses firm, not swollen."),
        CheckItem("su_wipers", "Wiper blades", "Sun hardens rubber. Replace if they chatter or leave streaks."),
        CheckItem("su_spare", "Spare tyre and jack", "Spare inflated; jack and wheel wrench present."),
        CheckItem("su_water", "Water and a sunshade in the car", "For you, not the car: carry water if the car breaks down."),
    )
}

// ====================================================================================== Parking spot
class ParkingSpot(val lat: Double, val lon: Double, val timeMs: Long, val accuracyM: Int)
