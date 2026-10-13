package com.actum.parse

import com.actum.parse.DateParser.Lean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class ParsersTest {

    /** A Tuesday. */
    private val today = LocalDate.of(2026, 10, 13)

    private fun day(n: Int) = LocalDate.of(2026, 10, n)
    private fun date(text: String, lean: Lean = Lean.NONE) = DateParser.parse(text, today, lean)
    private fun time(h: Int, m: Int = 0) = Reading.Sure(LocalTime.of(h, m))

    // Dates

    @Test
    fun `kal with nothing to lean on is tomorrow or yesterday`() {
        assertEquals(listOf(day(14), day(12)), (date("kal") as Reading.Unsure).candidates)
        assertEquals(listOf(day(14), day(12)), (date("কাল") as Reading.Unsure).candidates)
        assertEquals(listOf(day(14), day(12)), (date("कल मिलते हैं") as Reading.Unsure).candidates)
    }

    @Test
    fun `parshu with nothing to lean on is two days either way`() {
        assertEquals(listOf(day(15), day(11)), (date("parshu") as Reading.Unsure).candidates)
        assertEquals(listOf(day(15), day(11)), (date("পরশু") as Reading.Unsure).candidates)
    }

    @Test
    fun `next Friday said on a Tuesday is this week's or the one after`() {
        assertEquals(listOf(day(16), day(23)), (date("next Friday") as Reading.Unsure).candidates)
    }

    @Test
    fun `kal leans with what it is for`() {
        assertEquals(Reading.Sure(day(14)), date("kal 10 tay exam", Lean.FUTURE))
        assertEquals(Reading.Sure(day(12)), date("kal Rahul ke 500 dilam", Lean.PAST))
        assertEquals(Reading.Sure(day(15)), date("parso shaadi hai", Lean.FUTURE))
    }

    @Test
    fun `words that say which way are sure`() {
        assertEquals(Reading.Sure(day(13)), date("aaj"))
        assertEquals(Reading.Sure(day(13)), date("আজকে"))
        assertEquals(Reading.Sure(day(13)), date("आज"))
        assertEquals(Reading.Sure(day(14)), date("tomorrow"))
        assertEquals(Reading.Sure(day(14)), date("আগামীকাল"))
        assertEquals(Reading.Sure(day(12)), date("gotokal"))
        assertEquals(Reading.Sure(day(15)), date("day after tomorrow"))
    }

    @Test
    fun weekdays() {
        assertEquals(Reading.Sure(day(16)), date("Friday"))
        assertEquals(Reading.Sure(day(20)), date("on tuesday"))
        assertEquals(Reading.Sure(day(19)), date("next Monday"))
        assertEquals(Reading.Sure(day(9)), date("last friday"))
    }

    @Test
    fun `written dates are day first`() {
        assertEquals(Reading.Sure(day(15)), date("15/10"))
        assertEquals(Reading.Sure(LocalDate.of(2026, 12, 5)), date("05/12"))
        assertEquals(Reading.Sure(LocalDate.of(2027, 1, 5)), date("5-1-27"))
        assertEquals(Reading.Sure(LocalDate.of(2026, 12, 25)), date("25 Dec"))
        assertEquals(Reading.Sure(LocalDate.of(2026, 11, 2)), date("fees due 2nd November 2026"))
        assertEquals(Reading.Sure(LocalDate.of(2027, 3, 3)), date("March 3rd", Lean.FUTURE))
        assertEquals(Reading.Sure(LocalDate.of(2026, 10, 15)), date("১৫/১০"))
    }

    @Test
    fun `no date is no date`() {
        assertEquals(Reading.None, date("rice kinte hobe"))
        assertEquals(Reading.None, date("31/02"))
        assertEquals(Reading.None, date("Rs 500/-"))
    }

    // Times

    @Test
    fun `an hour with no half of the day is unsure`() {
        assertEquals(listOf(LocalTime.of(10, 0), LocalTime.of(22, 0)), (TimeParser.parse("10 tay") as Reading.Unsure).candidates)
        assertEquals(listOf(LocalTime.of(17, 0), LocalTime.of(5, 0)), (TimeParser.parse("5 baje") as Reading.Unsure).candidates)
    }

    @Test
    fun `the part of the day settles it`() {
        assertEquals(time(10), TimeParser.parse("sokal 10 tay"))
        assertEquals(time(17), TimeParser.parse("5 baje", context = "sham ko 5 baje meeting"))
        assertEquals(time(17, 30), TimeParser.parse("shaam saade 5 baje"))
        assertEquals(time(21), TimeParser.parse("রাত ৯টায়"))
        assertEquals(time(2), TimeParser.parse("raat 2 baje"))
        assertEquals(time(16), TimeParser.parse("বিকেল ৪টা"))
        assertEquals(time(8), TimeParser.parse("सुबह 8 बजे"))
    }

    @Test
    fun `clock times`() {
        assertEquals(time(22, 30), TimeParser.parse("10:30 pm"))
        assertEquals(time(18, 45), TimeParser.parse("18:45"))
        assertEquals(time(7), TimeParser.parse("7am"))
        assertEquals(time(0, 15), TimeParser.parse("12:15 AM"))
        assertEquals(time(12), TimeParser.parse("noon"))
        assertEquals(Reading.None, TimeParser.parse("Rahul ke 500 dilam"))
        assertEquals(Reading.None, TimeParser.parse("25:00"))
    }

    @Test
    fun `a stretch of time is counted from now, up to the whole minute`() {
        val now = LocalTime.of(20, 43, 30)
        fun at(text: String) = TimeParser.parse(text, now = now)
        assertEquals(time(20, 52), at("8 more minutes"))
        assertEquals(time(20, 54), at("in 10 minutes"))
        assertEquals(time(20, 54), at("10 mins from now"))
        assertEquals(time(21, 44), at("in an hour"))
        assertEquals(time(21, 14), at("half an hour"))
        assertEquals(time(22, 14), at("1 hour 30 minutes"))
        assertEquals(time(22, 14), at("in an hour and a half"))
        assertEquals(time(20, 45), at("in 90 seconds"))
        assertEquals(time(0, 44), at("after 4 hours"))
        // A clock time is still a clock time, and a stretch is nothing without a clock to count from.
        assertEquals(time(22, 30), at("10:30 pm"))
        assertEquals(Reading.None, TimeParser.parse("8 more minutes"))
    }

    // Amounts

    private fun amounts(text: String) = AmountParser.find(text).map { it.value.toPlainString() to it.marked }

    @Test
    fun amounts() {
        assertEquals(listOf("500" to false), amounts("Rahul ke 500 dilam"))
        assertEquals(listOf("1500.5" to true), amounts("₹1,500.50 for groceries"))
        assertEquals(listOf("500" to true), amounts("Rs. 500/-"))
        assertEquals(listOf("500" to true), amounts("৫০০ টাকা"))
        assertEquals(listOf("250" to true), amounts("250 rupees auto"))
        assertEquals(listOf("5000" to false), amounts("5k"))
        assertEquals(listOf("200000" to false), amounts("2 lakh"))
        assertEquals(listOf("2" to false), amounts("2 kg rice"))
        assertEquals(emptyList<Pair<String, Boolean>>(), amounts("15/10 at 10:30"))
        assertEquals(BigDecimal("500"), AmountParser.parse("500"))
        assertEquals(null, AmountParser.parse("about that much"))
    }

    // Phones

    @Test
    fun phones() {
        assertEquals(Reading.Sure("+919876543210"), PhoneParser.normalize("+91 98765 43210"))
        assertEquals(Reading.Sure("+919876543210"), PhoneParser.normalize("09876543210"))
        assertEquals(Reading.Sure("+919876543210"), PhoneParser.normalize("98765-43210"))
        assertEquals(Reading.Sure("+919876543210"), PhoneParser.normalize("৯৮৭৬৫৪৩২১০"))
        assertEquals(listOf("+919876543210"), PhoneParser.find("Amit's new number 98765 43210, save it"))
        assertTrue(PhoneParser.normalize("+44 20 7946 0958") is Reading.Unsure)
        assertEquals(Reading.None, PhoneParser.normalize("12345"))
        assertEquals(Reading.None, PhoneParser.normalize("Rahul"))
    }
}
