package com.example.obdy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftTest {
    private fun drive(): List<ShiftSample> {
        val s = ArrayList<ShiftSample>()
        var t = 0.0
        fun add(rpm: Double, v: Double) { s.add(ShiftSample(t, rpm, v)); t += 0.12 }
        for (k in 0 until 10) { val v = 10.0 + k * 1.5; add(v * 100, v) }
        add(2600.0, 26.0); add(3100.0, 27.0); add(2500.0, 28.0); add(1850.0, 28.5)
        for (k in 0 until 10) { val v = 29.0 + k * 1.5; add(v * 65, v) }
        add(2400.0, 43.0); add(1950.0, 43.5); add(1950.0, 43.5)
        for (k in 0 until 8) { val v = 44.0 + k; add(v * 45, v) }
        return s
    }

    @Test fun findsFlareAndNormalShift() {
        val sh = ShiftAnalysis.shifts(drive())
        assertEquals(2, sh.size)
        assertTrue(sh[0].verdict.startsWith("Flare"))
        assertEquals("Normal", sh[1].verdict)
    }

    @Test fun findsEngagementDipAndIgnoresSteadyIdle() {
        val e = ArrayList<ShiftSample>()
        var t = 0.0
        for (k in 0 until 24) {
            val r = when (k) { 12, 13, 14, 15 -> 540.0; 16 -> 640.0; else -> 700.0 + (k % 3) * 5 }
            e.add(ShiftSample(t, r, 0.0)); t += 0.15
        }
        val en = ShiftAnalysis.engagements(e)
        assertEquals(1, en.size)
        assertTrue(en[0].dip >= 150)
        val idle = (0 until 40).map { ShiftSample(it * 0.15, 700.0 + (it % 4) * 6, 0.0) }
        assertTrue(ShiftAnalysis.engagements(idle).isEmpty())
    }
}
