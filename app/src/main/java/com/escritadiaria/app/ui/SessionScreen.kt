package com.escritadiaria.app.ui

import android.app.Application
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.escritadiaria.app.data.AppDb
import com.escritadiaria.app.data.AppSettings
import com.escritadiaria.app.data.Content
import com.escritadiaria.app.data.InkCodec
import com.escritadiaria.app.data.InkStroke
import com.escritadiaria.app.data.Ritual
import com.escritadiaria.app.data.SettingsStore
import com.escritadiaria.app.reminders.Notifs
import com.escritadiaria.app.reminders.Scheduler
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

class SessionViewModel(private val app: Application, val quick: Boolean) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).rituals()
    val steps: List<String> = if (quick) listOf("b1", "b3") else listOf("b1", "b2", "b3")
    val inks: Map<String, SnapshotStateList<InkStroke>> = steps.associateWith { mutableStateListOf() }

    val today: LocalDate = LocalDate.now()
    val dayIndex = Content.dayIndex(today)

    var settings by mutableStateOf(AppSettings())
        private set
    var stepIndex by mutableIntStateOf(0)
        private set
    var remainingMs by mutableLongStateOf(0L)
        private set
    var running by mutableStateOf(true)
        private set
    var alert by mutableStateOf(false)
        private set
    var eraser by mutableStateOf(false)
    var finishing by mutableStateOf(false)
        private set

    var pageW by mutableFloatStateOf(0f)
    var pageH by mutableFloatStateOf(0f)

    private var endAt = 0L
    private var alertFired = false

    val step: String get() = steps[stepIndex]
    val isLast: Boolean get() = stepIndex == steps.lastIndex
    val question: String get() = settings.question(dayIndex, settings.cycleWeek(today) - 1)

    private val ritualId = viewModelScope.async {
        val s = SettingsStore(app).snapshot()
        settings = s
        Notifs.cancel(app, Notifs.ID_MORNING)
        Scheduler.cancelNag(app)
        dao.insert(
            Ritual(
                epochDay = today.toEpochDay(),
                startedAt = System.currentTimeMillis(),
                quick = quick,
                dayIndex = dayIndex,
                theme = Content.days[dayIndex].theme,
                cycleWeek = s.cycleWeek(today),
                question = if (quick) "" else s.question(dayIndex, s.cycleWeek(today) - 1),
            ),
        )
    }

    init {
        startStep(0)
        viewModelScope.launch {
            while (isActive) {
                delay(200)
                tick()
            }
        }
    }

    fun duration(block: String): Int = Content.duration(block, quick)

    private fun startStep(i: Int) {
        stepIndex = i
        remainingMs = duration(steps[i]) * 1000L
        endAt = SystemClock.elapsedRealtime() + remainingMs
        running = true
        alert = false
        alertFired = false
        eraser = false
    }

    private fun tick() {
        if (!running) return
        remainingMs = endAt - SystemClock.elapsedRealtime()
        if (remainingMs <= 0 && !alertFired) {
            alertFired = true
            alert = true
            signal()
        }
    }

    private fun signal() {
        runCatching {
            RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        }
        runCatching {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                app.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vib?.hasVibrator() == true) {
                vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
            }
        }
    }

    fun togglePause() {
        if (running) {
            remainingMs = endAt - SystemClock.elapsedRealtime()
            running = false
        } else {
            endAt = SystemClock.elapsedRealtime() + remainingMs
            running = true
        }
    }

    fun addMinute() {
        if (running) endAt += 60_000L
        remainingMs += 60_000L
        if (remainingMs > 0) {
            alert = false
            alertFired = false
        }
    }

    fun keepWriting() {
        alert = false
    }

    private suspend fun save(finished: Boolean) {
        val id = ritualId.await()
        val r = dao.get(id) ?: return
        dao.update(
            r.copy(
                pageW = pageW,
                pageH = pageH,
                b1Ink = InkCodec.encode(inks["b1"].orEmpty()),
                b2Ink = InkCodec.encode(inks["b2"].orEmpty()),
                b3Ink = InkCodec.encode(inks["b3"].orEmpty()),
                finishedAt = if (finished) System.currentTimeMillis() else null,
            ),
        )
    }

    fun next(onFinished: (Long) -> Unit) {
        if (isLast) {
            if (finishing) return
            finishing = true
            running = false
            viewModelScope.launch {
                save(finished = true)
                onFinished(ritualId.await())
            }
        } else {
            viewModelScope.launch { save(finished = false) }
            startStep(stepIndex + 1)
        }
    }

    fun discard(onDone: () -> Unit) {
        running = false
        viewModelScope.launch {
            dao.delete(ritualId.await())
            onDone()
        }
    }
}

private fun formatTimer(ms: Long): String {
    val negative = ms < 0
    val secs = if (!negative) (ms + 999) / 1000 else (-ms) / 1000
    val text = "${secs / 60}:${"%02d".format(secs % 60)}"
    return if (negative) "+$text" else text
}

private val blockNames = mapOf("b1" to "Onde estou", "b2" to "Pergunta do dia", "b3" to "Direção do dia")

@Composable
fun SessionScreen(quick: Boolean, onExit: () -> Unit, onFinished: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm: SessionViewModel = viewModel(factory = remember { SimpleFactory { SessionViewModel(app, quick) } })
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var confirmExit by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    BackHandler { confirmExit = true }

    val step = vm.step
    val strokes = vm.inks.getValue(step)

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Barra superior: sair, progresso dos blocos, cronômetro.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CircleIconButton(Icons.Filled.Close, "Sair do ritual", { confirmExit = true })
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.steps.forEachIndexed { i, b ->
                    val total = vm.duration(b) * 1000f
                    val pct = when {
                        i < vm.stepIndex -> 1f
                        i > vm.stepIndex -> 0f
                        else -> ((total - vm.remainingMs) / total).coerceIn(0f, 1f)
                    }
                    Column(Modifier.weight(vm.duration(b).toFloat()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.Track)) {
                            Box(Modifier.fillMaxWidth(pct).fillMaxHeight().background(C.Accent))
                        }
                        Text(
                            "${blockNames[b]} · ${vm.duration(b) / 60} min",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (i <= vm.stepIndex) C.Ink else C.Muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    formatTimer(vm.remainingMs),
                    fontSize = 40.sp, fontWeight = FontWeight.SemiBold,
                    color = if (vm.remainingMs < 0) C.WarnText else C.Ink,
                    textAlign = TextAlign.End, modifier = Modifier.width(140.dp),
                )
                CircleIconButton(
                    if (vm.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (vm.running) "Pausar" else "Continuar",
                    { vm.togglePause() }, dark = true,
                )
                Pill("+1 min", selected = false, onClick = { vm.addMinute() }, height = 48.dp)
            }
        }

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Painel da esquerda: instruções e perguntas do bloco.
            Column(Modifier.width(340.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BlockInfo(vm, step)
                    if (vm.alert) {
                        Column(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(C.WarnBg)
                                .border(1.5.dp, C.WarnBorder, RoundedCornerShape(16.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Tempo do bloco encerrado", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.WarnText)
                            Text("Quer seguir ou terminar o pensamento?", fontSize = 14.sp, color = C.WarnText)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BigButton(if (vm.isLast) "Concluir" else "Próximo bloco", { vm.next(onFinished) }, Modifier.weight(1f), dark = true, height = 44.dp)
                                BigButton("Continuar escrevendo", { vm.keepWriting() }, Modifier.weight(1f), primary = false, height = 44.dp)
                            }
                        }
                    } else if (vm.remainingMs < 0) {
                        Text("Tempo extra. O cronômetro mostra quanto passou.", fontSize = 14.sp, color = C.WarnText, fontWeight = FontWeight.Medium)
                    }
                }
                BigButton(
                    if (vm.isLast) "Concluir ritual" else "Próximo bloco",
                    { vm.next(onFinished) },
                    Modifier.fillMaxWidth(),
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    enabled = !vm.finishing,
                )
            }

            // Página para escrever.
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Caneta", selected = !vm.eraser, onClick = { vm.eraser = false }, dark = false, icon = Icons.Filled.Edit)
                    Pill("Borracha", selected = vm.eraser, onClick = { vm.eraser = true }, dark = false, icon = Icons.Outlined.CleaningServices)
                    CircleIconButton(Icons.AutoMirrored.Filled.Undo, "Desfazer", { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, size = 44.dp)
                    Pill("Limpar página", selected = false, onClick = { if (strokes.isNotEmpty()) confirmClear = true })
                    Spacer(Modifier.weight(1f))
                    Text("Botão da S Pen apaga · a palma é ignorada", fontSize = 13.sp, color = C.Muted)
                }
                val shape = RoundedCornerShape(16.dp)
                Box(Modifier.weight(1f).fillMaxWidth().clip(shape).background(C.Paper).border(1.dp, C.CardBorder, shape)) {
                    key(step) {
                        InkCanvas(
                            strokes = strokes,
                            eraser = vm.eraser,
                            modifier = Modifier.fillMaxSize(),
                            onPageSize = { w, h -> vm.pageW = w; vm.pageH = h },
                        )
                    }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Sair do ritual?") },
            text = { Text("O que você escreveu hoje neste ritual será descartado.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; vm.discard(onExit) }) { Text("Sair e descartar") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Continuar escrevendo") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Limpar a página?") },
            text = { Text("Tudo o que foi escrito neste bloco será apagado.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; strokes.clear() }) { Text("Limpar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun BlockInfo(vm: SessionViewModel, step: String) {
    val minutes = vm.duration(step) / 60
    val (eyebrow, desc, prompts) = when (step) {
        "b1" -> Triple("Bloco 1 · $minutes min", "Relatório de status, não reflexão. Uma linha por pergunta.", if (vm.quick) Content.b1Quick else Content.b1Full)
        "b2" -> Triple("Bloco 2 · $minutes min · ${Content.days[vm.dayIndex].theme}", "Escreva sem parar até o tempo acabar. Sem tentar escrever bonito.", listOf(vm.question))
        else -> Triple("Bloco 3 · $minutes min", "Sem uma ação concreta, o ritual vira só um diário. Se faltar tempo, faça só este bloco.", if (vm.quick) Content.b3Quick else Content.b3Full)
    }
    Eyebrow(eyebrow, C.AccentText)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(blockNames.getValue(step), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
        if (step == "b3") Badge("Inegociável", C.Ink, androidx.compose.ui.graphics.Color.White)
    }
    Text(desc, fontSize = 16.sp, lineHeight = 23.sp, color = C.Muted)
    prompts.forEachIndexed { i, text ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Paper)
                .border(1.dp, C.CardBorder, RoundedCornerShape(14.dp)).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(C.AccentSoft), contentAlignment = Alignment.Center) {
                Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.AccentText)
            }
            Text(text, fontSize = if (step == "b2") 20.sp else 16.sp, lineHeight = if (step == "b2") 27.sp else 22.sp, fontWeight = FontWeight.Medium, color = C.Ink)
        }
    }
    if (step == "b2") {
        Text("Travou? Escreva “não sei” e continue. O que vem depois costuma ser a resposta.", fontSize = 15.sp, lineHeight = 21.sp, color = C.Muted)
    }
}
