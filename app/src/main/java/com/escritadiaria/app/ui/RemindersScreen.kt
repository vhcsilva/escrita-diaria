package com.escritadiaria.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escritadiaria.app.data.AppSettings
import com.escritadiaria.app.data.Content
import com.escritadiaria.app.data.SettingsStore
import com.escritadiaria.app.data.formatMinutes
import com.escritadiaria.app.ink.Recognition
import com.escritadiaria.app.reminders.Notifs
import com.escritadiaria.app.reminders.Scheduler
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun RemindersScreen(onBack: () -> Unit, onEditQuestions: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val s by store.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()

    fun change(block: suspend () -> Unit) {
        scope.launch {
            block()
            Scheduler.rescheduleAll(context)
        }
    }

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, current / 60, current % 60, true).show()
    }

    var modelReady by remember { mutableStateOf<Boolean?>(null) }
    var downloading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { modelReady = Recognition.isModelReady() }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ScreenHeader("Ajustes", onBack) {
            BigButton("Editar perguntas", onEditQuestions, primary = false, height = 48.dp, icon = Icons.Filled.Edit)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Panel(gap = 14.dp) {
                    SettingRow("Fonte do texto convertido", "Como aparece o que você escreveu à mão depois de convertido.") {}
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ScriptFonts.all.forEachIndexed { i, f ->
                            val selected = s.scriptFont == i
                            Surface(
                                onClick = { change { store.setScriptFont(i) } },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) C.AccentSoft else androidx.compose.ui.graphics.Color.White,
                                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) C.Accent else C.CardBorder),
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ScriptText("Hoje eu começo.", size = 20.sp, font = f, maxLines = 1)
                                    Text(f.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                                    Text(f.description, fontSize = 12.sp, color = C.Muted)
                                }
                            }
                        }
                    }
                }

                Panel(gap = 16.dp) {
                    SettingRow("Ritual da manhã", "Avisa com o tema do dia da semana. Toque num dia para mudar o horário.") {
                        AppSwitch(s.morningEnabled, { v -> change { store.setMorningEnabled(v) } }, "Ritual da manhã")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Content.days.forEachIndexed { i, d ->
                            Surface(
                                onClick = {
                                    pickTime(s.morningTimes[i]) { m ->
                                        change { store.setMorningTimes(s.morningTimes.toMutableList().also { it[i] = m }) }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, C.CardBorder),
                                color = androidx.compose.ui.graphics.Color.White,
                            ) {
                                Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(d.short, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                                    Text(d.theme, fontSize = 12.sp, color = C.Muted, maxLines = 1)
                                    Text(formatMinutes(s.morningTimes[i]), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (s.morningEnabled) C.AccentText else C.Muted)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = C.Divider)
                    Text("Se eu não começar, lembrar de novo em", fontSize = 15.sp, color = C.Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Não lembrar", 10 to "10 min", 20 to "20 min", 30 to "30 min").forEach { (v, label) ->
                            Pill(label, selected = s.nagMinutes == v, onClick = { change { store.setNagMinutes(v) } }, dark = false, height = 40.dp, fontSize = 14.sp)
                        }
                    }
                    HorizontalDivider(color = C.Divider)
                    SettingRow("Fixar a ação do Bloco 3 na notificação", "Fica lá até você tocar em “Feito”.") {
                        AppSwitch(s.pinAction, { v -> change { store.setPinAction(v) } }, "Fixar ação")
                    }
                }

                Panel(gap = 12.dp) {
                    SettingRow("Ciclo de 5 semanas", "Cada semana usa a pergunta de mesmo número de cada tema.") {}
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Estou na semana", fontSize = 15.sp, color = C.Ink, modifier = Modifier.padding(end = 6.dp))
                        val current = s.cycleWeek(LocalDate.now())
                        (1..5).forEach { n -> Pill("$n", selected = n == current, onClick = { change { store.setCycleWeek(n) } }, dark = false) }
                    }
                }

                Panel(gap = 12.dp) {
                    SettingRow(
                        "Leitura da letra (português)",
                        when (modelReady) {
                            null -> "Verificando…"
                            true -> "Pacote instalado. Funciona sem internet."
                            false -> if (downloading) "Baixando… (precisa de internet)" else "Pacote não baixado. É preciso internet uma vez."
                        },
                    ) {
                        if (modelReady == false) {
                            BigButton("Baixar", {
                                downloading = true
                                scope.launch {
                                    modelReady = Recognition.ensureModel()
                                    downloading = false
                                    if (modelReady == true) Recognition.processPending(context)
                                }
                            }, height = 44.dp, enabled = !downloading)
                        }
                    }
                }

                BigButton(
                    "Testar notificação da manhã",
                    { Notifs.morning(context, LocalDate.now(), s) },
                    primary = false, height = 48.dp,
                )
            }

            Panel(Modifier.width(560.dp).fillMaxHeight(), gap = 14.dp) {
                SettingRow("Perguntas ao longo do dia", "Só para responder mentalmente, sem escrever.") {
                    AppSwitch(s.questionsEnabled, { v -> change { store.setQuestionsEnabled(v) } }, "Perguntas ao longo do dia")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quantas por dia", fontSize = 15.sp, color = C.Ink, modifier = Modifier.weight(1f))
                    (3..6).forEach { n -> Pill("$n", selected = s.questionCount == n, onClick = { change { store.setQuestionCount(n) } }, dark = false) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Horários", fontSize = 15.sp, color = C.Ink, modifier = Modifier.weight(1f))
                    Pill("Aleatórios", selected = s.randomTimes, onClick = { change { store.setRandomTimes(true) } }, dark = false, height = 40.dp, fontSize = 14.sp)
                    Pill("Fixos", selected = !s.randomTimes, onClick = { change { store.setRandomTimes(false) } }, dark = false, height = 40.dp, fontSize = 14.sp)
                }
                Text(
                    if (s.randomTimes) "Sorteados todo dia entre 10:00 e 21:30, para pegar você de surpresa." else "Toque num horário para mudar.",
                    fontSize = 13.sp, color = C.Muted,
                )
                HorizontalDivider(color = C.Divider)
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val todaySlots = Scheduler.questionSlots(s, LocalDate.now()).associate { it.second to it.first }
                    (0 until 6).forEach { i ->
                        val active = i < s.questionCount
                        val time = if (s.randomTimes) todaySlots[i]?.let { "hoje ${formatMinutes(it)}" } ?: "—" else formatMinutes(s.fixedTimes[i])
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                onClick = {
                                    if (!s.randomTimes) pickTime(s.fixedTimes[i]) { m ->
                                        change { store.setFixedTimes(s.fixedTimes.toMutableList().also { it[i] = m }) }
                                    }
                                },
                                enabled = !s.randomTimes,
                                shape = RoundedCornerShape(10.dp),
                                color = C.AccentSoft,
                            ) {
                                Text(
                                    time, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (active) C.AccentText else C.Muted,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                            Text(s.dayPrompt(i), fontSize = 15.sp, color = if (active) C.Ink else C.Muted, modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider(color = C.Divider)
                    }
                }
            }
        }
    }
}
