package com.actum.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * What a saved item proposes, read in the background soon after it was saved,
 * so **Find things to do in this** has its cards at once. Only proposals: they
 * are validated when shown and nothing runs without a tap. Goes with its memory.
 */
@Entity(
    tableName = "prepared",
    foreignKeys = [
        ForeignKey(
            entity = Memory::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class Prepared(
    @PrimaryKey val memoryId: String,
    /** The words and the model it was read from ([com.actum.act.Actor.preparedKey]): another key is stale. */
    val key: String,
    /** The actions, as an envelope: `{"actions":[…]}`. */
    val envelope: String,
    /** How many of the actions came from the rules. */
    val byRule: Int,
    /** How long the reading took, for the card's footer. */
    val millis: Long,
    val preparedAt: Long,
)

@Dao
interface PreparedDao {

    @Upsert
    suspend fun upsert(prepared: Prepared)

    @Query("SELECT * FROM prepared WHERE memoryId = :memoryId")
    suspend fun get(memoryId: String): Prepared?

    @Query("SELECT memoryId, `key` FROM prepared")
    suspend fun keys(): List<EmbeddingKey>
}
