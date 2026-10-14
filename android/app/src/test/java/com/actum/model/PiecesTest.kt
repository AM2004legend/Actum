package com.actum.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A saved item's words split for the rules. What matters most is what is not
 * split off: a sentence the picture wrapped over two lines, or a command with
 * words around it, must reach Gemma whole.
 */
class PiecesTest {

    private fun intents(split: Pieces.Split) = split.ruled.map { it.second.intent }

    @Test
    fun `plain commands on their own lines are read by the rules`() {
        val split = Pieces.split("Set an alarm for 6 am\nBuy eggs, bread and butter\nPaid Rahul 500 for lunch")
        assertEquals(listOf(ActumIntent.ALARM, ActumIntent.TODO, ActumIntent.EXPENSE), intents(split))
        assertEquals("", split.rest)
    }

    @Test
    fun `only the plain sentences are taken, and the rest goes to the model in order`() {
        val split = Pieces.split(
            "I need you to give me the PPT by 9 pm. Set an alarm for 6 am.\n" +
                "Remember the meds at 8 pm, and come to college tomorrow morning.",
        )
        assertEquals(listOf(ActumIntent.ALARM), intents(split))
        assertEquals(
            "I need you to give me the PPT by 9 pm.\nRemember the meds at 8 pm, and come to college tomorrow morning.",
            split.rest,
        )
    }

    @Test
    fun `a sentence wrapped over two lines stays whole`() {
        // OCR breaks where the picture did. Read alone, the second line is a shopping list.
        val split = Pieces.split("I don't need to\nbuy eggs, bread and butter")
        assertTrue(split.ruled.isEmpty())
        assertEquals("I don't need to buy eggs, bread and butter", split.rest)
    }

    @Test
    fun `a list that runs on to the next line stays whole`() {
        // Joined, it is one list, and the cheese is on it.
        val split = Pieces.split("buy eggs, bread and butter\nand some cheese")
        assertEquals(listOf(ActumIntent.TODO), intents(split))
        assertTrue(split.ruled.single().second.fields["items"].toString().contains("cheese"))
    }

    @Test
    fun `bullets and numbers start a new piece and are not part of it`() {
        val split = Pieces.split("things for tomorrow\n- buy milk\n2. set an alarm for 7 am")
        assertEquals(listOf(ActumIntent.TODO, ActumIntent.ALARM), intents(split))
        assertEquals("things for tomorrow", split.rest)
    }

    @Test
    fun `a blank line ends a piece`() {
        val split = Pieces.split("notes from the call\n\nbuy milk")
        assertEquals(listOf(ActumIntent.TODO), intents(split))
        assertEquals("notes from the call", split.rest)
    }

    @Test
    fun `a dot in a time or a title is not the end of a sentence`() {
        assertEquals(listOf("Wake me up at 7 a.m. Tomorrow is the exam."), Pieces.sentences("Wake me up at 7 a.m. Tomorrow is the exam."))
        assertEquals(listOf("Paid Rs. 500 to Dr. Sen"), Pieces.sentences("Paid Rs. 500 to Dr. Sen"))
    }

    @Test
    fun `a question or an exclamation ends a sentence`() {
        assertEquals(listOf("Are you coming?", "Buy milk!"), Pieces.sentences("Are you coming? Buy milk!"))
    }

    @Test
    fun `a diary entry is the whole text, not its first sentence`() {
        val split = Pieces.split("Diary: a long day. Met Riya after class.\nFelt better by night.")
        assertEquals(listOf(ActumIntent.DIARY), intents(split))
        assertEquals("", split.rest)
        assertTrue(split.ruled.single().second.fields["text"].toString().contains("Felt better by night"))
    }

    @Test
    fun `nothing plain means everything goes to the model`() {
        val text = "explain this notice: The society water tank will be cleaned on 18 October."
        val split = Pieces.split(text)
        assertTrue(split.ruled.isEmpty())
        assertEquals(text, split.rest)
    }
}
