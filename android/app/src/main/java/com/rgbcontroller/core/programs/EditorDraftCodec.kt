package com.rgbcontroller.core.programs

import java.util.Base64
import java.util.zip.CRC32

/** Local unsaved work; intentionally contains NO device credentials or pairing information. */
data class EditorDraftSnapshot(
    val gpio: String,
    val rgb: String,
    val arduino: String,
    val gpioName: String,
    val rgbName: String,
    val arduinoName: String,
    val selected: ProgramLanguage,
    val engine: String
)

object EditorDraftCodec {
    private const val VERSION = "COMPI_EDITOR_DRAFTS_V1"
    const val MAX_BYTES = 160 * 1024
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    private fun field(value: String) = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun text(value: String) = decoder.decode(value).toString(Charsets.UTF_8)
    private fun crc(payload: String): String {
        val checksum = CRC32()
        checksum.update(payload.toByteArray(Charsets.UTF_8))
        return checksum.value.toString(16).padStart(8, '0')
    }
    private fun validate(snapshot: EditorDraftSnapshot) {
        require(snapshot.engine in setOf("GPIO", "RGB")) { "Motor desconocido." }
        for (source in listOf(snapshot.gpio, snapshot.rgb, snapshot.arduino)) {
            require(source.toByteArray(Charsets.UTF_8).size <= ProgramSourcePolicy.MAX_BYTES) {
                "Borrador demasiado grande."
            }
        }
        for (name in listOf(snapshot.gpioName, snapshot.rgbName, snapshot.arduinoName)) {
            require(name.length <= 128 && name.none { it.isISOControl() }) {
                "Nombre de borrador inválido."
            }
        }
    }
    fun encode(snapshot: EditorDraftSnapshot): String {
        validate(snapshot)
        val fields = listOf(
            snapshot.gpio, snapshot.rgb, snapshot.arduino,
            snapshot.gpioName, snapshot.rgbName, snapshot.arduinoName,
            snapshot.selected.name, snapshot.engine
        )
        val payload = (listOf(VERSION) + fields.map(::field)).joinToString("\n")
        val encoded = "$payload\n${crc(payload)}"
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Borradores demasiado grandes."
        }
        return encoded
    }
    fun decode(encoded: String): EditorDraftSnapshot {
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Archivo de borradores demasiado grande."
        }
        val lines = encoded.split('\n')
        require(lines.size == 10 && lines[0] == VERSION) { "Versión de borradores inválida." }
        val payload = lines.dropLast(1).joinToString("\n")
        require(crc(payload).equals(lines.last(), ignoreCase = true)) {
            "Borrador corrupto; no se sobrescribirá."
        }
        val fields = lines.subList(1, 9).map(::text)
        return EditorDraftSnapshot(
            gpio = fields[0], rgb = fields[1], arduino = fields[2],
            gpioName = fields[3], rgbName = fields[4], arduinoName = fields[5],
            selected = ProgramLanguage.valueOf(fields[6]), engine = fields[7]
        ).also(::validate)
    }
}
