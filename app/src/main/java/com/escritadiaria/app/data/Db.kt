package com.escritadiaria.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rituals")
data class Ritual(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val quick: Boolean,
    val dayIndex: Int,
    val theme: String,
    val cycleWeek: Int,
    val question: String,
    val pageW: Float = 0f,
    val pageH: Float = 0f,
    val b1Ink: String = "",
    val b2Ink: String = "",
    val b3Ink: String = "",
    val b1Text: String = "",
    val b2Text: String = "",
    val b3Text: String = "",
    val actionText: String = "",
    val actionDone: Boolean = false,
    val recognized: Boolean = false,
    val searchText: String = "",
) {
    fun ink(block: String): String = when (block) {
        "b1" -> b1Ink
        "b2" -> b2Ink
        else -> b3Ink
    }

    fun text(block: String): String = when (block) {
        "b1" -> b1Text
        "b2" -> b2Text
        else -> b3Text
    }

    fun withText(block: String, value: String): Ritual = when (block) {
        "b1" -> copy(b1Text = value)
        "b2" -> copy(b2Text = value)
        else -> copy(b3Text = value)
    }.reindexed()

    fun blocks(): List<String> = if (quick) listOf("b1", "b3") else listOf("b1", "b2", "b3")

    fun reindexed(): Ritual = copy(
        searchText = normalizeForSearch(listOf(question, b1Text, b2Text, b3Text, actionText).joinToString("\n")),
    )
}

/** Versão leve (sem os traços) para listas e busca. */
data class RitualSummary(
    val id: Long,
    val epochDay: Long,
    val quick: Boolean,
    val dayIndex: Int,
    val theme: String,
    val question: String,
    val b1Text: String,
    val b2Text: String,
    val b3Text: String,
    val actionText: String,
    val actionDone: Boolean,
    val recognized: Boolean,
    val searchText: String,
)

@Dao
interface RitualDao {
    @Insert
    suspend fun insert(r: Ritual): Long

    @Update
    suspend fun update(r: Ritual)

    @Query("DELETE FROM rituals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM rituals WHERE id = :id")
    suspend fun get(id: Long): Ritual?

    @Query("SELECT * FROM rituals WHERE id = :id")
    fun observe(id: Long): Flow<Ritual?>

    @Query(
        "SELECT id, epochDay, quick, dayIndex, theme, question, b1Text, b2Text, b3Text, actionText, actionDone, " +
            "recognized, searchText FROM rituals WHERE finishedAt IS NOT NULL ORDER BY epochDay DESC, startedAt DESC",
    )
    fun observeSummaries(): Flow<List<RitualSummary>>

    @Query("SELECT * FROM rituals WHERE finishedAt IS NOT NULL AND recognized = 0")
    suspend fun pendingRecognition(): List<Ritual>

    @Query("SELECT * FROM rituals WHERE epochDay = :day AND finishedAt IS NOT NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun finishedOn(day: Long): Ritual?

    @Query("SELECT * FROM rituals WHERE epochDay = :day AND finishedAt IS NOT NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeFinishedOn(day: Long): Flow<Ritual?>

    @Query("SELECT COUNT(*) FROM rituals WHERE epochDay = :day")
    suspend fun countStartedOn(day: Long): Int

    @Query("SELECT * FROM rituals WHERE epochDay BETWEEN :from AND :to AND finishedAt IS NOT NULL ORDER BY epochDay, startedAt")
    fun observeBetween(from: Long, to: Long): Flow<List<Ritual>>

    @Query("SELECT * FROM rituals WHERE epochDay BETWEEN :from AND :to AND finishedAt IS NOT NULL ORDER BY epochDay, startedAt")
    suspend fun between(from: Long, to: Long): List<Ritual>

    @Query("UPDATE rituals SET actionDone = :done WHERE id = :id")
    suspend fun setActionDone(id: Long, done: Boolean)

    /** Remove rituais abandonados no meio (sem conclusão) de dias anteriores. */
    @Query("DELETE FROM rituals WHERE finishedAt IS NULL AND epochDay < :today")
    suspend fun deleteAbandoned(today: Long)
}

@Database(entities = [Ritual::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun rituals(): RitualDao

    companion object {
        @Volatile
        private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "escrita.db")
                .build()
                .also { instance = it }
        }
    }
}
