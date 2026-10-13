package com.actum.validate

import com.actum.model.Envelope
import com.actum.model.EnvelopeParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class ValidatorTest {

    private val today = LocalDate.of(2026, 10, 13)
    private val input = "kal 10 tay exam, rice kinte hobe, Rahul ke 500 dilam"

    private fun check(action: String, said: String = input): CheckedAction {
        val envelope = EnvelopeParser.parse("""{"actions":[$action]}""") as Envelope.Actions
        return Validator(today, LocalTime.of(20, 43, 30)).check(envelope.actions.single(), said)
    }

    private fun CheckedAction.field(name: String) = fields.single { it.name == name }

    @Test
    fun `the exam is an event tomorrow, with the hour's half of the day still to ask`() {
        val event = check("""{"intent":"EVENT","confidence":0.9,"fields":{"title":"exam","date_text":"kal","time_text":"10 tay"},"source_span":"kal 10 tay exam"}""")
        assertEquals(Verdict.UNCERTAIN, event.verdict)
        assertEquals(FieldState.VERIFIED, event.field("date_text").state)
        assertEquals(LocalDate.of(2026, 10, 14), event.field("date_text").value)
        assertEquals(FieldState.UNSURE, event.field("time_text").state)
        assertEquals(LocalTime.of(10, 0), event.field("time_text").value)
    }

    @Test
    fun `the rice is a to-do that passes`() {
        val todo = check("""{"intent":"TODO","confidence":0.9,"fields":{"items":["rice"]},"source_span":"rice kinte hobe"}""")
        assertEquals(Verdict.PASS, todo.verdict)
        assertEquals(listOf("rice"), todo.field("items").value)
    }

    @Test
    fun `the 500 is an expense today that passes`() {
        val expense = check("""{"intent":"EXPENSE","confidence":0.8,"fields":{"amount":500,"currency":"INR","counterparty_or_merchant":"Rahul","direction":"spent"},"source_span":"Rahul ke 500 dilam"}""")
        assertEquals(Verdict.PASS, expense.verdict)
        assertEquals(BigDecimal("500"), expense.field("amount").value)
        assertEquals(today, expense.field("date_text").value)
        assertEquals("other", expense.field("category").value)
    }

    @Test
    fun `an amount the person never wrote is unsure`() {
        val expense = check("""{"intent":"EXPENSE","confidence":0.8,"fields":{"amount":"5000"},"source_span":"Rahul ke 500 dilam"}""")
        assertEquals(Verdict.UNCERTAIN, expense.verdict)
        assertEquals(FieldState.UNSURE, expense.field("amount").state)
    }

    @Test
    fun `a date the model converted instead of copying is unsure`() {
        val event = check("""{"intent":"EVENT","confidence":0.9,"fields":{"title":"exam","date_text":"tomorrow","time_text":"10:00 AM"},"source_span":"kal 10 tay exam"}""")
        assertEquals(FieldState.UNSURE, event.field("date_text").state)
        assertEquals(FieldState.UNSURE, event.field("time_text").state)
    }

    @Test
    fun `a date the model left out is offered from the person's words`() {
        val event = check("""{"intent":"EVENT","confidence":0.9,"fields":{"title":"exam"},"source_span":"kal 10 tay exam"}""")
        assertEquals(Verdict.UNCERTAIN, event.verdict)
        assertEquals(LocalDate.of(2026, 10, 14), event.field("date_text").value)
    }

    @Test
    fun `a required field that is nowhere fails`() {
        val event = check("""{"intent":"EVENT","confidence":0.9,"fields":{"date_text":"kal"},"source_span":"kal"}""")
        assertEquals(Verdict.FAIL, event.verdict)
        assertEquals(FieldState.MISSING, event.field("title").state)
        val contact = check("""{"intent":"CONTACT","confidence":0.9,"fields":{"name":"Amit","phone":"12345"},"source_span":"Amit 12345"}""", "Amit 12345")
        assertEquals(Verdict.FAIL, contact.verdict)
    }

    @Test
    fun `a contact's number must be the one shared`() {
        val said = "Amit's new number 98765 43210"
        val right = check("""{"intent":"CONTACT","confidence":0.9,"fields":{"name":"Amit","phone":"9876543210"},"source_span":"$said"}""", said)
        assertEquals(Verdict.PASS, right.verdict)
        assertEquals("+919876543210", right.field("phone").value)
        val wrong = check("""{"intent":"CONTACT","confidence":0.9,"fields":{"name":"Amit","phone":"9876543211"},"source_span":"$said"}""", said)
        assertEquals(Verdict.UNCERTAIN, wrong.verdict)
    }

    @Test
    fun `an alarm in eight minutes is a time eight minutes from now`() {
        val said = "set up a reminder for 8 more minutes I want to sleep set an alarm for that"
        val alarm = check("""{"intent":"ALARM","confidence":0.9,"fields":{"time_text":"8 more minutes"},"source_span":"$said"}""", said)
        assertEquals(Verdict.PASS, alarm.verdict)
        assertEquals(LocalTime.of(20, 52), alarm.field("time_text").value)
    }

    @Test
    fun `unsupported and unknown intents do nothing`() {
        assertEquals(Verdict.FAIL, check("""{"intent":"UNSUPPORTED","confidence":0.9,"fields":{"reason":"booking a cab"},"source_span":"book me a cab"}""", "book me a cab").verdict)
        assertEquals(Verdict.FAIL, check("""{"intent":"BOOK_CAB","confidence":0.9,"fields":{},"source_span":"book me a cab"}""", "book me a cab").verdict)
    }
}
