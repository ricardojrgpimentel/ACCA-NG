package mattecarra.accapp.models

data class DashboardValues(
    var batteryInfo: BatteryInfo,
    var daemon: Boolean?,
    val plugged: Int? = null,
    val systemBatteryStatus: Int? = null,
    val diagnostics: BatteryDiagnostics = BatteryDiagnostics(),
    val power: BatteryPowerSnapshot = BatteryPowerSnapshot(),
    val batteryReadingFresh: Boolean = true
) {
}
