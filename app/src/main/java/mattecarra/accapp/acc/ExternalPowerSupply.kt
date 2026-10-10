package mattecarra.accapp.acc

import mattecarra.accapp.utils.RootShell

/** Generated shared engine semantics; unknown emits no calibration permission. */
internal object ExternalPowerSupply {
    fun script(directory: String = "/sys/class/power_supply"): String =
        GeneratedExternalPower.source + "\n" + """
            if ng_power_state ${RootShell.quote(directory)}; then printf 'online=true\n'
            else case "${'$'}?" in 1) printf 'online=false\n';; esac; fi
        """.trimIndent()
}
