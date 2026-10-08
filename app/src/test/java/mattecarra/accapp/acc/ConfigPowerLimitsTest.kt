package mattecarra.accapp.acc

import mattecarra.accapp.models.AccConfig
import org.junit.Assert.*
import org.junit.Test

class ConfigPowerLimitsTest {
    @Test fun alreadyDisabledLimitsDoNotTriggerHardwareDiscovery() {
        val current = AccConfig()
        val requested = current.copy(configCapacity = current.configCapacity.copy(pause = 90))
        val commands = ConfigUpdaterEnable().skipUnchangedPowerLimits(current, requested)
        assertFalse(commands.sendCurrMax)
        assertFalse(commands.sendVoltage)
        assertTrue(commands.sendCapacity)
    }

    @Test fun removingExistingLimitsStillRunsTheResetCommands() {
        val current = AccConfig(configCurrMax = 1000, configVoltage = AccConfig.ConfigVoltage(max = 4100))
        val commands = ConfigUpdaterEnable().skipUnchangedPowerLimits(current, AccConfig())
        assertTrue(commands.sendCurrMax)
        assertTrue(commands.sendVoltage)
    }

    @Test fun userDisabledControlsRemainDisabledWhenValuesChange() {
        val requested = AccConfig(configCurrMax = 1000, configVoltage = AccConfig.ConfigVoltage(max = 4100))
        val commands = ConfigUpdaterEnable(sendCurrMax = false, sendVoltage = false)
            .skipUnchangedPowerLimits(AccConfig(), requested)
        assertFalse(commands.sendCurrMax)
        assertFalse(commands.sendVoltage)
    }
}
