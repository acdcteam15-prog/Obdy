package com.example.obdy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.obdy.Docs
import com.example.obdy.Linker
import com.example.obdy.Manual
import com.example.obdy.ObdyVm
import com.example.obdy.Timeline

@Composable
fun SegBar(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.Track).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (sel) Brand.Accent else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (sel) Color.White else Brand.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
        }
    }
}

private fun urgencyColor(u: Int) = when (u) { 2 -> Brand.Red; 1 -> Brand.Amber; else -> Brand.Green }
private fun urgencyText(u: Int) = when (u) { 2 -> "Urgent"; 1 -> "Soon"; else -> "Info" }

// ====================================================================================== Car guide (from the owner's manual)
@Composable
fun GuideScreen(vm: ObdyVm) {
    var tab by remember { mutableStateOf(0) }
    var severe by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(-1) }
    ScreenFrame("Car guide", { vm.overlay = null }, subtitle = "From your 2013 Malibu owner's manual") {
        SegBar(listOf("Specs", "Service", "Lights", "How-to"), tab) { tab = it; open = -1 }
        when (tab) {
            0 -> {
                Panel {
                    Title("Your engine")
                    val e = Manual.engineFromVin(vm.car.vin)
                    Label(e ?: "Connect once so Obdy can read your VIN, then it checks the engine code against this manual.", size = 14, color = if (e == null) Brand.TextSecondary else Brand.TextPrimary)
                }
                Panel {
                    Title("Fluids, fuel and key numbers")
                    for (s in Manual.specs) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Label(s.label, size = 12, color = Brand.TextSecondary)
                            Label(s.value, bold = true)
                            if (s.note.isNotBlank()) Hint(s.note)
                        }
                    }
                }
                Panel {
                    Title("Part numbers")
                    for (p in Manual.parts) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Label(p.name, bold = true)
                            Label("GM ${p.gm}" + (if (p.acdelco != "-") "  ·  ACDelco ${p.acdelco}" else ""), size = 13, color = Brand.Accent)
                            if (p.note.isNotBlank()) Hint(p.note)
                        }
                    }
                }
            }
            1 -> {
                SegBar(listOf("Severe (hot, city)", "Normal"), if (severe) 0 else 1) { severe = it == 0 }
                Hint("The manual calls hot weather in heavy city traffic \"severe\" use. That is the UAE, so Severe is the right choice here.")
                for (d in Manual.services) {
                    val km = if (severe) d.kmSevere else d.kmNormal
                    Panel {
                        Title(d.name)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (km > 0) Pill("every $km km", Brand.Accent)
                            if (d.months > 0) Pill("or ${d.months} months", Brand.Blue)
                            if (km == 0 && d.months == 0) Pill("as needed", Brand.Blue)
                        }
                        Label(d.manual, size = 13)
                        if (d.appNote.isNotBlank()) Label(d.appNote, size = 12, color = Brand.Amber)
                    }
                }
                GradientButton("Use this schedule in my service reminders", Brand.MainBrush) { vm.applyManualSchedule(severe) }
                Hint("Your last-done dates, mileages and your own items are kept.")
            }
            2 -> {
                Field("Search a message or light (for example power steering)", query, { query = it })
                val q = query.trim().lowercase()
                val lights = Manual.lights.filter { q.isEmpty() || (it.text + " " + it.meaning).lowercase().contains(q) }
                val msgs = Manual.messages.filter { q.isEmpty() || (it.text + " " + it.meaning).lowercase().contains(q) }
                if (lights.isEmpty() && msgs.isEmpty()) Hint("Nothing found. Try another word.")
                if (lights.isNotEmpty()) Title("Warning lights")
                for (m in lights) MessageCard(m.text, m.meaning, m.action, m.urgency)
                if (msgs.isNotEmpty()) Title("Dashboard messages")
                for (m in msgs) MessageCard(m.text, m.meaning, m.action, m.urgency)
            }
            else -> {
                Manual.howTos.forEachIndexed { i, h ->
                    Panel(Modifier.clickable { open = if (open == i) -1 else i }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Title(h.title); Hint("Manual ${h.source}") }
                            AppIcon(Ic.Chevron, size = 18.dp, color = Brand.TextSecondary)
                        }
                        AnimatedVisibility(visible = open == i, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                h.steps.forEachIndexed { n, s -> Label("${n + 1}.  $s", size = 14) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(title: String, meaning: String, action: String, urgency: Int) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Brand.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Pill(urgencyText(urgency), urgencyColor(urgency))
        }
        Label(meaning, size = 13)
        Label(action, size = 13, color = Brand.Accent)
    }
}

// ====================================================================================== History and links
private class QuickLog(val type: String, val label: String, val title: String)

@Composable
fun TimelineScreen(vm: ObdyVm) {
    val events = remember(vm.repairs, vm.logged, vm.scanHistory, vm.batHistory) { vm.timelineEvents() }
    val links = remember(events) { Linker.links(events) }
    val insights = remember(events) { Linker.insights(events) }
    var logging by remember { mutableStateOf<QuickLog?>(null) }
    val quick = listOf(
        QuickLog("fuel", "Refuelled", "Refuelled"),
        QuickLog("wash", "Car wash or rain", "Car wash or heavy rain"),
        QuickLog("battery", "Battery off or jump start", "Battery disconnected or jump-started"),
        QuickLog("light", "Warning light came on", "Warning light came on"),
        QuickLog("note", "Noise or vibration", "Noise or vibration started"),
        QuickLog("note", "Something else", ""),
    )
    ScreenFrame("History and links", { vm.overlay = null }) {
        Panel {
            Title("Log what happened")
            Hint("Oil changes, repairs and costs are added by themselves. Log the rest here: Obdy then looks for links, for example an oil change followed by a warning light.")
            for (row in quick.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (q in row) SoftButton(q.label, Modifier.weight(1f)) { logging = q }
                }
            }
        }
        Panel {
            Title("Possible links")
            if (links.isEmpty()) Hint("No links found yet. Scan the car after a service or repair: if a fault appears soon after, it shows here.")
            for (l in links.take(8)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(when (l.level) { 2 -> "Likely"; 1 -> "Possible"; else -> "Weak" }, when (l.level) { 2 -> Brand.Red; 1 -> Brand.Amber; else -> Brand.Blue })
                        Text(l.code, color = Brand.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Label("Appeared ${if (l.gapDays == 0) "the same or next day" else "${l.gapDays} day(s)"} after: ${l.cause.title}" + (l.gapKm?.let { " ($it km later)" } ?: ""), size = 13)
                    Hint(l.why)
                    Label("Check: ${l.check}", size = 13, color = Brand.Accent)
                }
            }
        }
        if (insights.isNotEmpty()) Panel {
            Title("Repeating problems")
            for (i in insights) Label("•  $i", size = 13)
        }
        Panel {
            Title("Ask the AI to connect the dots")
            Hint("The AI reads your whole timeline together with the faults, live values and the manual's facts.")
            GradientButton(if (vm.linkAiBusy) "Thinking..." else "Look for links", Brand.AltBrush, enabled = !vm.linkAiBusy && vm.activeProvider != null) { vm.askLinks() }
            if (vm.activeProvider == null) { Hint("Add an AI service in Settings first."); SoftButton("Open Settings") { vm.overlay = "settings" } }
            vm.linkAiError?.let { Label(it, color = Brand.Red, size = 13) }
            val t = vm.linkAiText
            if (t != null) { SelectionContainer { Text(t, color = Brand.TextPrimary, fontSize = 14.sp) }; FuseChips(vm, t) }
        }
        Panel {
            Title("Timeline")
            if (events.isEmpty()) Hint("Nothing yet.")
            for (e in events.take(60)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    val tint = when (e.type) { "fault", "light" -> Brand.Red; "gone", "clear" -> Brand.Green; "battery" -> Brand.Amber; else -> Brand.Accent }
                    AppIcon(when (e.type) { "fault", "light" -> Ic.Warning; "gone", "clear" -> Ic.Check; "battery", "test" -> Ic.Battery; "fuel" -> Ic.Fuel; "tyres" -> Ic.Car; "wash", "note" -> Ic.Info; else -> Ic.Wrench }, size = 20.dp, color = tint)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Label(e.title, bold = true, size = 14)
                        Hint((Timeline.typeLabels[e.type] ?: e.type) + "  ·  " + Docs.formatDate(e.timeMs) + (if (e.km > 0) "  ·  ${e.km} km" else ""))
                        if (e.detail.isNotBlank()) Hint(e.detail)
                    }
                    if (e.loggedId != null) Text("✕", color = Brand.TextSecondary, fontSize = 18.sp, modifier = Modifier.clickable { vm.deleteEvent(e.loggedId) }.padding(8.dp))
                }
            }
        }
    }
    val q = logging
    if (q != null) {
        var title by remember(q) { mutableStateOf(q.title) }
        var detail by remember(q) { mutableStateOf("") }
        var date by remember(q) { mutableStateOf(Docs.formatDate(System.currentTimeMillis())) }
        var km by remember(q) { mutableStateOf(if (vm.car.odometerKm > 0) vm.car.odometerKm.toString() else "") }
        AlertDialog(
            onDismissRequest = { logging = null },
            title = { Text(q.label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("What happened", title, { title = it })
                    Field("Details (optional: station, what you noticed)", detail, { detail = it }, singleLine = false)
                    Field("Date (YYYY-MM-DD)", date, { date = it })
                    Field("Mileage (km)", km, { km = it }, number = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ms = Docs.parseDate(date)
                    if (ms == null) vm.say("Write the date like 2026-03-15")
                    else if (title.isBlank()) vm.say("Say what happened")
                    else { vm.addEvent(q.type, title.trim(), detail.trim(), ms, km.trim().toIntOrNull() ?: vm.car.odometerKm); logging = null }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { logging = null }) { Text("Cancel") } },
        )
    }
}
