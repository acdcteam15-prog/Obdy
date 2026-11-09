package com.example.obdy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdTest {
    @Test fun singleFrame() {
        val r = ObdParse.parse("7E8 06 41 00 BE 3E B8 13 \r\r>")
        assertTrue(r.ok)
        assertEquals(1, r.frames.size)
        assertEquals("7E8", r.frames[0].header)
        assertEquals("41 00 BE 3E B8 13", r.frames[0].hex())
        val sup = Pids.supported(0, r.frames[0].data.copyOfRange(2, 6))
        assertTrue(0x0C in sup); assertTrue(0x0D in sup); assertTrue(0x05 in sup); assertFalse(0x02 in sup)
    }

    @Test fun errors() {
        assertEquals("NO DATA", ObdParse.parse("NO DATA\r\r>").error)
        assertEquals("UNABLE TO CONNECT", ObdParse.parse("SEARCHING...\rUNABLE TO CONNECT\r\r>").error)
        assertEquals("?", ObdParse.parse("?\r\r>").error)
        assertFalse(ObdParse.parse("NO DATA").ok)
    }

    @Test fun vinMultiFrame() {
        val r = ObdParse.parse("7E8 10 14 49 02 01 31 47 31\r7E8 21 5A 54 35 33 38 32 33\r7E8 22 36 30 30 31 32 33 34\r\r>")
        val f = r.positive(0x09)
        assertNotNull(f)
        val vin = Vin.decode(f)
        assertEquals("1G1ZT538236001234", vin)
        assertEquals(2003, Vin.modelYear(vin!!))
        assertEquals(2013, Vin.modelYear("1G1ZT53D2DF123456"))
    }

    @Test fun vinNumberedLines() {
        val r = ObdParse.parse("014\r0: 49 02 01 31 47 31\r1: 5A 54 35 33 38 32 33\r2: 36 30 30 31 32 33 34\r\r>")
        assertEquals("1G1ZT538236001234", Vin.decode(r.frames.firstOrNull()))
    }

    @Test fun dtcBytes() {
        assertEquals("P0301", DtcDecode.fromBytes(0x03, 0x01))
        assertEquals("P0133", DtcDecode.fromBytes(0x01, 0x33))
        assertEquals("C0300", DtcDecode.fromBytes(0x43, 0x00))
        assertEquals("B0100", DtcDecode.fromBytes(0x81, 0x00))
        assertEquals("U0100", DtcDecode.fromBytes(0xC1, 0x00))
        assertEquals("P1516", DtcDecode.fromBytes(0x15, 0x16))
        assertEquals("P2135", DtcDecode.fromBytes(0x21, 0x35))
    }

    @Test fun mode03() {
        val one = ObdParse.parse("7E8 06 43 01 01 33 00 00\r>").frames[0]
        assertEquals(listOf("P0133"), DtcDecode.fromMode(one, 0x03))
        val none = ObdParse.parse("7E8 02 43 00\r>").frames[0]
        assertTrue(DtcDecode.fromMode(none, 0x03).isEmpty())
        val multi = ObdParse.parse("7E8 10 08 43 03 01 33 03 01\r7E8 21 04 02 00 00 00 00 00\r>").frames[0]
        assertEquals(listOf("P0133", "P0301", "P0402"), DtcDecode.fromMode(multi, 0x03))
    }

    @Test fun twoModules() {
        val r = ObdParse.parse("7E8 06 43 01 03 00 00 00\r7EA 06 43 01 07 41 00 00\r>")
        assertEquals(2, r.frames.size)
        assertEquals("7E8", r.frames[0].header)
        assertEquals(listOf("P0300"), DtcDecode.fromMode(r.frames[0], 0x03))
        assertEquals("7EA", r.frames[1].header)
        assertEquals(listOf("P0741"), DtcDecode.fromMode(r.frames[1], 0x03))
    }

    @Test fun negativeResponse() {
        val r = ObdParse.parse("641 03 7F 19 11\r>")
        assertEquals(1, r.negatives().size)
        assertEquals("641", r.frames[0].header)
    }

    @Test fun udsAndGmlan() {
        val uds = Frame("643", byteArrayOf(0x59, 0x02, 0xFF.toByte(), 0x40, 0x35, 0x11, 0x2F))
        assertEquals(listOf("C0035-11" to "status 2F"), DtcDecode.fromUds(uds))
        val gm = Frame("643", byteArrayOf(0xE9.toByte(), 0x81.toByte(), 0x01, 0x40, 0x35, 0x2F))
        assertEquals(listOf("C0035" to "status 2F"), DtcDecode.fromGmlan(gm))
    }

    @Test fun pidFormulas() {
        assertEquals(1726.0, Pids.byId(0x0C)!!.value(byteArrayOf(0x1A, 0xF8.toByte()))!!, 0.001)
        assertEquals(83.0, Pids.byId(0x05)!!.value(byteArrayOf(0x7B))!!, 0.001)
        assertEquals(0.0, Pids.byId(0x06)!!.value(byteArrayOf(0x80.toByte()))!!, 0.001)
        assertEquals(14.2, Pids.byId(0x42)!!.value(byteArrayOf(0x37, 0x78))!!, 0.001)
        assertNull(Pids.byId(0x0C)!!.value(byteArrayOf(0x1A)))
    }

    @Test fun readiness() {
        val m = Readiness.decode(byteArrayOf(0x81.toByte(), 0x07, 0xE5.toByte(), 0x04))!!
        assertTrue(m.milOn); assertEquals(1, m.dtcCount)
        assertTrue(m.monitors.first { it.name == "Catalyst" }.complete)
        assertFalse(m.monitors.first { it.name == "Evaporative system" }.complete)
        assertFalse(m.monitors.first { it.name == "Secondary air" }.supported)
    }

    @Test fun fuelAndTrip() {
        assertEquals(1.150, Calc.fuelLitersPerHour(3.5), 0.01)
        val t = TripAcc()
        for (i in 0 until 3600) t.add(1.0, 60.0, 6.0)
        assertEquals(60.0, t.km, 0.01); assertEquals(6.0, t.liters, 0.01)
        assertEquals(10.0, t.litersPer100!!, 0.01)
        t.add(30.0, 100.0, 5.0)   // a gap must not add distance
        assertEquals(60.0, t.km, 0.01)
    }

    @Test fun battery() {
        val v = BatteryTest.evaluate(12.6, 8.5, 14.1, 14.2)
        assertEquals(Level.Good, v[0].level); assertEquals(Level.Bad, v[1].level)
        assertEquals(Level.Good, v[2].level); assertEquals(Level.Good, v[3].level)
        assertEquals(Level.Bad, BatteryTest.evaluate(null, null, 12.4, null)[0].level)
    }

    @Test fun maintenance() {
        val oil = MaintItem("oil", "Oil", 5000, 6, lastKm = 10000, lastDateMs = 1_000_000L)
        val d = Maintenance.due(oil, 14800, 1_000_000L + 86_400_000L)
        assertEquals(200, d.kmLeft); assertTrue(d.soon); assertFalse(d.overdue)
        assertTrue(Maintenance.due(oil, 15100, 1_000_000L).overdue)
    }

    @Test fun recordingRoundTrip() {
        val r = Recording("Test, drive", 123L, listOf("RPM", "Speed"), listOf("rpm", "km/h"))
        r.add(0.0, doubleArrayOf(800.0, 0.0)); r.add(1.0, doubleArrayOf(1500.0, Double.NaN))
        val back = Recording.fromCsv(r.toCsv())!!
        assertEquals(2, back.rows.size); assertEquals("Test  drive", back.name)
        assertEquals(1, back.series(1).size)
        assertEquals(1500.0, back.stats(0)!!.third, 0.001)
    }

    @Test fun dtcDb() {
        assertTrue(DtcDb.describe("P0301").contains("Cylinder 1"))
        assertTrue(DtcDb.describe("P1999").startsWith("Manufacturer"))
        assertTrue(DtcDb.causes("P0171").isNotEmpty())
        assertEquals(2, DtcDb.severity("P0302"))
    }
}

class PartsTest {
    @Test fun partsForCodes() {
        assertTrue(Parts.forCode("P0301").any { it.name.contains("Spark") })
        assertEquals(Zone.ExhaustFront, Parts.forCode("P0420").first().zone)
        assertEquals(Zone.WheelRR, Parts.forCode("C0050").first().zone)
        assertEquals(Zone.WheelFL, Parts.forCode("C0035").first().zone)
        assertTrue(Parts.forCode("P9999").isEmpty())
        assertEquals(Zone.ExhaustRear, Parts.zoneByName("exhaustrear"))
    }

    @Test fun guidedQuestions() {
        val q = Guided.questions("P0171")
        assertEquals("done", q[0].id)
        assertTrue(q.any { it.id == "hiss" })
        assertTrue(Guided.questions("U0100").any { it.id == "batt" })
    }

    @Test fun nextStepsSkipDone() {
        val causes = DtcDb.causes("P0171")
        val all = Guided.nextSteps("P0171", emptySet())
        val some = Guided.nextSteps("P0171", setOf(causes[0].take(70)))
        assertTrue(all.size > some.size)
        assertTrue(some.none { it.startsWith(causes[0].take(40)) })
    }
}
