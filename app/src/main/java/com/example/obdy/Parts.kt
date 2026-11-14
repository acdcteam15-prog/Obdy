package com.example.obdy

/** Where things are on the car. Rectangles are fractions (left, top, right, bottom) of a top view with the front at the top. */
/**
 * Where things are on the car, as a top view with the FRONT AT THE TOP and the driver (left-hand drive) on the LEFT.
 * Rectangles are fractions (left, top, right, bottom) of the drawing. The zones do not overlap, except underbody parts
 * (marked [under]) which sit below the cabin in a top view. Layout for the 2013 Malibu 2.5 L: transverse engine, intake
 * manifold facing the radiator, exhaust and catalyst at the back of the engine (firewall side), gearbox on the driver side,
 * timing chain and drive belt on the passenger side.
 */
enum class Zone(val label: String, val l: Float, val t: Float, val r: Float, val b: Float, val under: Boolean = false) {
    Radiator("Radiator, condenser and cooling fans (very front)", 0.24f, 0.035f, 0.76f, 0.075f),
    BayLeft("Engine bay, driver side front: battery, fuse box, air filter box", 0.19f, 0.085f, 0.36f, 0.175f),
    Intake("Front of the engine: intake manifold and throttle body", 0.36f, 0.085f, 0.66f, 0.15f),
    EngineTop("Top of the engine: coils, spark plugs, injectors", 0.36f, 0.15f, 0.66f, 0.225f),
    TimingEnd("Passenger end of the engine: timing chain, cam solenoids, drive belt, alternator", 0.66f, 0.085f, 0.81f, 0.27f),
    Gearbox("Gearbox (6-speed automatic) and its control module", 0.19f, 0.175f, 0.36f, 0.27f),
    ExhaustSide("Back of the engine (firewall side): exhaust manifold, catalyst, front O2 sensor", 0.36f, 0.225f, 0.66f, 0.30f),
    Firewall("Driver side at the firewall: brake master cylinder and ABS module", 0.19f, 0.27f, 0.36f, 0.33f),
    Cowl("Under the windscreen (cowl), passenger side: wiper motor, cabin air intake", 0.55f, 0.30f, 0.81f, 0.335f),
    EngineLow("Underneath the engine: oil pan, crank sensor, knock sensor", 0.40f, 0.30f, 0.55f, 0.36f, true),
    Steering("Steering column under the dash: power steering motor and sensors", 0.25f, 0.38f, 0.40f, 0.47f),
    UnderDash("Under the dashboard, driver side: OBD port, body control module", 0.40f, 0.38f, 0.52f, 0.47f),
    Dash("Dashboard centre and passenger side: radio, climate, glove box", 0.52f, 0.38f, 0.78f, 0.47f),
    Cabin("Cabin floor and centre console: airbag module, seats", 0.21f, 0.48f, 0.79f, 0.67f),
    ExhaustMid("Underneath the cabin: middle exhaust pipe, rear O2 sensor", 0.46f, 0.36f, 0.54f, 0.48f, true),
    FuelTank("Fuel tank and pump (under the rear seat)", 0.30f, 0.68f, 0.70f, 0.78f, true),
    Trunk("Boot (trunk): fuel pump module, spare wheel", 0.26f, 0.79f, 0.74f, 0.93f),
    ExhaustRear("Exhaust silencer and tail pipe", 0.44f, 0.935f, 0.56f, 0.975f, true),
    WheelFL("Front left wheel", 0.04f, 0.10f, 0.16f, 0.24f),
    WheelFR("Front right wheel", 0.84f, 0.10f, 0.96f, 0.24f),
    WheelRL("Rear left wheel", 0.04f, 0.71f, 0.16f, 0.85f),
    WheelRR("Rear right wheel", 0.84f, 0.71f, 0.96f, 0.85f),
}

class PartInfo(val name: String, val zone: Zone, val where: String, val tip: String = "", val category: String = "Engine")

object Parts {
    private val spark = PartInfo("Spark plugs and ignition coils", Zone.EngineTop, "Under the plastic engine cover on top of the engine: one coil pack sits over the four plugs (or one coil per plug). Cylinder 1 is at the passenger (timing chain) end.", "Swap coils between cylinders and see if the misfire code follows.")
    private val maf = PartInfo("Air flow sensor (MAF / IAT)", Zone.BayLeft, "Plugged into the air intake duct right after the air filter box, driver side front.", "Clean only with MAF cleaner spray. Never touch the sensing wire.")
    private val airbox = PartInfo("Air filter box and intake duct", Zone.BayLeft, "Black plastic box at the driver side front of the engine bay; the duct runs to the throttle body.", "Cracked duct or loose clamps cause lean codes (P0171) and air flow codes.")
    private val throttle = PartInfo("Electronic throttle body", Zone.Intake, "On the driver end of the intake manifold, at the front of the engine, where the intake duct connects.", "Clean with throttle-body cleaner (ignition off), then do the idle relearn in Tools.")
    private val map = PartInfo("Manifold pressure sensor (MAP)", Zone.Intake, "Small sensor plugged into the intake manifold at the front of the engine.")
    private val purge = PartInfo("EVAP purge solenoid", Zone.Intake, "On or near the intake manifold at the front of the engine, with a thin hose to the manifold and one going back to the canister.", "It should click when commanded; a stuck-open valve causes hard starting after refuelling.")
    private val evapCanister = PartInfo("EVAP canister and vent valve", Zone.FuelTank, "Under the car next to the fuel tank, near the rear axle.", "Check its hoses and the vent filter for dirt or water first.", "Fuel")
    private val cap = PartInfo("Fuel cap and filler neck", Zone.WheelRL, "Behind the fuel flap above the rear wheel.", "Tighten until it clicks. A worn seal is the cheapest EVAP fix.", "Fuel")
    private val fuelPump = PartInfo("Fuel pump and level sender", Zone.FuelTank, "Inside the fuel tank, reached from an access cover under the rear seat cushion or by lowering the tank.", "This car has a fuel pressure sensor (value 0A on Live): check pressure before replacing the pump.", "Fuel")
    private val fpcm = PartInfo("Fuel pump control module (FPCM)", Zone.Trunk, "The module that answers as 7E3/7EB. On this generation it is usually in the rear: trunk side trim or near the tank. Check the trim on the left side of the boot.", "Corroded connector = fuel pressure and pump codes.", "Fuel")
    private val injectors = PartInfo("Fuel injectors and fuel rail", Zone.EngineTop, "Under the engine cover, in the intake side of the head, one per cylinder.")
    private val upstreamO2 = PartInfo("Front oxygen sensor (sensor 1)", Zone.ExhaustSide, "At the BACK of the engine (firewall side), screwed into the exhaust manifold / catalyst inlet. Easiest from above after removing the heat shield.", "Let the exhaust cool fully: hot threads strip.")
    private val downstreamO2 = PartInfo("Rear oxygen sensor (sensor 2)", Zone.ExhaustMid, "Under the car, in the exhaust pipe just after the catalytic converter, below the front of the cabin.")
    private val cat = PartInfo("Catalytic converter", Zone.ExhaustSide, "Close-coupled: bolted right on the exhaust manifold at the back of the engine.", "Rule out misfires, exhaust leaks and a lazy rear O2 sensor before replacing it.")
    private val vvt = PartInfo("Cam phaser solenoids (VVT), intake and exhaust", Zone.TimingEnd, "On top of the cylinder head at the timing-chain end (passenger side), each with a 2-pin connector. The intake one is toward the front of the car.", "For P0011/P0014: check oil level and change oil first (dirty or low oil is the most common cause), then the solenoid connector and the solenoid screen.")
    private val timing = PartInfo("Timing chain, tensioner and cam phasers", Zone.TimingEnd, "Inside the timing cover at the passenger end of the engine.", "High mileage with P0011/P0016 together points to a stretched chain: needs a workshop.")
    private val crank = PartInfo("Crankshaft position sensor", Zone.EngineLow, "On the back of the engine block, low down near the starter; easiest from below.", "Check the connector for oil soaking.")
    private val camSensor = PartInfo("Camshaft position sensors", Zone.TimingEnd, "On the cylinder head near the timing-chain end, one per camshaft.")
    private val coolantSensor = PartInfo("Thermostat and coolant temperature sensor", Zone.Gearbox, "In the coolant housing at the driver end of the cylinder head, above the gearbox.", "Check the coolant level in the reservoir (engine cold) first.")
    private val fans = PartInfo("Cooling fans and radiator", Zone.Radiator, "Behind the front grille, in front of the engine.")
    private val battery = PartInfo("Battery and terminals", Zone.BayLeft, "Engine bay, driver side front (next to the fuse box). The negative cable also has a body ground nearby.", "Clean and tighten both clamps; check the ground strap from engine to body.", "Electrical")
    private val alternator = PartInfo("Alternator and drive belt", Zone.TimingEnd, "Front of the engine at the passenger end, driven by the serpentine belt.", "A glazed or loose belt squeals and charges poorly with lights and A/C on.", "Electrical")
    private val fuseBox = PartInfo("Fuse boxes", Zone.BayLeft, "Under-bonnet fuse block: driver side next to the battery. Second block in the cabin at the end of the dashboard (see Fuse boxes in Tools).", "", "Electrical")
    private val grounds = PartInfo("Main ground straps", Zone.Gearbox, "Engine-to-body strap near the gearbox and battery negative to body.", "A loose ground causes many random codes and low voltage.", "Electrical")
    private val trans = PartInfo("Gearbox: solenoids, valve body and fluid", Zone.Gearbox, "Driver side of the engine bay, under the battery and air box. Fluid is checked at a plug on the case (no dipstick): workshop job.", "Fluid level and condition first.", "Gearbox")
    private val tcm = PartInfo("Gearbox control module (TCM)", Zone.Gearbox, "Built INTO the gearbox (inside the valve body) on this 6-speed: no separate box to swap. Answers as 7E2/7EA.", "Check the round connector on the gearbox case for oil or corrosion.", "Gearbox")
    private val transSpeed = PartInfo("Gearbox speed sensors", Zone.Gearbox, "Inside or on the gearbox case; signals go through the main gearbox connector.", "", "Gearbox")
    private val oilSensor = PartInfo("Oil pressure sensor and oil level", Zone.TimingEnd, "Oil pressure sensor on the block near the oil filter; dipstick at the front of the engine.", "Check the dipstick before anything else.")
    private val knock = PartInfo("Knock sensor", Zone.EngineLow, "Bolted to the engine block under the intake manifold (front of the engine), low down.")
    private val abs = PartInfo("ABS / brake module (EBCM)", Zone.Firewall, "Driver side of the engine bay, near the firewall under the brake master cylinder, with metal brake lines going into it.", "", "Brakes")
    private val brakeSwitch = PartInfo("Brake pedal switch / sensor", Zone.Steering, "At the top of the brake pedal, under the dashboard.", "", "Brakes")
    private val eps = PartInfo("Electric power steering motor and control unit", Zone.Steering, "On the steering column behind the lower dash panel (driver knee area). Motor, torque sensor and control unit are one assembly.", "Most 'Service Power Steering' cases start with low voltage, a loose battery terminal or ground, or the column connectors. Test the battery first.", "Steering")
    private val steerAngle = PartInfo("Steering angle sensor", Zone.Steering, "On the steering column behind the wheel, part of the clock-spring area.", "After a battery change: turn the wheel lock to lock with the engine running so it relearns.", "Steering")
    private val obd = PartInfo("OBD port and body control module (BCM)", Zone.UnderDash, "OBD port under the dash above the driver's knees; the BCM is behind the left side of the dash near the cabin fuse block.", "", "Electrical")
    private val cluster = PartInfo("Instrument cluster", Zone.Dash, "Behind the steering wheel in the dashboard.", "", "Electrical")
    private val clockSpring = PartInfo("Clock spring and steering wheel airbag", Zone.Steering, "Behind the steering wheel. Have a workshop do this: airbags can fire.", "Never probe airbag wiring with the battery connected.", "Airbag")
    private val sdm = PartInfo("Airbag control module (SDM)", Zone.Cabin, "Under the centre console between the front seats.", "Workshop job.", "Airbag")
    private val seat = PartInfo("Seat belt buckles, pretensioners, occupant sensor", Zone.Cabin, "Under and beside the front seats. Check the yellow connector under the seat with the ignition OFF.", "", "Airbag")
    private val hvac = PartInfo("Climate control and blend door actuators", Zone.Dash, "Behind the dashboard centre; actuators on the heater box.", "", "Body")
    private val wiper = PartInfo("Wiper motor and cabin air filter", Zone.Cowl, "Wiper motor under the cowl panel below the windscreen; cabin filter behind the glove box.", "", "Body")

    /** Everything, for browsing without a code. */
    val all: List<PartInfo> get() = listOf(battery, alternator, fuseBox, grounds, obd, cluster, spark, injectors, throttle, maf, airbox, map, purge, vvt, timing, camSensor, crank, knock, oilSensor, coolantSensor, fans, upstreamO2, cat, downstreamO2, fuelPump, fpcm, evapCanister, cap, trans, tcm, transSpeed, abs, brakeSwitch, eps, steerAngle, clockSpring, sdm, seat, hvac, wiper) +
        listOf(wheel(Zone.WheelFL, "Front left"), wheel(Zone.WheelFR, "Front right"), wheel(Zone.WheelRL, "Rear left"), wheel(Zone.WheelRR, "Rear right"))

    private fun wheel(zone: Zone, name: String) = PartInfo("$name wheel speed sensor", zone, "In the wheel hub behind the brake disc, with a wire running up to the body.", "Clean metal dust from the sensor tip; check the wire where it flexes.", "Brakes")

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
            c in setOf("P0011", "P0012", "P0013", "P0014", "P2646") -> { l.add(vvt); l.add(oilSensor); l.add(timing) }
            c in setOf("P0016", "P0017", "P0008", "P0009") -> { l.add(timing); l.add(camSensor); l.add(crank); l.add(vvt) }
            c in setOf("P0010", "P0013", "P0020", "P2088", "P2089", "P2090", "P2091") -> { l.add(vvt) }
            c.startsWith("P012") || c.startsWith("P022") || c.startsWith("P21") || c.startsWith("P2135") || c.startsWith("P1516") -> { l.add(throttle) }
            c in setOf("P0335", "P0336") -> { l.add(crank) }
            c in setOf("P0340", "P0341", "P0365", "P0366") -> { l.add(camSensor) }
            c in setOf("P0325", "P0327", "P0328") -> { l.add(knock) }
            c in setOf("P0562", "P0563", "P0620", "P0621", "P0622") -> { l.add(battery); l.add(alternator); l.add(grounds); l.add(fuseBox) }
            c.startsWith("P008") || c.startsWith("P009") || c.startsWith("P018") || c.startsWith("P019") || c.startsWith("P023") || c.startsWith("P025") || c.startsWith("P062") && c >= "P0627" -> { l.add(fuelPump); l.add(fpcm) }
            c.startsWith("P050") || c.startsWith("P051") -> { l.add(throttle); l.add(airbox) }
            c == "P0480" || c == "P0481" -> { l.add(fans) }
            c.startsWith("P052") -> { l.add(oilSensor) }
            c in setOf("P0500", "P0501", "P0502", "P0503") -> { l.add(abs); l.add(transSpeed) }
            c.startsWith("P071") || c.startsWith("P072") -> { l.add(transSpeed); l.add(trans) }
            c.startsWith("P07") || c.startsWith("P08") || c.startsWith("P09") || c.startsWith("P17") -> { l.add(trans); l.add(tcm); l.add(transSpeed) }
            c.startsWith("P06") -> { l.add(fuseBox); l.add(battery) }
            c.startsWith("U0") -> { l.add(battery); l.add(grounds); l.add(fuseBox); l.add(obd) }
            c in setOf("C0035", "C0031", "C0040", "C0034") -> { l.add(wheel(Zone.WheelFL, "Front")); l.add(abs) }
            // Steering codes (power steering, steering angle/torque): NOT the wheel sensors.
            c.startsWith("C04") || c.startsWith("C05") || c.startsWith("C08") || c == "C0710" -> { l.add(eps); l.add(steerAngle); l.add(battery); l.add(grounds) }
            c.startsWith("C016") || c.startsWith("C0161") -> { l.add(brakeSwitch); l.add(abs) }
            c.startsWith("C0") || c.startsWith("C1") -> { l.add(abs); l.add(wheel(Zone.WheelFL, "Front left")); l.add(wheel(Zone.WheelFR, "Front right")); l.add(battery) }
            c.startsWith("B0") -> { l.add(sdm); l.add(seat); l.add(clockSpring) }
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
