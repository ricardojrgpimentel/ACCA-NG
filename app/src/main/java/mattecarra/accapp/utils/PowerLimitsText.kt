package mattecarra.accapp.utils

import android.content.Context
import mattecarra.accapp.R
import mattecarra.accapp.acc.PowerLimitsSnapshot
import mattecarra.accapp.acc.PowerLimitState

object PowerLimitsText {
    fun format(context: Context, limits: PowerLimitsSnapshot): String = listOfNotNull(
        limits.current?.let { R.string.power_limit_current to it },
        limits.voltage?.let { R.string.power_limit_voltage to it }).joinToString("\n") { (label, limit) ->
        val state = when (limit.state) {
            PowerLimitState.OFF -> R.string.power_limit_off
            PowerLimitState.PENDING -> R.string.power_limit_pending
            PowerLimitState.APPLIED -> R.string.power_limit_applied
            PowerLimitState.UNSUPPORTED -> R.string.power_limit_unsupported
            PowerLimitState.FAILED -> R.string.power_limit_failed
            PowerLimitState.UNKNOWN -> R.string.power_limit_unknown
        }
        val unit = if (label == R.string.power_limit_current) "mA" else "mV"
        val name = context.getString(label) + (limit.requested?.let { " ($it $unit)" } ?: "")
        context.getString(R.string.power_limit_line, name, context.getString(state))
    }
}
