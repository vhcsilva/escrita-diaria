package com.escritadiaria.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escritadiaria.app.data.Content
import com.escritadiaria.app.data.SettingsStore
import kotlinx.coroutines.launch

/** Permite trocar o texto de qualquer pergunta (por exemplo, pelo texto original do artigo). */
@Composable
fun QuestionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val settings by store.flow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0..6 dias, 7 = ao longo do dia
    val edits = remember { mutableStateMapOf<String, String>() }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenHeader("Editar perguntas", onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Content.days.forEachIndexed { i, d -> Pill(d.short, selected = tab == i, onClick = { tab = i }) }
            Pill("Ao longo do dia", selected = tab == 7, onClick = { tab = 7 })
        }
        val s = settings ?: return@Column
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val items: List<Triple<String, String, String>> = if (tab < 7) {
                Content.days[tab].defaults.mapIndexed { i, def -> Triple("q_${tab}_$i", "Semana ${i + 1}", def) }
            } else {
                Content.dayPromptDefaults.mapIndexed { i, def -> Triple("dp_$i", "Pergunta ${i + 1}", def) }
            }
            if (tab < 7) {
                Text("${Content.days[tab].theme} · ${Content.days[tab].sub}", fontSize = 16.sp, color = C.Muted)
            }
            items.forEach { (key, label, def) ->
                val value = edits[key] ?: s.overrides[key]?.takeIf { it.isNotBlank() } ?: def
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.AccentText, modifier = Modifier.width(110.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { v ->
                            edits[key] = v
                            scope.launch { store.setOverride(key, if (v.isBlank() || v == def) null else v) }
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = C.Ink),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                            focusedBorderColor = C.Accent, unfocusedBorderColor = C.Border,
                        ),
                    )
                    TextButton(
                        onClick = {
                            edits.remove(key)
                            scope.launch { store.setOverride(key, null) }
                        },
                        enabled = value != def,
                    ) { Text("Restaurar") }
                }
            }
        }
    }
}
