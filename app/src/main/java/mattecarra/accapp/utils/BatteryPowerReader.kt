package mattecarra.accapp.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mattecarra.accapp.models.BatteryPowerSnapshot

object BatteryPowerReader {
    suspend fun read(): BatteryPowerSnapshot = withContext(Dispatchers.IO) {
        val script = """
            for field in status charge_type; do
                for source in battery bms; do
                    value=${'$'}(cat /sys/class/power_supply/${'$'}source/${'$'}field 2>/dev/null) || continue
                    printf '%s=%s\n' "${'$'}field" "${'$'}value"
                    break
                done
            done
            currentFile=${'$'}(sed -n 's/^currFile=//p' /dev/.vr25/acc/.batt-interface.sh 2>/dev/null)
            if printf '%s' "${'$'}currentFile" | grep -Eq '^[A-Za-z0-9_-]+/current_now$'; then
                value=${'$'}(cat "/sys/class/power_supply/${'$'}currentFile" 2>/dev/null) && printf 'current_now=%s\n' "${'$'}value"
                sed -n 's/^ampFactor_=/ampFactor=/p' /dev/.vr25/acc/.batt-interface.sh 2>/dev/null || :
            fi
            sed -n 's/^idleThreshold=/idleThreshold=/p' /data/adb/vr25/acc-data/config.txt 2>/dev/null || :
        """.trimIndent()
        val result = RootShell.execScript(script, 5)
        if (!result.isSuccess) return@withContext BatteryPowerSnapshot()
        val values = result.out.mapNotNull { line ->
            val parts = line.split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
        BatteryPowerSnapshot.from(values)
    }
}
