package com.rgbcontroller

import com.rgbcontroller.core.components.alerts.AlertComparison
import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.alerts.AlertSeverity
import com.rgbcontroller.core.components.automation.AutomationAction
import com.rgbcontroller.core.components.automation.AutomationRule
import com.rgbcontroller.core.components.editors.AlertRuleDraft
import com.rgbcontroller.core.components.editors.AutomationActionType
import com.rgbcontroller.core.components.editors.AutomationRuleDraft
import com.rgbcontroller.core.components.editors.ComponentConfigDraft
import com.rgbcontroller.core.components.editors.LabConfigValidation
import com.rgbcontroller.core.components.editors.SoilCalibrationDraft
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareLabEditorsTest {

    private fun alertDraft(
        thresholdText: String = "35",
        hysteresisText: String = "1",
        intervalText: String = "60",
        componentId: String = "dht22",
        measurementType: String = "temperature"
    ) = AlertRuleDraft(
        id = "r", componentId = componentId, measurementType = measurementType,
        comparison = AlertComparison.ABOVE, thresholdText = thresholdText,
        hysteresisText = hysteresisText, minIntervalSecondsText = intervalText,
        severity = AlertSeverity.WARNING, enabled = true
    )

    @Test fun alertDraftProducesRuleWithParsedValues() {
        val result = alertDraft().validate()
        assertTrue(result.isValid)
        val rule = result.value!!
        assertEquals(35f, rule.threshold)
        assertEquals(1f, rule.hysteresis)
        assertEquals(60_000L, rule.minIntervalMs)
    }

    @Test fun alertDraftRejectsBadNumbersAndSeries() {
        assertFalse(alertDraft(thresholdText = "abc").validate().isValid)
        assertFalse(alertDraft(hysteresisText = "-1").validate().isValid)
        assertFalse(alertDraft(intervalText = "999999").validate().isValid)
        assertFalse(alertDraft(componentId = "soil_moisture", measurementType = "temperature").validate().isValid)
        assertFalse(alertDraft(componentId = "nope").validate().isValid)
    }

    @Test fun alertDraftRoundTripsFromRule() {
        val rule = AlertRule("r", "soil_moisture", "soil_moisture", AlertComparison.BELOW, 25f,
            hysteresis = 2f, minIntervalMs = 30_000L, severity = AlertSeverity.CRITICAL, enabled = false)
        val round = AlertRuleDraft.from(rule).validate()
        assertEquals(rule, round.value)
    }

    @Test fun automationNotifyDraftValidates() {
        val draft = AutomationRuleDraft.blank().copy(
            thresholdText = "45", actionType = AutomationActionType.NOTIFY, messageText = "Calor"
        )
        val result = draft.validate()
        assertTrue(result.isValid)
        val action = result.value!!.action
        assertTrue(action is AutomationAction.Notify)
        assertEquals("Calor", (action as AutomationAction.Notify).message)
    }

    @Test fun automationRgbDraftValidatesAndRejectsBadBrightness() {
        val ok = AutomationRuleDraft.blank().copy(
            thresholdText = "10", actionType = AutomationActionType.SET_RGB,
            redText = "255", greenText = "128", blueText = "0", brightnessText = "80"
        ).validate()
        assertTrue(ok.isValid)
        val rgb = ok.value!!.action as AutomationAction.SetRgb
        assertEquals(128, rgb.green)
        assertEquals(80, rgb.brightness)

        val bad = AutomationRuleDraft.blank().copy(
            thresholdText = "10", actionType = AutomationActionType.SET_RGB, brightnessText = "200"
        ).validate()
        assertFalse(bad.isValid)
        assertTrue(bad.issues.any { it.field == "brightness" })
    }

    @Test fun automationOledRequiresText() {
        val empty = AutomationRuleDraft.blank().copy(
            thresholdText = "5", actionType = AutomationActionType.UPDATE_OLED, oledText = "  "
        ).validate()
        assertTrue(empty.issues.any { it.field == "oled" })
        val filled = AutomationRuleDraft.blank().copy(
            thresholdText = "5", actionType = AutomationActionType.UPDATE_OLED, oledText = "SECO"
        ).validate()
        assertEquals("SECO", (filled.value!!.action as AutomationAction.UpdateOled).text)
    }

    @Test fun automationDraftRoundTripsEachActionType() {
        val rules = listOf(
            AutomationRule("a", "dht22", "temperature", AlertComparison.ABOVE, 45f,
                action = AutomationAction.Notify("x", AlertSeverity.CRITICAL), enabled = true),
            AutomationRule("b", "dht22", "temperature", AlertComparison.ABOVE, 40f,
                action = AutomationAction.SetRgb(1, 2, 3, 50)),
            AutomationRule("c", "soil_moisture", "soil_moisture", AlertComparison.BELOW, 20f,
                action = AutomationAction.UpdateOled("seco"))
        )
        for (rule in rules) {
            assertEquals(rule, AutomationRuleDraft.from(rule).validate().value)
        }
    }

    @Test fun soilCalibrationDraftValidates() {
        val ok = SoilCalibrationDraft("40", "1200").validate()
        assertTrue(ok.isValid)
        assertEquals(50, ok.value!!.percentage(620))
        assertNotNull(ok.value)
        assertFalse(SoilCalibrationDraft("40", "80").validate().isValid)
        assertFalse(SoilCalibrationDraft("40", "5000").validate().isValid)
        assertFalse(SoilCalibrationDraft("abc", "1200").validate().isValid)
    }

    @Test fun componentConfigDraftValidatesCatalogAndPins() {
        val ok = ComponentConfigDraft("led_rgb", "25, 26, 27").validate()
        assertTrue(ok.isValid)
        assertEquals(listOf(25, 26, 27), ok.value!!.pins)

        assertFalse(ComponentConfigDraft("nope", "1").validate().isValid)
        // led_rgb requires 27, 25, 26: a wrong subset must be rejected.
        assertFalse(ComponentConfigDraft("led_rgb", "25, 26").validate().isValid)
        assertFalse(ComponentConfigDraft("led_rgb", "25, 25, 27").validate().isValid)
        assertFalse(ComponentConfigDraft("led_rgb", "40, 41, 42").validate().isValid)
    }

    @Test fun configValidationFlagsDuplicatesAndUnknownIds() {
        val issues = LabConfigValidation.validateDesired(
            listOf(
                LocalDesiredComponent("dht22", "sensor", listOf(32)),
                LocalDesiredComponent("dht22", "sensor", listOf(32)),
                LocalDesiredComponent("ghost", "sensor", emptyList())
            )
        )
        assertTrue(issues.any { it.message.contains("duplicado") })
        assertTrue(issues.any { it.message.contains("catálogo") })
        assertTrue(
            LabConfigValidation.validateDesired(
                listOf(LocalDesiredComponent("dht22", "sensor", listOf(32)))
            ).isEmpty()
        )
    }
}
