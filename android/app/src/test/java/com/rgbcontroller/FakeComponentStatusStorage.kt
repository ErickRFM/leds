package com.rgbcontroller

import com.rgbcontroller.core.components.status.ComponentStatusStorage
import com.rgbcontroller.core.components.status.LastKnownStatus
import com.rgbcontroller.core.components.status.LocalDesiredConfig

/** In-memory storage double used by dashboard tests. No Android required. */
class FakeComponentStatusStorage : ComponentStatusStorage {
    var lastKnown: LastKnownStatus? = null
    var desired: LocalDesiredConfig = LocalDesiredConfig.EMPTY
    var saveLastKnownCount = 0
    var saveDesiredCount = 0

    override suspend fun loadLastKnown(): LastKnownStatus? = lastKnown

    override suspend fun saveLastKnown(status: LastKnownStatus) {
        lastKnown = status
        saveLastKnownCount++
    }

    override suspend fun loadDesired(): LocalDesiredConfig = desired

    override suspend fun saveDesired(config: LocalDesiredConfig) {
        desired = config
        saveDesiredCount++
    }
}
