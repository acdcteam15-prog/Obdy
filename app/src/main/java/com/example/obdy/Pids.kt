package com.example.obdy

/** A standard Mode 01 value. [bytes] = how many data bytes the car sends back. */
class Pid(
    val id: Int, val name: String, val unit: String, val bytes: Int,
    val min: Double, val max: Double, val decode: (IntArray) -> Double,
    val hint: String = "",
) {
    val code: String get() = "01%02X".format(id)
    fun value(d: ByteArray): Double? {
        if (d.size < bytes) return null
        return decode(IntArray(bytes) { d[it].toInt() and 0xFF })
    }
}

object Pids {
    private fun p(id: Int, name: String, unit: String, bytes: Int, min: Double, max: Double, hint: String = "", f: (IntArray) -> Double) =
        Pid(id, name, unit, bytes, min, max, f, hint)

    val all: List<Pid> = listOf(
        p(0x04, "Engine load", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255 },
        p(0x05, "Coolant temperature", "°C", 1, -40.0, 130.0, "Normal when warm: about 85 to 100 °C") { it[0] - 40.0 },
        p(0x06, "Short fuel trim B1", "%", 1, -25.0, 25.0, "Near 0. Beyond ±10% means the engine is correcting a problem") { it[0] * 100.0 / 128 - 100 },
        p(0x07, "Long fuel trim B1", "%", 1, -25.0, 25.0, "Near 0. Beyond ±10% for long = lean/rich cause") { it[0] * 100.0 / 128 - 100 },
        p(0x0A, "Fuel pressure", "kPa", 1, 0.0, 765.0) { it[0] * 3.0 },
        p(0x0B, "Intake manifold pressure", "kPa", 1, 0.0, 255.0, "Idle: about 30 to 45 kPa") { it[0].toDouble() },
        p(0x0C, "Engine speed", "rpm", 2, 0.0, 7000.0, "Idle: about 600 to 850 rpm warm") { (256 * it[0] + it[1]) / 4.0 },
        p(0x0D, "Vehicle speed", "km/h", 1, 0.0, 240.0) { it[0].toDouble() },
        p(0x0E, "Timing advance", "°", 1, -64.0, 63.5) { it[0] / 2.0 - 64 },
        p(0x0F, "Intake air temperature", "°C", 1, -40.0, 100.0) { it[0] - 40.0 },
        p(0x10, "Air flow (MAF)", "g/s", 2, 0.0, 300.0, "Idle: about 2 to 5 g/s on a 2.5 L") { (256 * it[0] + it[1]) / 100.0 },
        p(0x11, "Throttle position", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255 },
        p(0x14, "O2 sensor B1S1", "V", 2, 0.0, 1.275, "Swings 0.1 to 0.9 V on a working upstream sensor") { it[0] / 200.0 },
        p(0x15, "O2 sensor B1S2", "V", 2, 0.0, 1.275, "Steady about 0.45 to 0.8 V when the catalyst works") { it[0] / 200.0 },
        p(0x1F, "Run time since start", "s", 2, 0.0, 65535.0) { (256 * it[0] + it[1]).toDouble() },
        p(0x21, "Distance with warning light on", "km", 2, 0.0, 65535.0) { (256 * it[0] + it[1]).toDouble() },
        p(0x2C, "Commanded EGR", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255 },
        p(0x2F, "Fuel level", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255 },
        p(0x30, "Warm-ups since codes cleared", "", 1, 0.0, 255.0) { it[0].toDouble() },
        p(0x31, "Distance since codes cleared", "km", 2, 0.0, 65535.0) { (256 * it[0] + it[1]).toDouble() },
        p(0x33, "Barometric pressure", "kPa", 1, 0.0, 255.0) { it[0].toDouble() },
        p(0x3C, "Catalyst temperature B1S1", "°C", 2, -40.0, 900.0) { (256 * it[0] + it[1]) / 10.0 - 40 },
        p(0x3E, "Catalyst temperature B1S2", "°C", 2, -40.0, 900.0) { (256 * it[0] + it[1]) / 10.0 - 40 },
        p(0x42, "Battery / system voltage", "V", 2, 8.0, 16.0, "Engine off ≥ 12.4 V; running 13.5 to 14.8 V") { (256 * it[0] + it[1]) / 1000.0 },
        p(0x43, "Absolute load", "%", 2, 0.0, 300.0) { (256 * it[0] + it[1]) * 100.0 / 255 },
        p(0x44, "Commanded air/fuel (lambda)", "λ", 2, 0.0, 2.0, "1.00 = ideal mixture") { (256 * it[0] + it[1]) / 32768.0 },
        p(0x45, "Relative throttle position", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255 },
        p(0x46, "Ambient air temperature", "°C", 1, -40.0, 80.0) { it[0] - 40.0 },
        p(0x49, "Accelerator pedal D", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255 },
        p(0x4A, "Accelerator pedal E", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255 },
        p(0x4C, "Commanded throttle actuator", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255 },
        p(0x5C, "Oil temperature", "°C", 1, -40.0, 160.0) { it[0] - 40.0 },
        p(0x5E, "Engine fuel rate", "L/h", 2, 0.0, 100.0) { (256 * it[0] + it[1]) / 20.0 },
    )

    private val byId = all.associateBy { it.id }
    fun byId(id: Int): Pid? = byId[id]

    /** The 10 values shown first on the Live screen. */
    val defaultLive = listOf(0x0C, 0x0D, 0x05, 0x04, 0x06, 0x07, 0x42, 0x10, 0x11, 0x0F)

    /** Several values in one answer: 41 <pid> <data> <pid> <data> ... (data = the whole answer including the leading 41). */
    fun parseMulti(data: ByteArray): Map<Int, Double> {
        val out = LinkedHashMap<Int, Double>()
        var i = 1
        while (i < data.size) {
            val pid = byId(data[i].toInt() and 0xFF) ?: break
            if (i + 1 + pid.bytes > data.size) break
            pid.value(data.copyOfRange(i + 1, i + 1 + pid.bytes))?.let { out[pid.id] = it }
            i += 1 + pid.bytes
        }
        return out
    }

    /** Reads the answer to PID 00/20/40/60..: 4 bytes where the highest bit of byte A means PID base+1. */
    fun supported(base: Int, d: ByteArray): Set<Int> {
        val out = HashSet<Int>()
        if (d.size < 4) return out
        for (i in 0 until 32) {
            val byte = d[i / 8].toInt() and 0xFF
            if ((byte shr (7 - i % 8)) and 1 == 1) out.add(base + 1 + i)
        }
        return out
    }
}

class Monitor(val name: String, val supported: Boolean, val complete: Boolean)
class MonitorStatus(val milOn: Boolean, val dtcCount: Int, val monitors: List<Monitor>, val spark: Boolean)

object Readiness {
    /** Decodes the 4 data bytes of Mode 01 PID 01. */
    fun decode(d: ByteArray): MonitorStatus? {
        if (d.size < 4) return null
        val a = d[0].toInt() and 0xFF; val b = d[1].toInt() and 0xFF; val c = d[2].toInt() and 0xFF; val dd = d[3].toInt() and 0xFF
        val spark = (b and 0x08) == 0
        val list = ArrayList<Monitor>()
        list.add(Monitor("Misfire", (b and 0x01) != 0, (b and 0x10) == 0))
        list.add(Monitor("Fuel system", (b and 0x02) != 0, (b and 0x20) == 0))
        list.add(Monitor("Components", (b and 0x04) != 0, (b and 0x40) == 0))
        val names = if (spark) listOf("Catalyst", "Heated catalyst", "Evaporative system", "Secondary air", "A/C refrigerant", "Oxygen sensor", "Oxygen sensor heater", "EGR system")
        else listOf("NMHC catalyst", "NOx aftertreatment", "-", "Boost pressure", "-", "Exhaust gas sensor", "PM filter", "EGR / VVT")
        for (i in 0 until 8) {
            if (names[i] == "-") continue
            list.add(Monitor(names[i], (c shr i) and 1 == 1, (dd shr i) and 1 == 0))
        }
        return MonitorStatus((a and 0x80) != 0, a and 0x7F, list, spark)
    }
}
