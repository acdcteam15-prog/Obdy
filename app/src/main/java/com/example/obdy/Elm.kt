package com.example.obdy

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AdapterInfo(val name: String, val voltage: Double?, val protocol: String)

class FoundModule(val probe: ModuleProbe, val firstAnswer: String, val name: String = probe.name)
/** One line per module for the Faults screen: codes = null when the module never gave a fault list. */
class ModuleResult(val name: String, val ids: String, val codes: Int?, val how: String, val module: FoundModule? = null)
/** Identity of one module: label to value, plus the raw answers. */
class ModuleInfo(val items: List<Pair<String, String>>, val raw: List<String>)
class ModuleScan(val faults: List<Fault>, val notes: List<String>, val result: ModuleResult)
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
        val keep = !isPolling(cmd)
        AppLog.i(">> $cmd", keep)
        AppLog.i("<< " + r.replace('\r', ' ').replace('\n', ' ').trim(), keep)
        return r
    }

    /** Live polling (one value at a time, the manufacturer values and the header switches around them) is not kept in the long log. */
    private fun isPolling(cmd: String): Boolean {
        val c = cmd.uppercase()
        if (c == "ATSH7E0" || c == "ATSH7E2" || c == "ATSH7DF") return true
        if (c.startsWith("22") && c.length == 6) return true
        return c.length in 4..12 && c.startsWith("01") && c !in setOf("0100", "0120", "0140", "0160", "0180", "0101")
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
        while (base <= 0xC0) {
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
    private suspend fun aim(p: ModuleProbe) {
        at("ATSH${p.req}"); at("ATCRA${p.resp}")
        // Long answers from the GM body/chassis modules arrived as the first frame only (see the discovery probe log): answer the
        // module's "send the rest" request ourselves, to the module's own address.
        if (!p.req.startsWith("7E")) { at("ATFCSH${p.req}"); at("ATFCSD300000"); at("ATFCSM1") }
    }
    private suspend fun aimReset() { at("ATFCSM0"); at("ATCRA"); at("ATSH7DF") }

    /** Functional "1A B0" to everyone (101 + extended address FE). Returns answer id -> diagnostic address. Read only. */
    suspend fun broadcastIdentify(): Map<String, Int?> {
        val out = LinkedHashMap<String, Int?>()
        try {
            at("ATFCSM0"); at("ATCRA"); at("ATCAF0"); at("ATSH101"); at("ATSTFF")
            val r = query("FE021AB0", 3000)
            for (f in r.frames) {
                if (!f.header.startsWith("6") && !f.header.startsWith("7")) continue
                // CAF off: bytes are raw, "03 5A B0 aa"
                val i = (0 until f.data.size - 2).firstOrNull { f.u(it) == 0x5A && f.u(it + 1) == 0xB0 }
                out[f.header] = i?.let { f.u(it + 2) }
            }
        } finally {
            try { at("ATCAF1"); aimReset(); at("ATST32") } catch (_: Exception) {}
        }
        return out
    }

    /**
     * Functional "A9 81 12" to everyone: every module with a code answers from its 5xx id. Catches codes from modules the
     * per-module pass missed. Returns answer id (5xx) -> rows. Read only.
     */
    suspend fun broadcastFaults(): Map<String, List<Frame>> {
        try {
            at("ATFCSM0"); at("ATCRA"); at("ATCAF0"); at("ATSH101"); at("ATSTFF")
            val r = query("FE03A98112", 5000)
            return r.frames.filter { it.header.startsWith("5") && it.u(0) == 0x81 }.groupBy { it.header }
        } finally {
            try { at("ATCAF1"); aimReset(); at("ATST32") } catch (_: Exception) {}
        }
    }

    /** Identity of one module (GM service 1A items: part numbers, programming date, VIN copy). Read only. */
    suspend fun readModuleInfo(m: FoundModule): ModuleInfo {
        val items = ArrayList<Pair<String, String>>(); val raw = ArrayList<String>()
        items.add("CAN ids" to "${m.probe.req} → ${m.probe.resp}  (fault list from ${Gm.uudtId(m.probe.resp)})")
        try {
            aim(m.probe); at("ATSTFF")
            for ((did, label) in Gm.identItems) {
                val r = query("1A$did", 2000)
                val f = r.frames.firstOrNull { it.u(0) == 0x5A && it.u(1) == did.toInt(16) }
                raw.add("1A$did -> " + (r.frames.joinToString(" | ") { it.hex() }.ifBlank { "no answer" }))
                if (f != null) items.add(label to Gm.decodeIdent(did, IntArray(f.data.size - 2) { f.u(it + 2) }))
            }
            // Standard OBD identity for engine-side modules (09 0A = the module's own name).
            if (m.probe.resp.startsWith("7E")) {
                val n = query("090A", 3000).frames.firstOrNull { it.u(0) == 0x49 && it.u(1) == 0x0A }
                if (n != null) items.add("Module says it is" to n.data.drop(3).map { it.toInt() and 0xFF }.filter { it in 0x20..0x7E }.joinToString("") { it.toChar().toString() })
            }
        } finally {
            try { aimReset(); at("ATST32") } catch (_: Exception) {}
        }
        return ModuleInfo(items, raw)
    }

    /** Looks for modules that answer a "tester present" request. Slow (about 10 s), so call it with progress. */
    suspend fun findModules(onProgress: (Int, Int, String) -> Unit): List<FoundModule> {
        // 1) Ask every module at once which diagnostic address it has (GM functional id 101, read only). This finds modules
        //    that sit outside the fixed address list below.
        val heard = try { broadcastIdentify() } catch (_: Exception) { emptyMap() }
        val base = Gm.probes()
        val extra = heard.keys.filter { id -> base.none { it.resp == id } }.mapNotNull { Gm.probeForResponse(it) }
        val probes = base + extra
        val found = ArrayList<FoundModule>()
        try {
            at("ATST20")
            for ((i, p) in probes.withIndex()) {
                onProgress(i + 1, probes.size, p.name)
                aim(p)
                val r = query("3E00", 900)
                val f = r.frames.firstOrNull { it.header == p.resp || it.header.isEmpty() }
                    ?: if (p.resp in heard) Frame(p.resp, byteArrayOf(0x5A, 0xB0.toByte())) else null
                if (f != null && f.data.isNotEmpty()) {
                    // Ask which GM diagnostic address it has (1A B0, read only) to give it a real name.
                    val id = query("1AB0", 900).frames.firstOrNull { it.header == p.resp && it.u(0) == 0x5A && it.u(1) == 0xB0 }
                    val addr = if (id != null && id.data.size >= 3) id.u(2) else null
                    found.add(FoundModule(p, f.hex(), if (p.verified) p.name else Gm.nameForDiagAddress(addr, p.name)))
                }
            }
        } finally {
            try { aimReset(); at("ATST32") } catch (_: Exception) {}
        }
        return found
    }

    /**
     * Reads the codes of one module. GM cars of this age (GMLAN) do not know UDS 19, and they send the A9 81 answer as
     * single frames from a SECOND id (5xx, e.g. 641 -> 541, 7E8 -> 5E8). The old receive filter only let 6xx through, so
     * the codes were thrown away by the adapter and the log showed "NO DATA" (that was the "Service Power Steering" bug).
     * [gmOnly] skips the UDS/OBD requests (used for the engine and transmission, already read with Mode 03/07/0A).
     */
    suspend fun readModuleFaults(m: FoundModule, gmOnly: Boolean = false): ModuleScan {
        val faults = ArrayList<Fault>(); val notes = ArrayList<String>()
        val name = m.name
        val ids = "${m.probe.req}/${m.probe.resp}"
        var result = ModuleResult(name, ids, null, "no fault list", m)
        try {
            aim(m.probe)
            at("ATSTFF")      // GM body/chassis modules can need up to a second to answer (the default wait is 0.2 s)
            val tried = ArrayList<String>()
            var done = false
            // 1) GMLAN A9 81 with a receive filter that lets BOTH the normal id and the 5xx fault-list id through.
            val uudt = Gm.uudtId(m.probe.resp)
            val a = m.probe.resp.toInt(16); val b = uudt.toInt(16)
            at("ATCF%03X".format(a and b)); at("ATCM%03X".format(0x7FF and (a xor b).inv()))
            for (req in listOf("A98112", "A981FF", "A98108")) {
                val r = query(req, 4000)
                val rows = r.frames.filter { it.header == uudt && it.u(0) == 0x81 }
                if (rows.isNotEmpty()) {
                    val list = DtcDecode.fromGmlanRows(rows)
                    for (d in list) faults.add(Fault(d.code, name, d.kind, d.status, d.raw))
                    notes.add("$name: ${list.size} code(s) (GM $req from $uudt)")
                    result = ModuleResult(name, ids, list.size, "GM A9 81", m); done = true; break
                }
                val gm = r.frames.firstOrNull { it.u(0) == 0xE9 }
                if (gm != null) {
                    val list = DtcDecode.fromGmlan(gm)
                    for ((c, st) in list) faults.add(Fault(c, name, "stored", st, gm.hex()))
                    notes.add("$name: ${list.size} code(s) (GM $req), raw ${gm.hex()}")
                    result = ModuleResult(name, ids, list.size, "GM A9", m); done = true; break
                }
                tried.add("$req -> " + (if (r.frames.isEmpty()) "no answer" else r.frames.joinToString(" | ") { "${it.header} ${it.hex()}" }))
            }
            at("ATCRA${m.probe.resp}")
            // 2) UDS (newer modules) and the old KWP requests.
            if (!done && !gmOnly) for (req in listOf("1902FF", "190208", "1800FF00", "170000")) {
                val r = query(req, 2500)
                val uds = r.frames.firstOrNull { it.u(0) == 0x59 }
                if (uds != null) {
                    val list = DtcDecode.fromUds(uds)
                    for ((c, st) in list) faults.add(Fault(c, name, "stored", st, uds.hex()))
                    notes.add("$name: ${list.size} code(s) (UDS $req)")
                    result = ModuleResult(name, ids, list.size, "UDS", m); done = true; break
                }
                tried.add("$req -> " + (if (r.frames.isEmpty()) "no answer" else r.frames.joinToString(" | ") { it.hex() }))
            }
            if (!done) notes.add("$name ($ids) answers but gave no fault list. Tried: ${tried.joinToString("; ")}. First answer: ${m.firstAnswer}")
        } finally {
            try { aimReset(); at("ATST32") } catch (_: Exception) {}
        }
        return ModuleScan(faults, notes, result)
    }

    /** Erases the codes of one non-engine module. Returns a short result text. */
    suspend fun clearModule(m: FoundModule): String {
        try {
            aim(m.probe)
            if (query("14FFFFFF", 4000).frames.any { it.u(0) == 0x54 }) return "${m.name}: cleared"
            if (query("04", 4000).frames.any { it.u(0) == 0x44 }) return "${m.name}: cleared"
            return "${m.name}: refused (needs a special session or security access)"
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


    /** GM transmission values (Mode 22, read only): fluid temperature from the gearbox module and the current gear from the engine module. Null parts = no answer. */
    class GearExtra(val tft: Double?, val gear: Int?, val raw: String)

    suspend fun readGearExtra(): GearExtra {
        var tft: Double? = null
        var gear: Int? = null
        val raw = ArrayList<String>()
        try {
            at("ATST32")
            at("ATSH7E2"); at("ATCRA7EA")
            val a = query("221940", 1500)
            raw.add("221940 -> " + a.frames.joinToString(" | ") { it.hex() }.ifBlank { "no answer" })
            a.frames.firstOrNull { it.u(0) == 0x62 && it.data.size >= 4 && it.u(1) == 0x19 && it.u(2) == 0x40 }?.let { tft = it.u(3) - 40.0 }
            at("ATSH7E0"); at("ATCRA7E8")
            val b = query("22199A", 1500)
            raw.add("22199A -> " + b.frames.joinToString(" | ") { it.hex() }.ifBlank { "no answer" })
            b.frames.firstOrNull { it.u(0) == 0x62 && it.data.size >= 4 && it.u(1) == 0x19 && it.u(2) == 0x9A }?.let { gear = it.u(3) }
        } finally {
            try { aimReset(); at("ATST32") } catch (_: Exception) {}
        }
        return GearExtra(tft, gear, raw.joinToString("; "))
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

    /**
     * Read-only discovery for the Claude Lab: what the car says it supports, the identity of every module that answers, and a
     * few standard identification requests. Nothing here changes anything in the car. Returns "command -> answer" lines
     * (the same lines are also kept in the log).
     */
    suspend fun discover(onProgress: (String) -> Unit): List<String> {
        val out = ArrayList<String>()
        fun clean(s: String) = s.replace(">", "").replace('\r', ' ').replace('\n', ' ').trim().replace(Regex("\\s+"), " ")
        suspend fun ask(cmd: String, to: Long = 2500) { out.add("$cmd -> " + clean(raw(cmd, to))) }
        onProgress("Adapter and protocol")
        for (c in listOf("ATI", "ATDPN", "ATRV")) ask(c, 2000)
        onProgress("Supported standard values")
        for (c in listOf("0100", "0120", "0140", "0160", "0180", "01A0", "01C0")) ask(c, 3000)
        onProgress("Standard information")
        for (c in listOf("0101", "0103", "011C", "0113", "011D", "0900", "0902", "0904", "0906", "0908", "090A", "03", "07", "0A")) ask(c, 3000)
        onProgress("Looking for other modules")
        val mods = findModules { i, n, name -> onProgress("Module $i of $n: $name") }
        out.add("MODULES THAT ANSWERED: " + mods.joinToString("; ") { "${it.probe.req}/${it.probe.resp} first=${it.firstAnswer}" }.ifBlank { "none" })
        for ((i, m) in mods.withIndex()) {
            onProgress("Identifying module ${i + 1} of ${mods.size}")
            try {
                aim(m.probe)
                at("ATSTFF")
                out.add("--- module ${m.probe.req}/${m.probe.resp} (${m.probe.name})")
                for (c in listOf("1A90", "1AB0", "1ABB", "22F190", "22F187", "22F18A", "22F18C", "22F194", "22F1A0")) ask(c, 1200)
                val uudt = Gm.uudtId(m.probe.resp); val a = m.probe.resp.toInt(16); val b = uudt.toInt(16)
                at("ATCF%03X".format(a and b)); at("ATCM%03X".format(0x7FF and (a xor b).inv()))
                for (c in listOf("A98112", "A981FF")) ask(c, 3000)
            } finally {
                try { aimReset(); at("ATST32") } catch (_: Exception) {}
            }
        }
        // GM manufacturer values (Mode 22), read only: transmission fluid temperature from the TCM, oil temperature/pressure/life and current gear from the ECM.
        onProgress("Transmission and engine extra values (GM)")
        try {
            at("ATST32")
            out.add("--- GM extra values, TCM 7E2/7EA")
            at("ATSH7E2"); at("ATCRA7EA")
            for (c in listOf("221940", "22199A", "221941", "221942")) ask(c, 1500)
            out.add("--- GM extra values, ECM 7E0/7E8")
            at("ATSH7E0"); at("ATCRA7E8")
            for (c in listOf("221154", "221470", "22115C", "22119F", "22199A")) ask(c, 1500)
        } finally {
            try { aimReset(); at("ATST32") } catch (_: Exception) {}
        }
        // GM "all modules" (functional) question: id 101, no receive filter, so every module that answers shows up with its own id.
        onProgress("Asking all modules at once (GM)")
        try {
            at("ATFCSM0"); at("ATCRA"); at("ATSH101"); at("ATSTFF")
            out.add("--- broadcast 101 (read only, any module may answer)")
            for (c in listOf("A98112", "A981FF", "A98100", "3E")) ask(c, 3500)
        } finally {
            try { aimReset(); at("ATST32") } catch (_: Exception) {}
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
