package com.escritadiaria.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.escritadiaria.app.data.AppDb
import com.escritadiaria.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        val s = SettingsStore(context).snapshot()
        val dao = AppDb.get(context).rituals()
        val today = LocalDate.now()
        val started = dao.countStartedOn(today.toEpochDay()) > 0
        when (intent.action) {
            Scheduler.ACTION_MORNING -> {
                if (!started) {
                    Notifs.morning(context, today, s)
                    Scheduler.scheduleNag(context, s)
                }
                Scheduler.scheduleMorning(context, s)
            }
            Scheduler.ACTION_NAG -> if (!started) Notifs.morning(context, today, s)
            Scheduler.ACTION_QUESTION -> {
                val idx = intent.getIntExtra(Scheduler.EXTRA_IDX, 0).coerceIn(0, 5)
                Notifs.question(context, s.dayPrompt(idx))
                Scheduler.scheduleQuestion(context, s)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        Scheduler.rescheduleAll(context)
        val s = SettingsStore(context).snapshot()
        val r = AppDb.get(context).rituals().finishedOn(LocalDate.now().toEpochDay())
        if (r != null && s.pinAction && !r.actionDone && r.actionText.isNotBlank()) {
            Notifs.action(context, r.id, r.actionText)
        }
    }
}

class ActionDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id > 0) AppDb.get(context).rituals().setActionDone(id, true)
        Notifs.cancel(context, Notifs.ID_ACTION)
    }

    companion object {
        const val EXTRA_ID = "ritual_id"
    }
}
