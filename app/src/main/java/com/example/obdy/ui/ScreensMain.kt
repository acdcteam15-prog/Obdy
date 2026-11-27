package com.example.obdy.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import com.example.obdy.DriveCycle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.horizontalScroll
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
import com.example.obdy.FaultState
import com.example.obdy.FaultStates
import com.example.obdy.Docs
import com.example.obdy.Gm
import com.example.obdy.Manual
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
fun ScreenFrame(title: String, onBack: (() -> Unit)?, subtitle: String = "", logo: Boolean = false, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    // The big title shrinks as the page scrolls, and a shadow line appears under the header.
    val k = (scroll.value / 160f).coerceIn(0f, 1f)
    val titleSize = (27f - 7f * k).sp
    val glow = androidx.compose.animation.core.rememberInfiniteTransition(label = "glow")
    val gx by glow.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(9000), androidx.compose.animation.core.RepeatMode.Reverse), label = "gx")
    Column(Modifier.fillMaxSize().background(Brand.BgDeep)) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(lerp(Brand.Header, Brand.Accent, 0.06f), Brand.Header)))
                .background(Brush.horizontalGradient(listOf(Brand.palette.gradA1.copy(alpha = 0.16f * (1f - gx)), Color.Transparent, Brand.palette.gradB2.copy(alpha = 0.16f * gx))))
                .padding(start = 6.dp, end = 6.dp, top = (14f - 6f * k).dp, bottom = (12f - 4f * k).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) IconTap(Ic.Back, onBack) else if (logo) { Spacer(Modifier.width(10.dp)); AppLogo((40f - 8f * k).dp); Spacer(Modifier.width(10.dp)) } else Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Brand.TextPrimary, fontSize = titleSize, fontWeight = FontWeight.Bold, maxLines = 1)
                if (subtitle.isNotEmpty() && k < 0.6f) Text(subtitle, color = Brand.TextSecondary.copy(alpha = 1f - k), fontSize = 12.sp, maxLines = 1)
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
        Box(Modifier.fillMaxWidth().height(2.dp).graphicsLayer { alpha = 0.35f + 0.5f * k }.background(Brush.horizontalGradient(listOf(Brand.palette.gradA1, Brand.palette.gradA2, Brand.palette.gradB1, Brand.palette.gradB2))))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun BottomBar(vm: ObdyVm) {
    val items = listOf(Ic.Home to "Home", Ic.Gauge to "Live", Ic.Warning to "Faults", Ic.Wrench to "Tools", Ic.Car to "Car", Ic.Chat to "Lab")
    val haptic = LocalHapticFeedback.current
    val pos by animateFloatAsState(targetValue = vm.tab.toFloat(), animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f), label = "navPos")
    val badge = vm.visibleFaults.size
    Box(Modifier.fillMaxWidth().background(Brand.BgDeep).padding(horizontal = 12.dp, vertical = 8.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().graphicsLayer { shadowElevation = 18f; shape = RoundedCornerShape(26.dp); clip = true }
                .clip(RoundedCornerShape(26.dp)).background(lerp(Brand.Header, Brand.Accent, 0.05f))
                .border(1.dp, Brand.CardBorder, RoundedCornerShape(26.dp)).padding(6.dp),
        ) {
            val cell = maxWidth / items.size
            // the sliding highlight behind the selected tab
            val selCol by animateColorAsState(targetValue = Brand.vivid(items[vm.tab.coerceIn(0, items.size - 1)].second).first, animationSpec = tween(260), label = "navSel")
            Box(Modifier.offset(x = cell * pos).width(cell).height(54.dp).padding(horizontal = 4.dp).clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(selCol.copy(alpha = 0.30f), selCol.copy(alpha = 0.10f)))))
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, (ic, label) ->
                    val sel = vm.tab == i
                    val col by animateColorAsState(targetValue = if (sel) Brand.vivid(label).first else Brand.TextSecondary, animationSpec = tween(200), label = "navColor")
                    val lift by animateFloatAsState(targetValue = if (sel) 1f else 0f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f), label = "lift")
                    val src = remember { MutableInteractionSource() }
                    Column(
                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(20.dp))
                            .clickable(interactionSource = src, indication = null) { if (vm.tab != i) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.tab = i },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Box {
                            AppIcon(ic, size = 22.dp, color = col, modifier = Modifier.graphicsLayer { translationY = -3f * lift; scaleX = 1f + 0.12f * lift; scaleY = 1f + 0.12f * lift })
                            if (i == 2 && badge > 0) Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-5).dp).size(17.dp).clip(CircleShape).background(Brand.Red),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (badge > 9) "9+" else "$badge", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        }
                        Text(label, color = col, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
fun ObdyRoot(vm: ObdyVm, askPermissions: () -> Unit) {
    CompositionLocalProvider(LocalOpenSettings provides { vm.overlay = "settings" }, LocalUpdateBadge provides (vm.updateReady != null)) {
    Box(Modifier.fillMaxSize()) {
        // Opening a tool slides it in from the right; going back slides it out (modern push navigation).
        AnimatedContent(
            targetState = vm.overlay ?: "main",
            transitionSpec = {
                val opening = targetState != "main"
                (slideInHorizontally(tween(280)) { w -> if (opening) w / 3 else -w / 3 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(260)) { w -> if (opening) -w / 4 else w / 3 } + fadeOut(tween(180)))
            },
            label = "screens",
        ) { ov ->
            if (ov != "main") {
                BackHandler { vm.overlay = null }
                when (ov) {
                    "connect" -> ConnectScreen(vm, askPermissions)
                    "settings" -> SettingsScreen(vm)
                    "battery" -> BatteryScreen(vm)
                    "trips" -> TripsScreen(vm)
                    "records" -> RecordsScreen(vm)
                    "relearn" -> RelearnScreen(vm)
                    "ai" -> AiScreen(vm)
                    "parts" -> PartsScreen(vm)
                    "guided" -> GuidedScreen(vm)
                    "doctor" -> DoctorScreen(vm)
                    "inspection" -> InspectionScreen(vm)
                    "health" -> HealthScreen(vm)
                    "tests" -> TestsScreen(vm)
                    "expenses" -> ExpensesScreen(vm)
                    "dash" -> DriveScreen(vm)     // old name: Dashboard mode is now Driving mode
                    "guide" -> GuideScreen(vm)
                    "timeline" -> TimelineScreen(vm)
                    "fuses" -> FusesScreen(vm)
                    "fuellog" -> FuelLogScreen(vm)
                    "partsreg" -> PartsRegisterScreen(vm)
                    "checklist" -> ChecklistScreen(vm)
                    "lab", "chat" -> { LaunchedEffect(Unit) { vm.overlay = null; vm.tab = 5 } }   // Lab is a tab now
                    "gearbox" -> GearboxScreen(vm)
                    "drive" -> DriveScreen(vm)
                    "steering" -> SteeringCheckScreen(vm)
                    "codehistory" -> TimelineScreen(vm)
                    "explorer" -> ExplorerScreen(vm)
                    "buses" -> BusesScreen(vm)
                    "monitor" -> MonitorScreen(vm)
                    "signals" -> SignalsScreen(vm)
                    "sweep" -> SweepScreen(vm)
                    "atests" -> ActiveTestsScreen(vm)
                    "inventory" -> InventoryScreen(vm)
                    "perf" -> PerfScreen(vm)
                    "netwatch" -> NetWatchScreen(vm)
                    else -> Box(Modifier.fillMaxSize()) { }
                }
            } else {
                BackHandler(enabled = vm.tab != 0) { vm.tab = 0 }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        // Tabs slide toward the side of the tab you picked.
                        AnimatedContent(
                            targetState = vm.tab,
                            transitionSpec = {
                                val dir = if (targetState > initialState) 1 else -1
                                (slideInHorizontally(tween(260)) { w -> dir * w / 5 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(tween(220)) { w -> -dir * w / 5 } + fadeOut(tween(160)))
                            },
                            label = "tabs",
                        ) { t ->
                            when (t) {
                                0 -> HomeScreen(vm)
                                1 -> LiveScreen(vm)
                                2 -> FaultsScreen(vm)
                                3 -> ToolsScreen(vm)
                                4 -> CarScreen(vm)
                                else -> LabScreen(vm)
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
        ) { ToastCard(keep.value) }
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
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greet = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    ScreenFrame("Obdy", null, logo = true, subtitle = "$greet · ${vm.car.name}" + if (vm.car.odometerKm > 0) " · %,d km".format(vm.car.odometerKm) else "") {
        val on = vm.conn == Conn.On
        Reveal(0) { HeroCard(vm) }
        vm.chargeWarning?.let { w -> Reveal(1) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(Ic.Battery, size = 22.dp, color = Brand.Red); Title("Battery not charging")
                }
                Hint(w)
                SoftButton("Open the battery test", Modifier.fillMaxWidth()) { vm.overlay = "battery" }
            }
        } }
        vm.ectNote?.let { w -> Reveal(1) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(Ic.Warning, size = 22.dp, color = Brand.Amber); Title("Coolant signal dropped out")
                }
                Hint(w)
                SoftButton("Got it", Modifier.fillMaxWidth()) { vm.ectNote = null }
            }
        } }
        if (vm.newCodes.isNotEmpty()) Reveal(1) {
            Panel(Modifier.clickable { vm.tab = 2 }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(Ic.Warning, size = 22.dp, color = Brand.Red); Title("New since last scan")
                }
                for (f in vm.newCodes.take(4)) Label("${f.code}  ${f.description}", size = 13)
                Hint("Tap to open Faults.")
            }
        }
        Reveal(1) { HealthDashboard(vm) }
        Reveal(1) { MileageCard(vm) }
        if (vm.updateReady != null) Reveal(1) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(Ic.Bolt, size = 24.dp, color = Brand.Green)
                    Column(Modifier.weight(1f)) { Title("Update ready"); Hint("Build ${vm.updateReadyCode} is saved on this phone.") }
                }
                GradientButton("Install now", Brand.GoodBrush) { vm.installUpdate() }
            }
        }
        Reveal(2) { Text("Quick actions", color = Brand.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        Reveal(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(Ic.Search, "Scan", "Engine and gearbox", Modifier.weight(1f), accent = true, enabled = on && !vm.scanBusy) { vm.scan(false); vm.tab = 2 }
                Tile(Ic.Shield, "Deep scan", "Every module", Modifier.weight(1f), enabled = on && !vm.scanBusy) { vm.scan(true); vm.tab = 2 }
            }
        }
        Reveal(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(Ic.Car, "Driving mode", "Big gauges, alerts", Modifier.weight(1f), enabled = on) { vm.overlay = "drive" }
                Tile(Ic.Chat, "Claude Lab", "Chat, photos, session data", Modifier.weight(1f)) { vm.tab = 5 }
            }
        }
        if (vm.parking != null || vm.parkingOn) Reveal(4) { ParkingCard(vm) }
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
            SymptomButton(vm, Modifier.fillMaxWidth())
            if (vm.tripOn) Panel {
                Title("Trip in progress")
                Label("${Calc.fmt(vm.tripKm, 2)} km  ·  ${Calc.fmt(vm.tripLiters, 2)} L  ·  ${(vm.tripSeconds / 60).toInt()} min")
                Label("Average: " + (vm.tripL100?.let { Calc.fmt(it, 1) + " L/100 km" } ?: "-") + "   Now: " + (vm.instL100?.let { Calc.fmt(it, 1) + " L/100 km" } ?: Calc.fmt(vm.instLph, 1) + " L/h"), size = 13)
                vm.tripScore?.let { Label("Driving score $it / 100  ·  ${vm.tripTip}", size = 13, color = Brand.Accent) }
                if (vm.car.fuelPrice > 0) Label("Cost so far: ${Calc.fmt(vm.tripLiters * vm.car.fuelPrice, 2)}", size = 13)
                if (!vm.supported.contains(0x10) && !vm.supported.contains(0x5E) && vm.supported.isNotEmpty()) Hint("This car reports neither air flow nor fuel rate, so fuel use cannot be calculated.")
            }
            val ids = vm.liveSel.filter { Pids.byId(it) != null || it == Gm.GEAR_ID || it == Gm.TFT_ID }
            if (ids.isEmpty()) Hint("Tap Choose values to pick what to watch.")
            else Hint("Hold a card and drag to move it.")
            ReorderGrid(ids, onMove = { a, b -> vm.moveLive(vm.liveSel.indexOf(ids[a]), vm.liveSel.indexOf(ids[b])) }) { id, m ->
                when (id) {
                    Gm.GEAR_ID -> GearCard(Gm.gearLabel(vm.gearLever, vm.gearNow), m)
                    Gm.TFT_ID -> GaugeCard("Gearbox fluid (GM)", vm.extValues["Transmission fluid temp (GM)"], "°C", 20.0, 130.0, m)
                    else -> { val p = Pids.byId(id)!!; GaugeCard(p.name, vm.liveValues[p.id], p.unit, p.min, p.max, m, decimals = if (p.max <= 10) 2 else if (p.max <= 200) 1 else 0, hint = p.hint) }
                }
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
                    for ((gid, gname) in listOf(Gm.GEAR_ID to "Gear (P R N D1–D6)", Gm.TFT_ID to "Gearbox fluid temperature (°C)")) {
                        Row(Modifier.fillMaxWidth().clickable { vm.toggleLive(gid) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = gid in vm.liveSel, onCheckedChange = { vm.toggleLive(gid) })
                            Text(gname, fontSize = 14.sp)
                        }
                    }
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
    var stateFilter by remember { mutableStateOf<FaultState?>(null) }
    val on = vm.conn == Conn.On
    ScreenFrame("Faults", null) {
        // ---- status summary
        if (vm.lastScanMs > 0 && !vm.scanBusy) FaultSummary(vm)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton("Quick scan", Brand.MainBrush, Modifier.weight(1f), enabled = on && !vm.scanBusy) { vm.scan(false) }
            GradientButton("Deep scan", Brand.AltBrush, Modifier.weight(1f), enabled = on && !vm.scanBusy) { vm.scan(true) }
        }
        Text("Scan options are in Settings ›", color = Brand.Accent, fontSize = 12.sp, modifier = Modifier.clickable { vm.overlay = "settings" })
        Hint("Quick: engine and transmission (a few seconds).")
        if (!on) Panel { Label("Not connected."); SoftButton("Connect to the car") { vm.overlay = "connect" } }
        vm.scanWarning?.let { w -> if (!vm.scanBusy) Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(Ic.Warning, size = 22.dp, color = Brand.Amber); Title("Deep scan could not reach the other modules")
            }
            Hint(w)
            if (on) GradientButton("Deep scan again", Brand.AltBrush, enabled = !vm.scanBusy) { vm.scan(true) }
        } }
        if (vm.scanBusy) Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(Brand.Accent, pulsing = true); Title("Scanning...")
                Spacer(Modifier.weight(1f))
                vm.scanFraction?.let { Text("${(it * 100).toInt()}%", color = Brand.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
            ScanBar(vm.scanFraction)
            Hint(vm.scanProgress)
            Shimmer(3)
        }

        if (vm.lastScanMs > 0 && !vm.scanBusy) {
            if (vm.lastDeepMs == 0L) Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(Ic.Info, size = 20.dp, color = Brand.Amber)
                    Label("Message on the dash but no code here?", bold = true, color = Brand.Amber)
                }
                Hint("Warnings like \"Service Power Steering\", ABS or airbag are stored in their own modules, not the engine.")
            }
            if (vm.visibleFaults.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clickable { stateFilter = null }) { Pill("All ${vm.visibleFaults.size}", if (stateFilter == null) Brand.Accent else Brand.TextSecondary) }
                    for (st in FaultState.values()) {
                        val n = vm.visibleFaults.count { FaultStates.of(it) == st }
                        if (n > 0) Box(Modifier.clickable { stateFilter = st }) { Pill("${st.label} $n", if (stateFilter == st) Brand.Accent else Brand.TextSecondary) }
                    }
                }
            }
            for ((module, list) in vm.visibleFaults.filter { stateFilter == null || FaultStates.of(it) == stateFilter }.groupBy { it.module }) {
                Text("$module  ·  ${list.size}", color = Brand.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                for (f in list) FaultCard(vm, f, open == f.key, { open = if (open == f.key) null else f.key })
            }
            if (vm.testsRead) {
                val bad = vm.tests.count { !it.pass }
                Panel(Modifier.clickable { vm.overlay = "tests" }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(Ic.Cpu, size = 20.dp, color = if (bad > 0) Brand.Amber else Brand.Green)
                        Column(Modifier.weight(1f)) {
                            Title("Engine self-tests")
                            Hint("${vm.tests.size} results read (Mode 06)  ·  " + if (bad == 0) "all within limits" else "$bad outside limits")
                        }
                        Text("Open", color = Brand.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    for (t in vm.tests.filter { !it.pass }.take(3)) Hint("•  ${t.name}: ${t.value} (limits ${t.min}–${t.max})")
                }
            }
            if (vm.moduleResults.isNotEmpty()) ModulesPanel(vm)
            if (vm.hiddenFaults.isNotEmpty()) Panel {
                Title("Hidden in Obdy (${vm.hiddenFaults.size})")
                Hint("The car still has these codes. They only stay out of your lists.")
                for (f in vm.hiddenFaults) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Label(f.code, bold = true); Hint(f.description) }
                    SoftButton("Show again") { vm.restoreCode(f.code) }
                }
            }
            val m = vm.monitors
            if (m != null) Panel {
                Title("Readiness self-tests")
                val sup = m.monitors.filter { it.supported }
                val done = sup.count { it.complete }
                Hint("$done of ${sup.size} finished. Unfinished tests complete with normal driving.")
                for (pair in sup.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (x in pair) ReadyChip(x.name, x.complete, Modifier.weight(1f))
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                val open = sup.filter { !it.complete }
                if (open.isNotEmpty()) {
                    var showHow by remember { mutableStateOf(false) }
                    Text(if (showHow) "Hide how to finish them" else "How to finish them by driving ›", color = Brand.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { showHow = !showHow }.padding(vertical = 4.dp))
                    AnimatedVisibility(visible = showHow, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (x in open) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Track).padding(10.dp)) {
                                Text(x.name, color = Brand.Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(DriveCycle.tip(x.name), color = Brand.TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            val fz = vm.freeze
            if (fz != null && fz.code != null) Panel {
                Title("Freeze frame: ${fz.code}")
                Hint("What the engine was doing the moment this fault was stored.")
                for ((p, v) in fz.values) Row {
                    Text(p.name, color = Brand.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${Calc.fmt(v, 1)} ${p.unit}", color = Brand.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (vm.scanNotes.isNotEmpty()) {
                var showNotes by remember { mutableStateOf(false) }
                Panel(Modifier.clickable { showNotes = !showNotes }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Title("Technical details"); Spacer(Modifier.weight(1f))
                        Hint(if (showNotes) "Hide" else "Show")
                    }
                    AnimatedVisibility(visible = showNotes, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { for (n in vm.scanNotes) Text(n, color = Brand.TextSecondary, fontSize = 11.sp) }
                    }
                }
            }
            if (on) {
                GradientButton("Clear codes", Brand.DangerBrush, enabled = !vm.clearing && !vm.scanBusy) { confirmClear = true }
                if (vm.faults.any { it.kind == "permanent" }) Hint("Permanent codes clear themselves after a few good drives.")
            }
        }

        Panel {
            Title("What do you notice?")
            Hint("Noise, shaking, when it happens (cold start, braking, A/C on...). It is added to the AI analysis and the report.")
            Field("Symptoms", vm.symptoms, { vm.saveSymptoms(it) }, singleLine = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("AI analysis", Modifier.weight(1f)) { vm.overlay = "ai" }
            SoftButton("History and links", Modifier.weight(1f)) { vm.overlay = "timeline" }
        }
        SoftButton("PDF report", Modifier.fillMaxWidth()) { vm.makeReport() }
        if (vm.scanHistory.isNotEmpty()) Panel {
            Title("Earlier scans")
            for (s in vm.scanHistory.take(6)) Label("${fmtDate(s.timeMs)}  ·  ${s.faults.size} code(s)" + (s.faults.take(3).joinToString(prefix = "  ", transform = { it.code }).takeIf { s.faults.isNotEmpty() } ?: ""), size = 13)
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all codes?") },
            text = { Text((if ((vm.liveValues[0x0C] ?: 0.0) > 300.0) "The engine is running: a fault that is active right now can come back within seconds. " else "") + "This erases the codes AND the freeze frame, turns the warning light off and resets the readiness monitors (the car needs about 50 km of mixed driving to be ready for an emissions test again). The problem is not fixed by clearing: if it is real, the code will return. This scan has been saved in the history.") },
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
            Pill(f.kinds, Brand.Blue)
            val fs = FaultStates.of(f)
            Pill(fs.label, when (fs) { FaultState.Active -> Brand.Red; FaultState.Pending -> Brand.Amber; else -> Brand.TextSecondary })
            Spacer(Modifier.weight(1f))
            if (f.status.isNotBlank()) Text(f.status, color = Brand.TextSecondary, fontSize = 11.sp)
        }
        Label(f.description, size = 14)
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.height(2.dp))
                for (k in f.kinds.split(" + ")) kindText(k)?.let { Label(it, color = Brand.Blue, size = 13) }
                if (f.code.take(5) in setOf("P0011", "P0012", "P0014", "P0016", "P0017", "P0008", "P0009", "P0521", "P0520")) vm.oilNote()?.let { Label("Oil: $it", color = Brand.Amber, size = 12) }
                if (f.code.take(5) in setOf("P0011", "P0010", "P0012", "P0014", "P0016", "P0017")) {
                    val cam = vm.camMonitorLines()
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Track).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Cam timing check", color = Brand.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        if (cam.isEmpty()) Text("Engine self-test for valve timing not read yet: Tools → self-tests (Mode 06).", color = Brand.TextPrimary, fontSize = 12.sp)
                        for (l in cam) Text(l, color = Brand.TextPrimary, fontSize = 12.sp)
                        Text("Does it set on a cold start or only when hot? Is the oil level and grade right? Tell the mechanic both.", color = Brand.TextSecondary, fontSize = 12.sp)
                        if (vm.camTestText.isNotBlank()) Text(vm.camTestText, color = Brand.Amber, fontSize = 12.sp)
                        SoftButton(if (vm.camTestBusy) "Stop cam test" else "Run cam test (idle 60 s + 2,500 rpm 30 s)", Modifier.fillMaxWidth()) { if (vm.camTestBusy) vm.stopCamTest() else vm.startCamTest() }
                    }
                }
                if (f.code.startsWith("C04") || f.code.startsWith("C05") || f.code.startsWith("C08")) SoftButton("Run the power steering check", Modifier.fillMaxWidth()) { vm.overlay = "steering" }
                vm.storyFor(f)?.let { st ->
                    Label("History: first seen ${fmtDate(st.firstMs)}" + (if (st.firstKm > 0) " at %,d km".format(st.firstKm) else "") + "  ·  in ${st.seen} scan(s)" + (if (st.returns > 0) "  ·  came back ${st.returns}×" else ""), color = if (st.returns > 0) Brand.Amber else Brand.TextSecondary, size = 12)
                }
                Label(DtcDb.severityText(sev), color = c, size = 13)
                Label(DtcDb.system(f.code), color = Brand.TextSecondary, size = 12)
                val causes = DtcDb.causes(f.code)
                if (causes.isNotEmpty()) {
                    Label("Likely causes (most common first):", bold = true, size = 13)
                    for (x in causes) Label("•  $x", size = 13)
                } else Hint("No built-in notes for this code. Use the AI analysis for this one.")
                DtcDb.bulletin(f.code)?.let { Label("What GM service information says", bold = true, size = 13); Hint(it) }
                Manual.noteForCode(f.code)?.let { Label("From your manual", bold = true, size = 13); Hint(it) }
                FuseCodeChips(vm, f.code)
                vm.linkFor(f.code)?.let { l -> Label("Possible link: appeared ${l.gapDays} day(s) after \"${l.cause.title}\"", color = Brand.Amber, size = 13) }
                val bits = DtcDb.statusBits(f.status)
                if (bits.isNotEmpty() || f.raw.isNotBlank() || f.code.contains('-')) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Track).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Technical", color = Brand.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (f.code.contains('-')) Text("Failure type ${f.code.substringAfter('-')}: the module's sub-code for HOW it failed (circuit low/high, signal invalid, internal...)", color = Brand.TextPrimary, fontSize = 12.sp)
                    if (bits.isNotEmpty()) Text("Status: " + bits.joinToString(", "), color = Brand.TextPrimary, fontSize = 12.sp)
                    Text("Module: ${f.module}  ·  type: ${f.kind}", color = Brand.TextPrimary, fontSize = 12.sp)
                    if (f.raw.isNotBlank()) Text("Raw: ${f.raw}", color = Brand.TextSecondary, fontSize = 11.sp)
                }
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

private fun kindText(kind: String): String? = when (kind) {
    "permanent" -> "Permanent: stored earlier and kept until the car re-tests the system and it passes. With the warning light off, the fault is not active right now."
    "pending" -> "Pending: seen once; it becomes a stored code (and may light the warning) if it happens again."
    "stored" -> "Stored: confirmed fault."
    "active" -> "Active: the module sees this fault right now."
    "history" -> "History: happened before and is not failing at the moment."
    else -> null
}

@Composable
private fun FaultSummary(vm: ObdyVm) {
    val list = vm.visibleFaults
    val mil = vm.monitors?.milOn == true
    val urgent = list.any { DtcDb.severity(it.code) == 2 && it.kind != "history" && it.kind != "permanent" }
    val (color, head) = when {
        mil || urgent -> Brand.Red to (if (list.isEmpty()) "Warning light on" else "${list.size} fault code${if (list.size == 1) "" else "s"} need attention")
        list.isNotEmpty() -> Brand.Amber to "${list.size} code${if (list.size == 1) "" else "s"} found"
        else -> Brand.Green to "No fault codes"
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                AppIcon(if (list.isEmpty() && !mil) Ic.Heart else Ic.Warning, size = 24.dp, color = color)
            }
            Column(Modifier.weight(1f)) {
                Text(head, color = Brand.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                val modules = if (vm.lastDeepMs > 0) "${vm.moduleResults.size} modules read" else "engine and transmission only"
                Hint("Warning light ${if (mil) "ON" else "off"}  ·  $modules  ·  ${fmtDate(vm.lastScanMs)}")
            }
        }
    }
}

@Composable
private fun ScanBar(frac: Float?) {
    val target = frac ?: 0f
    val anim by animateFloatAsState(targetValue = target, animationSpec = tween(400), label = "scanbar")
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Brand.Track)) {
        if (frac != null) Box(Modifier.fillMaxWidth(anim).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Brand.MainBrush))
    }
}

@Composable
private fun ModulesPanel(vm: ObdyVm) {
    var openId by remember { mutableStateOf<String?>(null) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("Modules"); Spacer(Modifier.weight(1f))
            val withCodes = vm.moduleResults.count { (it.codes ?: 0) > 0 }
            Pill("${vm.moduleResults.size} found", Brand.Blue)
            if (withCodes > 0) { Spacer(Modifier.width(6.dp)); Pill("$withCodes with codes", Brand.Red) }
        }
        Hint("Every computer that answered the deep scan${if (vm.lastDeepMs > 0) " (" + fmtDate(vm.lastDeepMs) + ")" else ""}. Tap one for its identity and codes.")
        val silent = vm.moduleResults.count { it.codes == null }
        if (silent > 0) Hint("$silent module${if (silent == 1) "" else "s"} answered but gave no fault list: NOT checked, so not proven fault-free. A module that did not answer at all (asleep, or on the second, single-wire bus) was not scanned either; run Tools > Second bus for that one.")
        for (r in vm.moduleResults) {
            val key = r.ids
            val (col, txt) = when {
                r.codes == null -> Brand.Amber to "not checked"
                r.codes == 0 -> Brand.Green to "no faults flagged"
                else -> Brand.Red to "${r.codes} code${if (r.codes == 1) "" else "s"}"
            }
            val isOpen = openId == key
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (isOpen) Brand.Track else Color.Transparent).clickable { openId = if (isOpen) null else key }.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        AppIcon(moduleIcon(r.name), size = 18.dp, color = col)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, color = Brand.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(r.ids + "  ·  " + r.how, color = Brand.TextSecondary, fontSize = 11.sp)
                    }
                    Pill(txt, col)
                }
                AnimatedVisibility(visible = isOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val codes = vm.faults.filter { it.module == r.name }
                        if (codes.isEmpty()) Hint(if (r.codes == null) "This module answers but did not give a fault list with any known request." else "No codes stored in this module.")
                        for (f in codes) Row {
                            Text(f.code, color = Brand.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp))
                            Text(f.description, color = Brand.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                        val m = r.module
                        val info = m?.let { vm.moduleInfo[it.probe.resp] }
                        if (info != null) {
                            Text("Identity", color = Brand.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            for ((k, v) in info.items) Row {
                                Text(k, color = Brand.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(v, color = Brand.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.3f))
                            }
                        }
                        if (m != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SoftButton(if (vm.infoBusy == m.probe.resp) "Reading..." else if (info == null) "Read identity" else "Read again", Modifier.weight(1f), enabled = vm.infoBusy == null && vm.conn == Conn.On) { vm.readInfo(m) }
                            SoftButton("Rescan codes", Modifier.weight(1f), enabled = !vm.scanBusy && vm.conn == Conn.On) { vm.rescanModule(m) }
                        }
                        if (m != null) SoftButton(if (vm.explorerBusy == m.probe.resp) "Exploring..." else "Explore all its data", Modifier.fillMaxWidth(), enabled = vm.explorerBusy == null && vm.conn == Conn.On) { vm.exploreModule(m); vm.overlay = "explorer" }
                    }
                }
            }
        }
    }
}

private fun moduleIcon(name: String): Ic {
    val n = name.lowercase()
    return when {
        "engine" in n || "fuel" in n -> Ic.Fuel
        "transmission" in n -> Ic.Gear
        "brake" in n || "airbag" in n -> Ic.Shield
        "body" in n || "cluster" in n -> Ic.Car
        else -> Ic.Cpu
    }
}

@Composable
private fun ReadyChip(name: String, done: Boolean, modifier: Modifier) {
    val c = if (done) Brand.Green else Brand.Amber
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (done) "✓" else "…", color = c, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
        Text(name, color = Brand.TextPrimary, fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
private fun MileageCard(vm: ObdyVm) {
    val km = vm.car.odometerKm
    val on = vm.conn == Conn.On
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                AppIcon(Ic.Gauge, size = 22.dp, color = Brand.Accent)
            }
            Column(Modifier.weight(1f)) {
                Text("Mileage", color = Brand.TextSecondary, fontSize = 12.sp)
                Text(if (km > 0) "%,d km".format(km) else "Not set", color = Brand.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            val (c, t) = when (vm.odoSource) {
                ObdyVm.OdoSource.Car -> Brand.Green to "live"
                ObdyVm.OdoSource.Tracked -> Brand.Green to "auto"
                ObdyVm.OdoSource.Module -> Brand.Green to "module"
                ObdyVm.OdoSource.Manual -> Brand.Amber to "manual"
                ObdyVm.OdoSource.Unknown -> Brand.TextSecondary to "not set"
            }
            Pill(t, c)
        }
        if (km <= 0) {
            Hint("Type the mileage from your dash once; Obdy then follows it automatically with the car's own distance counter.")
            SoftButton("Enter mileage", Modifier.fillMaxWidth()) { vm.tab = 4 }
        } else {
            val bits = ArrayList<String>()
            vm.lastD31?.let { bits.add("$it km since codes were cleared") }
            vm.kmPerDay()?.let { bits.add("about ${Calc.fmt(it, 0)} km a day") }
            if (vm.odoSyncMs > 0) bits.add("checked ${fmtDate(vm.odoSyncMs)}")
            if (bits.isNotEmpty()) Hint(bits.joinToString("  ·  "))
            val urgent = vm.dueList().filter { it.overdue || it.soon }
            for (d in urgent.take(3)) Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(if (d.overdue) Brand.Red else Brand.Amber); Spacer(Modifier.width(8.dp))
                Text((if (d.overdue) "Overdue: " else "Soon: ") + d.item.name, color = if (d.overdue) Brand.Red else Brand.Amber, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
            if (urgent.isNotEmpty()) Text("Open service list ›", color = Brand.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { vm.tab = 4 })
            val next = vm.dueList().filter { it.kmLeft != null && !it.overdue && !it.soon }.minByOrNull { it.kmLeft!! }
            if (next != null) Label(if (next.kmLeft!! < 0) "${next.item.name}: overdue by %,d km".format(-next.kmLeft) else "Next: ${next.item.name} in %,d km".format(next.kmLeft), color = if (next.kmLeft < 0) Brand.Red else if (next.kmLeft <= 500) Brand.Amber else Brand.TextPrimary, size = 14)
            if (on) SoftButton("Update from the car", Modifier.fillMaxWidth()) { vm.syncOdometerNow() }
        }
    }
}

@Composable
private fun HealthDashboard(vm: ObdyVm) {
    // Recomputed only when its inputs change (not on every live reading).
    val h = remember(vm.faults, vm.monitors, vm.batHistory, vm.tests, vm.lastScanMs, vm.car.odometerKm, vm.maint) { vm.carHealth() }
    val col = levelColor(h.level)
    val anim by animateFloatAsState(targetValue = h.score / 100f, animationSpec = tween(1100), label = "health")
    var open by remember { mutableStateOf(false) }
    Panel(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
                val track = Brand.Track
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val sw = 9.dp.toPx()
                    drawArc(track, 135f, 270f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawArc(col, 135f, 270f * anim, false, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(anim * 100).toInt()}", color = Brand.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("health", color = Brand.TextSecondary, fontSize = 10.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(h.headline, color = Brand.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                for (f in h.factors.take(if (open) 10 else 3)) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                    Dot(levelColor(f.level)); Spacer(Modifier.width(6.dp))
                    Text(f.name, color = Brand.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (f.penalty > 0) Text("−${f.penalty}", color = levelColor(f.level), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (vm.visibleFaults.isNotEmpty()) {
            for (f in vm.visibleFaults.take(3)) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.code, color = Brand.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp))
                Text(f.description, color = Brand.TextPrimary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
            }
            Text("All codes and causes ›", color = Brand.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { vm.tab = 2 })
        }
        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { for (f in h.factors) Hint("${f.name}: ${f.text}") }
        }
        if (!open) Hint(if (vm.lastScanMs > 0) "Last scan ${fmtDate(vm.lastScanMs)} · tap for details" else "Tap for details")
    }
}

@Composable
fun BigStat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.10f)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Brand.TextSecondary, fontSize = 11.sp)
        AnimatedContent(targetState = value, label = "stat") { v -> Text(v, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun OdoFinderPanel(vm: ObdyVm) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Track).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label("Read the odometer from a module", bold = true, size = 13)
        Hint("Your car does not send the odometer as a standard value.")
        if (vm.odoSource == ObdyVm.OdoSource.Module) {
            Label("In use: mileage is read from a module.", color = Brand.Green, size = 13)
            SoftButton("Stop using it", Modifier.fillMaxWidth()) { vm.forgetOdoModule() }
        } else SoftButton(if (vm.explorerBusy == "odo") "Searching... ${vm.scanProgress}" else "Search the modules", Modifier.fillMaxWidth(), enabled = vm.explorerBusy == null && vm.conn == Conn.On) { vm.findOdometer() }
        if (vm.odoSearchDone && vm.odoCandidates.isEmpty()) Hint("No match. Distance-counter tracking keeps working.")
        for (c in vm.odoCandidates.take(6)) Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("%,d km".format(c.value.toInt()), color = Brand.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("${c.module} · ${c.request} · bytes ${c.offset}+${c.len}${if (c.miles) " · miles" else ""}", color = Brand.TextSecondary, fontSize = 11.sp)
            }
            SoftButton("Use") { vm.useOdoCandidate(c) }
        }
    }
}

// ====================================================================================== Tools
private class ToolItem(val ic: Ic, val title: String, val sub: String, val open: () -> Unit)

private class ToolGroup(val name: String, val ic: Ic, val note: String?, val items: List<ToolItem>)

@Composable
fun ToolsScreen(vm: ObdyVm) {
    var query by remember { mutableStateOf("") }
    var openGroup by remember { mutableStateOf<String?>("Diagnose") }
    val start = listOf(
        ToolItem(Ic.Battery, "Battery test", "Start, charging") { vm.overlay = "battery" },
        ToolItem(Ic.Heart, "Health check", "Idle test, trends") { vm.overlay = "health" },
        ToolItem(Ic.Wrench, "Power steering", "Service Power Steering") { vm.overlay = "steering" },
        ToolItem(Ic.Search, "Find the cause", "Questions, then a fix") { vm.gCode = null; vm.overlay = "guided" },
    )
    val groups = listOf(
        ToolGroup("Diagnose", Ic.Cpu, null, listOf(
            ToolItem(Ic.Cpu, "Engine self-tests", "Misfire counts, catalyst") { vm.overlay = "tests" },
            ToolItem(Ic.Cpu, "Gearbox", "Shifts, lever position") { vm.overlay = "gearbox" },
            ToolItem(Ic.Cpu, "Network watch", "Which module drops out") { vm.overlay = "netwatch" },
            ToolItem(Ic.Cpu, "Module inventory", "Part numbers, software, VIN") { vm.overlay = "inventory" },
            ToolItem(Ic.Shield, "Inspection check", "Ready for the test?") { vm.overlay = "inspection" },
            ToolItem(Ic.Doc, "PDF report", "For your mechanic") { vm.makeReport() },
        )),
        ToolGroup("Fix and find", Ic.Wrench, null, listOf(
            ToolItem(Ic.Wrench, "Relearn guides", "After battery or cleaning") { vm.overlay = "relearn" },
            ToolItem(Ic.Pin, "Where is the part?", "Diagram of your Malibu") { vm.showParts(null) },
            ToolItem(Ic.Plug, "Fuse boxes", "Find a fuse") { vm.fuseSel = null; vm.overlay = "fuses" },
        )),
        ToolGroup("Drive and costs", Ic.Car, null, listOf(
            ToolItem(Ic.Car, "Driving mode", "Big gauges and alerts") { vm.overlay = "drive" },
            ToolItem(Ic.Gauge, "Performance", "0-100, braking, idle time") { vm.overlay = "perf" },
            ToolItem(Ic.Fuel, "Fuel and trips", "Trips, fill-ups, L/100 km") { vm.overlay = "trips" },
            ToolItem(Ic.Cpu, "Parts register", "Fitted parts and warranty") { vm.overlay = "partsreg" },
        )),
        ToolGroup("Expert", Ic.Terminal, "Raw adapter and network tools. They only listen and ask; nothing is changed except in Active tests, which has safety checks.", listOf(
            ToolItem(Ic.Plug, "Adapter doctor", "Test the adapter") { vm.overlay = "doctor" },
            ToolItem(Ic.Plug, "Second bus (pin 1)", "Single-wire GM network") { vm.overlay = "buses" },
            ToolItem(Ic.Terminal, "Bus monitor", "Listen to CAN, find signals") { vm.overlay = "monitor" },
            ToolItem(Ic.Gauge, "Live signals", "Your named CAN signals") { vm.overlay = "signals" },
            ToolItem(Ic.Search, "Data sweep", "Find readable values") { vm.overlay = "sweep" },
            ToolItem(Ic.Wrench, "Active tests", "Make a part work, guarded") { vm.overlay = "atests" },
        )),
    )
    val all = start + groups.flatMap { it.items }
    ScreensFrameTools(vm, query, { query = it }, start, groups, all, openGroup) { openGroup = if (openGroup == it) null else it }
}

@Composable
private fun ScreensFrameTools(
    vm: ObdyVm, query: String, onQuery: (String) -> Unit, start: List<ToolItem>, groups: List<ToolGroup>, all: List<ToolItem>,
    openGroup: String?, toggle: (String) -> Unit,
) {
    ScreenFrame("Tools", null, subtitle = "${all.size} tools in ${groups.size} groups") {
        Field("Search tools", query, onQuery)
        val q = query.trim()
        if (q.isNotEmpty()) {
            val hits = all.filter { it.title.contains(q, true) || it.sub.contains(q, true) }
            if (hits.isEmpty()) Hint("No tool matches \"$q\".")
            Panel { for (t in hits) ToolRow(t) }
        } else {
            Reveal(0) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    start.chunked(2).forEachIndexed { row, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (t in pair) Tile(t.ic, t.title, t.sub, Modifier.weight(1f), accent = row == 0 && t == pair.first()) { t.open() }
                        }
                    }
                }
            }
            groups.forEachIndexed { gi, g ->
                Reveal(1 + gi) {
                    val open = openGroup == g.name
                    val turn by animateFloatAsState(if (open) 90f else 0f, tween(180), label = "turn")
                    Panel {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { com.example.obdy.AppLog.i("[YOU] tapped group \"${g.name}\""); toggle(g.name) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconChip(g.ic, g.name, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.name, color = Brand.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(g.items.take(3).joinToString(", ") { it.title } + if (g.items.size > 3) "…" else "", color = Brand.TextSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            Text("${g.items.size}", color = Brand.TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("›", color = Brand.TextSecondary, fontSize = 22.sp, modifier = Modifier.rotate(turn))
                        }
                        AnimatedVisibility(open) {
                            Column {
                                g.note?.let { Hint(it) }
                                g.items.forEachIndexed { i, t ->
                                    if (i > 0 || g.note != null) Box(Modifier.fillMaxWidth().height(1.dp).background(Brand.CardBorder.copy(alpha = 0.6f)))
                                    ToolRow(t)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Compact list row for a tool: icon, name, one line, chevron. */
@Composable
private fun ToolRow(t: ToolItem) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); com.example.obdy.AppLog.i("[YOU] tapped tool \"${t.title}\""); t.open() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(t.ic, t.title, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Brand.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(t.sub, color = Brand.TextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        Text("›", color = Brand.TextSecondary, fontSize = 22.sp)
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
    var noteOpen by remember { mutableStateOf<String?>(null) }
    ScreenFrame("My car", null) {
        Panel {
            Title("Profile")
            Field("Name", name, { name = it })
            Field("Plate", plate, { plate = it })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Year", year, { year = it }, number = true, modifier = Modifier.weight(1f))
                Field("Mileage (km)", km, { km = it }, number = true, modifier = Modifier.weight(1f))
            }
            Hint("Type the dash mileage once; Obdy keeps it updated (${vm.odoSource.label}).")
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
            SoftButton("Use the manual's schedule (hot-weather severe use)", Modifier.fillMaxWidth()) { vm.applyManualSchedule(true) }
            Hint("Fills the intervals from your owner's manual: oil, filters, plugs, transmission fluid, coolant, belts.")
            Hint("Tap Edit and enter when each job was last done (date and mileage).")
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
                    Row(Modifier.fillMaxWidth().clickable { noteOpen = if (noteOpen == d.item.id) null else d.item.id }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Label(d.item.name, bold = true); Label(status, color = col, size = 12) }
                        if (d.item.note.isNotBlank()) AppIcon(Ic.Info, size = 18.dp, color = Brand.TextSecondary)
                    }
                    AnimatedVisibility(visible = noteOpen == d.item.id && d.item.note.isNotBlank(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { Hint(d.item.note) }
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
            SoftButton("Car guide", Modifier.weight(1f)) { vm.overlay = "guide" }
            SoftButton("History and links", Modifier.weight(1f)) { vm.overlay = "timeline" }
        }
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
                    Hint("Use the date and mileage of your last full service, or today if you are starting fresh.")
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
