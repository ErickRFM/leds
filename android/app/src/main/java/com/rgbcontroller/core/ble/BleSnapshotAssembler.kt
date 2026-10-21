package com.rgbcontroller.core.ble

/** Framed JSON over the existing GATT STATE notification (ATT payload <= 61).
 * Each request is bound to a client-generated 16-bit nonzero sequence.
 * Partial, out-of-order, oversized or other-session replies never become data.
 */
class BleSnapshotAssembler {
    enum class Kind(val code: String) { STATUS("S"), TELEMETRY("T") }
    data class Snapshot(val kind: Kind, val json: String, val sequence: Int)

    companion object {
        const val CHUNK_SIZE = 38
        const val MAX_PAYLOAD = 1536
        const val MAX_PARTS = (MAX_PAYLOAD + CHUNK_SIZE - 1) / CHUNK_SIZE
        const val MAX_INTERVAL_MS = 6_000L
    }

    private var wantedKind: Kind? = null
    private var wantedSequence = 0
    private var expectedCount = 0
    private var nextIndex = 0
    private val data = StringBuilder()
    private var lastFrameAt = 0L

    fun begin(kind: Kind, sequence: Int) {
        require(sequence in 1..65535)
        reset()
        wantedKind = kind
        wantedSequence = sequence
    }

    fun reset() {
        wantedKind = null
        wantedSequence = 0
        expectedCount = 0
        nextIndex = 0
        data.setLength(0)
        lastFrameAt = 0L
    }

    fun accept(frame: String, nowMs: Long): Snapshot? {
        val kind = wantedKind ?: return null
        if (lastFrameAt != 0L && (nowMs < lastFrameAt ||
                nowMs - lastFrameAt > MAX_INTERVAL_MS)) {
            reset()
            return null
        }
        if (frame.length > 61 || frame.any { it.code < 32 || it.code > 126 }) {
            reset()
            return null
        }
        val pieces = frame.split(',', limit = 5)
        if (pieces.size < 3 ||
            pieces[1] != kind.code ||
            pieces[2].toIntOrNull() != wantedSequence) return null
        val result = when (pieces[0]) {
            "B" -> {
                val count = pieces.getOrNull(3)?.toIntOrNull()
                if (pieces.size != 4 || expectedCount != 0 ||
                    count == null || count !in 1..MAX_PARTS) {
                    reset()
                } else {
                    expectedCount = count
                    nextIndex = 0
                    lastFrameAt = nowMs
                }
                null
            }
            "D" -> {
                val index = pieces.getOrNull(3)?.toIntOrNull()
                val part = pieces.getOrNull(4)
                if (pieces.size != 5 || expectedCount == 0 ||
                    index != nextIndex || part.isNullOrEmpty() ||
                    part.length > CHUNK_SIZE ||
                    data.length + part.length > MAX_PAYLOAD) {
                    reset()
                } else {
                    data.append(part)
                    ++nextIndex
                    lastFrameAt = nowMs
                }
                null
            }
            "E" -> {
                if (pieces.size != 3 || expectedCount == 0 ||
                    nextIndex != expectedCount || data.isEmpty()) {
                    reset()
                    null
                } else {
                    val snapshot = Snapshot(kind, data.toString(), wantedSequence)
                    reset()
                    snapshot
                }
            }
            else -> { reset(); null }
        }
        return result
    }
}
