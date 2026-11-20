package com.escritadiaria.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escritadiaria.app.data.AppDb
import com.escritadiaria.app.data.AppSettings
import com.escritadiaria.app.data.Content
import com.escritadiaria.app.data.SettingsStore
import com.escritadiaria.app.data.formatMinutes
import com.escritadiaria.app.reminders.Notifs
import com.escritadiaria.app.reminders.Scheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun HomeScreen(onStart: (String) -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val dao = remember { AppDb.get(context).rituals() }
    val today = remember { LocalDate.now() }
    val settings by store.flow.collectAsState(initial = AppSettings())
    val todayRitual by remember { dao.observeFinishedOn(today.toEpochDay()) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val todayIdx = Content.dayIndex(today)
    var previewDay by remember { mutableIntStateOf(todayIdx) }
    var previewWeek by remember { mutableIntStateOf(0) }
    LaunchedEffect(settings.cycleStart) { previewWeek = settings.cycleWeek(today) }

    val day = Content.days[previewDay]
    val week = previewWeek.coerceIn(1, 5)

    Row(
        Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (previewDay == todayIdx) Content.longDate(today) else "Prévia do tema de ${day.full.lowercase()}",
                    fontSize = 16.sp, color = C.Muted, fontWeight = FontWeight.Medium,
                )
                Text("Bom dia. Antes do celular, o papel.", style = TitleStyle.copy(fontSize = 34.sp, lineHeight = 40.sp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Content.days.forEachIndexed { i, d ->
                    Pill(d.short, selected = i == previewDay, onClick = { previewDay = i }, height = 48.dp)
                }
            }
            Panel(Modifier.weight(1f).fillMaxWidth(), padding = 24.dp) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Eyebrow("Tema de ${day.full.lowercase()}", C.AccentText)
                    Text(day.theme, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                    Text(day.sub, fontSize = 17.sp, color = C.Muted)
                    Spacer(Modifier.size(4.dp))
                    Text("Pergunta $week de 5 · muda a cada semana", fontSize = 14.sp, color = C.Muted, fontWeight = FontWeight.Medium)
                    Text(settings.question(previewDay, week - 1), fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium, color = C.Ink)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Semana do ciclo", fontSize = 14.sp, color = C.Muted, modifier = Modifier.padding(end = 6.dp))
                    (1..5).forEach { n ->
                        Pill("$n", selected = n == week, onClick = { previewWeek = n }, dark = false)
                    }
                }
            }
        }

        Column(
            Modifier.width(400.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val done = todayRitual
            if (done != null) {
                Panel(background = C.AccentSoft, padding = 20.dp, gap = 8.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = C.AccentText, modifier = Modifier.size(20.dp))
                        Text("Ritual de hoje concluído", fontWeight = FontWeight.SemiBold, color = C.AccentText, fontSize = 15.sp)
                    }
                    if (done.actionText.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = done.actionDone,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        dao.setActionDone(done.id, checked)
                                        if (checked) Notifs.cancel(context, Notifs.ID_ACTION)
                                        else if (settings.pinAction) Notifs.action(context, done.id, done.actionText)
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = C.Accent),
                            )
                            ScriptText(done.actionText, size = 17.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Surface(
                onClick = { onStart("full") },
                shape = RoundedCornerShape(20.dp),
                color = C.Accent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 28.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (done == null) "Começar ritual" else "Escrever de novo", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("15 min · 3 blocos · à mão", fontSize = 16.sp, color = Color.White)
                }
            }
            Surface(
                onClick = { onStart("quick") },
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.5.dp, C.Border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Versão rápida", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                    Text("5 min · para dias corridos", fontSize = 15.sp, color = C.Muted)
                }
            }
            Panel(padding = 22.dp, gap = 12.dp) {
                Eyebrow("Os 3 blocos")
                BlockLine("1 · Onde estou", "3 min")
                BlockLine("2 · Pergunta do dia", "8 min")
                BlockLine("3 · Direção do dia", "4 min")
            }
            val next = remember(settings) { Scheduler.nextQuestion(settings, LocalDateTime.now()) }
            if (next != null) {
                val (at, idx) = next
                Panel(background = C.AccentSoft, padding = 20.dp, gap = 6.dp) {
                    val whenLabel = if (at.toLocalDate() == today) "" else "amanhã, "
                    Text(
                        "Próximo lembrete · $whenLabel${formatMinutes(at.hour * 60 + at.minute)}",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.AccentText,
                    )
                    Text(settings.dayPrompt(idx), fontSize = 16.sp, color = C.Ink, lineHeight = 22.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Ajustes", { onNavigate("reminders") }, Modifier.weight(1f), primary = false, height = 52.dp, icon = Icons.Filled.Settings)
                BigButton("Semana", { onNavigate("week") }, Modifier.weight(1f), primary = false, height = 52.dp, icon = Icons.Filled.CalendarMonth)
            }
            BigButton("Histórico e busca", { onNavigate("history") }, Modifier.fillMaxWidth(), primary = false, height = 52.dp, icon = Icons.Filled.Search)
        }
    }
}

@Composable
private fun BlockLine(label: String, time: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 16.sp, color = C.Ink, modifier = Modifier.weight(1f))
        Text(time, fontSize = 16.sp, color = C.Muted)
    }
}
