package com.rgbcontroller

import com.rgbcontroller.core.ble.BleSnapshotAssembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleSnapshotAssemblerTest {
    private val json = """{"version":1,"measurements":[{"type":"temperature","status":"fresh","valid":true,"value":21.2,"unit":"C","timestampMs":1000}]}"""

    private fun frames(kind: String, sequence: Int, data: String): List<String> {
        val parts = data.chunked(BleSnapshotAssembler.CHUNK_SIZE)
        return listOf("B,$kind,$sequence,${parts.size}") +
            parts.mapIndexed { i, part -> "D,$kind,$sequence,$i,$part" } +
            "E,$kind,$sequence"
    }

    @Test fun acceptsCompleteInOrderNonceBoundSnapshot() {
        val assembler = BleSnapshotAssembler()
        assembler.begin(BleSnapshotAssembler.Kind.TELEMETRY, 37)
        var result: BleSnapshotAssembler.Snapshot? = null
        for (f in frames("T", 37, json)) {
            result = assembler.accept(f, 1000) ?: result
        }
        assertEquals(json, result?.json)
        assertEquals(37, result?.sequence)
        assertEquals(BleSnapshotAssembler.Kind.TELEMETRY, result?.kind)
    }

    @Test fun refusesOutOfOrderAndMissingParts() {
        val assembler = BleSnapshotAssembler()
        assembler.begin(BleSnapshotAssembler.Kind.TELEMETRY, 7)
        val parts = frames("T", 7, json)
        assertNull(assembler.accept(parts[0], 100))
        assertNull(assembler.accept(parts[2], 101)) // missing index zero
        for (frame in parts.drop(2)) assertNull(assembler.accept(frame, 200))
    }

    @Test fun wrongSessionCannotConfirmReading() {
        val assembler = BleSnapshotAssembler()
        assembler.begin(BleSnapshotAssembler.Kind.STATUS, 15)
        for (frame in frames("T", 15, json)) assertNull(assembler.accept(frame, 100))
        for (frame in frames("S", 14, json)) assertNull(assembler.accept(frame, 100))
        var result: BleSnapshotAssembler.Snapshot? = null
        for (frame in frames("S", 15, json)) {
            result = assembler.accept(frame, 100) ?: result
        }
        assertEquals(json, result?.json)
    }

    @Test fun expiredAndOversizedPacketsFailClosed() {
        val assembler = BleSnapshotAssembler()
        assembler.begin(BleSnapshotAssembler.Kind.TELEMETRY, 8)
        val parts = frames("T", 8, json)
        assembler.accept(parts[0], 1_000)
        assertNull(assembler.accept(parts[1], 8_000))
        for (frame in parts.drop(2)) assertNull(assembler.accept(frame, 8_001))
        assembler.begin(BleSnapshotAssembler.Kind.TELEMETRY, 9)
        assembler.accept("B,T,9,1", 1_000)
        assertNull(assembler.accept("D,T,9,0," + "x".repeat(70), 1_100))
        assertNull(assembler.accept("E,T,9", 1_200))
    }

    @Test fun incompleteOrDuplicateDataNeverCreatesSuccess() {
        val assembler = BleSnapshotAssembler()
        assembler.begin(BleSnapshotAssembler.Kind.STATUS, 40)
        assembler.accept("B,S,40,2", 1)
        assembler.accept("D,S,40,0,hello", 2)
        assertNull(assembler.accept("D,S,40,0,hello", 3))
        assertNull(assembler.accept("D,S,40,1,world", 4))
        assertNull(assembler.accept("E,S,40", 5))
        assertTrue(BleSnapshotAssembler.MAX_PARTS <= 41)
    }
}
