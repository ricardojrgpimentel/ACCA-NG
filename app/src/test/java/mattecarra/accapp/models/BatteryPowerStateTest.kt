package mattecarra.accapp.models

import org.junit.Assert.*
import org.junit.Test

class BatteryPowerStateTest {
    @Test fun chargingIsExplicitEvenAtLowCurrent() {
        assertEquals(BatteryPowerMode.CHARGING, BatteryPowerState.from("Charging", true, 500f).mode)
        assertEquals(BatteryPowerMode.CHARGING, BatteryPowerState.from("Charging", true, 5f).mode)
    }

    @Test fun disconnectedOverridesStaleChargingFullAndBypassReports() {
        assertEquals(BatteryPowerMode.BATTERY_ONLY,
            BatteryPowerState.from("Charging", false, 500f, full = true, bypassReported = true).mode)
    }

    @Test fun connectedDischargingIsNotMislabelledAsPausedOrFull() {
        assertEquals(BatteryPowerMode.CONNECTED_DISCHARGING,
            BatteryPowerState.from("Discharging", true, -300f, full = true).mode)
    }

    @Test fun accIdleAndLowCurrentSuggestChargerPowerWithoutProvingBypass() {
        val result = BatteryPowerState.from("Not charging", true, 6f, full = true)
        assertEquals(BatteryPowerMode.IDLE_ESTIMATED, result.mode)
        assertTrue(result.full)
        assertEquals(BatteryPowerMode.IDLE_ESTIMATED, BatteryPowerState.from("Idle", true, 0f).mode)
    }

    @Test fun missingCurrentMustNotBecomeAFakeZeroOrIdle() {
        assertEquals(BatteryPowerMode.PAUSED, BatteryPowerState.from("Not charging", true, null).mode)
        assertEquals(BatteryPowerMode.PAUSED, BatteryPowerState.from("Not charging", true, Float.NaN).mode)
    }

    @Test fun zeroAloneDoesNotEstablishIdleAndUnknownConnectionStaysUnconfirmed() {
        assertEquals(BatteryPowerMode.UNKNOWN, BatteryPowerState.from("Unknown", true, 0f).mode)
        assertEquals(BatteryPowerMode.PAUSED, BatteryPowerState.from("Not charging", null, 0f).mode)
    }

    @Test fun hardwareBypassReportIsDistinguishedFromAnEstimate() {
        assertEquals(BatteryPowerMode.BYPASS_REPORTED,
            BatteryPowerState.from("Not charging", true, 0f, bypassReported = true).mode)
        assertEquals(BatteryPowerMode.BYPASS_REPORTED,
            BatteryPowerState.from("Not charging", true, null, bypassReported = true).mode)
    }

    @Test fun substantialBatteryDischargeContradictsAnIdleBypassReport() {
        assertEquals(BatteryPowerMode.CONNECTED_DISCHARGING,
            BatteryPowerState.from("Discharging", true, -500f, bypassReported = true).mode)
    }

    @Test fun fullIsReportedWithoutGuessingThePowerSource() {
        assertEquals(BatteryPowerMode.FULL, BatteryPowerState.from("Not charging", true, null, full = true).mode)
        assertEquals(BatteryPowerMode.FULL, BatteryPowerState.from("Full", true, 0f).mode)
    }

    @Test fun theConfiguredIdleThresholdIsRespected() {
        assertEquals(BatteryPowerMode.PAUSED,
            BatteryPowerState.from("Not charging", true, 15f, idleThresholdMa = 10f).mode)
    }

    @Test fun kernelCurrentUsesTheDetectedUnitRatherThanItsMagnitude() {
        assertEquals(-6f, BatteryPowerSnapshot.from(mapOf("current_now" to "-6", "ampFactor" to "1000")).currentMa)
        assertEquals(-6f, BatteryPowerSnapshot.from(mapOf("current_now" to "-6000", "ampFactor" to "1000000")).currentMa)
        assertNull(BatteryPowerSnapshot.from(mapOf("current_now" to "-6")).currentMa)
    }

    @Test fun unavailableKernelValuesCannotConfirmBypass() {
        val missing = BatteryPowerSnapshot.from(emptyMap())
        assertNull(missing.currentMa)
        assertFalse(missing.bypassReported)
        assertTrue(BatteryPowerSnapshot.from(mapOf("charge_type" to "Bypass")).bypassReported)
        assertFalse(BatteryPowerSnapshot.from(mapOf("charge_type" to "Fast")).bypassReported)
    }
}
