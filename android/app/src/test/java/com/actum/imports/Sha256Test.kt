package com.actum.imports

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class Sha256Test {

    @Test
    fun `computes correct sha256 hash for byte stream`() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val emptyDigest = Sha256.digest(ByteArrayInputStream(ByteArray(0)))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyDigest)

        // SHA-256("actum") = 0561dae12425f7c0c8b634cc711fa7928e6646ce2dfc6ee53ab55ab43eaed47f
        val textBytes = "actum".toByteArray(Charsets.UTF_8)
        val textDigest = Sha256.digest(ByteArrayInputStream(textBytes))
        assertEquals("0561dae12425f7c0c8b634cc711fa7928e6646ce2dfc6ee53ab55ab43eaed47f", textDigest)
    }

    @Test
    fun `copyAndDigest copies identical bytes and returns correct hash`() {
        val inputData = "hello world from actum importer".toByteArray(Charsets.UTF_8)
        val input = ByteArrayInputStream(inputData)
        val output = ByteArrayOutputStream()

        val digest = Sha256.copyAndDigest(input, output)

        val expectedDigest = Sha256.digest(ByteArrayInputStream(inputData))
        assertEquals(expectedDigest, digest)
        assertEquals(inputData.toList(), output.toByteArray().toList())
    }
}
