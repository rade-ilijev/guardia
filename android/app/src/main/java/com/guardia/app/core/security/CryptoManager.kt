package com.guardia.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device encryption for captured media. Backed by Android Keystore (AES-256-GCM)
 * via Jetpack Security EncryptedFile. Nothing ever leaves the device.
 */
@Singleton
class CryptoManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private fun encryptedFile(file: File): EncryptedFile =
        EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()

    /** Encrypts [bytes] into [subDir] and returns the absolute file path. */
    fun saveEncrypted(bytes: ByteArray, subDir: String): String {
        val dir = File(context.filesDir, subDir).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.enc")
        if (file.exists()) file.delete()
        encryptedFile(file).openFileOutput().use { it.write(bytes) }
        return file.absolutePath
    }

    /** Decrypts a previously saved file. Returns null on any failure. */
    fun readEncrypted(path: String): ByteArray? = runCatching {
        encryptedFile(File(path)).openFileInput().use { it.readBytes() }
    }.getOrNull()

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    // --- Small-string encryption (for credentials stored in DataStore) ---

    private fun stringKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(STRING_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                STRING_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    /** Encrypts a short string; returns base64(iv || ciphertext). Returns the input on failure. */
    fun encryptString(plain: String): String = runCatching {
        val cipher = Cipher.getInstance(STRING_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, stringKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(iv + ct)
    }.getOrDefault(plain)

    /** Decrypts a value from [encryptString]. Returns null if it isn't a valid encrypted blob. */
    fun decryptString(blob: String): String? = runCatching {
        val raw = Base64.getDecoder().decode(blob)
        if (raw.size <= GCM_IV_BYTES) return null
        val cipher = Cipher.getInstance(STRING_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_BITS, raw, 0, GCM_IV_BYTES)
        cipher.init(Cipher.DECRYPT_MODE, stringKey(), spec)
        String(cipher.doFinal(raw, GCM_IV_BYTES, raw.size - GCM_IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val STRING_KEY_ALIAS = "guardia_pref_key"
        const val STRING_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
