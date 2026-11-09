package com.example.obdy

/** Facts from the 2013 Chevrolet Malibu owner's manual, in short plain words (not copied text). Page references are the manual's own numbers. */
class SpecRow(val label: String, val value: String, val note: String = "")
class PartRow(val name: String, val gm: String, val acdelco: String, val note: String = "")
class ManualMessage(val text: String, val meaning: String, val action: String, val urgency: Int)
class HowTo(val title: String, val steps: List<String>, val source: String)
class ServiceDef(val id: String, val name: String, val kmNormal: Int, val kmSevere: Int, val months: Int, val manual: String, val appNote: String = "")

object Manual {
    const val ENGINE_CODE = 'R'

    /** The 8th character of the VIN is the engine code. This manual lists only the 2.4L L4 (code R). */
    fun engineFromVin(vin: String): String? {
        if (vin.length != 17) return null
        return if (vin[7] == ENGINE_CODE) "VIN engine code R: the 2.4L L4 petrol engine this manual describes." else "VIN engine code ${vin[7]}: this manual lists only code R (2.4L L4). Check the Service Parts label (glove box or boot) for your engine."
    }

    val specs = listOf(
        SpecRow("Engine", "2.4L L4 petrol (VIN 8th character R)", "The same manual also covers eAssist cars (orange high-voltage cable under the bonnet). Do not touch orange cables."),
        SpecRow("Transmission", "Automatic", "Sealed: no owner dipstick. Fluid is checked and changed by the dealer with a special procedure (p.10-11)."),
        SpecRow("Engine oil", "dexos1 licensed, SAE 5W-30", "ACDelco dexos1 Synthetic Blend is recommended. No additives and no engine flushes (p.10-9)."),
        SpecRow("Engine oil with filter", "4.7 L (5.0 US qt)", "If the level is below MIN, add 1 L and recheck. Too much oil also harms the engine."),
        SpecRow("Coolant", "DEX-COOL (silicate-free), 50/50 with clean water", "System holds 9.6 L. Never plain water. Specific fill procedure (p.10-15)."),
        SpecRow("Brake fluid", "DOT 3 (GM 88862806)", "New fluid from a sealed container only."),
        SpecRow("Transmission fluid", "DEXRON-VI", "Use only this."),
        SpecRow("Fuel", "Unleaded, 87 octane or higher", "TOP TIER detergent petrol recommended. No E85, no methanol, avoid petrol with MMT. Knock with 87+ means the engine needs service (p.9-40)."),
        SpecRow("Fuel tank", "59.7 L (15.8 US gal)"),
        SpecRow("Spark plug gap", "0.9 mm (0.035 in)"),
        SpecRow("Wheel nut torque", "150 Nm (110 lb ft)"),
        SpecRow("A/C refrigerant", "R1234yf", "Charge amount is on the label under the bonnet."),
        SpecRow("Tyre pressure", "On the Tire and Loading Information label", "Check monthly, cold. Also shown in the driver information display."),
        SpecRow("Hinge and lock grease", "Superlube GM 12346241", "Weatherstrips: GM 3634770 lubricant or silicone grease GM 12345579."),
    )

    val parts = listOf(
        PartRow("Engine air filter", "55560894", "A3128C", "Passenger side of the engine bay, six screws on the housing."),
        PartRow("Engine oil filter (2.4L)", "12605566", "PF457G"),
        PartRow("Cabin (passenger compartment) air filter", "13271190", "-"),
        PartRow("Spark plugs (2.4L)", "12620540", "41-108", "Gap 0.9 mm."),
        PartRow("Wiper blade, driver", "92231676", "-", "60 cm (23.6 in)."),
        PartRow("Wiper blade, passenger", "20980551", "-", "47.5 cm (18.7 in)."),
    )

    /** The manual's maintenance charts. Hot weather in heavy city traffic counts as "severe", so [severe] is the right choice for the UAE. */
    val services = listOf(
        ServiceDef("oil", "Engine oil and filter", 7500, 7500, 6,
            "Change when CHANGE ENGINE OIL SOON shows (within 1,000 km) and at least once a year; reset the oil life every time. If the oil life was reset by accident, change within 5,000 km of the last change. dexos1 SAE 5W-30, 4.7 L with filter.",
            "Obdy uses 7,500 km or 6 months because of heat; the manual allows up to a year."),
        ServiceDef("rotate", "Tyre rotation and the 12,000 km checks", 12000, 12000, 0,
            "Every 12,000 km: rotate tyres; check oil level and oil life, coolant level, washer fluid, wipers, tyre pressure and wear, fluid leaks; inspect the air filter, brakes, steering, suspension and chassis, restraint system, fuel and exhaust systems; lubricate body parts; check starter switch, shift lock, park brake and P mechanism, accelerator pedal, gas struts, tyre sealant date, sunroof."),
        ServiceDef("cabin", "Cabin (A/C) filter", 36000, 36000, 24,
            "Replace every 36,000 km or two years, sooner with heavy traffic, poor air quality or dust, or if airflow drops, windows fog or smells appear. GM 13271190."),
        ServiceDef("air", "Engine air filter", 72000, 72000, 48,
            "Inspect at every 12,000 km. Replace every 72,000 km or four years (the care section says at the first oil change after 80,000 km). In dusty conditions inspect at every oil change. ACDelco A3128C."),
        ServiceDef("evap", "Evaporative system inspection", 72000, 72000, 0,
            "Check all fuel and vapour lines and hoses for connection, routing and condition, and check the purge valve works."),
        ServiceDef("atf", "Automatic transmission fluid and filter", 156000, 72000, 0,
            "Change fluid (and filter if serviceable) every 156,000 km (normal) or every 72,000 km (severe use). DEXRON-VI only. No owner dipstick check: the dealer uses a special procedure.",
            "Heat plus city traffic is severe use, so Obdy applies 72,000 km."),
        ServiceDef("plugs", "Spark plugs and plug wires", 156000, 156000, 0,
            "Replace spark plugs and inspect the plug wires at 156,000 km. ACDelco 41-108 (GM 12620540), gap 0.9 mm."),
        ServiceDef("coolant", "Engine coolant (drain, flush, fill)", 240000, 240000, 60,
            "Every 240,000 km or 5 years. DEX-COOL 50/50, 9.6 L. The system has a specific fill procedure: have a workshop do it."),
        ServiceDef("belts", "Drive belt inspection", 240000, 240000, 120,
            "Inspect belts at 240,000 km or 10 years; replace if cracked, split or squeaking."),
        ServiceDef("brakefluid", "Brake fluid", 0, 0, 36,
            "The manual says 240,000 km or 10 years (special application services). DOT 3.",
            "Obdy uses 3 years: brake fluid absorbs moisture and heat shortens its life (workshop practice, not from the manual)."),
        ServiceDef("underbody", "Underbody flush", 0, 0, 12, "Underbody flushing once a year (special application services)."),
        ServiceDef("battery", "Battery test", 0, 0, 12, "Not in the schedule, but the manual advises keeping a battery with full cranking power.", "Hot climates shorten battery life to about 2 to 3 years."),
        ServiceDef("ac", "A/C service", 0, 0, 24, "Not in the schedule.", "Practical interval, not from the manual."),
        ServiceDef("wipers", "Wiper blades", 0, 0, 12, "Inspect at every 12,000 km service; replace if worn, cracked or dirty. Driver 60 cm GM 92231676, passenger 47.5 cm GM 20980551."),
    )

    fun schedule(severe: Boolean): List<MaintItem> = services.map {
        MaintItem(it.id, it.name, if (severe) it.kmSevere else it.kmNormal, it.months, 0, 0L, (it.manual + if (it.appNote.isNotBlank()) "\n" + it.appNote else ""))
    }

    val lights = listOf(
        ManualMessage("Check engine light FLASHING", "A misfire is happening. It raises emissions and can damage the catalytic converter.", "Slow down, avoid hard acceleration and hills. If it keeps flashing, stop, switch off, wait 10 seconds and restart. Still flashing: drive gently to a workshop as soon as possible.", 2),
        ManualMessage("Check engine light STEADY", "The car found an emission-control problem.", "Check the fuel cap is fully tightened (the car can tell if it was left loose). Use good quality petrol; poor fuel can cause stalling, misfire and hesitation, and one full tank may be needed to clear it. If the light stays on, get it scanned.", 1),
        ManualMessage("Engine coolant temperature light", "The engine has overheated.", "Pull over and switch off as soon as it is safe. Never keep driving with this light on.", 2),
        ManualMessage("Engine oil pressure light", "Oil is not flowing properly: low oil or another problem.", "Stop, check the oil level, add oil if low. If the level is fine and the light stays on, do not drive: have it serviced.", 2),
        ManualMessage("Tyre pressure light (steady)", "One or more tyres are well under-inflated.", "Stop and inflate to the pressure on the Tire and Loading Information label.", 1),
        ManualMessage("Tyre pressure light (flashes about a minute, then steady)", "The tyre pressure monitoring system itself has a fault.", "Have it checked; it will come on at every start until fixed.", 0),
        ManualMessage("StabiliTrak / traction control light", "Steady with a message: the system is off or not working. Flashing: it is working right now.", "Read the display message. If it is not switched off by you, service is needed. The car stays drivable but without that help: slow down.", 1),
        ManualMessage("Security (immobiliser) light", "Normally lights briefly at start. If it stays on and the engine will not start, the theft-deterrent system has a problem.", "See the dealer.", 1),
    )

    val messages = listOf(
        ManualMessage("CHANGE ENGINE OIL SOON", "Oil life is used up.", "Change oil and filter within 1,000 km, then reset the oil life.", 1),
        ManualMessage("ENGINE OIL HOT, IDLE ENGINE", "Oil temperature is too high.", "Stop and let it idle until it cools.", 2),
        ManualMessage("OIL PRESSURE LOW - STOP ENGINE", "Low oil pressure.", "Stop as soon as safe. Do not drive until the cause is fixed. Check the oil level.", 2),
        ManualMessage("ENGINE POWER IS REDUCED", "The engine limits power because of a fault.", "You can drive slowly to your destination, but get it scanned soon. If it stays on, see the dealer.", 1),
        ManualMessage("TIGHTEN GAS CAP", "The fuel cap is not tight.", "Tighten it until it clicks.", 0),
        ManualMessage("FUEL LEVEL LOW", "Low fuel.", "Refuel soon.", 0),
        ManualMessage("COOLANT LEVEL LOW ADD COOLANT", "Coolant is low.", "Let the engine cool, then add 50/50 DEX-COOL mixture to the surge tank. Look for leaks.", 2),
        ManualMessage("A/C OFF DUE TO HIGH ENGINE TEMP", "The A/C compressor was switched off to relieve a hot engine.", "You can keep driving. If it keeps appearing, have the cooling system repaired soon.", 1),
        ManualMessage("ENGINE OVERHEATED - IDLE ENGINE", "Coolant is too hot.", "Stop and idle until it cools.", 2),
        ManualMessage("ENGINE OVERHEATED - STOP ENGINE", "Unsafe temperature, with a continuous chime.", "Stop and switch off as soon as safe to avoid severe damage. Do not open the hood if steam shows.", 2),
        ManualMessage("TRANSMISSION HOT - IDLE ENGINE", "Transmission fluid is too hot.", "Stop and idle until it cools. Driving hot damages the transmission.", 2),
        ManualMessage("SERVICE TRANSMISSION", "A transmission problem.", "See the dealer. Scan the transmission module (Deep scan).", 1),
        ManualMessage("SHIFT DENIED / SHIFT TO PARK", "The car needs to be in P.", "Shift to P (Park).", 0),
        ManualMessage("SERVICE POWER STEERING", "A power steering problem.", "See the dealer. Check battery voltage first: low voltage often causes steering warnings.", 1),
        ManualMessage("SERVICE VEHICLE SOON", "A general fault.", "Scan the car; take it to the dealer.", 1),
        ManualMessage("SERVICE A/C SYSTEM", "An air conditioning problem.", "See the dealer.", 0),
        ManualMessage("SERVICE STABILITRAK", "A problem with the StabiliTrak system.", "Stop, switch off for at least 15 seconds, restart. If it stays, it is a real fault: drive slower and see the dealer.", 1),
        ManualMessage("SERVICE TRACTION CONTROL", "Traction control is not working.", "Wheels can spin: drive accordingly, see the dealer.", 1),
        ManualMessage("BATTERY SAVER ACTIVE", "Battery voltage is dropping too far, so some features are reduced.", "Switch off unnecessary accessories so the battery can recover. Test battery and charging.", 1),
        ManualMessage("LOW BATTERY", "Battery voltage is low.", "Test the battery and the charging system (Tools > Battery test).", 1),
        ManualMessage("SERVICE BATTERY CHARGING SYSTEM", "Fault in the charging system.", "Go to the dealer. The battery will run flat.", 2),
        ManualMessage("BRAKE FLUID LOW", "Brake fluid level is low.", "Check the reservoir and look for leaks. Low fluid can mean worn pads or a leak: get it checked before driving far.", 2),
        ManualMessage("RELEASE PARKING BRAKE", "The electric parking brake is on while moving.", "Release it before driving.", 1),
        ManualMessage("SERVICE PARKING BRAKE", "A problem with the electric parking brake.", "See the dealer.", 1),
        ManualMessage("STEP ON BRAKE TO RELEASE PARK BRAKE", "You tried to release the parking brake without pressing the brake pedal.", "Press the brake pedal, then release.", 0),
        ManualMessage("SERVICE BRAKE ASSIST", "A problem with the brake boost assist. You may hear a motor and feel pedal pulsing.", "See the dealer.", 2),
        ManualMessage("TIRE PRESSURE LOW ADD AIR TO TIRE", "A tyre is low.", "Stop and inflate to the pressure on the tyre label.", 1),
        ManualMessage("SERVICE TIRE MONITOR SYSTEM", "A fault in the tyre pressure monitoring system.", "Have it checked.", 0),
        ManualMessage("TIRE LEARNING ACTIVE", "The system is learning new tyre sensors.", "Nothing to do.", 0),
        ManualMessage("NO REMOTE DETECTED / REPLACE BATTERY IN REMOTE KEY", "The key fob battery is weak.", "Replace the fob battery.", 0),
        ManualMessage("OPEN, THEN CLOSE DRIVER (or PASSENGER) WINDOW", "The window express-up needs reprogramming after the battery was disconnected or recharged.", "Open the window fully, then close it fully, for each front window.", 0),
    )

    val howTos = listOf(
        HowTo("Check the engine oil", listOf(
            "Park on level ground. If the engine just ran, wait a few minutes for the oil to drain back.",
            "Pull the dipstick (yellow loop), wipe it, push it fully in, pull it out again keeping the tip down.",
            "The level must be between the marks. Below MIN: add 1 L of dexos1 5W-30 and check again. Do not overfill.",
            "Check at every fuel stop.",
        ), "p.10-8"),
        HowTo("Reset the oil life after an oil change", listOf(
            "Ignition ON (engine off).",
            "Press the MENU button on the turn signal lever and scroll with the thumbwheel to REMAINING OIL LIFE.",
            "Press SET/CLR to reset to 100%.",
            "Ignition OFF. If CHANGE ENGINE OIL SOON comes back at the next start, repeat. Never reset it at any other time.",
        ), "p.10-11"),
        HowTo("Check and add coolant", listOf(
            "Engine cold, on level ground. Coolant must be visible in the surge tank, at or above the marked level.",
            "If low: add a 50/50 DEX-COOL and clean water mixture. Never plain water, never open a hot system.",
            "If the tank is empty the car has a specific fill procedure (run the engine with the cap off until the upper hose is hot, then top up): have a workshop do it.",
            "Find and fix any leak: do not run the engine low on coolant.",
        ), "p.10-14 to 10-17"),
        HowTo("If the engine overheats", listOf(
            "Pull over and switch off as soon as it is safe. Do not open the hood if steam is coming out; wait until it stops.",
            "With the hood open, check both cooling fans were running. If not, do not run the engine: get it serviced.",
            "Let it cool, check the coolant level in the surge tank, and look for leaks.",
            "Never keep driving with the coolant temperature light on.",
        ), "p.10-17"),
        HowTo("Check engine light: what to do", listOf(
            "Flashing: a misfire. Slow down, avoid hard acceleration and hills; if it keeps flashing, stop, switch off 10 seconds, restart; see a workshop soon.",
            "Steady: check the fuel cap is fully tightened. A few drives with the cap on can turn the light off.",
            "Think about the last fuel: poor fuel can cause stalling, misfire and hesitation. Change the brand; one full tank may be needed.",
            "If it stays on, scan the car with Obdy and look at the codes and the history timeline.",
        ), "p.5-13 to 5-14"),
        HowTo("After the battery was disconnected, replaced or jump-started", listOf(
            "Front windows: open fully then close fully to reprogram express-up.",
            "Idle and throttle relearn: see Tools > Relearn guides.",
            "The readiness monitors reset, so the car may show 'not ready' for an inspection for about 50 km of mixed driving. That is not a fault.",
            "Radio presets and clock may need resetting. A weak battery can trigger many false warnings: test it.",
        ), "p.10-21, 5-33"),
        HowTo("Transmission fluid", listOf(
            "Owners do not check the level: the transmission is sealed. Fluid only disappears through a leak.",
            "Use only DEXRON-VI. Change fluid and filter at the interval in the Service tab (72,000 km for severe use).",
            "Fluid change is a dealer procedure. Do not 'top up' by guessing.",
        ), "p.10-11"),
        HowTo("Replace the engine air filter", listOf(
            "The filter box is on the passenger side of the engine bay.",
            "Remove the six screws on top of the housing, lift the cover, pull out the filter.",
            "Shake off loose dust: if it stays dirty, replace it (ACDelco A3128C).",
            "Refit and tighten all six screws. Never drive with the filter out.",
        ), "p.10-11 to 10-12"),
        HowTo("Low tyre pressure", listOf(
            "Inflate to the pressure on the Tire and Loading Information label (cold).",
            "Check pressure monthly and inspect wear.",
            "After rotating tyres or fitting new ones the system may show TIRE LEARNING ACTIVE: that is normal.",
        ), "p.10-46 to 10-50"),
    )

    /** What the manual says that is relevant to a fault code, or null. */
    fun noteForCode(codeIn: String): String? {
        val c = codeIn.take(5).uppercase()
        return when {
            c.startsWith("P044") || c.startsWith("P045") -> "Manual: the car can tell if the fuel cap is loose or missing (message TIGHTEN GAS CAP). Check the cap is fully tightened; a few drives with the cap on should turn the light off (p.5-14)."
            Regex("^P030[0-8]$").matches(c) || c == "P0316" -> "Manual: a FLASHING check engine light means an active misfire: slow down, avoid hard acceleration and hills; if it keeps flashing stop, switch off 10 s and restart, then see a workshop (p.5-13). Poor fuel can cause misfire, hesitation and stalling: change the petrol brand; one full tank may be needed. Spark plug gap is 0.9 mm."
            c == "P0171" || c == "P0174" || c == "P0172" || c == "P0175" -> "Manual: use good quality petrol (TOP TIER recommended, 87+ octane, no E85). The engine air filter is inspected every 12,000 km and replaced at 72,000 km or 4 years; a clogged or loose filter box affects the mixture."
            c in setOf("P0128", "P0116", "P0117", "P0118", "P0217", "P0480", "P0481") -> "Manual: the coolant is DEX-COOL 50/50 (9.6 L). Check the surge tank level when cold; both fans should run when the engine overheats (p.10-17). Never drive with the coolant light on."
            c.startsWith("P052") -> "Manual: if the oil pressure light or OIL PRESSURE LOW message shows, stop and check the oil level first. Oil is dexos1 SAE 5W-30, 4.7 L with filter."
            c.startsWith("P001") || c == "P0016" || c == "P0017" -> "Manual: the oil must be dexos1 5W-30 at the right level; no additives or engine flushes. Check the level and the date of the last oil change."
            c in setOf("P0562", "P0563", "P0620", "P0621", "P0622") || c.startsWith("U0") -> "Manual: messages BATTERY SAVER ACTIVE, LOW BATTERY or SERVICE BATTERY CHARGING SYSTEM point to the battery or charging system. Low voltage can create false codes in many modules."
            c.startsWith("P07") || c.startsWith("P08") || c.startsWith("P09") -> "Manual: the transmission is sealed (no owner dipstick) and uses DEXRON-VI only. TRANSMISSION HOT - IDLE ENGINE means the fluid is overheating. Hot-climate city driving counts as severe use: fluid and filter every 72,000 km."
            c.startsWith("C0") -> "Manual: SERVICE STABILITRAK or SERVICE TRACTION CONTROL appear with these faults. Try switching off for 15 seconds and restarting; if it returns it is a real fault. The car stays drivable but without that help."
            c.startsWith("P") -> "Manual: a steady check engine light means an emission-control fault: check the fuel cap and the quality of the last fuel first (p.5-14)."
            else -> null
        }
    }

    /** Short facts given to the AI so its answers use this exact car. */
    fun aiFacts(): String =
        "CAR FACTS FROM THE OWNER'S MANUAL: 2013 Chevrolet Malibu, 2.4L L4 petrol (VIN 8th char R), automatic transmission (sealed, no dipstick, DEXRON-VI), electric parking brake. " +
        "Oil dexos1 SAE 5W-30, 4.7 L with filter; oil change when CHANGE ENGINE OIL SOON shows, at least yearly. Coolant DEX-COOL 50/50, 9.6 L. Brake fluid DOT 3. Fuel 87+ octane unleaded, no E85. " +
        "Spark plug gap 0.9 mm; plugs due 156,000 km. Severe-use schedule (hot weather and city traffic): transmission fluid and filter every 72,000 km, cabin filter every 36,000 km, engine air filter every 72,000 km, coolant 240,000 km or 5 years. " +
        "Hot UAE climate."
}
