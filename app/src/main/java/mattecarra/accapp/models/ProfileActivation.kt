package mattecarra.accapp.models

/** A selection is active only while its applied settings still match ACC. */
object ProfileActivation {
    fun matches(current: AccConfig, saved: AccConfig, applyVoltage: Boolean = true,
                applyCurrent: Boolean = true): Boolean {
        fun normalized(config: AccConfig) = config.copy(
            configVoltage = if (applyVoltage) config.configVoltage else AccConfig.ConfigVoltage(),
            configCurrMax = if (applyCurrent) config.configCurrMax else null,
            configOnBoot = config.configOnBoot?.takeIf { it.isNotBlank() },
            configOnPlug = config.configOnPlug?.takeIf { it.isNotBlank() },
            configChargeSwitch = config.configChargeSwitch?.takeIf { it.isNotBlank() },
            configIsAutomaticSwitchingEnabled = if (config.configChargeSwitch.isNullOrBlank()) true
                else config.configIsAutomaticSwitchingEnabled
        )
        return normalized(current) == normalized(saved)
    }
}
