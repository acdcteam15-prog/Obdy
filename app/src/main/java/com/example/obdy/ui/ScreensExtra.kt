package com.example.obdy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.obdy.Calc
import com.example.obdy.Conn
import com.example.obdy.Docs
import com.example.obdy.Expenses
import com.example.obdy.HealthCheck
import com.example.obdy.Level
import com.example.obdy.ObdyVm
import com.example.obdy.RepairRecord
import com.example.obdy.Trends
import com.example.obdy.Verdict

@Composable
fun VerdictList(list: List<Verdict>) {
    for (v in list) {
        Row(verticalAlignment = Alignment.CenterVertically) { Dot(levelColor(v.level)); Spacer(Modifier.width(4.dp)); Label(v.title + (if (v.value != "-") ": " + v.value else ""), bold = true) }
        Hint(v.text)
    }
}

@Composable
fun ProgressBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brand.Track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).background(Brand.Accent))
    }
}

@Composable
private fun NeedConnection(vm: ObdyVm) {
    if (vm.conn != Conn.On) { Label("Connect to the car first.", color = Brand.Amber, size = 13); SoftButton("Connect") { vm.overlay = "connect" } }
}

// ====================================================================================== Adapter doctor
@Composable
fun DoctorScreen(vm: ObdyVm) {
    ScreenFrame("Adapter doctor", { vm.overlay = null }) {
        Panel {
            Title("Check the adapter")
            Hint("Tests the adapter itself: what it says it is, battery voltage at the port, speed, protocol, and whether it can read several values at once. Run it the day the adapter arrives, with the ignition ON.")
            NeedConnection(vm)
            GradientButton(if (vm.doctorBusy) "Testing..." else "Run the test", Brand.MainBrush, enabled = !vm.doctorBusy && vm.conn == Conn.On) { vm.runDoctor() }
        }
        if (vm.doctorResult.isNotEmpty()) Panel { Title("Result"); VerdictList(vm.doctorResult) }
        Hint("If something looks wrong, share the full log from Tools > Console.")
    }
}

// ====================================================================================== Pre-inspection
@Composable
fun InspectionScreen(vm: ObdyVm) {
    val list = vm.inspection()
    ScreenFrame("Inspection check", { vm.overlay = null }) {
        Panel {
            Title("Ready for the registration test?")
            Hint("Looks at the warning light, stored codes and the car's readiness monitors from your last scan.")
            NeedConnection(vm)
            GradientButton(if (vm.scanBusy) "Scanning..." else "Scan now", Brand.MainBrush, enabled = vm.conn == Conn.On && !vm.scanBusy) { vm.scan(false) }
        }
        Panel { VerdictList(list) }
    }
}

// ====================================================================================== Health check and trends
@Composable
fun HealthScreen(vm: ObdyVm) {
    ScreenFrame("Health check", { vm.overlay = null }) {
        Panel {
            Title("Idle health check (25 seconds)")
            Hint("Warm the engine up first (drive 10 minutes), then park, leave it idling with the A/C and lights off, and press Start. It judges idle stability, fuel trims, the oxygen sensor, charging, air flow and vacuum.")
            NeedConnection(vm)
            if (vm.healthBusy) { Label("Measuring... ${vm.healthProgress}%", color = Brand.Amber, bold = true); ProgressBar(vm.healthProgress / 100f) }
            GradientButton(if (vm.healthBusy) "Measuring..." else "Start", Brand.AltBrush, enabled = !vm.healthBusy && vm.conn == Conn.On) { vm.runHealth() }
        }
        if (vm.healthResult.isNotEmpty()) {
            val o = HealthCheck.overall(vm.healthResult)
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Title("Result")
                    Pill(when (o) { Level.Good -> "Healthy"; Level.Warn -> "Needs attention"; Level.Bad -> "Problem found"; Level.Info -> "Not enough data" }, levelColor(o))
                }
                VerdictList(vm.healthResult)
            }
        }
        Panel {
            Title("Trends over time")
            val h = vm.healthHistory
            val first = h.minOfOrNull { it.timeMs } ?: 0L
            var shown = 0
            for ((key, label) in Trends.labels) {
                val s = Trends.series(h, key)
                if (s.size < 2) continue
                shown++
                Label(label, bold = true, size = 14)
                LineChart(listOf(ChartSeries(label, s.map { (it.first - first) / 86_400_000.0 to it.second }, Brand.Accent)), height = 110)
                Hint("Latest ${Calc.fmt(s.last().second, 1)}  ·  ${s.size} checks over ${Calc.fmt((s.last().first - s.first().first) / 86_400_000.0, 0)} days")
                Trends.drift(h, key)?.let { Label(it, color = Brand.Amber, size = 13) }
            }
            if (shown == 0) Hint("Run the health check a few times over several weeks (warm, idling). Graphs and drift warnings appear here, for example a battery that slowly weakens.")
        }
    }
}

// ====================================================================================== Engine self-tests (Mode 06)
@Composable
fun TestsScreen(vm: ObdyVm) {
    ScreenFrame("Engine self-tests", { vm.overlay = null }) {
        Panel {
            Title("What the car measured about itself")
            Hint("Misfire counters per cylinder (even before a code appears), catalyst, oxygen sensors and EVAP leak tests. Numbers are raw values; PASS means inside the limits the car sets for itself.")
            NeedConnection(vm)
            if (vm.testsBusy) Label(vm.testsProgress, color = Brand.Amber, bold = true)
            GradientButton(if (vm.testsBusy) "Reading..." else "Read the tests", Brand.MainBrush, enabled = !vm.testsBusy && vm.conn == Conn.On) { vm.readTests() }
        }
        if (vm.testsRead && vm.tests.isEmpty()) Hint("This car returned no self-test data over the standard service.")
        val fails = vm.tests.filter { !it.pass }
        if (vm.tests.isNotEmpty()) Panel {
            Title(if (fails.isEmpty()) "All ${vm.tests.size} tests inside limits" else "${fails.size} of ${vm.tests.size} tests outside limits")
            for (f in fails.take(10)) Label("${f.name}: ${f.value} (limit ${f.min} to ${f.max}), ${f.what}", color = Brand.Red, size = 13)
            if (fails.isNotEmpty()) Hint("An outside-limit test is an early warning. Misfire counts above the limit on one cylinder point to that cylinder's plug, coil or injector.")
        }
        for ((mid, list) in vm.tests.groupBy { it.mid }) {
            Panel {
                Label(list[0].name, bold = true)
                for (t in list) Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(if (t.pass) Brand.Green else Brand.Red); Spacer(Modifier.width(4.dp))
                    Label("${t.what}: ${t.value}  (${t.min} to ${t.max})", size = 13)
                }
            }
        }
    }
}

// ====================================================================================== Repairs and expenses
@Composable
fun ExpensesScreen(vm: ObdyVm) {
    var editing by remember { mutableStateOf<RepairRecord?>(null) }
    ScreenFrame("Repairs and costs", { vm.overlay = null }) {
        Panel {
            Title("History")
            Label("${vm.repairs.size} entries  ·  total ${Calc.fmt(Expenses.total(vm.repairs), 2)}", bold = true)
            for ((cat, sum) in Expenses.byCategory(vm.repairs)) Label("$cat: ${Calc.fmt(sum, 2)}", size = 13, color = Brand.TextSecondary)
            GradientButton("Add a repair, service or cost", Brand.MainBrush) { editing = RepairRecord(vm.newId(), System.currentTimeMillis(), "", "Service", 0.0, vm.car.odometerKm, "", "", "") }
            Hint("What you already replaced is also sent to the AI, so it does not suggest the same part again. It also appears in the PDF report.")
        }
        for (r in vm.repairs) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label(r.title, bold = true)
                        Hint(Docs.formatDate(r.timeMs) + "  ·  " + r.category + (if (r.km > 0) "  ·  ${r.km} km" else "") + (if (r.workshop.isNotBlank()) "  ·  ${r.workshop}" else ""))
                        if (r.notes.isNotBlank()) Label(r.notes, size = 13)
                    }
                    if (r.cost > 0) Label(Calc.fmt(r.cost, 2), bold = true, color = Brand.Accent)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Edit", Modifier.weight(1f)) { vm.pendingReceipt = ""; editing = r }
                    if (r.receipt.isNotBlank()) SoftButton("Photo", Modifier.weight(1f)) { vm.openImage(r.receipt) }
                    SoftButton("Delete", Modifier.weight(1f)) { vm.deleteRepair(r) }
                }
            }
        }
    }
    val e = editing
    if (e != null) {
        var title by remember(e.id) { mutableStateOf(e.title) }
        var cat by remember(e.id) { mutableStateOf(e.category) }
        var cost by remember(e.id) { mutableStateOf(if (e.cost > 0) Calc.fmt(e.cost, 2) else "") }
        var km by remember(e.id) { mutableStateOf(if (e.km > 0) e.km.toString() else "") }
        var shop by remember(e.id) { mutableStateOf(e.workshop) }
        var notes by remember(e.id) { mutableStateOf(e.notes) }
        var date by remember(e.id) { mutableStateOf(Docs.formatDate(e.timeMs)) }
        val receipt = if (vm.pendingReceipt.isNotBlank()) vm.pendingReceipt else e.receipt
        AlertDialog(
            onDismissRequest = { editing = null; vm.pendingReceipt = "" },
            title = { Text("Repair or cost") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("What was done (for example: spark plugs and coils)", title, { title = it })
                    SoftButton("Category: $cat  (tap to change)", Modifier.fillMaxWidth()) { val i = Expenses.categories.indexOf(cat); cat = Expenses.categories[(i + 1) % Expenses.categories.size] }
                    Field("Date (YYYY-MM-DD)", date, { date = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Cost", cost, { cost = it }, number = true, modifier = Modifier.weight(1f))
                        Field("Mileage (km)", km, { km = it }, number = true, modifier = Modifier.weight(1f))
                    }
                    Field("Workshop or shop", shop, { shop = it })
                    Field("Notes (part numbers, warranty...)", notes, { notes = it }, singleLine = false)
                    SoftButton(if (receipt.isNotBlank()) "Receipt photo attached (tap to change)" else "Attach a receipt photo", Modifier.fillMaxWidth()) { vm.pick("receipt", "image/*") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveRepair(RepairRecord(e.id, Docs.parseDate(date) ?: e.timeMs, title.trim().ifBlank { cat }, cat, cost.trim().toDoubleOrNull() ?: 0.0, km.trim().toIntOrNull() ?: 0, shop.trim(), notes.trim(), receipt))
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null; vm.pendingReceipt = "" }) { Text("Cancel") } },
        )
    }
}

// ====================================================================================== Registration and insurance dates
@Composable
fun DocsPanel(vm: ObdyVm) {
    Panel {
        Title("Registration and insurance")
        Hint("Type the expiry date as YYYY-MM-DD. Obdy reminds you 30 days before and when it has expired.")
        val now = System.currentTimeMillis()
        for (d in vm.docs) {
            var text by remember(d.id, d.dueMs) { mutableStateOf(if (d.dueMs > 0) Docs.formatDate(d.dueMs) else "") }
            val left = if (d.dueMs > 0) d.daysLeft(now) else null
            Label(d.name + (if (left != null) (if (left < 0) "  (expired)" else "  ($left days left)") else ""), bold = true, color = if (left != null && left < 0) Brand.Red else if (left != null && left <= 30) Brand.Amber else Brand.TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field("Expiry date", text, { text = it }, modifier = Modifier.weight(1f))
                SoftButton("Save") { if (vm.saveDoc(d.id, text)) vm.say("Saved") else vm.say("Use the form 2027-03-15") }
            }
        }
    }
}

// ====================================================================================== Chat with the car's data
@Composable
fun ChatScreen(vm: ObdyVm) {
    ScreenFrame("Ask about your car", { vm.overlay = null }) {
        if (vm.activeProvider == null) Panel { Label("Add an AI service first."); SoftButton("Open Settings") { vm.overlay = "settings" } }
        else Hint("Using ${vm.activeProvider?.name}. It sees your codes, freeze frame, live values, repairs and notes. Photos are sent only when you attach one.")
        if (vm.chat.isEmpty()) {
            Panel {
                Title("Try")
                for (q in listOf("Is it safe to drive with my current codes?", "Why might the idle be rough when cold?", "What should I check first before paying a workshop?")) SoftButton(q, Modifier.fillMaxWidth()) { vm.chatDraft = q }
            }
        }
        for (m in vm.chat) {
            Panel(if (m.fromUser) Modifier.padding(start = 40.dp) else Modifier.padding(end = 24.dp)) {
                Label(if (m.fromUser) "You" + (if (m.hasImage) " (with photo)" else "") else "Assistant", bold = true, size = 12, color = if (m.fromUser) Brand.Accent else Brand.TextSecondary)
                SelectionContainer { Text(m.text, color = Brand.TextPrimary, fontSize = 14.sp) }
            }
        }
        if (vm.chatBusy) Label("Thinking...", color = Brand.Amber, bold = true)
        vm.chatError?.let { Label(it, color = Brand.Red, size = 13) }
        Panel {
            Field("Your question", vm.chatDraft, { vm.chatDraft = it }, singleLine = false)
            if (vm.chatImage != null) Label("Photo attached", color = Brand.Green, size = 13)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Voice", Modifier.weight(1f)) { vm.launchVoice?.invoke() }
                SoftButton(if (vm.chatImage != null) "Change photo" else "Photo", Modifier.weight(1f)) { vm.pick("chat", "image/*") }
            }
            GradientButton(if (vm.chatBusy) "Thinking..." else "Send", Brand.MainBrush, enabled = !vm.chatBusy && vm.activeProvider != null && vm.chatDraft.isNotBlank()) { vm.chatSend(vm.chatDraft) }
            if (vm.chat.isNotEmpty()) SoftButton("New conversation", Modifier.fillMaxWidth()) { vm.chat.clear(); vm.chatError = null }
        }
    }
}

// ====================================================================================== Dashboard mode
@Composable
fun DashScreen(vm: ObdyVm) {
    DisposableEffect(Unit) { vm.wantLive("dash", true); onDispose { vm.wantLive("dash", false) } }
    @Composable
    fun Cell(label: String, value: String, unit: String, big: Int, modifier: Modifier, color: Color = Brand.TextPrimary) {
        Column(modifier.padding(6.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Card).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, color = Brand.TextSecondary, fontSize = 13.sp)
            Text(value, color = color, fontSize = big.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(unit, color = Brand.TextSecondary, fontSize = 13.sp)
        }
    }
    fun v(id: Int, digits: Int = 0): String = vm.liveValues[id]?.let { Calc.fmt(it, digits) } ?: "--"
    Box(Modifier.fillMaxSize().background(Brand.BgDeep)) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(Modifier.weight(1.4f).fillMaxWidth()) {
                Cell("Speed", v(0x0D), "km/h", 84, Modifier.weight(1f).fillMaxHeight())
                Cell("Engine", v(0x0C), "rpm", 84, Modifier.weight(1f).fillMaxHeight(), if ((vm.liveValues[0x0C] ?: 0.0) > 5500) Brand.Red else Brand.TextPrimary)
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                val cool = vm.liveValues[0x05] ?: 0.0
                Cell("Coolant", v(0x05), "°C", 44, Modifier.weight(1f).fillMaxHeight(), if (cool >= 108) Brand.Red else if (cool >= 100) Brand.Amber else Brand.TextPrimary)
                val volt = vm.liveValues[0x42]
                Cell("Battery", v(0x42, 1), "V", 44, Modifier.weight(1f).fillMaxHeight(), if (volt != null && (volt < 12.0 || volt > 15.0)) Brand.Red else Brand.TextPrimary)
                Cell("Load", v(0x04), "%", 44, Modifier.weight(1f).fillMaxHeight())
                if (vm.tripOn) Cell("Fuel", vm.instL100?.let { Calc.fmt(it, 1) } ?: Calc.fmt(vm.instLph, 1), if (vm.instL100 != null) "L/100 km" else "L/h", 44, Modifier.weight(1f).fillMaxHeight())
            }
        }
        Text("✕", color = Brand.TextSecondary, fontSize = 26.sp, modifier = Modifier.align(Alignment.TopEnd).clickable { vm.overlay = null }.padding(16.dp))
        if (vm.conn != Conn.On) Text("Not connected", color = Brand.Amber, fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp))
    }
}

// ====================================================================================== Settings: driving, reliability and backup
@Composable
fun ExtraSettingsPanels(vm: ObdyVm) {
    Panel {
        Title("Driving and reliability")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.fastMode, onCheckedChange = { vm.changeFast(it) }); Spacer(Modifier.width(8.dp))
            Column { Label("Fast mode", bold = true); Hint("Reads up to 4 values per request. Switches itself off if the adapter does not support it.") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.autoReconnect, onCheckedChange = { vm.changeReconnect(it) }); Spacer(Modifier.width(8.dp))
            Column { Label("Reconnect automatically", bold = true); Hint("If the link drops during a drive, try again up to 4 times.") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.recordRoute, onCheckedChange = { vm.changeRoute(it) }); Spacer(Modifier.width(8.dp))
            Column { Label("Record the route of trips (GPS)", bold = true); Hint("Saves a GPX file for each trip, stored on the phone. Needs location permission.") }
        }
    }
    Panel {
        Title("Backup and restore")
        Hint("One file with your car profile, scans, trips, repairs, service data and settings. Recordings are not included.")
        SoftButton("Back up (without AI keys)", Modifier.fillMaxWidth()) { vm.exportBackup(false) }
        SoftButton("Back up (with AI keys)", Modifier.fillMaxWidth()) { vm.exportBackup(true) }
        SoftButton("Restore from a backup file", Modifier.fillMaxWidth()) { vm.pick("restore", "*/*") }
    }
}
