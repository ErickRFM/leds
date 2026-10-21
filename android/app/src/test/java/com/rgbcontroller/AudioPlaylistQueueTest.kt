package com.rgbcontroller

import com.rgbcontroller.core.audio.AudioPlaylistQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlaylistQueueTest {
    @Test fun boundedQueueOnlyAcceptsRealUserSelectedContentUris() {
        val q = AudioPlaylistQueue(limit = 2)
        assertFalse(q.enqueue("file:///etc/passwd"))
        assertFalse(q.enqueue("https://example.com/song.mp3"))
        assertFalse(q.enqueue("content://"))
        assertFalse(q.enqueue("content:///missing-authority"))
        assertFalse(q.enqueue("content://audio/with space"))
        assertFalse(q.enqueue("content://audio/with\nnewline"))
        assertTrue(q.enqueue("content://audio/1"))
        assertFalse(q.enqueue("content://audio/1"))
        assertTrue(q.enqueue("content://audio/2"))
        assertFalse(q.enqueue("content://audio/3"))
        assertEquals(listOf("content://audio/1", "content://audio/2"), q.tracks())
    }

    @Test fun controlsAndDeletionNeverAccessOutsidePlaylist() {
        val q = AudioPlaylistQueue()
        assertNull(q.next())
        assertNull(q.previous())
        assertNull(q.select(-1))
        q.enqueue("content://audio/1")
        q.enqueue("content://audio/2")
        assertEquals("content://audio/2", q.next())
        assertEquals("content://audio/1", q.next())
        assertEquals("content://audio/2", q.previous())
        assertFalse(q.remove(5))
        assertTrue(q.remove(0))
        assertEquals("content://audio/2", q.current())
        assertTrue(q.remove(0))
        assertNull(q.current())
        q.clear()
        assertEquals(-1, q.currentIndex)
    }
}
