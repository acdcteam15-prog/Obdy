package com.example.obdy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class Ic {
    Home, Gauge, Warning, Wrench, Car, Gear, Back, Close, Battery, Chat, Doc, Search, Fuel, Chart, Heart, Shield, Cash,
    Plug, Bolt, Pin, Check, Info, Cpu, Globe, Terminal, Mic, Camera, Chevron,
}

/** The app's own icon set, drawn on a 24 x 24 grid so it needs no icon library and follows the theme colours. */
@Composable
fun AppIcon(ic: Ic, modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = Brand.TextPrimary) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val sw = 2f * u
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun pt(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color = color, start = pt(x1, y1), end = pt(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
        fun poly(vararg c: Float, close: Boolean = false) {
            val p = Path()
            p.moveTo(c[0] * u, c[1] * u)
            var i = 2
            while (i + 1 < c.size) { p.lineTo(c[i] * u, c[i + 1] * u); i += 2 }
            if (close) p.close()
            drawPath(path = p, color = color, style = st)
        }
        fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) =
            drawCircle(color = color, radius = r * u, center = pt(cx, cy), style = if (fill) Fill else st)
        fun rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) =
            drawRoundRect(color = color, topLeft = pt(l, t), size = Size((r - l) * u, (b - t) * u), cornerRadius = CornerRadius(rad * u, rad * u), style = st)

        when (ic) {
            Ic.Home -> { poly(3f, 11f, 12f, 3f, 21f, 11f); poly(5f, 9.5f, 5f, 21f, 19f, 21f, 19f, 9.5f); poly(10f, 21f, 10f, 15f, 14f, 15f, 14f, 21f) }
            Ic.Gauge -> {
                drawArc(color = color, startAngle = 135f, sweepAngle = 270f, useCenter = false, topLeft = pt(3f, 3f), size = Size(18f * u, 18f * u), style = st)
                line(12f, 12f, 16f, 8f); circle(12f, 12f, 1.3f, true)
            }
            Ic.Warning -> { poly(12f, 3.5f, 22f, 20.5f, 2f, 20.5f, close = true); line(12f, 9f, 12f, 14f); circle(12f, 17.3f, 0.9f, true) }
            Ic.Wrench -> { line(4f, 20f, 12.5f, 11.5f); circle(16.5f, 7.5f, 4.2f) }
            Ic.Car -> {
                poly(3f, 17f, 3f, 12.5f, 5.5f, 8f, 18.5f, 8f, 21f, 12.5f, 21f, 17f, close = true)
                line(3f, 12.5f, 21f, 12.5f); circle(7.5f, 17.5f, 2f); circle(16.5f, 17.5f, 2f)
            }
            Ic.Gear -> {
                circle(12f, 12f, 3f); circle(12f, 12f, 6.3f)
                for (k in 0 until 8) {
                    val a = k * Math.PI / 4
                    val c = cos(a).toFloat(); val s = sin(a).toFloat()
                    line(12f + c * 6.3f, 12f + s * 6.3f, 12f + c * 9.3f, 12f + s * 9.3f)
                }
            }
            Ic.Back -> poly(15f, 5f, 8f, 12f, 15f, 19f)
            Ic.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Ic.Battery -> { rrect(3f, 7f, 19f, 17f, 2f); line(21f, 10f, 21f, 14f); poly(10.5f, 9f, 8.5f, 12f, 12f, 12f, 10f, 15f) }
            Ic.Chat -> {
                rrect(3f, 4f, 21f, 16f, 3f); poly(8f, 16f, 8f, 20.5f, 12.5f, 16f)
                circle(8f, 10f, 0.8f, true); circle(12f, 10f, 0.8f, true); circle(16f, 10f, 0.8f, true)
            }
            Ic.Doc -> { poly(6f, 3f, 14f, 3f, 19f, 8f, 19f, 21f, 6f, 21f, close = true); poly(14f, 3f, 14f, 8f, 19f, 8f); line(9f, 13f, 16f, 13f); line(9f, 17f, 16f, 17f) }
            Ic.Search -> { circle(10.5f, 10.5f, 6f); line(15f, 15f, 20.5f, 20.5f) }
            Ic.Fuel -> { rrect(4f, 4f, 14f, 21f, 1.5f); line(7f, 8f, 11f, 8f); poly(14f, 9f, 18f, 9f, 18f, 17f, 20f, 17f, 20f, 8f, 17f, 5f) }
            Ic.Chart -> { line(5f, 20f, 5f, 12f); line(12f, 20f, 12f, 5f); line(19f, 20f, 19f, 9f) }
            Ic.Heart -> poly(2f, 12f, 7f, 12f, 9.5f, 5f, 13f, 19f, 15.5f, 12f, 22f, 12f)
            Ic.Shield -> {
                val p = Path()
                p.moveTo(12f * u, 3f * u); p.lineTo(20f * u, 6f * u); p.lineTo(20f * u, 12f * u)
                p.cubicTo(20f * u, 16.5f * u, 16.5f * u, 19.5f * u, 12f * u, 21f * u)
                p.cubicTo(7.5f * u, 19.5f * u, 4f * u, 16.5f * u, 4f * u, 12f * u)
                p.lineTo(4f * u, 6f * u); p.close()
                drawPath(path = p, color = color, style = st)
                poly(8.5f, 12f, 11f, 14.5f, 15.5f, 9.5f)
            }
            Ic.Cash -> { rrect(3f, 6f, 21f, 18f, 2f); circle(12f, 12f, 3f); circle(6.5f, 12f, 0.7f, true); circle(17.5f, 12f, 0.7f, true) }
            Ic.Plug -> { line(9f, 3f, 9f, 8f); line(15f, 3f, 15f, 8f); rrect(6f, 8f, 18f, 14f, 2f); line(12f, 14f, 12f, 21f) }
            Ic.Bolt -> poly(13f, 2.5f, 5f, 13.5f, 11f, 13.5f, 10f, 21.5f, 19f, 10f, 13f, 10f, close = true)
            Ic.Pin -> {
                val p = Path()
                p.moveTo(12f * u, 21f * u)
                p.cubicTo(12f * u, 21f * u, 5f * u, 14f * u, 5f * u, 10f * u)
                p.cubicTo(5f * u, 5.5f * u, 8f * u, 3f * u, 12f * u, 3f * u)
                p.cubicTo(16f * u, 3f * u, 19f * u, 5.5f * u, 19f * u, 10f * u)
                p.cubicTo(19f * u, 14f * u, 12f * u, 21f * u, 12f * u, 21f * u)
                p.close()
                drawPath(path = p, color = color, style = st)
                circle(12f, 10f, 2.5f)
            }
            Ic.Check -> poly(5f, 12.5f, 10f, 17.5f, 19f, 7f)
            Ic.Info -> { circle(12f, 12f, 9f); line(12f, 11f, 12f, 16.5f); circle(12f, 7.8f, 0.9f, true) }
            Ic.Cpu -> {
                rrect(6f, 6f, 18f, 18f, 1.5f); rrect(9.5f, 9.5f, 14.5f, 14.5f, 0.5f)
                for (x in listOf(9f, 15f)) { line(x, 3f, x, 6f); line(x, 18f, x, 21f) }
                for (y in listOf(9f, 15f)) { line(3f, y, 6f, y); line(18f, y, 21f, y) }
            }
            Ic.Globe -> {
                circle(12f, 12f, 9f); line(3f, 12f, 21f, 12f)
                drawOval(color = color, topLeft = pt(8f, 3f), size = Size(8f * u, 18f * u), style = st)
            }
            Ic.Terminal -> { rrect(3f, 5f, 21f, 19f, 2.5f); poly(7f, 10f, 10f, 12.5f, 7f, 15f); line(12f, 15.5f, 16f, 15.5f) }
            Ic.Mic -> {
                rrect(9f, 3f, 15f, 13f, 3f)
                drawArc(color = color, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = pt(6f, 7f), size = Size(12f * u, 11f * u), style = st)
                line(12f, 18.5f, 12f, 21.5f)
            }
            Ic.Camera -> { rrect(3f, 7f, 21f, 20f, 2.5f); circle(12f, 13.5f, 3.5f); poly(8.5f, 7f, 10f, 4f, 14f, 4f, 15.5f, 7f) }
            Ic.Chevron -> poly(9f, 5f, 16f, 12f, 9f, 19f)
        }
    }
}
