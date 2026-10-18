package com.rgbcontroller.core.ble.enrollment

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.rgbcontroller.core.security.BouncyCastleSessionCrypto
import com.rgbcontroller.core.security.Hex
import com.rgbcontroller.core.security.SessionKeyPair
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the owner's X25519 identity across BLE reconnects without storing the private
 * key in plain SharedPreferences. AndroidKeyStore AES-GCM authenticates ciphertext
 * and binds it to the device address, so records cannot be swapped between boards.
 *
 * A lost/unavailable Keystore key is NOT silently replaced by a new owner: the board
 * must go through its physical recovery flow. Never back up the ciphertext alone.
 */
internal class BleOwnerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "compi_ble_owner_encrypted_v1", Context.MODE_PRIVATE
    )
    private val crypto = BouncyCastleSessionCrypto()

    private data class Record(
        val ownerId: String, val publicKey: ByteArray, val privateKey: ByteArray,
        val reservedCounter: Int
    )

    fun load(address: String): OwnerEnrollmentClient? {
        val encrypted = prefs.getString(recordKey(address), null) ?: return null
        val record = decrypt(address, encrypted)
        return OwnerEnrollmentClient(
            crypto, record.ownerId, SessionKeyPair(record.publicKey, record.privateKey),
            record.reservedCounter
        )
    }

    /** Persist BEFORE sending PAIR: once the ESP32 accepts it, the key cannot be lost. */
    fun create(address: String): OwnerEnrollmentClient {
        check(!prefs.contains(recordKey(address))) { "Ya existe un propietario local para este ESP32" }
        val keys = crypto.generateKeyPair()
        val id = Hex.encode(crypto.randomBytes(16))
        save(address, Record(id, keys.publicKey, keys.privateKey, 0))
        return load(address) ?: error("No se pudo recuperar la identidad protegida")
    }

    /**
     * Reserve a strictly greater replay counter BEFORE AUTH goes on air.
     * A crash can skip a counter but must never cause an accepted one to be reused.
     */
    fun reserveCounter(address: String, next: Int) {
        val encrypted = prefs.getString(recordKey(address), null)
            ?: error("Identidad BLE no encontrada")
        val old = decrypt(address, encrypted)
        require(next > old.reservedCounter) { "Contador BLE no monotónico" }
        save(address, old.copy(reservedCounter = next))
    }

    private fun save(address: String, record: Record) {
        val clear = listOf(
            record.ownerId, Hex.encode(record.publicKey), Hex.encode(record.privateKey),
            record.reservedCounter.toString()
        ).joinToString("|").toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(address.uppercase().toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + cipher.doFinal(clear)
        check(prefs.edit().putString(recordKey(address),
            Base64.encodeToString(payload, Base64.NO_WRAP)).commit()) {
            "No se pudo guardar la identidad BLE"
        }
    }

    private fun decrypt(address: String, stored: String): Record {
        val payload = Base64.decode(stored, Base64.NO_WRAP)
        require(payload.size > 12 + 16) { "Identidad BLE dañada" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        cipher.updateAAD(address.uppercase().toByteArray(Charsets.UTF_8))
        val fields = cipher.doFinal(payload.copyOfRange(12, payload.size))
            .toString(Charsets.UTF_8).split('|')
        require(fields.size == 4) { "Registro BLE inválido" }
        val pub = Hex.decode(fields[1], 32) ?: error("Clave pública inválida")
        val priv = Hex.decode(fields[2], 32) ?: error("Clave privada inválida")
        val counter = fields[3].toIntOrNull() ?: error("Contador inválido")
        require(counter >= 0 && fields[0].matches(Regex("[a-zA-Z0-9_-]{1,32}")))
        return Record(fields[0], pub, priv, counter)
    }

    private fun recordKey(address: String) = "owner_" + address.uppercase()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return generator.generateKey()
    }

    private companion object { const val ALIAS = "compi_ble_owner_aes_v1" }
}
