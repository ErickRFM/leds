package com.rgbcontroller.core.components.registry

/** UI-only grouping. No synthetic sensor detection or authority is implied. */
data class WorkspaceGroup(val id: String, val components: List<String>)

object ModuleWorkspaceGrouping {
    const val ENVIRONMENT = "environmental_workspace"

    fun group(ids: List<String>): List<WorkspaceGroup> {
        val distinct = ids.distinct()
        val joined = "dht22" in distinct && "soil_moisture" in distinct
        val grouped = distinct.filterNot {
            joined && (it == "dht22" || it == "soil_moisture")
        }.map { WorkspaceGroup(it, listOf(it)) }
        return if (joined) {
            listOf(WorkspaceGroup(ENVIRONMENT, listOf("dht22", "soil_moisture"))) + grouped
        } else grouped
    }
}
