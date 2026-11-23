package com.example.obdy

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Shift quality from fast gearbox samples (gear, turbine and output shaft speed about every 0.22 s).
 * For every gear change it writes one [SHIFT-FAST] line:
 *   dur<=  ms between the last sample still at the old gear ratio and the first one at the new ratio (upper bound, +-0.22 s)
 *   flare  % the turbine/output ratio went past BOTH gears' ratios on the high side (engine speeds up, clutch not holding yet)
 *   tie    % the ratio went past both on the low side (clutches fighting each other)
 *   jolt   biggest change of vehicle acceleration between two samples next to the shift, m/s2 (output shaft speed)
 *   peak   strongest deceleration / acceleration seen next to the shift, m/s2
 * A jolt of 1.5 m/s2 or more also writes [SHOCK?]. That limit is a first guess: the numbers in the log calibrate it.
 * Pure logic (no Android), unit tested.
 */
class ShiftCapture {
    class S(val tMs: Long, val gear: Int?, val turb: Double?, val out: Double?, val tft: Double? = null)

    private class Pending(val tChange: Long, val from: Int, val to: Int)
    private class Stat { var n = 0; var joltSum = 0.0; var joltMax = 0.0; var flareMax = 0.0; var tieMax = 0.0; var durSum = 0L; var durN = 0; var shocks = 0 }

    private val buf = ArrayDeque<S>()
    private val pend = ArrayList<Pending>()
    private val stats = java.util.TreeMap<String, Stat>()

    fun reset() { buf.clear(); pend.clear(); stats.clear() }

    private fun f(v: Double, d: Int = 1) = String.format(Locale.US, "%.${d}f", v)

    fun add(s: S): List<String> {
        val out = ArrayList<String>()
        buf.addLast(s)
        while (buf.size > 80 || (buf.size > 2 && s.tMs - buf.first().tMs > 8000)) buf.removeFirst()
        val prev = if (buf.size >= 2) buf[buf.size - 2] else null
        if (prev != null && prev.gear != null && s.gear != null && prev.gear in 1..6 && s.gear in 1..6 && prev.gear != s.gear && s.tMs - prev.tMs < 1500)
            pend.add(Pending(s.tMs, prev.gear, s.gear))
        val iter = pend.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            val age = s.tMs - p.tChange
            if (age > 8000) iter.remove()
            else if (age >= 1200) { iter.remove(); out.addAll(evaluate(p)) }
        }
        return out
    }

    private fun evaluate(p: Pending): List<String> {
        val key = "D${p.from}->D${p.to}"
        val w = buf.filter { abs(it.tMs - p.tChange) <= 1500 && it.turb != null && it.out != null && it.out >= 150 }.sortedBy { it.tMs }
        if (w.size < 4) return listOf("[SHIFT-FAST] $key: only ${w.size} usable samples (too slow or too few), skipped")
        val ro = Gm.GEAR_RATIO[p.from - 1]; val rn = Gm.GEAR_RATIO[p.to - 1]
        fun r(x: S) = x.turb!! / x.out!!
        val tStart = w.lastOrNull { it.tMs < p.tChange && abs(r(it) / ro - 1) <= 0.04 }?.tMs ?: w.first().tMs
        val tEnd = w.firstOrNull { it.tMs > tStart && abs(r(it) / rn - 1) <= 0.04 }?.tMs
        val dur = tEnd?.let { it - tStart }
        val mid = w.filter { it.tMs >= tStart && (tEnd == null || it.tMs <= tEnd) }.ifEmpty { w }
        val hi = max(ro, rn); val lo = min(ro, rn)
        val flare = max(0.0, (mid.maxOf { r(it) } / hi - 1) * 100)
        val tie = max(0.0, (1 - mid.minOf { r(it) } / lo) * 100)
        // vehicle acceleration from the output shaft, only right next to the shift
        val near = w.filter { it.tMs >= tStart - 600 && it.tMs <= (tEnd ?: p.tChange) + 600 }
        val acc = ArrayList<Double>()
        for (i in 1 until near.size) {
            val dt = (near[i].tMs - near[i - 1].tMs) / 1000.0
            if (dt in 0.08..0.6) acc.add(((near[i].out!! - near[i - 1].out!!) / RPM_PER_KMH / 3.6) / dt)
        }
        var jolt = 0.0
        for (i in 1 until acc.size) jolt = max(jolt, abs(acc[i] - acc[i - 1]))
        val peakDec = acc.minOrNull() ?: 0.0
        val peakAcc = acc.maxOrNull() ?: 0.0
        val kmh = w.minByOrNull { abs(it.tMs - p.tChange) }!!.out!! / RPM_PER_KMH
        val dir = if (p.to > p.from) "up" else "down"
        val tft = w.last().tft
        val line = "[SHIFT-FAST] $key $dir at ${f(kmh, 0)} km/h n=${w.size} dur" + (if (dur != null) "<=${dur} ms" else "=not settled") +
            " flare=${f(flare)}% tie=${f(tie)}% jolt=${f(jolt)} m/s2 peak=${f(peakDec)}/+${f(peakAcc)}" + (if (tft != null) " tft=${f(tft, 0)} C" else "")
        val st = stats.getOrPut(key) { Stat() }
        st.n++; st.joltSum += jolt; st.joltMax = max(st.joltMax, jolt); st.flareMax = max(st.flareMax, flare); st.tieMax = max(st.tieMax, tie)
        if (dur != null) { st.durSum += dur; st.durN++ }
        val res = arrayListOf(line)
        if (jolt >= SHOCK_JOLT) { st.shocks++; res.add("[SHOCK?] $key at ${f(kmh, 0)} km/h: acceleration changed by ${f(jolt)} m/s2 (limit ${f(SHOCK_JOLT)}), flare ${f(flare)}%, tie ${f(tie)}%" + (if (tft != null) ", tft ${f(tft, 0)} C" else "")) }
        return res
    }

    /** One line with every shift type seen so far. Null until there is one. */
    fun summary(): String? {
        if (stats.isEmpty()) return null
        return "[SHIFT-FAST SUMMARY] " + stats.entries.joinToString(" | ") { (k, v) ->
            "$k n=${v.n} jolt avg=${f(v.joltSum / v.n)} max=${f(v.joltMax)} shocks=${v.shocks} flare max=${f(v.flareMax)}% tie max=${f(v.tieMax)}%" + (if (v.durN > 0) " dur avg=${v.durSum / v.durN} ms" else "")
        }
    }

    companion object {
        /** Output shaft rpm per km/h on this car (3019 rpm at 97 km/h, measured 11-22). */
        const val RPM_PER_KMH = 31.1
        const val SHOCK_JOLT = 1.5
    }
}
