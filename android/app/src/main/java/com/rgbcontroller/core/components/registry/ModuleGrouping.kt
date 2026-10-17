package com.rgbcontroller.core.components.registry

/** Logical dashboards can combine real components without merging GPIO owners. */
object ModuleGrouping {
    const val ENVIRONMENTAL = "sensor_environmental"
    val environmentalParts = listOf("dht22", "soil_moisture")

    /** Combine two separately managed sensors into one user-facing workspace. */
    fun visibleModuleIds(configured: List<String>): List<String> {
        val unique = configured.distinct()
        return if (environmentalParts.all { it in unique }) {
            listOf(ENVIRONMENTAL) + unique.filterNot { it in environmentalParts }
        } else unique
    }

    fun partIds(id: String): List<String> =
        if (id == ENVIRONMENTAL) environmentalParts else listOf(id)
}
