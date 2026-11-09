package com.example.obdy

/** A module we try to talk to: [req] is the CAN id we send to, [resp] the id it answers from (11-bit, hex). */
class ModuleProbe(val name: String, val req: String, val resp: String, val verified: Boolean)

/** A manufacturer value (Mode 22). Experimental: only shown if the module answers. */
class ExtPid(
    val name: String, val unit: String, val header: String, val request: String, val bytes: Int,
    val decode: (IntArray) -> Double, val min: Double, val max: Double,
)

object Gm {
    /**
     * Standard OBD-II modules are certain (engine 7E0, transmission 7E2). The other addresses are a search: GM chassis and body
     * modules on 11-bit CAN usually answer from request + 0x400. The scan reports what actually answers, with the raw address,
     * so the names can be fixed after the first real scan of the car.
     */
    fun probes(): List<ModuleProbe> {
        val l = ArrayList<ModuleProbe>()
        l.add(ModuleProbe("Engine control (ECM)", "7E0", "7E8", true))
        l.add(ModuleProbe("Transmission control (TCM)", "7E2", "7EA", true))
        l.add(ModuleProbe("Module 7E1", "7E1", "7E9", true))
        l.add(ModuleProbe("Module 7E3", "7E3", "7EB", true))
        l.add(ModuleProbe("Module 7E4", "7E4", "7EC", true))
        for (n in 0x241..0x25F) {
            val resp = "%X".format(n + 0x400)
            l.add(ModuleProbe("Module %X".format(n), "%X".format(n), resp, false))
        }
        return l
    }

    /** Name for a response id such as 7E8 (engine) or 7EA (transmission). */
    fun moduleName(header: String): String {
        val h = header.uppercase()
        return probes().firstOrNull { it.resp == h }?.name ?: if (h.isEmpty()) "Engine / transmission" else "Module $h"
    }

    val extPids: List<ExtPid> = listOf(
        ExtPid("Transmission fluid temp (GM)", "°C", "7E2", "221940", 1, { it[0] - 40.0 }, -40.0, 160.0),
        ExtPid("Engine oil life (GM)", "%", "7E0", "22115E", 1, { it[0] * 100.0 / 255 }, 0.0, 100.0),
    )

    /** Guided manual relearns: these are the owner-manual style procedures, not scanner commands. */
    val relearns: List<Pair<String, String>> = listOf(
        "Throttle body / idle relearn" to "After cleaning the throttle body or disconnecting the battery: turn ignition ON (engine off) for 10 seconds, then OFF for 10 seconds, then start the engine and let it idle without touching the pedal until the idle is steady (about 2 to 3 minutes). Turn the A/C and lights off during this.",
        "After a battery change" to "Turn ignition ON for 1 minute without starting. Start and idle 3 minutes. Then drive gently 10 to 15 minutes so the idle, fuel trims and transmission shift points relearn. Radio codes, windows auto-up and the clock may need resetting.",
        "Fuel trim relearn" to "After fixing a vacuum leak, MAF or O2 problem: clear the codes, then drive 20 to 30 minutes with steady speeds and stops. Watch Long fuel trim on the Live screen: it should settle within ±5%.",
        "Transmission adaptive learn" to "A true reset of the transmission adaptive pressure needs a GM-specific scanner routine and is not offered by generic OBD. After a fluid change, drive normally and gently for about 100 km so the shifts adapt. Persistent harsh shifts: see a transmission specialist.",
    )
}
