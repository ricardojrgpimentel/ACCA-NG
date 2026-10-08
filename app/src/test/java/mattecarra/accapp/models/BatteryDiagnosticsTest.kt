package mattecarra.accapp.models

import org.junit.Assert.*
import org.junit.Test

class BatteryDiagnosticsTest {
    @Test fun convertsReportedMicroampHoursWithoutUsingCurrentChargeAsCapacity() {
        val result = BatteryDiagnostics.from(mapOf("battery.charge_full" to "3241000",
            "battery.charge_full_design" to "4100000", "battery.charge_counter" to "1600000"), null, null)
        assertEquals(3241, result.fullCapacityMah)
        assertEquals(4100, result.designCapacityMah)
    }

    @Test fun invalidAndMissingValuesStayUnavailable() {
        val result = BatteryDiagnostics.from(mapOf("battery.cycle_count" to "-1",
            "battery.charge_full" to "N/A", "battery.charge_full_design" to "0"), -1, 0)
        assertEquals(BatteryDiagnostics(), result)
        assertEquals(BatteryDiagnostics(), BatteryDiagnostics.from(emptyMap(), null, null))
    }

    @Test fun keepsARealZeroCycleCountAndPrefersTheStandardCounter() {
        assertEquals(0, BatteryDiagnostics.from(mapOf("battery.cycle_count" to "0",
            "samsung.fg_cycle" to "1"), 25, 50).cycles)
    }

    @Test fun fallsBackToOtherReportedCounters() {
        assertEquals(250, BatteryDiagnostics.from(mapOf("battery.cycle_count" to "-1",
            "bms.cycle_count" to "250"), null, null).cycles)
        assertEquals(1, BatteryDiagnostics.from(mapOf("samsung.fg_cycle" to "1"), -1, 0).cycles)
        assertEquals(150, BatteryDiagnostics.from(emptyMap(), -1, 150).cycles)
    }
}
