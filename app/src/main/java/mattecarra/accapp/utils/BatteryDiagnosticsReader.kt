package mattecarra.accapp.utils

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mattecarra.accapp.models.BatteryDiagnostics

object BatteryDiagnosticsReader {
    suspend fun read(accCycles: Int?, androidCycles: Int?): BatteryDiagnostics = withContext(Dispatchers.IO) {
        val script = """
            for source in battery bms; do
                for field in cycle_count charge_full charge_full_design; do
                    value=${'$'}(cat /sys/class/power_supply/${'$'}source/${'$'}field 2>/dev/null) || continue
                    printf '%s.%s=%s\n' "${'$'}source" "${'$'}field" "${'$'}value"
                done
            done
        """.trimIndent() + if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) "\n" + """

            value=${'$'}(cat /sys/class/power_supply/battery/fg_cycle 2>/dev/null) && printf 'samsung.fg_cycle=%s\n' "${'$'}value"
        """.trimIndent() else ""
        val values = RootShell.execScript(script, 5).out.mapNotNull { line ->
            val parts = line.split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
        BatteryDiagnostics.from(values, accCycles, androidCycles)
    }
}
