package com.escritadiaria.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.escritadiaria.app.data.AppSettings
import com.escritadiaria.app.data.SettingsStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Agenda os alarmes: lembrete da manhã (com o tema do dia), repetição se o ritual não começar
 * e as perguntas ao longo do dia. Cada alarme, ao tocar, agenda o próximo.
 */
object Scheduler {
    const val ACTION_MORNING = "com.escritadiaria.app.MORNING"
    const val ACTION_NAG = "com.escritadiaria.app.NAG"
    const val ACTION_QUESTION = "com.escritadiaria.app.QUESTION"
    const val EXTRA_IDX = "idx"

    private const val REQ_MORNING = 10
    private const val REQ_NAG = 11
    private const val REQ_QUESTION = 12

    /** Janela dos horários aleatórios: 10:00 a 21:30. */
    private const val RANDOM_START = 10 * 60
    private const val RANDOM_END = 21 * 60 + 30

    private fun pending(context: Context, action: String, req: Int, idx: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context, req,
            Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_IDX, idx),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun setAlarm(context: Context, at: LocalDateTime, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    private fun cancel(context: Context, action: String, req: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, action, req))
    }

    suspend fun rescheduleAll(context: Context) {
        val s = SettingsStore(context).snapshot()
        scheduleMorning(context, s)
        scheduleQuestion(context, s)
    }

    fun nextMorning(s: AppSettings, now: LocalDateTime): LocalDateTime? {
        if (!s.morningEnabled) return null
        for (i in 0..7) {
            val date = now.toLocalDate().plusDays(i.toLong())
            val m = s.morningTimes[date.dayOfWeek.value - 1]
            val t = date.atTime(m / 60, m % 60)
            if (t.isAfter(now)) return t
        }
        return null
    }

    fun scheduleMorning(context: Context, s: AppSettings) {
        val next = nextMorning(s, LocalDateTime.now())
        if (next == null) cancel(context, ACTION_MORNING, REQ_MORNING)
        else setAlarm(context, next, pending(context, ACTION_MORNING, REQ_MORNING))
    }

    fun scheduleNag(context: Context, s: AppSettings) {
        if (s.nagMinutes <= 0) return
        setAlarm(context, LocalDateTime.now().plusMinutes(s.nagMinutes.toLong()), pending(context, ACTION_NAG, REQ_NAG))
    }

    fun cancelNag(context: Context) = cancel(context, ACTION_NAG, REQ_NAG)

    /** Horários (minuto do dia) e índice da pergunta para uma data, em ordem. */
    fun questionSlots(s: AppSettings, date: LocalDate): List<Pair<Int, Int>> {
        if (!s.questionsEnabled) return emptyList()
        val count = s.questionCount.coerceIn(1, 6)
        return if (!s.randomTimes) {
            (0 until count).map { s.fixedTimes[it] to it }.sortedBy { it.first }
        } else {
            // Mesma data → mesmos horários (semente fixa), sem precisar guardar nada.
            val rnd = Random(date.toEpochDay())
            val slot = (RANDOM_END - RANDOM_START) / count
            (0 until count).map { i -> (RANDOM_START + i * slot + rnd.nextInt(slot.coerceAtLeast(1))) to i }
        }
    }

    fun nextQuestion(s: AppSettings, now: LocalDateTime): Pair<LocalDateTime, Int>? {
        for (d in 0..1) {
            val date = now.toLocalDate().plusDays(d.toLong())
            for ((m, i) in questionSlots(s, date)) {
                val t = date.atTime(m / 60, m % 60)
                if (t.isAfter(now)) return t to i
            }
        }
        return null
    }

    fun scheduleQuestion(context: Context, s: AppSettings) {
        val next = nextQuestion(s, LocalDateTime.now())
        if (next == null) cancel(context, ACTION_QUESTION, REQ_QUESTION)
        else setAlarm(context, next.first, pending(context, ACTION_QUESTION, REQ_QUESTION, next.second))
    }
}
