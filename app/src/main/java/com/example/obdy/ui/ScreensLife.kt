package com.example.obdy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.obdy.Calc
import com.example.obdy.Checklist
import com.example.obdy.Conn
import com.example.obdy.Docs
import com.example.obdy.Expenses
import com.example.obdy.FuelEntry
import com.example.obdy.FuelLog
import com.example.obdy.ObdyVm
import com.example.obdy.PartRecord

@Composable
fun BarChart(items: List<Pair<String, Double>>, height: Int = 130) {
    val max = (items.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
    Row(Modifier.fillMaxWidth().height(height.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        for ((label, v) in items) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(Calc.fmt(v, 0), fontSize = 9.sp, color = Brand.TextSecondary)
                Box(Modifier.fillMaxWidth().height(((height - 36) * (v / max)).toFloat().dp).clip(RoundedCornerShape(4.dp)).background(Brand.MainBrush))
                Text(label, fontSize = 10.sp, color = Brand.TextSecondary)
            }
        }
    }
}

// ====================================================================================== Battery health card
@Composable
fun BatteryHealthCard(vm: ObdyVm) {
    val h = vm.batteryHealth()
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(Ic.Battery, size = 22.dp, color = Brand.Accent); Title("Battery health")
            if (h != null) Pill("${h.score} / 100", levelColor(h.level))
        }
        if (h == null) {
            Hint("Run a battery test, and add the battery's fitting date in Parts register, for a health score and a replacement estimate.")
        } else {
            ProgressBar(h.score / 100f)
            Label(h.summary, bold = true, size = 14)
            for (d in h.details) Hint(d)
            h.replaceByMs?.let { Label("Plan to replace it by about " + Docs.formatDate(it), color = if (h.level == com.example.obdy.Level.Good) Brand.TextSecondary else Brand.Amber, size = 13) }
        }
    }
}

// ====================================================================================== Where I parked
@Composable
fun ParkingCard(vm: ObdyVm) {
    val p = vm.parking
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { AppIcon(Ic.Pin, size = 22.dp, color = Brand.Accent); Title("Where I parked") }
        if (p != null) {
            Label("Saved ${fmtDate(p.timeMs)}" + if (p.accuracyM > 0) "  (about ${p.accuracyM} m)" else "", size = 14)
            SoftButton("Open in Maps", Modifier.fillMaxWidth()) { vm.openParkingInMaps() }
        } else Hint(if (vm.parkingOn) "Saved automatically when the adapter disconnects (car off)." else "Turn on \"Remember where I parked\" in Settings, or save the spot by hand.")
        SoftButton("Save my current spot", Modifier.fillMaxWidth()) { vm.saveParkingHere(true) }
    }
}

// ====================================================================================== Symptom button
@Composable
fun SymptomButton(vm: ObdyVm, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    SoftButton("Mark symptom now", modifier, enabled = vm.conn == Conn.On) { open = true }
    if (open) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("It is happening now") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint("Saves the last minute of live data as a recording, and a note in your history.")
                    Field("What do you notice? (optional)", note, { note = it })
                }
            },
            confirmButton = { TextButton(onClick = { vm.markSymptom(note); open = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

// ====================================================================================== Fuel log
@Composable
fun FuelLogScreen(vm: ObdyVm) {
    var editing by remember { mutableStateOf<FuelEntry?>(null) }
    val seg = remember(vm.fuel) { FuelLog.segments(vm.fuel) }
    val avg = FuelLog.averageL100(seg)
    val cpk = FuelLog.costPerKm(seg)
    val months = remember(vm.fuel) { FuelLog.monthly(vm.fuel) }
    val year = Docs.formatDate(System.currentTimeMillis()).substring(0, 4)
    val fuelYear = vm.fuel.filter { Docs.formatDate(it.timeMs).startsWith(year) }.sumOf { it.cost }
    val repYear = vm.repairs.filter { Docs.formatDate(it.timeMs).startsWith(year) && it.category != "Fuel" }.sumOf { it.cost }
    ScreenFrame("Fuel log", { vm.overlay = null }) {
        Panel {
            Title("Fill-ups")
            Hint("Fill the tank each time and tick \"full tank\". Obdy then works out the real litres per 100 km, and checks its own trip estimate against it.")
            GradientButton("Add a fill-up", Brand.MainBrush) { editing = FuelEntry(vm.newId(), System.currentTimeMillis(), vm.car.odometerKm, 0.0, 0.0, true, "") }
        }
        Panel {
            Title("Your numbers")
            if (avg == null) Hint("Two full-tank fill-ups are needed to calculate consumption.")
            else {
                Label("Average: ${Calc.fmt(avg, 1)} L per 100 km", bold = true)
                cpk?.let { Label("Cost: ${Calc.fmt(it * 100, 2)} per 100 km  (${Calc.fmt(it, 3)} per km)", size = 14) }
                Hint("From ${seg.size} full-tank stretch(es), ${seg.sumOf { it.km }} km.")
            }
            Label("This year: fuel ${Calc.fmt(fuelYear, 2)}  ·  repairs and service ${Calc.fmt(repYear, 2)}  ·  total ${Calc.fmt(fuelYear + repYear, 2)}", size = 13, color = Brand.TextSecondary)
        }
        if (seg.size >= 2) Panel {
            Title("Litres per 100 km, tank by tank")
            LineChart(listOf(ChartSeries("L/100 km", seg.mapIndexed { i, s -> i.toDouble() to s.l100 }, Brand.Accent)), height = 120)
        }
        if (months.isNotEmpty()) Panel {
            Title("Fuel cost per month")
            BarChart(months.takeLast(6).map { it.first.substring(5) to it.second })
        }
        Panel {
            Title("Trip estimate calibration")
            val sug = vm.fuelCalibrationSuggestion()
            Label("Current factor: x${Calc.fmt(vm.fuelCal, 2)}", size = 14)
            if (sug == null) Hint("Needs fill-ups and recorded trips (Live > Start trip) over the same period.")
            else if (kotlin.math.abs(sug - vm.fuelCal) < 0.02) Label("The estimate matches the pump within 2%.", color = Brand.Green, size = 14)
            else {
                Label("The pump says the estimate should be x${Calc.fmt(sug, 2)}.", color = Brand.Amber, size = 14)
                SoftButton("Apply x${Calc.fmt(sug, 2)}", Modifier.fillMaxWidth()) { vm.applyFuelCal(sug) }
            }
        }
        for (e in vm.fuel.take(40)) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label(Docs.formatDate(e.timeMs) + "  ·  ${e.km} km", bold = true)
                        Label("${Calc.fmt(e.liters, 1)} L" + (if (e.cost > 0) "  ·  ${Calc.fmt(e.cost, 2)}  (${Calc.fmt(e.cost / maxOf(e.liters, 0.1), 2)}/L)" else "") + (if (e.full) "" else "  ·  part fill"), size = 13)
                        if (e.station.isNotBlank()) Hint(e.station)
                    }
                    SoftButton("Edit") { editing = e }
                    Spacer(Modifier.width(6.dp))
                    SoftButton("Delete") { vm.deleteFuel(e) }
                }
            }
        }
    }
    val e = editing
    if (e != null) {
        var date by remember(e.id) { mutableStateOf(Docs.formatDate(e.timeMs)) }
        var km by remember(e.id) { mutableStateOf(if (e.km > 0) e.km.toString() else "") }
        var liters by remember(e.id) { mutableStateOf(if (e.liters > 0) Calc.fmt(e.liters, 2) else "") }
        var cost by remember(e.id) { mutableStateOf(if (e.cost > 0) Calc.fmt(e.cost, 2) else "") }
        var station by remember(e.id) { mutableStateOf(e.station) }
        var full by remember(e.id) { mutableStateOf(e.full) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Fill-up") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Date (YYYY-MM-DD)", date, { date = it })
                    Field("Mileage at the pump (km)", km, { km = it }, number = true)
                    Field("Litres", liters, { liters = it }, number = true)
                    Field("Total cost", cost, { cost = it }, number = true)
                    Field("Station (optional)", station, { station = it })
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = full, onCheckedChange = { full = it }); Spacer(Modifier.width(8.dp)); Label("Filled to full", size = 14) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ms = Docs.parseDate(date); val k = km.trim().toIntOrNull(); val l = liters.trim().toDoubleOrNull()
                    if (ms == null) vm.say("Write the date like 2026-03-15")
                    else if (k == null || k <= 0) vm.say("Enter the mileage")
                    else if (l == null || l <= 0.0) vm.say("Enter the litres")
                    else { vm.saveFuel(FuelEntry(e.id, ms, k, l, cost.trim().toDoubleOrNull() ?: 0.0, full, station.trim())); editing = null }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

// ====================================================================================== Parts register
@Composable
fun PartsRegisterScreen(vm: ObdyVm) {
    var editing by remember { mutableStateOf<PartRecord?>(null) }
    val now = System.currentTimeMillis()
    ScreenFrame("Parts register", { vm.overlay = null }) {
        Panel {
            Title("Parts you fitted")
            Hint("Battery, tyres, brake pads, alternator... with the date, mileage and warranty. Obdy reminds you 30 days before a warranty ends, and uses the battery's age for its health score.")
            GradientButton("Add a part", Brand.MainBrush) { editing = PartRecord(vm.newId(), "", now, vm.car.odometerKm, 0, 0, "") }
        }
        if (vm.registry.isEmpty()) Hint("Nothing registered yet.")
        for (p in vm.registry) {
            val d = p.daysLeft(now); val k = p.kmLeft(vm.car.odometerKm)
            val covered = p.covered(now, vm.car.odometerKm)
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Label(p.name, bold = true)
                        Hint("Fitted " + Docs.formatDate(p.installedMs) + (if (p.km > 0) "  ·  ${p.km} km" else ""))
                    }
                    if (d != null || k != null) Pill(if (covered) "In warranty" else "Warranty ended", if (covered) Brand.Green else Brand.TextSecondary)
                }
                if (covered) Label(listOfNotNull(d?.let { "$it days left" }, k?.let { "$it km left" }).joinToString("  ·  "), size = 13, color = if ((d ?: 999) <= 30) Brand.Amber else Brand.TextSecondary)
                if (p.notes.isNotBlank()) Hint(p.notes)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Edit", Modifier.weight(1f)) { editing = p }
                    SoftButton("Delete", Modifier.weight(1f)) { vm.deleteRegistryPart(p) }
                }
            }
        }
    }
    val e = editing
    if (e != null) {
        var name by remember(e.id) { mutableStateOf(e.name) }
        var date by remember(e.id) { mutableStateOf(Docs.formatDate(e.installedMs)) }
        var km by remember(e.id) { mutableStateOf(if (e.km > 0) e.km.toString() else "") }
        var wm by remember(e.id) { mutableStateOf(if (e.warrantyMonths > 0) e.warrantyMonths.toString() else "") }
        var wk by remember(e.id) { mutableStateOf(if (e.warrantyKm > 0) e.warrantyKm.toString() else "") }
        var notes by remember(e.id) { mutableStateOf(e.notes) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Part") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Part (for example Battery)", name, { name = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (s in listOf("Battery", "Tyres", "Brake pads")) SoftButton(s, Modifier.weight(1f)) { name = s }
                    }
                    Field("Fitted on (YYYY-MM-DD)", date, { date = it })
                    Field("Mileage then (km)", km, { km = it }, number = true)
                    Field("Warranty (months, 0 = none)", wm, { wm = it }, number = true)
                    Field("Warranty (km, 0 = none)", wk, { wk = it }, number = true)
                    Field("Notes (brand, receipt number...)", notes, { notes = it }, singleLine = false)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ms = Docs.parseDate(date)
                    if (ms == null) vm.say("Write the date like 2026-03-15")
                    else if (name.isBlank()) vm.say("Give the part a name")
                    else { vm.saveRegistryPart(PartRecord(e.id, name.trim(), ms, km.trim().toIntOrNull() ?: 0, wm.trim().toIntOrNull() ?: 0, wk.trim().toIntOrNull() ?: 0, notes.trim())); editing = null }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

// ====================================================================================== Quote checker
@Composable
fun QuoteScreen(vm: ObdyVm) {
    val ctx = LocalContext.current
    ScreenFrame("Quote checker", { vm.overlay = null }) {
        Panel {
            Title("Is the workshop quote fair?")
            Hint("Photograph a quote or invoice. The AI reads each line and compares it with your car's service schedule and your repair history: what is needed now, what can wait, what looks optional, what you may already have had done.")
            Hint("It cannot know local prices. It judges need, timing and repeats, and gives you questions to ask.")
            SoftButton(if (vm.quoteImage != null) "Photo attached (tap to change)" else "Attach a photo of the quote", Modifier.fillMaxWidth()) { vm.pick("quote", "image/*") }
            if (vm.activeProvider == null) { Hint("Add an AI service in Settings first."); SoftButton("Open Settings") { vm.overlay = "settings" } }
            GradientButton(if (vm.quoteBusy) "Reading..." else "Check this quote", Brand.AltBrush, enabled = !vm.quoteBusy && vm.activeProvider != null && vm.quoteImage != null) { vm.askQuote() }
            vm.quoteError?.let { Label(it, color = Brand.Red, size = 13) }
        }
        val t = vm.quoteText
        if (t != null) Panel {
            Title("What the AI found")
            SelectionContainer { Text(t, color = Brand.TextPrimary, fontSize = 14.sp) }
            SoftButton("Copy", Modifier.fillMaxWidth()) { copyText(ctx, t); vm.say("Copied") }
            Hint("A guide, not a verdict. Ask the workshop the questions above before you agree.")
        }
    }
}

// ====================================================================================== Checklists
@Composable
fun ChecklistScreen(vm: ObdyVm) {
    var tab by remember { mutableStateOf(0) }
    val list = if (tab == 0) Checklist.preTrip else Checklist.summer
    val kind = if (tab == 0) "pre" else "summer"
    val done = if (tab == 0) vm.checkedPre else vm.checkedSummer
    ScreenFrame("Checklists", { vm.overlay = null }) {
        SegBar(listOf("Before a trip", "Summer check"), tab) { tab = it }
        Hint(if (tab == 0) "30 seconds. Ticks reset every day." else "Once a month in the hot season. Ticks reset every month.")
        Label("${list.count { it.id in done }} of ${list.size} done", bold = true, color = if (list.all { it.id in done }) Brand.Green else Brand.TextPrimary)
        ProgressBar(list.count { it.id in done }.toFloat() / list.size)
        for (c in list) {
            Panel {
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(checked = c.id in done, onCheckedChange = { vm.toggleCheck(kind, c.id) })
                    Column(Modifier.weight(1f).padding(top = 10.dp)) {
                        Label(c.title, bold = true)
                        Hint(c.how)
                        if (c.id == "su_battery") SoftButton("Open Battery test", Modifier.fillMaxWidth()) { vm.overlay = "battery" }
                        if (c.id == "pt_oil" || c.id == "pt_coolant" || c.id == "su_oil" || c.id == "su_coolant") SoftButton("How-to in the Car guide", Modifier.fillMaxWidth()) { vm.overlay = "guide" }
                    }
                }
            }
        }
    }
}

// ====================================================================================== Settings: smart monitoring and backup
@Composable
fun SmartSettingsPanels(vm: ObdyVm) {
    Panel {
        Title("Smart monitoring")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.flightOn, onCheckedChange = { vm.changeFlight(it) }); Spacer(Modifier.width(8.dp))
            Column { Label("Flight recorder", bold = true); Hint("While connected and the app is open, keeps the last minute of live data. It saves that minute by itself when a code appears, the engine overheats, the voltage drops or the idle turns rough. It does not run with the app closed.") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.coldCapture, onCheckedChange = { vm.changeCold(it) }); Spacer(Modifier.width(8.dp))
            Column { Label("Cold-start recording", bold = true); Hint("If the engine is cold when you connect, records the first 3 minutes (many faults only show when cold).") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.parkingOn, onCheckedChange = { vm.changeParking(it) }); Spacer(Modifier.width(8.dp))
            Column { Label("Remember where I parked", bold = true); Hint("Saves the phone's last known location when the adapter disconnects. Stored on the phone only. Needs location permission.") }
        }
    }
    Panel {
        Title("Automatic backup")
        Hint("Once a week, when the app opens, a backup file (without AI keys) is saved to a folder you choose: on the phone, or in Google Drive.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.autoBackup, onCheckedChange = { vm.changeAutoBackup(it) }); Spacer(Modifier.width(8.dp))
            Label("Back up automatically", bold = true)
        }
        Hint(if (vm.backupFolder.isBlank()) "No folder chosen yet." else "Folder chosen.")
        SoftButton("Choose the backup folder", Modifier.fillMaxWidth()) { vm.launchFolderPicker?.invoke() }
        SoftButton("Back up now", Modifier.fillMaxWidth()) { vm.backupNow(true) }
        if (vm.lastBackup > 0) Hint("Last backup: " + fmtDate(vm.lastBackup))
    }
}
