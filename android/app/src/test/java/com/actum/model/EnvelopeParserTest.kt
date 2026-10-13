package com.actum.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeParserTest {

    private val three = """{"actions":[
        {"intent":"EVENT","confidence":0.9,"fields":{"title":"exam","date_text":"kal","time_text":"10 tay"},"source_span":"kal 10 tay exam"},
        {"intent":"TODO","confidence":0.9,"fields":{"items":["rice"]},"source_span":"rice kinte hobe"},
        {"intent":"EXPENSE","confidence":0.8,"fields":{"amount":500,"counterparty_or_merchant":"Rahul"},"source_span":"Rahul ke 500 dilam"}
    ]}"""

    private fun actions(answer: String) = EnvelopeParser.parse(answer) as Envelope.Actions

    @Test
    fun `a clean envelope is read as it is`() {
        val parsed = actions(three)
        assertFalse(parsed.repaired)
        assertEquals(listOf(ActumIntent.EVENT, ActumIntent.TODO, ActumIntent.EXPENSE), parsed.actions.map { it.intent })
        assertEquals("kal 10 tay exam", parsed.actions[0].sourceSpan)
        assertEquals("\"kal\"", parsed.actions[0].fields["date_text"].toString())
    }

    @Test
    fun `a code fence and words around the JSON are repaired`() {
        val parsed = actions("Here you go:\n```json\n$three\n```\nHope that helps.")
        assertTrue(parsed.repaired)
        assertEquals(3, parsed.actions.size)
    }

    @Test
    fun `trailing commas are repaired`() {
        val parsed = actions("""{"actions":[{"intent":"TODO","confidence":1,"fields":{"items":["rice",],},"source_span":"rice",},]}""")
        assertTrue(parsed.repaired)
        assertEquals(ActumIntent.TODO, parsed.actions.single().intent)
    }

    @Test
    fun `an answer cut off before its brackets closed is repaired`() {
        val parsed = actions("""{"actions":[{"intent":"DIARY","confidence":0.7,"fields":{"text":"a long day with a } in it""")
        assertTrue(parsed.repaired)
        assertEquals("\"a long day with a } in it\"", parsed.actions.single().fields["text"].toString())
    }

    @Test
    fun `a closing bracket too many is left out`() {
        val parsed = actions("""{"actions":[{"intent":"UNSUPPORTED","confidence":0.9,"fields":{"reason":"booking"}}}]}""")
        assertTrue(parsed.repaired)
        assertEquals(ActumIntent.UNSUPPORTED, parsed.actions.single().intent)
    }

    @Test
    fun `a diary's words written bare are its text`() {
        val action = actions("""{"actions":[{"intent":"DIARY","confidence":0.9,"fields":"a good day","source_span":"a good day"}]}""").actions.single()
        assertEquals("\"a good day\"", action.fields["text"].toString())
    }

    @Test
    fun `an action closed along with its fields is reopened for its source span`() {
        val parsed = actions("""{"actions":[{"intent":"ALARM","fields":{"time_text":"6 am"}},"source_span":"wake me up at 6 am"}]}""")
        assertTrue(parsed.repaired)
        val alarm = parsed.actions.single()
        assertEquals(ActumIntent.ALARM, alarm.intent)
        assertEquals("wake me up at 6 am", alarm.sourceSpan)
        assertEquals("\"6 am\"", alarm.fields["time_text"].toString())
    }

    @Test
    fun `fields written beside the fields object are gathered back in`() {
        val action = actions("""{"actions":[{"intent":"EXPLAIN","fields":{"summary":"Tank cleaning."},"key_dates":["18 October"],"what_to_do":"Store water","source_span":"notice"}]}""").actions.single()
        assertEquals(setOf("summary", "key_dates", "what_to_do"), action.fields.keys)
    }

    @Test
    fun `an unknown intent becomes unsupported`() {
        val action = actions("""{"actions":[{"intent":"BOOK_CAB","confidence":0.9,"fields":{"to":"airport"},"source_span":"book me a cab"}]}""").actions.single()
        assertEquals(ActumIntent.UNSUPPORTED, action.intent)
        assertFalse("to" in action.fields)
    }

    @Test
    fun `no more than five actions are kept`() {
        val one = """{"intent":"TODO","confidence":1,"fields":{"items":["x"]},"source_span":"x"}"""
        assertEquals(5, actions("""{"actions":[${List(8) { one }.joinToString(",")}]}""").actions.size)
    }

    @Test
    fun `prose, an empty list and a wrong shape are invalid`() {
        assertTrue(EnvelopeParser.parse("I can't help with that.") is Envelope.Invalid)
        assertTrue(EnvelopeParser.parse("""{"actions":[]}""") is Envelope.Invalid)
        assertTrue(EnvelopeParser.parse("""{"answer":"42"}""") is Envelope.Invalid)
    }
}
