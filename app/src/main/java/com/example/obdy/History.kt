package com.example.obdy

/** Something the owner logged by hand: refuelled, car wash, battery disconnected, warning light came on... */
class LoggedEvent(val id: String, val timeMs: Long, val type: String, val title: String, val detail: String, val km: Int)

/** One line of the car's history. [code] is set for fault events. */
class Event(val timeMs: Long, val type: String, val title: String, val detail: String, val km: Int, val code: String? = null, val loggedId: String? = null)

/** A possible link between an earlier event and a fault that appeared afterwards. [level]: 2 = likely, 1 = possible, 0 = weak. */
class Link(val code: String, val fault: Event, val cause: Event, val gapDays: Int, val gapKm: Int?, val level: Int, val why: String, val check: String)

class LinkRule(
    val name: String, val words: List<String>, val types: List<String>, val codes: List<String>,
    val windowDays: Int, val base: Int, val why: String, val check: String,
)

object Timeline {
    val typeLabels = mapOf(
        "service" to "Service", "repair" to "Repair", "parts" to "Parts", "tyres" to "Tyres", "fuel" to "Refuelled", "wash" to "Car wash or rain",
        "battery" to "Battery", "clear" to "Codes cleared", "fault" to "Fault appeared", "gone" to "Fault gone", "light" to "Warning light", "note" to "Note", "test" to "Test",
    )

    private fun typeForCategory(c: String) = when (c) { "Fuel" -> "fuel"; "Tyres" -> "tyres"; "Parts" -> "parts"; "Repair" -> "repair"; else -> "service" }

    /** All history in one list, newest first: repairs and services, things you logged, faults that appeared or went away between scans, tests. */
    fun build(repairs: List<RepairRecord>, logged: List<LoggedEvent>, scans: List<ScanRecord>, battery: List<BatteryRecord>): List<Event> {
        val out = ArrayList<Event>()
        for (r in repairs) out.add(Event(r.timeMs, typeForCategory(r.category), r.title, listOfNotNull(r.category, r.workshop.ifBlank { null }, r.notes.ifBlank { null }).joinToString("  ·  "), r.km))
        for (l in logged) out.add(Event(l.timeMs, l.type, l.title, l.detail, l.km, null, l.id))
        var prev = emptySet<String>()
        for (s in scans.sortedBy { it.timeMs }) {
            val now = s.faults.map { it.code.take(5) }.toSet()
            for (f in s.faults.distinctBy { it.code.take(5) }) {
                if (f.code.take(5) !in prev) out.add(Event(s.timeMs, "fault", f.code.take(5) + "  " + f.description, f.module + "  ·  " + f.kind, s.odometerKm, f.code.take(5)))
            }
            for (code in prev - now) out.add(Event(s.timeMs, "gone", "$code no longer reported", "", s.odometerKm, code))
            prev = now
        }
        for (b in battery) out.add(Event(b.timeMs, "test", "Battery test", listOfNotNull(b.rest?.let { "rest ${Calc.fmt(it, 2)} V" }, b.crank?.let { "crank ${Calc.fmt(it, 2)} V" }, b.idle?.let { "idle ${Calc.fmt(it, 2)} V" }, b.rev?.let { "2000 rpm ${Calc.fmt(it, 2)} V" }).joinToString("  ·  "), 0))
        return out.sortedByDescending { it.timeMs }
    }

    /** Last [n] events as plain lines for the AI. */
    fun summary(events: List<Event>, n: Int = 20): String =
        events.take(n).joinToString("\n") { "- ${Docs.formatDate(it.timeMs)}${if (it.km > 0) " (${it.km} km)" else ""}: ${typeLabels[it.type] ?: it.type}: ${it.title}" + (if (it.detail.isNotBlank()) " (${it.detail})" else "") }
}

object Linker {
    val rules = listOf(
        LinkRule("Oil work", listOf("oil", "dipstick"), emptyList(), listOf("P001", "P0016", "P0017", "P2646", "P052"), 30, 2,
            "Cam timing and oil pressure codes depend on the oil: wrong grade, too much or too little oil, or the wrong filter, right after an oil change.",
            "Check the dipstick (level between the marks), that the oil is dexos1 SAE 5W-30 and the filter is the right one, and that the oil life was reset correctly."),
        LinkRule("Oil work and misfire", listOf("oil"), emptyList(), listOf("P030", "P0316"), 14, 1,
            "Work under the bonnet can leave an ignition coil connector loose, and overfilled oil can foul the plugs.",
            "Check the coil connectors click in and the oil level is not above the top mark."),
        LinkRule("Plug or coil work", listOf("spark", "plug", "coil", "ignition"), emptyList(), listOf("P030", "P0351", "P0352", "P0353", "P0354", "P0316"), 30, 2,
            "A misfire after plug or coil work usually means a wrong plug or gap (this engine needs 0.9 mm), a coil connector not fully seated, a cracked plug or a dropped coil.",
            "Check plug type (ACDelco 41-108) and gap 0.9 mm, that every connector is clicked in, then swap that cylinder's coil with another to see if the code follows."),
        LinkRule("Intake or air filter work", listOf("air filter", "airbox", "air box", "intake", "throttle", "maf", "air flow", "hose"), emptyList(),
            listOf("P0101", "P0102", "P0103", "P0171", "P0172", "P0174", "P0175", "P2135", "P012", "P050", "P2176", "P2187", "P2188"), 30, 2,
            "After work on the air filter, intake or throttle body, an unplugged MAF connector, a loose intake clamp or a filter cover with missing screws lets in unmetered air. A cleaned throttle body needs an idle relearn.",
            "Check the filter box is closed with all six screws, the intake clamps and the MAF connector, then do the throttle/idle relearn (Tools > Relearn guides)."),
        LinkRule("Battery or electrical work", listOf("battery", "jump", "alternator", "terminal", "fuse"), listOf("battery"),
            listOf("U0", "P0562", "P0563", "P0620", "P2135", "P012", "P050", "C0"), 14, 2,
            "After the battery was disconnected, replaced or jump-started, modules lose their settings, and a weak or loosely clamped battery creates false codes and warnings.",
            "Test the battery and charging (Tools > Battery test), clean and tighten both terminals, do the idle/throttle relearn, then clear the codes and see if they return."),
        LinkRule("Refuelling or fuel", listOf("fuel", "petrol", "gas cap", "refuel", "filler"), listOf("fuel"),
            listOf("P044", "P045", "P030", "P017", "P0172", "P0175", "P0087"), 3, 2,
            "Right after refuelling, a loose fuel cap triggers EVAP codes, and poor or wrong fuel causes misfire, hesitation and lean or rich codes.",
            "Tighten the fuel cap until it clicks, try petrol from another station (TOP TIER, 87+ octane, no E85) and drive a full tank."),
        LinkRule("Cooling system work", listOf("coolant", "thermostat", "radiator", "water pump", "antifreeze", "cooling"), emptyList(),
            listOf("P0128", "P011", "P0217", "P0480", "P0481"), 30, 2,
            "After cooling-system work, air left in the system, a wrong mixture or a hose not seated causes temperature codes. This car has a specific coolant fill procedure.",
            "Check the surge tank level when cold, look for leaks, confirm both fans run, and have the system filled with the correct procedure."),
        LinkRule("Transmission fluid work", listOf("transmission", "atf", "gearbox", "dexron"), emptyList(), listOf("P07", "P08", "P09", "P17"), 30, 2,
            "After a transmission service, the wrong fluid (only DEXRON-VI is allowed) or a wrong fill level causes shift and pressure codes. The transmission is sealed and needs the dealer procedure.",
            "Confirm DEXRON-VI was used and the level was set with the proper procedure. Shifts may need about 100 km to adapt."),
        LinkRule("Brake or hub work", listOf("brake", "pad", "disc", "rotor", "caliper", "bearing", "hub"), emptyList(), listOf("C0", "P0571"), 30, 2,
            "After brake or hub work a wheel speed sensor can be knocked or a connector left unplugged. On this car the electric parking brake also has its own procedure for pad changes.",
            "Check each wheel speed sensor wire and connector, the sensor tip for dirt, and ask whether the parking brake service procedure was followed."),
        LinkRule("Wheel or tyre work", listOf("tyre", "tire", "rotation", "wheel", "alignment", "balance"), listOf("tyres"), listOf("C0", "P0500"), 14, 1,
            "After wheel or tyre work a wheel speed sensor wire can be disturbed, and different tyre sizes change the wheel speed signals.",
            "Check all tyres are the same size and the wheel speed sensor connectors are secure."),
        LinkRule("Exhaust work", listOf("exhaust", "oxygen", "o2", "catalytic", "muffler"), emptyList(),
            listOf("P013", "P014", "P0420", "P0430", "P2096", "P2097", "P2195", "P2196", "P0171", "P0174"), 30, 2,
            "After exhaust work, a leak before the sensor or an unplugged oxygen sensor connector gives sensor and fuel-trim codes.",
            "Check for exhaust leaks near the sensors and that the sensor connectors are plugged in."),
        LinkRule("Water or car wash", listOf("wash", "rain", "flood", "water", "puddle"), listOf("wash"),
            listOf("U0", "B0", "B1", "P030", "P0562", "C0", "P0340", "P0335"), 3, 2,
            "Water in connectors, the engine bay electrics or the floor wiring can cause communication and sensor codes.",
            "Dry the engine bay, check connectors for moisture, and fix any leak that lets water in. Codes often clear once everything is dry."),
        LinkRule("Added accessory", listOf("dash cam", "dashcam", "alarm", "stereo", "amplifier", "accessory", "tracker", "remote start", "installed", "fitted"), emptyList(),
            listOf("U0", "P0562", "P0563", "P0620", "B1"), 30, 2,
            "Added electrical accessories can drain the battery or disturb the network wiring.",
            "Unplug the accessory and see if the problem stops; check it is wired to a proper fuse tap and not into data wires."),
        LinkRule("Software or programming", listOf("software", "update", "reprogram", "flash", "programming"), emptyList(), listOf("P06", "U0", "P1", "P2"), 30, 2,
            "After programming or an update, modules can mismatch or need a relearn.",
            "Ask the dealer to re-check the programming and clear the codes."),
    )

    private val workTypes = setOf("service", "repair", "parts", "tyres", "battery", "wash", "fuel")
    private const val DAY = 86_400_000L

    fun matches(rule: LinkRule, e: Event): Boolean {
        val t = (e.title + " " + e.detail).lowercase()
        return e.type in rule.types || rule.words.any { t.contains(it) }
    }

    fun codeMatches(rule: LinkRule, code: String) = rule.codes.any { code.startsWith(it) }

    /** Faults that appeared shortly after something that could cause them. Best matches first, at most 3 per fault. */
    fun links(events: List<Event>): List<Link> {
        val faults = events.filter { it.type == "fault" && it.code != null }
        val causes = events.filter { it.type !in setOf("fault", "gone", "clear", "light", "test", "note") || it.type == "note" }
        val out = ArrayList<Link>()
        for (f in faults) {
            val code = f.code ?: continue
            val found = ArrayList<Link>()
            for (c in causes) {
                if (c.timeMs >= f.timeMs) continue
                val gapDays = ((f.timeMs - c.timeMs) / DAY).toInt()
                val gapKm = if (c.km > 0 && f.km > 0 && f.km >= c.km) f.km - c.km else null
                if (gapKm != null && gapKm > 2000) continue
                var best: Link? = null
                for (rule in rules) {
                    if (!matches(rule, c) || !codeMatches(rule, code) || gapDays > rule.windowDays) continue
                    val score = rule.base + (if (gapDays <= 3) 1 else 0) + (if (gapKm != null && gapKm <= 300) 1 else 0)
                    val level = if (score >= 3) 2 else if (score == 2) 1 else 0
                    val l = Link(code, f, c, gapDays, gapKm, level, rule.why, rule.check)
                    if (best == null || l.level > best.level) best = l
                }
                if (best == null && gapDays <= 3 && c.type in workTypes && c.type != "fuel" && c.type != "wash") {
                    best = Link(code, f, c, gapDays, gapKm, 0,
                        "Something was worked on shortly before this fault appeared. A loosened connector or hose, a wrong part or a disturbed wire is a common cause.",
                        "Ask what exactly was touched, look at the connectors and hoses near that area, and compare with the code's area.")
                }
                if (best != null) found.add(best)
            }
            out.addAll(found.sortedWith(compareByDescending<Link> { it.level }.thenBy { it.gapDays }).take(3))
        }
        return out.sortedWith(compareByDescending<Link> { it.level }.thenByDescending { it.fault.timeMs })
    }

    /** Repeating problems and repairs that did not fix a code, in plain words. */
    fun insights(events: List<Event>): List<String> {
        val out = ArrayList<String>()
        val faults = events.filter { it.type == "fault" && it.code != null }.sortedBy { it.timeMs }
        for ((code, list) in faults.groupBy { it.code!! }) {
            if (list.size >= 2) {
                val gap = (list[1].timeMs - list[0].timeMs) / DAY
                out.add("$code has appeared ${list.size} times. It came back ${gap} days after it first went away, so the cause has not been fixed yet.")
            }
            val first = list[0].timeMs
            val repairsAfter = events.filter { it.type in setOf("service", "repair", "parts") && it.timeMs > first }
            for (r in repairsAfter) {
                val rule = rules.firstOrNull { matches(it, r) && codeMatches(it, code) } ?: continue
                if (list.any { it.timeMs > r.timeMs }) {
                    out.add("$code was reported before and again after \"${r.title}\" (${Docs.formatDate(r.timeMs)}). That work may not have been the cause: " + rule.check.substringBefore('.') + ", or look for a different cause (Faults > Find the cause).")
                    break
                }
            }
        }
        return out.take(6)
    }
}
