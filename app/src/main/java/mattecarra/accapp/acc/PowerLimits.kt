package mattecarra.accapp.acc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mattecarra.accapp.utils.RootShell

enum class PowerLimitState { OFF, PENDING, APPLIED, UNSUPPORTED, FAILED, UNKNOWN }

data class PowerLimit(val requested: Int?, val state: PowerLimitState)

data class PowerLimitsSnapshot(val current: PowerLimit?, val voltage: PowerLimit?) {
    fun forControls(currentEnabled: Boolean, voltageEnabled: Boolean) = copy(
        current = current.takeIf { currentEnabled }, voltage = voltage.takeIf { voltageEnabled })

    fun matchingRequest(request: mattecarra.accapp.models.AccConfig): PowerLimitsSnapshot = copy(
        current = current?.let { if (it.requested == request.configCurrMax) it else PowerLimit(request.configCurrMax, PowerLimitState.UNKNOWN) },
        voltage = voltage?.let { if (it.requested == request.configVoltage.max) it else PowerLimit(request.configVoltage.max, PowerLimitState.UNKNOWN) })

    companion object {
        fun parse(output: String): PowerLimitsSnapshot? {
            val pairs = output.lineSequence().filter { it.isNotBlank() }.map { it.split('=', limit = 2) }.toList()
            if (pairs.any { it.size != 2 } || pairs.map { it[0] }.distinct().size != pairs.size) return null
            val fields = pairs.associate { it[0] to it[1] }
            if (fields["powerLimitsVersion"] != "1") return null
            fun limit(kind: String, range: IntRange): PowerLimit? {
                val raw = fields["$kind.requested"] ?: return null
                val requested = if (raw == "default") null else raw.toIntOrNull()?.takeIf { it in range } ?: return null
                val state = fields["$kind.state"]?.uppercase()?.let {
                    PowerLimitState.entries.firstOrNull { entry -> entry.name == it }
                } ?: PowerLimitState.UNKNOWN
                // Incomplete/contradictory readback cannot confirm application.
                val verified = if (state == PowerLimitState.APPLIED &&
                    (requested == null || fields["$kind.supported"] != "true")) PowerLimitState.UNKNOWN
                    else if (state == PowerLimitState.OFF && requested != null) PowerLimitState.UNKNOWN else state
                return PowerLimit(requested, verified)
            }
            val current = limit("current", 0..9999) ?: return null
            val voltage = limit("voltage", 3700..4300) ?: return null
            return PowerLimitsSnapshot(current, voltage)
        }
    }
}

object PowerLimits {
    suspend fun read(): PowerLimitsSnapshot? = withContext(Dispatchers.IO) {
        if (AccNg.readContract()?.capabilities?.contains("power-limits-status") != true) return@withContext null
        val result = RootShell.exec("/dev/.vr25/acc/acca --power-status", 5)
        if (result.isSuccess) PowerLimitsSnapshot.parse(result.out.joinToString("\n")) else null
    }
}
