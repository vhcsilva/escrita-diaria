package com.escritadiaria.app.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escritadiaria.app.data.Content
import com.escritadiaria.app.data.PasteParser
import com.escritadiaria.app.data.SettingsStore
import kotlinx.coroutines.launch

private data class QTab(val label: String, val title: String, val hint: String, val items: List<Triple<String, String, String>>)

private fun tabs(): List<QTab> {
    val list = mutableListOf<QTab>()
    list += QTab(
        "Bloco 1", "Bloco 1 · Onde estou",
        "Copie as 3 perguntas do bloco “Onde estou” no artigo e toque em Colar.",
        Content.b1Full.mapIndexed { i, d -> Triple("b1_$i", "Pergunta ${i + 1}", d) },
    )
    Content.days.forEachIndexed { day, d ->
        list += QTab(
            d.short, "${d.full} · ${d.theme}",
            "Copie as 5 perguntas de ${d.full.lowercase()} no artigo e toque em Colar. A pergunta 1 vale na semana 1 do ciclo, a 2 na semana 2, e assim por diante.",
            d.defaults.mapIndexed { i, def -> Triple("q_${day}_$i", "Semana ${i + 1}", def) },
        )
    }
    list += QTab(
        "Bloco 3", "Bloco 3 · Direção do dia",
        "Copie os 3 itens do bloco “Direção do dia” no artigo e toque em Colar. O primeiro é a ação do dia.",
        Content.b3Full.mapIndexed { i, d -> Triple("b3_$i", "Item ${i + 1}", d) },
    )
    list += QTab(
        "Ao longo do dia", "Perguntas ao longo do dia",
        "Copie as perguntas dos lembretes ao longo do dia no artigo e toque em Colar. Os horários são ajustados em Ajustes.",
        Content.dayPromptDefaults.mapIndexed { i, d -> Triple("dp_$i", "Pergunta ${i + 1}", d) },
    )
    return list
}

/** Permite trocar o texto de qualquer pergunta, digitando ou colando trechos copiados do artigo. */
@Composable
fun QuestionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember { SettingsStore(context) }
    val settings by store.flow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val allTabs = remember { tabs() }
    var tab by rememberSaveable { mutableIntStateOf(1) }
    val edits = remember { mutableStateMapOf<String, String>() }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenHeader("Editar perguntas", onBack)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            allTabs.forEachIndexed { i, t -> Pill(t.label, selected = tab == i, onClick = { tab = i }) }
        }
        val s = settings ?: return@Column
        val current = allTabs[tab]

        Panel(background = C.AccentSoft, padding = 18.dp, gap = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(current.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                    Text(current.hint, fontSize = 14.sp, color = Color(0xFF3A4252))
                }
                BigButton(
                    "Colar", {
                        val text = clipboard.getText()?.text.orEmpty()
                        val parsed = PasteParser.parse(text, current.items.size)
                        if (parsed.isEmpty()) {
                            Toast.makeText(context, "Nada para colar. Copie as perguntas primeiro.", Toast.LENGTH_SHORT).show()
                        } else {
                            parsed.forEachIndexed { i, q ->
                                val key = current.items[i].first
                                edits[key] = q
                                scope.launch { store.setOverride(key, q) }
                            }
                            val msg = if (parsed.size < current.items.size) {
                                "Encontrei ${parsed.size} de ${current.items.size}. Confira e complete."
                            } else {
                                "${parsed.size} perguntas coladas. Confira abaixo."
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    height = 48.dp, icon = Icons.Filled.ContentPaste,
                )
            }
        }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            current.items.forEach { (key, label, def) ->
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
                            edits[key] = def
                            scope.launch { store.setOverride(key, null) }
                        },
                        enabled = value != def,
                    ) { Text("Restaurar") }
                }
            }
        }
    }
}
