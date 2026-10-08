package mattecarra.accapp.models

import kotlin.math.abs

enum class BatteryPowerMode {
    CHARGING, BATTERY_ONLY, CONNECTED_DISCHARGING, DISCHARGING,
    IDLE_ESTIMATED, BYPASS_REPORTED, PAUSED, FULL, UNKNOWN
}

data class BatteryPowerState(val mode: BatteryPowerMode, val full: Boolean = false) {
    companion object {
        fun from(status: String, plugged: Boolean?, currentMa: Float?,
                 full: Boolean = false, bypassReported: Boolean = false,
                 idleThresholdMa: Float = 40f): BatteryPowerState {
            val current = currentMa?.takeIf { it.isFinite() }
            val threshold = idleThresholdMa.takeIf { it.isFinite() && it >= 0 } ?: 40f
            val charging = status.equals("Charging", true)
            val discharging = status.equals("Discharging", true)
            val idle = status.equals("Idle", true) || status.equals("Not charging", true)
            val batteryFull = full || status.equals("Full", true)
            val mode = when {
                plugged == false -> BatteryPowerMode.BATTERY_ONLY
                // A hardware report and substantial battery current conflict.
                plugged == true && bypassReported && (current == null || abs(current) <= threshold) ->
                    BatteryPowerMode.BYPASS_REPORTED
                charging -> BatteryPowerMode.CHARGING
                discharging && plugged == true -> BatteryPowerMode.CONNECTED_DISCHARGING
                discharging -> BatteryPowerMode.DISCHARGING
                plugged == true && idle && current != null && abs(current) <= threshold ->
                    BatteryPowerMode.IDLE_ESTIMATED
                batteryFull -> BatteryPowerMode.FULL
                idle -> BatteryPowerMode.PAUSED
                else -> BatteryPowerMode.UNKNOWN
            }
            return BatteryPowerState(mode, batteryFull)
        }
    }
}

data class BatteryPowerSnapshot(
    val status: String? = null,
    val chargeType: String? = null,
    val currentMa: Float? = null,
    val idleThresholdMa: Float = 40f
) {
    val bypassReported: Boolean get() = chargeType.equals("Bypass", ignoreCase = true)

    companion object {
        fun from(values: Map<String, String>): BatteryPowerSnapshot {
            // Samsung reports mA here; standard drivers report µA. ACC's
            // discovered conversion factor avoids guessing from magnitude.
            val factor = values["ampFactor"]?.toFloatOrNull()?.takeIf { it == 1000f || it == 1000000f }
            val raw = values["current_now"]?.toFloatOrNull()?.takeIf { it.isFinite() }
            return BatteryPowerSnapshot(
                values["status"], values["charge_type"],
                if (raw != null && factor != null) raw * 1000f / factor else null,
                values["idleThreshold"]?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 40f
            )
        }
    }
}
