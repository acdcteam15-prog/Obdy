package com.example.obdy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.obdy.AiPresets
import com.example.obdy.AiProvider
import com.example.obdy.AiType
import com.example.obdy.ObdyVm
import java.util.UUID

/** The list of AI services (Settings): choose the active one, edit, delete, and test each with one tiny question. */
@Composable
fun ProvidersPanel(vm: ObdyVm) {
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AiProvider?>(null) }
    Panel {
        Title("AI services")
        Hint("Add one or more. The selected one answers the analysis, the questions and the part locations. Press Test to prove the key, the model name and the connection work.")
        if (vm.providers.isEmpty()) Label("None yet. The built-in offline notes still work.", color = Brand.Amber, size = 13)
        for (p in vm.providers) {
            val active = vm.activeProvider?.id == p.id
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.setActiveProvider(p.id) }) {
                    RadioButton(selected = active, onClick = { vm.setActiveProvider(p.id) })
                    Column(Modifier.weight(1f)) {
                        Label(p.name, bold = true)
                        Hint(p.type.label + "  ·  " + p.model.ifBlank { "no model set" })
                    }
                }
                val t = vm.aiTest[p.id]
                if (t != null) Label(t, size = 13, color = if (t.startsWith("Works")) Brand.Green else if (t.startsWith("Testing")) Brand.Amber else Brand.Red)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Test", Modifier.weight(1f)) { vm.testProvider(p) }
                    SoftButton("Edit", Modifier.weight(1f)) { editing = p }
                    SoftButton("Delete", Modifier.weight(1f)) { vm.deleteProvider(p.id) }
                }
            }
        }
        GradientButton("Add an AI service", Brand.MainBrush) { picking = true }
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Which service?") },
            text = {
                Column(Modifier.height(380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (pre in AiPresets.all) {
                        Column(Modifier.fillMaxWidth().clickable {
                            picking = false
                            editing = AiProvider(UUID.randomUUID().toString(), pre.type, pre.name, "", pre.model, pre.baseUrl)
                        }.padding(vertical = 4.dp)) { Label(pre.name, bold = true); Hint(pre.note) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }

    val e = editing
    if (e != null) {
        var name by remember(e.id) { mutableStateOf(e.name) }
        var key by remember(e.id) { mutableStateOf(e.key) }
        var model by remember(e.id) { mutableStateOf(e.model) }
        var base by remember(e.id) { mutableStateOf(e.baseUrl) }
        fun build() = AiProvider(e.id, e.type, name.trim().ifBlank { e.type.label }, key.trim(), model.trim(), base.trim())
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(e.type.label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Name", name, { name = it })
                    Field(if (e.type == AiType.OpenAi) "API key (leave empty for a local server)" else "API key", key, { key = it })
                    Field("Model name", model, { model = it })
                    if (e.type == AiType.OpenAi) Field("Base address", base, { base = it })
                    Hint("Model names change often. If the test says the model is unknown, type a current name from the service's website.")
                    SoftButton("Save and test", Modifier.fillMaxWidth()) { val p = build(); vm.saveProvider(p); vm.testProvider(p); editing = null }
                }
            },
            confirmButton = { TextButton(onClick = { vm.saveProvider(build()); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
