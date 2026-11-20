package com.escritadiaria.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escritadiaria.app.data.AppDb
import com.escritadiaria.app.data.Content
import com.escritadiaria.app.data.InkCodec
import com.escritadiaria.app.data.Ritual
import com.escritadiaria.app.data.RitualSummary
import com.escritadiaria.app.data.mondayOf
import com.escritadiaria.app.data.normalizeForSearch
import com.escritadiaria.app.export.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val grans = listOf("tudo" to "Tudo", "ano" to "Ano", "mes" to "Mês", "semana" to "Semana", "dia" to "Dia")

private fun periodKey(epochDay: Long, gran: String): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return when (gran) {
        "ano" -> "${d.year}"
        "mes" -> "%d-%02d".format(d.year, d.monthValue)
        "semana" -> mondayOf(d).toString()
        "dia" -> d.toString()
        else -> "all"
    }
}

private fun periodLabel(key: String, gran: String): String {
    val m = Content.monthsShort
    return when (gran) {
        "ano" -> key
        "mes" -> {
            val (y, mo) = key.split("-").map { it.toInt() }
            Content.monthsFull[mo - 1].replaceFirstChar { it.uppercase() } + " $y"
        }
        "semana" -> {
            val a = LocalDate.parse(key)
            val b = a.plusDays(6)
            "Semana de ${a.dayOfMonth} ${m[a.monthValue - 1]} a ${b.dayOfMonth} ${m[b.monthValue - 1]}" +
                if (a.year != LocalDate.now().year) " ${a.year}" else ""
        }
        "dia" -> {
            val d = LocalDate.parse(key)
            "${d.dayOfMonth} ${m[d.monthValue - 1]} ${d.year}"
        }
        else -> ""
    }
}

/** Trecho do texto que contém o termo, com o termo destacado. */
private data class Snippet(val prefix: String, val before: String, val hit: String, val after: String)

private fun snippetFor(r: RitualSummary, query: String): Snippet {
    val main = r.b2Text.ifBlank { r.b1Text }.ifBlank { r.actionText }
    if (query.isBlank()) return Snippet("", main.take(220), "", "")
    val fields = listOf("" to main, "Onde estou: " to r.b1Text, "Pergunta do dia: " to r.b2Text, "Direção: " to r.b3Text, "Ação: " to r.actionText, "Pergunta: " to r.question)
    for ((label, text) in fields) {
        val norm = normalizeForSearch(text)
        val pos = norm.indexOf(query)
        if (pos >= 0 && norm.length == text.length) {
            val from = maxOf(0, pos - 70)
            return Snippet(
                label,
                (if (from > 0) "…" else "") + text.substring(from, pos),
                text.substring(pos, pos + query.length),
                text.substring(pos + query.length).take(160),
            )
        }
    }
    return Snippet("", main.take(220), "", "")
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenPages: (Long) -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDb.get(context).rituals() }
    val all by remember { dao.observeSummaries() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var gran by rememberSaveable { mutableStateOf("mes") }
    var period by rememberSaveable { mutableStateOf(periodKey(LocalDate.now().toEpochDay(), "mes")) }
    var theme by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableLongStateOf(-1L) }

    val periods = remember(all, gran) { all.map { periodKey(it.epochDay, gran) }.distinct() }
    val nq = normalizeForSearch(query.trim())
    val results = remember(all, gran, period, theme, nq) {
        all.filter { r ->
            (gran == "tudo" || periodKey(r.epochDay, gran) == period) &&
                (theme == null || r.theme == theme) &&
                (nq.isEmpty() || r.searchText.contains(nq))
        }
    }
    val shownId = if (results.any { it.id == selectedId }) selectedId else results.firstOrNull()?.id ?: -1L

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("Histórico", onBack) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.width(520.dp),
                placeholder = { Text("Buscar no que você escreveu…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = C.Muted) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Limpar busca") }
                },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(C.Divider).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                grans.forEach { (k, label) ->
                    Surface(
                        onClick = {
                            gran = k
                            period = periodKey(LocalDate.now().toEpochDay(), k).let { cur ->
                                val ps = all.map { periodKey(it.epochDay, k) }.distinct()
                                if (cur in ps || ps.isEmpty()) cur else ps.first()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (gran == k) Color.White else Color.Transparent,
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
            if (gran != "tudo") {
                periods.forEach { p ->
                    Pill(periodLabel(p, gran), selected = p == period, onClick = { period = p }, height = 40.dp, fontSize = 14.sp)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tema", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Muted, modifier = Modifier.padding(end = 4.dp))
            Pill("Todos", selected = theme == null, onClick = { theme = null }, height = 36.dp, fontSize = 13.sp)
            Content.themeNames.forEach { t -> Pill(t, selected = theme == t, onClick = { theme = t }, height = 36.dp, fontSize = 13.sp) }
        }

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val periodText = if (gran == "tudo") "todo o período" else periodLabel(period, gran).lowercase()
                Text(
                    "${results.size} ${if (results.size == 1) "ritual" else "rituais"} · $periodText" +
                        if (nq.isNotEmpty()) " · contendo “${query.trim()}”" else "",
                    fontSize = 14.sp, color = C.Muted, fontWeight = FontWeight.Medium,
                )
                if (results.isEmpty()) {
                    Text(
                        if (all.isEmpty()) "Seus rituais concluídos aparecem aqui." else "Nenhum ritual encontrado com esses filtros.",
                        fontSize = 15.sp, color = C.Muted, modifier = Modifier.padding(top = 32.dp),
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.id }) { r ->
                        ResultRow(r, nq, selected = r.id == shownId) { selectedId = r.id }
                    }
                }
            }
            Panel(Modifier.width(460.dp).fillMaxHeight(), padding = 22.dp, gap = 14.dp) {
                if (shownId > 0) {
                    val detail by remember(shownId) { dao.observe(shownId) }.collectAsState(initial = null)
                    detail?.let { r ->
                        RitualDetail(
                            r,
                            onOpenPages = { onOpenPages(r.id) },
                            onSaveText = { block, text -> scope.launch { dao.update(r.withText(block, text)) } },
                        )
                    }
                } else {
                    Text("Escolha um ritual para ver os detalhes.", fontSize = 15.sp, color = C.Muted)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(r: RitualSummary, nq: String, selected: Boolean, onClick: () -> Unit) {
    val d = LocalDate.ofEpochDay(r.epochDay)
    val sn = snippetFor(r, nq)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, if (selected) C.Accent else C.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${d.dayOfMonth}", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                Text(
                    Content.monthsShort[d.monthValue - 1].uppercase() + if (d.year != LocalDate.now().year) " ${d.year % 100}" else "",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Muted,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${Content.days[r.dayIndex].short} · ${r.theme}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.AccentText)
                    if (r.quick) Badge("5 min", C.WarnBg, C.WarnText) else Badge("15 min", C.AccentSoft, C.AccentText)
                    if (!r.recognized) Badge("texto pendente", C.Divider, C.Muted)
                }
                Text(
                    r.question.ifBlank { "Versão rápida" + if (r.actionText.isNotBlank()) ": ${r.actionText}" else "" },
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink, maxLines = 2,
                )
                if (sn.prefix.isNotEmpty()) Text(sn.prefix, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Muted)
                ScriptText(
                    buildAnnotatedString {
                        append(sn.before)
                        withStyle(SpanStyle(background = C.Highlight)) { append(sn.hit) }
                        append(sn.after)
                    },
                    size = 15.sp, maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun RitualDetail(r: Ritual, onOpenPages: () -> Unit, onSaveText: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<String?>(null) }
    val font = LocalScriptFont.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { PdfExporter.write(context, uri, listOf(r), font.resId, font.scale) }
            Toast.makeText(context, if (ok) "PDF salvo." else "Não foi possível salvar o PDF.", Toast.LENGTH_SHORT).show()
        }
    }
    val date = LocalDate.ofEpochDay(r.epochDay)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow("${r.theme} · ${if (r.quick) "versão rápida" else "ritual completo"}", C.AccentText)
            Text("${Content.longDate(date)} de ${date.year}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            r.blocks().forEach { b ->
                val label = when (b) {
                    "b1" -> "1 · Onde estou"
                    "b2" -> "2 · ${r.question}"
                    else -> "3 · Direção do dia"
                }
                val strokes = remember(r.id, r.ink(b)) { InkCodec.decode(r.ink(b)) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val shape = RoundedCornerShape(8.dp)
                        Box(Modifier.size(96.dp, 68.dp).clip(shape).background(Color(0xFFFBFBF9)).border(1.dp, C.Divider, shape)) {
                            InkPreview(strokes, r.pageW, r.pageH, Modifier.fillMaxSize().padding(2.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (r.text(b).isNotBlank()) {
                                ScriptText(r.text(b), size = 16.sp)
                            } else {
                                Text(if (r.recognized) "(sem texto)" else "Texto ainda não convertido.", fontSize = 14.sp, color = C.Muted)
                            }
                            TextButton(onClick = { editing = b }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text("Corrigir texto", fontSize = 13.sp, color = C.AccentText)
                            }
                        }
                    }
                }
            }
            if (r.actionText.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (r.actionDone) "Ação ✓" else "Ação", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.Muted)
                    ScriptText(r.actionText, size = 16.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Ver páginas originais", onOpenPages, Modifier.weight(1f), dark = true, height = 48.dp)
            BigButton("Exportar PDF", { exportLauncher.launch("escrita-${r.epochDay.let { LocalDate.ofEpochDay(it) }}.pdf") }, Modifier.weight(1f), primary = false, height = 48.dp)
        }
    }

    editing?.let { block ->
        var text by remember(block) { mutableStateOf(r.text(block)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Corrigir texto") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().height(220.dp))
            },
            confirmButton = { TextButton(onClick = { onSaveText(block, text); editing = null }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
        )
    }
}
