package com.example.obdy.ui

import androidx.compose.foundation.Canvas
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
import java.util.Locale

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(18.dp))
            .background(Brand.Card)
            .border(1.dp, Brand.CardBorder, RoundedCornerShape(18.dp))
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
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) brush else Brush.linearGradient(listOf(Brand.Track, Brand.Track)))
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
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
    Column(
        modifier
            .graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .background(Brand.Card)
            .border(1.dp, if (accent && enabled) Brand.Accent.copy(alpha = 0.6f) else Brand.CardBorder, shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
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
@Composable
fun GaugeCard(name: String, value: Double?, unit: String, min: Double, max: Double, modifier: Modifier = Modifier, decimals: Int = 0, hint: String = "") {
    val target = if (value == null || max <= min) 0.0 else ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    val animated by animateFloatAsState(targetValue = target.toFloat(), animationSpec = tween(450), label = "gauge")
    val frac = animated.toDouble()
    val arcColor = if (target > 0.88) Brand.Red else Brand.Accent
    val track = Brand.Track
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Brand.Card).border(1.dp, Brand.CardBorder, RoundedCornerShape(14.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val stroke = 11.dp.toPx()
                val d = minOf(size.width, size.height) - stroke
                val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                drawArc(color = track, startAngle = 135f, sweepAngle = 270f, useCenter = false, topLeft = tl, size = Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                if (frac > 0.0) drawArc(color = arcColor, startAngle = 135f, sweepAngle = (270.0 * frac).toFloat(), useCenter = false, topLeft = tl, size = Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (value == null) "--" else String.format(Locale.US, "%.${decimals}f", value), color = Brand.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(unit, color = Brand.TextSecondary, fontSize = 12.sp)
            }
        }
        Text(name, color = Brand.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        if (hint.isNotEmpty()) Text(hint, color = Brand.TextSecondary.copy(alpha = 0.7f), fontSize = 10.sp, textAlign = TextAlign.Center)
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
