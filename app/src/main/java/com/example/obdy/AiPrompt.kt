package com.example.obdy

/** Builds the text sent to the AI: all facts first, then what to answer. */
object AiPrompt {
    fun build(
        car: Car, faults: List<Fault>, monitors: MonitorStatus?, freeze: FreezeFrame?, live: Map<String, String>,
        battery: BatteryRecord?, symptoms: String, overdue: List<String>, focusCode: String?, extra: String = "", withFormat: Boolean = true,
    ): String {
        val sb = StringBuilder()
        sb.append("You are an experienced automotive diagnostic technician helping the owner of a ${car.year} Chevrolet Malibu LT (GCC specification, full option, ")
        sb.append("automatic transmission, used in the hot UAE climate). The data below was read with an OBD-II scanner. ")
        sb.append("Give practical, honest guidance. Do not invent readings that are not listed. Say clearly when something is a guess.\n\n")
        sb.append("CAR\n")
        sb.append("- Name: ${car.name}\n- Year: ${car.year}\n")
        if (car.engine.isNotBlank()) sb.append("- Engine: ${car.engine}\n")
        if (car.vin.isNotBlank()) sb.append("- VIN: ${car.vin}\n")
        if (car.odometerKm > 0) sb.append("- Mileage: ${car.odometerKm} km\n")
        if (car.notes.isNotBlank()) sb.append("- Owner notes: ${car.notes}\n")
        if (symptoms.isNotBlank()) sb.append("\nSYMPTOMS DESCRIBED BY THE OWNER\n${symptoms.trim()}\n")
        sb.append("\nFAULT CODES\n")
        if (faults.isEmpty()) sb.append("- none stored\n")
        for (f in faults) sb.append("- ${f.code} [${f.kind}] in ${f.module}: ${f.description}${if (f.status.isNotBlank()) " (${f.status})" else ""}\n")
        if (monitors != null) {
            sb.append("\nWARNING LIGHT: ${if (monitors.milOn) "ON" else "off"}; codes counted by engine computer: ${monitors.dtcCount}\n")
            val inc = monitors.monitors.filter { it.supported && !it.complete }.joinToString(", ") { it.name }
            sb.append("Readiness monitors not complete: ${inc.ifEmpty { "none" }}\n")
        }
        if (freeze != null && freeze.code != null) {
            sb.append("\nFREEZE FRAME (conditions when ${freeze.code} was stored)\n")
            for ((p, v) in freeze.values) sb.append("- ${p.name}: ${Calc.fmt(v, 1)} ${p.unit}\n")
        }
        if (live.isNotEmpty()) {
            sb.append("\nLIVE VALUES RIGHT NOW\n")
            for ((k, v) in live) sb.append("- $k: $v\n")
        }
        if (battery != null) {
            sb.append("\nBATTERY TEST: rest=${battery.rest ?: "-"} V, lowest while cranking=${battery.crank ?: "-"} V, idle=${battery.idle ?: "-"} V, 2000 rpm=${battery.rev ?: "-"} V\n")
        }
        if (overdue.isNotEmpty()) sb.append("\nOVERDUE SERVICE: ${overdue.joinToString(", ")}\n")
        if (extra.isNotBlank()) sb.append("\n$extra\n")
        if (withFormat) {
            sb.append("\nANSWER FORMAT (plain text, short sections, no tables):\n")
            if (focusCode != null) sb.append("Focus on $focusCode, but use the other data if it is related.\n")
            sb.append("1. Most likely causes, ranked, each with the evidence from the data above.\n")
            sb.append("2. What to check first: cheap and simple tests in order, which the owner can do or ask a workshop to do.\n")
            sb.append("3. Is it safe to keep driving? What to avoid.\n")
            sb.append("4. Rough priority and what could get worse if ignored.\n")
            sb.append("5. Which extra reading or test would confirm the diagnosis.\n")
            sb.append("If several codes or symptoms (engine, transmission, ABS, electrical) share one root cause such as low battery voltage, a bad ground or a CAN-bus wiring problem, say so first.")
        } else if (focusCode != null) sb.append("\nThe code in question is $focusCode.\n")
        return sb.toString()
    }
}
