package mattecarra.accapp.acc

import com.google.gson.Gson
import mattecarra.accapp.acc.v202107280.AccHandler
import mattecarra.accapp.models.AccConfig
import mattecarra.accapp.models.ProfileActivation
import org.junit.Assert.*
import org.junit.Test

class TemperatureConfigTest {
    private val modern = AccHandler(202610093)
    private fun printed(resume: String = "40", charge: Int = 50) = """
        shutdown_capacity=5
        resume_capacity=70
        pause_capacity=80
        cooldown_capacity=60
        cooldown_charge=$charge
        cooldown_pause=10
        cooldown_temp=40
        max_temp=45
        resume_temp=$resume
        max_temp_pause=45
        prioritize_batt_idle_mode=false
        charging_switch=battery/charging_enabled 1 0
    """.trimIndent()
    private fun oldProfile() = AccConfig(
        configCapacity = AccConfig.ConfigCapacity(5, 70, 80),
        configTemperature = AccConfig.ConfigTemperature(40, 45, 90),
        configCoolDown = AccConfig.ConfigCoolDown(60, 50, 10)
    )

    @Test fun oldCooldownProfileWritesDegreesInsteadOfLegacySeconds() {
        val command = modern.getUpdateAccTemperatureCommand(oldProfile().configTemperature)
        assertTrue(command.contains("resume_temp=40"))
        assertFalse(command.contains("max_temp_pause"))
        assertFalse(command.contains("90"))
        assertTrue(command.startsWith("env async=true "))
    }

    @Test fun modernReadbackMatchesMigratedProfileButStillChecksActualLimits() {
        val actual = modern.parseConfig(printed())
        assertEquals(40, actual.configTemperature.resumeTemperature)
        assertTrue(ProfileActivation.matches(actual, oldProfile()))
        assertFalse(ProfileActivation.matches(modern.parseConfig(printed("41")), oldProfile()))
        assertFalse(ProfileActivation.matches(modern.parseConfig(printed(charge = 49)), oldProfile()))
    }

    @Test fun modernResumeOverrideSurvivesReadWriteRoundTrip() {
        val parsed = modern.parseConfig(printed("40r"))
        assertTrue(parsed.configTemperature.resumeTemperatureOverridesCapacity)
        assertTrue(modern.getUpdateAccTemperatureCommand(parsed.configTemperature).endsWith("resume_temp=40r"))
    }

    @Test fun legacyEngineStillUsesPauseSeconds() {
        val legacy = AccHandler(202107280)
        assertTrue(legacy.getUpdateAccTemperatureCommand(oldProfile().configTemperature).contains("max_temp_pause=90"))
        assertNull(legacy.parseConfig(printed()).configTemperature.resumeTemperature)
        assertEquals(45, legacy.parseConfig(printed()).configTemperature.pause)
    }

    @Test fun oldDatabaseJsonRemainsReadableWithoutTreatingSecondsAsDegrees() {
        val old = Gson().fromJson("""{"coolDownTemperature":40,"maxTemperature":45,"pause":90}""", AccConfig.ConfigTemperature::class.java)
        assertNull(old.resumeTemperature)
        assertEquals(40, TemperatureConfig.modern(old).resumeTemperature)
        val new = old.copy(resumeTemperature = 38, resumeTemperatureOverridesCapacity = true)
        assertEquals(new, Gson().fromJson(Gson().toJson(new), AccConfig.ConfigTemperature::class.java))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidExplicitResumeIsRejected() {
        TemperatureConfig.command(AccConfig.ConfigTemperature(40, 45, resumeTemperature = 90))
    }

    @Test fun modernScriptUsesSameTemperatureCommandAsInteractiveApply() {
        val commands = ConfigUpdater(oldProfile(), ConfigUpdaterEnable()).concatenateCommands(modern)
        assertTrue(commands.contains("resume_temp=40"))
        assertFalse(commands.contains("max_temp_pause"))
    }
}
