package com.actum.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A request the rules could learn from, kept on the phone only: a sentence the
 * model read as one plain action (it could have been instant), or a rule's card
 * the person corrected (the rule read it wrong). Nothing is learned by itself:
 * a person reads these, writes the rule and its tests, and ships it. The
 * person sees the list in the log and can forget it. Never a diary's words.
 */
@Entity(tableName = "lessons")
data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The person's words, as they asked. */
    val words: String,
    /** [SLOW], [RULE_WRONG] or [RULE_EDITED]. */
    val kind: String,
    /** What it turned out to be. */
    val intent: String,
    val createdAt: Long,
) {
    companion object {
        /** The model read it as one plain action, confirmed as it was: a rule could have, at once. */
        const val SLOW = "slow"

        /** The person asked the model again after a rule's card: the rule read it wrong. */
        const val RULE_WRONG = "rule_wrong"

        /** The person changed a rule's card before confirming it: the rule read part of it wrong. */
        const val RULE_EDITED = "rule_edited"

        /** Enough to learn from; older ones go. */
        const val KEEP = 200
    }
}

@Dao
interface LessonDao {

    @Insert
    suspend fun add(lesson: Lesson)

    @Query("DELETE FROM lessons WHERE id NOT IN (SELECT id FROM lessons ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int = Lesson.KEEP)

    @Query("SELECT * FROM lessons ORDER BY createdAt DESC")
    fun all(): Flow<List<Lesson>>

    @Query("SELECT * FROM lessons ORDER BY createdAt DESC")
    suspend fun list(): List<Lesson>

    @Query("DELETE FROM lessons")
    suspend fun clear()
}
