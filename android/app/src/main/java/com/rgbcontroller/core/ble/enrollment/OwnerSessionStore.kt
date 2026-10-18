package com.rgbcontroller.core.ble.enrollment

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.rgbcontroller.core.security.Hex
import com.rgbcontroller.core.security.SessionCrypto
import com.rgbcontroller.core.security.SessionKeyPair
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persistent owner identity per firmware public device ID.
 *
 * X25519 private keys are encrypted at rest by a non-exportable AndroidKeyStore
 * AES-GCM key. The full record is bound to the public device ID with GCM AAD.
 * Never silently re-enroll when the keystore or ciphertext becomes unavailable:
 * physical recovery on the ESP32 is required in that situation.
 */
class OwnerSessionStore(context: Context) {
    data class Record(val ownerId: String, val keys: SessionKeyPair, val counter: Int)

    private val preferences = context.applicationContext.getSharedPreferences(
        "compi_ble_owner_sessions_v1", Context.MODE_PRIVATE
    )

    fun load(deviceId: String): Record? {
        checkDeviceId(deviceId)
        val payload = preferences.getString(deviceId, null) ?: return null
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "owner record truncated" }
        val decrypt = Cipher.getInstance(CIPHER)
        decrypt.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
        decrypt.updateAAD(deviceId.toByteArray(Charsets.US_ASCII))
        val fields = decrypt.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
            .toString(Charsets.US_ASCII).split('|')
        require(fields.size == 4) { "owner record damaged" }
        val publicKey = Hex.decode(fields[1], SessionCrypto.PUBLIC_KEY_BYTES)
            ?: error("invalid public owner key")
        val privateKey = Hex.decode(fields[2], SessionCrypto.PRIVATE_KEY_BYTES)
            ?: error("invalid private owner key")
        val counter = fields[3].toIntOrNull()
            ?.takeIf { it >= 0 } ?: error("invalid owner counter")
        require(fields[0].matches(Regex("[A-Za-z0-9_-]{1,32}"))) { "invalid owner ID" }
        return Record(fields[0], SessionKeyPair(publicKey, privateKey), counter)
    }

    /** Synchronous durable counter commit BEFORE sending AUTH prevents counter reuse. */
    fun save(deviceId: String, record: Record) {
        checkDeviceId(deviceId)
        require(record.ownerId.matches(Regex("[A-Za-z0-9_-]{1,32}")))
        require(record.counter >= 0)
        val plain = listOf(
            record.ownerId, Hex.encode(record.keys.publicKey),
            Hex.encode(record.keys.privateKey), record.counter.toString()
        ).joinToString("|").toByteArray(Charsets.US_ASCII)
        val encrypt = Cipher.getInstance(CIPHER)
        encrypt.init(Cipher.ENCRYPT_MODE, getKey())
        encrypt.updateAAD(deviceId.toByteArray(Charsets.US_ASCII))
        val sealed = encrypt.iv + encrypt.doFinal(plain)
        check(preferences.edit().putString(deviceId,
            Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()) {
            "cannot persist BLE owner credential"
        }
    }

    private fun checkDeviceId(id: String) {
        require(id.matches(Regex("[0-9A-F]{16}"))) { "invalid device ID" }
    }

    private fun getKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private companion object {
        const val IV_BYTES = 12
        const val CIPHER = "AES/GCM/NoPadding"
        const val KEY_ALIAS = "compi.bluetooth.owner.aesgcm.v1"
    }
}
