package mattecarra.accapp.models

import org.junit.Assert.*
import org.junit.Test

class AccHealthTest {
    private val discharge = AccHealthSnapshot(available = true, running = true, moduleHash = "bundled",
        hasCurrentSensor = true, workaround = true, currentRaw = -250, ampFactor = 1000,
        status = "Discharging", online = false, polarity = "-", pause = 60, resume = 50, capacity = 98)

    @Test fun calibrationRequiresFiveStableUnpluggedReadings() {
        assertEquals("-", AccHealthSnapshot.calibrationPolarity(List(5) { discharge }))
        assertEquals("+", AccHealthSnapshot.calibrationPolarity(List(5) { discharge.copy(currentRaw = 250) }))
        assertNull(AccHealthSnapshot.calibrationPolarity(List(4) { discharge }))
        for (bad in listOf(discharge.copy(online = true), discharge.copy(online = null),
            discharge.copy(status = "Charging"), discharge.copy(currentRaw = 0),
            discharge.copy(currentRaw = 250), discharge.copy(ampFactor = null),
            discharge.copy(hasCurrentSensor = false))) {
            assertNull(AccHealthSnapshot.calibrationPolarity(List(4) { discharge } + bad))
        }
    }

    @Test fun microampReadingsUseDetectedScale() {
        assertEquals(-250.0, discharge.copy(currentRaw = -250000, ampFactor = 1000000).currentMa!!, .001)
        assertNull(AccHealthSnapshot.calibrationPolarity(List(5) {
            discharge.copy(currentRaw = -500, ampFactor = 1000000)
        }))
    }

    @Test fun processRunningCanStillMeanCalibrationPending() {
        val s = discharge.copy(polarity = "")
        assertTrue(AccHealthIssue.CALIBRATION_PENDING in AccHealthTracker().evaluate(s, "bundled", 0).issues)
        assertFalse(discharge.copy(polarity = "", hasCurrentSensor = false).calibrationPending)
        assertFalse(s.copy(workaround = false).calibrationPending)
    }

    @Test fun sustainedRealChargeAboveLimitRaisesWarningAfterGrace() {
        val tracker = AccHealthTracker()
        val charging = discharge.copy(status = "Charging", currentRaw = 150, online = true)
        for (time in listOf(0L, 10000L, 20000L))
            assertFalse(AccHealthIssue.CHARGING_ABOVE_LIMIT in tracker.evaluate(charging, "bundled", time).issues)
        assertTrue(AccHealthIssue.CHARGING_ABOVE_LIMIT in tracker.evaluate(charging, "bundled", 30000).issues)
        assertFalse(AccHealthIssue.CHARGING_ABOVE_LIMIT in tracker.evaluate(discharge, "bundled", 35000).issues)
    }

    @Test fun oppositePolarityWorksAndUsbTransientsDoNotTriggerWarning() {
        val tracker = AccHealthTracker()
        val charging = discharge.copy(polarity = "+", currentRaw = -150, status = "Charging")
        for (time in listOf(0L, 10000L, 20000L, 30000L)) tracker.evaluate(charging, "bundled", time)
        assertTrue(AccHealthIssue.CHARGING_ABOVE_LIMIT in tracker.evaluate(charging, "bundled", 35000).issues)
        val drainedOnUsb = charging.copy(currentRaw = 150)
        assertFalse(AccHealthIssue.CHARGING_ABOVE_LIMIT in tracker.evaluate(drainedOnUsb, "bundled", 40000).issues)
    }

    @Test fun observationGapsAndNewLimitsResetTheGracePeriod() {
        val tracker = AccHealthTracker()
        val charging = discharge.copy(currentRaw = 200, status = "Charging")
        tracker.evaluate(charging, "bundled", 0)
        assertFalse(AccHealthIssue.CHARGING_ABOVE_LIMIT in tracker.evaluate(charging, "bundled", 60000).issues)
        tracker.evaluate(charging, "bundled", 70000)
        tracker.evaluate(charging, "bundled", 80000)
        assertFalse(AccHealthIssue.CHARGING_ABOVE_LIMIT in tracker.evaluate(charging.copy(pause = 80), "bundled", 90000).issues)
    }

    @Test fun voltageThresholdIsNotComparedWithBatteryPercentage() {
        val tracker = AccHealthTracker()
        val s = discharge.copy(pause = 4200, currentRaw = 200, status = "Charging")
        for (time in 0L..40000L step 10000)
            assertFalse(AccHealthIssue.CHARGING_ABOVE_LIMIT in tracker.evaluate(s, "bundled", time).issues)
    }

    @Test fun differentHashIsNotProofOfFixedLimits() {
        val issues = AccHealthTracker().evaluate(discharge.copy(moduleHash = "newer"), "bundled", 0).issues
        assertTrue(AccHealthIssue.DIFFERENT_MODULE in issues)
        assertFalse(AccHealthIssue.FIXED_LIMIT_MODULE in issues)
        assertTrue(AccHealthIssue.UNAVAILABLE in AccHealthTracker().evaluate(discharge.copy(running = null), "bundled", 0).issues)
    }

    @Test fun knownFixedLimitLoopIsRecognisedButConfiguredLoopIsNot() {
        val fixed = """ctrl_charging() { if [ "${'$'}batt_level" -ge 90 ]; then disable_charging; fi
            if [ "${'$'}batt_level" -le 80 ]; then enable_charging; fi; } force_off() { }"""
        assertTrue(AccHealthSnapshot.hasFixedLimitLoop(fixed))
        assertFalse(AccHealthSnapshot.hasFixedLimitLoop(fixed.replace("-ge 90", "-ge 85")))
        assertFalse(AccHealthSnapshot.hasFixedLimitLoop(fixed.replace("disable_charging", "_ge_pause_cap && disable_charging")))
    }
}
