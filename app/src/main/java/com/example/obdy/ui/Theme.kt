package com.example.obdy.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** One complete set of colours. [gradA1]..[gradA2] is the main button/gauge gradient, [gradB1]..[gradB2] the secondary one. */
data class Palette(
    val key: String, val name: String, val light: Boolean,
    val bg: Color, val card: Color, val border: Color, val header: Color, val track: Color,
    val text: Color, val textDim: Color, val accent: Color,
    val gradA1: Color, val gradA2: Color, val gradB1: Color, val gradB2: Color,
)

object Palettes {
    val all = listOf(
        Palette("dash", "Dash (amber gauges)", false, Color(0xFF090B0F), Color(0xFF12161D), Color(0xFF262D3A), Color(0xFF0D1015), Color(0xFF1C222D),
            Color(0xFFF4F1EA), Color(0xFF8E94A3), Color(0xFFFFB020), Color(0xFFFFB020), Color(0xFFFF5A1F), Color(0xFF0F8FA8), Color(0xFF3CD2E8)),
        Palette("turbo", "Turbo (electric cyan)", false, Color(0xFF060C14), Color(0xFF0D1824), Color(0xFF1B3149), Color(0xFF09121C), Color(0xFF162739),
            Color(0xFFEAF6FF), Color(0xFF7F9BB5), Color(0xFF29D3FF), Color(0xFF1FA2FF), Color(0xFF12D8C8), Color(0xFF5B3BD9), Color(0xFF9B6CFF)),
        Palette("redline", "Redline", false, Color(0xFF0B0707), Color(0xFF1A1011), Color(0xFF3A1D20), Color(0xFF130B0C), Color(0xFF2A1A1C),
            Color(0xFFFFF1F1), Color(0xFFB59A9C), Color(0xFFFF4D4D), Color(0xFFE53935), Color(0xFFFF8A00), Color(0xFF7A1010), Color(0xFFFF4D4D)),
        Palette("stealth", "Stealth (AMOLED)", false, Color(0xFF000000), Color(0xFF0A0A0A), Color(0xFF242424), Color(0xFF000000), Color(0xFF171717),
            Color(0xFFEDEDED), Color(0xFF8A8A8A), Color(0xFF7CFFB2), Color(0xFF3DDC84), Color(0xFF00B0FF), Color(0xFF3A3A3A), Color(0xFF8A8A8A)),
        Palette("light", "Daylight", true, Color(0xFFF4F5F8), Color(0xFFFFFFFF), Color(0xFFC9CEDA), Color(0xFFE8EAF0), Color(0xFFDFE3EC),
            Color(0xFF14171F), Color(0xFF515A70), Color(0xFFD97800), Color(0xFFE08A00), Color(0xFFE64A19), Color(0xFF00809A), Color(0xFF16B0CC)),
    )

    fun byKey(key: String): Palette = all.firstOrNull { it.key == key } ?: all[0]
}

/** Every colour in the app comes from here; the values follow the chosen palette (Compose state, so screens redraw at once). */
object Brand {
    var palette by mutableStateOf(Palettes.all[0])

    val BgDeep: Color get() = palette.bg
    val Card: Color get() = palette.card
    val CardBorder: Color get() = palette.border
    val Header: Color get() = palette.header
    val Accent: Color get() = palette.accent
    val TextPrimary: Color get() = palette.text
    val TextSecondary: Color get() = palette.textDim
    val Track: Color get() = palette.track

    // status colours keep their meaning in every theme
    val Green = Color(0xFF2ECC71)
    val Red = Color(0xFFFF5252)
    val Amber = Color(0xFFFFB300)
    val Blue = Color(0xFF448AFF)

    val MainBrush: Brush get() = Brush.linearGradient(listOf(palette.gradA1, palette.gradA2))
    val AltBrush: Brush get() = Brush.linearGradient(listOf(palette.gradB1, palette.gradB2))
    val DangerBrush: Brush get() = Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFFF6F00)))
    val GoodBrush: Brush get() = Brush.linearGradient(listOf(Color(0xFF00897B), Color(0xFF2ECC71)))
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun ObdyTheme(content: @Composable () -> Unit) {
    val p = Brand.palette
    val scheme = if (p.light) lightColorScheme(
        primary = p.accent, onPrimary = Color.White, secondary = p.accent,
        background = p.bg, onBackground = p.text, surface = p.card, onSurface = p.text,
        surfaceVariant = p.track, onSurfaceVariant = p.textDim, outline = p.border, error = Brand.Red,
    ) else darkColorScheme(
        primary = p.accent, onPrimary = Color.Black, secondary = p.accent,
        background = p.bg, onBackground = p.text, surface = p.card, onSurface = p.text,
        surfaceVariant = p.track, onSurfaceVariant = p.textDim, outline = p.border, error = Brand.Red,
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as? Activity)?.window ?: return@SideEffect
            w.statusBarColor = p.header.toArgb()
            w.navigationBarColor = p.bg.toArgb()
            w.setBackgroundDrawable(ColorDrawable(p.bg.toArgb()))
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = p.light
                isAppearanceLightNavigationBars = p.light
            }
        }
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes) {
        // The surface gives every screen a background AND the right text colour.
        Surface(modifier = Modifier.fillMaxSize(), color = p.bg, contentColor = p.text, content = content)
    }
}
