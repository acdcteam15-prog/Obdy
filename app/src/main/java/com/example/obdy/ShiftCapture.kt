package com.example.obdy

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Shift quality from fast gearbox samples (gear, turbine and output shaft speed about every 0.22 s).
 * The gear value is the gearbox's TARGET gear: it changes about 0.5 s before the real ratio gets there (11-23 drive).
 * For every gear change it writes one [SHIFT-FAST] line:
 *   dur<=  ms from the last sample at the old ratio to the first one at the new ratio (upper bound, +-0.22 s)
 *   flare  % the turbine/output ratio went past BOTH gears' ratios on the high side (engine speeds up, clutch not holding yet)
 *   tie    % the ratio went past both on the low side (clutches fighting each other)
 *   spike  biggest one-or-two-sample kick of vehicle acceleration next to the shift (output shaft speed, m/s2), measured against the
 *          local trend, so braking or throttle steps do not count, only a clunk does
 *   peak   strongest deceleration / acceleration next to the shift, m/s2
 * Only samples between the neighbouring gear changes are used, so back-to-back coast-down shifts (3->2->1) do not pollute each other.
 * A spike of 1.0 m/s2 or more writes [SHOCK?] (80% of shifts on 11-23 were below 0.8; 2.0 and more = strong). Pure logic, unit tested.
 */
class ShiftCapture {
    class S(val tMs: Long, val gear: Int?, val turb: Double?, val out: Double?, val tft: Double? = null)

    private class Pending(val tChange: Long, val from: Int, val to: Int)
    private class Stat { var n = 0; var joltSum = 0.0; var joltMax = 0.0; var flareMax = 0.0; var tieMax = 0.0; var durSum = 0L; var durN = 0; var shocks = 0 }

    private val buf = ArrayDeque<S>()
    private val pend = ArrayList<Pending>()
    private val changes = ArrayList<Long>()
    private val stats = java.util.TreeMap<String, Stat>()

    fun reset() { buf.clear(); pend.clear(); changes.clear(); stats.clear() }

    private fun f(v: Double, d: Int = 1) = String.format(Locale.US, "%.${d}f", v)
    private fun sg(v: Double) = String.format(Locale.US, "%+.1f", v)

    fun add(s: S): List<String> {
        val out = ArrayList<String>()
        buf.addLast(s)
        while (buf.size > 80 || (buf.size > 2 && s.tMs - buf.first().tMs > 8000)) buf.removeFirst()
        val prev = if (buf.size >= 2) buf[buf.size - 2] else null
        if (prev != null && prev.gear != null && s.gear != null && prev.gear in 1..6 && s.gear in 1..6 && prev.gear != s.gear && s.tMs - prev.tMs < 1500) {
            pend.add(Pending(s.tMs, prev.gear, s.gear)); changes.add(s.tMs)
            while (changes.size > 30) changes.removeAt(0)
        }
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
        val tPrev = changes.lastOrNull { it < p.tChange }
        val tNext = changes.firstOrNull { it > p.tChange }
        val from = max(p.tChange - 1500, if (tPrev != null) tPrev + 500 else Long.MIN_VALUE)   // the previous shift needs ~0.5 s to finish
        val to = min(p.tChange + 1500, tNext ?: Long.MAX_VALUE)
        val w = buf.filter { it.tMs in from until to && it.turb != null && it.out != null && it.out >= 150 }.sortedBy { it.tMs }
        if (w.size < 4) return listOf("[SHIFT-FAST] $key: only ${w.size} usable samples (too slow, too few or back-to-back shifts), skipped")
        val ro = Gm.GEAR_RATIO[p.from - 1]; val rn = Gm.GEAR_RATIO[p.to - 1]
        fun r(x: S) = x.turb!! / x.out!!
        val tStart = w.lastOrNull { it.tMs <= p.tChange && abs(r(it) / ro - 1) <= 0.04 }?.tMs ?: p.tChange
        val tEnd = w.firstOrNull { it.tMs > tStart && abs(r(it) / rn - 1) <= 0.04 }?.tMs
        val dur = tEnd?.let { it - tStart }
        val mid = w.filter { it.tMs >= tStart && (tEnd == null || it.tMs <= tEnd) }.ifEmpty { w }
        val hi = max(ro, rn); val lo = min(ro, rn)
        val flare = max(0.0, (mid.maxOf { r(it) } / hi - 1) * 100)
        val tie = max(0.0, (1 - mid.minOf { r(it) } / lo) * 100)
        // vehicle acceleration (m/s2) between consecutive samples, from the output shaft
        val tA = ArrayList<Long>(); val acc = ArrayList<Double>()
        for (i in 1 until w.size) {
            val dt = (w[i].tMs - w[i - 1].tMs) / 1000.0
            if (dt in 0.08..0.6) { tA.add(w[i].tMs); acc.add(((w[i].out!! - w[i - 1].out!!) / RPM_PER_KMH / 3.6) / dt) }
        }
        val zoneEnd = (tEnd ?: (p.tChange + 800)) + 300
        var spike = 0.0
        for (j in acc.indices) {
            if (tA[j] < tStart - 300 || tA[j] > zoneEnd) continue
            val nb = acc.subList(max(0, j - 2), min(acc.size, j + 3)).sorted()
            if (nb.size < 3) continue
            spike = max(spike, abs(acc[j] - nb[nb.size / 2]))
        }
        val peakDec = acc.minOrNull() ?: 0.0
        val peakAcc = acc.maxOrNull() ?: 0.0
        val kmh = w.minByOrNull { abs(it.tMs - p.tChange) }!!.out!! / RPM_PER_KMH
        val dir = if (p.to > p.from) "up" else "down"
        val tft = w.last().tft
        val line = "[SHIFT-FAST] $key $dir at ${f(kmh, 0)} km/h n=${w.size} dur" + (if (dur != null) "<=${dur} ms" else "=not settled") +
            " flare=${f(flare)}% tie=${f(tie)}% spike=${f(spike)} m/s2 peak=${sg(peakDec)}/${sg(peakAcc)}" + (if (tft != null) " tft=${f(tft, 0)} C" else "")
        val st = stats.getOrPut(key) { Stat() }
        st.n++; st.joltSum += spike; st.joltMax = max(st.joltMax, spike); st.flareMax = max(st.flareMax, flare); st.tieMax = max(st.tieMax, tie)
        if (dur != null) { st.durSum += dur; st.durN++ }
        val res = arrayListOf(line)
        if (spike >= SHOCK_JOLT) {
            st.shocks++
            res.add("[SHOCK?] $key at ${f(kmh, 0)} km/h: kick of ${f(spike)} m/s2 (limit ${f(SHOCK_JOLT)}${if (spike >= 2.0) ", strong" else ""}), flare ${f(flare)}%, tie ${f(tie)}%" + (if (tft != null) ", tft ${f(tft, 0)} C" else ""))
        }
        return res
    }

    /** One line with every shift type seen so far. Null until there is one. */
    fun summary(): String? {
        if (stats.isEmpty()) return null
        return "[SHIFT-FAST SUMMARY] " + stats.entries.joinToString(" | ") { (k, v) ->
            "$k n=${v.n} spike avg=${f(v.joltSum / v.n)} max=${f(v.joltMax)} shocks=${v.shocks} flare max=${f(v.flareMax)}% tie max=${f(v.tieMax)}%" + (if (v.durN > 0) " dur avg=${v.durSum / v.durN} ms" else "")
        }
    }

    companion object {
        /** Output shaft rpm per km/h on this car (3019 rpm at 97 km/h, measured 11-22). */
        const val RPM_PER_KMH = 31.1
        const val SHOCK_JOLT = 1.0
    }
}
