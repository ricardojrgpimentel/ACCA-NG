package mattecarra.accapp.acc

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class NgEngineContractTest {
    @Test fun explicitMetadataIsIndependentOfReleaseDate() {
        val contract = requireNotNull(NgEngineContract.parse("""
            versionCode=1
            ngApiVersion=1
            ngConfigSchema=202310160
            ngCapabilities=resume-temperature,ng-notifications,idle-apps
        """.trimIndent()))
        assertTrue(contract.supportsConfig)
        assertEquals(setOf("resume-temperature", "ng-notifications"), contract.capabilities)
        val handler = Acc.getAccInterfaceForversion(1, contract)
        assertTrue(handler.usesResumeTemperature)
        assertTrue(handler.getUpdateAccTemperatureCommand(mattecarra.accapp.models.AccConfig.ConfigTemperature(35, 50))
            .contains("resume_temp=45"))
    }

    @Test fun oldNgBuildsKeepApiOneBaselineWithoutInventingCapabilities() {
        for (metadata in listOf("ngApiVersion=1\nupstreamVersionCode=202310160", "ngApiVersion=1")) {
            val contract = requireNotNull(NgEngineContract.parse(metadata))
            assertTrue(contract.supportsConfig)
            assertTrue(contract.usesResumeTemperature)
            assertTrue(contract.capabilities.isEmpty())
        }
    }

    @Test fun nonNgMetadataRetainsLegacyHandling() {
        assertNull(NgEngineContract.parse("versionCode=202107280"))
        assertNull(NgEngineContract.parse(""))
        assertTrue(Acc.getAccInterfaceForversion(202107280).getUpdateAccTemperatureCommand(
            mattecarra.accapp.models.AccConfig.ConfigTemperature()).contains("max_temp_pause="))
    }

    @Test fun unknownOrMalformedApiAndSchemaCannotUseGuessedConfigLayout() {
        for (metadata in listOf("ngApiVersion=2", "ngApiVersion=broken", "ngApiVersion=1\nngConfigSchema=202610090",
            "ngApiVersion=1\nngConfigSchema=broken", "ngApiVersion=1\nupstreamVersionCode=broken")) {
            val contract = requireNotNull(NgEngineContract.parse(metadata + "\nngCapabilities=ng-notifications"))
            assertFalse(contract.supportsConfig)
            assertTrue(contract.capabilities.isEmpty())
            try { Acc.getAccInterfaceForversion(202610095, contract); fail("Unknown layout must not select a writer") }
            catch (_: IOException) { }
        }
    }

    @Test fun explicitSchemaOverridesHistoricalUpstreamVersion() {
        val contract = requireNotNull(NgEngineContract.parse("ngApiVersion=1\nngConfigSchema=202310160\nupstreamVersionCode=202001010"))
        assertTrue(contract.supportsConfig)
    }
}
