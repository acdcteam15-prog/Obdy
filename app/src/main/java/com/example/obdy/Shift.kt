package com.example.obdy

import java.util.Locale
import kotlin.math.abs

/** One reading of engine speed and vehicle speed, [t] = seconds since the recording started. */
class ShiftSample(val t: Double, val rpm: Double, val speed: Double)

/** A gear change found in a driving recording. [ratio] is rpm per km/h; a smaller ratio after the change means an upshift. */
class ShiftEvent(
    val t: Double, val up: Boolean, val speed: Double, val rpmBefore: Double, val rpmAfter: Double,
    val ratioBefore: Double, val ratioAfter: Double, val ms: Int, val rise: Double, val verdict: String,
)

/** A dip in engine speed while parked, which is what putting the gearbox into Drive or Reverse looks like. */
class Engagement(val t: Double, val baseline: Double, val dip: Double, val ms: Int, val verdict: String)

/**
 * Looks for gear changes and gear engagement in rpm and speed readings. Pure Kotlin.
 * These are indicators to compare between drives and to show a workshop, not a diagnosis.
 */
object ShiftAnalysis {
    private fun ratio(s: ShiftSample): Double = if (s.speed >= 8.0 && s.rpm >= 600.0) s.rpm / s.speed else Double.NaN

    private fun median(v: List<Double>): Double { val s = v.sorted(); return if (s.isEmpty()) 0.0 else s[s.size / 2] }

    fun shifts(all: List<ShiftSample>): List<ShiftEvent> {
        val s = all.filter { !ratio(it).isNaN() }
        val out = ArrayList<ShiftEvent>()
        var i = 3
        while (i < s.size) {
            val base = median(listOf(ratio(s[i - 1]), ratio(s[i - 2]), ratio(s[i - 3])))
            val r = ratio(s[i])
            if (s[i].t - s[i - 1].t < 0.8 && base > 0 && abs(r / base - 1.0) > 0.04) {
                var j = i
                var stable = 0
                while (j + 1 < s.size && s[j + 1].t - s[j].t < 0.8 && j - i < 25) {
                    val d = abs(ratio(s[j + 1]) / ratio(s[j]) - 1.0)
                    if (d < 0.025) { stable++; if (stable >= 2) break } else stable = 0
                    j++
                }
                val after = ratio(s[j])
                val total = after / base - 1.0
                if (abs(total) >= 0.12) {
                    val from = i - 1
                    val window = s.subList(from, j + 1)
                    val ms = ((s[j].t - s[from].t) * 1000).toInt()
                    val up = total < 0
                    val rise = if (up) (window.maxOf { it.rpm } - s[from].rpm).coerceAtLeast(0.0) else 0.0
                    val verdict = when {
                        up && rise > 150 -> "Flare: engine speed rose by ${rise.toInt()} rpm during the upshift (slipping clutch or low pressure can do this)"
                        ms > 1500 -> "Slow: the change took ${ms} ms"
                        else -> "Normal"
                    }
                    out.add(ShiftEvent(s[i].t, up, s[from].speed, s[from].rpm, s[j].rpm, base, after, ms, rise, verdict))
                    i = j + 1
                    continue
                }
            }
            i++
        }
        return out
    }

    fun engagements(all: List<ShiftSample>): List<Engagement> {
        val out = ArrayList<Engagement>()
        var i = 0
        while (i < all.size) {
            val s = all[i]
            if (s.speed > 2.0 || s.rpm < 300.0) { i++; continue }
            val prior = all.filter { it.t >= s.t - 2.0 && it.t <= s.t - 0.4 && it.speed <= 2.0 && it.rpm >= 300.0 }.map { it.rpm }
            if (prior.size < 3) { i++; continue }
            val base = median(prior)
            if (s.rpm < base - 70.0) {
                var j = i
                var low = s.rpm
                while (j + 1 < all.size && all[j + 1].t - all[j].t < 0.8 && all[j + 1].rpm < base - 40.0) { j++; low = minOf(low, all[j].rpm) }
                val dip = base - low
                val ms = ((all[j].t - s.t) * 1000).toInt() + 100
                val verdict = when { dip >= 250 -> "Heavy"; dip >= 120 -> "Noticeable"; else -> "Light" }
                out.add(Engagement(s.t, base, dip, ms, verdict))
                i = j + 1
            } else i++
        }
        return out
    }

    fun fmt(v: Double, digits: Int = 0): String = String.format(Locale.US, "%.${digits}f", v)

    fun shiftLine(e: ShiftEvent) =
        "at ${fmt(e.t, 1)} s, ${fmt(e.speed)} km/h: ${if (e.up) "upshift" else "downshift"}, rpm ${fmt(e.rpmBefore)} to ${fmt(e.rpmAfter)}, " +
            "ratio ${fmt(e.ratioBefore, 1)} to ${fmt(e.ratioAfter, 1)}, took ${e.ms} ms, ${e.verdict}"

    fun engagementLine(e: Engagement) =
        "at ${fmt(e.t, 1)} s: idle ${fmt(e.baseline)} rpm dipped by ${fmt(e.dip)} rpm for about ${e.ms} ms, ${e.verdict}"

    /** Text for the Claude Lab bundle. */
    fun report(mode: String, samples: List<ShiftSample>, shifts: List<ShiftEvent>, eng: List<Engagement>, extra: String): String {
        if (samples.isEmpty()) return "(no gearbox recording yet: open Tools, Gearbox)"
        val sb = StringBuilder("Recording: $mode, ${samples.size} readings over ${fmt(samples.last().t, 1)} s\n")
        if (extra.isNotBlank()) sb.append("GM gearbox values: $extra\n")
        if (shifts.isNotEmpty()) { sb.append("Shifts found:\n"); shifts.forEach { sb.append("- ").append(shiftLine(it)).append("\n") } }
        if (eng.isNotEmpty()) { sb.append("Engagement dips:\n"); eng.forEach { sb.append("- ").append(engagementLine(it)).append("\n") } }
        sb.append("Readings (t_s,rpm,km/h):\n")
        samples.takeLast(400).forEach { sb.append(fmt(it.t, 2)).append(",").append(fmt(it.rpm)).append(",").append(fmt(it.speed)).append("\n") }
        return sb.toString().trimEnd()
    }
}
