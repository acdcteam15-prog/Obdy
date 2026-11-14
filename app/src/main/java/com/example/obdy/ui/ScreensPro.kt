package com.example.obdy.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.example.obdy.Calc
import com.example.obdy.Conn
import com.example.obdy.DtcDb
import com.example.obdy.Gm
import com.example.obdy.ObdyVm

// ====================================================================================== Driving mode
/** Four big gauges for a phone mount, screen kept on, loud alerts (sound + vibration) for heat, charging and rpm. */
@Composable
fun DriveScreen(vm: ObdyVm) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        vm.wantLive("drive", true)
        val w = (ctx as? Activity)?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { vm.wantLive("drive", false); w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler { vm.overlay = null }
    val v = vm.liveValues
    val alert = vm.driveAlert
    val flash = rememberInfiniteTransition(label = "flash")
    val a by flash.animateFloat(0.25f, 0.9f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")
    Box(Modifier.fillMaxSize().background(Color(0xFF05080D))) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Driving", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("✕", color = Color.White, fontSize = 26.sp, modifier = Modifier.clickable { vm.overlay = null }.padding(8.dp))
            }
            if (vm.conn != Conn.On) Text("Not connected", color = Brand.Amber, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DriveGauge("km/h", v[0x0D], 0.0, 200.0, 0, Brand.Accent, Modifier.weight(1f))
                DriveGauge("rpm", v[0x0C], 0.0, 7000.0, 0, if ((v[0x0C] ?: 0.0) > 6000) Brand.Red else Brand.Accent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val c = v[0x05]
                DriveGauge("coolant °C", c, 40.0, 130.0, 0, if ((c ?: 0.0) >= 110) Brand.Red else if ((c ?: 0.0) >= 104) Brand.Amber else Brand.Green, Modifier.weight(1f))
                val vv = v[0x42]
                DriveGauge("volts", vv, 10.0, 16.0, 1, if (vv != null && vv < 12.6) Brand.Red else if (vv != null && vv < 13.2) Brand.Amber else Brand.Green, Modifier.weight(1f))
            }
            val tft = vm.extValues["Transmission fluid temp (GM)"]
            val gear = vm.extValues["Current gear (GM)"]
            if (tft != null || gear != null) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BigStat("Gear", gear?.let { if (it == 0.0) "N/P" else it.toInt().toString() } ?: "--", Brand.Accent, Modifier.weight(1f))
                BigStat("Gearbox °C", tft?.let { Calc.fmt(it, 0) } ?: "--", if ((tft ?: 0.0) >= 110) Brand.Red else Brand.Green, Modifier.weight(1f))
            }
            if (vm.tripOn) Text("Trip ${Calc.fmt(vm.tripKm, 1)} km  ·  ${vm.tripL100?.let { Calc.fmt(it, 1) + " L/100" } ?: "-"}", color = Color.White, fontSize = 18.sp)
            Text("Alerts beep and vibrate: engine over 112 °C, charging under 12.6 V, gearbox over 115 °C, rpm over 6200. Screen stays on.", color = Color(0xFF8899AA), fontSize = 12.sp)
        }
        AnimatedVisibility(visible = alert != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Box(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Red.copy(alpha = a)).padding(18.dp)) {
                Text(alert ?: "", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DriveGauge(label: String, value: Double?, min: Double, max: Double, decimals: Int, color: Color, modifier: Modifier) {
    val frac = if (value == null) 0f else ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
    val anim by animateFloatAsState(frac, tween(350), label = "g")
    val col by animateColorAsState(color, label = "c")
    val track = Color(0xFF1A2330)
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val sw = 14.dp.toPx()
            drawArc(track, 135f, 270f, false, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(col, 135f, 270f * anim, false, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value?.let { Calc.fmt(it, decimals) } ?: "--", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Color(0xFF8899AA), fontSize = 14.sp)
        }
    }
}

// ====================================================================================== Code history
/** Every code ever seen, per module: when it first appeared, how often, when it went away and whether it came back. */
@Composable
fun CodeHistoryScreen(vm: ObdyVm) {
    val stories = vm.codeStories()
    ScreenFrame("Code history", { vm.overlay = null }) {
        if (stories.isEmpty()) Panel { Label("No scans saved yet."); Hint("Every scan is kept; this page shows how each code behaves over time.") }
        for ((module, list) in stories.groupBy { it.module }) {
            Text(module, color = Brand.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            for (st in list) Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(st.code, color = if (st.nowPresent) Brand.Red else Brand.TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Pill(if (st.nowPresent) "present" else "gone", if (st.nowPresent) Brand.Red else Brand.Green)
                    if (st.returns > 0) Pill("came back ${st.returns}×", Brand.Amber)
                }
                Label(DtcDb.describe(st.code), size = 13)
                Hint("Seen in ${st.seen} scan(s), first ${fmtDate(st.firstMs)}" + (if (st.firstKm > 0) " at %,d km".format(st.firstKm) else "") + ", last ${fmtDate(st.lastMs)}")
                // timeline dots
                Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for ((t, km, what) in st.events.takeLast(6)) Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(when (what) { "gone" -> Brand.Green; "came back" -> Brand.Amber; else -> Brand.Red }); Spacer(Modifier.width(8.dp))
                        Text("${fmtDate(t)}${if (km > 0) "  ·  %,d km".format(km) else ""}  ·  $what", color = Brand.TextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ====================================================================================== Module explorer
/** Everything a module answered to the read-only explorer: identity items and data identifiers, decoded several ways. */
@Composable
fun ExplorerScreen(vm: ObdyVm) {
    ScreenFrame("Module data", { vm.overlay = null }) {
        Panel {
            Title("What each module tells")
            Hint("Read-only requests (1A identity and 22 data). Most items are refused by the car; only answers are listed. Numbers are shown raw and as possible values, because GM does not publish what each item means. Steering values (angle, torque, motor current) show up here if the steering module shares them.")
            if (vm.explorerBusy != null) { Label("Reading... ${vm.scanProgress}", color = Brand.Amber, bold = true) }
        }
        if (vm.explorer.isEmpty() && vm.explorerBusy == null) Hint("Open Faults → Modules → a module → Explore all its data.")
        for ((resp, rows) in vm.explorer) {
            val name = vm.moduleResults.firstOrNull { it.module?.probe?.resp == resp }?.name ?: "Module $resp"
            val ok = rows.filter { it.second != null }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Title(name); Spacer(Modifier.weight(1f)); Pill("${ok.size}/${rows.size} answered", if (ok.isEmpty()) Brand.TextSecondary else Brand.Green)
                }
                if (ok.isEmpty()) Hint("This module refused every item tried.")
                for ((req, d) in ok) {
                    val b = d!!
                    val label = Gm.identItems.firstOrNull { "1A" + it.first == req }?.second
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Brand.Track).padding(8.dp)) {
                        Text(req + (label?.let { "  ·  $it" } ?: ""), color = Brand.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (label != null) Gm.decodeIdent(req.drop(2), b) else Explain.numbers(b), color = Brand.TextPrimary, fontSize = 12.sp)
                        Text(b.joinToString(" ") { "%02X".format(it) }, color = Brand.TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
        if (vm.odoCandidates.isNotEmpty()) Panel {
            Title("Looks like your odometer")
            for (c in vm.odoCandidates) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%,d km  ·  ${c.request}".format(c.value.toInt()), color = Brand.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                SoftButton("Use") { vm.useOdoCandidate(c) }
            }
        }
    }
}

private object Explain {
    /** One payload as text when printable, else as unsigned / signed numbers (8 and 16 bit). */
    fun numbers(b: IntArray): String {
        if (b.isEmpty()) return "(empty answer)"
        val t = b.filter { it != 0 }
        if (t.size >= 3 && t.all { it in 0x20..0x7E }) return "text: " + t.joinToString("") { it.toChar().toString() }
        val parts = ArrayList<String>()
        parts.add("bytes " + b.joinToString(", "))
        if (b.size >= 2) parts.add("16-bit " + (0 until b.size / 2).joinToString(", ") { (b[2 * it] shl 8 or b[2 * it + 1]).toString() })
        if (b.size == 1) parts.add("−40 = ${b[0] - 40} (if a temperature)")
        return parts.joinToString("  ·  ")
    }
}
