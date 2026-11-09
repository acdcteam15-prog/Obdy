package com.example.obdy.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.obdy.FuseBox
import com.example.obdy.FuseSlot
import com.example.obdy.Fuses
import com.example.obdy.ObdyVm

private fun slotColor(kind: String): Color = when (kind) {
    "Mini fuse" -> Brand.Amber
    "J-case fuse" -> Brand.Blue
    "Mini relay", "Micro relay", "Relay" -> Brand.TextSecondary
    "Circuit breaker" -> Color(0xFFB388FF)
    else -> Brand.CardBorder
}

private fun shortBox(s: FuseSlot) = if (s.box == "engine") "Engine bay" else "Dash"

/** The fuse box drawn from the manual's layout. The selected slot pulses red. Pinch is replaced by the zoom buttons; scroll sideways when zoomed. */
@Composable
fun FuseBoxDiagram(box: FuseBox, selected: FuseSlot?, zoom: Float, onSelect: (FuseSlot) -> Unit) {
    val pulse = rememberInfiniteTransition(label = "fuse")
    val alpha by pulse.animateFloat(initialValue = 0.45f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(650), repeatMode = RepeatMode.Reverse), label = "fuseAlpha")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth * zoom
        val h = w / box.aspect
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Box(Modifier.size(w, h).clip(RoundedCornerShape(12.dp)).background(Brand.Track.copy(alpha = 0.55f))) {
                for (s in box.slots) {
                    val sw = w * s.w
                    val sh = h * s.h
                    val isSel = selected != null && selected.key == s.key
                    val c = slotColor(s.kind)
                    val fs = (minOf(sw.value, sh.value) * 0.5f).coerceIn(6f, 17f)
                    val shape = if (s.kind == "Nut") CircleShape else RoundedCornerShape(3.dp)
                    val base = Modifier.offset(x = w * s.x, y = h * s.y).size(sw, sh).clip(shape)
                        .background(if (isSel) Brand.Red.copy(alpha = alpha) else c.copy(alpha = if (s.kind == "Nut") 0.25f else 0.22f))
                        .border(if (isSel) 2.dp else 1.dp, if (isSel) Brand.Red else c, shape)
                    Box(if (s.clickable) base.clickable { onSelect(s) } else base, contentAlignment = Alignment.Center) {
                        if (s.id.isNotEmpty()) Text(s.id, color = if (isSel) Color.White else Brand.TextPrimary, fontSize = fs.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** Tappable fuse names found in some text (for example an AI answer). */
@Composable
fun FuseChips(vm: ObdyVm, text: String) {
    val list = remember(text) { Fuses.mentions(text) }
    if (list.isNotEmpty()) {
        Label("Fuses named above (tap to see where)", bold = true, size = 13)
        for (s in list) SoftButton(shortBox(s) + "  ·  " + s.label + "  ·  " + s.usage.take(34), Modifier.fillMaxWidth()) { vm.openFuse(s) }
    }
}

/** Tappable fuses worth checking for a fault code. */
@Composable
fun FuseCodeChips(vm: ObdyVm, code: String) {
    val list = remember(code) { Fuses.forCode(code) }
    if (list.isNotEmpty()) {
        Label("Fuses and relays to check", bold = true, size = 13)
        for (s in list.take(4)) SoftButton(shortBox(s) + "  ·  " + s.label + "  ·  " + s.usage.take(34), Modifier.fillMaxWidth()) { vm.openFuse(s) }
    }
}

@Composable
fun FusesScreen(vm: ObdyVm) {
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var zoom by remember { mutableStateOf(2f) }
    val sel = vm.fuseSel
    var boxKey by remember { mutableStateOf(sel?.box ?: "engine") }
    var checking by remember { mutableStateOf<FuseSlot?>(null) }
    LaunchedEffect(sel) { if (sel != null) boxKey = sel.box }

    ScreenFrame("Fuse boxes", { vm.overlay = null }, subtitle = "From your owner's manual") {
        SegBar(listOf("Find", "Colours", "Safety and tests"), tab) { tab = it }
        when (tab) {
            0 -> {
                Panel {
                    Title("What is not working?")
                    Field("Type it: horn, wipers, radio, 12 V socket, OBD port...", query, { query = it })
                    val res = remember(query) { Fuses.search(query) }
                    if (query.isNotBlank() && res.isEmpty()) Hint("Nothing found. Try another word, or browse the diagram below.")
                    for (s in res.take(8)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Label(s.usage, bold = true, size = 14); Hint(shortBox(s) + "  ·  " + s.label) }
                            SoftButton("Show") { vm.fuseSel = s; boxKey = s.box }
                        }
                    }
                }
                SegBar(Fuses.boxes.map { it.shortName }, if (boxKey == "panel") 1 else 0) { boxKey = if (it == 1) "panel" else "engine" }
                val box = Fuses.box(boxKey)
                Panel {
                    Title(box.name)
                    Label(box.where, size = 14)
                    Hint(box.open)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label("Zoom ${if (zoom % 1f == 0f) zoom.toInt().toString() else zoom.toString()}x", size = 13)
                        Spacer(Modifier.weight(1f))
                        SoftButton("−") { zoom = maxOf(1f, zoom - 0.5f) }
                        SoftButton("+") { zoom = minOf(4f, zoom + 0.5f) }
                    }
                    FuseBoxDiagram(box, sel, zoom) { vm.fuseSel = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for ((k, n) in listOf("Mini fuse" to "Mini", "J-case fuse" to "J-case", "Mini relay" to "Relay", "Circuit breaker" to "Breaker")) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(slotColor(k))); Spacer(Modifier.width(4.dp)); Text(n, color = Brand.TextSecondary, fontSize = 11.sp) }
                        }
                    }
                    Hint("Tap a fuse. Scroll sideways when zoomed in. The diagram is the view shown in the manual.")
                }
                if (sel != null) Panel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(sel.label, slotColor(sel.kind))
                    }
                    Title(sel.usage)
                    Label(sel.boxName, size = 14)
                    Hint(Fuses.box(sel.box).where)
                    Hint(Fuses.describeKind(sel.kind))
                    Label("Rating: printed on the fuse and on the box lid (this manual does not list ratings). Replace with the same size and rating.", size = 13, color = Brand.Amber)
                    val related = vm.faults.filter { f -> Fuses.forCode(f.code).any { it.key == sel.key } }.map { it.code.take(5) }.distinct()
                    if (related.isNotEmpty()) Label("Related to your codes: " + related.joinToString(", "), size = 13, color = Brand.Accent)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton("I checked it", Modifier.weight(1f)) { checking = sel }
                        SoftButton("Close", Modifier.weight(1f)) { vm.fuseSel = null }
                    }
                }
            }
            1 -> {
                Panel {
                    Title("Mini (blade) fuse colours")
                    Hint("Mini fuses are colour coded by rating.")
                    for (c in Fuses.miniColours) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)).background(Color(c.argb)).border(1.dp, Brand.CardBorder, RoundedCornerShape(5.dp)))
                            Spacer(Modifier.width(12.dp))
                            Label("${c.amps}   ·   ${c.colour}", bold = true)
                        }
                    }
                }
                Panel {
                    Title("J-case and relays")
                    Label("J-case fuses are the larger cartridge type for heavy loads. Their colour follows the rating but differs a little between makers, so read the number printed on the fuse.", size = 14)
                    Label("Relays have no rating to match: use the same type (mini relay or micro relay) and read the markings.", size = 14)
                }
                Panel {
                    Title("About your car's ratings")
                    Label("The owner's manual tells you what each fuse protects, but it does not list the amp ratings. The rating is printed on each fuse and on the fuse box lid.", size = 14)
                    Label("Always fit the same size and the same rating. Never a higher one.", size = 14, color = Brand.Amber)
                }
            }
            else -> {
                Panel {
                    Title("Before you start")
                    for (s in Fuses.safety) Label("•  $s", size = 14)
                }
                Panel {
                    Title("How to check a fuse")
                    Fuses.checkSteps.forEachIndexed { i, s -> Label("${i + 1}.  $s", size = 14) }
                }
                Panel {
                    Title("How to test a relay")
                    for (s in Fuses.relayTests) Label("•  $s", size = 14)
                }
            }
        }
    }

    val c = checking
    if (c != null) {
        AlertDialog(
            onDismissRequest = { checking = null },
            title = { Text("Checked: ${c.usage}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint("This goes into your history, so Obdy can link it to later faults.")
                    SoftButton("The fuse was fine", Modifier.fillMaxWidth()) { vm.logFuseCheck(c, 0); checking = null }
                    SoftButton("Blown: I replaced it", Modifier.fillMaxWidth()) { vm.logFuseCheck(c, 1); checking = null }
                    SoftButton("It blew again after replacing", Modifier.fillMaxWidth()) { vm.logFuseCheck(c, 2); checking = null }
                }
            },
            confirmButton = { TextButton(onClick = { checking = null }) { Text("Cancel") } },
        )
    }
}
