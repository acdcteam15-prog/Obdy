package com.example.obdy

/** Where things are on the car. Rectangles are fractions (left, top, right, bottom) of a top view with the front at the top. */
enum class Zone(val label: String, val l: Float, val t: Float, val r: Float, val b: Float) {
    Radiator("Radiator and cooling fans (very front)", 0.28f, 0.03f, 0.72f, 0.075f),
    Intake("Air intake and air filter box", 0.30f, 0.085f, 0.70f, 0.14f),
    EngineTop("Top of the engine", 0.33f, 0.15f, 0.67f, 0.29f),
    BayLeft("Engine bay, driver side", 0.19f, 0.09f, 0.31f, 0.31f),
    BayRight("Engine bay, passenger side", 0.69f, 0.09f, 0.81f, 0.31f),
    EngineLow("Lower engine and transmission", 0.36f, 0.30f, 0.64f, 0.385f),
    UnderDash("Under the dashboard, driver side", 0.21f, 0.40f, 0.49f, 0.47f),
    Cabin("Cabin, seats and centre console", 0.21f, 0.48f, 0.79f, 0.69f),
    FuelTank("Fuel tank (under the rear seat)", 0.33f, 0.70f, 0.67f, 0.79f),
    Trunk("Boot (trunk)", 0.26f, 0.80f, 0.74f, 0.93f),
    ExhaustFront("Exhaust: manifold, catalytic converter, front sensors", 0.45f, 0.39f, 0.55f, 0.50f),
    ExhaustRear("Exhaust: middle pipe and silencer", 0.45f, 0.70f, 0.55f, 0.97f),
    WheelFL("Front left wheel", 0.04f, 0.10f, 0.15f, 0.23f),
    WheelFR("Front right wheel", 0.85f, 0.10f, 0.96f, 0.23f),
    WheelRL("Rear left wheel", 0.04f, 0.72f, 0.15f, 0.85f),
    WheelRR("Rear right wheel", 0.85f, 0.72f, 0.96f, 0.85f),
}

class PartInfo(val name: String, val zone: Zone, val where: String, val tip: String = "")

object Parts {
    private val spark = PartInfo("Spark plugs and ignition coils", Zone.EngineTop, "Under the plastic cover on top of the engine: one coil sits on each spark plug. Cylinder 1 is usually at the drive-belt end of the engine.", "Swap a coil with the next cylinder and see if the code follows it.")
    private val maf = PartInfo("Air flow sensor (MAF)", Zone.Intake, "In the air intake tube between the air filter box and the throttle body.", "Clean only with MAF cleaner. Never touch the wire.")
    private val airbox = PartInfo("Air filter box and filter", Zone.Intake, "Black plastic box at the front of the engine bay, joined to the intake tube by clips.", "A clogged filter and loose intake clamps cause lean and air flow codes.")
    private val throttle = PartInfo("Throttle body", Zone.EngineTop, "Where the air intake tube joins the engine's intake manifold.", "Clean with throttle-body cleaner, then do the idle relearn (Tools > Relearn guides).")
    private val map = PartInfo("Manifold pressure sensor (MAP)", Zone.EngineTop, "Small sensor bolted on or into the intake manifold.")
    private val purge = PartInfo("EVAP purge valve", Zone.EngineTop, "Small valve with two hoses near the intake manifold or valve cover.", "Listen for a click when the ignition turns on, or test with a hand vacuum pump.")
    private val evapCanister = PartInfo("EVAP canister and vent valve", Zone.FuelTank, "Near the fuel tank at the rear underneath the car.", "Often fails from dirt or water; check hoses first.")
    private val cap = PartInfo("Fuel cap and filler neck", Zone.Trunk, "Behind the fuel flap at the rear of the car.", "Tighten until it clicks. A cracked seal causes EVAP codes.")
    private val fuelPump = PartInfo("Fuel pump and fuel filter", Zone.FuelTank, "Inside the fuel tank. Access is usually under the rear seat cushion.", "Check fuel pressure before replacing it.")
    private val injectors = PartInfo("Fuel injectors", Zone.EngineTop, "In the intake manifold, one per cylinder, under the engine cover.")
    private val upstreamO2 = PartInfo("Upstream oxygen sensor (before the catalyst)", Zone.ExhaustFront, "Screwed into the exhaust manifold or front pipe, close to the engine.", "Let the exhaust cool first: the thread seizes easily.")
    private val downstreamO2 = PartInfo("Downstream oxygen sensor (after the catalyst)", Zone.ExhaustFront, "In the exhaust pipe just behind the catalytic converter, under the car.")
    private val cat = PartInfo("Catalytic converter", Zone.ExhaustFront, "Metal canister in the exhaust pipe under the front of the car, behind the engine.", "Check for exhaust leaks and earlier misfires before replacing it.")
    private val vvt = PartInfo("Cam timing solenoid (VVT) and oil level", Zone.EngineTop, "Solenoids are on the valve cover; the oil dipstick is at the front of the engine.", "Check the oil level and grade first.")
    private val crank = PartInfo("Crankshaft and camshaft sensors", Zone.EngineLow, "Crank sensor: low on the engine block where it meets the transmission. Cam sensors: on the cylinder head.")
    private val coolantSensor = PartInfo("Thermostat and coolant temperature sensor", Zone.EngineTop, "On the coolant outlet housing at the side of the engine; check the coolant level in the reservoir first.")
    private val fans = PartInfo("Cooling fans and radiator", Zone.Radiator, "Behind the front grille, in front of the engine.")
    private val battery = PartInfo("Battery and terminals", Zone.BayLeft, "Under the bonnet on the driver side on most Malibus (some versions keep it in the boot: check yours).", "Clean the terminals and tighten both clamps first.")
    private val alternator = PartInfo("Alternator and drive belt", Zone.BayRight, "On the front of the engine, turned by the drive belt.")
    private val fuseBox = PartInfo("Fuse boxes and grounds", Zone.BayLeft, "One box in the engine bay (driver side), one under the dashboard (driver side).")
    private val trans = PartInfo("Transmission: solenoids, valve body, fluid", Zone.EngineLow, "Next to the engine, between the front wheels. Many 6-speed GM automatics have no dipstick; fluid is checked at a plug under the car by a workshop.", "Fluid level and condition first.")
    private val transSpeed = PartInfo("Transmission speed sensors and connector", Zone.EngineLow, "On the transmission case; one electrical connector carries all the signals.")
    private val oilSensor = PartInfo("Oil pressure sensor and oil level", Zone.EngineTop, "Near the oil filter, at the front of the engine.", "Check the dipstick before anything else.")
    private val knock = PartInfo("Knock sensor", Zone.EngineLow, "Bolted to the engine block, under the intake manifold.")
    private val abs = PartInfo("ABS / brake module", Zone.BayLeft, "Near the brake master cylinder on the driver side of the engine bay.")
    private val obd = PartInfo("OBD port and body control module", Zone.UnderDash, "Under the dashboard above the driver's knees.")
    private val clockSpring = PartInfo("Clock spring, steering sensor, airbag module", Zone.Cabin, "Clock spring is behind the steering wheel; the airbag control module is low in the centre of the car. Have a workshop do this: airbags can fire.", "Never probe airbag connectors with the battery connected.")
    private val seat = PartInfo("Seat belt buckles and seat occupant sensor", Zone.Cabin, "Under and beside the front seats. Check the connector under the seat; do not unplug it with the ignition on.")

    private fun wheel(zone: Zone, name: String) = PartInfo("$name wheel speed sensor and tone ring", zone, "Behind the brake disc on the wheel hub, with a wire to the chassis.", "Clean metal dust from the sensor tip; check the wire.")

    /** The parts to look at for a code, most likely first. */
    fun forCode(codeIn: String): List<PartInfo> {
        val c = codeIn.take(5).uppercase()
        val l = ArrayList<PartInfo>()
        when {
            Regex("^P030[0-8]$").matches(c) -> { l.add(spark); l.add(injectors); l.add(throttle); l.add(airbox) }
            c == "P0171" || c == "P0174" || c == "P2177" || c == "P2187" -> { l.add(maf); l.add(airbox); l.add(throttle); l.add(purge); l.add(fuelPump); l.add(upstreamO2) }
            c == "P0172" || c == "P0175" || c == "P2178" || c == "P2188" -> { l.add(maf); l.add(injectors); l.add(purge); l.add(upstreamO2) }
            c == "P0420" || c == "P0430" -> { l.add(cat); l.add(downstreamO2); l.add(upstreamO2) }
            c.startsWith("P044") || c.startsWith("P045") -> { l.add(cap); l.add(purge); l.add(evapCanister) }
            c.startsWith("P013") || c.startsWith("P014") || c == "P0030" || c == "P0036" || c == "P2195" || c == "P2196" -> { l.add(upstreamO2); l.add(downstreamO2) }
            c.startsWith("P0101") || c.startsWith("P0102") || c.startsWith("P0103") -> { l.add(maf); l.add(airbox) }
            c.startsWith("P0106") || c.startsWith("P0107") || c.startsWith("P0108") -> { l.add(map); l.add(throttle) }
            c.startsWith("P011") && c != "P0116" -> { l.add(maf); l.add(airbox) }
            c in setOf("P0116", "P0117", "P0118", "P0128", "P0217") -> { l.add(coolantSensor); l.add(fans) }
            c in setOf("P0011", "P0012", "P0013", "P0014", "P0016", "P0017", "P2646") -> { l.add(vvt); l.add(crank) }
            c.startsWith("P012") || c.startsWith("P022") || c.startsWith("P21") || c.startsWith("P2135") || c.startsWith("P1516") -> { l.add(throttle) }
            c in setOf("P0335", "P0336", "P0340", "P0341", "P0365") -> { l.add(crank) }
            c in setOf("P0325", "P0327", "P0328") -> { l.add(knock) }
            c in setOf("P0562", "P0563", "P0620", "P0621", "P0622") -> { l.add(battery); l.add(alternator); l.add(fuseBox) }
            c.startsWith("P050") || c.startsWith("P051") -> { l.add(throttle); l.add(airbox) }
            c == "P0480" || c == "P0481" -> { l.add(fans) }
            c.startsWith("P052") -> { l.add(oilSensor) }
            c in setOf("P0500", "P0501", "P0502", "P0503") -> { l.add(abs); l.add(transSpeed) }
            c.startsWith("P071") || c.startsWith("P072") -> { l.add(transSpeed); l.add(trans) }
            c.startsWith("P07") || c.startsWith("P08") || c.startsWith("P09") || c.startsWith("P17") -> { l.add(trans); l.add(transSpeed) }
            c.startsWith("P06") -> { l.add(fuseBox); l.add(battery) }
            c.startsWith("U0") -> { l.add(battery); l.add(fuseBox); l.add(obd) }
            c in setOf("C0035", "C0031", "C0040", "C0034") -> { l.add(wheel(Zone.WheelFL, "Front")); l.add(abs) }
            c.startsWith("C0") -> { l.add(wheel(Zone.WheelFL, "A")); l.add(abs); l.add(battery) }
            c.startsWith("B0") -> { l.add(seat); l.add(clockSpring) }
            c.startsWith("B1") -> { l.add(fuseBox); l.add(obd) }
        }
        if (c == "C0035" || c == "C0031") { l.clear(); l.add(wheel(Zone.WheelFL, "Front left")); l.add(abs) }
        if (c == "C0040" || c == "C0034") { l.clear(); l.add(wheel(Zone.WheelFR, "Front right")); l.add(abs) }
        if (c == "C0045") { l.clear(); l.add(wheel(Zone.WheelRL, "Rear left")); l.add(abs) }
        if (c == "C0050") { l.clear(); l.add(wheel(Zone.WheelRR, "Rear right")); l.add(abs) }
        return l.distinctBy { it.name }
    }

    fun zoneByName(n: String): Zone? = Zone.values().firstOrNull { it.name.equals(n.trim(), ignoreCase = true) }
}
