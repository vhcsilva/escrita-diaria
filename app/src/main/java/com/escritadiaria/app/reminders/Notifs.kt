package com.escritadiaria.app.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.escritadiaria.app.R
import com.escritadiaria.app.data.AppSettings
import com.escritadiaria.app.data.Content
import com.escritadiaria.app.ui.MainActivity
import java.time.LocalDate

object Notifs {
    private const val CH_RITUAL = "ritual"
    private const val CH_QUESTIONS = "perguntas"
    private const val CH_ACTION = "acao"
    const val ID_MORNING = 100
    const val ID_QUESTION = 200
    const val ID_ACTION = 300
    const val EXTRA_START = "start"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RITUAL, "Ritual da manhã", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lembrete para escrever, com o tema do dia"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_QUESTIONS, "Perguntas ao longo do dia", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Perguntas para responder mentalmente"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ACTION, "Ação do dia", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A ação escrita no Bloco 3, fixada até ser feita"
            },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, req: Int, start: String?): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (start != null) i.putExtra(EXTRA_START, start)
        return PendingIntent.getActivity(context, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, n: android.app.Notification) {
        if (canPost(context)) NotificationManagerCompat.from(context).notify(id, n)
    }

    fun morning(context: Context, date: LocalDate, settings: AppSettings) {
        val day = Content.days[Content.dayIndex(date)]
        val week = settings.cycleWeek(date)
        val n = NotificationCompat.Builder(context, CH_RITUAL)
            .setSmallIcon(R.drawable.ic_stat_pen)
            .setContentTitle("${day.full.substringBefore("-")}: ${day.theme}")
            .setContentText("15 minutos no papel antes do celular. Toque para começar.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "15 minutos no papel antes do celular.\nPergunta do dia: ${settings.question(Content.dayIndex(date), week - 1)}",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, 1, "full"))
            .addAction(0, "Começar", openApp(context, 1, "full"))
            .addAction(0, "Versão de 5 min", openApp(context, 2, "quick"))
            .build()
        post(context, ID_MORNING, n)
    }

    fun question(context: Context, text: String) {
        val n = NotificationCompat.Builder(context, CH_QUESTIONS)
            .setSmallIcon(R.drawable.ic_stat_pen)
            .setContentTitle(text)
            .setContentText("Sem escrever. Responda em silêncio e siga.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nSem escrever. Responda em silêncio e siga."))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, 3, null))
            .build()
        post(context, ID_QUESTION, n)
    }

    fun action(context: Context, ritualId: Long, text: String) {
        val done = PendingIntent.getBroadcast(
            context, 4,
            Intent(context, ActionDoneReceiver::class.java).putExtra(ActionDoneReceiver.EXTRA_ID, ritualId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CH_ACTION)
            .setSmallIcon(R.drawable.ic_stat_pen)
            .setContentTitle("Ação de hoje")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context, 5, null))
            .addAction(0, "Feito", done)
            .build()
        post(context, ID_ACTION, n)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
