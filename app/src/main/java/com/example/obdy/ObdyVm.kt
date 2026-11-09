package com.example.obdy

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.obdy.ui.Brand
import com.example.obdy.ui.Palettes
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

enum class Conn { Off, Connecting, On }

class ObdyVm(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication<Application>()
    val store = Store(app)

    // ------------------------------------------------------------------ look, navigation, messages
    init { Brand.palette = Palettes.byKey(store.themeKey) }
    var themeKey by mutableStateOf(store.themeKey)
    fun applyTheme(key: String) { themeKey = key; store.themeKey = key; Brand.palette = Palettes.byKey(key) }

    var tab by mutableStateOf(0)
    var overlay by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    fun say(t: String) { toast = t }

    // ------------------------------------------------------------------ car profile and settings
    var car by mutableStateOf(store.loadCar())
    fun updateCar(c: Car) { car = c; store.saveCar(c) }
    var symptoms by mutableStateOf(store.symptoms)
    fun saveSymptoms(s: String) { symptoms = s; store.symptoms = s }
    // ---- AI providers
    var providers by mutableStateOf(store.loadProviders())
    var activeProviderId by mutableStateOf(store.activeProvider)
    val activeProvider: AiProvider? get() = providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()
    val aiTest = mutableStateMapOf<String, String>()
    var aiWeb by mutableStateOf(false)

    fun saveProvider(p: AiProvider) {
        providers = if (providers.any { it.id == p.id }) providers.map { if (it.id == p.id) p else it } else providers + p
        store.saveProviders(providers)
        if (activeProvider == null || providers.size == 1) setActiveProvider(p.id)
        aiTest.remove(p.id)
    }

    fun deleteProvider(id: String) { providers = providers.filter { it.id != id }; store.saveProviders(providers); aiTest.remove(id) }
    fun setActiveProvider(id: String) { activeProviderId = id; store.activeProvider = id }

    /** Sends one tiny question to prove the key, model and connection work. */
    fun testProvider(p: AiProvider) {
        aiTest[p.id] = "Testing..."
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            val r = withContext(Dispatchers.IO) { Ai.test(p) }
            val ms = System.currentTimeMillis() - t0
            aiTest[p.id] = if (r.text != null) "Works (${ms} ms): ${r.text.take(40)}" else "Failed: ${r.error}"
        }
    }
    var wifiHost by mutableStateOf(store.wifiHost)
    var wifiPort by mutableStateOf(store.wifiPort.toString())
    fun saveWifi(h: String, p: String) { wifiHost = h.trim(); wifiPort = p.trim(); store.wifiHost = h; store.wifiPort = p.trim().toIntOrNull() ?: 35000 }
    var autoConnect by mutableStateOf(store.autoConnect)
    fun changeAutoConnect(v: Boolean) { autoConnect = v; store.autoConnect = v }
    var fastMode by mutableStateOf(store.fastMode)
    fun changeFast(v: Boolean) { fastMode = v; store.fastMode = v; fastBroken = false; fastFails = 0 }
    var autoReconnect by mutableStateOf(store.autoReconnect)
    fun changeReconnect(v: Boolean) { autoReconnect = v; store.autoReconnect = v }
    var recordRoute by mutableStateOf(store.recordRoute)
    fun changeRoute(v: Boolean) { recordRoute = v; store.recordRoute = v; if (v && !RouteRecorder(ctx).hasPermission()) askLocation?.invoke() }
    var askLocation: (() -> Unit)? = null
    var keepScreenOn by mutableStateOf(store.keepScreenOn)
    fun changeKeepScreen(v: Boolean) { keepScreenOn = v; store.keepScreenOn = v }

    // ------------------------------------------------------------------ connection
    var conn by mutableStateOf(Conn.Off)
    var connStatus by mutableStateOf("Not connected")
    var adapter by mutableStateOf<AdapterInfo?>(null)
    var supported by mutableStateOf<Set<Int>>(emptySet())
    var voltage by mutableStateOf<Double?>(null)
    var btPermission by mutableStateOf(false)
    private var client: ElmClient? = null
    private var connectJob: Job? = null

    fun refreshPermission() {
        btPermission = if (Build.VERSION.SDK_INT >= 31)
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<BluetoothDevice> = try {
        BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList()?.sortedBy { d ->
            val n = (d.name ?: "").uppercase()
            if (listOf("OBD", "ELM", "VGATE", "VLINK", "V-LINK", "CAN", "KONNWEI", "IOS").any { n.contains(it) }) 0 else 1
        } ?: emptyList()
    } catch (e: SecurityException) { emptyList() }

    private var lastReconnect: (() -> Unit)? = null
    private var reconnectEnabled = false
    private var reconnectJob: Job? = null
    var reconnecting by mutableStateOf(false)
    private var fastBroken = false
    private var fastFails = 0

    fun connectClassic(d: BluetoothDevice) { lastReconnect = { connectClassic(d) }; connect(ClassicTransport(d), "classic", d.address) }
    fun connectBle(d: BluetoothDevice) { lastReconnect = { connectBle(d) }; connect(BleTransport(ctx, d), "ble", d.address) }
    fun usbDrivers(): List<UsbSerialDriver> = UsbHost.list(ctx)
    fun connectUsb(d: UsbSerialDriver) { lastReconnect = { connectUsb(d) }; connect(UsbTransport(ctx, d), "usb", "") }
    fun connectWifi() { lastReconnect = { connectWifi() }; connect(WifiTransport(ctx, wifiHost, wifiPort.toIntOrNull() ?: 35000), "wifi", "") }

    @Suppress("DEPRECATION")
    fun autoConnectNow() {
        if (conn != Conn.Off || !autoConnect) return
        refreshPermission()
        when (store.lastKind) {
            "wifi" -> connectWifi()
            "usb" -> UsbHost.list(ctx).firstOrNull()?.let { connectUsb(it) }
            "classic", "ble" -> {
                if (!btPermission) return
                val d = pairedDevices().firstOrNull { it.address == store.lastAddress }
                    ?: try { BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(store.lastAddress) } catch (e: Exception) { null }
                if (d != null) { if (store.lastKind == "ble") connectBle(d) else connectClassic(d) }
            }
        }
    }

    private fun connect(t: Transport, kind: String, address: String) {
        if (conn == Conn.Connecting) return
        disconnect()
        reconnectEnabled = true
        conn = Conn.Connecting
        connStatus = "Connecting to ${t.label}..."
        connectJob = viewModelScope.launch {
            try {
                t.open()
                val c = ElmClient(t)
                connStatus = "Adapter found. Talking to the car..."
                val info = c.exclusive { initialize() }
                client = c; adapter = info; voltage = info.voltage
                dead.clear(); fastFails = 0; fastBroken = false
                supported = c.exclusive { supportedPids() }
                val vin = c.exclusive { readVin() }
                if (vin != null && vin != car.vin) {
                    val y = Vin.modelYear(vin)
                    updateCar(car.copy(vin = vin, year = if (y != null && y in 2000..2035) y else car.year))
                }
                store.lastKind = kind; store.lastAddress = address
                conn = Conn.On
                connStatus = "Connected: ${t.label}"
                say("Connected. ${supported.size} live values available.")
                if (liveReasons.isNotEmpty()) startLive()
            } catch (e: CancellationException) {
                t.close(); throw e
            } catch (e: Exception) {
                try { t.close() } catch (_: Exception) {}
                client = null; conn = Conn.Off
                connStatus = friendly(e)
                AppLog.i("connect failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun friendly(e: Exception): String = when {
        e is SecurityException -> "Bluetooth permission is missing. Allow it and try again."
        e is IllegalStateException -> e.message ?: "Could not talk to the car."
        e is IOException && (e.message ?: "").contains("read failed", true) -> "Could not open the Bluetooth link. Is the adapter paired (PIN 1234 or 0000) and plugged in, and is the ignition on?"
        e is IOException -> "Connection problem: ${e.message}"
        else -> "${e.javaClass.simpleName}: ${e.message}"
    }

    fun disconnect() {
        connectJob?.cancel()
        liveJob?.cancel(); liveJob = null; liveRunning = false
        try { client?.close() } catch (_: Exception) {}
        client = null
        if (conn != Conn.Off) { conn = Conn.Off; connStatus = "Not connected" }
        adapter = null
        stopRecordingInternal(save = true)
        tripAcc?.let { finishTrip() }
    }

    private fun linkLost(e: Exception) {
        AppLog.i("link lost: ${e.javaClass.simpleName}: ${e.message}")
        val msg = "Connection lost: ${e.message ?: e.javaClass.simpleName}"
        disconnect()
        connStatus = msg
        say("Connection to the adapter was lost.")
        if (autoReconnect && reconnectEnabled && lastReconnect != null) startReconnect()
    }

    /** The Disconnect button: stops everything and does not try again by itself. */
    fun userDisconnect() { reconnectEnabled = false; reconnectJob?.cancel(); reconnecting = false; disconnect() }

    private fun startReconnect() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            reconnecting = true
            try {
                for (i in 1..4) {
                    delay(3000)
                    if (conn != Conn.Off || !reconnectEnabled) return@launch
                    connStatus = "Connection lost. Reconnecting ($i of 4)..."
                    lastReconnect?.invoke()
                    while (conn == Conn.Connecting) delay(300)
                    if (conn == Conn.On) { say("Reconnected."); return@launch }
                }
                connStatus = "Connection lost. Could not reconnect."
            } finally { reconnecting = false }
        }
    }

    private suspend fun <T> withCar(block: suspend ElmClient.() -> T): T? {
        val c = client ?: run { say("Connect to the car first."); return null }
        return try { c.exclusive(block) } catch (e: CancellationException) { throw e } catch (e: IOException) { linkLost(e); null }
    }

    fun refreshVoltage() { viewModelScope.launch { withCar { voltage() }?.let { voltage = it } } }

    // ------------------------------------------------------------------ live data, trip, recording
    val liveValues = mutableStateMapOf<Int, Double>()
    val extValues = mutableStateMapOf<String, Double>()
    var liveSel by mutableStateOf(store.livePids)
    var showExt by mutableStateOf(false)
    var liveRunning by mutableStateOf(false)
    private var liveJob: Job? = null
    private val liveReasons = HashSet<String>()
    private val dead = HashMap<Int, Int>()

    fun toggleLive(id: Int) {
        liveSel = if (id in liveSel) liveSel - id else liveSel + id
        store.livePids = liveSel
    }

    fun wantLive(reason: String, on: Boolean) {
        if (on) liveReasons.add(reason) else liveReasons.remove(reason)
        if (liveReasons.isEmpty()) stopLive() else if (conn == Conn.On) startLive()
    }

    private fun stopLive() { liveJob?.cancel(); liveJob = null; liveRunning = false }

    private fun startLive() {
        val c = client ?: return
        if (liveJob?.isActive == true) return
        liveRunning = true
        liveJob = viewModelScope.launch {
            try {
                var last = System.nanoTime()
                val t0 = last
                var cycle = 0
                while (currentCoroutineContext().isActive) {
                    val ids = pollIds()
                    if (ids.isEmpty()) { delay(400); continue }
                    suspend fun single(p: Pid) {
                        val v = c.exclusive { readPid(p) }
                        if (v != null) { liveValues[p.id] = v; dead[p.id] = 0 } else dead[p.id] = (dead[p.id] ?: 0) + 1
                    }
                    val pids = ids.mapNotNull { Pids.byId(it) }
                    if (fastMode && !fastBroken && pids.size > 1) {
                        for (chunk in pids.chunked(4)) {
                            val m = if (chunk.size > 1) c.exclusive { readPids(chunk) } else null
                            if (m != null && m.size == chunk.size) {
                                for ((id, value) in m) { liveValues[id] = value; dead[id] = 0 }
                            } else {
                                if (chunk.size > 1) { fastFails++; if (fastFails >= 3) { fastBroken = true; say("This adapter does not support fast mode. Reading one value at a time.") } }
                                for (p in chunk) single(p)
                            }
                        }
                    } else for (p in pids) single(p)
                    if (showExt && cycle % 4 == 0) {
                        for (e in Gm.extPids) {
                            if ((dead[-e.name.hashCode()] ?: 0) >= 3) continue
                            val v = c.exclusive { readExt(e) }
                            if (v != null) { extValues[e.name] = v; dead[-e.name.hashCode()] = 0 } else dead[-e.name.hashCode()] = (dead[-e.name.hashCode()] ?: 0) + 1
                        }
                    }
                    val now = System.nanoTime()
                    val dt = (now - last) / 1e9; last = now
                    tripTick(dt)
                    recTick((now - t0) / 1e9)
                    cycle++
                    delay(15)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                linkLost(e)
            } catch (e: Exception) {
                AppLog.i("live loop error: ${e.javaClass.simpleName}: ${e.message}")
                say("Live data stopped: ${e.message}")
            } finally {
                liveRunning = false
            }
        }
    }

    private fun pollIds(): List<Int> {
        val want = ArrayList<Int>(liveSel)
        if (tripAcc != null) want.addAll(listOf(0x0D, 0x10, 0x44, 0x5E))
        if (liveReasons.contains("dash")) want.addAll(listOf(0x0C, 0x0D, 0x05, 0x42, 0x04))
        return want.distinct().filter { (supported.isEmpty() || it in supported) && (dead[it] ?: 0) < 3 }
    }

    // ---- trip and fuel
    private var tripAcc: TripAcc? = null
    private var route: RouteRecorder? = null
    private var tripStartMs = 0L
    var tripOn by mutableStateOf(false)
    var tripKm by mutableStateOf(0.0)
    var tripLiters by mutableStateOf(0.0)
    var tripSeconds by mutableStateOf(0.0)
    var tripMaxKmh by mutableStateOf(0.0)
    var tripL100 by mutableStateOf<Double?>(null)
    var instLph by mutableStateOf(0.0)
    var instL100 by mutableStateOf<Double?>(null)
    var trips by mutableStateOf(store.loadTrips())

    fun startTrip() {
        if (conn != Conn.On) { say("Connect to the car first."); return }
        tripAcc = TripAcc(); tripStartMs = System.currentTimeMillis(); tripOn = true
        route = null
        if (recordRoute) {
            val r = RouteRecorder(ctx)
            if (r.start()) route = r else { say("Route not recorded: location permission needed."); askLocation?.invoke() }
        }
        tripKm = 0.0; tripLiters = 0.0; tripSeconds = 0.0; tripMaxKmh = 0.0; tripL100 = null
        wantLive("trip", true)
    }

    fun stopTrip() { finishTrip(); wantLive("trip", false) }

    private fun finishTrip() {
        val a = tripAcc ?: return
        tripAcc = null; tripOn = false
        route?.let { r ->
            r.stop()
            if (r.points.size >= 2 && a.km >= 0.05) {
                try { File(store.routesDir(), "trip_$tripStartMs.gpx").writeText(Gpx.build("Obdy trip " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.US).format(java.util.Date(tripStartMs)), r.points)) } catch (_: Exception) {}
            }
        }
        route = null
        if (a.km >= 0.05) {
            store.addTrip(TripRecord(tripStartMs, a.seconds, a.km, a.liters, a.maxKmh))
            trips = store.loadTrips()
            updateCar(car.copy(odometerKm = car.odometerKm + Math.round(a.km).toInt()))
            say("Trip saved: ${Calc.fmt(a.km, 1)} km")
        }
    }

    fun deleteTrip(t: TripRecord) { store.deleteTrip(t.startMs); routeFile(t)?.delete(); trips = store.loadTrips() }
    fun routeFile(t: TripRecord): File? = File(store.routesDir(), "trip_${t.startMs}.gpx").takeIf { it.exists() }

    private fun tripTick(dt: Double) {
        val a = tripAcc ?: return
        val speed = liveValues[0x0D] ?: 0.0
        val maf = liveValues[0x10]
        val lph = when {
            maf != null -> Calc.fuelLitersPerHour(maf, liveValues[0x44] ?: 1.0)
            liveValues[0x5E] != null -> liveValues[0x5E] ?: 0.0
            else -> 0.0
        }
        a.add(dt, speed, lph)
        instLph = lph
        instL100 = if (speed > 5 && lph > 0) lph / speed * 100.0 else null
        tripKm = a.km; tripLiters = a.liters; tripSeconds = a.seconds; tripMaxKmh = a.maxKmh; tripL100 = a.litersPer100
    }

    // ---- recording
    private var rec: Recording? = null
    var recOn by mutableStateOf(false)
    var recRows by mutableStateOf(0)
    var recordings by mutableStateOf<List<Pair<File, Recording>>>(emptyList())
    var viewRec by mutableStateOf<Recording?>(null)
    var compareRec by mutableStateOf<Recording?>(null)

    fun startRecording() {
        if (conn != Conn.On) { say("Connect to the car first."); return }
        val ids = liveSel.filter { Pids.byId(it) != null }
        if (ids.isEmpty()) { say("Pick at least one value on the Live screen."); return }
        val ps = ids.mapNotNull { Pids.byId(it) }
        val now = System.currentTimeMillis()
        rec = Recording("Drive " + java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.US).format(java.util.Date(now)), now, ps.map { it.name }, ps.map { it.unit })
        recRows = 0; recOn = true
        wantLive("rec", true)
    }

    fun stopRecording() { stopRecordingInternal(save = true); wantLive("rec", false) }

    private fun stopRecordingInternal(save: Boolean) {
        val r = rec ?: return
        rec = null; recOn = false
        if (save && r.rows.size >= 3) { store.saveRecording(r); say("Recording saved (${r.rows.size} samples)"); loadRecordings() }
    }

    private fun recTick(t: Double) {
        val r = rec ?: return
        val ids = liveSel.mapNotNull { Pids.byId(it)?.id }
        if (ids.size != r.columns.size) return
        r.add(t, DoubleArray(ids.size) { liveValues[ids[it]] ?: Double.NaN })
        recRows = r.rows.size
        if (r.rows.size >= 20_000) stopRecordingInternal(save = true)
    }

    fun loadRecordings() { viewModelScope.launch { recordings = withContext(Dispatchers.IO) { store.listRecordings() } } }
    fun deleteRecording(f: File) { store.deleteRecording(f); if (viewRec?.startedMs?.let { "rec_$it.csv" } == f.name) viewRec = null; loadRecordings() }

    fun shareFile(f: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openImage(path: String) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(path))
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { say("Could not open the photo.") }
    }

    fun shareText(text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ------------------------------------------------------------------ faults, readiness, freeze frame
    var scanBusy by mutableStateOf(false)
    var scanProgress by mutableStateOf("")
    var faults by mutableStateOf<List<Fault>>(emptyList())
    var monitors by mutableStateOf<MonitorStatus?>(null)
    var freeze by mutableStateOf<FreezeFrame?>(null)
    var scanNotes by mutableStateOf<List<String>>(emptyList())
    var lastScanMs by mutableStateOf(0L)
    var scanHistory by mutableStateOf(store.loadScans())
    private var foundModules: List<FoundModule> = emptyList()
    var foundCount by mutableStateOf(0)

    /** [deep] also looks for ABS, airbag, body and other modules (about 15 seconds). */
    fun scan(deep: Boolean) {
        if (scanBusy) return
        if (conn != Conn.On) { say("Connect to the car first."); return }
        scanBusy = true; scanProgress = "Reading the engine computer..."
        viewModelScope.launch {
            try {
                val all = ArrayList<Fault>(); val notes = ArrayList<String>()
                val mon = withCar { readMonitors() }
                scanProgress = "Reading fault codes..."
                val obd = withCar { readObdFaults() } ?: return@launch
                all.addAll(obd)
                scanProgress = "Reading freeze frame..."
                val fr = withCar { readFreezeFrame() }
                voltage = withCar { voltage() } ?: voltage
                warmups = if (0x30 in supported) withCar { readPid(Pids.byId(0x30)!!) }?.toInt() else null
                distClear = if (0x31 in supported) withCar { readPid(Pids.byId(0x31)!!) }?.toInt() else null
                if (deep) {
                    foundModules = withCar { findModules { i, n, name -> scanProgress = "Looking for modules ($i/$n): $name" } } ?: emptyList()
                    foundCount = foundModules.size
                    for (m in foundModules) {
                        if (m.probe.resp == "7E8" || m.probe.resp == "7EA") continue    // engine and transmission were read above
                        scanProgress = "Reading ${m.probe.name}..."
                        val r = withCar { readModuleFaults(m) } ?: continue
                        all.addAll(r.faults); notes.addAll(r.notes)
                    }
                    notes.add(0, "Modules that answered: " + (foundModules.joinToString { "${it.probe.name} [${it.probe.req}/${it.probe.resp}]" }.ifEmpty { "none besides the engine computer" }))
                }
                faults = all.distinctBy { it.key }; monitors = mon; freeze = fr; scanNotes = notes
                lastScanMs = System.currentTimeMillis()
                store.addScan(ScanRecord(lastScanMs, car.odometerKm, mon?.milOn, faults, voltage, notes.joinToString(" | ")))
                scanHistory = store.loadScans()
                say(if (faults.isEmpty()) "No fault codes found." else "${faults.size} fault code(s) found.")
            } finally {
                scanBusy = false; scanProgress = ""
            }
        }
    }

    var warmups by mutableStateOf<Int?>(null)
    var distClear by mutableStateOf<Int?>(null)
    fun inspection(): List<Verdict> = if (lastScanMs == 0L) Inspection.evaluate(null, emptyList(), null, null, null) else Inspection.evaluate(monitors?.milOn, faults, monitors, warmups, distClear)

    var clearing by mutableStateOf(false)
    fun clearCodes() {
        if (clearing) return
        clearing = true
        viewModelScope.launch {
            try {
                val results = ArrayList<String>()
                val ok = withCar { clearEmissionCodes() }
                results.add(if (ok == true) "Engine and transmission: cleared" else "Engine and transmission: no confirmation")
                for (m in foundModules) {
                    if (m.probe.resp == "7E8" || m.probe.resp == "7EA") continue
                    withCar { clearModule(m) }?.let { results.add(it) }
                }
                scanNotes = results
                say("Clear command sent. Scanning again...")
                scanBusy = false
                scan(deep = foundModules.isNotEmpty())
            } finally { clearing = false }
        }
    }

    // ------------------------------------------------------------------ battery and charging test
    var batRest by mutableStateOf<Double?>(null)
    var batCrank by mutableStateOf<Double?>(null)
    var batIdle by mutableStateOf<Double?>(null)
    var batRev by mutableStateOf<Double?>(null)
    var batBusy by mutableStateOf<String?>(null)
    var batMsg by mutableStateOf("")
    var batLive by mutableStateOf<Double?>(null)
    var batHistory by mutableStateOf(store.loadBattery())

    fun batteryReset() { batRest = null; batCrank = null; batIdle = null; batRev = null; batMsg = "" }

    /** step 1 = resting, 2 = cranking, 3 = idle, 4 = about 2000 rpm */
    fun batteryStep(step: Int) {
        if (batBusy != null) return
        if (conn != Conn.On) { say("Connect to the car first."); return }
        val seconds = if (step == 2) 15 else 6
        batBusy = when (step) { 1 -> "Reading the resting voltage..."; 2 -> "START THE ENGINE NOW (cranking is being measured)..."; 3 -> "Measuring at idle..."; else -> "Hold about 2000 rpm..." }
        if (step == 4) wantLive("battery", true)
        viewModelScope.launch {
            try {
                val samples = ArrayList<Double>()
                val end = System.currentTimeMillis() + seconds * 1000L
                while (System.currentTimeMillis() < end) {
                    val v = withCar { voltage() }
                    if (conn != Conn.On) break
                    if (v != null) { samples.add(v); batLive = v }
                    delay(if (step == 2) 5 else 150)
                }
                if (samples.isEmpty()) { batMsg = "No voltage readings. If the adapter restarted while cranking, reconnect and continue with the idle step."; return@launch }
                when (step) {
                    1 -> batRest = samples.average()
                    2 -> { batCrank = samples.min(); if (samples.max() - samples.min() < 0.4) batMsg = "No voltage dip was seen. The engine may not have been started during the window." else batMsg = "" }
                    3 -> batIdle = samples.average()
                    else -> batRev = samples.average()
                }
                voltage = samples.last()
            } finally {
                batBusy = null
                if (step == 4) wantLive("battery", false)
            }
        }
    }

    fun batterySave() {
        store.addBattery(BatteryRecord(System.currentTimeMillis(), batRest, batCrank, batIdle, batRev))
        batHistory = store.loadBattery(); say("Test saved")
    }

    // ------------------------------------------------------------------ maintenance
    var maint by mutableStateOf(store.loadMaintenance())
    private fun saveMaint() { store.saveMaintenance(maint); maint = maint.toList() }

    fun dueList(): List<Due> = maint.map { Maintenance.due(it, car.odometerKm, System.currentTimeMillis()) }
    fun markDone(item: MaintItem) { item.lastKm = car.odometerKm; item.lastDateMs = System.currentTimeMillis(); saveMaint() }
    fun setInterval(item: MaintItem, km: Int, months: Int) { item.everyKm = km; item.everyMonths = months; saveMaint() }

    fun dueNames(): List<String> = dueList().filter { it.overdue || it.soon }.map { it.item.name } + docNotices()

    fun docNotices(): List<String> {
        val now = System.currentTimeMillis()
        return docs.filter { it.dueMs > 0 }.mapNotNull { d ->
            val n = d.daysLeft(now)
            if (n < 0) "${d.name} expired" else if (n <= 30) "${d.name} expires in $n days" else null
        }
    }

    fun checkServiceNotice() {
        val now = System.currentTimeMillis()
        Notifier.serviceDue(ctx, dueList().filter { it.overdue }.map { it.item.name } + docs.filter { it.dueMs > 0 && it.daysLeft(now) <= 14 }.map { if (it.daysLeft(now) < 0) "${it.name} expired" else "${it.name} in ${it.daysLeft(now)} days" })
    }

    // ------------------------------------------------------------------ AI analysis and report
    var aiBusy by mutableStateOf(false)
    var aiText by mutableStateOf<String?>(null)
    var aiError by mutableStateOf<String?>(null)
    var aiHistory by mutableStateOf(store.loadAi())

    private fun liveSnapshot(): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        for ((id, v) in liveValues.toMap().toSortedMap()) { val p = Pids.byId(id) ?: continue; m[p.name] = Calc.fmt(v, 1) + " " + p.unit }
        for ((k, v) in extValues.toMap()) m[k] = Calc.fmt(v, 1)
        return m
    }

    private fun repairHistory(): String = repairs.take(8).joinToString("\n") { "- ${Docs.formatDate(it.timeMs)}: ${it.title} (${it.category})" + (if (it.km > 0) " at ${it.km} km" else "") + (if (it.notes.isNotBlank()) ": ${it.notes.take(120)}" else "") }

    fun aiPrompt(focus: String?, extra: String = "", withFormat: Boolean = true): String {
        val rep = repairHistory()
        val full = if (rep.isBlank()) extra else extra + "\nREPAIR AND SERVICE HISTORY (already done, do not suggest it again as the first step):\n" + rep
        return AiPrompt.build(car, faults, monitors, freeze, liveSnapshot(), batHistory.firstOrNull(), symptoms, dueList().filter { it.overdue }.map { it.item.name }, focus, full, withFormat)
    }

    fun runAi(focus: String?) {
        if (aiBusy) return
        val p = activeProvider
        if (p == null) { aiError = "Add an AI provider in Settings first."; return }
        aiBusy = true; aiError = null
        val prompt = aiPrompt(focus)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Ai.ask(p, prompt, aiWeb) }
            if (r.text != null) {
                aiText = r.text
                store.addAi(AiRecord(System.currentTimeMillis(), focus ?: "Full analysis", r.text)); aiHistory = store.loadAi()
            } else aiError = r.error
            aiBusy = false
        }
    }

    fun makeReport() {
        viewModelScope.launch {
            val lines = Report.lines(car, faults, monitors, freeze, liveSnapshot(), batHistory.firstOrNull(), symptoms, aiText, dueNames(), voltage, repairs)
            val f = withContext(Dispatchers.IO) { Report.build(ctx, lines) }
            shareFile(f, "application/pdf")
        }
    }

    // ------------------------------------------------------------------ console
    val consoleOut = mutableStateListOf<String>()
    var consoleBusy by mutableStateOf(false)

    fun consoleSend(header: String, cmd: String) {
        if (cmd.isBlank() || consoleBusy) return
        if (conn != Conn.On) { consoleOut.add("Not connected."); return }
        consoleBusy = true
        consoleOut.add("> ${if (header.isNotBlank()) "[$header] " else ""}$cmd")
        viewModelScope.launch {
            try { withCar { console(header, cmd) }?.let { consoleOut.add(it) } } finally { consoleBusy = false }
        }
    }

    // ------------------------------------------------------------------ updates from GitHub
    var updateText by mutableStateOf("")
    var updateBusy by mutableStateOf(false)
    var updateProgress by mutableStateOf(-1)
    var updateInfo by mutableStateOf<UpdateInfo?>(null)

    fun checkUpdate() {
        if (updateBusy) return
        updateBusy = true; updateText = "Checking GitHub..."; updateInfo = null
        viewModelScope.launch {
            try {
                val u = withContext(Dispatchers.IO) { Updater.check(ctx) }
                updateInfo = u
                updateText = if (u == null) "You have the newest version (${BuildConfig.VERSION_LABEL})." else "A newer build is available: ${u.name}"
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                updateText = e.message ?: "Could not check for updates."
            } finally { updateBusy = false }
        }
    }

    fun installUpdate() {
        val u = updateInfo ?: return
        if (updateBusy) return
        updateBusy = true; updateProgress = 0; updateText = "Downloading..."
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.IO) { Updater.download(ctx, u) { p -> updateProgress = p } }
                updateText = "Opening the installer..."
                if (!Updater.install(ctx, f)) updateText = "Allow Obdy to install apps, come back, and press Install again."
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                updateText = "Download failed: ${e.message}"
            } finally { updateBusy = false; updateProgress = -1 }
        }
    }


    // ------------------------------------------------------------------ guided diagnosis: questions -> solution
    var gCode by mutableStateOf<String?>(null)
    var gStage by mutableStateOf(0)            // 0 = your answers, 1 = AI follow-up questions, 2 = solution
    val gAnswers = mutableStateMapOf<String, List<String>>()
    var gNotes by mutableStateOf("")
    var gAiQs by mutableStateOf<List<AiQuestion>>(emptyList())
    val gAiAnswers = mutableStateMapOf<Int, String>()
    var gBusy by mutableStateOf(false)
    var gResult by mutableStateOf<String?>(null)
    var gError by mutableStateOf<String?>(null)

    fun startGuided(code: String) {
        gCode = code.take(5); gStage = 0; gAnswers.clear(); gNotes = ""; gAiQs = emptyList(); gAiAnswers.clear(); gResult = null; gError = null
    }

    fun toggleAnswer(q: Question, option: String) {
        val cur = gAnswers[q.id] ?: emptyList()
        gAnswers[q.id] = if (q.multi) { if (option in cur) cur - option else cur + option } else listOf(option)
    }

    fun guidedReplaced(): Set<String> = (gAnswers["done"] ?: emptyList()).toSet()

    private fun guidedFacts(): String {
        val code = gCode ?: return ""
        val sb = StringBuilder("OWNER'S ANSWERS ABOUT $code\n")
        for (q in Guided.questions(code)) { val a = gAnswers[q.id]; if (!a.isNullOrEmpty()) sb.append("- ${q.text} ${a.joinToString("; ")}\n") }
        if (gNotes.isNotBlank()) sb.append("- Extra notes: ${gNotes.trim()}\n")
        for ((i, q) in gAiQs.withIndex()) gAiAnswers[i]?.let { sb.append("- ${q.q} $it\n") }
        return sb.toString()
    }

    /** Step 2: the AI reads the answers and asks a few short questions of its own (or goes straight to the solution). */
    fun guidedAskAi() {
        val code = gCode ?: return
        val p = activeProvider
        if (p == null) { gError = "Add an AI provider in Settings, or use the built-in suggestions."; return }
        if (gBusy) return
        gBusy = true; gError = null
        val prompt = aiPrompt(code, guidedFacts() +
            "\nThe owner may already have replaced parts without fixing the problem, so do NOT just repeat the usual part replacement.\n" +
            "Ask up to 4 SHORT follow-up questions the owner can answer without tools (pick one option each) that separate the remaining possible causes. " +
            "If you already have enough, return an empty list.\n" +
            "Reply ONLY with JSON, no other text: {\"questions\":[{\"q\":\"question text\",\"options\":[\"option 1\",\"option 2\",\"option 3\"]}]}", withFormat = false)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Ai.ask(p, prompt) }
            gBusy = false
            if (r.text == null) { gError = r.error; return@launch }
            val arr = Ai.extractJson(r.text)?.optJSONArray("questions")
            val qs = ArrayList<AiQuestion>()
            if (arr != null) for (i in 0 until minOf(arr.length(), 4)) {
                val o = arr.optJSONObject(i) ?: continue
                val opts = o.optJSONArray("options")
                val list = if (opts == null) emptyList() else (0 until opts.length()).map { opts.optString(it) }.filter { it.isNotBlank() }
                if (o.optString("q").isNotBlank() && list.size >= 2) qs.add(AiQuestion(o.optString("q"), list))
            }
            gAiQs = qs; gAiAnswers.clear()
            if (qs.isEmpty()) guidedSolve() else gStage = 1
        }
    }

    /** Step 3: the AI gives the solution using every answer. */
    fun guidedSolve() {
        val code = gCode ?: return
        val p = activeProvider
        if (p == null) { gError = "Add an AI provider in Settings, or use the built-in suggestions."; return }
        if (gBusy) return
        gBusy = true; gError = null
        val prompt = aiPrompt(code, guidedFacts() +
            "\nGive the solution for $code. Rules: (1) say first what is most likely given ALL the answers; " +
            "(2) if the owner already replaced the usual part and the code stayed, explain why and give the next causes: cleaning or inspection jobs (filters, connectors, hoses, grounds, carbon deposits) before more parts; " +
            "(3) simple step-by-step checks in order, cheapest first; (4) what each result means and which fix follows; (5) when to stop and go to a workshop. Plain text, short, no tables.", withFormat = false)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Ai.ask(p, prompt) }
            gBusy = false
            if (r.text != null) { gResult = r.text; gStage = 2; store.addAi(AiRecord(System.currentTimeMillis(), "Solution for $code", r.text)); aiHistory = store.loadAi() } else gError = r.error
        }
    }

    // ------------------------------------------------------------------ where is the part
    var partsCode by mutableStateOf<String?>(null)
    var aiParts by mutableStateOf<List<AiPart>>(emptyList())
    var partsBusy by mutableStateOf(false)
    var partsError by mutableStateOf<String?>(null)

    fun showParts(code: String?) { partsCode = code?.take(5); aiParts = emptyList(); partsError = null; overlay = "parts" }

    /** The AI names the likely parts for the code (and your answers) and where to look. Always an estimate. */
    fun locateWithAi() {
        val p = activeProvider
        if (p == null) { partsError = "Add an AI provider in Settings first."; return }
        if (partsBusy) return
        partsBusy = true; partsError = null
        val zones = Zone.values().joinToString("; ") { it.name + " = " + it.label }
        val facts = if (gCode == partsCode && gCode != null) guidedFacts() else ""
        val prompt = aiPrompt(partsCode, facts +
            "\nTask: tell the owner EXACTLY where to look on this car for the faulty part. Use only these zone names: $zones.\n" +
            "Reply ONLY with JSON, no other text: {\"parts\":[{\"name\":\"part\",\"zone\":\"ZoneName\",\"where\":\"where it is, plain words, what is next to it\",\"check\":\"how to check it simply\",\"confidence\":\"high|medium|low\"}]}\n" +
            "Give 1 to 5 parts, most likely first. If you are not sure of the exact location on this specific model, say so in where and use confidence low.", withFormat = false)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Ai.ask(p, prompt) }
            partsBusy = false
            if (r.text == null) { partsError = r.error; return@launch }
            val arr = Ai.extractJson(r.text)?.optJSONArray("parts")
            val list = ArrayList<AiPart>()
            if (arr != null) for (i in 0 until minOf(arr.length(), 5)) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(AiPart(o.optString("name"), Parts.zoneByName(o.optString("zone")), o.optString("where"), o.optString("check"), o.optString("confidence", "low")))
            }
            if (list.isEmpty()) partsError = "The AI did not return a usable answer. Try again." else aiParts = list
        }
    }

    // ------------------------------------------------------------------ online checks
    var online by mutableStateOf<OnlineInfo?>(null)
    var onlineBusy by mutableStateOf(false)
    var onlineError by mutableStateOf<String?>(null)
    var webAiBusy by mutableStateOf(false)
    var webAiText by mutableStateOf<String?>(null)
    var webAiError by mutableStateOf<String?>(null)

    fun loadOnline() {
        if (onlineBusy) return
        onlineBusy = true; onlineError = null
        viewModelScope.launch {
            try { online = withContext(Dispatchers.IO) { Online.load("Chevrolet", "Malibu", car.year) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { onlineError = "Could not load: ${e.message}. Check the phone's internet." }
            finally { onlineBusy = false }
        }
    }

    fun openUrl(url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { say("No browser found.") }
    }

    fun webSearch(kind: String, code: String?) {
        val what = if (code != null) "$code ${car.year} Chevrolet Malibu fix" else "${car.year} Chevrolet Malibu common problems service bulletin"
        openUrl(Online.searchUrl(kind, what))
    }

    /** Asks the AI to look for known fixes and service bulletins. Only Gemini can really search Google; others answer from what they learned. */
    fun aiWebSearch(code: String?) {
        val p = activeProvider
        if (p == null) { webAiError = "Add an AI provider in Settings first."; return }
        if (webAiBusy) return
        webAiBusy = true; webAiError = null
        val subject = if (code != null) "fault code $code" else "the faults listed above"
        val prompt = aiPrompt(code, "Task: look for known fixes, technical service bulletins, software updates and common causes for $subject on a ${car.year} Chevrolet Malibu. " +
            "Name where the information comes from (forum, bulletin, manual). Separate what is well documented from what is only reported by owners. Plain text, short.", withFormat = false)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Ai.ask(p, prompt, web = p.type == AiType.Gemini) }
            webAiBusy = false
            if (r.text != null) webAiText = (if (p.type == AiType.Gemini) "" else "(${p.name} cannot search the web: this answer comes from what it learned in training.)\n\n") + r.text else webAiError = r.error
        }
    }

    // ------------------------------------------------------------------ hide a code inside Obdy (the car is not changed)
    var ignored by mutableStateOf(store.loadIgnored())
    val visibleFaults: List<Fault> get() = faults.filter { it.code.take(5) !in ignored }
    val hiddenFaults: List<Fault> get() = faults.filter { it.code.take(5) in ignored }

    fun ignoreCode(code: String) {
        if (DtcDb.severity(code) >= 2) { say("Urgent codes (engine misfire, overheating, brakes, airbag) cannot be hidden."); return }
        ignored = ignored + code.take(5); store.saveIgnored(ignored); say("Hidden in Obdy. The car still has it.")
    }

    fun restoreCode(code: String) { ignored = ignored - code.take(5); store.saveIgnored(ignored) }


    // ------------------------------------------------------------------ adapter doctor
    var doctorResult by mutableStateOf<List<Verdict>>(emptyList())
    var doctorBusy by mutableStateOf(false)

    fun runDoctor() {
        if (doctorBusy) return
        if (conn != Conn.On) { say("Connect to the car first."); return }
        doctorBusy = true
        viewModelScope.launch {
            try { doctorResult = withCar { doctor(supported.size) } ?: emptyList() } finally { doctorBusy = false }
        }
    }

    // ------------------------------------------------------------------ engine self-tests (Mode 06)
    var tests by mutableStateOf<List<TestResult>>(emptyList())
    var testsBusy by mutableStateOf(false)
    var testsProgress by mutableStateOf("")
    var testsRead by mutableStateOf(false)

    fun readTests() {
        if (testsBusy) return
        if (conn != Conn.On) { say("Connect to the car first."); return }
        testsBusy = true; testsProgress = "Asking which tests exist..."
        viewModelScope.launch {
            try { tests = withCar { readMode06 { p -> testsProgress = p } } ?: emptyList(); testsRead = true } finally { testsBusy = false; testsProgress = "" }
        }
    }

    // ------------------------------------------------------------------ health check and trends
    var healthBusy by mutableStateOf(false)
    var healthProgress by mutableStateOf(0)
    var healthResult by mutableStateOf<List<Verdict>>(emptyList())
    var healthHistory by mutableStateOf(store.loadHealth())

    /** 25 seconds of readings (engine warm and idling), judged by simple rules. The averages are kept for the trend graphs. */
    fun runHealth() {
        if (healthBusy) return
        if (conn != Conn.On) { say("Connect to the car first."); return }
        healthBusy = true; healthProgress = 0; healthResult = emptyList()
        viewModelScope.launch {
            try {
                val ids = HealthCheck.pids.filter { supported.isEmpty() || it in supported }.mapNotNull { Pids.byId(it) }
                val samples = HashMap<Int, MutableList<Double>>()
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 25_000) {
                    for (chunk in ids.chunked(4)) {
                        val m = if (fastMode && !fastBroken && chunk.size > 1) withCar { readPids(chunk) } else null
                        if (m != null && m.size == chunk.size) { for ((id, value) in m) samples.getOrPut(id) { ArrayList() }.add(value) }
                        else for (p in chunk) withCar { readPid(p) }?.let { samples.getOrPut(p.id) { ArrayList() }.add(it) }
                        if (conn != Conn.On) return@launch
                    }
                    healthProgress = ((System.currentTimeMillis() - start) * 100 / 25_000).toInt().coerceAtMost(100)
                }
                healthResult = HealthCheck.evaluate(samples)
                val sum = HealthCheck.summary(samples)
                if (sum.isNotEmpty() && (samples[0x0C]?.average() ?: 0.0) > 400 && (sum["coolant"] ?: 0.0) >= 75) {
                    store.addHealth(HealthRecord(System.currentTimeMillis(), sum)); healthHistory = store.loadHealth()
                }
            } finally { healthBusy = false }
        }
    }

    // ------------------------------------------------------------------ repairs, expenses, documents
    var repairs by mutableStateOf(store.loadRepairs())
    var docs by mutableStateOf(store.loadDocs())
    var pendingReceipt by mutableStateOf("")

    fun newId(): String = UUID.randomUUID().toString()

    fun saveRepair(r: RepairRecord) {
        repairs = (repairs.filter { it.id != r.id } + r).sortedByDescending { it.timeMs }; store.saveRepairs(repairs)
        if (r.km > car.odometerKm) updateCar(car.copy(odometerKm = r.km))
        pendingReceipt = ""
    }

    fun deleteRepair(r: RepairRecord) {
        if (r.receipt.isNotBlank()) try { File(r.receipt).delete() } catch (_: Exception) {}
        repairs = repairs.filter { it.id != r.id }; store.saveRepairs(repairs)
    }

    fun saveDoc(id: String, dateText: String): Boolean {
        val ms = Docs.parseDate(dateText) ?: return false
        docs = docs.map { if (it.id == id) DocReminder(id, it.name, ms) else it }; store.saveDocs(docs); return true
    }

    // ------------------------------------------------------------------ pickers (photo, backup file, voice)
    var launchPicker: ((String) -> Unit)? = null
    var launchVoice: (() -> Unit)? = null
    private var pickerPurpose = ""

    fun pick(purpose: String, mime: String) {
        pickerPurpose = purpose
        val l = launchPicker
        if (l == null) say("The file picker is not available.") else l(mime)
    }

    private fun loadJpeg(uri: Uri, maxSide: Int): ByteArray? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp == null) null else {
            val big = maxOf(bmp.width, bmp.height)
            val scaled = if (big > maxSide) { val f = maxSide.toFloat() / big; Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true) } else bmp
            val bos = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, 80, bos); bos.toByteArray()
        }
    } catch (e: Exception) { null }

    fun pickerResult(uri: Uri?) {
        if (uri == null) return
        when (pickerPurpose) {
            "receipt" -> {
                val b = loadJpeg(uri, 1600)
                if (b == null) say("Could not read that photo.") else {
                    val f = File(store.receiptsDir(), "r_${System.currentTimeMillis()}.jpg"); f.writeBytes(b); pendingReceipt = f.absolutePath; say("Photo attached")
                }
            }
            "chat" -> {
                val b = loadJpeg(uri, 1280)
                if (b == null) say("Could not read that photo.") else { chatImage = b; say("Photo attached to your question") }
            }
            "restore" -> {
                val text = try { ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } } catch (e: Exception) { null }
                val n = if (text == null) -1 else store.importAll(text)
                if (n < 0) say("That file is not an Obdy backup.") else { reloadAll(); say("Restored $n parts.") }
            }
        }
    }

    fun voiceResult(text: String) { chatDraft = text }

    private fun reloadAll() {
        car = store.loadCar(); symptoms = store.symptoms; trips = store.loadTrips(); scanHistory = store.loadScans(); batHistory = store.loadBattery()
        maint = store.loadMaintenance(); aiHistory = store.loadAi(); repairs = store.loadRepairs(); docs = store.loadDocs(); healthHistory = store.loadHealth()
        providers = store.loadProviders(); ignored = store.loadIgnored(); liveSel = store.livePids; applyTheme(store.themeKey)
        wifiHost = store.wifiHost; wifiPort = store.wifiPort.toString()
    }

    // ------------------------------------------------------------------ backup
    fun exportBackup(includeKeys: Boolean) {
        val dir = File(ctx.filesDir, "backups").also { it.mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "obdy-backup-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".json")
        f.writeText(store.exportAll(includeKeys))
        shareFile(f, "application/json")
    }

    // ------------------------------------------------------------------ chat with the car's data
    class ChatMsg(val fromUser: Boolean, val text: String, val hasImage: Boolean)
    val chat = mutableStateListOf<ChatMsg>()
    var chatBusy by mutableStateOf(false)
    var chatImage by mutableStateOf<ByteArray?>(null)
    var chatError by mutableStateOf<String?>(null)
    var chatDraft by mutableStateOf("")

    fun chatSend(text: String) {
        val q = text.trim()
        if (q.isEmpty() || chatBusy) return
        val p = activeProvider
        if (p == null) { chatError = "Add an AI service in Settings first."; return }
        val img = chatImage
        chat.add(ChatMsg(true, q, img != null))
        chatImage = null; chatDraft = ""; chatError = null; chatBusy = true
        val history = chat.dropLast(1).takeLast(8).joinToString("\n") { (if (it.fromUser) "Owner: " else "Assistant: ") + it.text }
        val prompt = aiPrompt(null,
            "You are chatting with the owner about this car. Use the data above. Answer the latest question directly and briefly (under 180 words unless more is needed). " +
            "If a photo is attached, say what you see that matters for the car (warning light meaning, part, leak, tyre damage) and what to do. If something could be dangerous, say so first.\n" +
            (if (history.isNotBlank()) "CONVERSATION SO FAR:\n$history\n" else "") + "LATEST OWNER QUESTION: $q", withFormat = false)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Ai.ask(p, prompt, false, img) }
            if (r.text != null) chat.add(ChatMsg(false, r.text.replace("**", "").replace("##", ""), false)) else chatError = r.error
            chatBusy = false
        }
    }

    override fun onCleared() { disconnect(); super.onCleared() }
}

class AiPart(val name: String, val zone: Zone?, val where: String, val check: String, val confidence: String)
