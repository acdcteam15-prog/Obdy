package com.example.obdy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.obdy.ActiveGuard
import com.example.obdy.ActiveTestDef
import com.example.obdy.AlertRule
import com.example.obdy.Bus
import com.example.obdy.Calc
import com.example.obdy.Candidate
import com.example.obdy.Conn
import com.example.obdy.CustomSignal
import com.example.obdy.DidHit
import com.example.obdy.DidState
import com.example.obdy.DidSweep
import com.example.obdy.FaultStates
import com.example.obdy.Gm
import com.example.obdy.ObdyVm
import com.example.obdy.WatchDef

// ====================================================================================== shared bits
@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clickable { com.example.obdy.AppLog.i("[YOU] picked \"$text\""); onClick() }) { Pill(text, if (selected) Brand.Accent else Brand.TextSecondary) }
}

@Composable
private fun NeedCar(vm: ObdyVm) {
    if (vm.conn != Conn.On) { Label("Connect to the car first.", color = Brand.Amber, size = 13); SoftButton("Connect") { vm.overlay = "connect" } }
}

@Composable
private fun Mono(text: String, color: Color = Brand.TextPrimary, size: Int = 12) =
    Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = size.sp)

/** Readable guess of what a payload could be: text, or unsigned / signed numbers. */
private fun guess(b: IntArray): String {
    if (b.isEmpty()) return "(empty)"
    val t = b.filter { it != 0 }
    if (t.size >= 3 && t.all { it in 0x20..0x7E }) return "text: " + t.joinToString("") { it.toChar().toString() }
    val parts = ArrayList<String>()
    parts.add("bytes " + b.joinToString(", "))
    if (b.size >= 2) parts.add("16-bit " + (b[0] shl 8 or b[1]))
    if (b.size == 1) parts.add("−40 = ${b[0] - 40} (if a temperature)")
    return parts.joinToString("  ·  ")
}

private fun busOf(i: Int): Bus = if (i == 0) Bus.Hs else Bus.Sw

// ====================================================================================== both buses (pin 1)
@Composable
fun BusesScreen(vm: ObdyVm) {
    val p = vm.pro
    ScreenFrame("Second bus (pin 1)", { vm.overlay = null }, subtitle = "Low-speed single-wire network") {
        Panel {
            Title("What this does")
            Hint("GM cars have a fast bus (pins 6 and 14, which the Deep scan already reads) and often a slow single-wire bus on pin 1 for body parts.")
            NeedCar(vm)
            GradientButton(if (p.busBusy) "Working..." else "Scan the pin 1 bus", Brand.MainBrush, Modifier.fillMaxWidth(), enabled = !p.busBusy && vm.conn == Conn.On) { p.scanLowSpeed() }
            if (p.busBusy) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusDot(Brand.Accent, pulsing = true); Hint(p.busStatus) } }
        }
        if (p.busLines.isNotEmpty()) Panel {
            Title("Result")
            for (l in p.busLines) Label(l, size = 13)
        }
        for (r in p.swResults) Panel {
            Row(verticalAlignment = Alignment.CenterVertically) { Title(r.name); Spacer(Modifier.weight(1f)); Pill(if (r.codes == null) "no list" else "${r.codes} code(s)", if ((r.codes ?: 0) > 0) Brand.Red else Brand.Green) }
            Hint(r.ids + "  ·  " + r.how)
            for (f in p.swFaults.filter { it.module == r.name }) Row {
                Text(f.code, color = Brand.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp))
                Text(f.description + "  (" + FaultStates.of(f).label + ")", color = Brand.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
        }
        if (p.swModules.isNotEmpty() && p.swResults.isEmpty()) Panel { for (m in p.swModules) Label("${m.name}  ${m.probe.req}/${m.probe.resp}", size = 13) }
    }
}

// ====================================================================================== CAN monitor + signal finder
@Composable
fun MonitorScreen(vm: ObdyVm) {
    val p = vm.pro
    DisposableEffect(Unit) { onDispose { p.stopCapture() } }       // leaving the screen ends the recording
    var bus by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("") }
    var secs by remember { mutableStateOf("10") }
    var naming by remember { mutableStateOf<Candidate?>(null) }
    var sigName by remember { mutableStateOf("") }
    var sigUnit by remember { mutableStateOf("") }
    val seconds = secs.trim().toIntOrNull()?.coerceIn(2, 120) ?: 10
    p.capTick  // reading this makes the table follow the recording
    ScreenFrame("Bus monitor", { vm.overlay = null }, subtitle = "Listen to what the modules say") {
        Panel {
            Title("Listen")
            Hint("Shows every CAN frame (id and data bytes).")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Fast bus (6+14)", bus == 0) { bus = 0 }
                Choice("Pin 1 bus", bus == 1) { bus = 1 }
            }
            Field("CAN ids to listen to (optional)", filter, { filter = it })
            Field("Seconds", secs, { secs = it }, number = true)
            NeedCar(vm)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(if (p.capBusy == "main") "Listening..." else "Start", Brand.MainBrush, Modifier.weight(1f), enabled = p.capBusy == null && vm.conn == Conn.On) { p.capture(busOf(bus), filter, seconds, bus == 1, "main") }
                SoftButton("Stop", Modifier.weight(1f), enabled = p.capBusy != null) { p.stopCapture() }
            }
        }
        val c = p.cap
        if (c != null) Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title("${c.total} frames  ·  ${c.ids.size} ids"); Spacer(Modifier.weight(1f))
                if (c.overflow > 0) Pill("adapter overflow", Brand.Amber)
            }
            if (c.overflow > 0) Hint("The adapter could not keep up. Listen to fewer ids.")
            for (r in c.rows().take(40)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mono(r.id.padEnd(9), Brand.Accent)
                    Mono(Calc.fmt(r.hz(c.durationMs), 0).padStart(4) + " Hz  ", Brand.TextSecondary, 11)
                    Mono(r.last.joinToString(" ") { "%02X".format(it) }, Brand.TextPrimary, 11)
                }
            }
            if (c.ids.size > 40) Hint("…and ${c.ids.size - 40} more ids (all are in the saved log).")
            if (c.total > 0) SoftButton("Share the log (CSV)", Modifier.fillMaxWidth()) { p.shareCapture(c) }
        }

        Panel {
            Title("Signal finder")
            Hint("Find which byte means what.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton(if (p.capBusy == "base") "Recording..." else "1  Record quiet", Modifier.weight(1f), enabled = p.capBusy == null && vm.conn == Conn.On) { p.capture(busOf(bus), filter, 5, bus == 1, "base") }
                SoftButton(if (p.capBusy == "act") "Recording..." else "2  Record the action", Modifier.weight(1f), enabled = p.capBusy == null && vm.conn == Conn.On && p.finderBase != null) { p.capture(busOf(bus), filter, 8, bus == 1, "act") }
            }
            if (p.finderBase != null) Hint("Quiet recording: ${p.finderBase!!.total} frames." + (p.finderAct?.let { "  Action recording: ${it.total} frames." } ?: ""))
            for (cand in p.candidates.take(12)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Brand.Track).padding(8.dp)) {
                    Text(cand.label, color = Brand.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(cand.summary, color = Brand.TextPrimary, fontSize = 12.sp)
                    SoftButton("Save as a signal", Modifier.fillMaxWidth()) { naming = cand; sigName = ""; sigUnit = "" }
                }
            }
        }
        if (p.signals.isNotEmpty()) Panel {
            Title("Saved signals")
            for (s in p.signals) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${s.name}  ·  ${s.id} byte ${s.start + 1}" + (if (s.mask != 0) " bit mask %02X".format(s.mask) else ""), color = Brand.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                SoftButton("Delete") { p.deleteSignal(s.name) }
            }
            SoftButton("Open Live signals", Modifier.fillMaxWidth()) { vm.overlay = "signals" }
        }
    }
    val cand = naming
    if (cand != null) {
        val singleBit = cand.bits.countOneBits() == 1
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text("Name this signal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint(cand.label + (if (singleBit) ": one bit changed, saved as on/off." else ": saved as a number (the byte value)."))
                    Field("Name (for example Door, Brake, Speed)", sigName, { sigName = it })
                    if (!singleBit) Field("Unit (optional)", sigUnit, { sigUnit = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (sigName.isNotBlank()) {
                        p.addSignal(CustomSignal(sigName.trim(), cand.id, cand.byteIndex, 1, if (singleBit) cand.bits else 0, unit = if (singleBit) "" else sigUnit.trim()))
                        vm.say("Saved signal ${sigName.trim()}")
                        naming = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } },
        )
    }
}

// ====================================================================================== live signals, dashboard, alerts
@Composable
fun SignalsScreen(vm: ObdyVm) {
    val p = vm.pro
    DisposableEffect(Unit) { onDispose { p.stopSignals() } }
    var bus by remember { mutableStateOf(0) }
    var sig by remember { mutableStateOf("") }
    var op by remember { mutableStateOf(">") }
    var thr by remember { mutableStateOf("0.5") }
    var msg by remember { mutableStateOf("") }
    var moving by remember { mutableStateOf(false) }
    ScreenFrame("Live signals", { vm.overlay = null }, subtitle = "Your named CAN signals, with alerts") {
        p.sigAlert?.let { a -> Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { AppIcon(Ic.Warning, size = 22.dp, color = Brand.Red); Title(a) }
        } }
        Panel {
            Title("Live")
            Hint("Listens to the ids of your saved signals and shows each value as it changes (several updates per second).")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Fast bus (6+14)", bus == 0) { bus = 0 }
                Choice("Pin 1 bus", bus == 1) { bus = 1 }
            }
            NeedCar(vm)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(if (p.sigRunning) "Listening..." else "Start", Brand.MainBrush, Modifier.weight(1f), enabled = !p.sigRunning && vm.conn == Conn.On) { p.startSignals(busOf(bus)) }
                SoftButton("Stop", Modifier.weight(1f), enabled = p.sigRunning) { p.stopSignals() }
            }
            if (p.sigNote.isNotBlank()) Hint(p.sigNote)
        }
        if (p.signals.isEmpty()) Panel { Hint("No signals yet. Open Bus monitor > Signal finder, record a quiet and an action capture, and save the byte that changed.") }
        for (s in p.signals) {
            val v = p.sigValues[s.name]
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Title(s.name); Hint("${s.id} byte ${s.start + 1}") }
                    Text(if (v == null) "--" else if (s.mask != 0) (if (v > 0.5) "ON" else "off") else Calc.fmt(v, if (s.scale % 1.0 == 0.0) 0 else 1) + (if (s.unit.isNotBlank()) " " + s.unit else ""),
                        color = Brand.Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (p.signals.isNotEmpty()) Panel {
            Title("Alerts")
            Hint("Example: signal Door, is above 0.5, message \"Door open\", only while driving.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (s in p.signals.take(6)) Choice(s.name, sig == s.name) { sig = s.name } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Choice("above", op == ">") { op = ">" }; Choice("below", op == "<") { op = "<" }; Choice("equals", op == "=") { op = "=" } }
            Field("Value", thr, { thr = it })
            Field("Message", msg, { msg = it })
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = moving, onCheckedChange = { moving = it }); Spacer(Modifier.width(8.dp)); Label("Only while the car is moving") }
            SoftButton("Add the alert", Modifier.fillMaxWidth(), enabled = sig.isNotBlank() && msg.isNotBlank() && thr.toDoubleOrNull() != null) {
                p.addRule(AlertRule(sig, op, thr.toDouble(), msg.trim(), moving)); msg = ""
            }
            for (r in p.rules) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${r.signal} ${r.op} ${r.threshold}" + (if (r.onlyMoving) " (moving)" else "") + "  →  ${r.message}", color = Brand.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                SoftButton("Delete") { p.deleteRule(r) }
            }
        }
    }
}

// ====================================================================================== data sweep + watch list
@Composable
fun SweepScreen(vm: ObdyVm) {
    val p = vm.pro
    DisposableEffect(Unit) { onDispose { p.stopSweep(); p.stopWatch() } }
    val mods = p.allModules()
    var adding by remember { mutableStateOf<DidHit?>(null) }
    var wName by remember { mutableStateOf("") }
    var wUnit by remember { mutableStateOf("") }
    var wStart by remember { mutableStateOf("0") }
    var wLen by remember { mutableStateOf("1") }
    var wScale by remember { mutableStateOf("1") }
    var wOff by remember { mutableStateOf("0") }
    ScreenFrame("Data sweep", { vm.overlay = null }, subtitle = "Find what each module can tell (read only)") {
        Panel {
            Title("Scan everything")
            Hint("Asks every module for all the data it can give (read only) and writes each answer into the log. Park the car with the engine running and keep the screen on. It can take up to an hour; you can stop at any time.")
            if (mods.isEmpty()) Hint("Run a Deep scan first so Obdy knows the modules.")
            NeedCar(vm)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(if (p.sweepBusy) "Scanning..." else "Scan everything", Brand.MainBrush, Modifier.weight(1f), enabled = !p.sweepBusy && mods.isNotEmpty() && vm.conn == Conn.On) { p.startFullSweep() }
                SoftButton("Stop", Modifier.weight(1f), enabled = p.sweepBusy) { p.stopSweep() }
            }
            Hint(if (p.recordList.isEmpty()) "Recording while driving: off." else "While you drive, Obdy also records ${p.recordList.size} values the car answered (4 every second or two) into the log.")
            if (p.recordList.isNotEmpty()) SoftButton("Stop recording these", Modifier.fillMaxWidth()) { p.clearRecordList() }
            if (!p.sweepBusy && p.sweepResumeText.isNotBlank()) GradientButton("Continue: ${p.sweepResumeText}", Brand.AltBrush, Modifier.fillMaxWidth(), enabled = vm.conn == Conn.On) { p.startFullSweep(resume = true) }
            if (p.sweepBusy) { ProgressBar(p.sweepFrac); Hint(p.sweepStep + "  ·  ${p.sweepHits.size} found") }
            if (!p.sweepBusy && p.sweepNote.isNotBlank()) Hint(p.sweepNote)
        }
        if (p.sweepHits.isNotEmpty()) Panel {
            Row(verticalAlignment = Alignment.CenterVertically) { Title("${p.sweepHits.size} found"); Spacer(Modifier.weight(1f)); SoftButton("Share CSV") { p.shareSweep() } }
            for (h in p.sweepHits.toList().take(150)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Brand.Track).padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("22 ${h.code}", color = Brand.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (h.state != DidState.Answers) Pill(h.state.label, Brand.Amber)
                    }
                    if (h.state == DidState.Answers) {
                        Text(guess(h.payload), color = Brand.TextPrimary, fontSize = 12.sp)
                        Text(h.hex(), color = Brand.TextSecondary, fontSize = 10.sp)
                        SoftButton("Watch this", Modifier.fillMaxWidth()) { adding = h; wName = ""; wUnit = ""; wStart = "0"; wLen = "1"; wScale = "1"; wOff = "0" }
                    }
                }
            }
        }
        Panel {
            Title("Watch list")
            Hint("Values you chose to watch, read again and again from their module.")
            if (p.watches.isEmpty()) Hint("Nothing yet: press \"Watch this\" on a sweep result.")
            for (w in p.watches) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(w.name, color = Brand.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text("${w.resp} 22 ${w.did}  ·  ${p.watchRaw[w.name] ?: "no answer"}", color = Brand.TextSecondary, fontSize = 11.sp) }
                Text(p.watchValues[w.name]?.let { Calc.fmt(it, if (w.scale % 1.0 == 0.0 && w.offset % 1.0 == 0.0) 0 else 2) + (if (w.unit.isNotBlank()) " " + w.unit else "") } ?: "--", color = Brand.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp)); SoftButton("×") { p.deleteWatch(w.name) }
            }
            NeedCar(vm)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(if (p.watchRunning) "Reading..." else "Start", Brand.AltBrush, Modifier.weight(1f), enabled = !p.watchRunning && p.watches.isNotEmpty() && vm.conn == Conn.On) { p.startWatch() }
                SoftButton("Stop", Modifier.weight(1f), enabled = p.watchRunning) { p.stopWatch() }
            }
        }
    }
    val hit = adding
    val m = hit?.let { p.hitModule(it) }
    if (hit != null && m != null) {
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text("Watch 22 ${hit.code}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint("Answer bytes: ${hit.hex()}. Choose which bytes make the number, and how to scale it.")
                    Field("Name", wName, { wName = it })
                    Field("Unit (optional)", wUnit, { wUnit = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("First byte (0 = first)", wStart, { wStart = it }, number = true, modifier = Modifier.weight(1f))
                        Field("Bytes (1 to 4)", wLen, { wLen = it }, number = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Multiply by", wScale, { wScale = it }, modifier = Modifier.weight(1f))
                        Field("Then add", wOff, { wOff = it }, modifier = Modifier.weight(1f))
                    }
                    Hint("A temperature in one byte is usually: multiply by 1, add −40.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = wStart.toIntOrNull() ?: 0; val l = (wLen.toIntOrNull() ?: 1).coerceIn(1, 4)
                    if (wName.isNotBlank() && s + l <= hit.payload.size) {
                        p.addWatch(WatchDef(wName.trim(), m.probe.req, m.probe.resp, hit.code, s, l, false, true, wScale.toDoubleOrNull() ?: 1.0, wOff.toDoubleOrNull() ?: 0.0, wUnit.trim()))
                        adding = null
                    } else vm.say("Give it a name and pick bytes inside the answer.")
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } },
        )
    }
}

// ====================================================================================== active tests
@Composable
fun ActiveTestsScreen(vm: ObdyVm) {
    val p = vm.pro
    DisposableEffect(Unit) { onDispose { p.stopTest() } }
    var confirm by remember { mutableStateOf<ActiveTestDef?>(null) }
    var title by remember { mutableStateOf("") }
    var req by remember { mutableStateOf("") }
    var resp by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var stop by remember { mutableStateOf("") }
    var secs by remember { mutableStateOf("5") }
    var note by remember { mutableStateOf("") }
    ScreenFrame("Active tests", { vm.overlay = null }, subtitle = "Make a part work, safely") {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { AppIcon(Ic.Warning, size = 22.dp, color = Brand.Amber); Title("Read this first") }
            Hint("An active test sends the car a command that makes one part work (a fan, a relay, a light).")
            Hint("Car must stand still, battery above 11.8 V, max 30 s.")
        }
        for (t in p.tests) Panel {
            Title(t.title)
            Hint("module ${t.req}/${t.resp}  ·  start ${t.start}  ·  stop ${t.stop.ifBlank { "-" }}  ·  ${t.seconds} s" + (if (t.note.isNotBlank()) "\n" + t.note else ""))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton(if (p.testBusy == t.id) "Running..." else "Run", Brand.AltBrush, Modifier.weight(1f), enabled = p.testBusy == null && vm.conn == Conn.On) { confirm = t }
                SoftButton("Stop", Modifier.weight(1f), enabled = p.testBusy == t.id) { p.stopTest() }
                SoftButton("Delete", Modifier.weight(1f), enabled = p.testBusy == null) { p.deleteTest(t.id) }
            }
        }
        if (p.testLog.isNotEmpty()) Panel {
            Title("Log")
            SelectionContainer { Column { for (l in p.testLog) Mono(l) } }
        }
        Panel {
            Title("Add a test")
            Field("Name (for example Cooling fan low)", title, { title = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Module request id", req, { req = it }, modifier = Modifier.weight(1f))
                Field("Module answer id", resp, { resp = it }, modifier = Modifier.weight(1f))
            }
            Field("Start command (hex)", start, { start = it })
            Field("Stop command (hex, a 2F test gets one automatically)", stop, { stop = it })
            Field("Seconds (1 to 30)", secs, { secs = it }, number = true)
            Field("Note (where the command comes from)", note, { note = it })
            SoftButton("Save the test", Modifier.fillMaxWidth(), enabled = title.isNotBlank()) {
                val st = ActiveGuard.cleanHex(start)
                val d = ActiveTestDef(vm.newId(), title.trim(), req.trim().uppercase(), resp.trim().uppercase(), st,
                    ActiveGuard.cleanHex(stop).ifBlank { ActiveGuard.defaultStop(st) }, secs.toIntOrNull() ?: 5, note.trim())
                val why = ActiveGuard.refuse(d, 0.0, 0.0, 13.0)       // checks the command itself (the live conditions are checked again at run time)
                if (why != null) vm.say(why) else { p.addTest(d); title = ""; start = ""; stop = ""; note = "" }
            }
        }
        Panel {
            Title("Service resets")
            Hint("Oil life, TPMS and idle relearns are done from the dash and by driving.")
            SoftButton("Open the relearn guides", Modifier.fillMaxWidth()) { vm.overlay = "relearn" }
        }
    }
    val c = confirm
    if (c != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Run \"${c.title}\"?") },
            text = { Text("The car must stand still. Command ${c.start} is sent to module ${c.req} for ${c.seconds} s, then control is given back. Keep hands, hair and tools away from fans and moving parts.") },
            confirmButton = { TextButton(onClick = { confirm = null; p.runTest(c) }) { Text("Run it") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

// ====================================================================================== module inventory
@Composable
fun InventoryScreen(vm: ObdyVm) {
    val p = vm.pro
    val mods = p.allModules()
    ScreenFrame("Module inventory", { vm.overlay = null }, subtitle = "Every module, its numbers and VIN") {
        Panel {
            Title("All modules")
            Hint("Part, software and VIN numbers of every module found.")
            NeedCar(vm)
            if (mods.isEmpty()) Hint("Run a Deep scan first.")
            GradientButton(if (p.invBusy) "Reading..." else "Read all modules", Brand.MainBrush, Modifier.fillMaxWidth(), enabled = !p.invBusy && mods.isNotEmpty() && vm.conn == Conn.On) { p.readInventory() }
            if (p.invBusy) Hint(p.invProgress)
            if (p.inv.isNotEmpty()) SoftButton("Share as text", Modifier.fillMaxWidth()) { vm.shareText(p.inventoryText()) }
        }
        for (m in mods) {
            val info = p.inv[p.keyOf(m)]
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Title(m.name); Hint("${if (m.probe.bus == Bus.Sw) "pin 1" else "fast bus"}  ·  ${m.probe.req}/${m.probe.resp}") }
                    if (p.vinMismatch(m)) Pill("VIN differs", Brand.Red) else if (info != null) Pill("read", Brand.Green)
                }
                if (info == null) Hint("Not read yet.")
                else for ((k, v) in info.items) Row {
                    Text(k, color = Brand.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(v, color = Brand.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.3f))
                }
                if (p.vinMismatch(m)) Label("This module holds a different VIN than the car (${vm.car.vin}).", color = Brand.Red, size = 13)
            }
        }
    }
}

// ====================================================================================== performance timers
@Composable
fun PerfScreen(vm: ObdyVm) {
    val p = vm.pro
    DisposableEffect(Unit) { onDispose { p.stopPerf() } }
    p.lifeTick
    val best = p.perfRuns.groupBy { it.kind }.mapValues { (_, l) -> l.minOf { it.seconds } }
    ScreenFrame("Performance", { vm.overlay = null }, subtitle = "0–100 km/h, 100–0 and braking distance") {
        Panel {
            Title("Timer")
            Hint("Times 0–60, 0–100 and 100–0 km/h from the car's speed signal.")
            NeedCar(vm)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(if (p.perfRunning) "Timing..." else "Start", Brand.MainBrush, Modifier.weight(1f), enabled = !p.perfRunning && vm.conn == Conn.On) { p.startPerf() }
                SoftButton("Stop", Modifier.weight(1f), enabled = p.perfRunning) { p.stopPerf() }
            }
            if (p.perfRunning) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column { Text(Calc.fmt(p.perfSpeed, 0), color = Brand.Accent, fontSize = 54.sp, fontWeight = FontWeight.Bold); Hint("km/h") }
                    Column { Text(Calc.fmt(p.perfElapsed, 2), color = Brand.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold); Hint("seconds") }
                }
                Label(p.perfHint, size = 13)
                if (p.perfHz > 0) Hint("${Calc.fmt(p.perfHz, 1)} speed readings per second")
            }
        }
        if (p.perfRuns.isNotEmpty()) Panel {
            Title("Results")
            for ((k, v) in best) Row { Text("Best $k", color = Brand.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(Calc.fmt(v, 2) + " s", color = Brand.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Hint("Latest")
            for (r in p.perfRuns.take(12)) Row {
                Text(java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US).format(java.util.Date(r.atMs)) + "  " + r.kind, color = Brand.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(Calc.fmt(r.seconds, 2) + " s" + (if (r.distanceM > 0) "  ·  " + Calc.fmt(r.distanceM, 0) + " m" else ""), color = Brand.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Share CSV", Modifier.weight(1f)) { p.sharePerf() }
                SoftButton("Clear", Modifier.weight(1f)) { p.clearRuns() }
            }
        }
        Panel {
            Title("Driving and idling")
            Hint("Counted automatically while Live data runs (the app keeps it running while connected).")
            val l = p.life
            Row { Text("Driving", color = Brand.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(Calc.fmt(l.driveSec / 3600.0, 1) + " h  ·  " + Calc.fmt(l.km, 0) + " km", color = Brand.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Row { Text("Idling", color = Brand.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(Calc.fmt(l.idleSec / 3600.0, 1) + " h  (" + Calc.fmt(l.idleShare * 100, 0) + " %)", color = Brand.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Row { Text("Fuel used (estimate)", color = Brand.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(Calc.fmt(l.liters, 1) + " L", color = Brand.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            if (l.km >= 1.0 && l.liters > 0.1) Row { Text("Average", color = Brand.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(Calc.fmt(l.liters / l.km * 100, 1) + " L/100 km", color = Brand.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            SoftButton("Reset totals") { p.resetLife() }
        }
    }
}

// ====================================================================================== network watch + computer help
@Composable
fun NetWatchScreen(vm: ObdyVm) {
    val p = vm.pro
    DisposableEffect(Unit) { onDispose { p.stopNetWatch() } }
    val tf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
    ScreenFrame("Network watch", { vm.overlay = null }, subtitle = "Which module drops out, and the voltage at that moment") {
        Panel {
            Title("Record the moment it goes wrong")
            Hint("Asks every module you found in the last Deep scan \"are you there?\" about every two seconds, and reads the battery voltage.")
            NeedCar(vm)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(if (p.netRunning) "Watching..." else "Start", Brand.MainBrush, Modifier.weight(1f), enabled = !p.netRunning && vm.conn == Conn.On) { p.startNetWatch() }
                SoftButton("Stop", Modifier.weight(1f), enabled = p.netRunning) { p.stopNetWatch() }
            }
            if (p.netRounds > 0) {
                Label("${p.netRounds} rounds  ·  battery ${p.netVolt?.let { Calc.fmt(it, 1) } ?: "?"} V (lowest ${p.netMinVolt?.let { Calc.fmt(it, 1) } ?: "?"}, highest ${p.netMaxVolt?.let { Calc.fmt(it, 1) } ?: "?"})", size = 13)
                for ((n, a) in p.netState) Row {
                    Text(n, color = Brand.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Pill(if (a) "answers" else "SILENT", if (a) Brand.Green else Brand.Red)
                }
            }
        }
        if (p.netEvents.isNotEmpty() || p.netRounds > 0) Panel {
            Title("Events")
            if (p.netEvents.isEmpty()) Hint("Nothing yet: every module has answered every round.")
            for (e in p.netEvents.take(40)) Row { Text(tf.format(java.util.Date(e.atMs)) + "  " + e.text, color = Brand.TextPrimary, fontSize = 12.sp) }
            SoftButton("Share report", Modifier.fillMaxWidth()) { p.shareNetWatch() }
        }
        Panel {
            Title("If the computer itself seems to be the problem")
            Label("What an app can and cannot do", bold = true, size = 13)
            Hint("A GM module's software can only be rewritten (\"reflashed\") with GM's programming system (SPS) through a pass-thru adapter and a GM programming subscription, at a dealer or a shop that has it.")
            Label("Check these first (cheap, and they cause strange behaviour)", bold = true, size = 13)
            Label("•  Battery and charging: low or swinging voltage makes modules drop off the network and set steering, brake and gear messages. Test the battery under load and the charging at the battery.", size = 13)
            Label("•  Grounds and connectors: a loose ground or a corroded connector at the steering, brake or body modules.", size = 13)
            Label("•  The adapter itself: GM lists a faulty tool cable or a bad contact at the diagnostic socket as a cause of communication codes. If the problem only happens with the adapter plugged in, drive with it unplugged and compare, and try another adapter.", size = 13)
            Label("•  Do not keep clearing codes: a code in history is a clue. Photograph or share the Deep scan first, then clear.", size = 13)
            Label("What to bring to the shop", bold = true, size = 13)
            Hint("The Deep scan report, the Module inventory (part and software numbers), and this network report.")
            SoftButton("Open the Module inventory", Modifier.fillMaxWidth()) { vm.overlay = "inventory" }
        }
    }
}

/** Live lever position from the gearbox computer (used on the Gearbox screen). */
@Composable
fun LeverPanel(vm: ObdyVm) {
    val p = vm.pro
    DisposableEffect(Unit) { onDispose { p.stopLever() } }
    Panel {
        Title("Lever position, live")
        Hint("Hold the brake and move the lever slowly through P, R, N, D, M.")
        NeedCar(vm)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton(if (p.leverRunning) "Watching..." else "Start", Brand.MainBrush, Modifier.weight(1f), enabled = !p.leverRunning && vm.conn == Conn.On) { p.startLever() }
            SoftButton("Stop", Modifier.weight(1f), enabled = p.leverRunning) { p.stopLever() }
        }
        p.leverNow?.let { Text(Gm.leverName(it), color = Brand.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        p.leverRange?.let { Label("Range switch bits: %02X".format(it), size = 13) }
        for (line in p.leverTrail.take(12)) Label(line, size = 13)
    }
}
