package com.example.obdy

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AdapterInfo(val name: String, val voltage: Double?, val protocol: String)

class FoundModule(val probe: ModuleProbe, val firstAnswer: String)
class ModuleScan(val faults: List<Fault>, val notes: List<String>)
class FreezeFrame(val code: String?, val values: List<Pair<Pid, Double>>)

/**
 * Talks to the ELM327 adapter. Every conversation with the car goes through [exclusive], which lets only one task use the
 * adapter at a time (live polling pauses while a scan runs). Never call [exclusive] from inside [exclusive].
 */
class ElmClient(private val transport: Transport) {
    private val mutex = Mutex()
    val label: String get() = transport.label

    suspend fun <T> exclusive(block: suspend ElmClient.() -> T): T = mutex.withLock { block() }

    fun close() = transport.close()

    // ---------------------------------------------------------------- low level (call inside exclusive)
    suspend fun raw(cmd: String, timeoutMs: Long = 3000): String {
        transport.drain()
        transport.write(cmd)
        val r = transport.readUntilPrompt(timeoutMs)
        AppLog.i(">> $cmd")
        AppLog.i("<< " + r.replace('\r', ' ').replace('\n', ' ').trim())
        return r
    }

    suspend fun query(cmd: String, timeoutMs: Long = 3000): ElmReply = ObdParse.parse(raw(cmd, timeoutMs))

    private suspend fun at(cmd: String): Boolean = raw(cmd, 2500).uppercase().contains("OK")

    private fun ecmFrame(r: ElmReply, service: Int, pid: Int? = null): Frame? {
        val list = r.frames.filter { it.data.isNotEmpty() && it.u(0) == service + 0x40 && (pid == null || it.u(1) == pid) }
        return list.firstOrNull { it.header == "7E8" } ?: list.firstOrNull()
    }

    // ---------------------------------------------------------------- start up
    /** Sets the adapter up and finds the car. Throws IllegalStateException with a plain explanation if it cannot. */
    suspend fun initialize(): AdapterInfo {
        raw("ATZ", 5000)
        at("ATE0"); at("ATL0"); at("ATS1"); at("ATH1"); at("ATAT1")
        val name = raw("ATI", 2000).replace(">", "").trim().lines().lastOrNull { it.isNotBlank() }?.trim() ?: "ELM327"
        val volt = voltage()
        at("ATSP6")
        var ok = query("0100", 8000).ok
        if (!ok) { at("ATSP0"); ok = query("0100", 12000).ok }
        if (!ok) {
            val v = if (volt != null && volt < 11.0) " The adapter reads only ${Calc.fmt(volt, 1)} V: the battery is low." else ""
            throw IllegalStateException("The adapter answers but the car does not. Turn the ignition ON (engine can be off), check the adapter is fully pushed into the OBD port, then try again.$v")
        }
        val proto = raw("ATDPN", 2000).replace(">", "").trim().lines().lastOrNull { it.isNotBlank() }?.trim() ?: "?"
        return AdapterInfo(name, volt, proto)
    }

    /** Battery voltage at the OBD port, read by the adapter itself (works without the engine computer). */
    suspend fun voltage(): Double? {
        val r = raw("ATRV", 1500)
        val m = Regex("(\\d{1,2}\\.\\d)\\s*V?", RegexOption.IGNORE_CASE).find(r) ?: return null
        return m.groupValues[1].toDoubleOrNull()
    }

    // ---------------------------------------------------------------- standard OBD-II
    suspend fun supportedPids(): Set<Int> {
        val all = HashSet<Int>()
        var base = 0
        while (base <= 0x80) {
            val r = query("01%02X".format(base), 4000)
            val f = ecmFrame(r, 0x01, base) ?: break
            val set = Pids.supported(base, f.data.copyOfRange(2, minOf(f.data.size, 6)))
            all.addAll(set)
            if ((base + 0x20) !in set) break
            base += 0x20
        }
        return all
    }

    /** One live value; null if the car does not answer. */
    suspend fun readPid(pid: Pid): Double? {
        val r = query(pid.code, 1500)
        val f = ecmFrame(r, 0x01, pid.id) ?: return null
        return pid.value(f.data.copyOfRange(2, f.data.size))
    }

    suspend fun readVin(): String? = Vin.decode(ecmFrame(query("0902", 5000), 0x09, 0x02))

    suspend fun readMonitors(): MonitorStatus? {
        val f = ecmFrame(query("0101", 3000), 0x01, 0x01) ?: return null
        return Readiness.decode(f.data.copyOfRange(2, f.data.size))
    }

    /** Stored (03), pending (07) and permanent (0A) codes from every engine/transmission module that answers. */
    suspend fun readObdFaults(): List<Fault> {
        val out = ArrayList<Fault>()
        for ((svc, kind) in listOf(0x03 to "stored", 0x07 to "pending", 0x0A to "permanent")) {
            val r = query("%02X".format(svc), 5000)
            for (f in r.frames) {
                if (f.data.isEmpty() || f.u(0) != svc + 0x40) continue
                for (code in DtcDecode.fromMode(f, svc)) out.add(Fault(code, Gm.moduleName(f.header), kind))
            }
        }
        return out
    }

    suspend fun readFreezeFrame(): FreezeFrame? {
        val r0 = query("0202", 3000)
        val f0 = ecmFrame(r0, 0x02, 0x02) ?: return null
        val code = if (f0.data.size >= 5 && (f0.u(3) != 0 || f0.u(4) != 0)) DtcDecode.fromBytes(f0.u(3), f0.u(4)) else null
        if (code == null) return FreezeFrame(null, emptyList())
        val vals = ArrayList<Pair<Pid, Double>>()
        for (id in listOf(0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0F, 0x11, 0x42)) {
            val p = Pids.byId(id) ?: continue
            val r = query("02%02X00".format(id), 2000)
            val f = ecmFrame(r, 0x02, id) ?: continue
            if (f.data.size >= 3 + p.bytes) p.value(f.data.copyOfRange(3, f.data.size))?.let { vals.add(p to it) }
        }
        return FreezeFrame(code, vals)
    }

    /** Mode 04: erases emission codes, freeze frame and resets the readiness monitors. */
    suspend fun clearEmissionCodes(): Boolean = query("04", 6000).frames.any { it.u(0) == 0x44 }

    // ---------------------------------------------------------------- other modules (ABS, airbag, body ...)
    private suspend fun aim(p: ModuleProbe) { at("ATSH${p.req}"); at("ATCRA${p.resp}") }
    private suspend fun aimReset() { at("ATCRA"); at("ATSH7DF") }

    /** Looks for modules that answer a "tester present" request. Slow (about 10 s), so call it with progress. */
    suspend fun findModules(onProgress: (Int, Int, String) -> Unit): List<FoundModule> {
        val probes = Gm.probes()
        val found = ArrayList<FoundModule>()
        try {
            at("ATST20")
            for ((i, p) in probes.withIndex()) {
                onProgress(i + 1, probes.size, p.name)
                aim(p)
                val r = query("3E00", 900)
                val f = r.frames.firstOrNull { it.header == p.resp || it.header.isEmpty() }
                if (f != null && f.data.isNotEmpty()) found.add(FoundModule(p, f.hex()))
            }
        } finally {
            try { aimReset(); at("ATST32") } catch (_: Exception) {}
        }
        return found
    }

    /** Reads the codes of one module with the common diagnostic services (UDS 19 02, then GM's A9 81). */
    suspend fun readModuleFaults(m: FoundModule): ModuleScan {
        val faults = ArrayList<Fault>(); val notes = ArrayList<String>()
        val name = if (m.probe.verified) m.probe.name else "${m.probe.name} (${m.probe.req}/${m.probe.resp})"
        try {
            aim(m.probe)
            at("ATSTFF")      // GM body/chassis modules can need up to a second to answer (the default wait is 0.2 s)
            val u = query("1902FF", 2500)
            val ok = u.frames.firstOrNull { it.u(0) == 0x59 }
            if (ok != null) {
                val list = DtcDecode.fromUds(ok)
                for ((c, st) in list) faults.add(Fault(c, name, "stored", st, ok.hex()))
                notes.add("$name: ${list.size} code(s) (UDS)")
            } else {
                val g = query("A98112", 2500)
                val ok2 = g.frames.firstOrNull { it.u(0) == 0xE9 }
                if (ok2 != null) {
                    val list = DtcDecode.fromGmlan(ok2)
                    for ((c, st) in list) faults.add(Fault(c, name, "stored", st, ok2.hex()))
                    notes.add("$name: ${list.size} code(s) (GM service A9)")
                } else {
                    val neg = (u.negatives() + g.negatives()).firstOrNull()
                    val gm = if (g.frames.isEmpty()) "GM request A9: no answer" else "GM request A9: " + g.frames.joinToString(" | ") { it.hex() }
                    notes.add("$name answers but did not give codes" + (neg?.let { " (${it.hex()})" } ?: "") + ". $gm. Raw: ${m.firstAnswer}")
                }
            }
        } finally {
            try { aimReset(); at("ATST32") } catch (_: Exception) {}
        }
        return ModuleScan(faults, notes)
    }

    /** Erases the codes of one non-engine module. Returns a short result text. */
    suspend fun clearModule(m: FoundModule): String {
        try {
            aim(m.probe)
            if (query("14FFFFFF", 4000).frames.any { it.u(0) == 0x54 }) return "${m.probe.name}: cleared"
            if (query("04", 4000).frames.any { it.u(0) == 0x44 }) return "${m.probe.name}: cleared"
            return "${m.probe.name}: refused (needs a special session or security access)"
        } finally {
            try { aimReset() } catch (_: Exception) {}
        }
    }

    /** A manufacturer value (Mode 22). Null if that module does not know it. */
    suspend fun readExt(e: ExtPid): Double? {
        try {
            at("ATSH${e.header}")
            val r = query(e.request, 1500)
            val want = e.request.substring(0, 2).toInt(16)
            val f = r.frames.firstOrNull { it.u(0) == want + 0x40 } ?: return null
            if (f.data.size < 3 + e.bytes) return null
            return e.decode(IntArray(e.bytes) { f.u(3 + it) })
        } finally {
            try { at("ATSH7DF") } catch (_: Exception) {}
        }
    }


    /** Several live values in one request (faster). Null when the car does not answer that way. */
    suspend fun readPids(pids: List<Pid>): Map<Int, Double>? {
        if (pids.isEmpty()) return emptyMap()
        val r = query("01" + pids.joinToString("") { "%02X".format(it.id) }, 2000)
        val f = r.frames.firstOrNull { it.header == "7E8" && it.u(0) == 0x41 } ?: r.frames.firstOrNull { it.u(0) == 0x41 } ?: return null
        return Pids.parseMulti(f.data)
    }

    /** Mode 06: the car's own self-test results (misfire counters per cylinder, catalyst, oxygen sensors, EVAP). */
    suspend fun readMode06(onProgress: (String) -> Unit): List<TestResult> {
        val mids = ArrayList<Int>()
        var base = 0
        while (base <= 0xE0) {
            val f = query("06%02X".format(base), 3000).frames.firstOrNull { it.u(0) == 0x46 && it.u(1) == base } ?: break
            val set = Mode06.supported(f)
            mids.addAll(set.filter { it % 0x20 != 0 })
            if ((base + 0x20) !in set) break
            base += 0x20
        }
        val out = ArrayList<TestResult>()
        for ((i, mid) in mids.take(40).withIndex()) {
            onProgress("Reading test ${i + 1} of ${minOf(mids.size, 40)}")
            val r = query("06%02X".format(mid), 2500)
            for (f in r.frames) if (f.u(0) == 0x46 && f.u(1) == mid) out.addAll(Mode06.parse(f))
        }
        return out
    }

    /** Checks the adapter itself: identity, protocol, voltage, speed and whether it can ask for several values at once. */
    suspend fun doctor(supportedCount: Int): List<Verdict> {
        val out = ArrayList<Verdict>()
        fun clean(s: String) = s.replace(">", "").replace('\r', ' ').trim().replace(Regex("\\s+"), " ")
        val ati = clean(raw("ATI", 2000))
        val desc = clean(raw("AT@1", 2000))
        val sti = clean(raw("STI", 2000))
        val dpn = clean(raw("ATDPN", 2000))
        out.add(Verdict("Adapter says", ati.ifBlank { "no answer" }, Level.Info, "Identity: " + desc.ifBlank { "-" } + ". Newer adapters pretend to be an ELM327; what matters is the tests below."))
        out.add(if (sti.uppercase().contains("STN") || sti.uppercase().contains("VLINKER") || sti.uppercase().contains("OBDLINK")) Verdict("Extended command set", sti.take(40), Level.Good, "The adapter has the enhanced STN-style commands.") else Verdict("Extended command set", "not found", Level.Info, "A plain ELM327-style adapter. That is enough for everything Obdy does now."))
        out.add(Verdict("Protocol", dpn.ifBlank { "?" }, if (dpn.contains("6")) Level.Good else Level.Warn, "Your Malibu normally uses protocol 6 (ISO 15765-4 CAN, 500 kbit). Another protocol means the adapter chose differently."))
        val v = voltage()
        out.add(when {
            v == null -> Verdict("Battery voltage", "no reading", Level.Warn, "The adapter did not report the voltage (ATRV).")
            v < 11.5 -> Verdict("Battery voltage", Calc.fmt(v, 1) + " V", Level.Bad, "Low: the adapter may misbehave and the car may give false codes.")
            else -> Verdict("Battery voltage", Calc.fmt(v, 1) + " V", Level.Good, "Fine.")
        })
        val times = ArrayList<Double>()
        var failed = 0
        for (i in 0 until 8) {
            val t0 = System.nanoTime()
            val r = query("0100", 2500)
            times.add((System.nanoTime() - t0) / 1e6)
            if (!r.ok) failed++
        }
        val avg = times.average()
        out.add(when {
            failed > 0 -> Verdict("Answers from the car", "${8 - failed} of 8", Level.Bad, "Some requests got no answer: loose adapter, ignition off, or a poor adapter.")
            avg <= 90 -> Verdict("Speed", Calc.fmt(avg, 0) + " ms per request", Level.Good, "Fast: live data will refresh quickly.")
            avg <= 220 -> Verdict("Speed", Calc.fmt(avg, 0) + " ms per request", Level.Warn, "Usable. Fast mode (several values per request) helps.")
            else -> Verdict("Speed", Calc.fmt(avg, 0) + " ms per request", Level.Bad, "Slow link. Try another connection type, or move the phone closer.")
        })
        val multi = query("010C0D05", 2500)
        val f = multi.frames.firstOrNull { it.u(0) == 0x41 }
        val got = if (f != null) Pids.parseMulti(f.data).size else 0
        out.add(if (got >= 3) Verdict("Several values per request", "works", Level.Good, "Fast mode can read 4 values in one request.") else Verdict("Several values per request", "no", Level.Info, "Obdy will read one value at a time (slower but fine)."))
        out.add(Verdict("Live values available", "$supportedCount", if (supportedCount >= 15) Level.Good else if (supportedCount > 0) Level.Info else Level.Warn, "How many standard values the engine computer reports."))
        return out
    }

    // ---------------------------------------------------------------- console
    /** Sends anything the user types. [header] (3 hex digits) is optional; it is restored afterwards. */
    suspend fun console(header: String?, cmd: String): String {
        val h = header?.trim().orEmpty()
        try {
            if (h.isNotEmpty()) { at("ATSH$h") }
            val r = raw(cmd.trim(), 5000)
            return r.replace(">", "").trim().ifEmpty { "(no reply)" }
        } finally {
            if (h.isNotEmpty()) try { at("ATSH7DF") } catch (_: Exception) {}
        }
    }
}
