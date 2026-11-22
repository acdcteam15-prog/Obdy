package com.example.obdy

import java.util.Locale
import kotlin.math.abs

/**
 * Turns live gearbox samples into log lines for a later analysis:
 *  [GEARBOX] one line per sample: gear, engine rpm, speed, load, fluid temp, turbine/output shaft rpm, measured ratio and deviation from the gear's normal ratio
 *  [SHIFT]   every gear or lever change
 *  [SLIP?]   a clutch (ratio) or the converter slipping while the car is steady, two samples in a row
 *  [GEARBOX SUMMARY] per-gear counts and deviations so far
 * Pure logic (no Android), unit tested. Samples are about 1.5 s apart: steady-state slip is measurable, shift duration is not.
 */
class GearLog {
    class Sample(
        val tMs: Long, val lever: Int?, val gear: Int?, val rpm: Double?, val kmh: Double?, val load: Double?, val tft: Double?,
        val turbine: Double? = null, val output: Double? = null,
    )

    private class Stat { var n = 0; var sum = 0.0; var worst = 0.0; var slips = 0 }

    private var last: Sample? = null
    private var lastLabel: String? = null
    private var inGearSince = 0L
    private var slipRun = 0
    private var convRun = 0
    private val stats = java.util.TreeMap<String, Stat>()

    fun reset() { last = null; lastLabel = null; slipRun = 0; convRun = 0; stats.clear() }

    private fun f(v: Double?, d: Int = 0) = if (v == null) "-" else String.format(Locale.US, "%.${d}f", v)

    /** Measured turbine/output ratio, only when the output shaft turns fast enough to be exact (about 8 km/h and more). */
    fun ratio(s: Sample): Double? = if (s.turbine != null && s.output != null && s.output >= 250) s.turbine / s.output else null

    /** Percent above (+) or below (-) the normal ratio of the gear the gearbox reports. */
    fun deviation(s: Sample): Double? {
        val r = ratio(s) ?: return null
        val g = s.gear ?: return null
        if (g !in 1..6) return null
        return (r / Gm.GEAR_RATIO[g - 1] - 1) * 100
    }

    private fun steady(a: Sample, b: Sample): Boolean {
        val o1 = a.output; val o2 = b.output
        if (o1 == null || o2 == null || o1 < 250 || abs(o2 - o1) / o1 > 0.06) return false
        val r1 = a.rpm; val r2 = b.rpm
        return r1 == null || r2 == null || r1 < 400 || abs(r2 - r1) / r1 <= 0.08
    }

    fun add(s: Sample): List<String> {
        val out = ArrayList<String>()
        val label = Gm.gearLabel(s.lever, s.gear)
        val dev = deviation(s)
        val r = ratio(s)
        out.add("[GEARBOX] $label lever=${s.lever?.let { "%02X".format(it) } ?: "-"} gear=${s.gear ?: "-"} rpm=${f(s.rpm)} kmh=${f(s.kmh)} load=${f(s.load)} tft=${f(s.tft)} turb=${f(s.turbine)} outp=${f(s.output)} ratio=${f(r, 3)} dev=${f(dev, 1)}")
        val p = last
        val pl = lastLabel
        var shifted = false
        if (p != null && pl != null && label != pl && s.lever != null) {
            val dir = when {
                p.gear != null && s.gear != null && pl[0] == label[0] && s.gear > p.gear -> "up"
                p.gear != null && s.gear != null && pl[0] == label[0] && s.gear < p.gear -> "down"
                else -> "lever"
            }
            out.add("[SHIFT] $pl->$label $dir at ${f(s.kmh)} km/h, rpm ${f(p.rpm)}->${f(s.rpm)}, load ${f(s.load)}%, tft ${f(s.tft)} C, ${f((s.tMs - inGearSince) / 1000.0, 1)} s in $pl")
            inGearSince = s.tMs; shifted = true; slipRun = 0; convRun = 0
        } else if (pl == null) inGearSince = s.tMs
        if (dev != null && s.gear != null) {
            val st = stats.getOrPut(label) { Stat() }
            // The gear byte and the shaft speeds are read ~0.1 s apart: right at a shift the byte can still show the old gear.
            // If the measured ratio fits another gear, it is that label lag, not slip.
            val lag = r != null && (1..6).any { it != s.gear && abs(r / Gm.GEAR_RATIO[it - 1] - 1) < 0.05 }
            val ok = p != null && !shifted && p.gear == s.gear && steady(p, s) && !lag
            if (ok) { st.n++; st.sum += dev; if (abs(dev) > abs(st.worst)) st.worst = dev }
            if (ok && abs(dev) >= 8) {
                slipRun++
                if (slipRun == 2) { st.slips++; out.add("[SLIP?] $label clutch: ratio ${f(r, 3)} is ${f(dev, 1)}% off the normal ${f(Gm.GEAR_RATIO[s.gear - 1], 3)} at ${f(s.kmh)} km/h, load ${f(s.load)}%, tft ${f(s.tft)} C") }
            } else slipRun = 0
            // Converter: in 5th/6th the engine should turn with the turbine (locked).
            if (ok && s.gear >= 5 && s.rpm != null && s.turbine != null && s.turbine > 600 && (s.rpm - s.turbine) / s.turbine >= 0.08) {
                convRun++
                if (convRun == 2) out.add("[SLIP?] $label converter: engine ${f(s.rpm)} vs turbine ${f(s.turbine)} rpm (+${f((s.rpm / s.turbine - 1) * 100)}%) at ${f(s.kmh)} km/h, load ${f(s.load)}%, tft ${f(s.tft)} C")
            } else convRun = 0
        }
        last = s; lastLabel = label
        return out
    }

    /** One line: per gear, steady samples, average and worst deviation, slip events. Null until there is something. */
    fun summary(): String? {
        val parts = stats.filter { it.value.n > 0 }.map { (k, v) -> "$k n=${v.n} avg=${f(v.sum / v.n, 1)}% worst=${f(v.worst, 1)}% slips=${v.slips}" }
        return if (parts.isEmpty()) null else "[GEARBOX SUMMARY] " + parts.joinToString(" | ")
    }
}
