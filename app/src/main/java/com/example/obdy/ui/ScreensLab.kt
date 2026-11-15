package com.example.obdy.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.obdy.Conn
import com.example.obdy.ObdyVm
import com.example.obdy.Store

/**
 * Claude Lab (its own tab): one continuing conversation with Claude about the car and the app. Messages can carry a photo
 * and the full session data (logs, live values, faults, self-tests, module notes). The conversation is kept between app
 * starts until "New chat".
 */
@Composable
fun LabScreen(vm: ObdyVm) {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    var showSetup by remember { mutableStateOf(vm.lab.isEmpty()) }
    LaunchedEffect(vm.lab.size, vm.labChatBusy) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().background(Brand.BgDeep)) {
        // ---- header
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(lerp(Brand.Header, Brand.Accent, 0.06f), Brand.Header)))
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppLogo(34.dp); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Claude Lab", color = Brand.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(vm.labProvider?.let { "${it.name} · ${it.model}" } ?: "No AI service yet", color = Brand.TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
            IconTap(Ic.Doc) { vm.labShareFile() }
            if (vm.lab.isNotEmpty()) IconTap(Ic.Close) { vm.labNewChat(); showSetup = true }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brand.CardBorder))

        // ---- conversation
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vm.labProvider == null) Panel {
                Label("Add an AI service (Anthropic Claude works best) to talk here.", bold = true)
                SoftButton("Open Settings") { vm.overlay = "settings" }
            }
            SessionCard(vm, showSetup) { showSetup = !showSetup }
            if (vm.lab.isEmpty()) {
                Text("Start with", color = Brand.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                val starters = listOf(
                    "Full analysis of my car with this session data" to true,
                    "Find wrong or missing readings in the app" to true,
                    "What else can this car tell us?" to true,
                    "Is it safe to drive with my current codes?" to false,
                )
                for ((q, attach) in starters) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.Card).border(1.dp, Brand.CardBorder, RoundedCornerShape(14.dp))
                        .clickable(enabled = !vm.labChatBusy && vm.labProvider != null) { vm.labAttach = attach; vm.labChatSend(q) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(q, color = Brand.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (attach) Pill("+ data", Brand.Blue)
                }
            }
            for (m in vm.lab) Bubble(vm, m)
            if (vm.labChatBusy) TypingDots()
            vm.labError?.let { Label(it, color = Brand.Red, size = 13) }
            val notes = vm.labNotes
            if (notes != null && vm.lab.isNotEmpty()) Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(Ic.Wrench, size = 18.dp, color = Brand.Accent); Spacer(Modifier.width(8.dp)); Title("Notes for the app")
                }
                Hint("Paste these where the app is built and the code gets updated.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Share", Modifier.weight(1f)) { vm.labShareNotes() }
                    SoftButton("Copy", Modifier.weight(1f)) { copyText(ctx, notes); vm.say("Copied") }
                }
            }
        }

        // ---- composer
        Composer(vm)
    }
}

/** Collapsible card: what the session data asks for, the discovery probe, and sharing the data as a file. */
@Composable
private fun SessionCard(vm: ObdyVm, open: Boolean, onToggle: () -> Unit) {
    val ctx = LocalContext.current
    Panel(Modifier.clickable { onToggle() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(Ic.Cpu, size = 18.dp, color = Brand.Accent); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Title("Session data")
                Hint("Car, adapter, live values, faults, self-tests, module notes and the log" + if (vm.labProbe.isNotEmpty()) " + ${vm.labProbe.size} probe lines" else "")
            }
            Text(if (open) "Hide" else "Options", color = Brand.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("When data is attached, ask Claude to:", color = Brand.TextSecondary, fontSize = 12.sp)
                Opt("Diagnose the car", vm.labDiagnose) { vm.labDiagnose = it }
                Opt("Find wrong or missing readings in the app", vm.labBugs) { vm.labBugs = it }
                Opt("Ideas for new features and tests", vm.labIdeas) { vm.labIdeas = it }
                Opt("What else this car can tell us", vm.labDiscover) { vm.labDiscover = it }
                Spacer(Modifier.height(4.dp))
                Text("Discovery probe (about 30 s, read only)", color = Brand.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (vm.labBusy && vm.labProgress.isNotBlank()) Label(vm.labProgress, color = Brand.Amber, size = 13)
                SoftButton(if (vm.labBusy) "Working..." else "Run the discovery probe", Modifier.fillMaxWidth(), enabled = !vm.labBusy && vm.conn == Conn.On) { vm.labRunProbe() }
                if (vm.conn != Conn.On) Hint("Connect to the car to run it. For the best data, open Live for a minute and run a Deep scan first.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Share as a file", Modifier.weight(1f)) { vm.labShareFile() }
                    SoftButton("Copy all", Modifier.weight(1f)) { copyText(ctx, vm.labBundle()); vm.say("Copied the whole bundle") }
                }
            }
        }
    }
}

@Composable
private fun Opt(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onChange); Spacer(Modifier.width(8.dp)); Label(label, size = 14)
    }
}

@Composable
private fun Bubble(vm: ObdyVm, m: Store.LabMsg) {
    val ctx = LocalContext.current
    val mine = m.fromUser
    val shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(shape)
                .background(if (mine) Brush.linearGradient(listOf(lerp(Brand.Card, Brand.Accent, 0.28f), lerp(Brand.Card, Brand.Accent, 0.16f))) else Brush.linearGradient(listOf(Brand.Card, Brand.Card)))
                .border(1.dp, if (mine) Brand.Accent.copy(alpha = 0.35f) else Brand.CardBorder, shape).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!mine) Text("Claude", color = Brand.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            m.imagePath?.let { path ->
                val bmp = remember(path) {
                    try { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() } catch (_: Exception) { null }
                }
                if (bmp != null) Image(bmp, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)))
            }
            if (m.withBundle) Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(Ic.Cpu, size = 14.dp, color = Brand.Blue); Spacer(Modifier.width(6.dp))
                Text("Session data attached", color = Brand.Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            SelectionContainer { Text(m.text, color = Brand.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmtDate(m.timeMs).substringAfterLast(' '), color = Brand.TextSecondary, fontSize = 10.sp)
                if (!mine) {
                    Spacer(Modifier.weight(1f))
                    Text("Copy", color = Brand.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { copyText(ctx, m.text); vm.say("Copied") }.padding(4.dp))
                }
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Brand.Card).border(1.dp, Brand.CardBorder, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0..2) {
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, delayMillis = i * 160), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(8.dp).graphicsLayer { alpha = a; translationY = -4f * a }.clip(CircleShape).background(Brand.Accent))
        }
        Spacer(Modifier.width(4.dp)); Text("Claude is thinking", color = Brand.TextSecondary, fontSize = 12.sp)
    }
}

/** Bottom bar: attachments (session data, photo preview), photo, voice, text and send. */
@Composable
private fun Composer(vm: ObdyVm) {
    Column(Modifier.fillMaxWidth().background(Brand.Header).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(if (vm.labAttach) "✓ Session data" else "+ Session data", vm.labAttach) { vm.labAttach = !vm.labAttach }
            val img = vm.labImage
            if (img != null) {
                val bmp = remember(img) { BitmapFactory.decodeByteArray(img, 0, img.size)?.asImageBitmap() }
                Box {
                    if (bmp != null) Image(bmp, "Photo to send", contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                    Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(Brand.Red).clickable { vm.labImage = null }, contentAlignment = Alignment.Center) {
                        Text("×", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { vm.pick("lab", "image/*") }, contentAlignment = Alignment.Center) { AppIcon(Ic.Camera, size = 22.dp, color = Brand.Accent) }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { vm.launchVoice?.invoke() }, contentAlignment = Alignment.Center) { AppIcon(Ic.Mic, size = 22.dp, color = Brand.Accent) }
            OutlinedTextField(
                value = vm.labDraft, onValueChange = { vm.labDraft = it },
                placeholder = { Text(if (vm.lab.isEmpty()) "Ask Claude about the car or the app..." else "Continue the chat...", fontSize = 14.sp) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 140.dp),
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand.Accent, unfocusedBorderColor = Brand.CardBorder, focusedTextColor = Brand.TextPrimary, unfocusedTextColor = Brand.TextPrimary, cursorColor = Brand.Accent),
            )
            val can = !vm.labChatBusy && vm.labProvider != null && (vm.labDraft.isNotBlank() || vm.labAttach || vm.labImage != null)
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (can) Brand.MainBrush else Brush.linearGradient(listOf(Brand.Track, Brand.Track)))
                    .clickable(enabled = can) { vm.labChatSend() },
                contentAlignment = Alignment.Center,
            ) { Text("➤", color = if (can) Color.White else Brand.TextSecondary, fontSize = 20.sp) }
        }
    }
}
