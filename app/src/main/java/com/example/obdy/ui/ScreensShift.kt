package com.example.obdy.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.obdy.Conn
import com.example.obdy.ObdyVm
import com.example.obdy.ShiftAnalysis

/** Gearbox: records engine and vehicle speed fast, then finds gear changes and the engagement into Drive and Reverse. */
@Composable
fun GearboxScreen(vm: ObdyVm) {
    val on = vm.conn == Conn.On
    ScreenFrame("Gearbox", { vm.overlay = null }, subtitle = "Hard shifts, forward and reverse") {
        LeverPanel(vm)
        if (vm.gearBusy) {
            Panel {
                Title("Recording... ${vm.gearSecs} s")
                Label(vm.gearMsg, color = Brand.Amber)
                GradientButton("Stop", Brand.AltBrush) { vm.gearStop() }
            }
        } else {
            Panel {
                Title("1. Parked: Drive and Reverse engagement")
                Hint("Warm engine, level ground, foot firmly on the brake.")
                GradientButton("Start the 25 second test", Brand.MainBrush, enabled = on) { vm.gearStart(true) }
            }
            Panel {
                Title("2. Driving: gear changes")
                Hint("Best done with a passenger holding the phone.")
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
            Hint("Gearbox computer stored no codes. Try: switch off 2 min, start with the key; keep battery above 12.4 V before remote start.")
        }
        Panel {
            Title("GM gearbox values (optional)")
            Hint("Asks the car for the gearbox fluid temperature and the current gear using GM's own requests (read only).")
            SoftButton("Try the GM values", enabled = on && !vm.gearBusy) { vm.gearTryExtra() }
            vm.gearExtra?.let { Label(it, size = 13) }
        }
    }
}
