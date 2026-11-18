package com.example.obdy

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A pretend adapter: answers each command with whatever [respond] says. A monitor command answers without a prompt, like the real thing. */
class FakeTransport(private val respond: (String) -> String) : Transport {
    val sent = ArrayList<String>()
    private var pending = ""
    override val label = "fake"
    override suspend fun open() {}
    override suspend fun write(text: String) { sent.add(text); pending = respond(text) }
    override suspend fun readUntilPrompt(timeoutMs: Long): String {
        val r = pending; pending = ""
        return if (r.contains(">") || r.startsWith("##")) r.removePrefix("##") else r + "\r\r>"
    }
    override fun drain() {}
    override fun close() {}
}

class BusTest {
    // ------------------------------------------------------------------------------------------ frames, signals
    @Test fun parsesMonitorFrames() {
        val t = "0C9 3F 80 00 12\r1A0 01 02\rBUFFER FULL\r18DAF110 03 41 0C 1A\rSTOPPED\r"
        val f = CanParse.parse(t, 5L)
        assertEquals(3, f.size)
        assertEquals("0C9", f[0].id); assertEquals("3F 80 00 12", f[0].hex())
        assertEquals("18DAF110", f[2].id)
        assertEquals(1, CanParse.overflows(t))
    }

    private fun cap(vararg rows: Pair<String, List<Int>>): Capture {
        val c = Capture(); var t = 0L
        for ((id, d) in rows) { t += 10; c.add(listOf(CanFrame(id, d.toIntArray(), t))) }
        c.endMs = t
        return c
    }

    @Test fun signalFinderFindsDoorBitAndIgnoresCounter() {
        val rows = ArrayList<Pair<String, List<Int>>>()
        val acts = ArrayList<Pair<String, List<Int>>>()
        for (i in 0 until 40) {
            rows.add("3E9" to listOf(0x00, 0x10, i % 16, 0))            // byte 3 is a counter in both
            acts.add("3E9" to listOf(0x00, 0x10, i % 16, 0))
            rows.add("1F5" to listOf(0x00, 0x00))
            acts.add("1F5" to listOf(0x00, if (i > 5) 0x04 else 0x00))     // door bit appears
        }
        val found = SignalFinder.find(cap(*rows.toTypedArray()), cap(*acts.toTypedArray()))
        assertEquals("1F5", found.first().id)
        assertEquals(1, found.first().byteIndex)
        assertEquals(0x04, found.first().bits)
        assertTrue(found.none { it.id == "3E9" })
    }

    @Test fun customSignalDecoding() {
        val d = intArrayOf(0x12, 0x34, 0xFF, 0x80)
        assertEquals(0x1234.toDouble(), CustomSignal("a", "1", 0, 2).decode(d)!!, 0.0)
        assertEquals(0x3412.toDouble(), CustomSignal("a", "1", 0, 2, big = false).decode(d)!!, 0.0)
        assertEquals(-128.0, CustomSignal("a", "1", 3, 1, signed = true).decode(d)!!, 0.0)
        assertEquals(1.0, CustomSignal("door", "1", 1, 1, mask = 0x04).decode(d)!!, 0.0)
        assertEquals(0.0, CustomSignal("door", "1", 0, 1, mask = 0x01).decode(d)!!, 0.0)
        assertEquals(7.5, CustomSignal("t", "1", 0, 1, scale = 0.5, offset = -10.5).decode(intArrayOf(36))!!, 1e-9)
        assertNull(CustomSignal("x", "1", 3, 2).decode(d))
    }

    @Test fun alertOnlyWhileMoving() {
        val r = AlertRule("Door", ">", 0.5, "Door open", onlyMoving = true)
        assertFalse(r.fires(1.0, 0.0)); assertFalse(r.fires(1.0, null)); assertTrue(r.fires(1.0, 30.0)); assertFalse(r.fires(0.0, 30.0))
    }

    // ------------------------------------------------------------------------------------------ fault state
    @Test fun faultStates() {
        assertEquals(FaultState.Pending, FaultStates.of(Fault("P0300", "ECM", "pending")))
        assertEquals(FaultState.Active, FaultStates.of(Fault("C0545", "EPS", "stored", "status 09")))
        assertEquals(FaultState.Pending, FaultStates.of(Fault("C0545", "EPS", "stored", "status 04")))
        assertEquals(FaultState.History, FaultStates.of(Fault("C0545", "EPS", "stored", "status 08")))
        assertEquals(FaultState.Stored, FaultStates.of(Fault("P0420", "ECM", "stored")))
    }

    // ------------------------------------------------------------------------------------------ safety
    @Test fun readOnlyGuard() {
        for (ok in listOf("0100", "22F190", "1A90", "3E00", "A98112", "190208", "0902")) assertTrue(ok, Safety.isReadOnly(ok))
        for (bad in listOf("2E1234AA", "2F123403", "3101FF00", "1101", "14FFFFFF", "04", "2701", "AE0101", "A5", "zz")) assertFalse(bad, Safety.isReadOnly(bad))
    }

    private fun def(start: String, stop: String = "", secs: Int = 5) = ActiveTestDef("t", "Test", "241", "641", start, stop, secs, "")

    @Test fun activeGuard() {
        assertEquals("2F123400", ActiveGuard.defaultStop("2F 12 34 03"))
        assertEquals("", ActiveGuard.defaultStop("AE0102"))
        assertNull(ActiveGuard.refuse(def("2F123403", "2F123400"), 0.0, 700.0, 12.6))
        assertNotNull(ActiveGuard.refuse(def("2F123403", "2F123400"), null, 700.0, 12.6))       // speed unknown
        assertNotNull(ActiveGuard.refuse(def("2F123403", "2F123400"), 12.0, 700.0, 12.6))       // moving
        assertNotNull(ActiveGuard.refuse(def("2F123403", "2F123400"), 0.0, 700.0, 11.0))        // weak battery
        assertNotNull(ActiveGuard.refuse(def("2F123403", ""), 0.0, 700.0, 12.6))                // no stop command for 2F
        assertNotNull(ActiveGuard.refuse(def("2E123403", "2F123400"), 0.0, 700.0, 12.6))        // a write is never allowed
        assertNotNull(ActiveGuard.refuse(def("1101", ""), 0.0, 700.0, 12.6))                    // ECU reset is never allowed
        assertNotNull(ActiveGuard.refuse(def("AE0102", "", 99), 0.0, 700.0, 12.6))              // too long
        assertNull(ActiveGuard.refuse(def("AE0102", "", 5), 0.0, null, null))
    }

    // ------------------------------------------------------------------------------------------ sweep decisions
    @Test fun sweepJudging() {
        fun fr(h: String, vararg b: Int) = listOf(Frame(h, ByteArray(b.size) { b[it].toByte() }))
        val hit = DidSweep.judge(0x1940, fr("644", 0x62, 0x19, 0x40, 0x55), "644").hit
        assertEquals(DidState.Answers, hit!!.state); assertEquals("55", hit.hex()); assertEquals("1940", hit.code)
        assertNull(DidSweep.judge(0x1940, fr("644", 0x7F, 0x22, 0x31), "644").hit)
        assertEquals(DidState.Locked, DidSweep.judge(1, fr("644", 0x7F, 0x22, 0x33), "644").hit!!.state)
        assertTrue(DidSweep.judge(1, fr("644", 0x7F, 0x22, 0x11), "644").unsupportedService)
        assertNull(DidSweep.judge(1, fr("645", 0x62, 0x00, 0x01, 1), "644").hit)                  // another module's answer
    }

    // ------------------------------------------------------------------------------------------ timers
    @Test fun zeroToHundred() {
        val runs = ArrayList<PerfRun>()
        val t = PerfTimer { runs.add(it) }
        var time = 0.0
        for (i in 0..5) { t.add(time, 0.0); time += 0.1 }                  // standing
        var v = 0.0
        while (v < 110.0) { time += 0.1; v += 8.0 * 0.1; t.add(time, v) }  // 8 km/h per second
        val r100 = runs.first { it.kind == "0–100 km/h" }
        assertEquals(12.5, r100.seconds, 0.12)
        assertEquals(7.5, runs.first { it.kind == "0–60 km/h" }.seconds, 0.12)
    }

    @Test fun brakeHundredToZero() {
        val runs = ArrayList<PerfRun>()
        val t = PerfTimer { runs.add(it) }
        var time = 0.0
        var v = 120.0
        t.add(time, v)
        while (v > 0.0) { time += 0.1; v = maxOf(0.0, v - 30.0 * 0.1); t.add(time, v) }   // 30 km/h per second
        val b = runs.first { it.kind == "100–0 km/h" }
        assertEquals(100.0 / 30.0, b.seconds, 0.15)
        assertEquals(46.3, b.distanceM, 2.0)
    }

    @Test fun liftingOffIsNotARun() {
        val runs = ArrayList<PerfRun>()
        val t = PerfTimer { runs.add(it) }
        var time = 0.0
        t.add(time, 0.0)
        for (v in listOf(10.0, 30.0, 50.0, 40.0, 20.0, 5.0)) { time += 0.5; t.add(time, v) }
        assertTrue(runs.isEmpty())
    }

    @Test fun lifetimeTotals() {
        val l = Lifetime()
        for (i in 0 until 60) l.add(1.0, 0.0, 800.0, 0.8)      // idling a minute
        for (i in 0 until 60) l.add(1.0, 36.0, 2000.0, 3.6)    // driving a minute
        for (i in 0 until 60) l.add(1.0, 0.0, 0.0, 0.0)        // engine off: not counted
        assertEquals(60.0, l.idleSec, 1e-9); assertEquals(60.0, l.driveSec, 1e-9)
        assertEquals(0.6, l.km, 1e-9); assertEquals(0.5, l.idleShare, 1e-9)
    }

    @Test fun vinMismatch() {
        assertTrue(Inventory.vinMismatch("1G11C5SA8DF123456", "1G11C5SA8DF654321"))
        assertFalse(Inventory.vinMismatch("1G11C5SA8DF123456", "1G11C5SA8DF123456"))
        assertFalse(Inventory.vinMismatch("1G11C5SA8DF123456", "FFFFFFFFFFFFFFFFF"))
        assertFalse(Inventory.vinMismatch("", "1G11C5SA8DF123456"))
    }

    @Test fun protocolNumbers() {
        assertEquals(63, Stn.SW_ISO_11); assertEquals(61, Stn.SW_RAW_11)
        val p = Stn.swProbes()
        assertEquals("241", p.first().req); assertEquals("641", p.first().resp); assertEquals(Bus.Sw, p.first().bus); assertEquals(31, p.size)
    }

    // ------------------------------------------------------------------------------------------ the adapter side, with a pretend STN adapter
    private fun stn(extra: (String) -> String? = { null }): FakeTransport = FakeTransport { raw ->
        val c = raw.trim().uppercase().replace(" ", "")
        extra(c) ?: when {
            c.startsWith("STP63") -> "OK"
            c.startsWith("ATSP6") -> "OK"
            c.startsWith("AT") || c.startsWith("ST") && !c.startsWith("STM") -> "OK"
            c.startsWith("STM") -> "##241 AA BB\r241 AA BC\r3C0 01 02 03 04\r"
            c == "" -> ">"
            else -> "NO DATA"
        }
    }

    @Test fun monitorUsesFiltersAndComesBackToHighSpeed() = runBlocking {
        val tr = stn()
        val c = ElmClient(tr)
        val got = StringBuilder()
        var n = 0
        val err = c.exclusive { monitorSession(Bus.Sw, listOf("241"), false, 0, { n++ >= 8 }) { got.append(it) } }
        assertNull(err)
        assertTrue(tr.sent.contains("STP63"))
        assertTrue(tr.sent.any { it.startsWith("STFAP 241,7FF") })
        assertTrue(tr.sent.contains("STM"))
        assertTrue(CanParse.parse(got.toString(), 0).size >= 6)
        assertEquals("ATSP6", tr.sent.last { it.startsWith("ATSP") })           // always back on the high-speed bus
        assertEquals("ATSH7DF", tr.sent.last { it.startsWith("ATSH") })
    }

    @Test fun plainElmAdapterExplainsNoSingleWire() = runBlocking {
        val tr = FakeTransport { raw -> if (raw.uppercase().startsWith("STP")) "?" else "OK" }
        val c = ElmClient(tr)
        val err = c.exclusive { monitorSession(Bus.Sw, emptyList(), false, 1, { false }) { } }
        assertNotNull(err); assertTrue(err!!.contains("single-wire"))
        assertTrue(tr.sent.contains("ATSP6") || tr.sent.none { it.startsWith("ATSP") && it != "ATSP6" })
    }

    @Test fun sweepOnlySendsServiceTwentyTwoAndFindsHits() = runBlocking {
        val tr = stn { raw ->
            val c = if (raw.length == 7 && raw.startsWith("22")) raw.dropLast(1) else raw          // the sweep adds a response count digit
            when {
                c.startsWith("ATSH") || c.startsWith("ATCRA") || c.startsWith("ATST") -> "OK"
                c == "220102" -> "644 04 62 01 02 AB"
                c == "220105" -> "644 03 7F 22 33"
                c.startsWith("22") -> "644 03 7F 22 31"
                else -> null
            }
        }
        val c = ElmClient(tr)
        val m = FoundModule(ModuleProbe("Test", "244", "644", false), "x")
        val hits = ArrayList<DidHit>()
        val end = c.exclusive { sweepDids(m, 0x0100, 0x010F, { false }, { hits.add(it) }, { }) }
        assertEquals(0x0110, end.next); assertNull(end.note)
        assertEquals(listOf("0102", "0105"), hits.map { it.code })
        assertEquals(DidState.Locked, hits[1].state)
        val data = tr.sent.filter { !it.uppercase().startsWith("AT") && !it.uppercase().startsWith("ST") && it.isNotBlank() }
        assertEquals(16, data.size)
        assertTrue(data.all { it.startsWith("22") })
    }

    @Test fun gmlanFaultListIsReadFromRawFramesWithFormattingOff() = runBlocking {
        var formatting = true
        val tr = FakeTransport { raw ->
            val c = raw.trim().uppercase().replace(" ", "")
            when {
                c == "ATCAF0" -> { formatting = false; "OK" }
                c == "ATCAF1" -> { formatting = true; "OK" }
                c.startsWith("AT") || c.startsWith("ST") -> "OK"
                // the module answers only when asked by hand; its list is raw frames from 541 with no length byte
                c == "03A981FF00000000" && !formatting -> "541 81 44 7A 00 09\r541 81 00 00 00 FF"
                else -> "NO DATA"
            }
        }
        val c = ElmClient(tr)
        val m = FoundModule(ModuleProbe("Steering test", "241", "641", false), "x")
        val scan = c.exclusive { readModuleFaults(m) }
        assertEquals(1, scan.faults.size)
        assertTrue(scan.faults[0].code.startsWith("C047A"))
        assertEquals("active", scan.faults[0].kind)
        assertEquals(1, scan.result.codes)
        assertTrue(tr.sent.none { it.uppercase().startsWith("A9") && it.length < 8 })          // never the old formatted request when the raw one worked
    }

    @Test fun gmlanEmptyListIsZeroCodesNotSilence() = runBlocking {
        var formatting = true
        val tr = FakeTransport { raw ->
            val c = raw.trim().uppercase().replace(" ", "")
            when {
                c == "ATCAF0" -> { formatting = false; "OK" }
                c == "ATCAF1" -> { formatting = true; "OK" }
                c.startsWith("AT") || c.startsWith("ST") -> "OK"
                c == "03A981FF00000000" && !formatting -> "541 81 00 00 00 FF"
                else -> "NO DATA"
            }
        }
        val scan = ElmClient(tr).exclusive { readModuleFaults(FoundModule(ModuleProbe("Body", "241", "641", false), "x")) }
        assertEquals(0, scan.faults.size)
        assertEquals(0, scan.result.codes)
    }

    @Test fun pingReportsWhoAnswers() = runBlocking {
        val alive = ModuleProbe("A", "241", "641", false); val dead = ModuleProbe("B", "243", "643", false)
        var cur = ""
        val tr2 = FakeTransport { raw ->
            val c = raw.trim().uppercase().replace(" ", "")
            if (c.startsWith("ATSH")) cur = c.removePrefix("ATSH")
            when {
                c == "ATRV" -> "12.6V"
                c == "3E" -> if (cur == "241") "641 01 7E" else "NO DATA"
                else -> "OK"
            }
        }
        val (v, res) = ElmClient(tr2).exclusive { pingModules(listOf(alive, dead)) }
        assertEquals(12.6, v!!, 0.01)
        assertEquals(true, res["A"]); assertEquals(false, res["B"])
    }

    @Test fun leverNamesMatchTheLog() { assertEquals("Drive", Gm.leverName(6)); assertEquals("Park", Gm.leverName(1)); assertEquals("Manual", Gm.leverName(8)) }

    @Test fun sweepStopsOnSilence() = runBlocking {
        val tr = stn { c -> if (c.startsWith("22")) "NO DATA" else null }
        val c = ElmClient(tr)
        val end = c.exclusive { sweepDids(FoundModule(ModuleProbe("T", "244", "644", false), "x"), 0, 0x1000, { false }, { }, { }) }
        assertNotNull(end.note); assertEquals(40, end.next)
    }

    @Test fun activeTestAlwaysSendsStop() = runBlocking {
        val tr = stn { c ->
            when {
                c.startsWith("ATSH") || c.startsWith("ATCRA") || c.startsWith("ATST") || c.startsWith("ATFC") -> "OK"
                c == "2F123403" -> "641 04 6F 12 34 03"
                c == "2F123400" -> "641 04 6F 12 34 00"
                else -> null
            }
        }
        val c = ElmClient(tr)
        val d = ActiveTestDef("t", "Fan", "241", "641", "2F123403", "2F123400", 5, "")
        val log = c.exclusive { runActiveTest(d) { true } }                  // cancelled at once
        assertTrue(tr.sent.indexOf("2F123403") < tr.sent.indexOf("2F123400"))
        assertTrue(log.any { it.startsWith("stop 2F123400") })
    }

    @Test fun activeTestRefusedByModuleSendsNoStop() = runBlocking {
        val tr = stn { c -> when { c.startsWith("ATSH") || c.startsWith("ATCRA") || c.startsWith("ATST") || c.startsWith("ATFC") -> "OK"; c == "2F123403" -> "641 03 7F 2F 31"; else -> null } }
        val c = ElmClient(tr)
        val d = ActiveTestDef("t", "Fan", "241", "641", "2F123403", "2F123400", 5, "")
        val log = c.exclusive { runActiveTest(d) { false } }
        assertFalse(tr.sent.contains("2F123400"))
        assertTrue(log.any { it.contains("nothing was changed") })
    }

    @Test fun singleWireModuleSearchUsesPin1AndReturns() = runBlocking {
        val tr = stn { c ->
            when {
                c.startsWith("ATSH") || c.startsWith("ATCRA") || c.startsWith("ATST") || c.startsWith("ATFC") || c.startsWith("STCSWM") -> "OK"
                c == "3E00" -> "OK"
                else -> null
            }
        }
        val c = ElmClient(tr)
        val found = c.exclusive { findModules(Stn.swProbes()) { _, _, _ -> } }
        assertTrue(found.isEmpty())
        assertTrue(tr.sent.contains("STP63"))
        assertTrue(tr.sent.any { it.startsWith("STCSWM 2") })                    // wake-up mode used
        assertEquals("ATSP6", tr.sent.last { it.startsWith("ATSP") })
    }
}

class EctGuardTest {
    @Test fun dropoutFromTheRealDriveIsSuspectAndRecovers() {
        val g = EctGuard()
        var t = 0L
        val seq = listOf(60.0, 59.0, 23.0, 22.0, 21.0, 19.0, 60.0, 60.0)
        val out = seq.map { v -> t += 5000; g.feed(t, v) }
        assertTrue(out[1] is EctGuard.Verdict.Good)
        assertTrue(out[2] is EctGuard.Verdict.Suspect && (out[2] as EctGuard.Verdict.Suspect).first)
        assertEquals(59.0, (out[3] as EctGuard.Verdict.Suspect).shown, 0.01)
        assertTrue(out[6] is EctGuard.Verdict.Recovered)
        assertTrue(out[7] is EctGuard.Verdict.Good)
        assertEquals(1, g.suspectCount)
    }
    @Test fun slowWarmUpIsNeverSuspect() {
        val g = EctGuard(); var t = 0L
        for (v in 20..90 step 2) assertTrue(g.feed(t.also { t += 5000 }, v.toDouble()) is EctGuard.Verdict.Good)
    }
    @Test fun afterALongPauseAnyValueIsAccepted() {
        val g = EctGuard()
        g.feed(0, 70.0)
        assertTrue(g.feed(900_000, 30.0) is EctGuard.Verdict.Good)
    }
}

class IncompleteReplyTest {
    @Test fun halfAnAnswerIsDiscarded() = runBlocking {
        val tr = FakeTransport { "##41 0C 1A" }          // no prompt: adapter had not finished
        val c = ElmClient(tr)
        val r = c.exclusive { raw("010C") }
        assertEquals("", r)
        assertEquals(1, c.incompleteReplies)
    }
}
