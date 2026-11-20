package com.escritadiaria.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escritadiaria.app.data.AppDb
import com.escritadiaria.app.data.Content
import com.escritadiaria.app.data.InkCodec
import com.escritadiaria.app.data.LINE_SPACING
import java.time.LocalDate

/** As páginas de um ritual: a escrita original ou o texto convertido em fonte cursiva, sobre a pauta. */
@Composable
fun RitualPagesScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDb.get(context).rituals() }
    val ritual by remember(id) { dao.observe(id) }.collectAsState(initial = null)
    var block by rememberSaveable { mutableStateOf("b1") }
    var cursive by rememberSaveable { mutableStateOf(true) }
    val r = ritual
    val date = r?.let { LocalDate.ofEpochDay(it.epochDay) }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            Spacer(Modifier.weight(1f))
            Pill("Em cursiva", selected = cursive, onClick = { cursive = true }, dark = false)
            Pill("Escrita original", selected = !cursive, onClick = { cursive = false }, dark = false)
        }
        val current = if (block in r.blocks()) block else "b1"
        if (current == "b2") Text(r.question, fontSize = 18.sp, color = C.Muted)
        val shape = RoundedCornerShape(16.dp)
        Box(Modifier.weight(1f).fillMaxWidth().clip(shape).background(C.Paper).border(1.dp, C.CardBorder, shape)) {
            if (cursive) {
                CursivePage(r.text(current), r.recognized)
            } else {
                val strokes = remember(r.id, current, r.ink(current)) { InkCodec.decode(r.ink(current)) }
                InkPreview(strokes, r.pageW, r.pageH, Modifier.fillMaxSize())
            }
        }
    }
}

/** Texto em cursiva alinhado às linhas da pauta, como se tivesse sido escrito à mão. */
@Composable
private fun CursivePage(text: String, recognized: Boolean) {
    val font = LocalScriptFont.current
    val density = LocalDensity.current
    val lineSp = with(density) { LINE_SPACING.dp.toSp() }
    val ruleColor = C.Rule
    val stepPx = with(density) { LINE_SPACING.dp.toPx() }
    val scroll = rememberScrollState()
    Box(
        Modifier.fillMaxSize().drawBehind {
            // As linhas acompanham a rolagem do texto.
            var y = stepPx - (scroll.value % stepPx)
            while (y < size.height) {
                drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += stepPx
            }
        },
    ) {
        if (text.isBlank()) {
            Text(
                if (recognized) "Nada escrito neste bloco." else "O texto ainda não foi convertido. Abra o app com internet uma vez para baixar o pacote de leitura da letra.",
                fontSize = 16.sp, color = C.Muted, modifier = Modifier.padding(24.dp).width(560.dp),
            )
        } else {
            Text(
                text,
                // Cada linha ocupa exatamente uma linha da pauta; a base da letra fica sobre a linha.
                style = TextStyle(
                    fontFamily = font.family, fontSize = 26.sp * font.scale, lineHeight = lineSp, color = C.Stroke,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Bottom, LineHeightStyle.Trim.None),
                ),
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 28.dp),
            )
        }
    }
}
