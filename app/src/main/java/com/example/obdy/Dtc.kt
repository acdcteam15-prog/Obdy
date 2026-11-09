package com.example.obdy

/** One fault code found in one module. */
class Fault(val code: String, val module: String, val kind: String, val status: String = "", val raw: String = "") {
    val description: String get() = DtcDb.describe(code)
    val key: String get() = "$module/$code/$kind"
}

object DtcDecode {
    private val letters = "PCBU"

    /** Two bytes of a standard OBD-II code, for example 0x01 0x33 = P0133. */
    fun fromBytes(hi: Int, lo: Int): String {
        val l = letters[(hi shr 6) and 3]
        val d2 = (hi shr 4) and 3
        val d3 = hi and 0xF
        return "%c%d%X%02X".format(l, d2, d3, lo and 0xFF)
    }

    /** Answer to Mode 03 / 07 / 0A: [service] answer byte then (on CAN) a count byte, then pairs. Zero pairs are padding. */
    fun fromMode(f: Frame, service: Int): List<String> {
        if (f.data.isEmpty() || f.u(0) != service + 0x40) return emptyList()
        val rest = f.data.drop(1).map { it.toInt() and 0xFF }
        val body = if (rest.size % 2 == 1) rest.drop(1) else rest
        val out = ArrayList<String>()
        var i = 0
        while (i + 1 < body.size) {
            if (body[i] != 0 || body[i + 1] != 0) out.add(fromBytes(body[i], body[i + 1]))
            i += 2
        }
        return out
    }

    /** UDS ReadDTCInformation (19 02): 59 02 mask, then 4 bytes per code (3 code bytes + status). */
    fun fromUds(f: Frame): List<Pair<String, String>> {
        if (f.data.size < 3 || f.u(0) != 0x59) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        var i = 3
        while (i + 3 < f.data.size) {
            val hi = f.u(i); val lo = f.u(i + 1); val ftb = f.u(i + 2); val st = f.u(i + 3)
            if (hi != 0 || lo != 0 || ftb != 0) {
                val code = fromBytes(hi, lo) + (if (ftb != 0) "-%02X".format(ftb) else "")
                out.add(code to "status %02X".format(st))
            }
            i += 4
        }
        return out
    }

    /** GM GMLAN ReadDiagnosticInformation (A9 81): E9 81 [count] then 3 bytes per code (2 code bytes + status). Best effort. */
    fun fromGmlan(f: Frame): List<Pair<String, String>> {
        if (f.data.size < 2 || f.u(0) != 0xE9) return emptyList()
        var rest = f.data.drop(2).map { it.toInt() and 0xFF }
        if (rest.size % 3 == 1) rest = rest.drop(1)
        val out = ArrayList<Pair<String, String>>()
        var i = 0
        while (i + 2 < rest.size) {
            val hi = rest[i]; val lo = rest[i + 1]; val st = rest[i + 2]
            if (hi != 0 || lo != 0) out.add(fromBytes(hi, lo) to "status %02X".format(st))
            i += 3
        }
        return out
    }
}

object DtcDb {
    fun system(code: String): String = when (code.firstOrNull()) {
        'P' -> "Powertrain (engine / transmission)"
        'C' -> "Chassis (ABS, brakes, steering, suspension)"
        'B' -> "Body (airbags, doors, climate, lights)"
        'U' -> "Network (modules not talking to each other)"
        else -> "Unknown"
    }

    private val names = mapOf(
        "P0011" to "Intake camshaft position over-advanced (bank 1)", "P0012" to "Intake camshaft position over-retarded (bank 1)",
        "P0013" to "Exhaust camshaft position actuator circuit (bank 1)", "P0014" to "Exhaust camshaft position over-advanced (bank 1)",
        "P0016" to "Crankshaft and camshaft position correlation (bank 1 sensor A)", "P0017" to "Crankshaft and camshaft position correlation (bank 1 sensor B)",
        "P0030" to "O2 sensor heater control circuit (B1S1)", "P0036" to "O2 sensor heater control circuit (B1S2)",
        "P0087" to "Fuel rail pressure too low", "P0088" to "Fuel rail pressure too high", "P0089" to "Fuel pressure regulator performance",
        "P0090" to "Fuel pressure regulator control circuit", "P0101" to "Mass air flow sensor range/performance", "P0102" to "Mass air flow sensor circuit low",
        "P0103" to "Mass air flow sensor circuit high", "P0106" to "Manifold pressure (MAP) sensor range/performance", "P0107" to "MAP sensor circuit low",
        "P0108" to "MAP sensor circuit high", "P0111" to "Intake air temperature sensor range/performance", "P0112" to "Intake air temperature sensor circuit low",
        "P0113" to "Intake air temperature sensor circuit high", "P0116" to "Coolant temperature sensor range/performance", "P0117" to "Coolant temperature sensor circuit low",
        "P0118" to "Coolant temperature sensor circuit high", "P0121" to "Throttle/pedal position sensor A range/performance", "P0122" to "Throttle position sensor circuit low",
        "P0123" to "Throttle position sensor circuit high", "P0128" to "Coolant temperature below thermostat regulating temperature",
        "P0130" to "O2 sensor circuit (B1S1)", "P0131" to "O2 sensor circuit low voltage (B1S1)", "P0132" to "O2 sensor circuit high voltage (B1S1)",
        "P0133" to "O2 sensor slow response (B1S1)", "P0134" to "O2 sensor no activity (B1S1)", "P0135" to "O2 sensor heater circuit (B1S1)",
        "P0136" to "O2 sensor circuit (B1S2)", "P0137" to "O2 sensor circuit low voltage (B1S2)", "P0138" to "O2 sensor circuit high voltage (B1S2)",
        "P0139" to "O2 sensor slow response (B1S2)", "P0140" to "O2 sensor no activity (B1S2)", "P0141" to "O2 sensor heater circuit (B1S2)",
        "P0171" to "System too lean (bank 1)", "P0172" to "System too rich (bank 1)", "P0174" to "System too lean (bank 2)", "P0175" to "System too rich (bank 2)",
        "P0191" to "Fuel rail pressure sensor range/performance", "P0192" to "Fuel rail pressure sensor circuit low", "P0193" to "Fuel rail pressure sensor circuit high",
        "P0201" to "Injector circuit, cylinder 1", "P0202" to "Injector circuit, cylinder 2", "P0203" to "Injector circuit, cylinder 3", "P0204" to "Injector circuit, cylinder 4",
        "P0217" to "Engine coolant over-temperature", "P0218" to "Transmission fluid over-temperature", "P0219" to "Engine overspeed",
        "P0221" to "Throttle/pedal position sensor B range/performance", "P0222" to "Throttle position sensor B circuit low", "P0223" to "Throttle position sensor B circuit high",
        "P0230" to "Fuel pump primary circuit", "P0231" to "Fuel pump secondary circuit low", "P0232" to "Fuel pump secondary circuit high",
        "P0261" to "Cylinder 1 injector circuit low", "P0262" to "Cylinder 1 injector circuit high",
        "P0299" to "Turbo/supercharger underboost",
        "P0300" to "Random / multiple cylinder misfire", "P0301" to "Cylinder 1 misfire", "P0302" to "Cylinder 2 misfire", "P0303" to "Cylinder 3 misfire",
        "P0304" to "Cylinder 4 misfire", "P0305" to "Cylinder 5 misfire", "P0306" to "Cylinder 6 misfire", "P0307" to "Cylinder 7 misfire", "P0308" to "Cylinder 8 misfire",
        "P0316" to "Misfire detected on startup (first 1000 rpm)", "P0325" to "Knock sensor 1 circuit (bank 1)", "P0327" to "Knock sensor 1 circuit low", "P0328" to "Knock sensor 1 circuit high",
        "P0335" to "Crankshaft position sensor circuit", "P0336" to "Crankshaft position sensor range/performance", "P0340" to "Camshaft position sensor circuit (bank 1)",
        "P0341" to "Camshaft position sensor range/performance (bank 1)", "P0351" to "Ignition coil A circuit", "P0352" to "Ignition coil B circuit",
        "P0353" to "Ignition coil C circuit", "P0354" to "Ignition coil D circuit", "P0365" to "Exhaust camshaft position sensor circuit (bank 1)",
        "P0401" to "EGR flow insufficient", "P0402" to "EGR flow excessive", "P0403" to "EGR control circuit", "P0404" to "EGR range/performance", "P0405" to "EGR sensor A circuit low",
        "P0420" to "Catalyst efficiency below threshold (bank 1)", "P0430" to "Catalyst efficiency below threshold (bank 2)",
        "P0440" to "Evaporative emission (EVAP) system", "P0441" to "EVAP purge flow incorrect", "P0442" to "EVAP small leak detected", "P0443" to "EVAP purge valve circuit",
        "P0446" to "EVAP vent control circuit", "P0447" to "EVAP vent control circuit open", "P0448" to "EVAP vent control circuit shorted", "P0449" to "EVAP vent valve/solenoid circuit",
        "P0450" to "EVAP pressure sensor", "P0451" to "EVAP pressure sensor range/performance", "P0452" to "EVAP pressure sensor low", "P0453" to "EVAP pressure sensor high",
        "P0455" to "EVAP large leak detected (often the fuel cap)", "P0456" to "EVAP very small leak detected", "P0457" to "EVAP leak detected (fuel cap loose or missing)",
        "P0461" to "Fuel level sensor range/performance", "P0462" to "Fuel level sensor circuit low", "P0463" to "Fuel level sensor circuit high",
        "P0480" to "Cooling fan 1 control circuit", "P0481" to "Cooling fan 2 control circuit", "P0491" to "Secondary air injection insufficient flow",
        "P0500" to "Vehicle speed sensor", "P0501" to "Vehicle speed sensor range/performance", "P0502" to "Vehicle speed sensor circuit low", "P0503" to "Vehicle speed sensor intermittent",
        "P0505" to "Idle control system", "P0506" to "Idle speed lower than expected", "P0507" to "Idle speed higher than expected",
        "P0508" to "Idle air control circuit low", "P0509" to "Idle air control circuit high", "P0520" to "Engine oil pressure sensor/switch circuit",
        "P0521" to "Engine oil pressure sensor range/performance", "P0522" to "Engine oil pressure sensor low voltage", "P0523" to "Engine oil pressure sensor high voltage",
        "P0524" to "Engine oil pressure too low", "P0562" to "System voltage low", "P0563" to "System voltage high", "P0571" to "Brake switch circuit",
        "P0601" to "Control module memory checksum error", "P0602" to "Control module programming error", "P0603" to "Control module keep-alive memory error",
        "P0606" to "Control module processor fault", "P0610" to "Control module vehicle options error", "P0620" to "Generator control circuit",
        "P0621" to "Generator lamp control circuit", "P0622" to "Generator field control circuit", "P0630" to "VIN not programmed or mismatched",
        "P0633" to "Immobiliser key not programmed", "P0641" to "Sensor reference voltage A circuit", "P0651" to "Sensor reference voltage B circuit",
        "P0685" to "Engine control relay circuit", "P0690" to "Engine control relay sense circuit high", "P0700" to "Transmission control system fault (TCM asked for the warning light)",
        "P0701" to "Transmission control system range/performance", "P0702" to "Transmission control system electrical", "P0703" to "Brake switch B circuit",
        "P0705" to "Transmission range sensor circuit", "P0706" to "Transmission range sensor range/performance", "P0707" to "Transmission range sensor circuit low",
        "P0708" to "Transmission range sensor circuit high", "P0711" to "Transmission fluid temperature sensor range/performance", "P0712" to "Transmission fluid temperature sensor low",
        "P0713" to "Transmission fluid temperature sensor high", "P0715" to "Input/turbine speed sensor circuit", "P0716" to "Input/turbine speed sensor range/performance",
        "P0717" to "Input/turbine speed sensor no signal", "P0719" to "Brake switch circuit low", "P0720" to "Output speed sensor circuit", "P0721" to "Output speed sensor range/performance",
        "P0722" to "Output speed sensor no signal", "P0730" to "Incorrect gear ratio", "P0731" to "Gear 1 incorrect ratio", "P0732" to "Gear 2 incorrect ratio",
        "P0733" to "Gear 3 incorrect ratio", "P0734" to "Gear 4 incorrect ratio", "P0735" to "Gear 5 incorrect ratio", "P0736" to "Reverse incorrect ratio",
        "P0741" to "Torque converter clutch performance / stuck off", "P0742" to "Torque converter clutch stuck on", "P0743" to "Torque converter clutch circuit",
        "P0746" to "Pressure control solenoid A performance / stuck off", "P0747" to "Pressure control solenoid A stuck on", "P0748" to "Pressure control solenoid A electrical",
        "P0751" to "Shift solenoid A performance / stuck off", "P0752" to "Shift solenoid A stuck on", "P0753" to "Shift solenoid A electrical",
        "P0756" to "Shift solenoid B performance / stuck off", "P0757" to "Shift solenoid B stuck on", "P0758" to "Shift solenoid B electrical",
        "P0776" to "Pressure control solenoid B performance", "P0777" to "Pressure control solenoid B stuck on", "P0778" to "Pressure control solenoid B electrical",
        "P0796" to "Pressure control solenoid C performance", "P0797" to "Pressure control solenoid C stuck on", "P0798" to "Pressure control solenoid C electrical",
        "P0841" to "Transmission fluid pressure sensor/switch A range/performance", "P0842" to "Transmission fluid pressure sensor/switch A low", "P0843" to "Transmission fluid pressure sensor/switch A high",
        "P0868" to "Transmission fluid pressure low", "P0869" to "Transmission fluid pressure high", "P0871" to "Transmission fluid pressure switch C range/performance",
        "P0962" to "Pressure control solenoid A control circuit low", "P0963" to "Pressure control solenoid A control circuit high",
        "P1133" to "O2 sensor insufficient switching (B1S1, GM)", "P1516" to "Throttle actuator control module performance (GM)",
        "P1682" to "Ignition 1 switch circuit 2 (GM)", "P2096" to "Post-catalyst fuel trim too lean (bank 1)", "P2097" to "Post-catalyst fuel trim too rich (bank 1)",
        "P2101" to "Throttle actuator control motor circuit range/performance", "P2106" to "Throttle actuator control system forced limited power",
        "P2110" to "Throttle actuator control system forced limited rpm", "P2111" to "Throttle actuator control system stuck open", "P2112" to "Throttle actuator control system stuck closed",
        "P2119" to "Throttle actuator control throttle body range/performance", "P2122" to "Pedal position sensor D circuit low", "P2123" to "Pedal position sensor D circuit high",
        "P2127" to "Pedal position sensor E circuit low", "P2128" to "Pedal position sensor E circuit high", "P2135" to "Throttle/pedal position sensors A/B correlation",
        "P2138" to "Pedal position sensors D/E correlation", "P2176" to "Throttle actuator control system idle position not learned",
        "P2177" to "System too lean off idle (bank 1)", "P2178" to "System too rich off idle (bank 1)", "P2187" to "System too lean at idle (bank 1)", "P2188" to "System too rich at idle (bank 1)",
        "P2195" to "O2 sensor signal stuck lean (B1S1)", "P2196" to "O2 sensor signal stuck rich (B1S1)", "P2270" to "O2 sensor signal stuck lean (B1S2)", "P2271" to "O2 sensor signal stuck rich (B1S2)",
        "P2610" to "Engine off timer performance", "P2646" to "Rocker arm / VVT actuator performance",
        "U0073" to "Control module communication bus off", "U0100" to "Lost communication with engine control module (ECM)", "U0101" to "Lost communication with transmission control module (TCM)",
        "U0121" to "Lost communication with ABS / brake control module", "U0122" to "Lost communication with vehicle dynamics control module",
        "U0126" to "Lost communication with steering angle sensor module", "U0140" to "Lost communication with body control module (BCM)",
        "U0151" to "Lost communication with airbag (SDM) module", "U0155" to "Lost communication with instrument cluster", "U0164" to "Lost communication with HVAC module",
        "U0167" to "Lost communication with immobiliser module", "U0214" to "Lost communication with remote function actuator", "U0235" to "Lost communication with cruise control sensor",
        "U0300" to "Internal control module software incompatibility", "U0401" to "Invalid data received from ECM", "U0402" to "Invalid data received from TCM",
        "U0415" to "Invalid data received from ABS module", "U1000" to "Class 2 communication malfunction (GM)", "U2100" to "CAN bus configuration error",
        "C0035" to "Left front wheel speed sensor circuit",
        "C0040" to "Right front wheel speed sensor circuit", "C0045" to "Left rear wheel speed sensor circuit", "C0050" to "Right rear wheel speed sensor circuit", "C0110" to "ABS pump motor circuit", "C0161" to "Brake switch circuit", "C0710" to "Steering position sensor circuit", "B1000" to "Electronic control unit malfunction", "B1325" to "Ignition 1 circuit voltage low", "B1330" to "Ignition 1 circuit voltage high",
        "P0100" to "Mass or volume air flow circuit",
        "P0104" to "Mass or volume air flow circuit intermittent",
        "P0105" to "Manifold absolute / barometric pressure circuit",
        "P0109" to "Manifold absolute / barometric pressure circuit intermittent",
        "P0110" to "Intake air temperature circuit",
        "P0114" to "Intake air temperature circuit intermittent",
        "P0115" to "Engine coolant temperature circuit",
        "P0119" to "Engine coolant temperature circuit intermittent",
        "P0120" to "Throttle / pedal position sensor A circuit",
        "P0124" to "Throttle / pedal position sensor A circuit intermittent",
        "P0125" to "Insufficient coolant temperature for closed loop fuel control",
        "P0129" to "Barometric pressure too low",
        "P0142" to "O2 sensor circuit (B1S3)",
        "P0143" to "O2 sensor circuit low voltage (B1S3)",
        "P0144" to "O2 sensor circuit high voltage (B1S3)",
        "P0150" to "O2 sensor circuit (B2S1)",
        "P0151" to "O2 sensor circuit low voltage (B2S1)",
        "P0152" to "O2 sensor circuit high voltage (B2S1)",
        "P0153" to "O2 sensor slow response (B2S1)",
        "P0154" to "O2 sensor no activity (B2S1)",
        "P0155" to "O2 sensor heater circuit (B2S1)",
        "P0156" to "O2 sensor circuit (B2S2)",
        "P0157" to "O2 sensor circuit low voltage (B2S2)",
        "P0158" to "O2 sensor circuit high voltage (B2S2)",
        "P0159" to "O2 sensor slow response (B2S2)",
        "P0160" to "O2 sensor no activity (B2S2)",
        "P0161" to "O2 sensor heater circuit (B2S2)",
        "P0170" to "Fuel trim malfunction (bank 1)",
        "P0173" to "Fuel trim malfunction (bank 2)",
        "P0176" to "Fuel composition sensor circuit",
        "P0180" to "Fuel temperature sensor A circuit",
        "P0190" to "Fuel rail pressure sensor circuit",
        "P0194" to "Fuel rail pressure sensor circuit intermittent",
        "P0195" to "Engine oil temperature sensor",
        "P0196" to "Engine oil temperature sensor range/performance",
        "P0197" to "Engine oil temperature sensor low",
        "P0198" to "Engine oil temperature sensor high",
        "P0200" to "Injector circuit",
        "P0205" to "Injector circuit, cylinder 5",
        "P0206" to "Injector circuit, cylinder 6",
        "P0207" to "Injector circuit, cylinder 7",
        "P0208" to "Injector circuit, cylinder 8",
        "P0220" to "Throttle / pedal position sensor B circuit",
        "P0224" to "Throttle / pedal position sensor B circuit intermittent",
        "P0225" to "Throttle / pedal position sensor C circuit",
        "P0226" to "Throttle / pedal position sensor C range/performance",
        "P0227" to "Throttle / pedal position sensor C circuit low",
        "P0228" to "Throttle / pedal position sensor C circuit high",
        "P0233" to "Fuel pump secondary circuit intermittent",
        "P0234" to "Turbo / supercharger overboost condition",
        "P0235" to "Turbo boost sensor A circuit",
        "P0324" to "Knock control system error",
        "P0326" to "Knock sensor 1 circuit range/performance",
        "P0329" to "Knock sensor 1 circuit intermittent",
        "P0330" to "Knock sensor 2 circuit (bank 2)",
        "P0337" to "Crankshaft position sensor A circuit low",
        "P0338" to "Crankshaft position sensor A circuit high",
        "P0339" to "Crankshaft position sensor A circuit intermittent",
        "P0342" to "Camshaft position sensor A circuit low (bank 1)",
        "P0343" to "Camshaft position sensor A circuit high (bank 1)",
        "P0344" to "Camshaft position sensor A circuit intermittent (bank 1)",
        "P0345" to "Camshaft position sensor A circuit (bank 2)",
        "P0355" to "Ignition coil E circuit",
        "P0356" to "Ignition coil F circuit",
        "P0357" to "Ignition coil G circuit",
        "P0358" to "Ignition coil H circuit",
        "P0366" to "Camshaft position sensor B circuit range/performance (bank 1)",
        "P0367" to "Camshaft position sensor B circuit low (bank 1)",
        "P0368" to "Camshaft position sensor B circuit high (bank 1)",
        "P0406" to "EGR sensor A circuit high",
        "P0410" to "Secondary air injection system",
        "P0411" to "Secondary air injection system incorrect flow",
        "P0444" to "EVAP purge control valve circuit open",
        "P0445" to "EVAP purge control valve circuit shorted",
        "P0454" to "EVAP pressure sensor intermittent",
        "P0458" to "EVAP purge control valve circuit low",
        "P0459" to "EVAP purge control valve circuit high",
        "P0460" to "Fuel level sensor circuit",
        "P0464" to "Fuel level sensor circuit intermittent",
        "P0465" to "EVAP purge flow sensor circuit",
        "P0482" to "Cooling fan 3 control circuit",
        "P0483" to "Cooling fan rationality check",
        "P0484" to "Cooling fan circuit over current",
        "P0485" to "Cooling fan power/ground circuit",
        "P0489" to "EGR control circuit low",
        "P0490" to "EGR control circuit high",
        "P0496" to "EVAP flow during non-purge",
        "P0497" to "EVAP low purge flow",
        "P0510" to "Closed throttle position switch",
        "P0530" to "A/C refrigerant pressure sensor circuit",
        "P0531" to "A/C refrigerant pressure sensor range/performance",
        "P0532" to "A/C refrigerant pressure sensor circuit low",
        "P0533" to "A/C refrigerant pressure sensor circuit high",
        "P0534" to "A/C refrigerant charge loss",
        "P0550" to "Power steering pressure sensor/switch circuit",
        "P0551" to "Power steering pressure sensor/switch range/performance",
        "P0552" to "Power steering pressure sensor/switch circuit low",
        "P0553" to "Power steering pressure sensor/switch circuit high",
        "P0560" to "System voltage",
        "P0561" to "System voltage unstable",
        "P0564" to "Cruise control multi-function input A circuit",
        "P0565" to "Cruise control on signal",
        "P0566" to "Cruise control off signal",
        "P0567" to "Cruise control resume signal",
        "P0568" to "Cruise control set signal",
        "P0572" to "Brake switch A circuit low",
        "P0573" to "Brake switch A circuit high",
        "P0574" to "Cruise control system, vehicle speed too high",
        "P0604" to "Internal control module random access memory (RAM) error",
        "P0605" to "Internal control module read only memory (ROM) error",
        "P0607" to "Control module performance",
        "P0611" to "Fuel injector control module performance",
        "P0615" to "Starter relay circuit",
        "P0616" to "Starter relay circuit low",
        "P0617" to "Starter relay circuit high",
        "P0623" to "Generator lamp control circuit",
        "P0624" to "Fuel cap lamp control circuit",
        "P0625" to "Generator field/F terminal circuit low",
        "P0626" to "Generator field/F terminal circuit high",
        "P0627" to "Fuel pump A control circuit/open",
        "P0628" to "Fuel pump A control circuit low",
        "P0629" to "Fuel pump A control circuit high",
        "P0634" to "Control module internal temperature too high",
        "P0635" to "Power steering control circuit",
        "P0636" to "Power steering control circuit low",
        "P0637" to "Power steering control circuit high",
        "P0642" to "Sensor reference voltage A circuit low",
        "P0643" to "Sensor reference voltage A circuit high",
        "P0645" to "A/C clutch relay control circuit",
        "P0646" to "A/C clutch relay control circuit low",
        "P0647" to "A/C clutch relay control circuit high",
        "P0650" to "Malfunction indicator lamp (MIL) control circuit",
        "P0652" to "Sensor reference voltage B circuit low",
        "P0653" to "Sensor reference voltage B circuit high",
        "P0657" to "Actuator supply voltage A circuit/open",
        "P0658" to "Actuator supply voltage A circuit low",
        "P0659" to "Actuator supply voltage A circuit high",
        "P0704" to "Clutch switch input circuit",
        "P0709" to "Transmission fluid temperature sensor A circuit intermittent",
        "P0710" to "Transmission fluid temperature sensor A circuit",
        "P0718" to "Input/turbine speed sensor circuit intermittent",
        "P0723" to "Output speed sensor circuit intermittent",
        "P0724" to "Brake switch B circuit high",
        "P0725" to "Engine speed input circuit",
        "P0726" to "Engine speed input circuit range/performance",
        "P0727" to "Engine speed input circuit no signal",
        "P0729" to "Gear 6 incorrect ratio",
        "P0740" to "Torque converter clutch circuit",
        "P0744" to "Torque converter clutch circuit intermittent",
        "P0745" to "Pressure control solenoid A",
        "P0749" to "Pressure control solenoid A intermittent",
        "P0750" to "Shift solenoid A",
        "P0754" to "Shift solenoid A intermittent",
        "P0755" to "Shift solenoid B",
        "P0759" to "Shift solenoid B intermittent",
        "P0760" to "Shift solenoid C",
        "P0761" to "Shift solenoid C performance / stuck off",
        "P0762" to "Shift solenoid C stuck on",
        "P0763" to "Shift solenoid C electrical",
        "P0765" to "Shift solenoid D",
        "P0766" to "Shift solenoid D performance / stuck off",
        "P0767" to "Shift solenoid D stuck on",
        "P0768" to "Shift solenoid D electrical",
        "P0775" to "Pressure control solenoid B",
        "P0780" to "Shift malfunction",
        "P0781" to "1-2 shift malfunction",
        "P0782" to "2-3 shift malfunction",
        "P0783" to "3-4 shift malfunction",
        "P0784" to "4-5 shift malfunction",
        "P0785" to "Shift/timing solenoid",
        "P0790" to "Normal/performance switch circuit",
        "P0791" to "Intermediate shaft speed sensor A circuit",
        "P0795" to "Pressure control solenoid C",
        "P0801" to "Reverse inhibit control circuit",
        "P0805" to "Clutch position sensor circuit",
        "P0840" to "Transmission fluid pressure sensor/switch A circuit",
        "P0845" to "Transmission fluid pressure sensor/switch B circuit",
        "P0850" to "Park/neutral switch input circuit",
        "P0870" to "Transmission fluid pressure sensor/switch C circuit",
        "P0960" to "Pressure control solenoid A control circuit/open",
        "P0961" to "Pressure control solenoid A control circuit range/performance",
        "P0964" to "Pressure control solenoid B control circuit/open",
        "P0965" to "Pressure control solenoid B control circuit range/performance",
        "P0966" to "Pressure control solenoid B control circuit low",
        "P0967" to "Pressure control solenoid B control circuit high",
        "P0968" to "Pressure control solenoid C control circuit/open",
        "P0969" to "Pressure control solenoid C control circuit range/performance",
        "P0970" to "Pressure control solenoid C control circuit low",
        "P0971" to "Pressure control solenoid C control circuit high",
        "P0973" to "Shift solenoid A control circuit low",
        "P0974" to "Shift solenoid A control circuit high",
        "P0976" to "Shift solenoid B control circuit low",
        "P0977" to "Shift solenoid B control circuit high",
        "P0979" to "Shift solenoid C control circuit low",
        "P0980" to "Shift solenoid C control circuit high",
        "P2088" to "Camshaft position actuator A control circuit low (bank 1)",
        "P2089" to "Camshaft position actuator A control circuit high (bank 1)",
        "P2090" to "Camshaft position actuator B control circuit low (bank 1)",
        "P2091" to "Camshaft position actuator B control circuit high (bank 1)",
        "P2098" to "Post-catalyst fuel trim too lean (bank 2)",
        "P2099" to "Post-catalyst fuel trim too rich (bank 2)",
        "P2100" to "Throttle actuator control motor circuit/open",
        "P2102" to "Throttle actuator control motor circuit low",
        "P2103" to "Throttle actuator control motor circuit high",
        "P2104" to "Throttle actuator control system forced idle",
        "P2105" to "Throttle actuator control system forced engine shutdown",
        "P2107" to "Throttle actuator control module processor",
        "P2108" to "Throttle actuator control module performance",
        "P2118" to "Throttle actuator control motor current range/performance",
        "P2120" to "Throttle / pedal position sensor D circuit",
        "P2121" to "Throttle / pedal position sensor D range/performance",
        "P2125" to "Throttle / pedal position sensor E circuit",
        "P2126" to "Throttle / pedal position sensor E range/performance",
        "P2130" to "Throttle / pedal position sensor F circuit",
        "P2136" to "Throttle / pedal position sensors A/C voltage correlation",
        "P2137" to "Throttle / pedal position sensors B/C voltage correlation",
        "P2181" to "Cooling system performance",
        "P2183" to "Engine coolant temperature sensor 2 circuit",
        "P2184" to "Engine coolant temperature sensor 2 circuit low",
        "P2185" to "Engine coolant temperature sensor 2 circuit high",
        "P2189" to "System too lean at idle (bank 2)",
        "P2190" to "System too rich at idle (bank 2)",
        "P2191" to "System too lean at higher load (bank 1)",
        "P2192" to "System too rich at higher load (bank 1)",
        "P2193" to "System too lean at higher load (bank 2)",
        "P2194" to "System too rich at higher load (bank 2)",
        "P2197" to "O2 sensor signal stuck lean (B2S1)",
        "P2198" to "O2 sensor signal stuck rich (B2S1)",
        "P2300" to "Ignition coil A primary control circuit low",
        "P2301" to "Ignition coil A primary control circuit high",
        "P2303" to "Ignition coil B primary control circuit low",
        "P2304" to "Ignition coil B primary control circuit high",
        "P2306" to "Ignition coil C primary control circuit low",
        "P2307" to "Ignition coil C primary control circuit high",
        "P2309" to "Ignition coil D primary control circuit low",
        "P2310" to "Ignition coil D primary control circuit high",
        "P2400" to "EVAP leak detection pump control circuit/open",
        "P2401" to "EVAP leak detection pump control circuit low",
        "P2402" to "EVAP leak detection pump control circuit high",
        "P2404" to "EVAP leak detection pump sense circuit range/performance",
        "P2422" to "EVAP vent valve stuck closed",
        "P2423" to "HC adsorption catalyst efficiency below threshold",
        "P2500" to "Generator lamp/L-terminal circuit low",
        "P2501" to "Generator lamp/L-terminal circuit high",
        "P2503" to "Charging system voltage low",
        "P2504" to "Charging system voltage high",
        "P2505" to "Control module power input signal",
        "P2507" to "Control module power input signal low",
        "P2509" to "Control module power input signal intermittent",
        "P2510" to "Control module power relay sense circuit range/performance",
        "P2647" to "Rocker arm actuator system stuck on (bank 1)",
        "U0001" to "High speed CAN communication bus",
        "U0002" to "High speed CAN communication bus performance",
        "U0003" to "High speed CAN communication bus (+) open",
        "U0004" to "High speed CAN communication bus low",
        "U0005" to "High speed CAN communication bus high",
        "U0010" to "Medium speed CAN communication bus",
        "U0074" to "Control module communication bus B off",
        "U0075" to "Control module communication bus C off",
        "U0102" to "Lost communication with transfer case control module",
        "U0103" to "Lost communication with gear shift module",
        "U0104" to "Lost communication with cruise control module",
        "U0105" to "Lost communication with fuel injector control module",
        "U0107" to "Lost communication with throttle actuator control module",
        "U0109" to "Lost communication with fuel pump control module",
        "U0123" to "Lost communication with yaw rate sensor module",
        "U0124" to "Lost communication with lateral acceleration sensor module",
        "U0125" to "Lost communication with multi-axis acceleration sensor module",
        "U0127" to "Lost communication with tire pressure monitor module",
        "U0128" to "Lost communication with park brake control module",
        "U0129" to "Lost communication with brake system control module",
        "U0130" to "Lost communication with steering effort control module",
        "U0131" to "Lost communication with power steering control module",
        "U0141" to "Lost communication with body control module B",
        "U0142" to "Lost communication with body control module C",
        "U0146" to "Lost communication with gateway A",
        "U0147" to "Lost communication with gateway B",
        "U0152" to "Lost communication with side restraint control module left",
        "U0153" to "Lost communication with side restraint control module right",
        "U0154" to "Lost communication with restraints occupant sensing control module",
        "U0156" to "Lost communication with information center A",
        "U0157" to "Lost communication with information center B",
        "U0158" to "Lost communication with head up display",
        "U0159" to "Lost communication with parking assist control module",
        "U0160" to "Lost communication with audible alert control module",
        "U0163" to "Lost communication with navigation control module",
        "U0165" to "Lost communication with auxiliary heater control module",
        "U0168" to "Lost communication with vehicle security control module",
        "U0169" to "Lost communication with sunroof control module",
        "U0198" to "Lost communication with telematic control module",
        "U0199" to "Lost communication with door control module A",
        "U0200" to "Lost communication with door control module B",
        "U0212" to "Lost communication with steering column control module",
        "U0301" to "Software incompatibility with engine control module",
        "U0302" to "Software incompatibility with transmission control module",
        "U0403" to "Invalid data received from gear shift module",
        "U0404" to "Invalid data received from cruise control module",
        "U0416" to "Invalid data received from yaw rate sensor module",
        "U0422" to "Invalid data received from body control module",
        "U0423" to "Invalid data received from instrument panel control module",
        "U0424" to "Invalid data received from HVAC control module",
    )

    fun known(code: String): Boolean = names.containsKey(code.take(5).uppercase())

    /** Description of a code; falls back to what the code number itself tells (system and group). */
    fun describe(codeIn: String): String {
        val code = codeIn.take(5).uppercase()
        names[code]?.let { return it }
        if (code.length < 5) return "Unknown code"
        val sys = code[0]
        val manufacturer = code[1] == '1' || code[1] == '3'
        val group = code[2]
        val prefix = if (manufacturer) "Manufacturer-specific (GM) code" else "Generic code"
        val area = when (sys) {
            'P' -> when (group) {
                '0' -> "fuel and air metering / auxiliary emission"
                '1' -> "fuel and air metering"
                '2' -> "fuel and air metering (injector circuit)"
                '3' -> "ignition system or misfire"
                '4' -> "auxiliary emission controls"
                '5' -> "vehicle speed, idle control and inputs"
                '6' -> "computer and output circuits"
                '7', '8', '9' -> "transmission"
                else -> "powertrain"
            }
            'C' -> "brakes, ABS, steering or suspension"
            'B' -> "body systems (airbags, climate, doors, lighting)"
            else -> "module communication"
        }
        return "$prefix: $area (details in AI analysis)"
    }

    /** 0 = information, 1 = fix soon, 2 = do not keep driving / urgent. */
    fun severity(code: String): Int {
        val c = code.take(5).uppercase()
        if (c in setOf("P0217", "P0218", "P0524", "P0521", "P0522", "P0685", "P0690")) return 2
        if (c.startsWith("B0") || c.startsWith("C0") || c.startsWith("C1")) return 2
        if (c in setOf("P0300", "P0301", "P0302", "P0303", "P0304", "P0305", "P0306")) return 2
        if (c.startsWith("P07") || c.startsWith("P08") || c.startsWith("P2106") || c.startsWith("P2110") || c.startsWith("U0")) return 1
        if (c.startsWith("P044") || c.startsWith("P045") || c == "P0128" || c.startsWith("P0420") || c.startsWith("P0430")) return 0
        return 1
    }

    fun severityText(s: Int) = when (s) {
        2 -> "Urgent: avoid hard driving and get it checked soon"
        1 -> "Fix soon: the car usually drives, but damage or more faults can follow"
        else -> "Low urgency: emissions or minor"
    }

    /** Plain probable causes (offline). The AI analysis goes deeper using live data. */
    fun causes(codeIn: String): List<String> {
        val c = codeIn.take(5).uppercase()
        val misfire = Regex("^P030[1-8]$")
        return when {
            c == "P0300" -> listOf("Worn spark plugs or weak ignition coils", "Vacuum/intake leak or dirty throttle body", "Low fuel pressure or clogged injectors", "Carbon build-up in cylinders", "Camshaft timing (VVT) problem")
            misfire.matches(c) -> listOf("Spark plug and ignition coil of cylinder ${c.last()} (swap coils to test)", "Injector of that cylinder clogged or failing", "Compression loss in that cylinder (valve/ring)", "Vacuum leak near that cylinder")
            c == "P0171" || c == "P0174" -> listOf("Vacuum leak (intake hose, PCV hose, manifold gasket)", "Dirty or failing MAF sensor", "Weak fuel pump or clogged fuel filter", "Leaking injector or low fuel pressure", "Exhaust leak before the O2 sensor")
            c == "P0172" || c == "P0175" -> listOf("Leaking or stuck-open injector", "Dirty/faulty MAF sensor", "Fuel pressure too high", "Stuck-open EVAP purge valve", "Failing upstream O2 sensor")
            c == "P0420" || c == "P0430" -> listOf("Worn catalytic converter (common with age and heat)", "Failing downstream O2 sensor", "Exhaust leak near the sensors", "Earlier misfires or rich running that damaged the catalyst")
            c == "P0455" || c == "P0457" || c == "P0442" || c == "P0456" || c == "P0440" -> listOf("Fuel cap loose, cracked or wrong (try first, clear, drive a few days)", "EVAP purge valve or vent valve stuck", "Cracked EVAP hose or filler neck", "EVAP pressure sensor fault")
            c == "P0441" || c == "P0446" || c == "P0443" -> listOf("EVAP purge or vent valve stuck/failed", "Blocked or cracked EVAP hose", "Wiring or connector of the valve")
            c == "P0128" -> listOf("Thermostat stuck open", "Coolant temperature sensor reading wrong", "Low coolant level")
            c == "P0117" || c == "P0118" || c == "P0116" -> listOf("Coolant temperature sensor or its connector", "Wiring short/open", "Real overheating or low coolant (check level first)")
            c in setOf("P0011", "P0012", "P0013", "P0014", "P0016", "P0017") -> listOf("Low oil level or old/wrong-grade oil (check first)", "Camshaft position (VVT) solenoid or actuator", "Stretched timing chain on high-mileage engines", "Crank/cam sensor signal")
            c in setOf("P0101", "P0102", "P0103") -> listOf("Dirty or damaged MAF sensor", "Air leak after the MAF (loose intake tube/air filter box)", "Connector or wiring problem")
            c in setOf("P0106", "P0107", "P0108") -> listOf("MAP sensor or its connector", "Vacuum leak", "Wiring short/open")
            c in setOf("P0121", "P0122", "P0123", "P0221", "P0222", "P0223", "P2135", "P2101", "P2119", "P2176", "P2106", "P2110", "P2111", "P2112") -> listOf("Dirty or failing electronic throttle body (clean, then relearn)", "Throttle/pedal sensor or connector", "Low battery voltage or poor ground", "Throttle motor wear")
            c in setOf("P0335", "P0336", "P0340", "P0341", "P0365") -> listOf("Crankshaft/camshaft position sensor or connector", "Wiring or reluctor wheel damage", "Weak battery or starter dip during cranking")
            c in setOf("P0562", "P0563", "P0620", "P0621", "P0622") -> listOf("Weak battery or loose/corroded terminals", "Failing alternator or regulator", "Bad ground strap", "Drive belt slipping")
            c.startsWith("P050") || c.startsWith("P051") -> listOf("Dirty throttle body", "Vacuum leak", "Idle not learned after cleaning or battery disconnect")
            c in setOf("P0500", "P0501", "P0502", "P0503") -> listOf("Wheel/vehicle speed sensor or connector", "ABS module signal problem", "Wiring damage")
            c in setOf("P0520", "P0521", "P0522", "P0523") -> listOf("Check oil level and pressure first (stop driving if low)", "Oil pressure sensor or connector", "Wiring short/open")
            c == "P0524" -> listOf("Oil level too low: stop and check immediately", "Worn oil pump or blocked pickup", "Oil too thin or diluted")
            c == "P0217" -> listOf("Low coolant, fan not working, thermostat stuck closed, water pump, or blocked radiator. Stop driving: overheating destroys engines")
            c == "P0700" -> listOf("The transmission module (TCM) stored its own code: read the transmission module codes (P07xx) from the Faults screen")
            c.startsWith("P073") -> listOf("Low or old transmission fluid (check level and colour)", "Worn clutch packs or valve body", "Speed sensor input/output signal problem", "Needs transmission specialist if it persists")
            c in setOf("P0741", "P0742", "P0743") -> listOf("Torque converter clutch solenoid or wiring", "Old/burnt fluid", "Worn torque converter")
            c.startsWith("P075") || c.startsWith("P0746") || c.startsWith("P0747") || c.startsWith("P0748") || c.startsWith("P077") || c.startsWith("P079") || c.startsWith("P096") -> listOf("Shift or pressure control solenoid or its connector", "Dirty/old fluid blocking the valve body", "Transmission harness or connector at the case")
            c in setOf("P0715", "P0716", "P0717", "P0720", "P0721", "P0722") -> listOf("Input/output speed sensor or connector", "Damaged wiring at the transmission", "Low fluid or internal damage")
            c in setOf("P0711", "P0712", "P0713", "P0218") -> listOf("Transmission fluid too hot (towing, heat, low fluid) or temperature sensor wiring", "Cooler line restriction")
            c.startsWith("U0") -> listOf("Weak battery or low voltage (very common cause of U-codes)", "Damaged wiring or connector on the CAN bus", "Bad ground", "A module that failed or lost power. If many modules are 'lost', suspect power/ground or the bus")
            c.startsWith("C00") || c.startsWith("C02") -> listOf("Wheel speed sensor, its wiring or the tone ring (dirt, metal debris, rust)", "Wheel bearing play", "Low battery voltage", "ABS module")
            c.startsWith("B0") -> listOf("Airbag (SRS) fault: do not probe airbag connectors with the battery connected", "Connector under the seat or clock spring", "Seat belt buckle/pretensioner circuit or occupant sensor", "Have the SRS scanned and repaired by a workshop")
            else -> emptyList()
        }
    }
}
