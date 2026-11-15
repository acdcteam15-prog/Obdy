package com.example.obdy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.obdy.Level
import androidx.compose.ui.graphics.lerp
import java.util.Locale

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(lerp(Brand.Card, Brand.Accent, 0.045f), Brand.Card)))
            .border(1.dp, Brush.verticalGradient(listOf(lerp(Brand.CardBorder, Brand.Accent, 0.25f), Brand.CardBorder)), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun GradientButton(text: String, brush: Brush, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(targetValue = if (pressed) 0.97f else 1f, animationSpec = tween(90), label = "btn")
    val haptic = LocalHapticFeedback.current
    // A soft light sweep across the button every few seconds (only when it can be pressed).
    val shine = rememberInfiniteTransition(label = "shine")
    val sx by shine.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(2600, delayMillis = 1800)), label = "sx")
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = sc; scaleY = sc; shadowElevation = if (enabled) 10f else 0f; shape = RoundedCornerShape(16.dp); clip = true }
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) brush else Brush.linearGradient(listOf(Brand.Track, Brand.Track)))
            .drawWithContent {
                drawContent()
                if (enabled) {
                    val x = size.width * sx
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent), start = Offset(x - 120f, 0f), end = Offset(x + 120f, size.height)))
                }
            }
            .clickable(interactionSource = src, indication = null, enabled = enabled) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .padding(vertical = 15.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else Brand.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(targetValue = if (pressed) 0.96f else 1f, animationSpec = tween(90), label = "soft")
    Box(
        modifier
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) Brand.Accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (enabled) Brand.Accent.copy(alpha = 0.7f) else Brand.CardBorder, RoundedCornerShape(14.dp))
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Brand.Accent else Brand.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** A square action tile with an icon: the main way to start things on Home and Tools. */
@Composable
fun Tile(ic: Ic, title: String, sub: String = "", modifier: Modifier = Modifier, accent: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(targetValue = if (pressed) 0.96f else 1f, animationSpec = tween(100), label = "tile")
    val shape = RoundedCornerShape(18.dp)
    val haptic = LocalHapticFeedback.current
    Column(
        modifier
            .graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(Brand.Card, Brand.Accent, if (accent) 0.10f else 0.04f), Brand.Card)))
            .border(1.dp, if (accent && enabled) Brand.Accent.copy(alpha = 0.6f) else Brand.CardBorder, shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(if (accent) Brand.MainBrush else Brush.linearGradient(listOf(Brand.Accent.copy(alpha = 0.20f), Brand.Accent.copy(alpha = 0.08f)))),
            contentAlignment = Alignment.Center,
        ) { AppIcon(ic, size = 22.dp, color = if (accent) Color.White else Brand.Accent) }
        Text(title, color = Brand.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (sub.isNotEmpty()) Text(sub, color = Brand.TextSecondary, fontSize = 12.sp, maxLines = 2)
    }
}

/** Fades and slides its content in when the screen opens. [index] staggers the items. */
@Composable
fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state, modifier = modifier,
        enter = fadeIn(animationSpec = tween(300, delayMillis = index * 50)) +
            slideInVertically(animationSpec = tween(300, delayMillis = index * 50)) { it / 6 },
    ) { content() }
}

@Composable
fun StatusDot(color: Color, pulsing: Boolean, size: Dp = 12.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(initialValue = 1f, targetValue = if (pulsing) 0.25f else 1f, animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse), label = "pulseA")
    Box(Modifier.size(size).graphicsLayer { alpha = a }.clip(CircleShape).background(color))
}

@Composable
fun IconTap(ic: Ic, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        AppIcon(ic, size = 24.dp, color = Brand.Accent)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun Dot(color: Color) {
    Box(Modifier.padding(end = 2.dp).size(10.dp).clip(CircleShape).background(color))
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) = Text(text, color = Brand.TextSecondary, fontSize = 13.sp, modifier = modifier)

@Composable
fun Title(text: String) = Text(text, color = Brand.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)

@Composable
fun Label(text: String, bold: Boolean = false, color: Color = Brand.TextPrimary, size: Int = 14) =
    Text(text, color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)

fun levelColor(l: Level): Color = when (l) {
    Level.Good -> Brand.Green
    Level.Warn -> Brand.Amber
    Level.Bad -> Brand.Red
    Level.Info -> Brand.Blue
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, number: Boolean = false, singleLine: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth(),
    )
}

/** A round gauge: the arc fills with the value between [min] and [max]. */
/**
 * Live gauge: gradient arc that turns amber/red near the top, tick marks, the lowest and highest value seen since the
 * screen opened (small markers on the arc), and a tiny trend line of the last readings.
 */
@Composable
fun GaugeCard(name: String, value: Double?, unit: String, min: Double, max: Double, modifier: Modifier = Modifier, decimals: Int = 0, hint: String = "") {
    val span = if (max > min) max - min else 1.0
    val target = if (value == null) 0.0 else ((value - min) / span).coerceIn(0.0, 1.0)
    val animated by animateFloatAsState(targetValue = target.toFloat(), animationSpec = tween(450), label = "gauge")
    val shown by animateFloatAsState(targetValue = (value ?: 0.0).toFloat(), animationSpec = tween(450), label = "num")
    val hist = remember { ArrayList<Double>() }
    var lo by remember { mutableStateOf<Double?>(null) }
    var hi by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(value) {
        if (value != null) {
            hist.add(value); if (hist.size > 40) hist.removeAt(0)
            lo = minOf(lo ?: value, value); hi = maxOf(hi ?: value, value)
        }
    }
    val zone = when { target > 0.9 -> Brand.Red; target > 0.75 -> Brand.Amber; else -> Brand.Accent }
    val track = Brand.Track
    val accent = Brand.Accent
    val dim = Brand.TextSecondary
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(lerp(Brand.Card, zone, 0.06f), Brand.Card)))
            .border(1.dp, lerp(Brand.CardBorder, zone, 0.25f), RoundedCornerShape(16.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                val stroke = 10.dp.toPx()
                val d = minOf(size.width, size.height) - stroke - 6.dp.toPx()
                val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                val c = Offset(size.width / 2f, size.height / 2f)
                drawArc(color = track, startAngle = 135f, sweepAngle = 270f, useCenter = false, topLeft = tl, size = Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                // ticks every 10 %
                for (i in 0..10) {
                    val ang = Math.toRadians(135.0 + 27.0 * i)
                    val r1 = d / 2f + stroke * 0.9f; val r2 = r1 + (if (i % 5 == 0) 5.dp.toPx() else 3.dp.toPx())
                    drawLine(dim.copy(alpha = 0.5f), Offset(c.x + (r1 * Math.cos(ang)).toFloat(), c.y + (r1 * Math.sin(ang)).toFloat()), Offset(c.x + (r2 * Math.cos(ang)).toFloat(), c.y + (r2 * Math.sin(ang)).toFloat()), strokeWidth = 2f)
                }
                // Gradient rotated so it starts where the arc starts: blue, then amber at 75 %, red at the top of the scale.
                if (animated > 0f) rotate(135f, c) {
                    drawArc(
                        brush = Brush.sweepGradient(0f to accent, 0.50f to accent, 0.60f to Brand.Amber, 0.72f to Brand.Red, 1f to Brand.Red, center = c),
                        startAngle = 0f, sweepAngle = 270f * animated, useCenter = false, topLeft = tl, size = Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                // min / max markers
                for ((v, col) in listOfNotNull(lo?.let { it to dim }, hi?.let { it to Color.White })) {
                    val f = ((v - min) / span).coerceIn(0.0, 1.0)
                    val ang = Math.toRadians(135.0 + 270.0 * f)
                    val r = d / 2f
                    drawCircle(col, radius = 3.dp.toPx(), center = Offset(c.x + (r * Math.cos(ang)).toFloat(), c.y + (r * Math.sin(ang)).toFloat()))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (value == null) "--" else String.format(Locale.US, "%.${decimals}f", shown), color = Brand.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(unit, color = Brand.TextSecondary, fontSize = 12.sp)
            }
        }
        if (hist.size >= 3) Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val lo2 = hist.min(); val hi2 = hist.max(); val rng = if (hi2 - lo2 < 1e-6) 1.0 else hi2 - lo2
            val p = Path()
            hist.forEachIndexed { i, v ->
                val x = size.width * i / (hist.size - 1).toFloat()
                val y = size.height - ((v - lo2) / rng * size.height).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, zone.copy(alpha = 0.8f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        Text(name, color = Brand.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        if (lo != null && hi != null && hi!! > lo!!) Text("min ${String.format(Locale.US, "%.${decimals}f", lo)} · max ${String.format(Locale.US, "%.${decimals}f", hi)}", color = Brand.TextSecondary.copy(alpha = 0.8f), fontSize = 10.sp)
        if (hint.isNotEmpty()) Text(hint, color = Brand.TextSecondary.copy(alpha = 0.7f), fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

/** Placeholder bars with a moving light, shown while something loads (scans, reads). */
@Composable
fun Shimmer(lines: Int = 3) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300)), label = "x")
    val base = Brand.Track
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until lines) {
            Box(
                Modifier.fillMaxWidth(if (i == lines - 1) 0.6f else 1f).height(14.dp).clip(RoundedCornerShape(7.dp))
                    .drawWithContent {
                        drawRect(base)
                        val w = size.width
                        drawRect(Brush.linearGradient(listOf(base, Color.White.copy(alpha = 0.10f), base), start = Offset(w * x - 200f, 0f), end = Offset(w * x + 200f, 0f)))
                    },
            )
        }
    }
}

/** Toast: rounded card that slides up, with an icon chosen from the message. */
@Composable
fun ToastCard(text: String) {
    val ic = when {
        text.contains("not", true) || text.contains("fail", true) || text.contains("error", true) -> Ic.Info
        text.contains("code", true) -> Ic.Warning
        else -> Ic.Check
    }
    Row(
        Modifier.graphicsLayer { shadowElevation = 16f; shape = RoundedCornerShape(18.dp); clip = true }
            .clip(RoundedCornerShape(18.dp)).background(lerp(Brand.Card, Brand.Accent, 0.10f))
            .border(1.dp, Brand.Accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(ic, size = 18.dp, color = Brand.Accent); Spacer(Modifier.width(10.dp))
        Text(text, color = Brand.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

class ChartSeries(val name: String, val points: List<Pair<Double, Double>>, val color: Color)

/** Simple line chart. [shared] = all lines use the same vertical scale (for comparing two drives); otherwise each fills the height. */
@Composable
fun LineChart(series: List<ChartSeries>, modifier: Modifier = Modifier, shared: Boolean = false, height: Int = 170) {
    val grid = Brand.CardBorder
    Canvas(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(10.dp)).background(Brand.Track.copy(alpha = 0.4f))) {
        val pad = 8.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        for (i in 0..4) {
            val y = pad + h * i / 4f
            drawLine(grid, Offset(pad, y), Offset(pad + w, y), strokeWidth = 1f)
        }
        val all = series.flatMap { it.points }
        if (all.isEmpty()) return@Canvas
        val tMax = (all.maxOf { it.first }).coerceAtLeast(1.0)
        val gMin = all.minOf { it.second }
        val gMax = all.maxOf { it.second }
        for (s in series) {
            if (s.points.size < 2) continue
            val lo = if (shared) gMin else s.points.minOf { it.second }
            val hi = if (shared) gMax else s.points.maxOf { it.second }
            val range = if (hi - lo < 1e-9) 1.0 else hi - lo
            val path = Path()
            s.points.forEachIndexed { i, p ->
                val x = pad + (p.first / tMax * w).toFloat()
                val y = pad + h - ((p.second - lo) / range * h).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, s.color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun Legend(items: List<Pair<String, Color>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((n, c) in items) { Row(verticalAlignment = Alignment.CenterVertically) { Dot(c); Spacer(Modifier.width(4.dp)); Text(n, color = Brand.TextSecondary, fontSize = 12.sp) } }
    }
}


/** The Obdy logo (same drawing as the launcher icon): gradient gauge, needle and pulse line on a dark tile. */
@Composable
fun AppLogo(size: Dp = 36.dp) {
    Canvas(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Brush.radialGradient(listOf(Color(0xFF16233A), Color(0xFF05070B))))) {
        val u = this.size.width / 66f            // drawing units: the 66-unit safe area of the icon
        fun o(x: Float, y: Float) = Offset((x - 21f) * u, (y - 21f) * u)
        val r = 24f * u
        val c = o(54f, 56f)
        val arcTl = Offset(c.x - r, c.y - r)
        drawArc(
            Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF2F80FF), Color(0xFF8B5CF6)), start = o(30f, 70f), end = o(78f, 40f)),
            startAngle = 135f, sweepAngle = 270f, useCenter = false, topLeft = arcTl, size = Size(2 * r, 2 * r), style = Stroke(6.5f * u, cap = StrokeCap.Round),
        )
        drawLine(Color.White, c, o(60.8f, 39.6f), strokeWidth = 4f * u, cap = StrokeCap.Round)
        drawCircle(Color.White, radius = 4.5f * u, center = c)
        val p = Path().apply { moveTo(o(41f, 80f).x, o(41f, 80f).y); for ((x, y) in listOf(47.5f to 80f, 50.5f to 75f, 54.5f to 85f, 57.5f to 80f, 67f to 80f)) lineTo(o(x, y).x, o(x, y).y) }
        drawPath(p, Color(0xFF00E5FF), style = Stroke(2.6f * u, cap = StrokeCap.Round))
    }
}
