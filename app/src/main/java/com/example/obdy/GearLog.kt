package com.example.obdy

import java.util.Locale

/**
 * Turns live gearbox samples into log lines that a later analysis can read:
 *  [GEARBOX] one line per sample (gear, rpm, speed, throttle, load, fluid temp, rpm per km/h)
 *  [SHIFT]   every gear or lever change, with speed and rpm before/after and time spent in the old gear
 *  [SLIP?]   rpm per km/h clearly above what this gear normally shows, two samples in a row, with throttle applied
 * Pure logic (no Android), so it is unit tested.
 */
class GearLog {
    class Sample(val tMs: Long, val lever: Int?, val gear: Int?, val rpm: Double?, val kmh: Double?, val thr: Double?, val load: Double?, val tft: Double?)

    private var last: Sample? = null
    private var lastLabel: String? = null
    private var inGearSince = 0L
    private val ratios = HashMap<String, ArrayDeque<Double>>()
    private var highRun = 0

    fun reset() { last = null; lastLabel = null; ratios.clear(); highRun = 0 }

    private fun f(v: Double?, d: Int = 0) = if (v == null) "-" else String.format(Locale.US, "%.${d}f", v)

    /** rpm per km/h: fixed for each gear once the converter is locked. Null when too slow to mean anything. */
    fun ratio(s: Sample): Double? = if (s.rpm != null && s.kmh != null && s.kmh >= 8 && s.rpm > 400) s.rpm / s.kmh else null

    fun add(s: Sample): List<String> {
        val out = ArrayList<String>()
        val label = Gm.gearLabel(s.lever, s.gear)
        val r = ratio(s)
        out.add("[GEARBOX] $label lever=${s.lever?.let { "%02X".format(it) } ?: "-"} gear=${s.gear ?: "-"} rpm=${f(s.rpm)} kmh=${f(s.kmh)} thr=${f(s.thr)} load=${f(s.load)} tft=${f(s.tft)} ratio=${f(r, 1)}")
        val p = last
        val pl = lastLabel
        if (p != null && pl != null && label != pl && s.lever != null) {
            val dir = when {
                p.gear != null && s.gear != null && pl[0] == label[0] && s.gear > p.gear -> "up"
                p.gear != null && s.gear != null && pl[0] == label[0] && s.gear < p.gear -> "down"
                else -> "lever"
            }
            val held = (s.tMs - inGearSince) / 1000.0
            out.add("[SHIFT] $pl->$label $dir at ${f(s.kmh)} km/h, rpm ${f(p.rpm)}->${f(s.rpm)}, thr ${f(s.thr)}%, tft ${f(s.tft)} C, ${f(held, 1)} s in $pl")
            inGearSince = s.tMs; highRun = 0
        } else if (pl == null) inGearSince = s.tMs
        // Slip check only in a real forward gear, steady enough and with throttle (coasting unlocks the converter).
        if (r != null && s.gear != null && (label.startsWith("D") || label.startsWith("M"))) {
            val q = ratios.getOrPut(label) { ArrayDeque() }
            val usual = if (q.size >= 10) q.sorted()[q.size / 2] else null
            if (usual != null && (s.thr ?: 0.0) >= 10 && r > usual * 1.15) {
                highRun++
                if (highRun == 2) out.add("[SLIP?] $label rpm per km/h ${f(r, 1)} vs usual ${f(usual, 1)} (+${f((r / usual - 1) * 100)}%) at ${f(s.kmh)} km/h, rpm ${f(s.rpm)}, thr ${f(s.thr)}%, tft ${f(s.tft)} C")
            } else {
                highRun = 0
                q.addLast(r); if (q.size > 60) q.removeFirst()
            }
        }
        last = s; lastLabel = label
        return out
    }
}
