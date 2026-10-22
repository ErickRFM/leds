package com.rgbcontroller.core.lab

import java.util.Base64

data class LabProject(val name: String, val sequence: String)

/** Local-only, bounded and lossless project storage without credentials or executable code. */
object LabProjects {
    const val MAX_PROJECTS = 8
    private const val MAX_NAME_LENGTH = 24
    private const val MAX_ENCODED_LENGTH = 4096

    private fun validName(name: String) =
        name.isNotBlank() && name == name.trim() && name.length <= MAX_NAME_LENGTH &&
            name.none(Char::isISOControl)

    fun upsert(existing: List<LabProject>, name: String, sequence: String): List<LabProject> {
        val cleaned = name.trim()
        require(validName(cleaned)) { "Nombre inválido (1 a 24 caracteres)" }
        require(LabExperimentLibrary.parse(sequence) != null) { "Secuencia inválida" }
        val previous = existing.indexOfFirst { it.name.equals(cleaned, ignoreCase = true) }
        require(previous != -1 || existing.size < MAX_PROJECTS) { "Máximo 8 proyectos" }
        val result = existing.toMutableList()
        if (previous >= 0) result[previous] = LabProject(cleaned, sequence)
        else result.add(LabProject(cleaned, sequence))
        return result
    }

    fun remove(existing: List<LabProject>, name: String) =
        existing.filterNot { it.name.equals(name, ignoreCase = true) }

    private fun encodePart(text: String) =
        Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))
    private fun decodePart(text: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(text), Charsets.UTF_8)
    }.getOrNull()

    fun encode(projects: List<LabProject>): String {
        require(projects.size <= MAX_PROJECTS)
        require(projects.all { validName(it.name) && LabExperimentLibrary.parse(it.sequence) != null })
        require(projects.map { it.name.lowercase() }.distinct().size == projects.size)
        val content = projects.joinToString("\n") {
            encodePart(it.name) + "." + encodePart(it.sequence)
        }
        require(content.length <= MAX_ENCODED_LENGTH)
        return content
    }

    fun decode(content: String): List<LabProject> {
        if (content.isEmpty()) return emptyList()
        if (content.length > MAX_ENCODED_LENGTH) return emptyList()
        val lines = content.split('\n')
        if (lines.size > MAX_PROJECTS) return emptyList()
        val projects = ArrayList<LabProject>()
        for (line in lines) {
            val fields = line.split('.')
            if (fields.size != 2) return emptyList()
            val name = decodePart(fields[0]) ?: return emptyList()
            val sequence = decodePart(fields[1]) ?: return emptyList()
            if (!validName(name) || LabExperimentLibrary.parse(sequence) == null ||
                projects.any { it.name.equals(name, ignoreCase = true) }) return emptyList()
            projects.add(LabProject(name, sequence))
        }
        return projects
    }
}
