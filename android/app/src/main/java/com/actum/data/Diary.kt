package com.actum.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * An AES-256-GCM key that lives in the Android Keystore and cannot be read
 * out of it. Under [DiaryVault] it wraps the diary's private key a second
 * time, so the app's files copied to another phone open nothing even with
 * the password. Alone, it seals the entries written before a password is
 * set; those are sealed again to the vault the moment one is.
 */
class Diary : DiaryVault.Layer {

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(STORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE).apply { init(spec) }.generateKey()
    }

    /** The 12-byte nonce the keystore chose, then the ciphertext and its tag. */
    override fun seal(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(bytes)
    }

    /** Null when [bytes] was not made by this phone's key, or was changed since. */
    override fun open(bytes: ByteArray): ByteArray? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES))
        cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val STORE = "AndroidKeyStore"
        const val ALIAS = "actum-diary"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
    }
}
