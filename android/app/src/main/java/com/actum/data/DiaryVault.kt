package com.actum.data

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * The diary's lock: a password the person sets, and can change.
 *
 * Writing an entry must not need the password (an entry is confirmed from a
 * card, with the diary shut), and reading one must. So the diary has a key
 * pair. Entries are sealed to the public half, which is kept in the open;
 * the private half is kept encrypted under a key stretched from the
 * password, and nothing on the phone can open an entry without it. Changing
 * the password wraps the same private key again and touches no entry.
 *
 * There is no way back in without the password: nothing stores it, or
 * anything it could be recovered from.
 */
class DiaryVault(
    private val store: Store,
    /** A second wrap by the phone's own keystore, so a copy of the files is useless on another phone. */
    private val device: Layer = Layer.None,
    private val iterations: Int = ITERATIONS,
) {
    /** Where the vault keeps its few values. Strings, so SharedPreferences will do. */
    interface Store {
        fun get(key: String): String?
        fun put(key: String, value: String)
    }

    interface Layer {
        fun seal(bytes: ByteArray): ByteArray
        fun open(bytes: ByteArray): ByteArray?

        object None : Layer {
            override fun seal(bytes: ByteArray) = bytes
            override fun open(bytes: ByteArray): ByteArray? = bytes
        }
    }

    /** The diary unlocked: held only while it is being read, and gone when the tab is left. */
    class Reader internal constructor(private val key: PrivateKey) {
        /** Null when [sealed] is not an entry this key opens, or was changed since. */
        fun open(sealed: ByteArray): String? = try {
            require(isSealed(sealed))
            var at = MAGIC.size
            val wrappedSize = (sealed[at].toInt() and 0xff shl 8) or (sealed[at + 1].toInt() and 0xff)
            at += 2
            val entryKey = rsa(Cipher.DECRYPT_MODE, key).doFinal(sealed, at, wrappedSize)
            at += wrappedSize
            String(aes(Cipher.DECRYPT_MODE, entryKey, sealed.copyOfRange(at, at + NONCE)).doFinal(sealed, at + NONCE, sealed.size - at - NONCE), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private val random = SecureRandom()

    val hasPassword: Boolean get() = store.get(PUBLIC) != null && store.get(PRIVATE) != null

    /** Sets the first password and makes the key pair. The diary is open on return. */
    fun setPassword(password: CharArray): Reader {
        check(!hasPassword) { "a password is already set" }
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, random) }.generateKeyPair()
        wrap(pair.private, password)
        // Last: until the public key is there, the vault counts as having no password.
        store.put(PUBLIC, encode(pair.public.encoded))
        return Reader(pair.private)
    }

    /** The diary opened with [password], or null when it is not the password. */
    fun unlock(password: CharArray): Reader? = unwrap(password)?.let(::Reader)

    /** False, and nothing changed, when [old] is not the password. */
    fun changePassword(old: CharArray, new: CharArray): Boolean {
        val key = unwrap(old) ?: return false
        wrap(key, new)
        return true
    }

    /** One entry, sealed so only the password opens it. Null while no password is set. */
    fun seal(text: String): ByteArray? {
        val public = store.get(PUBLIC)?.let { KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decode(it))) } ?: return null
        // A fresh key for each entry, itself sealed to the diary's public key.
        val entryKey = ByteArray(32).also(random::nextBytes)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val wrapped = rsa(Cipher.ENCRYPT_MODE, public).doFinal(entryKey)
        val words = aes(Cipher.ENCRYPT_MODE, entryKey, nonce).doFinal(text.toByteArray(Charsets.UTF_8))
        return MAGIC + byteArrayOf((wrapped.size shr 8).toByte(), wrapped.size.toByte()) + wrapped + nonce + words
    }

    private fun wrap(key: PrivateKey, password: CharArray) {
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val sealed = aes(Cipher.ENCRYPT_MODE, stretch(password, salt, iterations), nonce).doFinal(key.encoded)
        // One value, written at once: a new salt beside an old wrap would lock the diary for good.
        store.put(PRIVATE, listOf(iterations.toString(), encode(salt), encode(device.seal(nonce + sealed))).joinToString(":"))
    }

    private fun unwrap(password: CharArray): PrivateKey? = try {
        val (rounds, salt, wrapped) = store.get(PRIVATE)!!.split(":")
        val bytes = device.open(decode(wrapped))!!
        // GCM's tag fails on the wrong key, so a wrong password is known without storing anything of the right one.
        val encoded = aes(Cipher.DECRYPT_MODE, stretch(password, decode(salt), rounds.toInt()), bytes.copyOfRange(0, NONCE))
            .doFinal(bytes, NONCE, bytes.size - NONCE)
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(encoded))
    } catch (_: Exception) {
        null
    }

    companion object {
        /** OWASP's 2023 figure for PBKDF2-HMAC-SHA256 is 600,000; this is what a mid-range phone does in about a second. */
        const val ITERATIONS = 310_000
        const val MIN_LENGTH = 6

        private const val PUBLIC = "public"
        private const val PRIVATE = "private"
        private const val NONCE = 12
        private val MAGIC = "ACTUM2".toByteArray(Charsets.US_ASCII)

        /** Whether [sealed] was sealed by a vault, not by the keystore alone as entries were before a password was set. */
        fun isSealed(sealed: ByteArray): Boolean = sealed.size > MAGIC.size + 2 + NONCE && sealed.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

        private fun stretch(password: CharArray, salt: ByteArray, rounds: Int): ByteArray =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password, salt, rounds, 256)).encoded

        private fun aes(mode: Int, key: ByteArray, nonce: ByteArray): Cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)) }

        /** Both digests named: providers disagree on MGF1's default, and a key wrapped by one must open with another. */
        private fun rsa(mode: Int, key: java.security.Key): Cipher =
            Cipher.getInstance("RSA/ECB/OAEPPadding").apply {
                init(mode, key, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
            }

        private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
        private fun decode(text: String) = Base64.getDecoder().decode(text)
    }
}
