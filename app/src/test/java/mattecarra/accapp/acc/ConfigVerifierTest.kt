package mattecarra.accapp.acc

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import mattecarra.accapp.models.AccConfig
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ConfigVerifierTest {
    @Test fun asynchronousWritesMustBeReadBackBeforeSuccess() = runBlocking {
        val requested = AccConfig()
        val stale = requested.copy(configCapacity = requested.configCapacity.copy(pause = 90))
        var reads = 0
        val actual = ConfigVerifier.awaitSaved(requested, true, true, wait = {}) {
            if (++reads < 3) stale else requested.copy(configChargeSwitch = "battery/batt_slate_mode 0 1")
        }
        assertNotNull(actual)
        assertEquals(3, reads)
    }

    @Test fun acceptedCommandWithUnchangedLimitsIsNotSuccess() = runBlocking {
        val requested = AccConfig()
        var reads = 0
        assertNull(ConfigVerifier.awaitSaved(requested, true, true, wait = {}) {
            reads++
            requested.copy(configCapacity = requested.configCapacity.copy(pause = 90))
        })
        assertEquals(10, reads)
    }

    @Test fun transientReadFailureIsRetried() = runBlocking {
        var reads = 0
        assertNotNull(ConfigVerifier.awaitSaved(AccConfig(), true, true, wait = {}) {
            if (++reads == 1) throw IOException("temporary read failure") else AccConfig()
        })
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotReportedAsAConfigMismatch(): Unit = runBlocking {
        ConfigVerifier.awaitSaved(AccConfig(), true, true, wait = {}) { throw CancellationException() }
        Unit
    }
}
