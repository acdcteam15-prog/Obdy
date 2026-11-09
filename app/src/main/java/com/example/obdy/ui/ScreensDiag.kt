package com.example.obdy.ui

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
@Composable
fun CarDiagram(red: Set<Zone>, amber: Set<Zone>) {
    val calm = Brand.CardBorder
    val bodyColor = Brand.Track
    BoxWithConstraints(Modifier.fillMaxWidth().height(440.dp)) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val cw = size.width
            val ch = size.height
            drawRoundRect(color = bodyColor.copy(alpha = 0.5f), topLeft = Offset(cw * 0.12f, ch * 0.02f), size = Size(cw * 0.76f, ch * 0.96f), cornerRadius = CornerRadius(cw * 0.14f, cw * 0.14f))
            for (z in Zone.values()) {
                val hot = z in red
                val warm = z in amber
                val col = if (hot) Brand.Red else if (warm) Brand.Amber else calm
                val tl = Offset(cw * z.l, ch * z.t)
                val sz = Size(cw * (z.r - z.l), ch * (z.b - z.t))
                drawRoundRect(color = col.copy(alpha = if (hot || warm) 0.55f else 0.16f), topLeft = tl, size = sz, cornerRadius = CornerRadius(10f, 10f))
                drawRoundRect(color = col, topLeft = tl, size = sz, cornerRadius = CornerRadius(10f, 10f), style = Stroke(width = if (hot || warm) 3f else 1.5f))
            }
        }
        for (z in Zone.values()) {
            if (z in red || z in amber) {
                Text(
                    z.label.substringBefore(':').substringBefore(','), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.offset(x = w * z.l + 3.dp, y = h * z.t + 2.dp).width(w * (z.r - z.l) - 6.dp),
                )
            }
        }
        Text("FRONT", fontSize = 10.sp, color = Brand.TextSecondary, modifier = Modifier.align(Alignment.TopCenter))
        Text("REAR", fontSize = 10.sp, color = Brand.TextSecondary, modifier = Modifier.align(Alignment.BottomCenter))
        Text("driver\nside", fontSize = 9.sp, color = Brand.TextSecondary, modifier = Modifier.align(Alignment.CenterStart))
        Text("passenger\nside", fontSize = 9.sp, color = Brand.TextSecondary, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

// ====================================================================================== Where is the part
@Composable
fun PartsScreen(vm: ObdyVm) {
    val code = vm.partsCode
    val codes = if (code != null) listOf(code) else vm.visibleFaults.map { it.code.take(5) }.distinct()
    val offline = codes.flatMap { c -> Parts.forCode(c).map { c to it } }
    val red = offline.map { it.second.zone }.toSet()
    val amber = vm.aiParts.mapNotNull { it.zone }.toSet()
    ScreenFrame("Where is it?", { vm.overlay = null }) {
        Panel {
            Title(if (code != null) "Parts for $code" else "Parts for your faults")
            if (code != null) Hint(DtcDb.describe(code))
            Hint("Red: the usual suspects for the code. Amber: the AI's estimate. These locations are general for this kind of car: check the workshop manual before removing anything.")
            CarDiagram(red, amber)
        }
        if (codes.isEmpty()) Hint("No fault codes yet. Scan on the Faults tab, or open this from a code.")
        for ((c, part) in offline) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Title(part.name) }
                Pill(part.zone.label.substringBefore(':'), Brand.Red)
                Label("Code $c", color = Brand.TextSecondary, size = 12)
                Label(part.where, size = 14)
                if (part.tip.isNotBlank()) Hint("Tip: " + part.tip)
            }
        }
        Panel {
            Title("Ask the AI to pin-point it")
            Hint("The AI names the likely parts for this code and your answers, and where to look. It is an estimate for this model, so treat low confidence as a pointer, not a fact.")
            GradientButton(if (vm.partsBusy) "Thinking..." else "Locate with AI", Brand.AltBrush, enabled = !vm.partsBusy && vm.activeProvider != null) { vm.locateWithAi() }
            if (vm.activeProvider == null) { Hint("Add an AI service in Settings first."); SoftButton("Open Settings") { vm.overlay = "settings" } }
            vm.partsError?.let { Label(it, color = Brand.Red, size = 13) }
        }
        for (p in vm.aiParts) {
            Panel {
                Title(p.name)
                Pill((p.zone?.label?.substringBefore(':') ?: "location not mapped"), Brand.Amber)
                Pill("confidence: " + p.confidence, if (p.confidence == "high") Brand.Green else if (p.confidence == "medium") Brand.Amber else Brand.Red)
                Label(p.where, size = 14)
                if (p.check.isNotBlank()) Hint("Check: " + p.check)
            }
        }
        if (code != null) SoftButton("Find the cause with questions", Modifier.fillMaxWidth()) { vm.startGuided(code); vm.overlay = "guided" }
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
