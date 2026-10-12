package com.rgbcontroller.core.programs

/** Preserve user work when a new imported or pasted source would overwrite it. */
object EditorReplacementPolicy {
    fun requiresConfirmation(current: String, incoming: String): Boolean =
        current.isNotBlank() && current != incoming
}
