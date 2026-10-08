package mattecarra.accapp.acc

import org.junit.Assert.*
import org.junit.Test

class ConfigUpdateResultTest {
    @Test fun optionalDisabledUpdatesDoNotCountAsFailure() {
        assertTrue(ConfigUpdateResult().isSuccessful())
    }

    @Test fun failuresInPreviouslyIgnoredSettingsAreReported() {
        assertFalse(ConfigUpdateResult(currentMaxUpdateSuccessful = ConfigUpdateStatus.STATUS_FAIL).isSuccessful())
        assertFalse(ConfigUpdateResult(resetBSOnPauseSuccessful = ConfigUpdateStatus.STATUS_FAIL).isSuccessful())
        assertFalse(ConfigUpdateResult(prioritizeBatteryIdleModeSuccessful = ConfigUpdateStatus.STATUS_FAIL).isSuccessful())
    }
}
