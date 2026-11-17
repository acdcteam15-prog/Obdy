package com.example.obdy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.obdy.Conn
import com.example.obdy.ObdyVm
import com.example.obdy.ShiftAnalysis

/** Gearbox: records engine and vehicle speed fast, then finds gear changes and the engagement into Drive and Reverse. */
@Composable
fun GearboxScreen(vm: ObdyVm) {
    val on = vm.conn == Conn.On
    ScreenFrame("Gearbox", { vm.overlay = null }, subtitle = "Hard shifts, forward and reverse") {
        Panel {
            Title("What this can and cannot do")
            Hint("This car's gearbox computer does not share its own data over the standard connection (no gear, clutch or fluid values). Obdy works around that: it records engine speed and vehicle speed many times a second and finds the gear changes from them. The results are indicators to compare between drives and to show a workshop, not a diagnosis.")
        }
        if (vm.gearBusy) {
            Panel {
                Title("Recording... ${vm.gearSecs} s")
                Label(vm.gearMsg, color = Brand.Amber)
                GradientButton("Stop", Brand.AltBrush) { vm.gearStop() }
            }
        } else {
            Panel {
                Title("1. Parked: Drive and Reverse engagement")
                Hint("Warm engine, level ground, foot firmly on the brake. Press Start, then shift P to D, wait 5 seconds, D to P, wait 3 seconds, P to R, wait 5 seconds, R to P. A big dip in engine speed means a heavy engagement.")
                GradientButton("Start the 25 second test", Brand.MainBrush, enabled = on) { vm.gearStart(true) }
            }
            Panel {
                Title("2. Driving: gear changes")
                Hint("Best done with a passenger holding the phone. Press Start, then drive gently through all gears (light throttle) and also with a firmer push. Press Stop after about a minute.")
                GradientButton("Start recording a drive", Brand.MainBrush, enabled = on) { vm.gearStart(false) }
            }
            if (!on) Hint("Connect to the car first.")
        }
        if (!vm.gearBusy && vm.gearSamples.isNotEmpty()) {
            Panel {
                Title("Result: ${vm.gearMode}")
                Hint(vm.gearMsg)
                val sh = vm.gearShifts
                val en = vm.gearEngage
                if (sh.isEmpty() && en.isEmpty()) Label("Nothing clear was found. Try again: for the drive, pass through at least two gears; for the parked test, shift while recording.", color = Brand.Amber, size = 13)
                for (e in en) Label(ShiftAnalysis.engagementLine(e), color = if (e.verdict == "Light") Brand.Green else Brand.Amber, size = 13)
                for (e in sh) Label(ShiftAnalysis.shiftLine(e), color = if (e.verdict == "Normal") Brand.Green else Brand.Amber, size = 13)
                if (sh.isNotEmpty()) {
                    val flares = sh.count { it.verdict.startsWith("Flare") }
                    val slow = sh.count { it.verdict.startsWith("Slow") }
                    Label("${sh.size} gear changes found: $flares flare, $slow slow.", bold = true)
                }
            }
        }
        Panel {
            Title("Gear display skips D (N to M)")
            Hint("What the last log shows: the gearbox computer answered with no codes (Mode 03 and 07: 7EA 02 43 00 and 02 47 00), so it has not stored a fault. A problem that clears after switching the car off and waiting points to the computers holding a wrong state (for example after a remote start), more than a broken part. Things to try, cheapest first: 1) switch off, wait 2 minutes, start with the key instead of the remote; 2) check the battery is above 12.4 V before a remote start; 3) if it comes back often, ask a workshop to check the lever position sensor, the shift cable adjustment and the gearbox computer software update.")
            Hint("Find the real gear value: the car's gearbox computer has GM values that standard OBD cannot show. With the engine running and the foot on the brake, put the lever in a position, then press its button. Obdy asks about 250 read-only values and lists the ones that change between positions. Do Park, Neutral and Drive at least; the value that changes only with the lever is the gear.")
            if (vm.gearSnapBusy != null) {
                Label("Reading in ${vm.gearSnapBusy}: ${vm.gearSnapMsg}", color = Brand.Amber, size = 13)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (pos in listOf("Park", "Reverse", "Neutral")) SoftButton(if (pos in vm.gearSnaps) "$pos ✓" else pos, Modifier.weight(1f), enabled = on) { vm.gearSnapshot(pos) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (pos in listOf("Drive", "Manual")) SoftButton(if (pos in vm.gearSnaps) "$pos ✓" else pos, Modifier.weight(1f), enabled = on) { vm.gearSnapshot(pos) }
                }
                if (!on) Hint("Connect to the car first.")
            }
            if (vm.gearSnaps.isNotEmpty() && vm.gearSnapBusy == null) {
                Label(vm.gearSnaps.entries.joinToString("   ") { (k, v) -> "$k: ${v.count { it.second != null }} answered" }, size = 12)
                val ch = vm.gearChanges
                if (vm.gearSnaps.size < 2) Hint("Now move the lever to another position and press its button.")
                else if (ch.isEmpty()) Label("No value changed between these positions.", color = Brand.Amber, size = 13)
                else {
                    Label("Values that changed (hex bytes):", bold = true, size = 13)
                    for (c in ch) Label(c.line(), size = 12)
                }
                SoftButton("Start over") { vm.gearSnapClear() }
            }
        }
        Panel {
            Title("GM gearbox values (optional)")
            Hint("Asks the car for the gearbox fluid temperature and the current gear using GM's own requests (read only). It may not answer on this car.")
            SoftButton("Try the GM values", enabled = on && !vm.gearBusy) { vm.gearTryExtra() }
            vm.gearExtra?.let { Label(it, size = 13) }
        }
        Panel {
            Title("Share the result")
            Hint("The recording is included automatically when you send or share from Claude Lab.")
            SoftButton("Open Claude Lab") { vm.overlay = null; vm.tab = 5 }
        }
    }
}
