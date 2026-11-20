package com.escritadiaria.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val morningEnabled: Boolean = true,
    /** Minutos do dia para o lembrete da manhã, de segunda (0) a domingo (6). */
    val morningTimes: List<Int> = listOf(390, 390, 390, 390, 390, 480, 480),
    val nagMinutes: Int = 20,
    val pinAction: Boolean = true,
    val questionsEnabled: Boolean = true,
    val questionCount: Int = 6,
    val randomTimes: Boolean = false,
    val fixedTimes: List<Int> = Content.defaultFixedTimes,
    val cycleStart: Long = -1,
    val overrides: Map<String, String> = emptyMap(),
) {
    fun question(day: Int, idx: Int): String =
        overrides["q_${day}_$idx"]?.takeIf { it.isNotBlank() } ?: Content.days[day].defaults[idx]

    fun dayPrompt(idx: Int): String =
        overrides["dp_$idx"]?.takeIf { it.isNotBlank() } ?: Content.dayPromptDefaults[idx]

    /** Semana do ciclo de 5 (1..5) para a data. */
    fun cycleWeek(date: LocalDate): Int {
        val monday = mondayOf(date).toEpochDay()
        val start = if (cycleStart < 0) monday else cycleStart
        val weeks = Math.floorDiv(monday - start, 7L)
        return (Math.floorMod(weeks, 5L) + 1).toInt()
    }
}

class SettingsStore(private val context: Context) {
    private object K {
        val morningEnabled = booleanPreferencesKey("morning_enabled")
        val morningTimes = stringPreferencesKey("morning_times")
        val nagMinutes = intPreferencesKey("nag_minutes")
        val pinAction = booleanPreferencesKey("pin_action")
        val questionsEnabled = booleanPreferencesKey("questions_enabled")
        val questionCount = intPreferencesKey("question_count")
        val randomTimes = booleanPreferencesKey("random_times")
        val fixedTimes = stringPreferencesKey("fixed_times")
        val cycleStart = longPreferencesKey("cycle_start")
    }

    private fun parseInts(s: String?, fallback: List<Int>): List<Int> {
        val v = s?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: return fallback
        return if (v.size == fallback.size) v else fallback
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        val overrides = p.asMap()
            .filterKeys { it.name.startsWith("q_") || it.name.startsWith("dp_") }
            .mapNotNull { (k, v) -> (v as? String)?.let { k.name to it } }
            .toMap()
        AppSettings(
            morningEnabled = p[K.morningEnabled] ?: d.morningEnabled,
            morningTimes = parseInts(p[K.morningTimes], d.morningTimes),
            nagMinutes = p[K.nagMinutes] ?: d.nagMinutes,
            pinAction = p[K.pinAction] ?: d.pinAction,
            questionsEnabled = p[K.questionsEnabled] ?: d.questionsEnabled,
            questionCount = p[K.questionCount] ?: d.questionCount,
            randomTimes = p[K.randomTimes] ?: d.randomTimes,
            fixedTimes = parseInts(p[K.fixedTimes], d.fixedTimes),
            cycleStart = p[K.cycleStart] ?: d.cycleStart,
            overrides = overrides,
        )
    }

    suspend fun snapshot(): AppSettings = flow.first()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setMorningEnabled(v: Boolean) = edit { it[K.morningEnabled] = v }
    suspend fun setMorningTimes(v: List<Int>) = edit { it[K.morningTimes] = v.joinToString(",") }
    suspend fun setNagMinutes(v: Int) = edit { it[K.nagMinutes] = v }
    suspend fun setPinAction(v: Boolean) = edit { it[K.pinAction] = v }
    suspend fun setQuestionsEnabled(v: Boolean) = edit { it[K.questionsEnabled] = v }
    suspend fun setQuestionCount(v: Int) = edit { it[K.questionCount] = v }
    suspend fun setRandomTimes(v: Boolean) = edit { it[K.randomTimes] = v }
    suspend fun setFixedTimes(v: List<Int>) = edit { it[K.fixedTimes] = v.joinToString(",") }

    suspend fun ensureCycleStart() = edit {
        if (it[K.cycleStart] == null) it[K.cycleStart] = mondayOf(LocalDate.now()).toEpochDay()
    }

    /** "Estou na semana N do ciclo" — ajusta o início do ciclo para bater com hoje. */
    suspend fun setCycleWeek(n: Int) = edit {
        it[K.cycleStart] = mondayOf(LocalDate.now()).minusWeeks((n - 1).toLong()).toEpochDay()
    }

    suspend fun setOverride(key: String, value: String?) = edit {
        val k = stringPreferencesKey(key)
        if (value == null) it.remove(k) else it[k] = value
    }
}
