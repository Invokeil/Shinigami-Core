package com.invokeil.shinigami.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.invokeil.shinigami.core.util.ShiniLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted secret storage for API keys, passwords and tokens.
 *
 * Design (MASTER SPEC §66):
 *  - an AES-256 key lives inside the Android Keystore and never leaves it;
 *  - payloads are sealed with AES/GCM (authenticated encryption) and stored,
 *    base64-encoded, in a private prefs file that is excluded from backups;
 *  - values are looked up by logical [reference] (e.g. `provider:3:apikey`),
 *    so Room rows never contain credential material;
 *  - if the keystore key is destroyed (rare OEM resets) sealed blobs simply
 *    fail to open and are reported as absent — no crash, no plaintext.
 */
@Singleton
class SecretVault @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    @Synchronized
    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun put(reference: String, plainText: String) {
        if (plainText.isEmpty()) {
            remove(reference)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val blob = Base64.getEncoder().encodeToString(iv + sealed)
        prefs.edit().putString(reference, blob).apply()
        ShiniLog.d(TAG, "stored secret $reference (encrypted)")
    }

    fun get(reference: String): String? {
        val blob = prefs.getString(reference, null) ?: return null
        return try {
            val bytes = Base64.getDecoder().decode(blob)
            val spec = GCMParameterSpec(128, bytes, 0, IV_LENGTH)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, obtainKey(), spec)
            String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
        } catch (t: Throwable) {
            // Keystore key invalidated — the blob is unrecoverable by design.
            ShiniLog.w(TAG, "secret $reference unreadable, dropping it")
            prefs.edit().remove(reference).apply()
            null
        }
    }

    fun remove(reference: String) {
        prefs.edit().remove(reference).apply()
    }

    /** Removes every stored secret (used by "clear all data"). */
    @Synchronized
    fun wipeAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val TAG = "SecretVault"
        const val PREFS = "shinigami_vault"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_ALIAS = "shinigami_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}

/** Standard reference builders so call sites stay consistent. */
fun providerApiKeyRef(profileId: Long) = "provider:$profileId:apikey"

fun providerPasswordRef(profileId: Long) = "provider:$profileId:password"

fun providerHeaderSecretRef(profileId: Long, header: String) =
    "provider:$profileId:header:${header.lowercase().trim()}"
