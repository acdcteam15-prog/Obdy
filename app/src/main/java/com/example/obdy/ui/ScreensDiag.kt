package com.example.obdy.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.example.obdy.PartInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.obdy.Conn
import com.example.obdy.DtcDb
import com.example.obdy.Guided
import com.example.obdy.ObdyVm
import com.example.obdy.Parts
import com.example.obdy.Zone

// ====================================================================================== Car diagram
/** Top view of the car, front at the top. Red zones = most likely parts, amber = the AI's estimate. */
/** One pin on the diagram: [n] is the number shown on the card below. */
class DiagramPin(val n: Int, val zone: Zone, val color: Color)

/**
 * Top view of the car, front at the top, driver on the left. Drawn at real car proportions (about 1 : 2.1) so the zones
 * land where they are; numbered pins match the part cards; the selected zone pulses.
 */
@Composable
fun CarDiagram(pins: List<DiagramPin>, selected: Zone?, onPin: (Zone) -> Unit = {}) {
    val calm = Brand.CardBorder
    val body = Brand.Track
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(0.35f, 0.85f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(pins.size) { appear.snapTo(0f); appear.animateTo(1f, tween(600)) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = minOf(maxWidth, 300.dp)
        val h = w * 2.1f
        Box(Modifier.size(w, h).align(Alignment.Center)) {
            Canvas(Modifier.fillMaxSize()) {
                val cw = size.width; val ch = size.height
                // body, windscreen, rear screen, mirrors
                drawRoundRect(color = body.copy(alpha = 0.55f), topLeft = Offset(cw * 0.15f, ch * 0.015f), size = Size(cw * 0.70f, ch * 0.97f), cornerRadius = CornerRadius(cw * 0.16f, cw * 0.16f))
                drawRoundRect(color = calm.copy(alpha = 0.5f), topLeft = Offset(cw * 0.21f, ch * 0.335f), size = Size(cw * 0.58f, ch * 0.04f), cornerRadius = CornerRadius(12f, 12f))
                drawRoundRect(color = calm.copy(alpha = 0.5f), topLeft = Offset(cw * 0.23f, ch * 0.675f), size = Size(cw * 0.54f, ch * 0.03f), cornerRadius = CornerRadius(12f, 12f))
                drawRoundRect(color = body, topLeft = Offset(cw * 0.08f, ch * 0.36f), size = Size(cw * 0.08f, ch * 0.025f), cornerRadius = CornerRadius(6f, 6f))
                drawRoundRect(color = body, topLeft = Offset(cw * 0.84f, ch * 0.36f), size = Size(cw * 0.08f, ch * 0.025f), cornerRadius = CornerRadius(6f, 6f))
                for (z in Zone.values()) {
                    val pin = pins.firstOrNull { it.zone == z }
                    val sel = z == selected
                    val col = pin?.color ?: calm
                    val tl = Offset(cw * z.l, ch * z.t)
                    val sz = Size(cw * (z.r - z.l), ch * (z.b - z.t))
                    val fill = when { sel -> glow; pin != null -> 0.40f * appear.value; else -> 0.10f }
                    drawRoundRect(color = col.copy(alpha = fill), topLeft = tl, size = sz, cornerRadius = CornerRadius(12f, 12f))
                    val dash = if (z.under) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
                    drawRoundRect(color = if (sel) col else col.copy(alpha = if (pin != null) 0.9f else 0.35f), topLeft = tl, size = sz, cornerRadius = CornerRadius(12f, 12f), style = Stroke(width = if (sel) 5f else if (pin != null) 3f else 1.5f, pathEffect = dash))
                }
            }
            // numbered pins (several parts in one zone sit side by side)
            for ((z, list) in pins.groupBy { it.zone }) {
                val cx = w * ((z.l + z.r) / 2); val cy = h * ((z.t + z.b) / 2)
                for ((i, pin) in list.withIndex()) {
                    val step = 24.dp * (i - (list.size - 1) / 2f)
                    val tall = h * (z.b - z.t) > w * (z.r - z.l)     // narrow zones stack their pins vertically
                    val dx = if (tall) 0.dp else step; val dy = if (tall) step else 0.dp
                    val sc = if (z == selected) 1.15f else 1f
                    Box(
                        Modifier.offset(x = cx - 11.dp + dx, y = cy - 11.dp + dy).size(22.dp).graphicsLayer { scaleX = sc * appear.value; scaleY = sc * appear.value }
                            .clip(RoundedCornerShape(11.dp)).background(pin.color).clickable { onPin(z) },
                        contentAlignment = Alignment.Center,
                    ) { Text("${pin.n}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            Text("FRONT", fontSize = 10.sp, color = Brand.TextSecondary, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-2).dp))
            Text("REAR", fontSize = 10.sp, color = Brand.TextSecondary, modifier = Modifier.align(Alignment.BottomCenter).offset(y = 2.dp))
        }
        Text("driver", fontSize = 10.sp, color = Brand.TextSecondary, modifier = Modifier.align(Alignment.CenterStart))
        Text("passenger", fontSize = 10.sp, color = Brand.TextSecondary, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

// ====================================================================================== Where is the part
@Composable
fun PartsScreen(vm: ObdyVm) {
    val code = vm.partsCode
    val codes = if (code != null) listOf(code) else vm.visibleFaults.map { it.code.take(5) }.distinct()
    var browse by remember(code) { mutableStateOf(codes.isEmpty()) }
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<Zone?>(null) }

    // (code or null, part) shown as numbered cards
    val list: List<Pair<String?, PartInfo>> = if (browse) {
        Parts.all.filter { (cat == "All" || it.category == cat) && (query.isBlank() || it.name.contains(query, true) || it.where.contains(query, true) || it.zone.label.contains(query, true)) }.map { null to it }
    } else codes.flatMap { c -> Parts.forCode(c).map { c to it } }.distinctBy { it.second.name }
    val pins = list.mapIndexed { i, (c, p) -> DiagramPin(i + 1, p.zone, if (browse) Brand.Accent else if (i == 0) Brand.Red else Brand.Amber) } +
        vm.aiParts.mapNotNull { it.zone }.filter { z -> list.none { it.second.zone == z } }.map { DiagramPin(0, it, Brand.Blue) }

    ScreenFrame("Where is it?", { vm.overlay = null }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("For my codes", !browse, enabled = codes.isNotEmpty()) { browse = false; selected = null }
            Chip("All parts", browse) { browse = true; selected = null }
        }
        Panel {
            Title(if (browse) "Parts of your Malibu" else if (code != null) "Parts for $code" else "Parts for your faults")
            if (!browse && code != null) Hint(DtcDb.describe(code))
            if (browse) {
                Field("Search a part (battery, O2, throttle...)", query, { query = it })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in listOf("All") + Parts.all.map { it.category }.distinct()) Chip(c, cat == c) { cat = c; selected = null }
                }
            } else Hint("Number 1 (red) is the most likely; amber are the next suspects; blue are extra places the AI named. Dashed areas are under the car.")
            CarDiagram(pins, selected) { z -> selected = if (selected == z) null else z }
            AnimatedContent(targetState = selected, label = "zone") { z ->
                if (z != null) Label(z.label + if (z.under) "  (seen from under the car)" else "", bold = true, color = Brand.Accent, size = 13)
                else Hint("Tap a number or a card to see where it is.")
            }
        }
        if (!browse && codes.isEmpty()) Hint("No fault codes yet. Scan on the Faults tab, or browse All parts.")
        for ((i, pair) in list.withIndex()) {
            val (c, part) = pair
            val sel = selected == part.zone
            val border by animateColorAsState(if (sel) Brand.Accent else Color.Transparent, label = "sel")
            Panel(Modifier.border(2.dp, border, RoundedCornerShape(18.dp)).clickable { selected = if (sel) null else part.zone }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(pins[i].color), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(part.name, color = Brand.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(part.zone.label.substringBefore(':') + (c?.let { "  ·  for $it" } ?: ""), color = Brand.TextSecondary, fontSize = 11.sp)
                    }
                    Pill(part.category, Brand.Blue)
                }
                Label(part.where, size = 14)
                if (part.tip.isNotBlank()) Label("Tip: " + part.tip, color = Brand.Amber, size = 13)
            }
        }
        Hint("Locations are for the 2013 Malibu 2.5 L (left-hand drive). Trim and module positions can differ between builds: check before removing panels.")
        if (!browse && codes.isNotEmpty()) Panel {
            Title("Ask the AI to pin-point it")
            Hint("The AI names likely parts for this code (and your answers in Find the cause). Treat low confidence as a pointer, not a fact.")
            GradientButton(if (vm.partsBusy) "Thinking..." else "Locate with AI", Brand.AltBrush, enabled = !vm.partsBusy && vm.activeProvider != null) { vm.locateWithAi() }
            if (vm.activeProvider == null) { Hint("Add an AI service in Settings first."); SoftButton("Open Settings") { vm.overlay = "settings" } }
            vm.partsError?.let { Label(it, color = Brand.Red, size = 13) }
        }
        for (p in vm.aiParts) {
            Panel(Modifier.clickable { selected = p.zone }) {
                Title(p.name)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill((p.zone?.label?.substringBefore(':') ?: "location not mapped"), Brand.Blue)
                    Pill("confidence: " + p.confidence, if (p.confidence == "high") Brand.Green else if (p.confidence == "medium") Brand.Amber else Brand.Red)
                }
                Label(p.where, size = 14)
                if (p.check.isNotBlank()) Hint("Check: " + p.check)
            }
        }
        if (code != null) SoftButton("Find the cause with questions", Modifier.fillMaxWidth()) { vm.startGuided(code); vm.overlay = "guided" }
    }
}

@Composable
fun Chip(text: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) Brand.Accent else Brand.Card, label = "chip")
    val fg by animateColorAsState(if (on) Color.Black else if (enabled) Brand.TextPrimary else Brand.TextSecondary, label = "chipT")
    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, if (on) Brand.Accent else Brand.CardBorder, RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ====================================================================================== Guided diagnosis
@Composable
fun GuidedScreen(vm: ObdyVm) {
    val ctx = LocalContext.current
    val code = vm.gCode
    ScreenFrame("Find the cause", { vm.overlay = null }) {
        if (code == null) {
            var manual by remember { mutableStateOf("") }
            Panel {
                Title("Which code?")
                for (f in vm.visibleFaults.distinctBy { it.code.take(5) }) SoftButton("${f.code}  ${f.description}", Modifier.fillMaxWidth()) { vm.startGuided(f.code) }
                if (vm.visibleFaults.isEmpty()) Hint("No codes from a scan yet. Type one, for example P0171:")
                Field("Code", manual, { manual = it.uppercase() })
                GradientButton("Use this code", Brand.MainBrush, enabled = manual.trim().length >= 5) { vm.startGuided(manual.trim()) }
            }
        } else {
            Panel {
                Title(code)
                Label(DtcDb.describe(code), size = 14)
                Hint(when (vm.gStage) { 0 -> "Step 1 of 3: your answers"; 1 -> "Step 2 of 3: a few more questions from the AI"; else -> "Step 3 of 3: the solution" })
            }
            when (vm.gStage) {
                0 -> {
                    Hint("Answer what you know. Skip the rest. If you already replaced a part and the code stayed, tick it: the answer then looks for the other causes (cleaning, filters, wiring) instead of the same part.")
                    for (q in Guided.questions(code)) {
                        val chosen = vm.gAnswers[q.id] ?: emptyList()
                        Panel {
                            Label(q.text, bold = true)
                            for (opt in q.options) {
                                Row(Modifier.fillMaxWidth().clickable { vm.toggleAnswer(q, opt) }, verticalAlignment = Alignment.CenterVertically) {
                                    if (q.multi) Checkbox(checked = opt in chosen, onCheckedChange = { vm.toggleAnswer(q, opt) })
                                    else RadioButton(selected = opt in chosen, onClick = { vm.toggleAnswer(q, opt) })
                                    Text(opt, fontSize = 14.sp, color = Brand.TextPrimary)
                                }
                            }
                        }
                    }
                    Panel { Field("Anything else? (what you replaced, noises, when...)", vm.gNotes, { vm.gNotes = it }, singleLine = false) }
                    val steps = Guided.nextSteps(code, vm.guidedReplaced())
                    if (steps.isNotEmpty()) Panel {
                        Title("Built-in suggestions (offline)")
                        Hint("Updates as you tick what you already did.")
                        for (s in steps) Label("•  $s", size = 14)
                    }
                    GradientButton(if (vm.gBusy) "Thinking..." else "Ask the AI", Brand.MainBrush, enabled = !vm.gBusy && vm.activeProvider != null) { vm.guidedAskAi() }
                    if (vm.activeProvider == null) { Hint("Add an AI service in Settings to use this step."); SoftButton("Open Settings") { vm.overlay = "settings" } }
                    vm.gError?.let { Label(it, color = Brand.Red, size = 13) }
                }
                1 -> {
                    for ((i, q) in vm.gAiQs.withIndex()) {
                        Panel {
                            Label(q.q, bold = true)
                            for (opt in q.options) {
                                Row(Modifier.fillMaxWidth().clickable { vm.gAiAnswers[i] = opt }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = vm.gAiAnswers[i] == opt, onClick = { vm.gAiAnswers[i] = opt })
                                    Text(opt, fontSize = 14.sp, color = Brand.TextPrimary)
                                }
                            }
                        }
                    }
                    GradientButton(if (vm.gBusy) "Thinking..." else "Get the solution", Brand.MainBrush, enabled = !vm.gBusy) { vm.guidedSolve() }
                    SoftButton("Back to my answers", Modifier.fillMaxWidth()) { vm.gStage = 0 }
                    vm.gError?.let { Label(it, color = Brand.Red, size = 13) }
                }
                else -> {
                    val r = vm.gResult
                    if (r != null) Panel {
                        Title("Solution")
                        SelectionContainer { Text(r.replace("**", "").replace("##", ""), color = Brand.TextPrimary, fontSize = 14.sp) }
                        FuseChips(vm, r)
                        Hint("A guide, not a verdict. If it says to stop and see a workshop, do that.")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoftButton("Where is the part?", Modifier.weight(1f)) { vm.showParts(code) }
                        SoftButton("Copy", Modifier.weight(1f)) { if (r != null) { copyText(ctx, r); vm.say("Copied") } }
                    }
                    SoftButton("Start again for $code", Modifier.fillMaxWidth()) { vm.startGuided(code) }
                    SoftButton("Another code", Modifier.fillMaxWidth()) { vm.gCode = null }
                }
            }
        }
    }
}

// ====================================================================================== Online checks
@Composable
fun OnlineScreen(vm: ObdyVm) {
    LaunchedEffect(Unit) { if (vm.online == null) vm.loadOnline() }
    val codes = vm.visibleFaults.map { it.code.take(5) }.distinct().take(4)
    ScreenFrame("Online checks", { vm.overlay = null }) {
        Panel {
            Title("Search the web")
            Hint("Opens your browser with a ready search for ${vm.car.year} Chevrolet Malibu.")
            SoftButton("Google: common problems and bulletins", Modifier.fillMaxWidth()) { vm.webSearch("google", null) }
            for (c in codes) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Google $c", Modifier.weight(1f)) { vm.webSearch("google", c) }
                    SoftButton("YouTube $c", Modifier.weight(1f)) { vm.webSearch("youtube", c) }
                }
            }
        }
        Panel {
            Title("Ask the AI to research it")
            Hint("Looks for known fixes, service bulletins and software updates. Gemini can really search Google; other services answer from what they learned.")
            val subject = codes.firstOrNull()
            GradientButton(if (vm.webAiBusy) "Searching..." else if (subject != null) "Research $subject" else "Research this car", Brand.AltBrush, enabled = !vm.webAiBusy && vm.activeProvider != null) { vm.aiWebSearch(subject) }
            if (vm.activeProvider == null) { Hint("Add an AI service in Settings first."); SoftButton("Open Settings") { vm.overlay = "settings" } }
            vm.webAiError?.let { Label(it, color = Brand.Red, size = 13) }
            val t = vm.webAiText
            if (t != null) SelectionContainer { Text(t.replace("**", "").replace("##", ""), color = Brand.TextPrimary, fontSize = 14.sp) }
        }
        Panel {
            Title("Official recalls and owner complaints")
            Hint("From the US safety agency (NHTSA). GCC cars are often built like the US ones, but ask your dealer if a recall applies to your VIN.")
            if (vm.onlineBusy) Label("Loading...", color = Brand.Amber)
            vm.onlineError?.let { Label(it, color = Brand.Red, size = 13); SoftButton("Try again") { vm.loadOnline() } }
            val o = vm.online
            if (o != null) {
                Label("${o.recalls.size} recall(s), ${o.complaintTotal} owner complaint(s)", bold = true)
                for ((comp, n) in o.complaintTop.take(8)) Label("$n  ·  $comp", size = 13)
            }
        }
        val o = vm.online
        if (o != null) for (r in o.recalls.take(12)) {
            Panel {
                Label(r.component, bold = true, size = 14)
                Hint("Campaign ${r.number}")
                Label(r.summary.take(260) + if (r.summary.length > 260) "..." else "", size = 13)
                if (r.remedy.isNotBlank()) Hint("Remedy: " + r.remedy.take(200))
            }
        }
        Panel {
            Title("About hiding or changing errors")
            Hint("Obdy only reads the car. It does not change the car's software, so it cannot switch a fault off. What you can do: hide a non-urgent code inside Obdy so it stops cluttering your lists (Faults tab > open the code > Hide in Obdy). Engine misfire, overheating, brake and airbag codes cannot be hidden, because they warn of real danger.")
            if (vm.hiddenFaults.isNotEmpty()) {
                Label("Hidden now:", bold = true, size = 13)
                for (f in vm.hiddenFaults) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Label(f.code, bold = true); Hint(f.description) }
                    SoftButton("Show again") { vm.restoreCode(f.code) }
                }
            }
        }
    }
}
