package com.example.obdy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Car(
    val name: String = "Chevrolet Malibu 2013 LT",
    val plate: String = "",
    val vin: String = "",
    val year: Int = 2013,
    val engine: String = "",
    val odometerKm: Int = 0,
    val tankLiters: Double = 59.0,
    val fuelPrice: Double = 0.0,
    val notes: String = "",
)

class ScanRecord(val timeMs: Long, val odometerKm: Int, val milOn: Boolean?, val faults: List<Fault>, val voltage: Double?, val notes: String)
class TripRecord(val startMs: Long, val seconds: Double, val km: Double, val liters: Double, val maxKmh: Double, val score: Int = -1)
class BatteryRecord(val timeMs: Long, val rest: Double?, val crank: Double?, val idle: Double?, val rev: Double?, val load: Double? = null, val km: Int = 0)
class AiRecord(val timeMs: Long, val title: String, val text: String)

/** All saved data lives in small JSON files in the app's private folder, so nothing leaves the phone except what you share. */
class Store(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("obdy", Context.MODE_PRIVATE)
    private fun file(name: String) = File(ctx.filesDir, name)
    private val prefLog = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, k ->
        try {
            val secret = Regex("key|token|password|secret", RegexOption.IGNORE_CASE).containsMatchIn(k ?: "")
            AppLog.i("[SETTING] $k = " + (if (secret) "***" else p.all[k].toString().take(200)), true)
        } catch (_: Exception) {}
    }
    init { prefs.registerOnSharedPreferenceChangeListener(prefLog) }

    // ---- simple settings
    var themeKey: String get() = prefs.getString("theme", "dash") ?: "dash"; set(v) { prefs.edit().putString("theme", v).apply() }
    var geminiKey: String get() = prefs.getString("gkey", "") ?: ""; set(v) { prefs.edit().putString("gkey", v.trim()).apply() }
    var geminiModel: String get() = prefs.getString("gmodel", "gemini-3.5-flash") ?: "gemini-3.5-flash"; set(v) { prefs.edit().putString("gmodel", v.trim()).apply() }
    var wifiHost: String get() = prefs.getString("whost", "192.168.0.10") ?: "192.168.0.10"; set(v) { prefs.edit().putString("whost", v.trim()).apply() }
    var wifiPort: Int get() = prefs.getInt("wport", 35000); set(v) { prefs.edit().putInt("wport", v).apply() }
    var lastKind: String get() = prefs.getString("lastKind", "") ?: ""; set(v) { prefs.edit().putString("lastKind", v).apply() }
    var lastAddress: String get() = prefs.getString("lastAddr", "") ?: ""; set(v) { prefs.edit().putString("lastAddr", v).apply() }
    /** "req,resp,did;..." identifiers recorded while driving; empty = the engine list from the 11-27 sweep. */
    var recordIds: String get() = prefs.getString("recIds", "") ?: ""; set(v) { prefs.edit().putString("recIds", v).apply() }
    var autoConnect: Boolean get() = prefs.getBoolean("autoConnect", true); set(v) { prefs.edit().putBoolean("autoConnect", v).apply() }
    var keepScreenOn: Boolean get() = prefs.getBoolean("keepOn", true); set(v) { prefs.edit().putBoolean("keepOn", v).apply() }
    var autoScan: Boolean get() = prefs.getBoolean("autoScan", true); set(v) { prefs.edit().putBoolean("autoScan", v).apply() }
    var deepMode06: Boolean get() = prefs.getBoolean("deep06", true); set(v) { prefs.edit().putBoolean("deep06", v).apply() }
    /** Odometer source found inside a module: "resp|request|offset|len|scale|miles", empty = none. */
    var odoSpec: String get() = prefs.getString("odoSpec", "") ?: ""; set(v) { prefs.edit().putString("odoSpec", v).apply() }
    var fastMode: Boolean get() = prefs.getBoolean("fast", true); set(v) { prefs.edit().putBoolean("fast", v).apply() }
    var autoReconnect: Boolean get() = prefs.getBoolean("reconnect", true); set(v) { prefs.edit().putBoolean("reconnect", v).apply() }
    var recordRoute: Boolean get() = prefs.getBoolean("route", false); set(v) { prefs.edit().putBoolean("route", v).apply() }
    var autoUpdate: Boolean get() = prefs.getBoolean("autoUpdate", true); set(v) { prefs.edit().putBoolean("autoUpdate", v).apply() }
    var wifiOnlyUpdate: Boolean get() = prefs.getBoolean("wifiUpdate", true); set(v) { prefs.edit().putBoolean("wifiUpdate", v).apply() }
    var lastUpdateCheck: Long get() = prefs.getLong("updCheck", 0L); set(v) { prefs.edit().putLong("updCheck", v).apply() }
    var flightOn: Boolean get() = prefs.getBoolean("flight", true); set(v) { prefs.edit().putBoolean("flight", v).apply() }
    var coldCapture: Boolean get() = prefs.getBoolean("cold", true); set(v) { prefs.edit().putBoolean("cold", v).apply() }
    var parkingOn: Boolean get() = prefs.getBoolean("parkingOn", false); set(v) { prefs.edit().putBoolean("parkingOn", v).apply() }
    var autoBackup: Boolean get() = prefs.getBoolean("autoBackup", false); set(v) { prefs.edit().putBoolean("autoBackup", v).apply() }
    var backupUri: String get() = prefs.getString("backupUri", "") ?: ""; set(v) { prefs.edit().putString("backupUri", v).apply() }
    var lastBackup: Long get() = prefs.getLong("lastBackup", 0L); set(v) { prefs.edit().putLong("lastBackup", v).apply() }
    var fuelCal: Double get() = (prefs.getString("fuelCal", "1.0") ?: "1.0").toDoubleOrNull() ?: 1.0; set(v) { prefs.edit().putString("fuelCal", v.toString()).apply() }
    var symptoms: String get() = prefs.getString("symptoms", "") ?: ""; set(v) { prefs.edit().putString("symptoms", v).apply() }
    var livePids: List<Int>
        get() {
            val l = (prefs.getString("livePids", null) ?: Pids.defaultLive.joinToString(",")).split(",").mapNotNull { it.trim().toIntOrNull() }
            // 1.16: the gear card is new; put it first once for existing users.
            if (!prefs.getBoolean("gearCardAdded", false)) { prefs.edit().putBoolean("gearCardAdded", true).apply(); if (Gm.GEAR_ID !in l) return listOf(Gm.GEAR_ID) + l }
            return l
        }
        set(v) { prefs.edit().putString("livePids", v.joinToString(",")).apply() }

    private fun readJson(name: String): JSONArray = try { JSONArray(file(name).readText()) } catch (e: Exception) { JSONArray() }
    private fun writeJson(name: String, a: JSONArray) { try { file(name).writeText(a.toString()) } catch (_: Exception) {} }
    private fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> = (0 until length()).mapNotNull { try { f(getJSONObject(it)) } catch (e: Exception) { null } }


    // ---- AI providers (several can be saved; one is active)
    var activeProvider: String get() = prefs.getString("aiActive", "") ?: ""; set(v) { prefs.edit().putString("aiActive", v).apply() }

    fun loadProviders(): List<AiProvider> {
        val list = readJson("providers.json").mapObjects { o ->
            AiProvider(o.getString("id"), try { AiType.valueOf(o.getString("type")) } catch (e: Exception) { AiType.OpenAi }, o.optString("name"), o.optString("key"), o.optString("model"), o.optString("base"))
        }
        if (list.isNotEmpty()) return list
        val legacy = geminiKey
        if (legacy.isNotBlank()) return listOf(AiProvider("gemini-legacy", AiType.Gemini, "Google Gemini", legacy, geminiModel, ""))
        return emptyList()
    }

    fun saveProviders(list: List<AiProvider>) {
        writeJson("providers.json", JSONArray().also { a -> list.forEach { a.put(JSONObject().put("id", it.id).put("type", it.type.name).put("name", it.name).put("key", it.key).put("model", it.model).put("base", it.baseUrl)) } })
    }

    // ---- codes hidden inside Obdy (the car is not changed)
    fun loadIgnored(): Set<String> = (prefs.getString("ignored", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()
    fun saveIgnored(s: Set<String>) { prefs.edit().putString("ignored", s.joinToString(",")).apply() }

    // ---- car
    fun loadCar(): Car {
        val o = try { JSONObject(file("car.json").readText()) } catch (e: Exception) { return Car() }
        return Car(
            o.optString("name", "Chevrolet Malibu 2013 LT"), o.optString("plate"), o.optString("vin"), o.optInt("year", 2013), o.optString("engine"),
            o.optInt("km", 0), o.optDouble("tank", 59.0), o.optDouble("price", 0.0), o.optString("notes"),
        )
    }

    fun saveCar(c: Car) {
        val o = JSONObject().put("name", c.name).put("plate", c.plate).put("vin", c.vin).put("year", c.year).put("engine", c.engine)
            .put("km", c.odometerKm).put("tank", c.tankLiters).put("price", c.fuelPrice).put("notes", c.notes)
        try { file("car.json").writeText(o.toString()) } catch (_: Exception) {}
    }

    // ---- odometer anchor: km on the dash at the moment the car's own "distance since codes cleared" counter read d31
    class OdoAnchor(val km: Int, val d31: Int, val timeMs: Long)
    fun loadOdo(): OdoAnchor? = try { val o = JSONObject(file("odo.json").readText()); OdoAnchor(o.getInt("km"), o.getInt("d"), o.optLong("t")) } catch (_: Exception) { null }
    fun saveOdo(a: OdoAnchor) { try { file("odo.json").writeText(JSONObject().put("km", a.km).put("d", a.d31).put("t", a.timeMs).toString()) } catch (_: Exception) {} }

    // ---- charging log: one row per drive (voltage at idle and while moving)
    class ChargeRecord(val timeMs: Long, val idleAvg: Double?, val idleMin: Double?, val driveAvg: Double?, val km: Double)
    fun loadCharge(): List<ChargeRecord> = readJson("charge.json").mapObjects {
        ChargeRecord(it.getLong("t"), it.optDoubleOrNull("ia"), it.optDoubleOrNull("im"), it.optDoubleOrNull("da"), it.optDouble("km", 0.0))
    }.sortedByDescending { it.timeMs }
    fun addCharge(c: ChargeRecord) {
        val all = listOf(c) + loadCharge()
        writeJson("charge.json", JSONArray().also { a -> all.take(100).forEach { r ->
            val o = JSONObject().put("t", r.timeMs).put("km", r.km)
            r.idleAvg?.let { o.put("ia", it) }; r.idleMin?.let { o.put("im", it) }; r.driveAvg?.let { o.put("da", it) }
            a.put(o)
        } })
    }

    // ---- Claude Lab conversation (kept between app starts; photos stored as files)
    class LabMsg(val timeMs: Long, val fromUser: Boolean, val text: String, val imagePath: String? = null, val withBundle: Boolean = false)
    fun labImagesDir(): File = File(ctx.filesDir, "lab_images").also { it.mkdirs() }
    fun loadLab(): List<LabMsg> = readJson("lab_chat.json").mapObjects {
        LabMsg(it.getLong("t"), it.optBoolean("u"), it.optString("x"), it.optString("img").ifBlank { null }, it.optBoolean("b"))
    }
    fun saveLab(list: List<LabMsg>) {
        writeJson("lab_chat.json", JSONArray().also { a -> list.takeLast(200).forEach { m ->
            a.put(JSONObject().put("t", m.timeMs).put("u", m.fromUser).put("x", m.text).put("img", m.imagePath ?: "").put("b", m.withBundle))
        } })
    }

    // ---- scan history
    private fun faultJson(f: Fault) = JSONObject().put("c", f.code).put("m", f.module).put("k", f.kind).put("s", f.status).put("r", f.raw)
    private fun faultOf(o: JSONObject) = Fault(o.getString("c"), o.optString("m"), o.optString("k", "stored"), o.optString("s"), o.optString("r"))

    fun loadScans(): List<ScanRecord> = readJson("scans.json").mapObjects { o ->
        ScanRecord(
            o.getLong("t"), o.optInt("km"), if (o.has("mil")) o.getBoolean("mil") else null,
            o.getJSONArray("f").mapObjects(::faultOf), if (o.has("v")) o.getDouble("v") else null, o.optString("n"),
        )
    }

    fun addScan(s: ScanRecord) {
        val all = (listOf(s) + loadScans()).take(60)
        val a = JSONArray()
        for (r in all) {
            val o = JSONObject().put("t", r.timeMs).put("km", r.odometerKm).put("n", r.notes)
            r.milOn?.let { o.put("mil", it) }; r.voltage?.let { o.put("v", it) }
            o.put("f", JSONArray().also { fa -> r.faults.forEach { fa.put(faultJson(it)) } })
            a.put(o)
        }
        writeJson("scans.json", a)
    }

    // ---- trips
    private fun tripJson(t: TripRecord) = JSONObject().put("t", t.startMs).put("s", t.seconds).put("km", t.km).put("l", t.liters).put("max", t.maxKmh).put("sc", t.score)

    fun loadTrips(): List<TripRecord> = readJson("trips.json").mapObjects { TripRecord(it.getLong("t"), it.getDouble("s"), it.getDouble("km"), it.getDouble("l"), it.getDouble("max"), it.optInt("sc", -1)) }

    fun addTrip(t: TripRecord) {
        val all = (listOf(t) + loadTrips()).take(300)
        writeJson("trips.json", JSONArray().also { a -> all.forEach { a.put(tripJson(it)) } })
    }

    fun deleteTrip(startMs: Long) {
        writeJson("trips.json", JSONArray().also { a -> loadTrips().filter { it.startMs != startMs }.forEach { a.put(tripJson(it)) } })
    }

    // ---- battery tests
    /**
     * Older versions saved one row per "Save" press, so one test showed up 4 times with more values each time. Rows less than
     * 15 minutes apart are merged into one test (newest value of each step wins).
     */
    fun loadBattery(): List<BatteryRecord> {
        val rows = readJson("battery.json").mapObjects {
            BatteryRecord(it.getLong("t"), it.optDoubleOrNull("a"), it.optDoubleOrNull("b"), it.optDoubleOrNull("c"), it.optDoubleOrNull("d"), it.optDoubleOrNull("e"), it.optInt("km"))
        }.sortedByDescending { it.timeMs }
        val out = ArrayList<BatteryRecord>()
        for (r in rows) {
            val last = out.lastOrNull()
            if (last != null && last.timeMs - r.timeMs <= 15 * 60_000L) {
                out[out.size - 1] = BatteryRecord(last.timeMs, last.rest ?: r.rest, last.crank ?: r.crank, last.idle ?: r.idle, last.rev ?: r.rev, last.load ?: r.load, maxOf(last.km, r.km))
            } else out.add(r)
        }
        return out
    }

    private fun writeBattery(all: List<BatteryRecord>) {
        writeJson("battery.json", JSONArray().also { a ->
            all.take(60).forEach { r ->
                val o = JSONObject().put("t", r.timeMs).put("km", r.km)
                r.rest?.let { o.put("a", it) }; r.crank?.let { o.put("b", it) }; r.idle?.let { o.put("c", it) }; r.rev?.let { o.put("d", it) }; r.load?.let { o.put("e", it) }
                a.put(o)
            }
        })
    }

    /** Saves a test; a test with the same start time is replaced (one row per test, updated after every step). */
    fun addBattery(b: BatteryRecord) = writeBattery(listOf(b) + loadBattery().filter { it.timeMs != b.timeMs && Math.abs(it.timeMs - b.timeMs) > 15 * 60_000L })

    fun deleteBattery(timeMs: Long) = writeBattery(loadBattery().filter { it.timeMs != timeMs })

    private fun JSONObject.optDoubleOrNull(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null

    // ---- maintenance (the list is the defaults, with your own intervals and last-done values on top)
    fun loadMaintenance(): List<MaintItem> {
        val saved = readJson("maint.json").mapObjects { o ->
            MaintItem(o.getString("id"), o.getString("name"), o.getInt("km"), o.getInt("mo"), o.optInt("lastKm"), o.optLong("lastMs"), o.optString("note"))
        }
        if (saved.isEmpty()) return Maintenance.defaults()
        return saved
    }

    fun saveMaintenance(items: List<MaintItem>) {
        writeJson("maint.json", JSONArray().also { a -> items.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("km", it.everyKm).put("mo", it.everyMonths).put("lastKm", it.lastKm).put("lastMs", it.lastDateMs).put("note", it.note)) } })
    }

    // ---- AI history
    fun loadAi(): List<AiRecord> = readJson("ai.json").mapObjects { AiRecord(it.getLong("t"), it.optString("title"), it.optString("text")) }

    fun addAi(r: AiRecord) {
        val all = (listOf(r) + loadAi()).take(30)
        writeJson("ai.json", JSONArray().also { a -> all.forEach { a.put(JSONObject().put("t", it.timeMs).put("title", it.title).put("text", it.text)) } })
    }


    // ---- repairs and expenses
    fun loadRepairs(): List<RepairRecord> = readJson("repairs.json").mapObjects { o ->
        RepairRecord(o.getString("id"), o.getLong("t"), o.optString("title"), o.optString("cat", "Other"), o.optDouble("cost", 0.0), o.optInt("km"), o.optString("shop"), o.optString("notes"), o.optString("receipt"))
    }.sortedByDescending { it.timeMs }

    fun saveRepairs(list: List<RepairRecord>) {
        writeJson("repairs.json", JSONArray().also { a -> list.forEach { a.put(JSONObject().put("id", it.id).put("t", it.timeMs).put("title", it.title).put("cat", it.category).put("cost", it.cost).put("km", it.km).put("shop", it.workshop).put("notes", it.notes).put("receipt", it.receipt)) } })
    }

    fun receiptsDir(): File = File(ctx.filesDir, "receipts").also { it.mkdirs() }
    fun routesDir(): File = File(ctx.filesDir, "routes").also { it.mkdirs() }

    // ---- things that happened (refuelled, car wash, battery disconnected, warning light on ...), used to link events together
    fun loadEvents(): List<LoggedEvent> = readJson("events.json").mapObjects { LoggedEvent(it.getString("id"), it.getLong("t"), it.optString("type", "note"), it.optString("title"), it.optString("detail"), it.optInt("km")) }.sortedByDescending { it.timeMs }

    fun saveEvents(list: List<LoggedEvent>) {
        writeJson("events.json", JSONArray().also { a -> list.takeLast(400).forEach { a.put(JSONObject().put("id", it.id).put("t", it.timeMs).put("type", it.type).put("title", it.title).put("detail", it.detail).put("km", it.km)) } })
    }

    // ---- fuel log
    fun loadFuel(): List<FuelEntry> = readJson("fuel.json").mapObjects { FuelEntry(it.getString("id"), it.getLong("t"), it.getInt("km"), it.getDouble("l"), it.optDouble("c", 0.0), it.optBoolean("full", true), it.optString("st")) }.sortedByDescending { it.km }

    fun saveFuel(list: List<FuelEntry>) {
        writeJson("fuel.json", JSONArray().also { a -> list.takeLast(600).forEach { a.put(JSONObject().put("id", it.id).put("t", it.timeMs).put("km", it.km).put("l", it.liters).put("c", it.cost).put("full", it.full).put("st", it.station)) } })
    }

    // ---- parts register (installed parts and their warranty)
    fun loadPartsReg(): List<PartRecord> = readJson("parts.json").mapObjects { PartRecord(it.getString("id"), it.optString("name"), it.optLong("t"), it.optInt("km"), it.optInt("wm"), it.optInt("wk"), it.optString("notes")) }.sortedByDescending { it.installedMs }

    fun savePartsReg(list: List<PartRecord>) {
        writeJson("parts.json", JSONArray().also { a -> list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("t", it.installedMs).put("km", it.km).put("wm", it.warrantyMonths).put("wk", it.warrantyKm).put("notes", it.notes)) } })
    }

    // ---- where the car was parked
    fun loadParking(): ParkingSpot? {
        val p = (prefs.getString("parking", "") ?: "").split("|")
        if (p.size < 4) return null
        return ParkingSpot(p[0].toDoubleOrNull() ?: return null, p[1].toDoubleOrNull() ?: return null, p[2].toLongOrNull() ?: return null, p[3].toIntOrNull() ?: 0)
    }

    fun saveParking(s: ParkingSpot) { prefs.edit().putString("parking", "${s.lat}|${s.lon}|${s.timeMs}|${s.accuracyM}").apply() }

    // ---- ticked checklist items (pre-trip resets daily, summer checks monthly)
    fun loadChecked(kind: String): Set<String> {
        val key = if (kind == "summer") Docs.formatDate(System.currentTimeMillis()).substring(0, 7) else Docs.formatDate(System.currentTimeMillis())
        val v = prefs.getString("chk_$kind", "") ?: ""
        return if (v.startsWith("$key|")) v.substringAfter('|').split(",").filter { it.isNotBlank() }.toSet() else emptySet()
    }

    fun saveChecked(kind: String, ids: Set<String>) {
        val key = if (kind == "summer") Docs.formatDate(System.currentTimeMillis()).substring(0, 7) else Docs.formatDate(System.currentTimeMillis())
        prefs.edit().putString("chk_$kind", key + "|" + ids.joinToString(",")).apply()
    }

    // ---- registration / insurance dates
    fun loadDocs(): List<DocReminder> {
        val saved = readJson("docs.json").mapObjects { DocReminder(it.getString("id"), it.getString("name"), it.getLong("due")) }
        return if (saved.isEmpty()) Docs.defaults() else saved
    }

    fun saveDocs(list: List<DocReminder>) {
        writeJson("docs.json", JSONArray().also { a -> list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("due", it.dueMs)) } })
    }

    // ---- health check history (for the trend graphs)
    fun loadHealth(): List<HealthRecord> = readJson("health.json").mapObjects { o ->
        val v = o.getJSONObject("v"); val m = LinkedHashMap<String, Double>()
        for (k in v.keys()) m[k] = v.getDouble(k)
        HealthRecord(o.getLong("t"), m)
    }

    fun addHealth(r: HealthRecord) {
        val all = (loadHealth() + r).sortedBy { it.timeMs }.takeLast(120)
        writeJson("health.json", JSONArray().also { a -> all.forEach { h -> a.put(JSONObject().put("t", h.timeMs).put("v", JSONObject().also { o -> h.values.forEach { (k, v) -> o.put(k, v) } })) } })
    }

    // ---- backup: everything except recordings, in one text file
    private val backupFiles = listOf("car.json", "scans.json", "trips.json", "battery.json", "maint.json", "ai.json", "repairs.json", "docs.json", "health.json", "events.json", "fuel.json", "parts.json", "providers.json", "odo.json", "charge.json", "lab_chat.json")

    fun exportAll(includeKeys: Boolean): String {
        val root = JSONObject().put("app", "obdy").put("version", 1)
        val files = JSONObject()
        for (n in backupFiles) {
            val f = file(n)
            if (!f.exists()) continue
            var text = f.readText()
            if (n == "providers.json" && !includeKeys) {
                val arr = JSONArray(text)
                for (i in 0 until arr.length()) arr.getJSONObject(i).put("key", "")
                text = arr.toString()
            }
            files.put(n, text)
        }
        root.put("files", files)
        root.put("prefs", JSONObject().put("theme", themeKey).put("livePids", livePids.joinToString(",")).put("symptoms", symptoms).put("wifiHost", wifiHost).put("wifiPort", wifiPort).put("ignored", loadIgnored().joinToString(",")).put("autoScan", autoScan).put("deep06", deepMode06).put("odoSpec", odoSpec))
        return root.toString()
    }

    /** Returns how many parts were restored, or -1 if the text is not an Obdy backup. */
    fun importAll(text: String): Int {
        val root = try { JSONObject(text) } catch (e: Exception) { return -1 }
        if (root.optString("app") != "obdy") return -1
        var n = 0
        val files = root.optJSONObject("files")
        if (files != null) for (name in backupFiles) {
            if (!files.has(name)) continue
            if (name == "providers.json") {
                // keep the keys that are already on this phone when the backup has none
                val old = loadProviders().associateBy { it.id }
                val arr = JSONArray(files.getString(name))
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); if (o.optString("key").isBlank()) old[o.optString("id")]?.let { o.put("key", it.key) } }
                file(name).writeText(arr.toString())
            } else file(name).writeText(files.getString(name))
            n++
        }
        root.optJSONObject("prefs")?.let { p ->
            if (p.has("theme")) themeKey = p.getString("theme")
            if (p.has("livePids")) prefs.edit().putString("livePids", p.getString("livePids")).apply()
            if (p.has("symptoms")) symptoms = p.getString("symptoms")
            if (p.has("wifiHost")) wifiHost = p.getString("wifiHost")
            if (p.has("wifiPort")) wifiPort = p.getInt("wifiPort")
            if (p.has("autoScan")) autoScan = p.getBoolean("autoScan")
            if (p.has("deep06")) deepMode06 = p.getBoolean("deep06")
            if (p.has("odoSpec")) odoSpec = p.getString("odoSpec")
            if (p.has("ignored")) saveIgnored(p.getString("ignored").split(",").filter { it.isNotBlank() }.toSet())
            n++
        }
        return n
    }

    // ---- recordings (CSV files)
    private fun recDir(): File = File(ctx.filesDir, "recordings").also { it.mkdirs() }

    fun saveRecording(r: Recording): File {
        val f = File(recDir(), "rec_${r.startedMs}.csv")
        try { f.writeText(r.toCsv()) } catch (_: Exception) {}
        return f
    }

    fun listRecordings(): List<Pair<File, Recording>> =
        (recDir().listFiles { f -> f.name.endsWith(".csv") } ?: emptyArray()).sortedByDescending { it.name }
            .mapNotNull { f -> try { Recording.fromCsv(f.readText())?.let { f to it } } catch (e: Exception) { null } }

    fun deleteRecording(f: File) { try { f.delete() } catch (_: Exception) {} }
}
