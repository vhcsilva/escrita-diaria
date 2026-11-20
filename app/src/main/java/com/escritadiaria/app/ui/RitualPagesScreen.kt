package com.escritadiaria.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escritadiaria.app.data.AppDb
import com.escritadiaria.app.data.Content
import com.escritadiaria.app.data.InkCodec
import java.time.LocalDate

/** As páginas originais de um ritual, em tamanho grande. */
@Composable
fun RitualPagesScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDb.get(context).rituals() }
    val ritual by remember(id) { dao.observe(id) }.collectAsState(initial = null)
    var block by rememberSaveable { mutableStateOf("b1") }
    val r = ritual
    val date = r?.let { LocalDate.ofEpochDay(it.epochDay) }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader(if (date != null) "${Content.longDate(date)} de ${date.year}" else "Ritual", onBack)
        if (r == null) return@Column
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            r.blocks().forEach { b ->
                val label = when (b) {
                    "b1" -> "1 · Onde estou"
                    "b2" -> "2 · Pergunta do dia"
                    else -> "3 · Direção do dia"
                }
                Pill(label, selected = block == b, onClick = { block = b })
            }
        }
        val current = if (block in r.blocks()) block else "b1"
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val shape = RoundedCornerShape(16.dp)
            val strokes = remember(r.id, current, r.ink(current)) { InkCodec.decode(r.ink(current)) }
            Box(Modifier.weight(1f).fillMaxHeight().clip(shape).background(C.Paper).border(1.dp, C.CardBorder, shape)) {
                InkPreview(strokes, r.pageW, r.pageH, Modifier.fillMaxSize())
            }
            Panel(Modifier.width(360.dp).fillMaxHeight(), gap = 10.dp) {
                if (current == "b2") Text(r.question, fontSize = 17.sp, color = C.Ink)
                Eyebrow("Texto reconhecido")
                Text(
                    r.text(current).ifBlank { if (r.recognized) "(sem texto)" else "Ainda não convertido." },
                    fontSize = 16.sp, lineHeight = 23.sp, color = C.Ink,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
