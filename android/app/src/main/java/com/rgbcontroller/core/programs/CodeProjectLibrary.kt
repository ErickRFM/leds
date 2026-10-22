package com.rgbcontroller.core.programs

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.Locale

/** Editable code projects stored locally; never contains a device access key. */
data class CodeProject(
    val name: String,
    val language: ProgramLanguage,
    val source: String,
    val updatedAtMillis: Long
)

/** Strict, versioned, pure-JVM project collection codec for private Android storage. */
object CodeProjectLibrary {
    const val MAX_PROJECTS = 12
    const val MAX_NAME_LENGTH = 40
    const val MAX_STORAGE_BYTES = 600 * 1024
    private const val VERSION = "CODE_PROJECTS_V1"

    private fun validName(name: String): Boolean =
        name.isNotBlank() && name == name.trim() && name.length <= MAX_NAME_LENGTH &&
            name.none { it.isISOControl() || it == '.' || it == '/' || it == '\\' }

    private fun validate(project: CodeProject) {
        require(validName(project.name)) { "Nombre inválido (1–40 caracteres, sin rutas ni controles)." }
        require(project.updatedAtMillis >= 0) { "Fecha inválida." }
        val inspection = ProgramSourcePolicy.inspect(project.source)
        require(inspection.acceptedForEditing) { inspection.explanation }
    }

    fun upsert(existing: List<CodeProject>, project: CodeProject): List<CodeProject> {
        validate(project)
        require(existing.size <= MAX_PROJECTS) { "Biblioteca inválida." }
        val found = existing.indexOfFirst {
            it.name.lowercase(Locale.ROOT) == project.name.lowercase(Locale.ROOT)
        }
        require(found >= 0 || existing.size < MAX_PROJECTS) {
            "Máximo $MAX_PROJECTS proyectos. Elimina alguno para continuar."
        }
        val result = existing.toMutableList()
        if (found >= 0) result[found] = project else result.add(project)
        return result.toList()
    }

    fun remove(existing: List<CodeProject>, name: String): List<CodeProject> =
        existing.filterNot { it.name.equals(name, ignoreCase = true) }

    private fun encodeField(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeField(encoded: String): String {
        val bytes = Base64.getUrlDecoder().decode(encoded)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    fun encode(projects: List<CodeProject>): String {
        require(projects.size <= MAX_PROJECTS) { "Demasiados proyectos." }
        projects.forEach(::validate)
        require(projects.map { it.name.lowercase(Locale.ROOT) }.distinct().size == projects.size) {
            "Hay nombres de proyectos duplicados."
        }
        val encoded = buildString {
            append(VERSION)
            projects.forEach { project ->
                append('\n')
                append(encodeField(project.name))
                append('.')
                append(project.language.name)
                append('.')
                append(project.updatedAtMillis)
                append('.')
                append(encodeField(project.source))
            }
        }
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_STORAGE_BYTES) {
            "La biblioteca supera el límite de almacenamiento."
        }
        return encoded
    }

    /** Reject corruption loudly so the original file is not silently overwritten. */
    fun decode(encoded: String): List<CodeProject> {
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_STORAGE_BYTES) {
            "Archivo de proyectos demasiado grande."
        }
        val lines = encoded.split('\n')
        require(lines.firstOrNull() == VERSION) { "Versión de biblioteca no compatible." }
        require(lines.size <= MAX_PROJECTS + 1) { "Archivo de proyectos demasiado grande." }
        val projects = ArrayList<CodeProject>()
        for (line in lines.drop(1)) {
            val fields = line.split('.')
            require(fields.size == 4) { "Entrada de proyecto dañada." }
            val project = CodeProject(
                name = decodeField(fields[0]),
                language = ProgramLanguage.valueOf(fields[1]),
                updatedAtMillis = fields[2].toLongOrNull() ?: error("Fecha de proyecto inválida."),
                source = decodeField(fields[3])
            )
            projects.add(project)
        }
        // The encoder applies all invariants and enforces uniqueness.
        encode(projects)
        return projects
    }
}
