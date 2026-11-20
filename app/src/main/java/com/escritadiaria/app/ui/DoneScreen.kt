package com.escritadiaria.app.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.escritadiaria.app.data.AppDb
import com.escritadiaria.app.data.AppSettings
import com.escritadiaria.app.data.SettingsStore
import com.escritadiaria.app.data.formatMinutes
import com.escritadiaria.app.ink.Recognition
import com.escritadiaria.app.reminders.Notifs
import com.escritadiaria.app.reminders.Scheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class DoneViewModel(private val app: Application, private val id: Long) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).rituals()

    enum class Status { Reading, Ready, NoModel }

    var status by mutableStateOf(Status.Reading)
        private set
    var action by mutableStateOf("")
    var settings by mutableStateOf(AppSettings())
        private set
    var saving by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            settings = SettingsStore(app).snapshot()
            val r = dao.get(id) ?: return@launch
            val updated = Recognition.recognizeRitual(r)
            if (updated != null) {
                val latestAction = dao.get(id)?.actionText.orEmpty()
                dao.update(if (latestAction.isNotBlank()) updated.copy(actionText = latestAction).reindexed() else updated)
                if (action.isBlank()) action = updated.actionText
                status = Status.Ready
            } else {
                status = Status.NoModel
            }
        }
    }

    fun finish(onDone: () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            val r = dao.get(id)
            if (r != null) {
                val text = action.trim()
                dao.update(r.copy(actionText = text).reindexed())
                if (settings.pinAction && text.isNotBlank()) Notifs.action(app, id, text)
            }
            onDone()
        }
    }
}

@Composable
fun DoneScreen(id: Long, onHome: () -> Unit, onWeek: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm: DoneViewModel = viewModel(factory = remember { SimpleFactory { DoneViewModel(app, id) } })

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.width(720.dp).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(C.Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Text("Ritual concluído.", style = TitleStyle.copy(fontSize = 42.sp))
            Text("Agora sim, pode pegar o celular.", fontSize = 18.sp, color = C.Muted)

            Panel(background = C.AccentSoft, padding = 24.dp, gap = 10.dp) {
                Eyebrow("Sua ação de hoje · lida da sua letra", C.AccentText)
                OutlinedTextField(
                    value = vm.action,
                    onValueChange = { vm.action = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = scriptStyle(20.sp, C.Ink, LocalScriptFont.current),
                    placeholder = {
                        Text(if (vm.status == DoneViewModel.Status.Reading) "Lendo sua letra…" else "Escreva aqui a ação de hoje")
                    },
                    singleLine = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                        focusedBorderColor = C.Accent, unfocusedBorderColor = Color(0xFF9DB0F0),
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (vm.status == DoneViewModel.Status.Reading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = C.AccentText)
                    }
                    Text(
                        when (vm.status) {
                            DoneViewModel.Status.Reading -> "Convertendo a escrita em texto, no próprio tablet…"
                            DoneViewModel.Status.Ready -> "Toque para corrigir. Ela fica fixada na notificação até você marcar como feita."
                            DoneViewModel.Status.NoModel -> "O pacote de leitura da letra ainda não foi baixado (precisa de internet uma vez). " +
                                "Digite a ação; as páginas serão convertidas depois."
                        },
                        fontSize = 14.sp, color = Color(0xFF3A4252),
                    )
                }
            }

            val s = vm.settings
            val now = LocalTime.now()
            val upcoming = remember(s) {
                Scheduler.questionSlots(s, LocalDate.now()).filter { it.first > now.hour * 60 + now.minute }.take(3)
            }
            if (upcoming.isNotEmpty()) {
                Panel(padding = 24.dp, gap = 12.dp) {
                    Eyebrow("Perguntas de hoje (sem escrever, só responder)")
                    upcoming.forEach { (m, i) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(formatMinutes(m), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.AccentText, modifier = Modifier.width(56.dp))
                            Text(s.dayPrompt(i), fontSize = 16.sp, color = C.Ink)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Fixar ação e voltar ao início", { vm.finish(onHome) }, dark = true, enabled = !vm.saving)
                BigButton("Ver minha semana", { vm.finish(onWeek) }, primary = false, enabled = !vm.saving)
            }
        }
    }
}
