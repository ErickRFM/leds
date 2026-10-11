package com.rgbcontroller.core.programs

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.Locale

data class EditorProject(
    val name: String,
    val language: ProgramLanguage,
    val source: String
)

/**
 * Strict, versioned, local-only library for code editor drafts.
 * This does NOT compile, execute, authenticate or persist ESP32 credentials.
 */
object EditorProjects {
    const val MAX_PROJECTS = 8
    const val MAX_NAME_LENGTH = 40
    const val MAX_TOTAL_BYTES = 80 * 1024
    private const val HEADER = "ESP32-PROJECTS-V1"

    private fun validName(value: String) = value.isNotBlank() &&
        value == value.trim() && value.length <= MAX_NAME_LENGTH &&
        value.none { it.isISOControl() || it == '/' || it == '\\' }

    private fun key(value: String) = value.lowercase(Locale.ROOT)

    private fun validated(projects: List<EditorProject>): List<EditorProject> {
        require(projects.size <= MAX_PROJECTS) { "Máximo 8 proyectos guardados." }
        require(projects.all {
            validName(it.name) && ProgramSourcePolicy.inspect(it.source).acceptedForEditing
        }) { "El proyecto contiene un nombre o código no válido." }
        require(projects.map { key(it.name) }.distinct().size == projects.size) {
            "Los nombres de proyecto deben ser únicos."
        }
        return projects
    }

    fun upsert(
        existing: List<EditorProject>, name: String,
        language: ProgramLanguage, source: String
    ): List<EditorProject> {
        val cleaned = name.trim()
        val next = existing.toMutableList()
        val position = next.indexOfFirst { key(it.name) == key(cleaned) }
        if (position >= 0) next[position] = EditorProject(cleaned, language, source)
        else next.add(EditorProject(cleaned, language, source))
        return validated(next)
    }

    fun remove(existing: List<EditorProject>, name: String): List<EditorProject> =
        existing.filterNot { key(it.name) == key(name.trim()) }

    private fun b64(value: String) = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun strictDecode(value: String): String? = try {
        val bytes = Base64.getUrlDecoder().decode(value)
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) {
        null
    }

    fun encode(projects: List<EditorProject>): String {
        validated(projects)
        val text = buildString {
            append(HEADER)
            projects.forEach {
                append('\n')
                append(b64(it.name))
                append('.')
                append(it.language.name)
                append('.')
                append(b64(it.source))
            }
        }
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_TOTAL_BYTES) {
            "La biblioteca local alcanzó 80 KiB."
        }
        return text
    }

    /** Corrupt/unsupported storage returns empty; it is never executed or upgraded silently. */
    fun decode(content: String): List<EditorProject> {
        if (content.isBlank()) return emptyList()
        if (content.toByteArray(Charsets.UTF_8).size > MAX_TOTAL_BYTES) return emptyList()
        val lines = content.split('\n')
        if (lines.firstOrNull() != HEADER || lines.size > MAX_PROJECTS + 1) return emptyList()
        val projects = mutableListOf<EditorProject>()
        for (line in lines.drop(1)) {
            val pieces = line.split('.')
            if (pieces.size != 3) return emptyList()
            val name = strictDecode(pieces[0]) ?: return emptyList()
            val language = runCatching { ProgramLanguage.valueOf(pieces[1]) }.getOrNull()
                ?: return emptyList()
            val source = strictDecode(pieces[2]) ?: return emptyList()
            if (!validName(name) || !ProgramSourcePolicy.inspect(source).acceptedForEditing ||
                projects.any { key(it.name) == key(name) }) return emptyList()
            projects.add(EditorProject(name, language, source))
        }
        return projects
    }
}
