package mattecarra.accapp.acc

import mattecarra.accapp.utils.RootShell

/** Battery/OTG online flags describe internal states, not incoming power. */
internal object ExternalPowerSupply {
    fun script(directory: String = "/sys/class/power_supply"): String = """
        seen=false; connected=false
        for path in ${RootShell.quote(directory)}/*/online; do
            case "${'$'}path" in */battery/*|*/bms/*|*/otg/*) continue;; esac
            type=${'$'}(cat "${'$'}{path%/online}/type" 2>/dev/null)
            case "${'$'}type" in Battery|BMS) continue;; esac
            value=${'$'}(cat "${'$'}path" 2>/dev/null) || continue
            case "${'$'}value" in 0) seen=true;; 1) seen=true; connected=true;; esac
        done
        ${'$'}seen && printf 'online=%s\n' "${'$'}connected"
    """.trimIndent()
}
