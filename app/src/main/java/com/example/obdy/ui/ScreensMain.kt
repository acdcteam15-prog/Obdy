package com.example.obdy.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.obdy.Calc
import com.example.obdy.Conn
import com.example.obdy.DtcDb
import com.example.obdy.Fault
import com.example.obdy.Gm
import com.example.obdy.ObdyVm
import com.example.obdy.Pids
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtDate(ms: Long): String = SimpleDateFormat("d MMM yyyy HH:mm", Locale.US).format(Date(ms))

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Obdy", text))
}

@Composable
fun HeaderButton(text: String, onClick: () -> Unit) {
    Text(text, color = Brand.Accent, fontSize = 22.sp, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Title bar (with a back arrow when [onBack] is given) and a scrolling column. */
@Composable
fun ScreenFrame(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Brand.Header).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) HeaderButton("‹", onBack) else Spacer(Modifier.width(8.dp))
            Text(title, color = Brand.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            actions()
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun ObdyRoot(vm: ObdyVm, askPermissions: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        val ov = vm.overlay
        if (ov != null) {
            BackHandler { vm.overlay = null }
            when (ov) {
                "connect" -> ConnectScreen(vm, askPermissions)
                "settings" -> SettingsScreen(vm)
                "battery" -> BatteryScreen(vm)
                "trips" -> TripsScreen(vm)
                "records" -> RecordsScreen(vm)
                "relearn" -> RelearnScreen(vm)
                "console" -> ConsoleScreen(vm)
                "ai" -> AiScreen(vm)
                "parts" -> PartsScreen(vm)
                "guided" -> GuidedScreen(vm)
                "online" -> OnlineScreen(vm)
                else -> vm.overlay = null
            }
        } else {
            BackHandler(enabled = vm.tab != 0) { vm.tab = 0 }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (vm.tab) {
                        0 -> HomeScreen(vm)
                        1 -> LiveScreen(vm)
                        2 -> FaultsScreen(vm)
                        3 -> ToolsScreen(vm)
                        else -> CarScreen(vm)
                    }
                }
                NavigationBar(containerColor = Brand.Header) {
                    val items = listOf("⌂" to "Home", "◔" to "Live", "⚠" to "Faults", "✚" to "Tools", "🚗" to "Car")
                    items.forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(
                            selected = vm.tab == i, onClick = { vm.tab = i },
                            icon = { Text(icon, fontSize = 20.sp) }, label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Brand.Accent, selectedTextColor = Brand.Accent,
                                unselectedIconColor = Brand.TextSecondary, unselectedTextColor = Brand.TextSecondary, indicatorColor = Brand.Track,
                            ),
                        )
                    }
                }
            }
        }
        val toast = vm.toast
        if (toast != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp, start = 16.dp, end = 16.dp)) { Pill(toast, Brand.Accent) }
            LaunchedEffect(toast) { delay(2800); if (vm.toast == toast) vm.toast = null }
        }
    }
}

// ====================================================================================== Home
@Composable
fun HomeScreen(vm: ObdyVm) {
    ScreenFrame("Obdy", null, actions = { HeaderButton("⚙") { vm.overlay = "settings" } }) {
        val on = vm.conn == Conn.On
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(when (vm.conn) { Conn.On -> Brand.Green; Conn.Connecting -> Brand.Amber; else -> Brand.Red })
                Spacer(Modifier.width(6.dp))
                Title(vm.car.name)
            }
            Hint(vm.connStatus)
            if (on) {
                val a = vm.adapter
                if (a != null) Label("Adapter: ${a.name}  ·  protocol ${a.protocol}")
                vm.voltage?.let { Label("Battery voltage: ${Calc.fmt(it, 2)} V") }
                if (vm.car.vin.isNotBlank()) Label("VIN: ${vm.car.vin}")
                Label("${vm.supported.size} live values available", color = Brand.TextSecondary, size = 13)
                Spacer(Modifier.height(4.dp))
                SoftButton("Disconnect") { vm.disconnect() }
            } else {
                Spacer(Modifier.height(4.dp))
                GradientButton(if (vm.conn == Conn.Connecting) "Connecting..." else "Connect to the car", Brand.AltBrush, enabled = vm.conn != Conn.Connecting) { vm.overlay = "connect" }
                if (vm.conn == Conn.Off) {
                    Hint("Plug the OBD adapter under the dashboard, turn the ignition ON (engine can stay off), then connect.")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton("Scan codes", Brand.MainBrush, Modifier.weight(1f), enabled = on && !vm.scanBusy) { vm.scan(false); vm.tab = 2 }
            GradientButton("Deep scan: all modules", Brand.AltBrush, Modifier.weight(1f), enabled = on && !vm.scanBusy) { vm.scan(true); vm.tab = 2 }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Live data", Modifier.weight(1f)) { vm.tab = 1 }
            SoftButton("Battery test", Modifier.weight(1f)) { vm.overlay = "battery" }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("AI analysis", Modifier.weight(1f)) { vm.overlay = "ai" }
            SoftButton("PDF report", Modifier.weight(1f)) { vm.makeReport() }
        }

        if (vm.lastScanMs > 0) {
            Panel {
                Title("Last scan")
                Hint(fmtDate(vm.lastScanMs))
                val m = vm.monitors
                if (m != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (m.milOn) "Warning light ON" else "Warning light off", if (m.milOn) Brand.Red else Brand.Green)
                    val inc = m.monitors.count { it.supported && !it.complete }
                    if (inc > 0) Pill("$inc monitor(s) not ready", Brand.Amber)
                }
                if (vm.visibleFaults.isEmpty()) Label("No fault codes.", color = Brand.Green)
                else {
                    Label("${vm.visibleFaults.size} fault code(s):", bold = true)
                    for (f in vm.visibleFaults.take(6)) Label("${f.code}  ${f.description}", size = 13)
                    if (vm.visibleFaults.size > 6) Hint("... and ${vm.visibleFaults.size - 6} more (Faults tab)")
                }
            }
        }

        val due = vm.dueList().filter { it.overdue || it.soon }
        if (due.isNotEmpty()) Panel {
            Title("Service")
            for (d in due.take(4)) Label((if (d.overdue) "Overdue: " else "Soon: ") + d.item.name, color = if (d.overdue) Brand.Red else Brand.Amber, size = 14)
            SoftButton("Open service list") { vm.tab = 4 }
        }
    }
}

// ====================================================================================== Live
@Composable
fun LiveScreen(vm: ObdyVm) {
    DisposableEffect(Unit) { vm.wantLive("screen", true); onDispose { vm.wantLive("screen", false) } }
    var pick by remember { mutableStateOf(false) }
    ScreenFrame("Live data", null) {
        if (vm.conn != Conn.On) {
            Panel { Label("Not connected."); GradientButton("Connect to the car", Brand.AltBrush) { vm.overlay = "connect" } }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton(if (vm.recOn) "■ Stop (${vm.recRows})" else "● Record", Modifier.weight(1f)) { if (vm.recOn) vm.stopRecording() else vm.startRecording() }
                SoftButton("Choose values", Modifier.weight(1f)) { pick = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton(if (vm.tripOn) "■ End trip" else "▶ Start trip + fuel", Modifier.weight(1f)) { if (vm.tripOn) vm.stopTrip() else vm.startTrip() }
                SoftButton("Graphs", Modifier.weight(1f)) { vm.overlay = "records" }
            }
            if (vm.tripOn) Panel {
                Title("Trip in progress")
                Label("${Calc.fmt(vm.tripKm, 2)} km  ·  ${Calc.fmt(vm.tripLiters, 2)} L  ·  ${(vm.tripSeconds / 60).toInt()} min")
                Label("Average: " + (vm.tripL100?.let { Calc.fmt(it, 1) + " L/100 km" } ?: "-") + "   Now: " + (vm.instL100?.let { Calc.fmt(it, 1) + " L/100 km" } ?: Calc.fmt(vm.instLph, 1) + " L/h"), size = 13)
                if (vm.car.fuelPrice > 0) Label("Cost so far: ${Calc.fmt(vm.tripLiters * vm.car.fuelPrice, 2)}", size = 13)
                if (!vm.supported.contains(0x10) && !vm.supported.contains(0x5E) && vm.supported.isNotEmpty()) Hint("This car reports neither air flow nor fuel rate, so fuel use cannot be calculated.")
            }
            val pids = vm.liveSel.mapNotNull { Pids.byId(it) }
            if (pids.isEmpty()) Hint("Tap Choose values to pick what to watch.")
            for (row in pids.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (p in row) GaugeCard(p.name, vm.liveValues[p.id], p.unit, p.min, p.max, Modifier.weight(1f), decimals = if (p.max <= 10) 2 else if (p.max <= 200) 1 else 0, hint = p.hint)
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.showExt, onCheckedChange = { vm.showExt = it })
                Spacer(Modifier.width(8.dp))
                Column { Label("GM extras (experimental)", bold = true); Hint("Transmission fluid temp and oil life. Shown only if the module answers.") }
            }
            if (vm.showExt) for (e in Gm.extPids) {
                val v = vm.extValues[e.name]
                Panel { Label(e.name, bold = true); Label(if (v == null) "no answer from the car" else Calc.fmt(v, 1) + " " + e.unit, color = if (v == null) Brand.TextSecondary else Brand.TextPrimary) }
            }
        }
    }
    if (pick) {
        AlertDialog(
            onDismissRequest = { pick = false },
            confirmButton = { TextButton(onClick = { pick = false }) { Text("Done") } },
            title = { Text("Live values") },
            text = {
                Column(Modifier.height(380.dp).verticalScroll(rememberScrollState())) {
                    val list = Pids.all.filter { vm.supported.isEmpty() || it.id in vm.supported }
                    for (p in list) {
                        Row(Modifier.fillMaxWidth().clickable { vm.toggleLive(p.id) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = p.id in vm.liveSel, onCheckedChange = { vm.toggleLive(p.id) })
                            Text(p.name + if (p.unit.isNotEmpty()) " (${p.unit})" else "", fontSize = 14.sp)
                        }
                    }
                }
            },
        )
    }
}

// ====================================================================================== Faults
@Composable
fun FaultsScreen(vm: ObdyVm) {
    var open by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val on = vm.conn == Conn.On
    ScreenFrame("Faults", null) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton("Quick scan", Brand.MainBrush, Modifier.weight(1f), enabled = on && !vm.scanBusy) { vm.scan(false) }
            GradientButton("Deep scan", Brand.AltBrush, Modifier.weight(1f), enabled = on && !vm.scanBusy) { vm.scan(true) }
        }
        Hint("Quick scan: engine and transmission. Deep scan also looks for ABS, airbag, body and other modules (about 15 seconds).")
        if (!on) Panel { Label("Not connected."); SoftButton("Connect to the car") { vm.overlay = "connect" } }
        if (vm.scanBusy) Panel { Title("Scanning..."); Hint(vm.scanProgress) }

        if (vm.lastScanMs > 0 && !vm.scanBusy) {
            Hint("Scanned ${fmtDate(vm.lastScanMs)}")
            if (vm.visibleFaults.isEmpty()) Panel { Label("No fault codes in the modules that answered" + (if (vm.hiddenFaults.isNotEmpty()) " (besides the ${vm.hiddenFaults.size} you hid)." else "."), color = Brand.Green, bold = true) }
            for ((module, list) in vm.visibleFaults.groupBy { it.module }) {
                Title("$module (${list.size})")
                for (f in list) FaultCard(vm, f, open == f.key, { open = if (open == f.key) null else f.key })
            }
            if (vm.hiddenFaults.isNotEmpty()) Panel {
                Title("Hidden in Obdy (${vm.hiddenFaults.size})")
                Hint("The car still has these codes. They only stay out of your lists.")
                for (f in vm.hiddenFaults) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Label(f.code, bold = true); Hint(f.description) }
                    SoftButton("Show again") { vm.restoreCode(f.code) }
                }
            }
            if (vm.scanNotes.isNotEmpty()) Panel {
                Title("Scan notes")
                for (n in vm.scanNotes) Hint(n)
            }
            val m = vm.monitors
            if (m != null) Panel {
                Title("Warning light and readiness")
                Label("Warning light: " + if (m.milOn) "ON" else "off", color = if (m.milOn) Brand.Red else Brand.Green, bold = true)
                Hint("Monitors that are not complete mean the car has not finished its self-tests since codes were cleared or the battery was disconnected.")
                for (x in m.monitors.filter { it.supported }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (x.complete) "✓" else "…", color = if (x.complete) Brand.Green else Brand.Amber, fontSize = 16.sp, modifier = Modifier.width(24.dp))
                        Label(x.name + if (x.complete) "" else " (not complete)", size = 14)
                    }
                }
            }
            val fz = vm.freeze
            if (fz != null && fz.code != null) Panel {
                Title("Freeze frame: ${fz.code}")
                Hint("What the engine was doing the moment this fault was stored.")
                for ((p, v) in fz.values) Label("${p.name}: ${Calc.fmt(v, 1)} ${p.unit}", size = 14)
            }
            if (on) GradientButton("Clear codes", Brand.DangerBrush, enabled = !vm.clearing && !vm.scanBusy) { confirmClear = true }
        }

        Panel {
            Title("What do you notice?")
            Hint("Noise, shaking, when it happens (cold start, braking, A/C on...). It is added to the AI analysis and the report.")
            Field("Symptoms", vm.symptoms, { vm.saveSymptoms(it) }, singleLine = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("AI analysis", Modifier.weight(1f)) { vm.overlay = "ai" }
            SoftButton("PDF report", Modifier.weight(1f)) { vm.makeReport() }
        }
        if (vm.scanHistory.isNotEmpty()) Panel {
            Title("Earlier scans")
            for (s in vm.scanHistory.take(6)) Label("${fmtDate(s.timeMs)}  ·  ${s.faults.size} code(s)" + (s.faults.take(3).joinToString(prefix = "  ", transform = { it.code }).takeIf { s.faults.isNotEmpty() } ?: ""), size = 13)
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all codes?") },
            text = { Text("This erases the codes AND the freeze frame, turns the warning light off and resets the readiness monitors (the car needs about 50 km of mixed driving to be ready for an emissions test again). The problem is not fixed by clearing: if it is real, the code will return. This scan has been saved in the history.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearCodes() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun FaultCard(vm: ObdyVm, f: Fault, expanded: Boolean, onToggle: () -> Unit) {
    val sev = DtcDb.severity(f.code)
    val c = when (sev) { 2 -> Brand.Red; 1 -> Brand.Amber; else -> Brand.Green }
    Panel(Modifier.clickable(onClick = onToggle)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(f.code, color = c, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Pill(f.kind, Brand.Blue)
            if (f.status.isNotBlank()) Text(f.status, color = Brand.TextSecondary, fontSize = 11.sp)
        }
        Label(f.description, size = 14)
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Label(DtcDb.severityText(sev), color = c, size = 13)
            Label(DtcDb.system(f.code), color = Brand.TextSecondary, size = 12)
            val causes = DtcDb.causes(f.code)
            if (causes.isNotEmpty()) {
                Label("Likely causes (most common first):", bold = true, size = 13)
                for (x in causes) Label("•  $x", size = 13)
            } else Hint("No built-in notes for this code. Use the AI analysis for this one.")
            if (f.raw.isNotBlank()) Hint("Raw: ${f.raw}")
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Where is it?", Modifier.weight(1f)) { vm.showParts(f.code) }
                SoftButton("Find the cause", Modifier.weight(1f)) { vm.startGuided(f.code); vm.overlay = "guided" }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Ask AI", Modifier.weight(1f)) { vm.overlay = "ai"; vm.runAi(f.code) }
                SoftButton("Hide in Obdy", Modifier.weight(1f)) { vm.ignoreCode(f.code) }
            }
        } else Hint("Tap for causes")
    }
}

// ====================================================================================== Tools
@Composable
fun ToolCard(title: String, desc: String, onClick: () -> Unit) {
    Panel(Modifier.clickable(onClick = onClick)) { Title(title); Hint(desc) }
}

@Composable
fun ToolsScreen(vm: ObdyVm) {
    ScreenFrame("Tools", null) {
        ToolCard("Battery and charging test", "Resting voltage, the dip while starting, and what the alternator delivers. Weak batteries cause many strange warning lights.") { vm.overlay = "battery" }
        ToolCard("Fuel and trips", "Start a trip on the Live screen: litres per 100 km, cost, and a saved history.") { vm.overlay = "trips" }
        ToolCard("Recordings and graphs", "Record a drive, look at the graphs, compare two drives (for example before and after a repair).") { vm.overlay = "records" }
        ToolCard("Relearn guides", "What to do after a battery change, cleaning the throttle body, or a fuel-system repair.") { vm.overlay = "relearn" }
        ToolCard("Console (advanced)", "Send your own commands to the adapter and share the full log. Use this to send me what the car answers.") { vm.overlay = "console" }
        ToolCard("Find the cause (questions)", "Answer a few simple questions about a code (what you already replaced, when it happens). The AI then gives the solution, including cleaning jobs and other causes.") { vm.gCode = null; vm.overlay = "guided" }
        ToolCard("Where is the part?", "A car diagram that lights up the area of each faulty part, with a description of where to look.") { vm.showParts(null) }
        ToolCard("Online checks", "Search the web, let the AI research known fixes, and see official recalls and owner complaints for your car.") { vm.overlay = "online" }
        ToolCard("AI analysis", "Deeper reasoning over codes, freeze frame, live values and your notes.") { vm.overlay = "ai" }
    }
}

// ====================================================================================== Car profile + service
@Composable
fun CarScreen(vm: ObdyVm) {
    val c = vm.car
    var name by remember(c) { mutableStateOf(c.name) }
    var plate by remember(c) { mutableStateOf(c.plate) }
    var year by remember(c) { mutableStateOf(c.year.toString()) }
    var engine by remember(c) { mutableStateOf(c.engine) }
    var km by remember(c) { mutableStateOf(c.odometerKm.toString()) }
    var tank by remember(c) { mutableStateOf(Calc.fmt(c.tankLiters, 0)) }
    var price by remember(c) { mutableStateOf(if (c.fuelPrice > 0) Calc.fmt(c.fuelPrice, 2) else "") }
    var notes by remember(c) { mutableStateOf(c.notes) }
    var editing by remember { mutableStateOf<com.example.obdy.MaintItem?>(null) }
    ScreenFrame("My car", null) {
        Panel {
            Title("Profile")
            Field("Name", name, { name = it })
            Field("Plate", plate, { plate = it })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Year", year, { year = it }, number = true, modifier = Modifier.weight(1f))
                Field("Mileage (km)", km, { km = it }, number = true, modifier = Modifier.weight(1f))
            }
            Field("Engine (as on the car, optional)", engine, { engine = it })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Tank (L)", tank, { tank = it }, number = true, modifier = Modifier.weight(1f))
                Field("Fuel price per L", price, { price = it }, number = true, modifier = Modifier.weight(1f))
            }
            Field("Notes (known repairs, history)", notes, { notes = it }, singleLine = false)
            Label("VIN: " + c.vin.ifBlank { "read automatically when you connect" }, size = 13, color = Brand.TextSecondary)
            GradientButton("Save", Brand.MainBrush) {
                vm.updateCar(c.copy(
                    name = name.trim().ifBlank { c.name }, plate = plate.trim(), year = year.trim().toIntOrNull() ?: c.year, engine = engine.trim(),
                    odometerKm = km.trim().toIntOrNull() ?: c.odometerKm, tankLiters = tank.trim().toDoubleOrNull() ?: c.tankLiters,
                    fuelPrice = price.trim().toDoubleOrNull() ?: 0.0, notes = notes.trim(),
                ))
                vm.say("Saved")
            }
        }
        Panel {
            Title("Service reminders")
            Hint("Intervals start on the hard side (heat and dust). Change them to what your workshop says. Keep the mileage above up to date.")
            for (d in vm.dueList()) {
                val status = when {
                    d.overdue -> "Overdue" + (d.kmLeft?.takeIf { it < 0 }?.let { " by ${-it} km" } ?: "") + (d.daysLeft?.takeIf { it < 0 }?.let { " (${-it} days)" } ?: "")
                    else -> listOfNotNull(d.kmLeft?.let { "$it km left" }, d.daysLeft?.let { "$it days left" }).joinToString("  ·  ").ifEmpty { "Not done yet: press Done today after the next service" }
                }
                val col = if (d.overdue) Brand.Red else if (d.soon) Brand.Amber else Brand.TextSecondary
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { editing = d.item }) {
                        Label(d.item.name, bold = true)
                        Label(status, color = col, size = 12)
                        Hint("Every " + listOfNotNull(d.item.everyKm.takeIf { it > 0 }?.let { "$it km" }, d.item.everyMonths.takeIf { it > 0 }?.let { "$it months" }).joinToString(" or ") + "  (tap to change)")
                    }
                    SoftButton("Done today") { vm.markDone(d.item) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("PDF report", Modifier.weight(1f)) { vm.makeReport() }
            SoftButton("Settings", Modifier.weight(1f)) { vm.overlay = "settings" }
        }
    }
    val e = editing
    if (e != null) {
        var ek by remember(e) { mutableStateOf(e.everyKm.toString()) }
        var em by remember(e) { mutableStateOf(e.everyMonths.toString()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(e.name) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Every (km), 0 = not used", ek, { ek = it }, number = true)
                Field("Every (months), 0 = not used", em, { em = it }, number = true)
            } },
            confirmButton = { TextButton(onClick = { vm.setInterval(e, ek.trim().toIntOrNull() ?: e.everyKm, em.trim().toIntOrNull() ?: e.everyMonths); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
