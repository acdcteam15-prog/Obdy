package com.example.obdy.ui

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.obdy.AppLog
import com.example.obdy.BatteryTest
import com.example.obdy.BleScanner
import com.example.obdy.BuildConfig
import com.example.obdy.Calc
import com.example.obdy.Verdict
import androidx.compose.ui.text.font.FontWeight
import com.example.obdy.Conn
import com.example.obdy.DtcDb
import com.example.obdy.Fuses
import com.example.obdy.Gm
import com.example.obdy.ObdyVm
import com.example.obdy.Recording
import com.example.obdy.UsbHost
import com.example.obdy.deviceName
import kotlinx.coroutines.delay

// ====================================================================================== Connect
@Composable
fun ConnectScreen(vm: ObdyVm, askPermissions: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    val scanner = remember { BleScanner() }
    val found = remember { mutableStateListOf<Pair<BluetoothDevice, Int>>() }
    var host by remember { mutableStateOf(vm.wifiHost) }
    var port by remember { mutableStateOf(vm.wifiPort) }
    DisposableEffect(Unit) { onDispose { scanner.stop() } }
    LaunchedEffect(scanning) { if (scanning) { delay(10_000); scanner.stop(); scanning = false } }

    ScreenFrame("Connect", { vm.overlay = null }) {
        Panel {
            Title("Which adapter?")
            Hint("You need an OBD-II adapter in the car's port (under the dashboard, driver side). Good choices that work with Obdy:")
            Hint("• OBDLink MX+ (Bluetooth): the most capable. Also reads GM single-wire CAN. Pair it in Android settings.")
            Hint("• vLinker MC+ (Bluetooth classic + LE): best value, compact, fast.")
            Hint("• vLinker FS / MS (Bluetooth): fast, good build.")
            Hint("• Veepeak OBDCheck BLE: cheap and reliable for engine codes and live data (LE: use the LE search below).")
            Hint("• OBDLink EX or vLinker FS USB: wired, no pairing.")
            Hint("Avoid unbranded \"ELM327 v2.1\" clones: most are fake and fail on GM modules. BlueDriver only works with its own app, not Obdy.")
            Hint("Turn the ignition ON before connecting. The engine can stay off.")
            SoftButton("Adapter has no power? Check the OBD port fuse", Modifier.fillMaxWidth()) { Fuses.find("panel", "Mini fuse", "14")?.let { vm.openFuse(it) } }
            if (vm.conn != Conn.Off) Label(vm.connStatus, bold = true)
        }
        Panel {
            Title("Bluetooth adapters you have paired")
            if (!vm.btPermission) {
                Hint("Android needs your permission to see Bluetooth devices.")
                GradientButton("Allow Bluetooth", Brand.MainBrush) { askPermissions() }
            } else {
                val list = remember(refresh, vm.btPermission) { vm.pairedDevices() }
                if (list.isEmpty()) Hint("No paired devices. Pair the adapter first in Android's Bluetooth settings (PIN 1234 or 0000), then come back and press Refresh.")
                for (d in list) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Label(deviceName(d), bold = true); Hint(d.address) }
                        SoftButton("Connect") { vm.connectClassic(d); vm.overlay = null }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Refresh", Modifier.weight(1f)) { refresh++ }
                    SoftButton("Pair a new adapter", Modifier.weight(1f)) { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        Panel {
            Title("Bluetooth LE adapters")
            Hint("For adapters that do not show in the paired list (LE type, do not pair in Android settings).")
            if (!vm.btPermission) GradientButton("Allow Bluetooth", Brand.MainBrush) { askPermissions() }
            else {
                SoftButton(if (scanning) "Searching..." else "Search for adapters", enabled = !scanning) {
                    found.clear(); scanning = scanner.start { d, rssi -> if (found.none { it.first.address == d.address }) found.add(d to rssi) }
                    if (!scanning) vm.say("Could not start the search. Is Bluetooth on?")
                }
                for ((d, rssi) in found.sortedWith(compareByDescending<Pair<BluetoothDevice, Int>> { it.first.address == vm.lastAdapterAddress() }.thenByDescending { it.second }).take(12)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Label(deviceName(d) + (if (d.address == vm.lastAdapterAddress()) "  (your car adapter)" else ""), bold = true); Hint("${d.address}  ·  signal $rssi") }
                        SoftButton("Connect") { scanner.stop(); scanning = false; vm.connectBle(d); vm.overlay = null }
                    }
                }
            }
        }
        Panel {
            Title("USB cable")
            Hint("A wired OBD cable (ELM327 USB, OBDLink EX, vLinker FS USB...) on the phone's USB port, with a USB-OTG adapter if the phone needs one. No pairing and no Bluetooth dropouts. The speed is found automatically.")
            val usb = remember(refresh) { vm.usbDrivers() }
            if (usb.isEmpty()) Hint("No USB cable detected. Plug it into the phone, then press Refresh. If nothing shows, the cable may use a chip Android does not recognise, or the phone may not support USB host mode.")
            for (d in usb) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Label(UsbHost.label(d), bold = true); Hint("Android asks permission the first time") }
                    SoftButton("Connect") { vm.connectUsb(d); vm.overlay = null }
                }
            }
            SoftButton("Refresh", Modifier.fillMaxWidth()) { refresh++ }
        }
        Panel {
            Title("Wi-Fi adapter")
            Hint("First join the adapter's own Wi-Fi network in Android's Wi-Fi settings (it has no internet; keep it connected if Android asks). Most use 192.168.0.10 port 35000.")
            Field("Address", host, { host = it })
            Field("Port", port, { port = it }, number = true)
            GradientButton("Connect over Wi-Fi", Brand.AltBrush) { vm.saveWifi(host, port); vm.connectWifi(); vm.overlay = null }
        }
    }
}

// ====================================================================================== Settings
@Composable
fun UpdatesPanel(vm: ObdyVm) {
    val ready = vm.updateReady
    val latest = vm.updateLatest
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { AppIcon(Ic.Bolt, size = 22.dp, color = Brand.Accent); Title("Updates") }
        Label("Installed: ${BuildConfig.VERSION_LABEL}")
        Hint("Updates come from GitHub: ${BuildConfig.UPDATE_REPO}")
        if (ready != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Ready to install", Brand.Green)
                Label("Build ${vm.updateReadyCode}  ·  " + Calc.fmt(ready.length() / 1048576.0, 1) + " MB", size = 13)
            }
            Hint("Already downloaded and saved on this phone. Install whenever you like, even without internet.")
            GradientButton("Install now", Brand.GoodBrush) { vm.installUpdate() }
            val notes = latest?.notes
            if (!notes.isNullOrBlank()) { Label("What's new", bold = true, size = 13); Hint(notes.take(500)) }
        } else if (latest != null) {
            Label("Update found: build ${latest.code}" + (if (latest.published.isNotBlank()) "  (${latest.published})" else ""), color = Brand.Amber, bold = true)
            GradientButton(if (vm.updateBusy) "Downloading..." else "Download now", Brand.MainBrush, enabled = !vm.updateBusy) { vm.downloadUpdate() }
        }
        if (vm.updateProgress >= 0) { ProgressBar(vm.updateProgress / 100f); Hint("${vm.updateProgress}%") }
        if (vm.updateText.isNotBlank()) Label(vm.updateText, size = 13, color = Brand.Accent)
        SoftButton(if (vm.updateBusy) "Please wait..." else "Check for updates", Modifier.fillMaxWidth(), enabled = !vm.updateBusy) { vm.checkUpdate() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.autoUpdate, onCheckedChange = { vm.changeAutoUpdate(it) }); Spacer(Modifier.width(8.dp))
            Column { Label("Find and download updates by itself", bold = true, size = 14); Hint("Checks when the app opens, then keeps the download ready.") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.wifiOnlyUpdate, onCheckedChange = { vm.changeWifiOnly(it) }); Spacer(Modifier.width(8.dp))
            Column { Label("Download only on Wi-Fi", bold = true, size = 14); Hint("Saves mobile data. Pressing the button always downloads.") }
        }
        if (vm.lastUpdateCheck > 0) Hint("Last checked ${fmtDate(vm.lastUpdateCheck)}")
    }
}

/** Settings section header: tinted icon and title. */
@Composable
fun SectionHead(ic: Ic, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Brand.Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { AppIcon(ic, size = 17.dp, color = Brand.Accent) }
        Spacer(Modifier.width(10.dp)); Title(title)
    }
}

@Composable
fun SettingsScreen(vm: ObdyVm) {
    val ctx = LocalContext.current
    ScreenFrame("Settings", { vm.overlay = null }, subtitle = "Everything in one place") {
        UpdatesPanel(vm)
        Panel {
            SectionHead(Ic.Search, "Scanning")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.autoScan, onCheckedChange = { vm.changeAutoScan(it) }); Spacer(Modifier.width(8.dp))
                Column { Label("Quick scan every time I connect", bold = true); Hint("Notifies you only when a NEW code appears.") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.deepMode06, onCheckedChange = { vm.changeDeepMode06(it) }); Spacer(Modifier.width(8.dp))
                Column { Label("Deep scan reads engine self-tests too", bold = true); Hint("Misfire counts, catalyst and O2 sensor results (adds about 10 s).") }
            }
        }
        Panel {
            SectionHead(Ic.Gauge, "Mileage source")
            Hint("Now: ${vm.odoSource.label}. Type the dash mileage once in the Car tab; Obdy then follows the car's own distance counter.")
            OdoFinderPanel(vm)
        }
        Panel {
            SectionHead(Ic.Heart, "Look")
            for (p in Palettes.all) {
                Row(Modifier.fillMaxWidth().clickable { vm.applyTheme(p.key) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(p.accent))
                    Spacer(Modifier.width(4.dp)); Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(p.card))
                    Spacer(Modifier.width(10.dp))
                    Label(p.name, bold = vm.themeKey == p.key)
                    if (vm.themeKey == p.key) { Spacer(Modifier.width(8.dp)); AppIcon(Ic.Check, size = 18.dp, color = Brand.Accent) }
                }
            }
        }
        Panel {
            SectionHead(Ic.Plug, "Connection and screen")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.autoConnect, onCheckedChange = { vm.changeAutoConnect(it) }); Spacer(Modifier.width(8.dp))
                Column { Label("Connect automatically", bold = true); Hint("Uses the last adapter you connected to. When the app opens, and when the adapter was not ready, it keeps trying by itself until the car answers. The Disconnect button pauses it until you connect or reopen the app.") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.autoWait, onCheckedChange = { vm.changeAutoWait(it) }); Spacer(Modifier.width(8.dp))
                Column { Label("Keep waiting for the car in the background", bold = true); Hint("Shows one quiet notification so Android does not stop the app while it waits. Turn off to save battery; then Obdy connects only while the app is open.") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.keepScreenOn, onCheckedChange = { vm.changeKeepScreen(it) }); Spacer(Modifier.width(8.dp))
                Column { Label("Keep the screen on while working", bold = true); Hint("Only while connected and showing live data, a trip, a recording, a scan or a test. Without a connection, or on other screens, the screen turns off normally.") }
            }
        }
        Panel {
            SectionHead(Ic.Chart, "Live data")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.showExt, onCheckedChange = { vm.showExt = it }); Spacer(Modifier.width(8.dp))
                Column { Label("GM extras (experimental)", bold = true); Hint("Transmission fluid temperature on the Live screen. (Oil life was removed: that value turned out to be a fast counter, not oil life. Read it on the dash.) Shown only if the module answers.") }
            }
        }
        SmartSettingsPanels(vm)
        ExtraSettingsPanels(vm)
        ProvidersPanel(vm)
        FullLogPanel(vm)
    }
}

// ====================================================================================== Battery
@Composable
fun BatteryScreen(vm: ObdyVm) {
    val on = vm.conn == Conn.On
    val free = on && vm.batBusy == null
    ScreenFrame("Battery and charging", { vm.overlay = null }) {
        // ---- live voltage hero
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Voltage now", color = Brand.TextSecondary, fontSize = 12.sp)
                    val v = vm.batLive
                    Text(if (v != null) "${Calc.fmt(v, 2)} V" else "--", color = v?.let { voltColor(it) } ?: Brand.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                if (on) SoftButton("Read", enabled = free) { vm.refreshVoltage() }
            }
            if (!on) { Label("Connect to the car first.", color = Brand.Amber); SoftButton("Connect") { vm.overlay = "connect" } }
            vm.batBusy?.let {
                Label(it, bold = true, color = Brand.Amber)
                ProgressBar(vm.batFraction)
            }
            if (vm.batSamples.size >= 3) {
                val pts = vm.batSamples.mapIndexed { i, x -> i.toDouble() to x }
                LineChart(listOf(ChartSeries("V", pts, Brand.Accent)), height = 90)
                Hint("Lowest ${Calc.fmt(vm.batSamples.min(), 2)} V  ·  highest ${Calc.fmt(vm.batSamples.max(), 2)} V  ·  ${vm.batSamples.size} readings")
            }
            if (vm.batMsg.isNotBlank()) Hint(vm.batMsg)
        }

        // ---- overall result
        val summary = BatteryTest.summary(vm.batRest, vm.batCrank, vm.batIdle, vm.batRev, vm.batLoad)
        if (summary != null) Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(Ic.Battery, size = 24.dp, color = levelColor(summary.level)); Title(summary.title)
            }
            Hint(summary.text)
            vm.batRest?.takeIf { it <= 12.85 }?.let { Label("Battery charge about ${BatteryTest.chargePercent(it)} %", size = 13) }
        }

        // ---- steps
        val verdicts = BatteryTest.evaluate(vm.batRest, vm.batCrank, vm.batIdle, vm.batRev, vm.batLoad).associateBy { it.title }
        BatStep(1, "Resting voltage", "Ignition ON, engine off, lights and A/C off. Best after the car stood 2 hours.", vm.batRest, verdicts["Resting voltage"], vm.batStepNow == 1, free) { vm.batteryStep(1) }
        BatStep(2, "Starting dip", "Press Measure, then START THE ENGINE within a few seconds. Some adapters restart while cranking: then skip this step.", vm.batCrank, verdicts["Lowest voltage while cranking"], vm.batStepNow == 2, free) { vm.batteryStep(2) }
        BatStep(3, "Charging at idle", "Engine idling, lights and A/C off.", vm.batIdle, verdicts["Charging at idle"], vm.batStepNow == 3, free) { vm.batteryStep(3) }
        BatStep(4, "Charging at 2000 rpm", "Hold about 2000 rpm for 6 seconds (another person helps).", vm.batRev, verdicts["Charging at 2000 rpm"], vm.batStepNow == 4, free) { vm.batteryStep(4) }
        BatStep(5, "Charging under load", "Engine idling with headlights, A/C on full and rear demister ON. This is the situation of your night-time low voltage.", vm.batLoad, verdicts["Charging under load"], vm.batStepNow == 5, free) { vm.batteryStep(5) }
        verdicts["Drop with load"]?.let { Panel { Label("${it.title}: ${it.value}", bold = true, color = levelColor(it.level)); Hint(it.text) } }
        if (vm.batRest != null || vm.batCrank != null || vm.batIdle != null || vm.batRev != null || vm.batLoad != null) {
            Hint("Each step is saved automatically into one test.")
            SoftButton("Start a new test", Modifier.fillMaxWidth()) { vm.batteryReset() }
        }

        BatteryHealthCard(vm)

        // ---- charging on every drive (logged automatically while a trip runs)
        Panel {
            Title("Charging on your drives")
            val log = vm.chargeLog
            if (log.isEmpty()) Hint("Start a trip on the Live tab: Obdy logs the voltage at idle and while moving, and saves one line per drive. This catches a charging problem that only shows at night or with A/C on.")
            else {
                val idle = log.mapNotNull { r -> r.idleAvg?.let { r.timeMs / 86_400_000.0 to it } }.reversed()
                val drive = log.mapNotNull { r -> r.driveAvg?.let { r.timeMs / 86_400_000.0 to it } }.reversed()
                if (idle.size >= 2 || drive.size >= 2) {
                    LineChart(listOfNotNull(idle.takeIf { it.size >= 2 }?.let { ChartSeries("Idle", it, Brand.Amber) }, drive.takeIf { it.size >= 2 }?.let { ChartSeries("Driving", it, Brand.Green) }), height = 110, shared = true)
                    Legend(listOf("Idle" to Brand.Amber, "Driving" to Brand.Green))
                }
                for (r in log.take(6)) Row {
                    Text(fmtDate(r.timeMs), color = Brand.TextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1.5f))
                    Text("idle " + (r.idleAvg?.let { Calc.fmt(it, 2) } ?: "–"), color = r.idleMin?.let { if (it < 12.8) Brand.Red else Brand.Green } ?: Brand.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("low " + (r.idleMin?.let { Calc.fmt(it, 2) } ?: "–"), color = Brand.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("drive " + (r.driveAvg?.let { Calc.fmt(it, 2) } ?: "–"), color = Brand.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
                val worst = log.take(10).mapNotNull { it.idleMin }.minOrNull()
                if (worst != null && worst < 12.8) Label("Lowest idle voltage on recent drives: ${Calc.fmt(worst, 2)} V. Do step 5 (under load) and check belt and terminals.", color = Brand.Red, size = 13)
            }
        }

        // ---- history
        if (vm.batHistory.isNotEmpty()) Panel {
            Title("Earlier tests")
            val crank = vm.batHistory.mapNotNull { b -> b.crank?.let { b.timeMs / 86_400_000.0 to it } }.reversed()
            val idle = vm.batHistory.mapNotNull { b -> (b.idle ?: b.rev)?.let { b.timeMs / 86_400_000.0 to it } }.reversed()
            if (crank.size >= 2 || idle.size >= 2) {
                LineChart(listOfNotNull(crank.takeIf { it.size >= 2 }?.let { ChartSeries("Cranking", it, Brand.Amber) }, idle.takeIf { it.size >= 2 }?.let { ChartSeries("Charging", it, Brand.Green) }), height = 110)
                Legend(listOf("Cranking" to Brand.Amber, "Charging" to Brand.Green))
            }
            Row(Modifier.padding(top = 4.dp)) {
                Text("Date", color = Brand.TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1.6f))
                for (h in listOf("Rest", "Crank", "Idle", "2000", "Load")) Text(h, color = Brand.TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(28.dp))
            }
            for (b in vm.batHistory.take(10)) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmtDate(b.timeMs), color = Brand.TextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1.6f))
                for ((x, kind) in listOf(b.rest to 1, b.crank to 2, b.idle to 3, b.rev to 3, b.load to 5))
                    Text(x?.let { Calc.fmt(it, 2) } ?: "–", color = x?.let { stepColor(kind, it) } ?: Brand.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconTap(Ic.Close) { vm.deleteBattery(b) }
            }
        }
    }
}

private fun voltColor(v: Double): androidx.compose.ui.graphics.Color = when {
    v < 11.8 || v > 15.0 -> Brand.Red; v < 12.4 -> Brand.Amber; else -> Brand.Green
}

/** Colour of a saved value: 1 rest, 2 crank, 3 charging, 5 under load. */
private fun stepColor(kind: Int, v: Double): androidx.compose.ui.graphics.Color = when (kind) {
    1 -> if (v >= 12.5) Brand.Green else if (v >= 12.2) Brand.Amber else Brand.Red
    2 -> if (v >= 9.6) Brand.Green else if (v >= 9.0) Brand.Amber else Brand.Red
    3 -> if (v in 13.2..14.9) Brand.Green else if (v in 13.0..15.0) Brand.Amber else Brand.Red
    else -> if (v >= 13.2) Brand.Green else if (v >= 12.8) Brand.Amber else Brand.Red
}

@Composable
private fun BatStep(n: Int, title: String, text: String, value: Double?, verdict: Verdict?, running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val col = when {
        running -> Brand.Accent
        verdict != null -> levelColor(verdict.level)
        else -> Brand.TextSecondary
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(col.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(if (value != null && !running) "✓" else "$n", color = col, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Label(title, bold = true)
                if (value != null) Text("${Calc.fmt(value, 2)} V", color = col, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            SoftButton(if (running) "..." else if (value == null) "Measure" else "Again", enabled = enabled, onClick = onClick)
        }
        Hint(text)
        if (verdict != null && !running) Text(verdict.text, color = col, fontSize = 12.sp)
    }
}

// ====================================================================================== Trips and fuel
@Composable
fun TripsScreen(vm: ObdyVm) {
    ScreenFrame("Fuel and trips", { vm.overlay = null }) {
        // Fill-ups (real litres from the pump) and trips (worked out from the engine) live together here.
        Panel(Modifier.clickable { vm.overlay = "fuellog" }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(Ic.Fuel, size = 22.dp, color = Brand.Accent)
                Column(Modifier.weight(1f)) { Title("Fill-up log"); Hint("Real L/100 km and cost from your fill-ups (${vm.fuel.size} saved)") }
                Text("›", color = Brand.TextSecondary, fontSize = 22.sp)
            }
        }
        Panel {
            Title(if (vm.tripOn) "Trip in progress" else "Start a trip")
            if (vm.tripOn) {
                Label("${Calc.fmt(vm.tripKm, 2)} km  ·  ${Calc.fmt(vm.tripLiters, 2)} L  ·  ${(vm.tripSeconds / 60).toInt()} min")
                Label("Average " + (vm.tripL100?.let { Calc.fmt(it, 1) + " L/100 km" } ?: "-"))
                GradientButton("End trip and save", Brand.DangerBrush) { vm.stopTrip() }
            } else {
                Hint("Fuel use is worked out from the air flow sensor (and the commanded mixture), so it is an estimate, usually within about 10 percent. Compare once with a full tank to the pump and note the difference.")
                GradientButton("Start trip", Brand.MainBrush, enabled = vm.conn == Conn.On) { vm.startTrip() }
                if (vm.conn != Conn.On) Hint("Connect to the car first.")
            }
        }
        if (vm.trips.isNotEmpty()) {
            val km = vm.trips.sumOf { it.km }; val l = vm.trips.sumOf { it.liters }
            Panel {
                Title("All saved trips")
                Label("${Calc.fmt(km, 1)} km  ·  ${Calc.fmt(l, 1)} L" + (if (km > 1) "  ·  ${Calc.fmt(l / km * 100, 1)} L/100 km" else ""))
                if (vm.car.fuelPrice > 0) Label("Fuel cost: ${Calc.fmt(l * vm.car.fuelPrice, 2)}", size = 13)
            }
            for (t in vm.trips.take(30)) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label(fmtDate(t.startMs), bold = true)
                            Label("${Calc.fmt(t.km, 1)} km  ·  ${Calc.fmt(t.liters, 2)} L  ·  " + (if (t.km >= 0.2) Calc.fmt(t.liters / t.km * 100, 1) + " L/100" else "-"), size = 13)
                            Hint("${(t.seconds / 60).toInt()} min  ·  top speed ${Calc.fmt(t.maxKmh, 0)} km/h" + (if (t.score >= 0) "  ·  driving score ${t.score}" else ""))
                            val rf = vm.routeFile(t)
                            if (rf != null) SoftButton("Share route (GPX)") { vm.shareFile(rf, "application/gpx+xml") }
                        }
                        SoftButton("Delete") { vm.deleteTrip(t) }
                    }
                }
            }
        } else Hint("No trips saved yet.")
    }
}

// ====================================================================================== Recordings
private val chartColors = listOf(Color(0xFFFFB020), Color(0xFF3CD2E8), Color(0xFFFF5A1F), Color(0xFF8BD450), Color(0xFFB388FF), Color(0xFFFF8AD8))

@Composable
fun RecordsScreen(vm: ObdyVm) {
    LaunchedEffect(Unit) { vm.loadRecordings() }
    BackHandler(enabled = vm.viewRec != null) { vm.viewRec = null; vm.compareRec = null }
    val r = vm.viewRec
    if (r == null) {
        ScreenFrame("Recordings", { vm.overlay = null }) {
            Panel {
                Title("Record a drive")
                Hint("On the Live screen press Record, drive, then press Stop. Compare two drives to see if a repair changed fuel trims, voltage or temperatures.")
                if (vm.recOn) { Label("Recording: ${vm.recRows} samples", color = Brand.Red, bold = true); GradientButton("Stop and save", Brand.DangerBrush) { vm.stopRecording() } }
                else GradientButton("Open Live screen", Brand.MainBrush) { vm.overlay = null; vm.tab = 1 }
            }
            if (vm.recordings.isEmpty()) Hint("No recordings yet.")
            for ((file, rec) in vm.recordings) {
                Panel {
                    Label(rec.name, bold = true)
                    Hint("${rec.rows.size} samples  ·  ${rec.columns.size} values  ·  ${(rec.times.lastOrNull() ?: 0.0).toInt()} s")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton("Graph", Modifier.weight(1f)) { vm.viewRec = rec; vm.compareRec = null }
                        SoftButton("Share", Modifier.weight(1f)) { vm.shareFile(file, "text/csv") }
                        SoftButton("Delete", Modifier.weight(1f)) { vm.deleteRecording(file) }
                    }
                }
            }
        }
    } else {
        RecordViewer(vm, r)
    }
}

@Composable
private fun RecordViewer(vm: ObdyVm, r: Recording) {
    var only by remember(r) { mutableStateOf(-1) }
    val other = vm.compareRec
    ScreenFrame(r.name, { vm.viewRec = null; vm.compareRec = null }) {
        if (other == null) {
            Panel {
                Title("Graph")
                val cols = if (only >= 0) listOf(only) else r.columns.indices.take(6).toList()
                LineChart(cols.map { ChartSeries(r.columns[it], r.series(it), chartColors[it % chartColors.size]) })
                Legend(cols.map { r.columns[it] to chartColors[it % chartColors.size] })
                Hint("Each line fills the height by itself. Pick one value to see it alone.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SoftButton("All") { only = -1 } }
                for (i in r.columns.indices) SoftButton(r.columns[i], Modifier.fillMaxWidth()) { only = i }
            }
            Panel {
                Title("Minimum, average, maximum")
                for (i in r.columns.indices) {
                    val s = r.stats(i) ?: continue
                    Label("${r.columns[i]} (${r.units[i]}): ${Calc.fmt(s.first, 1)} / ${Calc.fmt(s.second, 1)} / ${Calc.fmt(s.third, 1)}", size = 13)
                }
            }
            val others = vm.recordings.map { it.second }.filter { it.startedMs != r.startedMs }
            if (others.isNotEmpty()) Panel {
                Title("Compare with another drive")
                for (o in others.take(10)) SoftButton(o.name, Modifier.fillMaxWidth()) { vm.compareRec = o; only = 0 }
            }
        } else {
            val name = r.columns.getOrNull(maxOf(only, 0)) ?: ""
            val ia = r.columns.indexOf(name); val ib = other.columns.indexOf(name)
            Panel {
                Title("Compare: $name")
                if (ia < 0 || ib < 0) Hint("The other recording does not have this value.")
                else {
                    LineChart(listOf(ChartSeries(r.name, r.series(ia), chartColors[0]), ChartSeries(other.name, other.series(ib), chartColors[1])), shared = true, height = 220)
                    Legend(listOf(r.name to chartColors[0], other.name to chartColors[1]))
                    val a = r.stats(ia); val b = other.stats(ib)
                    if (a != null) Label("${r.name}: ${Calc.fmt(a.first, 1)} / ${Calc.fmt(a.second, 1)} / ${Calc.fmt(a.third, 1)}", size = 13)
                    if (b != null) Label("${other.name}: ${Calc.fmt(b.first, 1)} / ${Calc.fmt(b.second, 1)} / ${Calc.fmt(b.third, 1)}", size = 13)
                    Hint("Both lines use the same scale. Numbers are minimum / average / maximum.")
                }
                Hint("Pick another value:")
                for (i in r.columns.indices) SoftButton(r.columns[i], Modifier.fillMaxWidth()) { only = i }
                SoftButton("Stop comparing", Modifier.fillMaxWidth()) { vm.compareRec = null; only = -1 }
            }
        }
    }
}

// ====================================================================================== Relearn guides
@Composable
fun RelearnScreen(vm: ObdyVm) {
    ScreenFrame("Relearn guides", { vm.overlay = null }) {
        Hint("These are the usual manual procedures. Generic OBD adapters cannot run the factory scanner relearns.")
        for ((t, d) in Gm.relearns) Panel { Title(t); Label(d, size = 14) }
    }
}

// ====================================================================================== Console
@Composable
fun ConsoleScreen(vm: ObdyVm) {
    val ctx = LocalContext.current
    var cmd by remember { mutableStateOf("") }
    var header by remember { mutableStateOf("") }
    ScreenFrame("Console", { vm.overlay = null }) {
        Panel {
            Title("Send to the adapter")
            Hint("Examples: 0100 (supported values), 0902 (VIN), ATRV (voltage), ATDP (protocol). Header is optional: 7E0 = engine, 7E2 = transmission.")
            Field("Header (optional, 3 hex digits)", header, { header = it })
            Field("Command", cmd, { cmd = it })
            GradientButton(if (vm.consoleBusy) "Waiting..." else "Send", Brand.MainBrush, enabled = !vm.consoleBusy && vm.conn == Conn.On) { vm.consoleSend(header, cmd) }
            if (vm.conn != Conn.On) Hint("Connect to the car first.")
        }
        Panel {
            Title("Answers")
            if (vm.consoleOut.isEmpty()) Hint("Nothing yet.")
            SelectionContainer {
                Column { for (l in vm.consoleOut.takeLast(60)) Text(l, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Brand.TextPrimary) }
            }
            SoftButton("Clear") { vm.consoleOut.clear() }
        }
        FullLogPanel(vm)
    }
}

// ====================================================================================== AI analysis
private fun offlineNotes(vm: ObdyVm): List<String> {
    val n = ArrayList<String>()
    val f = vm.faults
    val v = vm.voltage
    if (f.count { it.code.startsWith("U") } >= 2) n.add("Several network (U) codes usually mean a power, ground or bus problem, not several broken modules. Run the Battery test first.")
    if (v != null && v < 12.0) n.add("Battery voltage is low (${Calc.fmt(v, 1)} V). Low voltage can create false codes in many modules. Charge or replace the battery, clear the codes, and scan again.")
    if (f.any { it.code in setOf("P0171", "P0174") } && f.any { it.code in setOf("P0300", "P0301", "P0302", "P0303", "P0304") }) n.add("Lean code together with misfire: look for a vacuum or intake leak first, then spark plugs and coils.")
    if (f.any { it.code.startsWith("P07") } && f.any { it.code == "P0700" }) n.add("P0700 only says the transmission module has its own code: read the P07xx codes with a Deep scan.")
    if (f.any { it.code.startsWith("P030") }) n.add("A flashing warning light with misfire means stop driving hard: unburnt fuel can destroy the catalytic converter.")
    return n
}

@Composable
fun AiScreen(vm: ObdyVm) {
    val ctx = LocalContext.current
    ScreenFrame("Analysis", { vm.overlay = null }) {
        Panel {
            Title("Built-in analysis (works offline)")
            if (vm.lastScanMs == 0L) Hint("Run a scan first (Faults tab). The analysis uses the codes, freeze frame, live values and your notes.")
            val notes = offlineNotes(vm)
            for (x in notes) Label("•  $x", size = 14)
            for (f in vm.faults.sortedByDescending { DtcDb.severity(it.code) }.take(8)) {
                Label("${f.code}: ${f.description}", bold = true, size = 14)
                for (c in DtcDb.causes(f.code).take(3)) Label("    - $c", size = 13)
            }
            if (vm.faults.isEmpty() && vm.lastScanMs > 0) Label("No codes to analyse.", color = Brand.Green)
        }
        Panel {
            Title("Deeper analysis with AI")
            Hint("Sends the car data above (codes, freeze frame, live values, notes) to the AI service you chose. Nothing is sent until you press a button.")
            val ap = vm.activeProvider
            if (ap == null) { Hint("Add an AI service in Settings first."); SoftButton("Open Settings") { vm.overlay = "settings" } }
            else Hint("Using: ${ap.name} (${ap.model})")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.aiWeb, onCheckedChange = { vm.aiWeb = it }); Spacer(Modifier.width(8.dp))
                Column { Label("Let it search Google", bold = true, size = 14); Hint("Gemini only. Finds known fixes and bulletins.") }
            }
            GradientButton(if (vm.aiBusy) "Thinking..." else "Analyse everything", Brand.MainBrush, enabled = !vm.aiBusy && ap != null) { vm.runAi(null) }
            SoftButton("Find the cause with questions", Modifier.fillMaxWidth()) { vm.gCode = null; vm.overlay = "guided" }
            SoftButton("Copy the data as text (paste it into Claude)") { copyText(ctx, vm.aiPrompt(null)); vm.say("Copied") }
            vm.aiError?.let { Label(it, color = Brand.Red, size = 13) }
        }
        val t = vm.aiText
        if (t != null) Panel {
            Title("AI answer")
            SelectionContainer { Text(t.replace("**", "").replace("##", ""), color = Brand.TextPrimary, fontSize = 14.sp) }
            FuseChips(vm, t)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Copy", Modifier.weight(1f)) { copyText(ctx, t); vm.say("Copied") }
                SoftButton("PDF report", Modifier.weight(1f)) { vm.makeReport() }
            }
            Hint("A guide, not a verdict: confirm with a workshop before replacing parts.")
        }
        if (vm.aiHistory.isNotEmpty()) Panel {
            Title("Earlier answers")
            for (h in vm.aiHistory.take(6)) SoftButton("${fmtDate(h.timeMs)}  ·  ${h.title}", Modifier.fillMaxWidth()) { vm.aiText = h.text }
        }
    }
}


/** Complete log on disk: every request, answer, scan result and live value since the first start, until deleted. */
@Composable
fun FullLogPanel(vm: ObdyVm) {
    var ask by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshFullLog() }
    Panel {
        Title("Full log")
        Hint("Everything Obdy does is saved here: what you pressed, every request sent to the adapter and every answer, scan results, every decoded value, every line of text the screens showed, every setting change, internet requests and crashes, each with its full date and time. It stays after you close the app. Share the file with me, then delete it to start clean.")
        val kb = vm.fullLogSize / 1024
        Label(if (kb < 1024) "$kb KB saved" else "${"%.1f".format(kb / 1024.0)} MB saved", bold = true, size = 13)
        SoftButton("Share the full log file", Modifier.fillMaxWidth(), enabled = vm.fullLogSize > 0) { vm.shareFullLog() }
        SoftButton("Refresh size", Modifier.fillMaxWidth()) { vm.refreshFullLog() }
        SoftButton("Archive the log and start a new one", Modifier.fillMaxWidth(), enabled = vm.fullLogSize > 0) { ask = true }
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("Archive the full log?") },
        text = { Text("The log so far (${vm.fullLogSize / 1024} KB) is kept in an archive file and a new log starts. Sharing the log sends the archive and the new log together.") },
        confirmButton = { TextButton(onClick = { ask = false; vm.deleteFullLog() }) { Text("Archive") } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("Cancel") } }
    )
}
