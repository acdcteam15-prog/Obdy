package com.example.obdy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.obdy.Conn
import com.example.obdy.ObdyVm

/** Claude Lab: send the logs, live data and test results to the AI, or share them as a file, and keep the notes for improving the app. */
@Composable
fun LabScreen(vm: ObdyVm) {
    val ctx = LocalContext.current
    @Composable
    fun Option(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = checked, onCheckedChange = onChange); Spacer(Modifier.width(8.dp)); Label(label, size = 14)
        }
    }
    ScreenFrame("Claude Lab", { vm.overlay = null }) {
        Panel {
            Title("What to ask for")
            Hint("The bundle always contains the car and adapter details (with the VIN), the raw live values and the last minutes of live data, faults, readiness, engine self-tests, module scan notes and the adapter log. Open the Live screen for a minute and read the self-tests and faults first for the best result.")
            Option("Diagnose the car", vm.labDiagnose) { vm.labDiagnose = it }
            Option("Find wrong or missing readings in the app", vm.labBugs) { vm.labBugs = it }
            Option("Ideas for new features and tests", vm.labIdeas) { vm.labIdeas = it }
            Option("What else this car can tell us", vm.labDiscover) { vm.labDiscover = it }
        }
        Panel {
            Title("Discovery probe (optional)")
            Hint("About 30 seconds, read-only: asks which standard values and modules exist and reads each module's identity. It changes nothing in the car. Run it with the ignition on.")
            if (vm.labBusy && vm.labProgress.isNotBlank()) Label(vm.labProgress, color = Brand.Amber, bold = true)
            GradientButton(if (vm.labBusy) "Working..." else "Run the discovery probe", Brand.AltBrush, enabled = !vm.labBusy && vm.conn == Conn.On) { vm.labRunProbe() }
            if (vm.conn != Conn.On) Hint("Connect to the car first.")
            if (vm.labProbe.isNotEmpty()) Label("${vm.labProbe.size} probe lines are ready for the next bundle.", color = Brand.Green, size = 13)
        }
        Panel {
            Title("Send")
            val p = vm.labProvider
            if (p == null) { Hint("Add an AI service (Anthropic Claude) in Settings to send directly."); SoftButton("Open Settings") { vm.overlay = "settings" } }
            else Hint("Goes to: ${p.name} (${p.model}). Nothing is sent until you press the button.")
            GradientButton(if (vm.labBusy) "Waiting for the answer..." else "Send to Claude", Brand.MainBrush, enabled = !vm.labBusy && p != null) { vm.labSend() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Share as a file", Modifier.weight(1f)) { vm.labShareFile() }
                SoftButton("Copy all", Modifier.weight(1f)) { copyText(ctx, vm.labBundle()); vm.say("Copied the whole bundle") }
            }
            vm.labError?.let { Label(it, color = Brand.Red, size = 13) }
        }
        val notes = vm.labNotes
        if (notes != null) Panel {
            Title("Notes for improving the app")
            Hint("Paste this into the chat where the app is built, and the code gets updated.")
            SelectionContainer { Text(notes, color = Brand.TextPrimary) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Share", Modifier.weight(1f)) { vm.labShareNotes() }
                SoftButton("Copy", Modifier.weight(1f)) { copyText(ctx, notes); vm.say("Copied") }
            }
        }
        val reply = vm.labReply
        if (reply != null) Panel {
            Title("Claude's answer")
            SelectionContainer { Text(reply, color = Brand.TextPrimary) }
            SoftButton("Copy the answer") { copyText(ctx, reply); vm.say("Copied") }
        }
    }
}
