package com.rgbcontroller

import com.rgbcontroller.core.ble.MediaControlProtocol
import org.junit.Assert.*
import org.junit.Test

class MediaControlProtocolTest {
    @Test fun `commands are bounded and reject invalid hardware values`() {
        assertEquals("AUD,17,PLAY,24", MediaControlProtocol.request("AUD", "17", "PLAY", "24"))
        assertEquals("OLD,18,FACE,dance", MediaControlProtocol.request("OLD", "18", "FACE", "dance"))
        assertEquals("OLD,19,TEXT,Hola, soy Compi!", MediaControlProtocol.request("OLD", "19", "TEXT", "Hola, soy Compi!"))
        for ((module, verb, value) in listOf(
            Triple("AUD", "PLAY", "0"), Triple("AUD", "VOLUME", "31"),
            Triple("AUD", "VOICE", "2"), Triple("OLD", "FACE", "missing"),
            Triple("OLD", "CONTRAST", "7"), Triple("OLD", "PAUSE", "true"),
            Triple("OLD", "TEXT", "invalid\ncommand")
        )) assertNull(MediaControlProtocol.request(module, "1", verb, value))
        assertNull(MediaControlProtocol.request("AUD", "1,GET", "GET"))
        assertNull(MediaControlProtocol.request("AUD", "1", "NEXT"))
    }

    @Test fun `UART acceptance is not playback confirmation`() {
        val reply = MediaControlProtocol.parseReply("AUD,7,ACCEPTED,1,1,24,UNKNOWN,-1,1,0,1")!!
        assertEquals("7", reply.id)
        assertEquals("ACCEPTED", reply.result)
        assertFalse(reply.audio!!.playing)
        assertEquals(24, reply.audio!!.tracks)
        assertTrue(reply.audio!!.canPlay)
    }

    @Test fun `missing SD and unconfirmed module disable playback`() {
        for (frame in listOf(
            "AUD,8,OK,1,0,-1,UNKNOWN,-1,-1,1,1",
            "AUD,8,OK,0,-1,-1,UNKNOWN,-1,-1,1,0"
        )) {
            val audio = MediaControlProtocol.parseReply(frame)!!.audio!!
            assertFalse(audio.canPlay)
            assertFalse(audio.playing)
        }
    }

    @Test fun `partial telemetry does not block confirmed SD playback`() {
        val audio = MediaControlProtocol.parseReply("AUD,8,OK,1,1,-1,UNKNOWN,-1,-1,0,0")!!.audio!!
        assertTrue(audio.canPlay)
        assertFalse(audio.playing)
        assertFalse(audio.fresh)
    }

    @Test fun `zero SD count does not block numbered MP3 playback`() {
        val audio = MediaControlProtocol.parseReply("AUD,8,OK,1,1,0,STOPPED,0,10,0,1")!!.audio!!
        assertTrue(audio.canPlay)
        assertEquals(0, audio.tracks)
        assertEquals("AUD,9,PLAY,10", MediaControlProtocol.request("AUD", "9", "PLAY", "10"))
        assertEquals("AUD,9,PLAY,2999", MediaControlProtocol.request("AUD", "9", "PLAY", "2999"))
        assertNull(MediaControlProtocol.request("AUD", "9", "PLAY", "3000"))
    }

    @Test fun `OLED readback determines scene and controls`() {
        val oled = MediaControlProtocol.parseReply("OLD,9,OK,1,dance,128,1,0")!!.oled!!
        assertEquals("dance", oled.scene)
        assertTrue(oled.inverted)
        assertFalse(oled.paused)
        assertTrue(oled.ready)
    }

    @Test fun `malformed replies cannot change hardware state`() {
        for (frame in listOf(
            "AUD,1,OK,1,1,24,FAKE,1,1,1,1",
            "AUD,1,OK,1,1,24,PLAYING,1,31,1,1",
            "AUD,1,OK,1,1,24,PLAYING,1,1,2,1",
            "AUD,1,OK,1,1,24,PLAYING,1,1,1",
            "OLD,1,OK,1,cat,128,2,0", "OLD,1,OK,1,cat,999,0,0",
            "OLD,,OK,1,cat,128,0,0", "OLD,1,OK,1,unsupported,128,0,0"
        )) assertNull(frame, MediaControlProtocol.parseReply(frame))
    }
}
