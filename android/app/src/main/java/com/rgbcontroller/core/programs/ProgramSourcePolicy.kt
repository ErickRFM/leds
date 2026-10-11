package com.rgbcontroller.core.programs

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

enum class ProgramLanguage { LAB_SCRIPT, ARDUINO_CPP }

data class SourceInspection(
    val acceptedForEditing: Boolean,
    val byteCount: Int,
    val explanation: String
)

/**
 * Import/edit policy, NOT a compiler and NOT authorization to execute or flash code.
 * Keep this module free from Android dependencies so validation can run on the JVM.
 */
object ProgramSourcePolicy {
    const val MAX_BYTES = 32 * 1024

    fun inferLanguage(fileName: String?, source: String): ProgramLanguage {
        val name = fileName?.trim()?.lowercase().orEmpty()
        if (name.endsWith(".ino") || name.endsWith(".cpp") ||
            name.endsWith(".c") || name.endsWith(".hpp") || name.endsWith(".h")) {
            return ProgramLanguage.ARDUINO_CPP
        }
        val beginning = source.take(4096)
        return if (Regex("""\bvoid\s+(setup|loop)\s*\(""").containsMatchIn(beginning) ||
            Regex("""(?m)^\s*#\s*include\s*[<"]""").containsMatchIn(beginning)) {
            ProgramLanguage.ARDUINO_CPP
        } else ProgramLanguage.LAB_SCRIPT
    }

    fun inspect(source: String): SourceInspection {
        val byteCount = source.toByteArray(Charsets.UTF_8).size
        val issue = when {
            byteCount > MAX_BYTES -> "El archivo supera el límite de 32 KiB."
            source.isBlank() -> "Escribe o importa instrucciones antes de validar."
            source.any { it.code < 32 && it != '\t' && it != '\r' && it != '\n' } ||
                source.any { it == '\u007f' } -> "El texto contiene caracteres de control no permitidos."
            else -> null
        }
        return if (issue != null) SourceInspection(false, byteCount, issue)
        else SourceInspection(true, byteCount,
            "Texto apto para edición. No está compilado ni validado para ejecución.")
    }

    /** Read a user-selected file with a strict byte cap and malformed UTF-8 rejection. */
    fun readUtf8(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        while (true) {
            val count = input.read(chunk)
            if (count < 0) break
            if (count == 0) continue
            require(output.size() + count <= MAX_BYTES) { "El archivo supera el límite de 32 KiB." }
            output.write(chunk, 0, count)
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val result = decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString()
        return result.removePrefix("\uFEFF")
    }
}
