package com.example.obdy

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small JSON files for the pro tools (signals, alert rules, watch list, tests, timer results, lifetime totals). */
class ProStore(private val ctx: Context) {
    private fun f(n: String) = File(ctx.filesDir, n)
    private fun <T> readArr(name: String, make: (JSONObject) -> T): List<T> = try {
        val t = f(name)
        if (!t.exists()) emptyList() else { val a = JSONArray(t.readText()); (0 until a.length()).map { make(a.getJSONObject(it)) } }
    } catch (_: Exception) { emptyList() }
    private fun writeArr(name: String, items: List<JSONObject>) { try { val a = JSONArray(); items.forEach { a.put(it) }; f(name).writeText(a.toString()) } catch (_: Exception) {} }

    fun loadSignals(): List<CustomSignal> = readArr("pro_signals.json") {
        CustomSignal(it.getString("name"), it.getString("id"), it.getInt("start"), it.getInt("len"), it.optInt("mask", 0), it.optBoolean("big", true),
            it.optBoolean("signed", false), it.optDouble("scale", 1.0), it.optDouble("offset", 0.0), it.optString("unit", ""))
    }
    fun saveSignals(l: List<CustomSignal>) = writeArr("pro_signals.json", l.map {
        JSONObject().put("name", it.name).put("id", it.id).put("start", it.start).put("len", it.len).put("mask", it.mask).put("big", it.big)
            .put("signed", it.signed).put("scale", it.scale).put("offset", it.offset).put("unit", it.unit)
    })

    fun loadRules(): List<AlertRule> = readArr("pro_rules.json") {
        AlertRule(it.getString("signal"), it.getString("op"), it.getDouble("threshold"), it.getString("message"), it.optBoolean("moving", false))
    }
    fun saveRules(l: List<AlertRule>) = writeArr("pro_rules.json", l.map {
        JSONObject().put("signal", it.signal).put("op", it.op).put("threshold", it.threshold).put("message", it.message).put("moving", it.onlyMoving)
    })

    fun loadWatches(): List<WatchDef> = readArr("pro_watch.json") {
        WatchDef(it.getString("name"), it.getString("req"), it.getString("resp"), it.getString("did"), it.optInt("start", 0), it.optInt("len", 1),
            it.optBoolean("signed", false), it.optBoolean("big", true), it.optDouble("scale", 1.0), it.optDouble("offset", 0.0), it.optString("unit", ""))
    }
    fun saveWatches(l: List<WatchDef>) = writeArr("pro_watch.json", l.map {
        JSONObject().put("name", it.name).put("req", it.req).put("resp", it.resp).put("did", it.did).put("start", it.start).put("len", it.len)
            .put("signed", it.signed).put("big", it.big).put("scale", it.scale).put("offset", it.offset).put("unit", it.unit)
    })

    fun loadTests(): List<ActiveTestDef> = readArr("pro_tests.json") {
        ActiveTestDef(it.getString("id"), it.getString("title"), it.getString("req"), it.getString("resp"), it.getString("start"), it.optString("stop", ""), it.optInt("seconds", 5), it.optString("note", ""))
    }
    fun saveTests(l: List<ActiveTestDef>) = writeArr("pro_tests.json", l.map {
        JSONObject().put("id", it.id).put("title", it.title).put("req", it.req).put("resp", it.resp).put("start", it.start).put("stop", it.stop).put("seconds", it.seconds).put("note", it.note)
    })

    fun loadRuns(): List<PerfRun> = readArr("pro_runs.json") {
        PerfRun(it.getString("kind"), it.getDouble("seconds"), it.getLong("at"), it.optDouble("speed", 0.0), it.optDouble("dist", 0.0))
    }
    fun saveRuns(l: List<PerfRun>) = writeArr("pro_runs.json", l.map {
        JSONObject().put("kind", it.kind).put("seconds", it.seconds).put("at", it.atMs).put("speed", it.speedKmh).put("dist", it.distanceM)
    })

    fun loadLife(): Lifetime = try {
        val t = f("pro_life.json")
        if (!t.exists()) Lifetime() else { val o = JSONObject(t.readText()); Lifetime(o.optDouble("drive", 0.0), o.optDouble("idle", 0.0), o.optDouble("liters", 0.0), o.optDouble("km", 0.0)) }
    } catch (_: Exception) { Lifetime() }
    fun saveLife(l: Lifetime) { try { f("pro_life.json").writeText(JSONObject().put("drive", l.driveSec).put("idle", l.idleSec).put("liters", l.liters).put("km", l.km).toString()) } catch (_: Exception) {} }
}

/**
 * The "pro tools" of the app, kept apart from the main view model: the pin 1 (single-wire) bus, the CAN monitor with the signal finder,
 * the data sweep and watch list, active tests with safety checks, the module inventory and the performance timers.
 * Every tool takes the adapter exclusively and pauses the normal live polling while it works.
 */
class ProTools(private val vm: ObdyVm) {
    private val store = ProStore(vm.appCtx)
    private val scope get() = vm.viewModelScope
    private fun connected(): Boolean { if (vm.conn != Conn.On) { vm.say("Connect to the car first."); return false }; return true }

    // ------------------------------------------------------------------ pin 1 (single-wire) bus
    var busBusy by mutableStateOf(false)
    var busStatus by mutableStateOf("")
    val busLines = mutableStateListOf<String>()
    var swModules by mutableStateOf<List<FoundModule>>(emptyList())
    var swFaults by mutableStateOf<List<Fault>>(emptyList())
    var swResults by mutableStateOf<List<ModuleResult>>(emptyList())

    /** Listens on pin 1, looks for modules there and reads their codes. Read only. */
    fun scanLowSpeed() {
        if (busBusy || !connected()) return
        busBusy = true; busLines.clear(); busStatus = "Switching to the pin 1 bus..."
        vm.livePaused = true
        scope.launch {
            try {
                val cap = Capture(); cap.startMs = System.currentTimeMillis()
                val err: String? = vm.withCar { monitorSession(Bus.Sw, emptyList(), true, 3, { false }) { t -> cap.add(CanParse.parse(t, System.currentTimeMillis())) } }
                cap.endMs = System.currentTimeMillis()
                if (err != null) { busStatus = err; busLines.add(err); return@launch }
                busLines.add("Listened for 3 seconds on pin 1: ${cap.total} frame(s) from ${cap.ids.size} id(s).")
                busStatus = "Looking for modules on pin 1..."
                val found = vm.withCar { findModules(Stn.swProbes()) { i, n, name -> busStatus = "Pin 1 module $i of $n: $name" } } ?: emptyList()
                swModules = found
                val faults = ArrayList<Fault>(); val results = ArrayList<ModuleResult>()
                for ((i, m) in found.withIndex()) {
                    busStatus = "Reading ${m.name} (${i + 1}/${found.size})..."
                    val r = vm.withCar { readModuleFaults(m) } ?: continue
                    faults.addAll(r.faults); results.add(r.result)
                }
                swFaults = faults; swResults = results
                if (faults.isNotEmpty()) vm.faults = (vm.faults.filter { f -> faults.none { it.module == f.module } } + faults).distinctBy { it.key }
                busLines.add(when {
                    found.isNotEmpty() -> "${found.size} module(s) answered on pin 1, ${faults.size} code(s) read."
                    cap.total > 0 -> "Pin 1 is alive (frames seen) but no module answered a diagnostic request. Save a monitor log and send it to the Lab."
                    else -> "Nothing on pin 1. Many 2013 cars carry all their modules on the high-speed bus, which the Deep scan already reads."
                })
                busStatus = busLines.last()
            } catch (e: IllegalStateException) {
                busStatus = e.message ?: "Could not use the pin 1 bus."; busLines.add(busStatus)
            } finally { busBusy = false; vm.livePaused = false }
        }
    }

    // ------------------------------------------------------------------ CAN monitor and signal finder
    var cap by mutableStateOf<Capture?>(null)
    var capTick by mutableStateOf(0)
    var capBusy by mutableStateOf<String?>(null)
    var finderBase by mutableStateOf<Capture?>(null)
    var finderAct by mutableStateOf<Capture?>(null)
    var candidates by mutableStateOf<List<Candidate>>(emptyList())
    private var capCancel = false

    fun parseFilters(text: String): List<String> =
        text.split(Regex("[,; ]+")).map { it.trim().uppercase() }.filter { (it.length == 3 || it.length == 8) && it.all { c -> c.isDigit() || c in 'A'..'F' } }.take(4)

    fun stopCapture() { capCancel = true }

    /** slot: "main" (monitor), "base" (quiet capture) or "act" (capture while you do the thing). */
    fun capture(bus: Bus, filterText: String, seconds: Int, wake: Boolean, slot: String) {
        if (capBusy != null || !connected()) return
        val filters = parseFilters(filterText)
        val c = Capture(); c.startMs = System.currentTimeMillis(); c.endMs = c.startMs
        when (slot) { "main" -> cap = c; "base" -> { finderBase = c; finderAct = null; candidates = emptyList() }; else -> finderAct = c }
        capCancel = false; capBusy = slot; vm.livePaused = true
        scope.launch {
            try {
                val err: String? = vm.withCar {
                    monitorSession(bus, filters, wake, seconds, { capCancel }) { t ->
                        val now = System.currentTimeMillis()
                        c.add(CanParse.parse(t, now)); c.overflow += CanParse.overflows(t); c.endMs = now; capTick++
                    }
                }
                c.endMs = System.currentTimeMillis()
                if (err != null) vm.say(err) else {
                    val secs = ((c.endMs - c.startMs) / 1000.0).coerceAtLeast(0.1)
                    val n = c.ids.values.sumOf { it.count }
                    val thin = if (c.overflow > 0 || (filters.isEmpty() && bus == Bus.Hs && n / secs < 400))
                        " The adapter could not pass every frame to the phone. Filter on one to four ids for a complete recording." else ""
                    vm.say("$n frames, ${c.ids.size} ids, %.0f frames per second.$thin".format(n / secs))
                }
                val base = finderBase
                if (slot == "act" && base != null) {
                    candidates = SignalFinder.find(base, c)
                    vm.say(if (candidates.isEmpty()) "No byte changed. Try again and do the action for the whole recording." else "${candidates.size} candidate byte(s) found.")
                }
            } finally { capBusy = null; vm.livePaused = false; capTick++ }
        }
    }

    fun shareCapture(c: Capture) {
        val dir = File(vm.appCtx.filesDir, "reports").also { it.mkdirs() }
        val f = File(dir, "canlog_" + System.currentTimeMillis() + ".csv")
        f.writeText(c.csv())
        vm.shareFile(f, "text/csv")
    }

    // ------------------------------------------------------------------ named signals, virtual dashboard, alerts
    var signals by mutableStateOf(store.loadSignals())
    var rules by mutableStateOf(store.loadRules())
    val sigValues = mutableStateMapOf<String, Double>()
    var sigRunning by mutableStateOf(false)
    var sigAlert by mutableStateOf<String?>(null)
    var sigNote by mutableStateOf("")
    private var sigCancel = false
    private var lastBeep = 0L

    fun addSignal(s: CustomSignal) { signals = signals.filter { it.name != s.name } + s; store.saveSignals(signals) }
    fun deleteSignal(name: String) { signals = signals.filter { it.name != name }; rules = rules.filter { it.signal != name }; store.saveSignals(signals); store.saveRules(rules) }
    fun addRule(r: AlertRule) { rules = rules + r; store.saveRules(rules) }
    fun deleteRule(r: AlertRule) { rules = rules.filter { it !== r }; store.saveRules(rules) }

    fun startSignals(bus: Bus) {
        if (sigRunning || capBusy != null || !connected()) return
        if (signals.isEmpty()) { vm.say("Save a signal first (Bus monitor > Signal finder)."); return }
        val ids = signals.map { it.id }.distinct()
        val filters = if (ids.size <= 4) ids else emptyList()
        sigNote = if (ids.size > 4) "More than 4 ids: listening to everything, so some frames may be missed." else ""
        sigCancel = false; sigRunning = true; vm.livePaused = true
        scope.launch {
            try {
                val err: String? = vm.withCar {
                    monitorSession(bus, filters, false, 0, { sigCancel }) { t ->
                        for (f in CanParse.parse(t, System.currentTimeMillis())) {
                            for (s in signals) if (s.id == f.id) s.decode(f.data)?.let { sigValues[s.name] = it }
                        }
                        evaluateRules()
                    }
                }
                if (err != null) { vm.say(err); sigNote = err }
            } finally { sigRunning = false; vm.livePaused = false }
        }
    }

    fun stopSignals() { sigCancel = true }

    private fun evaluateRules() {
        val speed = sigValues["Speed"] ?: vm.liveValues[0x0D]
        val hit = rules.firstOrNull { it.fires(sigValues[it.signal], speed) }
        sigAlert = hit?.message
        val now = System.currentTimeMillis()
        if (hit != null && now - lastBeep > 15_000) {
            lastBeep = now
            try {
                val vib = vm.appCtx.getSystemService(android.os.Vibrator::class.java)
                vib?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
                android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 90).startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 400)
            } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------------------------ data sweep (read only) and watch list
    var sweepBusy by mutableStateOf(false)
    var sweepModule by mutableStateOf<FoundModule?>(null)
    var sweepRange by mutableStateOf<SweepRange?>(null)
    val sweepHits = mutableStateListOf<DidHit>()
    var sweepNext by mutableStateOf(-1)
    var sweepAt by mutableStateOf(0)
    var sweepNote by mutableStateOf("")
    private var sweepCancel = false

    fun startSweep(m: FoundModule, r: SweepRange) {
        if (sweepBusy || !connected()) return
        sweepModule = m; sweepRange = r; sweepHits.clear(); sweepNext = -1
        runSweep(m, r.from, r.to)
    }

    fun continueSweep() {
        val m = sweepModule ?: return; val r = sweepRange ?: return
        if (sweepBusy || sweepNext !in r.from..r.to || !connected()) return
        runSweep(m, sweepNext, r.to)
    }

    fun stopSweep() { sweepCancel = true }

    // ---- one button: every module, every range, every answer into the log
    /** Identifiers read in small slices between live samples while driving (req, resp, did). */
    var recordList by mutableStateOf(run {
        val saved = vm.store.recordIds
        val src = if (saved.isBlank()) EcmDids.LIST.split(',').filter { it.length == 4 }.joinToString(";") { "${EcmDids.REQ},${EcmDids.RESP},$it" } else saved
        src.split(';').mapNotNull { e -> e.split(',').takeIf { it.size == 3 }?.let { Triple(it[0], it[1], it[2]) } }
    })
    fun clearRecordList() { recordList = emptyList(); vm.store.recordIds = "-"; }
    private fun saveRecordList() { vm.store.recordIds = recordList.joinToString(";") { "${it.first},${it.second},${it.third}" } }
    var sweepStep by mutableStateOf("")
    var sweepFrac by mutableStateOf(0f)
    private val hitModules = HashMap<DidHit, FoundModule>()
    fun hitModule(h: DidHit): FoundModule? = hitModules[h]

    /** Where a stopped scan can go on: module number, range number, next identifier. */
    var sweepResume by mutableStateOf<Triple<Int, Int, Int>?>(null)
    val sweepResumeText: String get() = sweepResume?.let { r ->
        val m = allModules().getOrNull(r.first); val rg = DidSweep.presets.getOrNull(r.second)
        if (m == null || rg == null) "" else "${m.name} · %04X".format(r.third)
    } ?: ""

    /** Read only (service 22). Needs the car stopped. Every identifier that answers is written to the log as [SWEEP-HIT]. [resume] goes on where it was stopped. */
    fun startFullSweep(resume: Boolean = false) {
        if (sweepBusy || !connected()) return
        if ((vm.liveValues[0x0D] ?: 0.0) > 1.0) { sweepNote = "The car must be stopped (park, handbrake)."; return }
        val mods = allModules()
        if (mods.isEmpty()) { sweepNote = "Run a Deep scan first so Obdy knows the modules."; return }
        val ranges = DidSweep.presets
        val perModule = ranges.sumOf { it.to - it.from + 1 }
        val total = (mods.size * perModule).toFloat()
        val from0 = if (resume) sweepResume else null
        val startM = from0?.first?.takeIf { it < mods.size } ?: 0
        val startR = if (from0 != null && startM == from0.first) from0.second.coerceIn(0, ranges.size - 1) else 0
        val startD = if (from0 != null && startM == from0.first) from0.third else ranges[0].from
        var done = startM * perModule + ranges.take(startR).sumOf { it.to - it.from + 1 } + (if (from0 != null) (startD - ranges[startR].from).coerceAtLeast(0) else 0)
        sweepBusy = true; sweepCancel = false; sweepNote = ""; sweepResume = null; sweepFrac = (done / total).coerceIn(0f, 1f); vm.livePaused = true
        if (from0 == null) { sweepHits.clear(); hitModules.clear() }
        scope.launch {
            AppLog.i("[SWEEP ALL] ${if (from0 != null) "continue" else "start"}: ${mods.size} module(s), ${ranges.size} ranges each", keep = true)
            try {
                for (mi in startM until mods.size) {
                    val m = mods[mi]
                    var found = 0; var why = ""
                    for ((ri, r) in ranges.withIndex()) {
                        if (mi == startM && ri < startR) continue
                        if (sweepCancel) break
                        val first = if (mi == startM && ri == startR && from0 != null) startD else r.from
                        if (first > r.to) continue
                        sweepStep = "${m.name} (${mi + 1}/${mods.size}) · ${r.label}"
                        val base = done - (first - r.from)
                        val end = vm.withCar {
                            sweepDids(m, first, r.to, { sweepCancel },
                                { h -> found++; sweepHits.add(h); hitModules[h] = m; AppLog.i("[SWEEP-HIT] ${m.name} ${m.probe.req}/${m.probe.resp} 22 ${h.code} ${h.state.label} ${h.hex()}", keep = true) },
                                { d -> sweepAt = d; sweepFrac = ((base + (d - r.from)) / total).coerceIn(0f, 1f) })
                        }
                        if (end == null) { sweepResume = Triple(mi, ri, first); why = "no answer from the adapter"; sweepCancel = true; break }
                        if (sweepCancel) { sweepResume = Triple(mi, ri, end.next.coerceAtMost(r.to + 1).let { if (it > r.to) r.to else it }); break }
                        done = base + (r.to - r.from + 1)
                        sweepFrac = (done / total).coerceIn(0f, 1f)
                        if (end.note != null && end.next <= r.to) {
                            why = end.note
                            done += ranges.drop(ri + 1).sumOf { it.to - it.from + 1 }      // the rest of this module is skipped
                            break
                        }
                    }
                    AppLog.i("[SWEEP ALL] ${m.name} ${m.probe.req}/${m.probe.resp}: $found identifier(s) answered" + (if (why.isNotBlank()) " ($why)" else ""), keep = true)
                    if (sweepCancel) break
                }
                val found = hitModules.entries.filter { it.key.state == DidState.Answers && it.key.payload.isNotEmpty() }
                    .map { Triple(it.value.probe.req, it.value.probe.resp, it.key.code) }.distinct().take(450)
                if (found.isNotEmpty()) { recordList = found; saveRecordList() }
                sweepNote = if (sweepCancel) "Stopped. ${sweepHits.size} found so far, all in the log. You can continue." else "Finished: ${sweepHits.size} identifier(s) found, all in the log. Share the full log."
                AppLog.i("[SWEEP ALL] ${if (sweepCancel) "stopped" else "finished"}: ${sweepHits.size} identifier(s) in total", keep = true)
            } finally { sweepBusy = false; sweepStep = ""; vm.livePaused = false }
        }
    }

    fun shareSweep() {
        if (sweepHits.isEmpty()) return
        val dir = File(vm.appCtx.filesDir, "reports").also { it.mkdirs() }
        val f = File(dir, "sweep_" + System.currentTimeMillis() + ".csv")
        f.writeText("module;request;answer id;did;state;bytes\n" + sweepHits.toList().joinToString("\n") { h ->
            val m = hitModules[h]; "${m?.name ?: sweepModule?.name ?: ""};${m?.probe?.req ?: ""};${m?.probe?.resp ?: ""};${h.code};${h.state.label};${h.hex()}"
        })
        vm.shareFile(f, "text/csv")
    }

    var watches by mutableStateOf(store.loadWatches())
    val watchValues = mutableStateMapOf<String, Double>()
    val watchRaw = mutableStateMapOf<String, String>()
    var watchRunning by mutableStateOf(false)
    private var watchCancel = false

    fun addWatch(w: WatchDef) { watches = watches.filter { it.name != w.name } + w; store.saveWatches(watches) }
    fun deleteWatch(name: String) { watches = watches.filter { it.name != name }; store.saveWatches(watches); watchValues.remove(name); watchRaw.remove(name) }
    fun stopWatch() { watchCancel = true }

    fun startWatch() {
        if (watchRunning || capBusy != null || !connected()) return
        if (watches.isEmpty()) { vm.say("Add a value to watch first."); return }
        watchCancel = false; watchRunning = true; vm.livePaused = true
        scope.launch {
            try {
                while (!watchCancel && vm.conn == Conn.On) {
                    val list = watches
                    // one lock per round: the header changes made for a module are undone before anything else uses the adapter
                    vm.withCar {
                        try {
                            for (w in list) {
                                val d = readWatch(w)
                                if (d != null) { watchRaw[w.name] = d.joinToString(" ") { "%02X".format(it) }; w.decode(d)?.let { watchValues[w.name] = it } }
                                else { watchValues.remove(w.name); watchRaw.remove(w.name) }
                            }
                        } finally { resetBus() }
                    } ?: break
                    delay(250)
                }
            } finally { watchRunning = false; vm.livePaused = false }
        }
    }

    // ------------------------------------------------------------------ active tests (safe wrapper)
    var tests by mutableStateOf(store.loadTests())
    var testBusy by mutableStateOf<String?>(null)
    val testLog = mutableStateListOf<String>()
    private var testCancel = false

    fun addTest(t: ActiveTestDef) { tests = tests.filter { it.id != t.id } + t; store.saveTests(tests) }
    fun deleteTest(id: String) { tests = tests.filter { it.id != id }; store.saveTests(tests) }
    fun stopTest() { testCancel = true }

    fun runTest(def: ActiveTestDef) {
        if (testBusy != null || !connected()) return
        testBusy = def.id; testCancel = false; testLog.clear(); vm.livePaused = true
        scope.launch {
            try {
                val speed = vm.withCar { readPid(Pids.byId(0x0D)!!) }
                val rpm = vm.withCar { readPid(Pids.byId(0x0C)!!) }
                val volts = vm.withCar { voltage() }
                val why = ActiveGuard.refuse(def, speed, rpm, volts)
                if (why != null) { testLog.add("Not run: $why"); return@launch }
                testLog.add("Running \"${def.title}\" for ${def.seconds} s (press Stop to end it early).")
                val lines = vm.withCar { runActiveTest(def) { testCancel } } ?: emptyList()
                testLog.addAll(lines)
                testLog.add("Finished. The stop command was sent so the module has control again.")
            } finally { testBusy = null; vm.livePaused = false }
        }
    }

    // ------------------------------------------------------------------ module inventory
    var invBusy by mutableStateOf(false)
    var invProgress by mutableStateOf("")
    var inv by mutableStateOf<Map<String, ModuleInfo>>(emptyMap())

    fun allModules(): List<FoundModule> = vm.foundModulesList() + swModules
    fun keyOf(m: FoundModule) = "${m.probe.bus.name}/${m.probe.resp}"

    fun readInventory() {
        if (invBusy || !connected()) return
        val mods = allModules()
        if (mods.isEmpty()) { vm.say("Run a Deep scan first so Obdy knows the modules."); return }
        invBusy = true; vm.livePaused = true
        scope.launch {
            try {
                for ((i, m) in mods.withIndex()) {
                    invProgress = "Reading ${m.name} (${i + 1}/${mods.size})..."
                    val info = vm.withCar { readModuleInfo(m) } ?: continue
                    inv = inv + (keyOf(m) to info)
                    if (m.probe.bus == Bus.Hs) vm.moduleInfo = vm.moduleInfo + (m.probe.resp to info)
                }
            } catch (e: IllegalStateException) {
                vm.say(e.message ?: "Could not read the modules.")
            } finally { invBusy = false; invProgress = ""; vm.livePaused = false }
        }
    }

    /** The VIN a module reports for itself (17 characters) or null. */
    fun moduleVin(info: ModuleInfo?): String? =
        info?.items?.firstOrNull { (k, v) -> (k == "VIN stored in module" || k == "VIN (UDS)") && v.trim().length == 17 }?.second?.trim()

    fun vinMismatch(m: FoundModule): Boolean = Inventory.vinMismatch(vm.car.vin, moduleVin(inv[keyOf(m)]))

    fun inventoryText(): String {
        val sb = StringBuilder("Module inventory - ${vm.car.name}  VIN ${vm.car.vin.ifBlank { "unknown" }}\n")
        for (m in allModules()) {
            sb.append("\n${m.name}  [${m.probe.bus.name} ${m.probe.req}/${m.probe.resp}]")
            if (vinMismatch(m)) sb.append("  <-- VIN DIFFERS FROM THE CAR (module may come from another car)")
            sb.append('\n')
            val info = inv[keyOf(m)]
            if (info == null) sb.append("  (not read)\n") else for ((k, v) in info.items) sb.append("  $k: $v\n")
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------ performance timers
    val timer = PerfTimer { run -> perfRuns = (listOf(run) + perfRuns).take(80); store.saveRuns(perfRuns); vm.say("${run.kind}: ${Calc.fmt(run.seconds, 2)} s") }
    var perfRunning by mutableStateOf(false)
    var perfSpeed by mutableStateOf(0.0)
    var perfElapsed by mutableStateOf(0.0)
    var perfHint by mutableStateOf(timer.hint)
    var perfHz by mutableStateOf(0.0)
    var perfRuns by mutableStateOf(store.loadRuns())

    fun startPerf() {
        if (perfRunning || capBusy != null || !connected()) return
        perfRunning = true; timer.reset(); vm.livePaused = true
        scope.launch {
            try {
                val t0 = System.nanoTime()
                var n = 0; var tHz = t0
                while (perfRunning && vm.conn == Conn.On) {
                    val m = vm.withCar { readPerf() }
                    if (m == null) { delay(150); continue }
                    val kmh = m[0x0D] ?: continue
                    if (kmh >= 255.0) continue                       // "no signal"
                    val now = System.nanoTime()
                    timer.add((now - t0) / 1e9, kmh)
                    perfSpeed = kmh; perfElapsed = timer.elapsed; perfHint = timer.hint
                    n++
                    if (now - tHz >= 2_000_000_000L) { perfHz = n * 1e9 / (now - tHz); n = 0; tHz = now }
                }
            } finally { perfRunning = false; vm.livePaused = false }
        }
    }

    fun stopPerf() { perfRunning = false }
    fun clearRuns() { perfRuns = emptyList(); store.saveRuns(perfRuns) }

    fun sharePerf() {
        val dir = File(vm.appCtx.filesDir, "reports").also { it.mkdirs() }
        val f = File(dir, "performance_" + System.currentTimeMillis() + ".csv")
        f.writeText("time;test;seconds;distance_m\n" + perfRuns.joinToString("\n") {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(it.atMs)) + ";${it.kind};${Calc.fmt(it.seconds, 2)};${Calc.fmt(it.distanceM, 1)}"
        })
        vm.shareFile(f, "text/csv")
    }

    // ------------------------------------------------------------------ lever position as the gearbox computer sees it (value 194F found by the gearbox lab)
    var leverRunning by mutableStateOf(false)
    var leverNow by mutableStateOf<Int?>(null)
    var leverRange by mutableStateOf<Int?>(null)
    val leverTrail = mutableStateListOf<String>()
    private val leverDef = WatchDef("Lever", "7E2", "7EA", "194F")
    private val leverRangeDef = WatchDef("Range switch", "7E2", "7EA", "1951")

    fun startLever() {
        if (leverRunning) return
        if (!connected()) { vm.say("Connect to the car first."); return }
        leverRunning = true; vm.livePaused = true; leverTrail.clear(); leverNow = null; leverRange = null
        scope.launch {
            try {
                var last: Int? = null
                var misses = 0
                val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                while (leverRunning && vm.conn == Conn.On) {
                    val g = vm.withCar { readWatch(leverDef) }?.firstOrNull()
                    if (g == null) {
                        if (++misses > 6) { vm.say("The gearbox computer is not answering."); break }
                        delay(300); continue
                    }
                    misses = 0
                    leverNow = g
                    if (g != last) {
                        if (last == 0x04 && g == 0x08) leverTrail.add(0, "Neutral went straight to Manual: the gearbox computer never saw Drive between them")
                        leverTrail.add(0, fmt.format(java.util.Date()) + "  " + Gm.leverName(g))
                        last = g
                    }
                    leverRange = vm.withCar { readWatch(leverRangeDef) }?.firstOrNull()
                    delay(100)
                }
            } catch (e: Exception) {
                vm.say("Lever watch stopped: " + (e.message ?: "adapter error"))
            } finally {
                leverRunning = false
                // Give the adapter back: readWatch leaves ATSH7E2 + ATCRA7EA set, which hides the engine computer from every later request (log 2026-11-19 07:51).
                try { vm.withCar { resetBus() } } catch (_: Exception) {}
                vm.livePaused = false
            }
        }
    }

    fun stopLever() { leverRunning = false }

    // ------------------------------------------------------------------ network watch (which module drops out, with the voltage)
    class NetEvent(val atMs: Long, val text: String)
    var netRunning by mutableStateOf(false)
    var netRounds by mutableStateOf(0)
    var netVolt by mutableStateOf<Double?>(null)
    var netMinVolt by mutableStateOf<Double?>(null)
    var netMaxVolt by mutableStateOf<Double?>(null)
    var netStartMs by mutableStateOf(0L)
    val netState = mutableStateMapOf<String, Boolean>()
    val netEvents = mutableStateListOf<NetEvent>()

    fun startNetWatch() {
        if (netRunning || capBusy != null || !connected()) return
        val mods = vm.foundModulesList().map { it.probe }.filter { it.bus == Bus.Hs }
        if (mods.isEmpty()) { vm.say("Run a Deep scan first so Obdy knows the modules."); return }
        netRunning = true; vm.livePaused = true
        netRounds = 0; netEvents.clear(); netState.clear(); netMinVolt = null; netMaxVolt = null; netStartMs = System.currentTimeMillis()
        scope.launch {
            try {
                var lastLow = false
                while (netRunning && vm.conn == Conn.On) {
                    val res = vm.withCar { pingModules(mods) }
                    if (res == null) { delay(400); continue }
                    val (v, round) = res
                    val now = System.currentTimeMillis()
                    netRounds++
                    if (v != null) {
                        netVolt = v
                        netMinVolt = minOf(netMinVolt ?: v, v); netMaxVolt = maxOf(netMaxVolt ?: v, v)
                        val low = v < 11.8
                        if (low != lastLow) { netEvents.add(0, NetEvent(now, if (low) "Battery voltage fell below 11.8 V (%.1f V)".format(v) else "Battery voltage back above 11.8 V (%.1f V)".format(v))); lastLow = low }
                    }
                    val vt = if (v != null) " (%.1f V)".format(v) else ""
                    for ((name, alive) in round) {
                        val before = netState[name]
                        netState[name] = alive
                        if (before != null && before != alive) netEvents.add(0, NetEvent(now, if (alive) "$name answers again$vt" else "$name stopped answering$vt"))
                    }
                    if (round.values.none { it }) netEvents.add(0, NetEvent(now, "No module answered at all$vt (adapter link, ignition or network down)"))
                    delay(400)
                }
            } catch (e: Exception) {
                vm.say("Network watch stopped: " + (e.message ?: "adapter error"))
            } finally { netRunning = false; vm.livePaused = false }
        }
    }

    fun stopNetWatch() { netRunning = false }

    fun shareNetWatch() {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val sb = StringBuilder()
        sb.append("Network watch\n")
        sb.append("Started ${fmt.format(java.util.Date(netStartMs))}, $netRounds rounds\n")
        sb.append("Battery at the adapter: min ${netMinVolt?.let { "%.1f".format(it) } ?: "?"} V, max ${netMaxVolt?.let { "%.1f".format(it) } ?: "?"} V\n\nModules now:\n")
        for ((n, a) in netState) sb.append("  ${if (a) "answers" else "SILENT"}  $n\n")
        sb.append("\nEvents (newest first):\n")
        if (netEvents.isEmpty()) sb.append("  none: every module answered every round\n")
        for (e in netEvents) sb.append("  ${fmt.format(java.util.Date(e.atMs))}  ${e.text}\n")
        val dir = File(vm.appCtx.filesDir, "reports").also { it.mkdirs() }
        val f = File(dir, "network_watch_" + System.currentTimeMillis() + ".txt")
        f.writeText(sb.toString())
        vm.shareFile(f, "text/plain")
    }

    // ------------------------------------------------------------------ driving and idling totals (kept for the life of the app)
    var life = store.loadLife()
    var lifeTick by mutableStateOf(0)
    private var lifeSaved = 0L

    /** Called by the live loop after every cycle. */
    fun tick(dt: Double) {
        val kmh = vm.liveValues[0x0D] ?: 0.0
        val rpm = vm.liveValues[0x0C]
        val maf = vm.liveValues[0x10]
        val lph = when {
            maf != null -> Calc.fuelLitersPerHour(maf, vm.liveValues[0x44] ?: 1.0) * vm.fuelCal
            vm.liveValues[0x5E] != null -> vm.liveValues[0x5E] ?: 0.0
            else -> 0.0
        }
        life.add(dt, kmh, rpm, lph)
        val now = System.currentTimeMillis()
        if (now - lifeSaved > 30_000) { lifeSaved = now; store.saveLife(life); lifeTick++ }
    }

    fun resetLife() { life = Lifetime(); store.saveLife(life); lifeTick++ }
}
