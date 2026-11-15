package com.example.obdy.ui

import androidx.compose.runtime.Composable
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
