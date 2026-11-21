package com.example.obdy

/** A module we try to talk to: [req] is the CAN id we send to, [resp] the id it answers from (11-bit, hex). */
class ModuleProbe(val name: String, val req: String, val resp: String, val verified: Boolean, val bus: Bus = Bus.Hs)

/** A manufacturer value (Mode 22). Experimental: only shown if the module answers. */
class ExtPid(
    val name: String, val unit: String, val header: String, val request: String, val bytes: Int,
    val decode: (IntArray) -> Double, val min: Double, val max: Double,
)

object Gm {
    /** Gearbox computer value 194F, read in each lever position on this Malibu (your own log, 15:46-15:48): P 01, R 02, N 04, D 06, M 08. */
    fun leverName(v: Int): String = when (v) { 0x01 -> "Park"; 0x02 -> "Reverse"; 0x04 -> "Neutral"; 0x06 -> "Drive"; 0x08 -> "Manual"; 0x00 -> "between positions (00)"; else -> "unknown value %02X".format(v) }

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
        l.add(ModuleProbe("Fuel pump control (FPCM)", "7E3", "7EB", true))   // the car names it itself: 090A -> "FPCM-FuelPumpCtrl"
        l.add(ModuleProbe("Module 7E4", "7E4", "7EC", true))
        l.add(ModuleProbe("Module 7E5", "7E5", "7ED", true))
        l.add(ModuleProbe("Module 7E6", "7E6", "7EE", true))
        l.add(ModuleProbe("Module 7E7", "7E7", "7EF", true))
        for (n in 0x241..0x25F) {
            val resp = "%X".format(n + 0x400)
            l.add(ModuleProbe("Module %X".format(n), "%X".format(n), resp, false))
        }
        return l
    }

    /**
     * Name from the GM diagnostic address a module reports for 1A B0 (read only). Only well-known addresses are named; the
     * others keep their CAN id so nothing is guessed. Proven on this car: 11 = ECM, 18 = TCM, 13 = FPCM (see 090A).
     */
    fun nameForDiagAddress(addr: Int?, fallback: String): String = when (addr) {
        0x11 -> "Engine control (ECM)"
        0x18 -> "Transmission control (TCM)"
        0x13 -> "Fuel pump control (FPCM)"
        0x28 -> "Brakes / ABS (EBCM)"
        0x40 -> "Body control (BCM)"
        0x58 -> "Airbag (SDM)"
        0x60 -> "Instrument cluster (IPC)"
        else -> if (addr == null) fallback else "$fallback · addr %02X".format(addr)
    }

    /** Probe for a module seen only by its answer id (from the "everyone" question): 6xx -> 2xx, 7E8..7EF -> 7E0..7E7. */
    fun probeForResponse(resp: String): ModuleProbe? {
        val r = resp.toIntOrNull(16) ?: return null
        return when (r) {
            in 0x7E8..0x7EF -> ModuleProbe("Module %X".format(r - 8), "%X".format(r - 8), resp, true)
            in 0x600..0x6FF -> ModuleProbe("Module %X".format(r - 0x400), "%X".format(r - 0x400), resp, false)
            else -> null
        }
    }

    /** The GM identification items (service 1A, read only) shown on the module page. */
    val identItems: List<Pair<String, String>> = listOf(
        "B0" to "Diagnostic address", "90" to "VIN stored in module", "C1" to "Part number (software 1)",
        "C2" to "Part number (software 2)", "CB" to "End model part number", "CC" to "Base model part number",
        "99" to "Programming date", "B4" to "Traceability number", "92" to "Supplier hardware number", "97" to "System name",
    )

    /** The same facts as UDS data identifiers (service 22), asked as a second route to the identity of a module. */
    val udsIdent: List<Pair<String, String>> = listOf(
        "F190" to "VIN (UDS)", "F187" to "Part number (UDS)", "F18C" to "Serial number (UDS)",
        "F194" to "Software number (UDS)", "F195" to "Software version (UDS)",
    )

    /** Turns a 1A answer payload into text: printable text, BCD date, 4-byte part number, or hex. */
    fun decodeIdent(did: String, b: IntArray): String {
        if (b.isEmpty()) return "-"
        if (did == "B0") return "%02X".format(b[0])
        if (did == "99" && b.size >= 4) return "%02X%02X-%02X-%02X".format(b[0], b[1], b[2], b[3])
        val txt = b.filter { it != 0 }
        if (txt.isNotEmpty() && txt.all { it in 0x20..0x7E }) return txt.joinToString("") { it.toChar().toString() }.trim()
        if (b.size == 4) return (((b[0].toLong() shl 24) or (b[1].toLong() shl 16) or (b[2].toLong() shl 8) or b[3].toLong())).toString()
        return b.joinToString(" ") { "%02X".format(it) }
    }

    /** GMLAN modules send their fault list (A9 81) as single frames from 5xx: 7E8 -> 5E8, 641 -> 541. */
    fun uudtId(resp: String): String = "%03X".format(0x500 or (resp.toInt(16) and 0xFF))

    /** Name for a response id such as 7E8 (engine) or 7EA (transmission). */
    fun moduleName(header: String): String {
        val h = header.uppercase()
        return probes().firstOrNull { it.resp == h }?.name ?: if (h.isEmpty()) "Engine / transmission" else "Module $h"
    }

    /** Live screen cards that are not standard PIDs (ids above 0xFF never go to the car as Mode 01). */
    const val GEAR_ID = 0x1001
    const val TFT_ID = 0x1002
    val leverPid = ExtPid("Lever (GM)", "", "7E2", "22194F", 1, { it[0].toDouble() }, 0.0, 15.0)
    /** TCM 199A: answered 01 in P (2026-11-19). Read as the gear the gearbox is in; confirm with a drive log. */
    val gearPid = ExtPid("Gear (GM)", "", "7E2", "22199A", 1, { it[0].toDouble() }, 0.0, 9.0)
    fun gearLabel(lever: Int?, gear: Int?): String = when (lever) {
        null -> "--"; 0x01 -> "P"; 0x02 -> "R"; 0x04 -> "N"; 0x00 -> "·"
        0x06 -> if (gear != null && gear in 1..6) "D$gear" else "D"
        0x08 -> if (gear != null && gear in 1..6) "M$gear" else "M"
        else -> "%02X".format(lever)
    }

    val extPids: List<ExtPid> = listOf(
        ExtPid("Transmission fluid temp (GM)", "°C", "7E2", "221940", 1, { it[0] - 40.0 }, -40.0, 160.0),
    )

    /** Guided manual relearns: these are the owner-manual style procedures, not scanner commands. */
    val relearns: List<Pair<String, String>> = listOf(
        "Oil life reset (do it on the dash)" to "Only right after an oil change, never at any other time. Ignition ON (engine off). Press the MENU button on the turn signal lever and scroll with the thumbwheel to REMAINING OIL LIFE. Press SET/CLR to reset to 100%. Ignition OFF. If CHANGE ENGINE OIL SOON comes back at the next start, repeat (owner's manual p.10-11).",
        "Throttle body / idle relearn" to "After cleaning the throttle body or disconnecting the battery: turn ignition ON (engine off) for 10 seconds, then OFF for 10 seconds, then start the engine and let it idle without touching the pedal until the idle is steady (about 2 to 3 minutes). Turn the A/C and lights off during this.",
        "After a battery change" to "Turn ignition ON for 1 minute without starting. Start and idle 3 minutes. Then drive gently 10 to 15 minutes so the idle, fuel trims and transmission shift points relearn. Radio codes, windows auto-up and the clock may need resetting.",
        "Fuel trim relearn" to "After fixing a vacuum leak, MAF or O2 problem: clear the codes, then drive 20 to 30 minutes with steady speeds and stops. Watch Long fuel trim on the Live screen: it should settle within ±5%.",
        "Transmission adaptive learn" to "A true reset of the transmission adaptive pressure needs a GM-specific scanner routine and is not offered by generic OBD. After a fluid change, drive normally and gently for about 100 km so the shifts adapt. Persistent harsh shifts: see a transmission specialist.",
    )
}
