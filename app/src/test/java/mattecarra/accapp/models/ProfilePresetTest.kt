package mattecarra.accapp.models

import org.junit.Assert.*
import org.junit.Test

class ProfilePresetTest {
    @Test fun presetsPreserveDeviceSpecificAndThermalSettings() {
        val original = AccConfig(
            configCapacity = AccConfig.ConfigCapacity(5, 65, 85),
            configVoltage = AccConfig.ConfigVoltage("device/path", 4100),
            configCurrMax = 1500,
            configTemperature = AccConfig.ConfigTemperature(35, 45, 60),
            configOnBoot = "boot-command",
            configOnPlug = "plug-command",
            configCoolDown = AccConfig.ConfigCoolDown(55, 40, 15),
            configChargeSwitch = "custom-switch",
            prioritizeBatteryIdleMode = true
        )
        for (preset in ProfilePreset.values()) {
            val result = preset.applyTo(original)
            assertEquals(original, result.copy(configCapacity = original.configCapacity))
            assertEquals(5, result.configCapacity.shutdown)
            assertTrue(result.configCapacity.shutdown < result.configCapacity.resume)
            assertTrue(result.configCapacity.resume < result.configCapacity.pause)
            assertTrue(result.configCapacity.pause <= 100)
            assertEquals(preset, ProfilePreset.matching(result.configCapacity))
        }
        assertEquals(AccConfig.ConfigCapacity(5, 65, 85), original.configCapacity)
    }

    @Test fun manuallyEditedLimitsAreNotLabelledAsAPreset() {
        assertNull(ProfilePreset.matching(AccConfig.ConfigCapacity(5, 74, 80)))
    }
}
