package mattecarra.accapp.models

import org.junit.Assert.*
import org.junit.Test

class ProfileActivationTest {
    @Test fun manualCapacityChangeStopsMatchingTheSavedProfile() {
        val saved = AccConfig()
        assertTrue(ProfileActivation.matches(saved.copy(), saved))
        val manual = saved.copy(configCapacity = saved.configCapacity.copy(pause = 90))
        assertFalse(ProfileActivation.matches(manual, saved))
    }

    @Test fun disabledOptionalControlsDoNotInvalidateTheAppliedProfile() {
        val saved = AccConfig(configVoltage = AccConfig.ConfigVoltage(max = 4200), configCurrMax = 1000)
        val current = saved.copy(configVoltage = AccConfig.ConfigVoltage(), configCurrMax = null)
        assertTrue(ProfileActivation.matches(current, saved, applyVoltage = false, applyCurrent = false))
        assertFalse(ProfileActivation.matches(current, saved))
    }

    @Test fun EmptyShellValuesAreEquivalentToUnsetOptionalFields() {
        val saved = AccConfig()
        val current = saved.copy(configOnBoot = "", configOnPlug = " ", configChargeSwitch = "",
            configIsAutomaticSwitchingEnabled = false)
        assertTrue(ProfileActivation.matches(current, saved))
    }

    @Test fun manualSwitchChangesAreStillDetected() {
        val saved = AccConfig(configChargeSwitch = "/sys/battery/charging_enabled 1 0")
        val manual = saved.copy(configIsAutomaticSwitchingEnabled = false)
        assertFalse(ProfileActivation.matches(manual, saved))
    }

    @Test fun automaticSwitchDiscoveryDoesNotDeactivateProfile() {
        val saved = AccConfig()
        val detected = saved.copy(configChargeSwitch = "battery/batt_slate_mode 0 1")
        assertTrue(ProfileActivation.matches(detected, saved))
        assertTrue(ProfileActivation.matches(detected.copy(configChargeSwitch = "battery/charging_enabled 1 0"), saved))
    }

    @Test fun enforcedSwitchMustStillMatch() {
        val saved = AccConfig(configChargeSwitch = "battery/batt_slate_mode 0 1",
            configIsAutomaticSwitchingEnabled = false)
        assertFalse(ProfileActivation.matches(saved.copy(configChargeSwitch = "battery/charging_enabled 1 0"), saved))
        assertFalse(ProfileActivation.matches(saved.copy(configIsAutomaticSwitchingEnabled = true), saved))
    }
}
