package com.rgbcontroller

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.feature.modules.ModuleWorkspaceBoard
import com.rgbcontroller.feature.modules.rememberModuleWorkspaceBoard
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The board is the expensive derivation of the Modules destination: it walks
 * every candidate GPIO of every module. These tests pin the memoisation itself,
 * which no JVM test can see because it only exists while a composition runs.
 * Rebuilding it per frame is what stalled the first opened board.
 */
@RunWith(AndroidJUnit4::class)
class ModuleWorkspaceBoardMemoTest {
    @get:Rule val composeRule = createComposeRule()

    private val firstBoard = AtomicReference<ModuleWorkspaceBoard?>(null)
    private val sameAsFirst = AtomicInteger()
    private val differentFromFirst = AtomicInteger()
    private val built = Collections.synchronizedList(mutableListOf<ModuleWorkspaceBoard>())

    private fun record(board: ModuleWorkspaceBoard) {
        if (firstBoard.get() == null) firstBoard.set(board)
        if (firstBoard.get() === board) sameAsFirst.incrementAndGet()
        else differentFromFirst.incrementAndGet()
    }

    @Test
    fun anUnrelatedRecompositionKeepsExactlyOneBoardInstance() {
        val desired = listOf(LocalDesiredComponent("dht11", "sensor", listOf(16)))
        composeRule.setContent {
            // The counter lives in this body, not in a child: only a recomposition
            // of the body itself can rebuild the memoised board.
            var ticks by remember { mutableIntStateOf(0) }
            val board = rememberModuleWorkspaceBoard(
                desired = remember { desired },
                authoritative = emptyList(),
                persisted = null,
                bleAuthorized = false,
                pending = emptyMap()
            )
            SideEffect { record(board) }
            Box(Modifier.testTag("tick_$ticks"))
            SideEffect { if (ticks < 30) ticks++ }
        }
        composeRule.waitUntil(20_000) { sameAsFirst.get() >= 25 }
        assertEquals(
            "the board was rebuilt while the hardware snapshot never changed",
            0, differentFromFirst.get()
        )
    }

    @Test
    fun aChangedDraftRebuildsTheBoardOnceAndCarriesTheNewModule() {
        var drafts by mutableStateOf(listOf(LocalDesiredComponent("dht11", "sensor", listOf(16))))
        composeRule.setContent {
            val board = rememberModuleWorkspaceBoard(
                desired = drafts,
                authoritative = emptyList(),
                persisted = null,
                bleAuthorized = false,
                pending = emptyMap()
            )
            SideEffect { built.add(board) }
            Box(
                Modifier.testTag("add_soil").clickable {
                    drafts = drafts + LocalDesiredComponent("soil_moisture", "sensor", listOf(34))
                }
            )
        }

        val before = built.size
        composeRule.onNodeWithTag("add_soil").performClick()
        composeRule.waitForIdle()

        val after = built.last()
        assertTrue("the draft change produced no new board", built.size > before)
        assertNotSame(built.first(), after)
        assertEquals(listOf(34), after.pinBoard.row("soil_moisture")?.pins)
        assertTrue(after.groups.any { it.components.contains("soil_moisture") })
    }
}
