package mattecarra.accapp.models

data class BatteryDiagnostics(
    val cycles: Int? = null,
    val fullCapacityMah: Int? = null,
    val designCapacityMah: Int? = null
) {
    companion object {
        fun from(values: Map<String, String>, accCycles: Int?, androidCycles: Int?): BatteryDiagnostics {
            fun nonNegative(key: String) = values[key]?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
            fun positive(vararg keys: String) = keys.firstNotNullOfOrNull { key ->
                values[key]?.trim()?.toLongOrNull()?.takeIf { it > 0 }
            }
            val cycles = nonNegative("battery.cycle_count") ?: nonNegative("bms.cycle_count") ?:
                accCycles?.takeIf { it >= 0 } ?: nonNegative("samsung.fg_cycle") ?:
                androidCycles?.takeIf { it > 0 }
            fun mah(raw: Long?): Int? = raw?.div(1000)?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
            return BatteryDiagnostics(cycles,
                mah(positive("battery.charge_full", "bms.charge_full")),
                mah(positive("battery.charge_full_design", "bms.charge_full_design")))
        }
    }
}
