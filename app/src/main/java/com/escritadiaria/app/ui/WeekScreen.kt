package com.escritadiaria.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escritadiaria.app.data.AppDb
import com.escritadiaria.app.data.AppSettings
import com.escritadiaria.app.data.Content
import com.escritadiaria.app.data.InkCodec
import com.escritadiaria.app.data.Ritual
import com.escritadiaria.app.data.SettingsStore
import com.escritadiaria.app.data.mondayOf
import com.escritadiaria.app.export.PdfExporter
import com.escritadiaria.app.reminders.Notifs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun WeekScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDb.get(context).rituals() }
    val store = remember { SettingsStore(context) }
    val settings by store.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val monday = mondayOf(today).plusWeeks(offset.toLong())
    val sunday = monday.plusDays(6)
    val rituals by remember(monday) { dao.observeBetween(monday.toEpochDay(), sunday.toEpochDay()) }
        .collectAsState(initial = emptyList())
    val byDay: Map<Long, Ritual> = rituals.groupBy { it.epochDay }.mapValues { it.value.last() }
    val cycle = settings.cycleWeek(monday)

    val font = LocalScriptFont.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { PdfExporter.write(context, uri, rituals, font.resId, font.scale) }
            Toast.makeText(context, if (ok) "PDF salvo." else "Não foi possível salvar o PDF.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Minha semana", onBack) {
            CircleIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Semana anterior", { offset-- })
            Text(
                "${monday.dayOfMonth} ${Content.monthsShort[monday.monthValue - 1]} – ${sunday.dayOfMonth} ${Content.monthsShort[sunday.monthValue - 1]}",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.Ink,
            )
            CircleIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próxima semana", { if (offset < 0) offset++ })
        }

        Panel(padding = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Eyebrow("Ciclo de 5 semanas")
                    Text("Semana $cycle de 5", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                    Text("Pergunta $cycle de cada tema", fontSize = 14.sp, color = C.Muted)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { n ->
                        val color = when {
                            n < cycle -> C.Accent
                            n == cycle -> Color(0xFF9DB0F0)
                            else -> C.Track
                        }
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (0..6).forEach { i ->
                val date = monday.plusDays(i.toLong())
                val r = byDay[date.toEpochDay()]
                val isToday = date == today
                val (status, statusColor) = when {
                    r != null && r.quick -> "Versão de 5 min" to C.WarnText
                    r != null -> "Ritual completo" to C.Green
                    isToday -> "Hoje" to C.AccentText
                    date.isAfter(today) -> "A seguir" to C.Muted
                    else -> "Sem ritual" to C.Muted
                }
                Surface(
                    onClick = { if (r != null) onOpen(r.id) },
                    enabled = r != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, if (isToday) C.Accent else C.CardBorder),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${Content.days[i].short} ${date.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.weight(1f))
                            Text(Content.days[i].theme, fontSize = 12.sp, color = C.Muted, maxLines = 1)
                        }
                        val shape = RoundedCornerShape(10.dp)
                        Box(Modifier.fillMaxWidth().height(110.dp).clip(shape).background(Color(0xFFFBFBF9)).border(1.dp, C.Divider, shape)) {
                            if (r != null) {
                                val block = if (r.quick) "b1" else "b2"
                                val strokes = remember(r.id, r.ink(block)) { InkCodec.decode(r.ink(block)) }
                                InkPreview(strokes, r.pageW, r.pageH, Modifier.fillMaxSize().padding(4.dp))
                            }
                        }
                        Text(status, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Panel(Modifier.weight(1f), gap = 6.dp) {
                Text("Ações do dia", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                val withAction = rituals.filter { it.actionText.isNotBlank() }
                if (withAction.isEmpty()) {
                    Text("As ações escritas no Bloco 3 aparecem aqui.", fontSize = 15.sp, color = C.Muted)
                }
                Column {
                    withAction.forEach { r ->
                        val date = LocalDate.ofEpochDay(r.epochDay)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = r.actionDone,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        dao.setActionDone(r.id, checked)
                                        if (date == today) {
                                            if (checked) Notifs.cancel(context, Notifs.ID_ACTION)
                                            else if (settings.pinAction) Notifs.action(context, r.id, r.actionText)
                                        }
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = C.Accent),
                            )
                            Text(Content.days[r.dayIndex].short, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Muted, modifier = Modifier.width(44.dp))
                            ScriptText(r.actionText, size = 16.sp)
                        }
                    }
                }
            }
            Column(Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(background = C.AccentSoft, padding = 20.dp, gap = 6.dp) {
                    Text("Domingo · Revisão", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.AccentText)
                    Text("Use esta tela no domingo para olhar as páginas da semana antes de escrever.", fontSize = 15.sp, color = C.Ink)
                }
                BigButton(
                    "Exportar semana em PDF",
                    { exportLauncher.launch("escrita-semana-$monday.pdf") },
                    Modifier.fillMaxWidth(), primary = false, height = 52.dp, icon = Icons.Filled.Download,
                    enabled = rituals.isNotEmpty(),
                )
            }
        }
    }
}
