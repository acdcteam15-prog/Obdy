package com.example.obdy

/**
 * The two fuse boxes of the 2013 Malibu, with every slot's number and use from the owner's manual (pages 10-32 to 10-37).
 * Positions come from the manual's diagrams, as fractions of the box picture (x, y, width, height).
 * The manual does not list amp ratings: they are printed on each fuse and on the fuse box lid.
 */
class FuseSlot(val box: String, val kind: String, val id: String, val usage: String, val x: Float, val y: Float, val w: Float, val h: Float) {
    val key: String get() = "$box|$kind|$id"
    val clickable: Boolean get() = kind != "Nut"
    val boxName: String get() = if (box == "engine") "Engine compartment" else "Dash (driver side)"
    val label: String get() = (if (kind.endsWith("fuse") || kind == "Circuit breaker") kind + " " + id else if (kind == "Relay") "Relay " + id else kind + " " + id)
    val title: String get() = "$boxName  ·  $label  ·  $usage"
}

class FuseBox(val key: String, val name: String, val shortName: String, val where: String, val open: String, val aspect: Float, val slots: List<FuseSlot>)

object Fuses {
    private val engineSlots = listOf(
        FuseSlot("engine", "Mini fuse", "1", "Transmission Control Module Battery", 0.1583f, 0.0709f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "2", "Engine Control Module Battery", 0.2263f, 0.0709f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "3", "Air Conditioning Compressor Clutch", 0.2943f, 0.0709f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "5", "Engine Control Module Ignition", 0.2943f, 0.1152f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "8", "Spare", 0.3731f, 0.1846f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "9", "Ignition Coils", 0.3731f, 0.2304f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "10", "Engine Control Module", 0.3731f, 0.2792f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "11", "Emissions", 0.3731f, 0.3294f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "13", "Transmission Module Ignition", 0.4717f, 0.1846f, 0.0428f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "14", "Cabin Heater Coolant Pump", 0.6239f, 0.0842f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "15", "MGU Coolant Pump", 0.6239f, 0.1315f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "16", "Aero Shutter / eAssist Ignition", 0.6239f, 0.1787f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "17", "SDM Ignition", 0.6239f, 0.2245f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "18", "Spare", 0.6239f, 0.2733f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "23", "eAssist Module", 0.6881f, 0.3368f, 0.0428f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "29", "Left Seat Power Lumbar Control", 0.8028f, 0.0709f, 0.0436f, 0.0325f),
        FuseSlot("engine", "Mini fuse", "30", "Right Seat Power Lumbar Control", 0.8570f, 0.0709f, 0.0428f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "31", "eAssist Module", 0.9159f, 0.0709f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "32", "Reverse Lamps / Interior Lamps", 0.9159f, 0.1123f, 0.0436f, 0.0295f),
        FuseSlot("engine", "Mini fuse", "33", "Front Heated Seats", 0.9159f, 0.1581f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "34", "Antilock Brake System Valve", 0.9159f, 0.2068f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "35", "Amplifier", 0.9159f, 0.2570f, 0.0436f, 0.0295f),
        FuseSlot("engine", "Mini fuse", "37", "Right High Beam", 0.0428f, 0.8434f, 0.0153f, 0.0827f),
        FuseSlot("engine", "Mini fuse", "38", "Left High Beam", 0.0673f, 0.8434f, 0.0161f, 0.0827f),
        FuseSlot("engine", "Mini fuse", "46", "Cooling Fan", 0.4687f, 0.6721f, 0.0161f, 0.0842f),
        FuseSlot("engine", "Mini fuse", "47", "Emissions", 0.4954f, 0.6721f, 0.0161f, 0.0842f),
        FuseSlot("engine", "Mini fuse", "48", "Foglamp", 0.4717f, 0.7843f, 0.0428f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "51", "Horn / Dual Horn", 0.4954f, 0.8434f, 0.0161f, 0.0827f),
        FuseSlot("engine", "Mini fuse", "52", "Cluster Ignition", 0.6239f, 0.6824f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "53", "Inside Rear View Mirror / Rear Camera / Fuel Module Ignition", 0.6239f, 0.7371f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "54", "Heating, Ventilation and Air Conditioning Module Ignition", 0.6239f, 0.7843f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "55", "Front Power Windows / Mirrors", 0.6239f, 0.8331f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "56", "Windshield Washer", 0.6239f, 0.8833f, 0.0436f, 0.0295f),
        FuseSlot("engine", "Mini fuse", "57", "Spare", 0.6888f, 0.6824f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "60", "Heated Mirror", 0.8157f, 0.5864f, 0.0428f, 0.0295f),
        FuseSlot("engine", "Mini fuse", "62", "Canister Vent Solenoid", 0.8570f, 0.6352f, 0.0428f, 0.0295f),
        FuseSlot("engine", "Mini fuse", "66", "SAIR Solenoid", 0.8570f, 0.8804f, 0.0428f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "67", "Fuel Module", 0.9159f, 0.5864f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "69", "Battery Voltage Sensor", 0.9159f, 0.7134f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "70", "Lane Departure / Rear Parking Aid", 0.9159f, 0.7622f, 0.0436f, 0.0310f),
        FuseSlot("engine", "Mini fuse", "71", "PEPS BATT", 0.9159f, 0.8124f, 0.0436f, 0.0310f),
        FuseSlot("engine", "J-case fuse", "6", "Front Wiper", 0.3035f, 0.2910f, 0.0581f, 0.1610f),
        FuseSlot("engine", "J-case fuse", "12", "Starter", 0.4648f, 0.0561f, 0.0489f, 0.1004f),
        FuseSlot("engine", "J-case fuse", "21", "Rear Power Window", 0.6751f, 0.0857f, 0.0573f, 0.0842f),
        FuseSlot("engine", "J-case fuse", "22", "Sunroof", 0.6751f, 0.1994f, 0.0573f, 0.0842f),
        FuseSlot("engine", "J-case fuse", "24", "Front Power Window", 0.7378f, 0.0857f, 0.0573f, 0.0842f),
        FuseSlot("engine", "J-case fuse", "25", "PEPS MTR", 0.8135f, 0.1285f, 0.0573f, 0.0842f),
        FuseSlot("engine", "J-case fuse", "26", "Antilock Brake System Pump", 0.8135f, 0.2393f, 0.0573f, 0.0842f),
        FuseSlot("engine", "J-case fuse", "27", "Electric Parking Brake", 0.7859f, 0.3383f, 0.0573f, 0.0842f),
        FuseSlot("engine", "J-case fuse", "28", "Rear Defogger", 0.7859f, 0.4476f, 0.0573f, 0.0857f),
        FuseSlot("engine", "J-case fuse", "41", "Brake Vacuum Pump", 0.0994f, 0.6573f, 0.0489f, 0.1108f),
        FuseSlot("engine", "J-case fuse", "42", "Cooling Fan K2", 0.1667f, 0.7578f, 0.0489f, 0.1108f),
        FuseSlot("engine", "J-case fuse", "44", "Transmission Auxiliary Pump (eAssist)", 0.3043f, 0.7578f, 0.0489f, 0.1108f),
        FuseSlot("engine", "J-case fuse", "45", "Cooling Fan K1", 0.3723f, 0.7578f, 0.0489f, 0.1108f),
        FuseSlot("engine", "J-case fuse", "59", "Air Pump Emissions", 0.7408f, 0.6411f, 0.0489f, 0.1004f),
        FuseSlot("engine", "Mini relay", "7", "Powertrain", 0.4281f, 0.2363f, 0.1063f, 0.1950f),
        FuseSlot("engine", "Mini relay", "9", "Cooling Fan K2", 0.0489f, 0.4461f, 0.0956f, 0.1965f),
        FuseSlot("engine", "Mini relay", "13", "Cooling Fan K1", 0.3853f, 0.4431f, 0.1070f, 0.1950f),
        FuseSlot("engine", "Mini relay", "15", "Run / Crank", 0.5696f, 0.4431f, 0.1070f, 0.1950f),
        FuseSlot("engine", "Mini relay", "17", "Window / Mirror Defogger", 0.8532f, 0.3456f, 0.1063f, 0.1965f),
        FuseSlot("engine", "Micro relay", "1", "Air Conditioning Compressor Clutch", 0.0275f, 0.0547f, 0.0856f, 0.1078f),
        FuseSlot("engine", "Micro relay", "2", "Starter Solenoid", 0.3670f, 0.0606f, 0.0864f, 0.1034f),
        FuseSlot("engine", "Micro relay", "4", "Front Wiper Speed", 0.1590f, 0.2910f, 0.0573f, 0.1610f),
        FuseSlot("engine", "Micro relay", "5", "Front Wiper On", 0.2347f, 0.2777f, 0.0581f, 0.1610f),
        FuseSlot("engine", "Micro relay", "6", "Cabin Pump eAssist", 0.1216f, 0.1743f, 0.0550f, 0.0871f),
        FuseSlot("engine", "Micro relay", "10", "Cooling Fan K3", 0.1590f, 0.4771f, 0.0573f, 0.1610f),
        FuseSlot("engine", "Micro relay", "11", "Transmission Oil Pump", 0.2324f, 0.4874f, 0.0550f, 0.0886f),
        FuseSlot("engine", "Micro relay", "16", "Air Pump Emissions", 0.6850f, 0.4077f, 0.0933f, 0.1817f),
        FuseSlot("engine", "Nut", "", "", 0.1988f, 0.1285f, 0.0619f, 0.1196f),
        FuseSlot("engine", "Nut", "", "", 0.7401f, 0.1979f, 0.0619f, 0.1196f),
        FuseSlot("engine", "Nut", "", "", 0.2202f, 0.6012f, 0.0619f, 0.1196f),
    )

    private val panelSlots = listOf(
        FuseSlot("panel", "Relay", "K1", "Trunk Release", 0.1738f, 0.0556f, 0.1953f, 0.0992f),
        FuseSlot("panel", "Relay", "K2", "Not Used", 0.3965f, 0.0556f, 0.1953f, 0.0992f),
        FuseSlot("panel", "Relay", "K3", "Power Outlet Relay", 0.6211f, 0.0556f, 0.1953f, 0.0992f),
        FuseSlot("panel", "Mini fuse", "1", "Steering Wheel Controls Backlight", 0.1611f, 0.2322f, 0.1494f, 0.0677f),
        FuseSlot("panel", "Mini fuse", "2", "Right Rear Turn Signal, Left Mirror Turn Signal, Left Front Turn Signal, Door Locks", 0.1611f, 0.3785f, 0.1494f, 0.0689f),
        FuseSlot("panel", "Mini fuse", "3", "Left Stoplamp, Left DRL Lamp, Headlamp Control, Right Taillamp, Right Park/Side Markers, Right Mirror Turn, Right Front Turn Signals", 0.1611f, 0.5296f, 0.1494f, 0.0677f),
        FuseSlot("panel", "Mini fuse", "4", "Radio", 0.3184f, 0.2201f, 0.0547f, 0.1838f),
        FuseSlot("panel", "Mini fuse", "5", "OnStar", 0.3184f, 0.4184f, 0.0547f, 0.1838f),
        FuseSlot("panel", "Mini fuse", "6", "Front Accessory Power Outlet", 0.3809f, 0.2926f, 0.0547f, 0.1838f),
        FuseSlot("panel", "Mini fuse", "7", "Console Bin Power Outlet", 0.4443f, 0.2926f, 0.0547f, 0.1838f),
        FuseSlot("panel", "Mini fuse", "8", "License Plate Lamp, Center High Mounted Stoplamp, Rear Fog Lamps, Right Front Park/Side Markers, LED Indicator Dim, Washer Pump, Right Stoplamp, Trunk Release", 0.2109f, 0.6119f, 0.0547f, 0.1862f),
        FuseSlot("panel", "Mini fuse", "9", "Left Low-Beam Headlamp, DRL", 0.2715f, 0.6119f, 0.0547f, 0.1862f),
        FuseSlot("panel", "Mini fuse", "14", "Diagnostic Link Connector", 0.5117f, 0.2938f, 0.1504f, 0.0689f),
        FuseSlot("panel", "Mini fuse", "15", "Airbag, SDM", 0.5117f, 0.3748f, 0.1504f, 0.0689f),
        FuseSlot("panel", "Mini fuse", "16", "Trunk Release", 0.5117f, 0.4559f, 0.1504f, 0.0677f),
        FuseSlot("panel", "Mini fuse", "17", "Heater Ventilation Air Conditioning Controller", 0.5117f, 0.5369f, 0.1504f, 0.0665f),
        FuseSlot("panel", "Mini fuse", "18", "Audio Main", 0.6816f, 0.2177f, 0.1504f, 0.0677f),
        FuseSlot("panel", "Mini fuse", "19", "Displays", 0.6816f, 0.2950f, 0.1504f, 0.0677f),
        FuseSlot("panel", "Mini fuse", "20", "Passenger Occupant Sense", 0.6816f, 0.3748f, 0.1504f, 0.0677f),
        FuseSlot("panel", "Mini fuse", "21", "Instrument Panel Cluster", 0.6816f, 0.4547f, 0.1504f, 0.0677f),
        FuseSlot("panel", "Mini fuse", "22", "Ignition Switch", 0.6816f, 0.5345f, 0.1504f, 0.0677f),
        FuseSlot("panel", "Mini fuse", "23", "Right Low-Beam Headlamp, DRL", 0.6816f, 0.6143f, 0.1504f, 0.0677f),
        FuseSlot("panel", "Mini fuse", "24", "Ambient Light, Switch Backlighting (LED), Trunk Lamp, Shift Lock, Key Capture", 0.6816f, 0.6941f, 0.1504f, 0.0677f),
        FuseSlot("panel", "Mini fuse", "25", "110V AC", 0.6816f, 0.7739f, 0.1504f, 0.0677f),
        FuseSlot("panel", "Mini fuse", "26", "Spare", 0.6816f, 0.8513f, 0.1504f, 0.0677f),
        FuseSlot("panel", "J-case fuse", "10", "Body Control Module 8 (J-Case Fuse), Power Locks", 0.3438f, 0.6119f, 0.1533f, 0.1911f),
        FuseSlot("panel", "J-case fuse", "11", "Front Heater Ventilation Air Conditioning / Blower (J-Case Fuse)", 0.5127f, 0.6119f, 0.1543f, 0.1911f),
        FuseSlot("panel", "Circuit breaker", "12", "Passenger Seat (Circuit Breaker)", 0.1855f, 0.8126f, 0.0742f, 0.1282f),
        FuseSlot("panel", "Circuit breaker", "13", "Driver Seat (Circuit Breaker)", 0.3105f, 0.8126f, 0.0732f, 0.1282f),
    )

    val engine = FuseBox(
        "engine", "Engine compartment fuse box", "Engine bay",
        "Driver side of the engine compartment, near the battery (the battery is under a cover).",
        "Remove the cover, keep it on afterwards: spilled liquid damages electrics. Diagram below is the view from above, as in the manual.",
        1308f / 677f, engineSlots,
    )

    val panel = FuseBox(
        "panel", "Instrument panel fuse box", "Dash",
        "In the instrument panel on the driver's side, low down near the pedals.",
        "Open the fuse panel door by pulling down at the top. Press in on the sides of the door to release it, or pull the door toward you to release it from the hinge.",
        1024f / 827f, panelSlots,
    )

    val boxes = listOf(engine, panel)
    val all: List<FuseSlot> = (engineSlots + panelSlots).filter { it.clickable }

    fun box(key: String): FuseBox = if (key == "panel") panel else engine

    fun find(box: String, kind: String, id: String): FuseSlot? = all.firstOrNull { it.box == box && it.kind == kind && it.id == id }

    fun describeKind(kind: String): String = when (kind) {
        "Mini fuse" -> "Small blade fuse. Look at the thin metal band inside: if it is broken or melted, the fuse is blown. The rating is printed on the fuse."
        "J-case fuse" -> "Larger cartridge-style fuse for heavier loads (fans, pumps, starter, windows). Look through the window for a broken band. Read the rating on the fuse. Repeated blowing means a short: do not just fit another."
        "Mini relay" -> "Plug-in relay (cube). It clicks when it switches. You can test it by swapping it with another relay of the same type."
        "Micro relay" -> "Small plug-in relay. It clicks when it switches. Swap with another micro relay of the same type to test."
        "Relay" -> "Plug-in relay in the dash fuse box."
        "Circuit breaker" -> "A resettable breaker (seat motors). It resets itself after it cools: it does not need replacing."
        else -> ""
    }

    // ------------------------------------------------------------------ search ("what is not working?")
    private val synonyms = mapOf(
        "headlight" to listOf("headlamp", "low-beam", "high beam"), "headlights" to listOf("headlamp", "low-beam", "high beam"), "headlamp" to listOf("headlamp", "low-beam", "high beam"),
        "high" to listOf("high beam"), "low" to listOf("low-beam"),
        "brake" to listOf("stoplamp", "brake"), "stop" to listOf("stoplamp"), "tail" to listOf("taillamp", "park/side"), "taillight" to listOf("taillamp", "park/side"),
        "indicator" to listOf("turn signal"), "indicators" to listOf("turn signal"), "blinker" to listOf("turn signal"), "signal" to listOf("turn signal"), "turn" to listOf("turn signal"),
        "lighter" to listOf("power outlet"), "cigarette" to listOf("power outlet"), "socket" to listOf("power outlet"), "charger" to listOf("power outlet"), "12v" to listOf("power outlet"), "usb" to listOf("power outlet"), "outlet" to listOf("power outlet"),
        "obd" to listOf("diagnostic link connector"), "scanner" to listOf("diagnostic link connector"), "dlc" to listOf("diagnostic link connector"), "diagnostic" to listOf("diagnostic link connector"),
        "reverse" to listOf("reverse lamps"), "backup" to listOf("reverse lamps"), "back-up" to listOf("reverse lamps"),
        "fog" to listOf("foglamp", "fog lamps"), "ac" to listOf("air conditioning"), "a/c" to listOf("air conditioning"), "aircon" to listOf("air conditioning"), "compressor" to listOf("compressor"),
        "heater" to listOf("heater", "heated"), "blower" to listOf("blower"), "climate" to listOf("air conditioning", "heater ventilation"),
        "fan" to listOf("cooling fan"), "fans" to listOf("cooling fan"), "cooling" to listOf("cooling fan", "coolant"),
        "wiper" to listOf("wiper"), "wipers" to listOf("wiper"), "windscreen" to listOf("wiper", "washer"), "washer" to listOf("washer"),
        "window" to listOf("window"), "windows" to listOf("window"), "mirror" to listOf("mirror"), "mirrors" to listOf("mirror"),
        "stereo" to listOf("radio", "audio", "amplifier"), "speakers" to listOf("amplifier", "audio"), "music" to listOf("radio", "audio", "amplifier"), "bluetooth" to listOf("radio", "onstar"), "navigation" to listOf("radio", "displays", "onstar"),
        "locks" to listOf("door locks", "power locks"), "lock" to listOf("door locks", "power locks"), "central" to listOf("door locks", "power locks"),
        "boot" to listOf("trunk"), "trunk" to listOf("trunk"), "tailgate" to listOf("trunk"),
        "seat" to listOf("seat"), "seats" to listOf("seat"), "sunroof" to listOf("sunroof"),
        "abs" to listOf("antilock"), "esc" to listOf("antilock"), "stabilitrak" to listOf("antilock"), "traction" to listOf("antilock"),
        "handbrake" to listOf("electric parking brake"), "parking" to listOf("electric parking brake", "park"), "epb" to listOf("electric parking brake"),
        "start" to listOf("starter", "ignition switch", "peps", "run / crank"), "starter" to listOf("starter"), "crank" to listOf("starter", "run / crank"), "key" to listOf("ignition switch", "peps", "key capture"), "button" to listOf("ignition switch", "peps"),
        "ecu" to listOf("control module"), "ecm" to listOf("control module"), "tcm" to listOf("transmission"), "gearbox" to listOf("transmission"), "transmission" to listOf("transmission"),
        "airbag" to listOf("airbag", "sdm"), "srs" to listOf("airbag", "sdm"), "cluster" to listOf("cluster"), "dashboard" to listOf("cluster", "displays"), "speedometer" to listOf("cluster"), "display" to listOf("displays"), "screen" to listOf("displays"),
        "fuel" to listOf("fuel module"), "pump" to listOf("pump"), "coil" to listOf("ignition coils"), "coils" to listOf("ignition coils"), "ignition" to listOf("ignition"), "spark" to listOf("ignition coils"),
        "emission" to listOf("emissions"), "oxygen" to listOf("emissions"), "o2" to listOf("emissions"), "evap" to listOf("canister vent"), "canister" to listOf("canister vent"),
        "defrost" to listOf("defogger", "heated mirror"), "defogger" to listOf("defogger"), "demister" to listOf("defogger", "heated mirror"),
        "horn" to listOf("horn"), "interior" to listOf("interior lamps", "ambient"), "dome" to listOf("interior lamps"), "light" to listOf("lamp"), "lights" to listOf("lamp"), "lamp" to listOf("lamp"),
        "parking-aid" to listOf("parking aid"), "camera" to listOf("rear camera"), "sensor" to listOf("sensor", "parking aid"), "sensors" to listOf("sensor", "parking aid"),
        "battery" to listOf("batt", "battery"), "onstar" to listOf("onstar"), "occupant" to listOf("occupant"),
    )

    /** Fuses and relays whose use matches what the person types. Every word must match; best matches first. */
    fun search(queryIn: String): List<FuseSlot> {
        val q = queryIn.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        // a number or K1..K3: find by id
        if (Regex("^(fuse|relay)?\\s*#?\\s*(k?\\d{1,2})$").matches(q)) {
            val id = Regex("(k?\\d{1,2})$").find(q)!!.value.uppercase()
            return all.filter { it.id.equals(id, ignoreCase = true) }
        }
        val tokens = q.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        val scored = ArrayList<Pair<FuseSlot, Int>>()
        for (s in all) {
            val u = s.usage.lowercase()
            var hit = 0
            for (t in tokens) {
                val alts = (synonyms[t] ?: emptyList()) + t
                if (alts.any { u.contains(it) }) hit++
            }
            if (hit == tokens.size) scored.add(s to hit)
        }
        return scored.sortedBy { it.first.usage.length }.map { it.first }.take(16)
    }

    // ------------------------------------------------------------------ fuses to check for a fault code
    private fun f(box: String, kind: String, id: String) = find(box, kind, id)

    fun forCode(codeIn: String): List<FuseSlot> {
        val c = codeIn.take(5).uppercase()
        val l: List<FuseSlot?> = when {
            Regex("^P030[0-8]$").matches(c) || c == "P0316" || c.startsWith("P035") -> listOf(f("engine", "Mini fuse", "9"), f("engine", "Mini fuse", "10"), f("engine", "Mini fuse", "5"), f("engine", "Mini fuse", "2"))
            c in setOf("P0087", "P0088", "P0089", "P0090", "P0190", "P0191", "P0192", "P0193") || c.startsWith("P023") -> listOf(f("engine", "Mini fuse", "67"), f("engine", "Mini fuse", "53"))
            c.startsWith("P044") || c.startsWith("P045") || c.startsWith("P0446") -> listOf(f("engine", "Mini fuse", "62"), f("engine", "Mini fuse", "47"), f("engine", "Mini fuse", "11"))
            c.startsWith("P013") || c.startsWith("P014") || c == "P0030" || c == "P0036" || c.startsWith("P0420") || c.startsWith("P0430") || c.startsWith("P2195") || c.startsWith("P2196") -> listOf(f("engine", "Mini fuse", "11"), f("engine", "Mini fuse", "47"), f("engine", "Mini fuse", "10"))
            c == "P0480" || c == "P0481" || c == "P0217" -> listOf(f("engine", "J-case fuse", "45"), f("engine", "J-case fuse", "42"), f("engine", "Mini relay", "13"), f("engine", "Mini relay", "9"), f("engine", "Micro relay", "10"), f("engine", "Mini fuse", "46"))
            c.startsWith("P07") || c.startsWith("P08") || c.startsWith("P09") -> listOf(f("engine", "Mini fuse", "1"), f("engine", "Mini fuse", "13"))
            c == "P0562" || c == "P0563" || c == "P0620" || c == "P0621" || c == "P0622" -> listOf(f("engine", "Mini fuse", "69"), f("engine", "Mini fuse", "2"))
            c.startsWith("P060") || c == "P0610" || c == "P0685" || c == "P0690" -> listOf(f("engine", "Mini fuse", "2"), f("engine", "Mini fuse", "5"), f("engine", "Mini fuse", "10"), f("engine", "Mini relay", "7"))
            c == "P1682" || c == "B1325" || c == "B1330" -> listOf(f("panel", "Mini fuse", "22"), f("engine", "Mini relay", "15"))
            c == "U0100" -> listOf(f("engine", "Mini fuse", "2"), f("engine", "Mini fuse", "5"), f("engine", "Mini fuse", "10"))
            c == "U0101" -> listOf(f("engine", "Mini fuse", "1"), f("engine", "Mini fuse", "13"))
            c == "U0121" || c == "U0122" || c.startsWith("C0") -> listOf(f("engine", "Mini fuse", "34"), f("engine", "J-case fuse", "26"))
            c == "U0140" -> listOf(f("panel", "J-case fuse", "10"))
            c == "U0151" || c.startsWith("B0") -> listOf(f("panel", "Mini fuse", "15"), f("engine", "Mini fuse", "17"))
            c == "U0155" -> listOf(f("panel", "Mini fuse", "21"), f("engine", "Mini fuse", "52"))
            c == "U0164" -> listOf(f("panel", "Mini fuse", "17"), f("engine", "Mini fuse", "54"))
            else -> emptyList()
        }
        return l.filterNotNull()
    }

    /** Fuses and relays the text names by their use (for example an AI answer). Up to 6, one per use. */
    fun mentions(text: String): List<FuseSlot> {
        val t = text.lowercase()
        val out = ArrayList<FuseSlot>()
        val seen = HashSet<String>()
        for (s in all) {
            val u = s.usage.lowercase()
            if (u.length < 6 || u == "not used" || u == "spare") continue
            if (t.contains(u) && seen.add(u)) out.add(s)
            if (out.size >= 6) break
        }
        return out
    }

    private fun index(b: FuseBox): String = b.slots.filter { it.clickable && it.usage.isNotBlank() && it.usage != "Spare" && it.usage != "Not Used" }
        .joinToString("; ") { (if (it.kind.startsWith("Mini fuse")) "fuse" else if (it.kind == "J-case fuse") "J-case fuse" else it.kind.lowercase()) + " " + it.id + " " + it.usage }

    /** Facts for the AI so it names fuses exactly as the app shows them. */
    fun aiFacts(codes: List<String>): String {
        val sb = StringBuilder("FUSE BOXES OF THIS CAR (from the manual; no amp ratings are listed there). ")
        sb.append("Engine compartment box (driver side near the battery): ").append(index(engine)).append(". ")
        sb.append("Dash box (driver side): ").append(index(panel)).append(". ")
        sb.append("When a fuse or relay could cause the problem, name it exactly with its box, number and use, for example 'engine box fuse 9 (Ignition Coils)' or 'dash box fuse 14 (Diagnostic Link Connector)', and say to check it in the Fuse boxes screen of the app. Do not invent fuses that are not listed. ")
        val per = codes.distinct().mapNotNull { c -> forCode(c).takeIf { it.isNotEmpty() }?.let { l -> c.take(5) + ": " + l.joinToString(", ") { s -> (if (s.box == "engine") "engine box " else "dash box ") + s.label.lowercase() + " (" + s.usage + ")" } } }
        if (per.isNotEmpty()) sb.append("Fuses worth checking for the current codes: ").append(per.joinToString("; ")).append(".")
        return sb.toString()
    }

    // ------------------------------------------------------------------ colour chart, safety, relay tests
    class FuseColour(val amps: String, val colour: String, val argb: Long)

    val miniColours = listOf(
        FuseColour("2 A", "grey", 0xFF9E9E9E), FuseColour("3 A", "violet", 0xFF9C27B0), FuseColour("4 A", "pink", 0xFFF48FB1), FuseColour("5 A", "tan", 0xFFD7B98E),
        FuseColour("7.5 A", "brown", 0xFF795548), FuseColour("10 A", "red", 0xFFE53935), FuseColour("15 A", "light blue", 0xFF4FC3F7), FuseColour("20 A", "yellow", 0xFFFDD835),
        FuseColour("25 A", "clear / white", 0xFFEEEEEE), FuseColour("30 A", "green", 0xFF43A047),
    )

    val safety = listOf(
        "Switch the ignition off and remove the key before opening a fuse box. For anything big (J-case fuses, relays in the engine bay) disconnect the battery's negative cable first.",
        "Let a hot engine bay cool. Keep hands and tools away from the cooling fans: they can start by themselves even with the engine off (manual p.10-13).",
        "Replace a bad fuse only with one of the identical size AND rating. Never use a higher rating, wire or foil: the wire can overheat and burn.",
        "If the same fuse blows again right away, there is a short or an overloaded circuit. Stop swapping fuses and have the wiring checked.",
        "In an emergency you may borrow a fuse of the same rating from a circuit you do not need (the manual allows this), and replace it as soon as possible.",
        "Use the plastic puller in the box (or fine pliers). Do not poke with metal tools: you can short the box.",
        "Keep the covers on. Spilled liquid on electrical parts can damage them (manual p.10-32). Do not use a water hose near the box.",
        "Airbag (SDM) fuse and the wiring around it: do not probe the airbag circuit with the battery connected. Have a workshop do airbag work.",
        "On eAssist cars, orange cables are high voltage and must never be touched or cut (manual p.10-31).",
        "After the battery was disconnected: reset the clock and radio, relearn the idle, and reprogram the front windows (open then close).",
        "The manual says the car may not have every fuse and relay shown in the diagram, so an empty slot can be normal.",
    )

    val checkSteps = listOf(
        "Find the fuse for what is not working (search by words like horn, wipers, radio, 12 V socket).",
        "Pull it out with the puller. Hold it to the light: a broken or melted metal band means it is blown.",
        "No light? Use a test light or a multimeter on the two small metal test points on top of the fuse (with the circuit switched on): both points should show 12 V if it is good.",
        "Fit a new fuse with the same size and the same rating. Test the feature.",
        "If it blows again, stop and have the circuit checked.",
    )

    val relayTests = listOf(
        "Listen: with someone switching the feature on, you should hear and feel a click from the relay.",
        "Swap test: swap the relay with another relay of the same type that is not critical, and see if the problem moves. Cooling Fan K1 (mini relay 13) and Cooling Fan K2 (mini relay 9) are the same type: swap them to test a fan problem.",
        "Bench test: apply 12 V to the two coil pins (they are usually numbered 85 and 86). You should hear a click, and the two contact pins (30 and 87) should then show continuity on a multimeter.",
        "A relay that clicks but the part still does not work usually points to the fuse, the wiring or the part itself.",
        "Do not force relays into the wrong slot: the shapes differ (mini and micro relays are not interchangeable).",
        "Rattling, burnt smell or melted plastic on a relay or its socket means replace the relay and check the socket and wiring.",
    )
}
