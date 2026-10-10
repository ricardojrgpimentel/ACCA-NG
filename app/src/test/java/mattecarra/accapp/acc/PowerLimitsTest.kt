package mattecarra.accapp.acc

import org.junit.Assert.*
import org.junit.Test

class PowerLimitsTest {
    private fun output(state: String = "applied", supported: String = "true", requested: String = "500") = """
        powerLimitsVersion=1
        online=true
        current.requested=$requested
        current.supported=$supported
        current.state=$state
        voltage.requested=default
        voltage.supported=unknown
        voltage.state=off
    """.trimIndent()

    @Test fun appliedControlIsDistinctFromSavedRequestAndMissingSupport() {
        for ((raw, expected) in listOf("applied" to PowerLimitState.APPLIED,
            "pending" to PowerLimitState.PENDING, "unsupported" to PowerLimitState.UNSUPPORTED,
            "failed" to PowerLimitState.FAILED)) {
            val parsed = requireNotNull(PowerLimitsSnapshot.parse(output(raw)))
            assertEquals(500, parsed.current?.requested)
            assertEquals(expected, parsed.current?.state)
            assertEquals(PowerLimitState.OFF, parsed.voltage?.state)
        }
    }
    @Test fun incompleteOrConflictingProtocolDoesNotConfirmControls() {
        for (raw in listOf("", output().replace("powerLimitsVersion=1", "powerLimitsVersion=2"),
            output() + "\ncurrent.state=failed", output().replace("current.requested=500\n", ""),
            output(requested = "500mA"), output(requested = "-1"), output(requested = "10000"))) {
            assertNull(PowerLimitsSnapshot.parse(raw))
        }
        assertEquals(PowerLimitState.UNKNOWN, PowerLimitsSnapshot.parse(output(supported = "false"))?.current?.state)
        assertEquals(PowerLimitState.UNKNOWN, PowerLimitsSnapshot.parse(output(requested = "default"))?.current?.state)
        assertEquals(PowerLimitState.UNKNOWN, PowerLimitsSnapshot.parse(output(state = "new-state"))?.current?.state)
    }
    @Test fun zeroIsAValidCurrentLimitAndDisabledPreferencesHideItsResult() {
        val parsed = requireNotNull(PowerLimitsSnapshot.parse(output(requested = "0")))
        assertEquals(0, parsed.current?.requested)
        assertNull(parsed.forControls(false, true).current)
        assertNotNull(parsed.forControls(false, true).voltage)
    }
    @Test fun statusOfAnotherRequestCannotConfirmTheRequestedProfile() {
        val requested = mattecarra.accapp.models.AccConfig(configCurrMax = 600)
        assertEquals(PowerLimitState.UNKNOWN,
            requireNotNull(PowerLimitsSnapshot.parse(output())).matchingRequest(requested).current?.state)
    }

}
