package com.actum.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One thing to buy or do, from a confirmed TODO action. */
@Entity(tableName = "todos")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val listName: String?,
    val done: Boolean = false,
    val createdAt: Long,
)

/** One line of the ledger, from a confirmed EXPENSE action. Rupees, kept in paise so sums are exact. */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val counterparty: String?,
    val category: String,
    /** spent, owed_to_me or i_owe. */
    val direction: String,
    /** The day it was for, as an ISO date: resolved by the parsers, never the model. */
    val date: String,
    val createdAt: Long,
)

/**
 * One diary entry. Its words are never stored as words: [sealed] is what
 * [DiaryVault.seal] made of them, readable only with the diary's password.
 */
@Entity(tableName = "diary")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sealed: ByteArray,
    /** The day it is about, as an ISO date. */
    val date: String,
    val createdAt: Long,
)

/**
 * One confirmed action, as a small JSON record: the intent, its checked
 * fields and what happened. Never a picture, and never a diary entry's words.
 */
@Entity(tableName = "action_log")
data class LogRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val intent: String,
    val json: String,
    val createdAt: Long,
)

/**
 * A day marked in Actum's own calendar: an event or a reminder the person
 * confirmed. A note of what was handed over, not the thing itself, which
 * lives in the calendar or clock app.
 */
@Entity(tableName = "markers")
data class Marker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [EVENT] or [REMINDER]. */
    val kind: String,
    val title: String,
    /** ISO date. */
    val date: String,
    /** ISO time, or null for a whole day. */
    val time: String?,
    val createdAt: Long,
) {
    companion object {
        const val EVENT = "event"
        const val REMINDER = "reminder"
    }
}

@Dao
interface ListsDao {

    @Insert
    suspend fun addMarker(marker: Marker)

    @Query("SELECT * FROM markers ORDER BY date, time")
    fun markers(): Flow<List<Marker>>

    @Query("DELETE FROM markers WHERE id = :id")
    suspend fun deleteMarker(id: Long)


    @Insert
    suspend fun addTodos(items: List<TodoItem>)

    @Query("SELECT * FROM todos ORDER BY done, createdAt DESC, id")
    fun todos(): Flow<List<TodoItem>>

    @Query("UPDATE todos SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteTodo(id: Long)

    @Insert
    suspend fun addExpense(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY date DESC, createdAt DESC")
    fun expenses(): Flow<List<Expense>>

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Long)

    @Insert
    suspend fun addDiary(entry: DiaryEntry)

    @Query("SELECT * FROM diary ORDER BY date DESC, createdAt DESC")
    fun diary(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary")
    suspend fun diaryNow(): List<DiaryEntry>

    @Query("UPDATE diary SET sealed = :sealed WHERE id = :id")
    suspend fun reseal(id: Long, sealed: ByteArray)

    @Query("DELETE FROM diary WHERE id = :id")
    suspend fun deleteDiary(id: Long)

    @Insert
    suspend fun addLog(record: LogRecord)

    /** Records whose JSON holds [words] anywhere; every record when it is empty. */
    @Query("SELECT * FROM action_log WHERE :words = '' OR json LIKE '%' || :words || '%' ORDER BY createdAt DESC LIMIT 200")
    fun log(words: String): Flow<List<LogRecord>>
}
