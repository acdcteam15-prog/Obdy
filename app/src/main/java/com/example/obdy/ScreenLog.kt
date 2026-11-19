package com.example.obdy

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.state.ToggleableState

/**
 * Writes what is on the screen into the log by reading the text of the visible Compose elements (the same information a screen
 * reader gets). A line is written when it first appears on a screen, so numbers that change are logged each time they change.
 */
@OptIn(InternalComposeUiApi::class)
object ScreenLog {
    private var prev = HashSet<String>()
    private var prevWhere = ""

    private fun root(v: View): ViewRootForTest? {
        if (v is ViewRootForTest) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) root(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun <T> SemanticsNode.prop(key: SemanticsPropertyKey<T>): T? = try { if (key in config) config[key] else null } catch (_: Throwable) { null }

    private val secretLike = Regex("^[A-Za-z0-9_\\-]{20,}$")

    private fun label(n: SemanticsNode): String {
        val parts = ArrayList<String>()
        n.prop(SemanticsProperties.Text)?.let { l -> parts.add(l.joinToString(" ") { it.text }) }
        n.prop(SemanticsProperties.ContentDescription)?.let { l -> parts.add(l.joinToString(" ")) }
        if (SemanticsProperties.Password !in n.config) {
            n.prop(SemanticsProperties.EditableText)?.let { parts.add(if (secretLike.matches(it.text)) "***" else it.text) }
        }
        n.prop(SemanticsProperties.ToggleableState)?.let { parts.add(when (it) { ToggleableState.On -> "[on]"; ToggleableState.Off -> "[off]"; else -> "[?]" }) }
        return parts.filter { it.isNotBlank() }.joinToString(" ").replace('\n', ' ').replace('\r', ' ').trim().take(600)
    }

    private fun walk(n: SemanticsNode, w: Int, h: Int, out: LinkedHashSet<String>) {
        val b = n.boundsInWindow
        if (b.bottom > 0f && b.top < h && b.right > 0f && b.left < w) {
            val t = label(n)
            if (t.isNotEmpty()) out.add(t)
        }
        for (c in n.children) walk(c, w, h, out)
    }

    /** Logs the lines that are visible now and were not visible at the previous look. A new screen logs everything on it. */
    fun snapshot(decor: View, where: String) {
        val r = root(decor) ?: return
        val dm = decor.resources.displayMetrics
        val cur = LinkedHashSet<String>()
        walk(r.semanticsOwner.rootSemanticsNode, dm.widthPixels, dm.heightPixels, cur)
        if (where != prevWhere) { prev = HashSet(); prevWhere = where; AppLog.i("[SHOWN] ---- screen: $where ----") }
        for (t in cur) if (t !in prev) AppLog.i("[SHOWN] $t")
        prev = HashSet(cur)
    }

    /** The text of the smallest labelled element under a touch, so every tap is logged with what was tapped. */
    fun labelAt(decor: View, x: Float, y: Float): String? {
        val r = root(decor) ?: return null
        var best: String? = null
        var bestArea = Float.MAX_VALUE
        fun go(n: SemanticsNode) {
            val b = n.boundsInWindow
            if (x >= b.left && x <= b.right && y >= b.top && y <= b.bottom) {
                val t = label(n)
                if (t.isNotEmpty()) { val a = b.width * b.height; if (a < bestArea) { bestArea = a; best = t } }
                for (c in n.children) go(c)
            }
        }
        go(r.semanticsOwner.rootSemanticsNode)
        return best?.take(80)
    }
}
