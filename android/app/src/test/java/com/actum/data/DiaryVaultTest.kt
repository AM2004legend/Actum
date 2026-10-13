package com.actum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryVaultTest {

    private class MapStore : DiaryVault.Store {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
    }

    private val store = MapStore()

    // Few rounds: the test is of the locking, not of how long a guess takes.
    private fun vault() = DiaryVault(store, iterations = 1_000)

    @Test
    fun `nothing is sealed before a password is set`() {
        assertFalse(vault().hasPassword)
        assertNull(vault().seal("a day"))
    }

    @Test
    fun `an entry written with the diary shut opens only with the password`() {
        vault().setPassword("lakeside".toCharArray())
        // A new vault over the same store: the diary shut, as when a card is confirmed.
        val sealed = vault().seal("felt tired, the walk helped")!!
        assertTrue(DiaryVault.isSealed(sealed))
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("tired"))

        assertNull(vault().unlock("lakesidE".toCharArray()))
        assertEquals("felt tired, the walk helped", vault().unlock("lakeside".toCharArray())!!.open(sealed))
    }

    @Test
    fun `changing the password keeps the entries and shuts out the old one`() {
        vault().setPassword("lakeside".toCharArray())
        val sealed = vault().seal("a good day")!!

        assertFalse(vault().changePassword("wrong".toCharArray(), "harbour".toCharArray()))
        assertNotNull(vault().unlock("lakeside".toCharArray()))

        assertTrue(vault().changePassword("lakeside".toCharArray(), "harbour".toCharArray()))
        assertNull(vault().unlock("lakeside".toCharArray()))
        assertEquals("a good day", vault().unlock("harbour".toCharArray())!!.open(sealed))
    }

    @Test
    fun `an entry changed after it was sealed does not open`() {
        val reader = vault().setPassword("lakeside".toCharArray())
        val sealed = vault().seal("a good day")!!
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()
        assertNull(reader.open(sealed))
        assertNull(reader.open("not an entry".toByteArray()))
    }

    @Test
    fun `the password itself is not kept`() {
        vault().setPassword("lakeside".toCharArray())
        assertFalse(store.values.values.any { it.contains("lakeside") })
    }
}
