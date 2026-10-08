package mattecarra.accapp.acc

import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import mattecarra.accapp.models.AccConfig
import mattecarra.accapp.models.ProfileActivation
import mattecarra.accapp.utils.CommandTraceContext

object ConfigVerifier {
    suspend fun awaitApplied(requested: AccConfig, applyVoltage: Boolean, applyCurrent: Boolean,
                             wait: suspend () -> Unit = { delay(500) },
                             read: suspend () -> AccConfig): AccConfig? {
        val trace = CommandTraceContext.current()
        val step = trace?.started("Verify applied ACC settings")
        try {
            repeat(10) {
                val current = try { read() }
                    catch (ex: CancellationException) { throw ex }
                    catch (ex: Exception) { null }
                if (current != null &&
                    ProfileActivation.matches(current, requested, applyVoltage, applyCurrent)) {
                    if (step != null) trace.finished(step, 0, null)
                    return current
                }
                wait()
            }
            if (step != null) trace.finished(step, 1, "ACC settings could not be read back as requested")
            return null
        } catch (ex: CancellationException) {
            if (step != null) trace.finished(step, null, "Configuration verification interrupted")
            throw ex
        }
    }
}
