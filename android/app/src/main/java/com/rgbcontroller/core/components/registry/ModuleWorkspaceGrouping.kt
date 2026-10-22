package com.rgbcontroller.core.components.registry

/** UI-only grouping. No synthetic sensor detection or authority is implied. */
data class WorkspaceGroup(val id: String, val components: List<String>)

object ModuleWorkspaceGrouping {
    const val ENVIRONMENT = "environmental_workspace"

    fun group(ids: List<String>): List<WorkspaceGroup> {
        val distinct = ids.distinct()
        val environmentModel = when {
            "dht11" in distinct -> "dht11"
            "dht22" in distinct -> "dht22"
            else -> null
        }
        val joined = environmentModel != null && "soil_moisture" in distinct
        val grouped = distinct.filterNot {
            joined && (it == environmentModel || it == "soil_moisture")
        }.map { WorkspaceGroup(it, listOf(it)) }
        return if (joined) {
            listOf(WorkspaceGroup(ENVIRONMENT, listOf(environmentModel!!, "soil_moisture"))) + grouped
        } else grouped
    }
}
