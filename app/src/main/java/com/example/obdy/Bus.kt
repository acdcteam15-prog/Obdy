package com.example.obdy

/*
 * Logic for the "pro tools" (bus switching, bus monitor, signal finder, data sweep, safe active tests, performance timers).
 * Everything in this file is plain Kotlin (no Android classes) so it can be unit tested on a computer.
 */

/** The two physical GM networks on the OBD-II port. */
enum class Bus(val label: String) {
    Hs("High-speed CAN (pins 6 + 14, 500 kbit/s)"),
    Sw("Low-speed single-wire CAN (pin 1, 33.3 kbit/s)"),
}

/**
 * STN adapter protocol numbers for GM single-wire CAN, from the scantool.net STN command list.
 * NOTE: 61 and 62 are RAW CAN (ISO 11898), only 63 and 64 are ISO 15765 (diagnostic messages with flow control).
 */
object Stn {
    const val SW_RAW_11 = 61
    const val SW_RAW_29 = 62
    const val SW_ISO_11 = 63
    const val SW_ISO_29 = 64

    /** STCSWM modes: single-wire transceiver mode. */
    const val SWM_SLEEP = 0
    const val SWM_HIGH_SPEED = 1      // 83.3 kbit/s, only for module programming: never used by Obdy
    const val SWM_WAKEUP = 2
    const val SWM_NORMAL = 3

    /** Single-wire CAN diagnostic addresses worth asking (same pattern as the high-speed bus: request 24x..25x, answer +0x400). */
    fun swProbes(): List<ModuleProbe> = (0x241..0x25F).map { n ->
        ModuleProbe("Low-speed module %X".format(n), "%X".format(n), "%X".format(n + 0x400), false, Bus.Sw)
    }
}

// ============================================================================================ raw CAN frames (bus monitor)

/** One frame seen by the monitor. [id] is 3 hex digits (11-bit) or 8 (29-bit). */
class CanFrame(val id: String, val data: IntArray, val tMs: Long) {
    fun hex(): String = data.joinToString(" ") { "%02X".format(it) }
    override fun toString() = "$id  ${hex()}"
}

object CanParse {
    private val hex = Regex("^[0-9A-Fa-f]+$")

    /** Reads the text of an STM / STMA answer (headers on, spaces on). Lines that are not frames are ignored. */
    fun parse(text: String, tMs: Long): List<CanFrame> {
        val out = ArrayList<CanFrame>()
        for (line in text.split('\r', '\n')) {
            val t = line.replace(">", "").trim()
            if (t.isEmpty()) continue
            val tok = t.split(Regex("\\s+"))
            if (tok.size < 2) continue
            val id = tok[0].uppercase()
            if (!(id.length == 3 || id.length == 8) || !hex.matches(id)) continue
            val rest = tok.drop(1)
            if (rest.size > 8 || !rest.all { it.length == 2 && hex.matches(it) }) continue
            out.add(CanFrame(id, IntArray(rest.size) { rest[it].toInt(16) }, tMs))
        }
        return out
    }

    /** Number of times the adapter said it could not keep up ("BUFFER FULL"): the filter is too wide. */
    fun overflows(text: String): Int = Regex("BUFFER FULL|DATA ERROR", RegexOption.IGNORE_CASE).findAll(text).count()
}

/** Everything seen for one CAN id. */
class IdAgg(val id: String) {
    var count = 0; private set
    var firstMs = 0L; private set
    var lastMs = 0L; private set
    var last: IntArray = IntArray(0); private set
    /** For each byte position: which values were seen, and how many times it changed from one frame to the next. */
    val seen = Array(8) { BooleanArray(256) }
    val changes = IntArray(8)

    fun add(f: CanFrame) {
        if (count == 0) firstMs = f.tMs
        lastMs = f.tMs
        for (i in 0 until minOf(8, f.data.size)) {
            seen[i][f.data[i] and 0xFF] = true
            if (count > 0 && i < last.size && last[i] != f.data[i]) changes[i]++
        }
        last = f.data
        count++
    }

    fun distinct(i: Int): Int = seen[i].count { it }
    fun lowest(i: Int): Int = seen[i].indexOfFirst { it }
    fun highest(i: Int): Int = seen[i].indexOfLast { it }
    /** Frames per second over the time this id was seen. */
    fun hz(windowMs: Long): Double = if (windowMs <= 0) 0.0 else count * 1000.0 / windowMs
}

/** A finished (or running) capture: per-id aggregates plus the raw lines for saving. */
class Capture {
    val ids = LinkedHashMap<String, IdAgg>()
    val lines = ArrayList<String>()
    var startMs = 0L
    var endMs = 0L
    var overflow = 0
    var total = 0; private set
    private val maxLines = 20000

    fun add(frames: List<CanFrame>) {
        for (f in frames) {
            ids.getOrPut(f.id) { IdAgg(f.id) }.add(f)
            total++
            if (lines.size < maxLines) lines.add("${f.tMs - startMs};${f.id};${f.hex()}")
        }
    }

    val durationMs: Long get() = maxOf(1L, endMs - startMs)
    fun rows(): List<IdAgg> = ids.values.sortedBy { it.id }
    fun csv(): String = "ms;id;data\n" + lines.joinToString("\n")
}

/** A byte that changed when you did something (open a door, turn the wheel, press the brake). */
class Candidate(
    val id: String, val byteIndex: Int,
    val baseMin: Int, val baseMax: Int, val actMin: Int, val actMax: Int,
    val novel: Int, val bits: Int, val frames: Int,
) {
    val label: String get() = "$id byte ${byteIndex + 1}"
    val summary: String get() = "was %d–%d, now %d–%d (changed bits %s)".format(baseMin, baseMax, actMin, actMax, "%02X".format(bits))
}

object SignalFinder {
    /**
     * Compares a quiet capture with a capture taken while you did one thing. A byte is a candidate when it shows values it never
     * showed in the quiet capture. Bytes that move all the time in the quiet capture (counters, checksums) are skipped.
     */
    fun find(base: Capture, act: Capture): List<Candidate> {
        val out = ArrayList<Candidate>()
        for ((id, a) in act.ids) {
            if (a.count < 6) continue                                                    // too few frames to judge (a thin capture only shows noise)
            val b = base.ids[id]
            if (b != null && b.count < 6) continue
            for (i in 0 until 8) {
                if (a.distinct(i) == 0) continue
                if (b != null && b.distinct(i) >= 12) continue                         // counter / checksum in the quiet capture
                if (b == null && a.distinct(i) >= 12) continue
                var novel = 0
                var bits = 0
                for (v in 0 until 256) {
                    if (a.seen[i][v] && (b == null || !b.seen[i][v])) {
                        novel++
                        val bv = if (b == null || b.distinct(i) == 0) a.lowest(i) else b.lowest(i)
                        bits = bits or (v xor bv)
                    }
                }
                if (novel == 0) continue
                out.add(Candidate(id, i, b?.lowest(i)?.coerceAtLeast(0) ?: 0, b?.highest(i)?.coerceAtLeast(0) ?: 0, a.lowest(i), a.highest(i), novel, bits and 0xFF, a.count))
            }
        }
        // fewest new values first (a door is 1 bit, a counter is many), then the busier id
        return out.sortedWith(compareBy<Candidate> { it.novel }.thenByDescending { it.frames }).take(40)
    }
}

/** A signal you named: where it sits in a CAN frame and how to turn it into a number. */
class CustomSignal(
    val name: String, val id: String, val start: Int, val len: Int,
    val mask: Int = 0, val big: Boolean = true, val signed: Boolean = false,
    val scale: Double = 1.0, val offset: Double = 0.0, val unit: String = "",
) {
    fun decode(data: IntArray): Double? {
        if (start < 0 || len < 1 || len > 4 || start + len > data.size) return null
        var raw = 0L
        if (big) for (i in 0 until len) raw = (raw shl 8) or data[start + i].toLong()
        else for (i in len - 1 downTo 0) raw = (raw shl 8) or data[start + i].toLong()
        if (mask != 0) return if ((raw and mask.toLong()) != 0L) 1.0 else 0.0
        if (signed) {
            val bitsN = len * 8
            if (raw and (1L shl (bitsN - 1)) != 0L) raw -= (1L shl bitsN)
        }
        return raw * scale + offset
    }
}

/** Warning rule on a custom signal. [onlyMoving] needs the car to be driving (speed from another signal or the live data). */
class AlertRule(val signal: String, val op: String, val threshold: Double, val message: String, val onlyMoving: Boolean = false) {
    fun fires(value: Double?, speedKmh: Double?): Boolean {
        if (value == null) return false
        if (onlyMoving && (speedKmh == null || speedKmh < 5.0)) return false
        return when (op) { ">" -> value > threshold; "<" -> value < threshold; else -> value == threshold }
    }
}

// ============================================================================================ fault state

enum class FaultState(val label: String) { Active("Active now"), Pending("Pending"), History("History"), Stored("Stored") }

object FaultStates {
    /** Active = failing now; Pending = seen but not confirmed; History = confirmed earlier, not failing now. Uses the module's status byte. */
    fun of(f: Fault): FaultState {
        if (f.kind == "pending") return FaultState.Pending
        val st = Regex("([0-9A-Fa-f]{2})\\s*$").find(f.status)?.groupValues?.get(1)?.toInt(16) ?: return FaultState.Stored
        return when {
            (st and 0x01) != 0 -> FaultState.Active
            (st and 0x04) != 0 -> FaultState.Pending
            else -> FaultState.History
        }
    }
}

// ============================================================================================ safety

object Safety {
    /** Requests that only read. Anything else must never be sent by a sweep or by the module readers. */
    private val readOnly = setOf(0x01, 0x02, 0x03, 0x06, 0x07, 0x09, 0x0A, 0x19, 0x1A, 0x22, 0x3E)

    fun serviceOf(req: String): Int? {
        val t = req.replace(" ", "")
        if (t.length < 2 || !t.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        return t.substring(0, 2).toInt(16)
    }

    fun isReadOnly(req: String): Boolean {
        val s = serviceOf(req) ?: return false
        if (s == 0xA9) return req.replace(" ", "").uppercase().startsWith("A981")
        return s in readOnly
    }

    /** Services that change something in the car. Active tests may use only 2F, AE and 31. */
    val actuating = setOf(0x2F, 0xAE, 0x31)
    val neverSend = setOf(0x2E, 0x3B, 0x11, 0x27, 0x28, 0x34, 0x35, 0x36, 0x37, 0x85, 0xA5, 0x10)
}

/** A test you set up: a command that makes one part work, with the command that gives control back. */
class ActiveTestDef(
    val id: String, val title: String,
    val req: String, val resp: String,
    val start: String, val stop: String,
    val seconds: Int, val note: String,
)

object ActiveGuard {
    fun cleanHex(s: String): String = s.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()

    /** The command that gives control back to the module: 2F + the 2-byte id + 00 (UDS return control). Empty for other services. */
    fun defaultStop(start: String): String {
        val s = cleanHex(start)
        return if (s.startsWith("2F") && s.length >= 6) s.substring(0, 6) + "00" else ""
    }

    /** Null = allowed. Otherwise the plain reason why the test must not run now. */
    fun refuse(def: ActiveTestDef, speedKmh: Double?, rpm: Double?, volts: Double?): String? {
        val s = cleanHex(def.start)
        if (s.length < 4 || s.length % 2 != 0 || s.length > 32) return "The start command must be 2 to 16 bytes of hex."
        val svc = s.substring(0, 2).toInt(16)
        if (svc !in Safety.actuating) return "Only commands that make a part work (2F, AE or 31) can be used here, not %02X.".format(svc)
        if (def.req.length != 3 || def.resp.length != 3) return "The module needs a request id and an answer id (3 hex digits each)."
        if (svc == 0x2F && cleanHex(def.stop).isEmpty()) return "A 2F test needs a stop command that gives control back to the module."
        val stopSvc = cleanHex(def.stop).takeIf { it.length >= 2 }?.substring(0, 2)?.toInt(16)
        if (stopSvc != null && stopSvc !in Safety.actuating) return "The stop command must also be 2F, AE or 31."
        if (def.seconds !in 1..30) return "The test time must be 1 to 30 seconds."
        if (speedKmh == null) return "Obdy cannot confirm the car is standing still. Open Live data first so the speed is known."
        if (speedKmh >= 1.0) return "The car is moving. Active tests only run when it stands still."
        if (volts != null && volts < 11.8) return "Battery voltage is low (%.1f V). A test could upset the modules; charge the battery first.".format(volts)
        if (rpm != null && rpm > 1200) return "Engine is revving. Let it idle (or switch it off if the test says so)."
        return null
    }
}

// ============================================================================================ data sweep (service 22, read only)

class SweepRange(val label: String, val from: Int, val to: Int)

enum class DidState(val label: String) { Answers("answers"), Locked("exists, needs security access"), Busy("exists, wrong conditions") }
class DidHit(val did: Int, val state: DidState, val payload: IntArray) {
    val code: String get() = "%04X".format(did)
    fun hex(): String = payload.joinToString(" ") { "%02X".format(it) }
}

object DidSweep {
    val presets = listOf(
        SweepRange("Common GM data (0000–02FF)", 0x0000, 0x02FF),
        SweepRange("GM live data (1100–19FF)", 0x1100, 0x19FF),
        SweepRange("Identity (F100–F1FF)", 0xF100, 0xF1FF),
        SweepRange("Wide: 0300–10FF", 0x0300, 0x10FF),
    )

    /** What one request got back: null = nothing usable (silence). */
    class SweepAnswer(val hit: DidHit?, val unsupportedService: Boolean)

    fun judge(did: Int, frames: List<Frame>, respId: String): SweepAnswer {
        val hi = (did shr 8) and 0xFF; val lo = did and 0xFF
        for (f in frames) {
            if (f.header.isNotEmpty() && f.header != respId) continue
            if (f.data.size >= 3 && f.u(0) == 0x62 && f.u(1) == hi && f.u(2) == lo)
                return SweepAnswer(DidHit(did, DidState.Answers, IntArray(f.data.size - 3) { f.u(it + 3) }), false)
            if (f.data.size >= 3 && f.u(0) == 0x7F && f.u(1) == 0x22) {
                return when (f.u(2)) {
                    0x33 -> SweepAnswer(DidHit(did, DidState.Locked, IntArray(0)), false)
                    0x22, 0x21 -> SweepAnswer(DidHit(did, DidState.Busy, IntArray(0)), false)
                    0x11, 0x7F -> SweepAnswer(null, true)
                    else -> SweepAnswer(null, false)          // 31 = request out of range: this id does not exist
                }
            }
        }
        return SweepAnswer(null, false)
    }
}

/** A value you chose to watch from one module (found with the sweep or typed in). */
class WatchDef(
    val name: String, val req: String, val resp: String, val did: String,
    val start: Int = 0, val len: Int = 1, val signed: Boolean = false, val big: Boolean = true,
    val scale: Double = 1.0, val offset: Double = 0.0, val unit: String = "",
) {
    val request: String get() = "22" + did.uppercase()
    fun decode(payload: IntArray): Double? = CustomSignal(name, resp, start, len, 0, big, signed, scale, offset, unit).decode(payload)
}

// ============================================================================================ performance timers

class PerfRun(val kind: String, val seconds: Double, val atMs: Long, val speedKmh: Double, val distanceM: Double)

/**
 * Times 0–60 and 0–100 km/h and 100–0 km/h (with the braking distance) from the speed the engine computer reports on the CAN bus.
 * Feed it every new speed with [add]. Crossing times are interpolated between samples, so the result is better than the sample rate.
 */
class PerfTimer(private val onRun: (PerfRun) -> Unit) {
    enum class Phase { Waiting, Ready, Launch, Brake }
    var phase = Phase.Waiting; private set
    var elapsed = 0.0; private set
    var speed = 0.0; private set
    val hint: String get() = when (phase) {
        Phase.Waiting -> "Stop the car (below 2 km/h) to arm the timer."
        Phase.Ready -> "Ready. Accelerate: the clock starts when the car moves. From 100 km/h, brake to a stop to time the braking."
        Phase.Launch -> "Running…"
        Phase.Brake -> "Braking…"
    }

    private var tPrev = 0.0; private var vPrev = 0.0
    private var t0 = 0.0
    private var standSince = -1.0
    private var peak = 0.0
    private var t60 = -1.0
    private var brakeDist = 0.0
    private var brakeStartV = 100.0
    private var havePrev = false

    private fun cross(t1: Double, v1: Double, t2: Double, v2: Double, target: Double): Double =
        if (v2 == v1) t2 else t1 + (target - v1) / (v2 - v1) * (t2 - t1)

    /** [tSec] = seconds on any steady clock, [kmh] = vehicle speed. */
    fun add(tSec: Double, kmh: Double) {
        speed = kmh
        if (!havePrev || tSec - tPrev > 2.0) {                       // first sample or a gap: start again
            havePrev = true; tPrev = tSec; vPrev = kmh
            phase = if (kmh < 2.0) Phase.Ready else Phase.Waiting
            elapsed = 0.0; standSince = -1.0
            return
        }
        when (phase) {
            Phase.Waiting -> {
                if (kmh < 2.0) { phase = Phase.Ready }
                // braking run: starts when it crosses 100 downwards
                if (vPrev >= 100.0 && kmh < 100.0) startBrake(cross(tPrev, vPrev, tSec, kmh, 100.0), tSec, kmh)
            }
            Phase.Ready -> {
                if (kmh >= 2.0) {
                    // start of the run: project the speed back to 0 km/h along the first rise, so the 2 km/h threshold does not eat time
                    val slope = (kmh - vPrev) / (tSec - tPrev)
                    t0 = if (slope > 0.5) (tSec - kmh / slope).coerceIn(tPrev - 0.5, tSec) else tPrev
                    phase = Phase.Launch; peak = kmh; t60 = -1.0
                    elapsed = tSec - t0
                }
            }
            Phase.Launch -> {
                elapsed = tSec - t0
                if (kmh > peak) peak = kmh
                if (t60 < 0 && vPrev < 60.0 && kmh >= 60.0) {
                    t60 = cross(tPrev, vPrev, tSec, kmh, 60.0)
                    onRun(PerfRun("0–60 km/h", t60 - t0, System.currentTimeMillis(), 60.0, 0.0))
                }
                if (vPrev < 100.0 && kmh >= 100.0) {
                    val tt = cross(tPrev, vPrev, tSec, kmh, 100.0)
                    onRun(PerfRun("0–100 km/h", tt - t0, System.currentTimeMillis(), 100.0, 0.0))
                    phase = Phase.Waiting; elapsed = tt - t0
                } else if (kmh < peak - 8.0 && peak < 100.0) {         // lifted off: not a full run
                    phase = if (kmh < 2.0) Phase.Ready else Phase.Waiting; elapsed = 0.0
                }
            }
            Phase.Brake -> {
                elapsed = tSec - t0
                brakeDist += (vPrev + kmh) / 2.0 / 3.6 * (tSec - tPrev)
                if (kmh > brakeStartV + 6.0) { phase = Phase.Waiting; elapsed = 0.0 }          // gas again: cancelled
                else if (kmh <= 0.5) {
                    val tt = if (vPrev > 0.5) cross(tPrev, vPrev, tSec, 0.0, 0.0) else tPrev
                    onRun(PerfRun("100–0 km/h", tt - t0, System.currentTimeMillis(), 100.0, brakeDist))
                    phase = Phase.Ready; elapsed = tt - t0
                }
            }
        }
        tPrev = tSec; vPrev = kmh
    }

    private fun startBrake(tCross: Double, tSec: Double, kmh: Double) {
        phase = Phase.Brake; t0 = tCross; brakeStartV = 100.0
        // distance from the crossing point to this sample
        brakeDist = (100.0 + kmh) / 2.0 / 3.6 * (tSec - tCross)
        elapsed = tSec - t0
    }

    fun reset() { phase = Phase.Waiting; elapsed = 0.0; havePrev = false }
}

/** Driving and idling time and fuel, kept over the life of the app. [add] every live cycle. */
class Lifetime(var driveSec: Double = 0.0, var idleSec: Double = 0.0, var liters: Double = 0.0, var km: Double = 0.0) {
    fun add(dt: Double, kmh: Double, rpm: Double?, litersPerHour: Double) {
        if (dt <= 0 || dt > 10) return
        val running = rpm != null && rpm > 400
        if (!running && kmh < 1.0) return
        if (kmh >= 1.0) { driveSec += dt; km += kmh * dt / 3600.0 } else idleSec += dt
        liters += litersPerHour * dt / 3600.0
    }
    val idleShare: Double get() = if (driveSec + idleSec > 0) idleSec / (driveSec + idleSec) else 0.0
}

// ============================================================================================ inventory

object Inventory {
    /** True when a module holds a different VIN than the car (a module taken from another car). Blank/unreadable never counts. */
    fun vinMismatch(carVin: String, moduleVin: String?): Boolean {
        val a = carVin.trim().uppercase()
        val b = moduleVin?.trim()?.uppercase().orEmpty()
        if (a.length != 17 || b.length != 17) return false
        if (b.all { it == '0' || it == 'F' || it == ' ' }) return false
        return a != b
    }
}
