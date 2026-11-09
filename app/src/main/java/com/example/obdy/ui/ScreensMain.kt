package com.example.obdy.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.shape.CircleShape
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
import com.example.obdy.Docs
import com.example.obdy.Gm
import com.example.obdy.MaintItem
import com.example.obdy.Maintenance
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
/** Set by the root: opens Settings. Every main screen shows a gear in its top-right corner. */
val LocalOpenSettings = compositionLocalOf<(() -> Unit)?> { null }
val LocalUpdateBadge = compositionLocalOf { false }

@Composable
fun ScreenFrame(title: String, onBack: (() -> Unit)?, subtitle: String = "", actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Brand.Header).padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconTap(Ic.Back, onBack) else Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Brand.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (subtitle.isNotEmpty()) Text(subtitle, color = Brand.TextSecondary, fontSize = 12.sp)
            }
            actions()
            val openSettings = LocalOpenSettings.current
            if (onBack == null && openSettings != null) {
                Box {
                    IconTap(Ic.Gear, openSettings)
                    if (LocalUpdateBadge.current) Box(Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 9.dp).size(10.dp).clip(CircleShape).background(Brand.Red))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brand.CardBorder))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun BottomBar(vm: ObdyVm) {
    val items = listOf(Ic.Home to "Home", Ic.Gauge to "Live", Ic.Warning to "Faults", Ic.Wrench to "Tools", Ic.Car to "Car")
    Row(Modifier.fillMaxWidth().background(Brand.Header).padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
        items.forEachIndexed { i, (ic, label) ->
            val sel = vm.tab == i
            val col by animateColorAsState(targetValue = if (sel) Brand.Accent else Brand.TextSecondary, animationSpec = tween(200), label = "navColor")
            val pill by animateFloatAsState(targetValue = if (sel) 1f else 0f, animationSpec = tween(220), label = "navPill")
            val src = remember { MutableInteractionSource() }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(interactionSource = src, indication = null) { vm.tab = i }.padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(58.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(Brand.Accent.copy(alpha = 0.18f * pill)), contentAlignment = Alignment.Center) {
                    AppIcon(ic, size = 22.dp, color = col)
                }
                Text(label, color = col, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun ObdyRoot(vm: ObdyVm, askPermissions: () -> Unit) {
    CompositionLocalProvider(LocalOpenSettings provides { vm.overlay = "settings" }, LocalUpdateBadge provides (vm.updateReady != null)) {
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = vm.overlay ?: "main", animationSpec = tween(220), label = "screens") { ov ->
            if (ov != "main") {
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
                    "doctor" -> DoctorScreen(vm)
                    "inspection" -> InspectionScreen(vm)
                    "health" -> HealthScreen(vm)
                    "tests" -> TestsScreen(vm)
                    "expenses" -> ExpensesScreen(vm)
                    "chat" -> ChatScreen(vm)
                    "dash" -> DashScreen(vm)
                    else -> Box(Modifier.fillMaxSize()) { }
                }
            } else {
                BackHandler(enabled = vm.tab != 0) { vm.tab = 0 }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        Crossfade(targetState = vm.tab, animationSpec = tween(200), label = "tabs") { t ->
                            when (t) {
                                0 -> HomeScreen(vm)
                                1 -> LiveScreen(vm)
                                2 -> FaultsScreen(vm)
                                3 -> ToolsScreen(vm)
                                else -> CarScreen(vm)
                            }
                        }
                    }
                    BottomBar(vm)
                }
            }
        }
        val toast = vm.toast
        val keep = remember { mutableStateOf("") }
        LaunchedEffect(toast) {
            if (toast != null) { keep.value = toast; delay(2800); if (vm.toast == toast) vm.toast = null }
        }
        AnimatedVisibility(
            visible = toast != null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp, start = 16.dp, end = 16.dp),
            enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
        ) { Pill(keep.value, Brand.Accent) }
    }
    }
}

// ====================================================================================== Home
@Composable
private fun HeroCard(vm: ObdyVm) {
    val on = vm.conn == Conn.On
    val busy = vm.conn == Conn.Connecting
    val color = if (on) Brand.Green else if (busy) Brand.Amber else Brand.Red
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color, pulsing = busy || vm.reconnecting, size = 14.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (on) "Connected" else if (busy) "Connecting..." else "Not connected", color = Brand.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (!on) Hint(vm.connStatus)
            }
        }
        if (on) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.adapter?.let { Pill(it.name.take(22), Brand.Blue) }
                vm.voltage?.let { Pill(Calc.fmt(it, 1) + " V", if (it < 12.0) Brand.Amber else Brand.Green) }
            }
            if (vm.car.vin.isNotBlank()) Hint("VIN ${vm.car.vin}")
            SoftButton("Disconnect", Modifier.fillMaxWidth()) { vm.userDisconnect() }
        } else {
            Spacer(Modifier.height(2.dp))
            GradientButton(if (busy) "Connecting..." else "Connect to the car", Brand.AltBrush, enabled = !busy) { vm.overlay = "connect" }
            if (vm.conn == Conn.Off) Hint("Plug the adapter into the car, turn the ignition ON (the engine can stay off), then connect.")
        }
    }
}

@Composable
fun HomeScreen(vm: ObdyVm) {
    ScreenFrame("Obdy", null, subtitle = vm.car.name) {
        val on = vm.conn == Conn.On
        Reveal(0) { HeroCard(vm) }
        if (vm.updateReady != null) Reveal(1) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(Ic.Bolt, size = 24.dp, color = Brand.Green)
                    Column(Modifier.weight(1f)) { Title("Update ready"); Hint("Build ${vm.updateReadyCode} is saved on this phone.") }
                }
                GradientButton("Install now", Brand.GoodBrush) { vm.installUpdate() }
            }
        }
        Reveal(1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(Ic.Search, "Scan codes", "Engine and gearbox", Modifier.weight(1f), accent = true, enabled = on && !vm.scanBusy) { vm.scan(false); vm.tab = 2 }
                Tile(Ic.Shield, "Deep scan", "ABS, airbag, body", Modifier.weight(1f), enabled = on && !vm.scanBusy) { vm.scan(true); vm.tab = 2 }
            }
        }
        Reveal(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(Ic.Gauge, "Live data", "Gauges and graphs", Modifier.weight(1f)) { vm.tab = 1 }
                Tile(Ic.Chat, "Ask the AI", "Chat, photo, voice", Modifier.weight(1f)) { vm.overlay = "chat" }
            }
        }
        Reveal(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(Ic.Battery, "Battery test", "Start and charging", Modifier.weight(1f)) { vm.overlay = "battery" }
                Tile(Ic.Doc, "PDF report", "For your mechanic", Modifier.weight(1f)) { vm.makeReport() }
            }
        }
        if (vm.lastScanMs > 0) Reveal(4) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Title("Last scan")
                    val m = vm.monitors
                    if (m != null) Pill(if (m.milOn) "Light ON" else "Light off", if (m.milOn) Brand.Red else Brand.Green)
                }
                Hint(fmtDate(vm.lastScanMs))
                if (vm.visibleFaults.isEmpty()) Label("No fault codes.", color = Brand.Green, bold = true)
                else {
                    for (f in vm.visibleFaults.take(5)) Label("${f.code}  ${f.description}", size = 13)
                    if (vm.visibleFaults.size > 5) Hint("... and ${vm.visibleFaults.size - 5} more")
                    SoftButton("See all and causes", Modifier.fillMaxWidth()) { vm.tab = 2 }
                }
            }
        }
        val due = vm.dueList().filter { it.overdue || it.soon }
        if (due.isNotEmpty()) Reveal(5) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(Ic.Wrench, size = 20.dp, color = Brand.Amber); Title("Service")
                }
                for (d in due.take(4)) Label((if (d.overdue) "Overdue: " else "Soon: ") + d.item.name, color = if (d.overdue) Brand.Red else Brand.Amber, size = 14)
                SoftButton("Open service list", Modifier.fillMaxWidth()) { vm.tab = 4 }
            }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(Ic.Warning, size = 22.dp, color = c)
            Text(f.code, color = c, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Pill(f.kind, Brand.Blue)
            Spacer(Modifier.weight(1f))
            if (f.status.isNotBlank()) Text(f.status, color = Brand.TextSecondary, fontSize = 11.sp)
        }
        Label(f.description, size = 14)
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.height(2.dp))
                Label(DtcDb.severityText(sev), color = c, size = 13)
                Label(DtcDb.system(f.code), color = Brand.TextSecondary, size = 12)
                val causes = DtcDb.causes(f.code)
                if (causes.isNotEmpty()) {
                    Label("Likely causes (most common first):", bold = true, size = 13)
                    for (x in causes) Label("•  $x", size = 13)
                } else Hint("No built-in notes for this code. Use the AI analysis for this one.")
                if (f.raw.isNotBlank()) Hint("Raw: ${f.raw}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Where is it?", Modifier.weight(1f)) { vm.showParts(f.code) }
                    SoftButton("Find the cause", Modifier.weight(1f)) { vm.startGuided(f.code); vm.overlay = "guided" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Ask AI", Modifier.weight(1f)) { vm.overlay = "ai"; vm.runAi(f.code) }
                    SoftButton("Hide in Obdy", Modifier.weight(1f)) { vm.ignoreCode(f.code) }
                }
            }
        }
        if (!expanded) Hint("Tap for causes")
    }
}

// ====================================================================================== Tools
private class ToolItem(val ic: Ic, val title: String, val sub: String, val open: () -> Unit)

@Composable
fun ToolsScreen(vm: ObdyVm) {
    val items = listOf(
        ToolItem(Ic.Chat, "Ask about your car", "Chat, photo, voice") { vm.overlay = "chat" },
        ToolItem(Ic.Search, "Find the cause", "Questions, then a fix") { vm.gCode = null; vm.overlay = "guided" },
        ToolItem(Ic.Pin, "Where is the part?", "Diagram of the car") { vm.showParts(null) },
        ToolItem(Ic.Heart, "Health check", "Idle test and trends") { vm.overlay = "health" },
        ToolItem(Ic.Cpu, "Engine self-tests", "Misfire counts, catalyst") { vm.overlay = "tests" },
        ToolItem(Ic.Shield, "Inspection check", "Ready for the test?") { vm.overlay = "inspection" },
        ToolItem(Ic.Battery, "Battery test", "Start and charging") { vm.overlay = "battery" },
        ToolItem(Ic.Fuel, "Fuel and trips", "Litres per 100 km") { vm.overlay = "trips" },
        ToolItem(Ic.Chart, "Recordings", "Graphs, compare drives") { vm.overlay = "records" },
        ToolItem(Ic.Cash, "Repairs and costs", "History and receipts") { vm.overlay = "expenses" },
        ToolItem(Ic.Gauge, "Dashboard mode", "Big landscape gauges") { vm.overlay = "dash" },
        ToolItem(Ic.Globe, "Online checks", "Recalls, web, research") { vm.overlay = "online" },
        ToolItem(Ic.Bolt, "AI analysis", "Deeper reasoning") { vm.overlay = "ai" },
        ToolItem(Ic.Wrench, "Relearn guides", "After battery or cleaning") { vm.overlay = "relearn" },
        ToolItem(Ic.Plug, "Adapter doctor", "Test the adapter") { vm.overlay = "doctor" },
        ToolItem(Ic.Terminal, "Console", "Raw commands and log") { vm.overlay = "console" },
    )
    ScreenFrame("Tools", null, subtitle = "Everything else Obdy can do") {
        items.chunked(2).forEachIndexed { row, pair ->
            Reveal(row) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (t in pair) Tile(t.ic, t.title, t.sub, Modifier.weight(1f)) { t.open() }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
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
    var editing by remember { mutableStateOf<MaintItem?>(null) }
    var doneFor by remember { mutableStateOf<MaintItem?>(null) }
    var bulk by remember { mutableStateOf(false) }
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
            val now = System.currentTimeMillis()
            val list = vm.dueList().sortedBy { if (it.overdue) 0 else if (it.soon) 1 else if (it.recorded) 2 else 3 }
            val kpd = vm.kmPerDay()
            val nOver = list.count { it.overdue }; val nSoon = list.count { it.soon }; val nNone = list.count { !it.recorded }
            Label(
                if (nOver > 0) "$nOver overdue" + (if (nSoon > 0) ", $nSoon soon" else "") else if (nSoon > 0) "$nSoon due soon" else "Nothing overdue",
                bold = true, color = if (nOver > 0) Brand.Red else if (nSoon > 0) Brand.Amber else Brand.Green,
            )
            Hint("Tap Edit and enter when each job was last done (date and mileage). Obdy then counts the next due date and mileage from it. Press Done after a service: it also goes into your repair and cost history.")
            if (kpd != null) Hint("Your usual driving: about ${Calc.fmt(kpd * 30, 0)} km a month (worked out from the mileages you entered).")
            if (nNone > 0) SoftButton("Set the $nNone not-recorded jobs to one date", Modifier.fillMaxWidth()) { bulk = true }
            for (d in list) {
                val status = when {
                    !d.recorded -> "Not recorded yet: tap Edit and enter when it was last done"
                    d.overdue -> "Overdue" + (d.kmLeft?.takeIf { it < 0 }?.let { " by ${-it} km" } ?: "") + (d.daysLeft?.takeIf { it < 0 }?.let { " (${-it} days)" } ?: "")
                    else -> listOfNotNull(d.kmLeft?.let { "$it km left" }, d.daysLeft?.let { "$it days left" }).joinToString("  ·  ")
                }
                val col = if (d.overdue) Brand.Red else if (d.soon) Brand.Amber else Brand.TextSecondary
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Label(d.item.name, bold = true)
                    Label(status, color = col, size = 12)
                    val parts = ArrayList<String>()
                    d.nextDueMs?.let { parts.add("by " + Docs.formatDate(it)) }
                    d.nextDueKm?.let { parts.add("at $it km") }
                    if (parts.isNotEmpty()) {
                        var line = "Next: " + parts.joinToString("  or  ")
                        val left = d.kmLeft
                        if (kpd != null && left != null && left > 0) Maintenance.projectedKmDate(left, kpd, now)?.let { line += "  (at your usual driving the km limit comes about " + Docs.formatDate(it) + ")" }
                        Hint(line)
                    }
                    if (d.recorded && d.item.everyKm > 0 && d.item.lastKm <= 0) Hint("Add the mileage in Edit to also track kilometres.")
                    if (d.recorded && d.item.everyMonths > 0 && d.item.lastDateMs <= 0) Hint("Add the date in Edit to also track time.")
                    Hint("Every " + listOfNotNull(d.item.everyKm.takeIf { it > 0 }?.let { "$it km" }, d.item.everyMonths.takeIf { it > 0 }?.let { "$it months" }).joinToString(" or ").ifEmpty { "(no interval)" })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton("Done", Modifier.weight(1f)) { doneFor = d.item }
                        SoftButton("Edit", Modifier.weight(1f)) { editing = d.item }
                    }
                }
            }
            SoftButton("Add a service item", Modifier.fillMaxWidth()) { editing = MaintItem(vm.newId(), "", 10000, 12) }
        }
        DocsPanel(vm)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Repairs and costs", Modifier.weight(1f)) { vm.overlay = "expenses" }
            SoftButton("PDF report", Modifier.weight(1f)) { vm.makeReport() }
        }
    }
    val e = editing
    if (e != null) {
        var name by remember(e.id) { mutableStateOf(e.name) }
        var ek by remember(e.id) { mutableStateOf(e.everyKm.toString()) }
        var em by remember(e.id) { mutableStateOf(e.everyMonths.toString()) }
        var ld by remember(e.id) { mutableStateOf(if (e.lastDateMs > 0) Docs.formatDate(e.lastDateMs) else "") }
        var lk by remember(e.id) { mutableStateOf(if (e.lastKm > 0) e.lastKm.toString() else "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (e.name.isBlank()) "New service item" else e.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Name", name, { name = it })
                    Label("Last done", bold = true, size = 13)
                    Field("Date (YYYY-MM-DD, for example 2026-03-15)", ld, { ld = it })
                    Field("Mileage then (km)", lk, { lk = it }, number = true)
                    Label("Repeat every", bold = true, size = 13)
                    Field("Kilometres (0 = not used)", ek, { ek = it }, number = true)
                    Field("Months (0 = not used)", em, { em = it }, number = true)
                    if (vm.maint.any { it.id == e.id }) SoftButton("Delete this item", Modifier.fillMaxWidth()) { vm.deleteMaintItem(e.id); editing = null }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dateMs = if (ld.isBlank()) 0L else Docs.parseDate(ld)
                    if (dateMs == null) vm.say("Write the date like 2026-03-15")
                    else if (name.isBlank()) vm.say("Give it a name")
                    else {
                        e.name = name.trim(); e.everyKm = ek.trim().toIntOrNull() ?: 0; e.everyMonths = em.trim().toIntOrNull() ?: 0
                        e.lastDateMs = dateMs; e.lastKm = lk.trim().toIntOrNull() ?: 0
                        vm.saveMaintItem(e); editing = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    val df = doneFor
    if (df != null) {
        var date by remember(df.id) { mutableStateOf(Docs.formatDate(System.currentTimeMillis())) }
        var km by remember(df.id) { mutableStateOf(if (vm.car.odometerKm > 0) vm.car.odometerKm.toString() else "") }
        var cost by remember(df.id) { mutableStateOf("") }
        var shop by remember(df.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { doneFor = null },
            title = { Text("Done: ${df.name}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint("The next due date and mileage are counted from this.")
                    Field("Date (YYYY-MM-DD)", date, { date = it })
                    Field("Mileage (km)", km, { km = it }, number = true)
                    Field("Cost (optional)", cost, { cost = it }, number = true)
                    Field("Workshop (optional)", shop, { shop = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ms = Docs.parseDate(date)
                    if (ms == null) vm.say("Write the date like 2026-03-15")
                    else { vm.markDone(df, ms, km.trim().toIntOrNull() ?: vm.car.odometerKm, cost.trim().toDoubleOrNull() ?: 0.0, shop.trim()); doneFor = null }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { doneFor = null }) { Text("Cancel") } },
        )
    }
    if (bulk) {
        var date by remember { mutableStateOf(Docs.formatDate(System.currentTimeMillis())) }
        var km by remember { mutableStateOf(if (vm.car.odometerKm > 0) vm.car.odometerKm.toString() else "") }
        AlertDialog(
            onDismissRequest = { bulk = false },
            title = { Text("Set all not-recorded jobs") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint("Use the date and mileage of your last full service, or today if you are starting fresh. You can correct single items later with Edit.")
                    Field("Date (YYYY-MM-DD)", date, { date = it })
                    Field("Mileage (km)", km, { km = it }, number = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ms = Docs.parseDate(date)
                    if (ms == null) vm.say("Write the date like 2026-03-15")
                    else { vm.setAllUnrecorded(ms, km.trim().toIntOrNull() ?: vm.car.odometerKm); bulk = false }
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { bulk = false }) { Text("Cancel") } },
        )
    }
}
