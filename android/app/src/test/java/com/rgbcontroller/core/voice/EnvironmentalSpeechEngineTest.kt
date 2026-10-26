package com.rgbcontroller.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentalSpeechEngineTest {
    @Test fun noInitialAnnouncementOrRepeatedReading() {
        val engine = EnvironmentalSpeechEngine()
        assertTrue(engine.evaluate(23f, 90f, 1000, 1000).isEmpty())
        assertTrue(engine.evaluate(28f, 10f, 1000, 1000).isEmpty())
    }

    @Test fun temperatureTransitionsRequireHysteresis() {
        val engine = EnvironmentalSpeechEngine()
        engine.evaluate(23f, null, 1000, 1000)
        assertEquals(listOf(EnvironmentalSpeechEngine.Cue.HOT),
            engine.evaluate(27f, null, 2000, 2000))
        assertTrue(engine.evaluate(25.9f, null, 3000, 3000).isEmpty())
        assertEquals(listOf(EnvironmentalSpeechEngine.Cue.COOLER),
            engine.evaluate(24f, null, 65000, 65000))
        assertEquals(listOf(EnvironmentalSpeechEngine.Cue.COLD),
            engine.evaluate(19f, null, 126000, 126000))
    }

    @Test fun lightTransitionsAreSuppressedByCooldownAndStaleness() {
        val engine = EnvironmentalSpeechEngine()
        engine.evaluate(null, 90f, 1000, 1000)
        assertEquals(listOf(EnvironmentalSpeechEngine.Cue.LIGHT_OFF),
            engine.evaluate(null, 10f, 2000, 2000))
        assertTrue(engine.evaluate(null, 90f, 3000, 3000).isEmpty())
        assertTrue(engine.evaluate(null, 10f, 4000, 4000).isEmpty())
        assertEquals(listOf(EnvironmentalSpeechEngine.Cue.LIGHT_ON),
            engine.evaluate(null, 90f, 65000, 65000))
        assertTrue(engine.evaluate(null, 5f, 66000, 90000).isEmpty())
    }

    @Test fun disconnectResetDoesNotInventATransition() {
        val engine = EnvironmentalSpeechEngine()
        engine.evaluate(23f, 90f, 1000, 1000)
        engine.reset()
        assertTrue(engine.evaluate(30f, 5f, 2000, 2000).isEmpty())
    }
}
