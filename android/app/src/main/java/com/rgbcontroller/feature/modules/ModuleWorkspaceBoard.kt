package com.rgbcontroller.feature.modules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.registry.ModuleWorkspaceGrouping
import com.rgbcontroller.core.components.registry.PinBoardMap
import com.rgbcontroller.core.components.registry.PinBoardReport
import com.rgbcontroller.core.components.registry.WorkspaceGroup
import com.rgbcontroller.core.components.status.LocalDesiredComponent

/** Everything the Modules destination derives from one status snapshot. */
internal data class ModuleWorkspaceBoard(
    val groups: List<WorkspaceGroup>,
    val environmentSensor: String,
    val pinBoard: PinBoardReport
)

/**
 * Pure derivation, kept apart from the screen so its rules are testable without
 * a device. Catalog-only entries mean the driver was compiled, not that a sensor
 * is physically present, so they never create a module on their own, and BLE
 * component status counts only after a complete owner-authenticated snapshot.
 */
internal fun moduleWorkspaceBoardOf(
    desired: List<LocalDesiredComponent>,
    authoritative: List<ComponentRuntimeStatus>?,
    persisted: List<ComponentRuntimeStatus>?,
    bleAuthorized: Boolean,
    pending: Map<String, List<Int>> = emptyMap()
): ModuleWorkspaceBoard {
    val confirmed = (authoritative ?: persisted).orEmpty()
    val reportedIds = confirmed
        .filter { it.active || it.state != "catalog" }
        .map { ComponentCatalog.catalogIdForFirmwareId(it.id) }
    val groups = ModuleWorkspaceGrouping.group(
        (desired.map { it.id } + reportedIds +
            (if (bleAuthorized) listOf("led_rgb") else emptyList())).distinct()
    )
    return ModuleWorkspaceBoard(
        groups = groups,
        environmentSensor = groups.firstOrNull {
            it.id == ModuleWorkspaceGrouping.ENVIRONMENT
        }?.components?.firstOrNull { it == "dht11" || it == "dht22" } ?: "dht11",
        pinBoard = PinBoardMap.build(
            modules = groups.flatMap { it.components },
            local = desired,
            reported = authoritative.orEmpty(),
            pending = pending
        )
    )
}

/**
 * Memoised on the snapshots themselves. Derived from plain vals the grouping was
 * a new instance in every recomposition, which rebuilt every GPIO option of the
 * board each frame even though nothing about the hardware had changed.
 */
@Composable
internal fun rememberModuleWorkspaceBoard(
    desired: List<LocalDesiredComponent>,
    authoritative: List<ComponentRuntimeStatus>?,
    persisted: List<ComponentRuntimeStatus>?,
    bleAuthorized: Boolean,
    pending: Map<String, List<Int>>
): ModuleWorkspaceBoard = remember(
    desired, authoritative, persisted, bleAuthorized, pending
) {
    moduleWorkspaceBoardOf(desired, authoritative, persisted, bleAuthorized, pending)
}
