package mattecarra.accapp.acc.v202107280

import androidx.annotation.WorkerThread
import mattecarra.accapp.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mattecarra.accapp.acc.TemperatureConfig
import mattecarra.accapp.acc.ConfigUpdateResult
import mattecarra.accapp.acc.ConfigUpdater
import mattecarra.accapp.acc.ConfigUpdaterEnable
import mattecarra.accapp.acc._interface.AccInterface
import mattecarra.accapp.models.AccConfig
import mattecarra.accapp.models.BatteryInfo
import java.io.IOException
import mattecarra.accapp.acc.AccOutput
import mattecarra.accapp.acc.NgEngineContract

open class AccHandler(override val version: Int, private val contract: NgEngineContract? = null) : AccInterface {
    override val usesResumeTemperature = contract?.usesResumeTemperature ?: TemperatureConfig.usesResumeTemperature(version)
    // String resources
    private val STRING_UNKNOWN = "Unknown"
    private val STRING_NOT_CHARGING = "Not charging"
    private val STRING_DISCHARGING = "Discharging"
    private val STRING_CHARGING = "Charging"

    // RegEx Values
    // Capacity
    val SHUTDOWN_CAPACITY_REGEXP = """^\s*shutdown_capacity=(-?\d*)""".toRegex(RegexOption.MULTILINE)
    val COOLDOWN_CAPACITY_REGEXP = """^\s*cooldown_capacity=(\d*)""".toRegex(RegexOption.MULTILINE)
    val RESUME_CAPACITY_REGEXP = """^\s*resume_capacity=(\d*)""".toRegex(RegexOption.MULTILINE)
    val PAUSE_CAPACITY_REGEXP = """^\s*pause_capacity=(\d*)""".toRegex(RegexOption.MULTILINE)

    // Cool Down
    val COOLDOWN_TEMP_REGEXP = """^\s*cooldown_temp=(\d*)""".toRegex(RegexOption.MULTILINE)
    val MAX_TEMP_REGEXP = """^\s*max_temp=(\d*)""".toRegex(RegexOption.MULTILINE)
    val RESUME_TEMP_REGEXP = """^\s*resume_temp=(\d+)(r?)\s*$""".toRegex(RegexOption.MULTILINE)
    val MAX_TEMP_PAUSE_REGEXP = """^\s*max_temp_pause=(\d*)""".toRegex(RegexOption.MULTILINE)

    val COOLDOWN_CHARGE_REGEXP = """^\s*cooldown_charge=(\d*)""".toRegex(RegexOption.MULTILINE)
    val COOLDOWN_PAUSE_REGEXP  = """^\s*cooldown_pause=(\d*)""".toRegex(RegexOption.MULTILINE)

    // Plugged/Pause
    val RESET_UNPLUGGED_CONFIG_REGEXP = """^\s*reset_batt_stats_on_unplug=(true|false)""".toRegex(RegexOption.MULTILINE)
    val RESET_ON_PAUSE_CONFIG_REGEXP = """^\s*reset_batt_stats_on_pause=(true|false)""".toRegex(RegexOption.MULTILINE)

    val MAX_CHARGING_VOLTAGE = """^\s*max_charging_voltage=(\d*)""".toRegex(RegexOption.MULTILINE)
    val MAX_CHARGING_CURRENT = """^\s*max_charging_current=(\d*)""".toRegex(RegexOption.MULTILINE)

    val PRIORITIZE_BATTERY_IDLE = """^\s*prioritize_batt_idle_mode=(true|false)""".toRegex(RegexOption.MULTILINE)

    @WorkerThread
    fun parseConfig(config: String): AccConfig {
        // NOTE: newer ACC releases may omit keys this app does not know
        // about yet (or print empty values). Every lookup below degrades to
        // a sane default instead of throwing, so the app shows "disabled"
        // rather than crashing (issues #212, #244).
        val capacityShutdown = SHUTDOWN_CAPACITY_REGEXP.find(config)?.destructured?.component1()
        val capacityCoolDown = COOLDOWN_CAPACITY_REGEXP.find(config)?.destructured?.component1()
        val capacityResume   = RESUME_CAPACITY_REGEXP.find(config)?.destructured?.component1()
        val capacityPause    = PAUSE_CAPACITY_REGEXP.find(config)?.destructured?.component1()

        val temperatureCooldown = COOLDOWN_TEMP_REGEXP.find(config)?.destructured?.component1()
        val temperatureMax      = MAX_TEMP_REGEXP.find(config)?.destructured?.component1()
        val resumeTemperature = RESUME_TEMP_REGEXP.find(config)
        val waitSeconds         = MAX_TEMP_PAUSE_REGEXP.find(config)?.destructured?.component1()

        val coolDownChargeSeconds = COOLDOWN_CHARGE_REGEXP.find(config)?.destructured?.component1()?.toIntOrNull()
        val coolDownPauseSeconds = COOLDOWN_PAUSE_REGEXP.find(config)?.destructured?.component1()?.toIntOrNull()

        val maxChargingVoltage = MAX_CHARGING_VOLTAGE.find(config)?.destructured?.component1()
        val maxChargingCurrent = MAX_CHARGING_CURRENT.find(config)?.destructured?.component1()

        val shutdown = capacityShutdown?.toIntOrNull() ?: 0
        val resume = capacityResume?.toIntOrNull() ?: 80
        // Keep the resume < pause invariant even with partial configs.
        val pause = capacityPause?.toIntOrNull()?.takeIf { it > resume } ?: (resume + 10).coerceAtMost(100)

        return AccConfig(
            AccConfig.ConfigCapacity(shutdown, resume, pause),
            AccConfig.ConfigVoltage(null, maxChargingVoltage?.toIntOrNull()),
            maxChargingCurrent?.toIntOrNull(),
            AccConfig.ConfigTemperature(temperatureCooldown?.toIntOrNull() ?: 90,
                temperatureMax?.toIntOrNull() ?: 95,
                if (usesResumeTemperature) 90 else waitSeconds?.toIntOrNull() ?: 90,
                if (usesResumeTemperature)
                    resumeTemperature?.groupValues?.get(1)?.toIntOrNull() ?: 45 else null,
                usesResumeTemperature && resumeTemperature?.groupValues?.get(2) == "r"),
            getOnBoot(config),
            getOnPlugged(config),
            if(coolDownChargeSeconds != null && coolDownPauseSeconds != null && capacityCoolDown?.toIntOrNull() != null)
                AccConfig.ConfigCoolDown(capacityCoolDown.toInt(), coolDownChargeSeconds, coolDownPauseSeconds)
            else null,
            getResetUnplugged(config),
            getResetOnPause(config),
            getCurrentChargingSwitch(config),
            isAutomaticSwitchEnabled(config),
            isPrioritizeBatteryIdleMode(config)
        )
    }

    override suspend fun readConfig(): AccConfig = withContext(Dispatchers.IO) {
        parseConfig(readConfigToString())
    }

    override suspend fun readDefaultConfig(): AccConfig = withContext(Dispatchers.IO) {
        val defaultConfig = RootShell.exec("/dev/.vr25/acc/acca --set --print-default").out.joinToString(separator = "\n")

        parseConfig(defaultConfig)
    }

    @Throws(IOException::class)
    @WorkerThread
    open fun readConfigToString(): String {
        val result = RootShell.exec("/dev/.vr25/acc/acca --set --print")
        if (!result.isSuccess || result.out.isEmpty()) throw IOException("Unable to read ACC configuration")
        return result.out.joinToString(separator = "\n")
    }

    // Returns OnBoot value
    private fun getOnBoot(config: CharSequence) : String? {
        return AccOutput.configValue(config, "apply_on_boot")
    }

    // Returns OnPlugged value
    private fun getOnPlugged(config: CharSequence) : String? {
        return AccOutput.configValue(config, "apply_on_plug")
    }

    // Returns ResetUnplugged value
    private fun getResetUnplugged(config: CharSequence) : Boolean {
        return RESET_UNPLUGGED_CONFIG_REGEXP.find(config)?.destructured?.component1() == "true"
    }

    // Returns ResetOnPause value
    private fun getResetOnPause(config: CharSequence) : Boolean {
        return RESET_ON_PAUSE_CONFIG_REGEXP.find(config)?.destructured?.component1() == "true"
    }

    override suspend fun listVoltageSupportedControlFiles(): List<String> = withContext(Dispatchers.IO) {
        val res = RootShell.exec("/dev/.vr25/acc/acca -v :")

        if(res.isSuccess)
            res.out.filter { it.isNotEmpty() }
        else
            emptyList()
    }

    override suspend fun resetBatteryStats(): Boolean = withContext(Dispatchers.IO) {
        RootShell.exec("/dev/.vr25/acc/acca -R").isSuccess
    }

    /**
     * Regex for acc -i (info)
     */
    // Regex for determining NAME of BATTERY
    private val NAME_REGEXP = """^\s*NAME=([^\r\n]+)""".toRegex(RegexOption.MULTILINE)
    // Regex for INPUT_SUSPEND
    private val INPUT_SUSPEND_REGEXP = """^\s*INPUT_SUSPEND=([01])\s*$""".toRegex(RegexOption.MULTILINE)
    private val STATUS_REGEXP = """^\s*STATUS=(${STRING_CHARGING}|${STRING_DISCHARGING}|${STRING_NOT_CHARGING}|Full|Idle|Unknown)\s*$""".toRegex(RegexOption.MULTILINE)
    private val HEALTH_REGEXP = """^\s*HEALTH=([^\r\n]+)""".toRegex(RegexOption.MULTILINE)
    // Regex for PRESENT value
    private val PRESENT_REGEXP = """^\s*PRESENT=(\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for determining CHARGE_TYPE
    private val CHARGE_TYPE_REGEXP = """^\s*CHARGE_TYPE=([^\r\n]+)""".toRegex(RegexOption.MULTILINE)
    // Regex for battery CAPACITY
    private val CAPACTIY_REGEXP = """^\s*CAPACITY=(\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for CHARGER_TEMP
    private val CHARGER_TEMP_REGEXP = """^\s*CHARGER_TEMP=(-?\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for CHARGER_TEMP_MAX
    private val CHARGER_TEMP_MAX_REGEXP = """^\s*CHARGER_TEMP_MAX=(\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for INPUT_CURRENT_LIMITED, 0 = false, 1 = true
    private val INPUT_CURRENT_LIMITED_REGEXP = """^\s*INPUT_CURRENT_LIMITED=([01])\s*$""".toRegex(RegexOption.MULTILINE)
    private val VOLTAGE_NOW_REGEXP = """^\s*VOLTAGE_NOW=([+-]?([0-9]*[.])?[0-9]+)\s*V?\s*$""".toRegex(RegexOption.MULTILINE)
    // Regex for VOLTAGE_MAX
    private val VOLTAGE_MAX_REGEXP = """^\s*VOLTAGE_MAX=(\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for VOLTAGE_QNOVO
    private val VOLTAGE_QNOVO_REGEXP = """^\s*VOLTAGE_QNOVO=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val CURRENT_NOW_REGEXP = """^\s*CURRENT_NOW=([+-]?([0-9]*[.])?[0-9]+)\s*A?\s*$""".toRegex(RegexOption.MULTILINE)
    // Regex for CURRENT_QNOVO
    private val CURRENT_QNOVO_REGEXP = """^\s*CURRENT_QNOVO=(-?\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for CONSTANT_CHARGE_CURRENT_MAX
    private val CONSTANT_CHARGE_CURRENT_MAX_REGEXP = """^\s*CONSTANT_CHARGE_CURRENT_MAX=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val TEMP_REGEXP = """^\s*TEMP=(-?\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for remaining 'acc -i' values
    private val TECHNOLOGY_REGEXP = """^\s*TECHNOLOGY=([a-zA-Z\-]+)""".toRegex(RegexOption.MULTILINE)
    private val STEP_CHARGING_ENABLED_REGEXP = """^\s*STEP_CHARGING_ENABLED=([01])\s*$""".toRegex(RegexOption.MULTILINE)
    private val SW_JEITA_ENABLED_REGEXP = """^\s*SW_JEITA_ENABLED=([01])\s*$""".toRegex(RegexOption.MULTILINE)
    private val TAPER_CONTROL_ENABLED_REGEXP = """^\s*TAPER_CONTROL_ENABLED=([01])\s*$""".toRegex(RegexOption.MULTILINE)
    // CHARGE_DISABLE is true when ACC disables charging due to conditions
    private val CHARGE_DISABLE_REGEXP = """^\s*CHARGE_DISABLE=([01])\s*$""".toRegex(RegexOption.MULTILINE)
    // CHARGE_DONE is true when the battery is done charging.
    private val CHARGE_DONE_REGEXP = """^\s*CHARGE_DONE=([01])\s*$""".toRegex(RegexOption.MULTILINE)
    private val PARALLEL_DISABLE_REGEXP = """^\s*PARALLEL_DISABLE=([01])\s*$""".toRegex(RegexOption.MULTILINE)
    private val SET_SHIP_MODE_REGEXP = """^\s*SET_SHIP_MODE=([01])\s*$""".toRegex(RegexOption.MULTILINE)
    private val DIE_HEALTH_REGEXP = """^\s*DIE_HEALTH=([^\r\n]+)""".toRegex(RegexOption.MULTILINE)
    private val RERUN_AICL_REGEXP = """^\s*RERUN_AICL=([01])\s*$""".toRegex(RegexOption.MULTILINE)
    private val DP_DM_REGEXP = """^\s*DP_DM=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val CHARGE_CONTROL_LIMIT_MAX_REGEXP = """^\s*CHARGE_CONTROL_LIMIT_MAX=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val CHARGE_CONTROL_LIMIT_REGEXP = """^\s*CHARGE_CONTROL_LIMIT=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val CHARGE_COUNTER_REGEXP = """^\s*CHARGE_COUNTER=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val INPUT_CURRENT_MAX_REGEXP = """^\s*INPUT_CURRENT_MAX=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val CYCLE_COUNT_REGEXP = """^\s*CYCLE_COUNT=(\d+)""".toRegex(RegexOption.MULTILINE)

    private val POWER_NOW_REGEXP = """^\s*POWER_NOW=([+-]?([0-9]*[.])?[0-9]+)\s*W?\s*$""".toRegex(RegexOption.MULTILINE)

    override suspend fun getBatteryInfo(): BatteryInfo = withContext(Dispatchers.IO) {
        val result = RootShell.exec("/dev/.vr25/acc/acca -i")
        if (!result.isSuccess || result.out.isEmpty()) throw IOException("Unable to read battery information")
        parseBatteryInfo(result.out.joinToString(separator = "\n"))
    }

    fun parseBatteryInfo(info: String): BatteryInfo {
        // ACC >= 2022 prints CURRENT_NOW/VOLTAGE_NOW/POWER_NOW in A/V/W
        // (e.g. -0.19, 4.34, -0.82) while older releases used µA/µV/µW.
        // Normalise to the legacy micro scales the rest of the app expects
        // (issue #243: wrong battery readings with new ACC).
        val rawVoltage = VOLTAGE_NOW_REGEXP.findAll(info).lastOrNull()?.destructured?.component1()?.toFloatOrNull() ?: 0f
        val voltageNow = if (contract?.supportsConfig == true || (rawVoltage != 0f && kotlin.math.abs(rawVoltage) < 10000f)) rawVoltage * 1000000f else rawVoltage
        val rawCurrent = CURRENT_NOW_REGEXP.findAll(info).lastOrNull()?.destructured?.component1()?.toFloatOrNull() ?: 0f
        val currentNow = if (contract?.supportsConfig == true || (rawCurrent != 0f && kotlin.math.abs(rawCurrent) < 10000f)) rawCurrent * 1000000f else rawCurrent
        val rawPower = POWER_NOW_REGEXP.findAll(info).lastOrNull()?.destructured?.component1()?.toFloatOrNull() ?: 0f
        val powerNow = if (contract?.supportsConfig == true || (rawPower != 0f && kotlin.math.abs(rawPower) < 10000f)) rawPower * 1000000f else rawPower

        return BatteryInfo(
            NAME_REGEXP.find(info)?.destructured?.component1()?.trim() ?: STRING_UNKNOWN,
            INPUT_SUSPEND_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let { // A kernel flag is enabled when its value is 1
                it == 1
            },
            STATUS_REGEXP.find(info)?.destructured?.component1() ?: STRING_UNKNOWN,
            HEALTH_REGEXP.find(info)?.destructured?.component1()?.trim() ?: STRING_UNKNOWN,
            PRESENT_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            CHARGE_TYPE_REGEXP.find(info)?.destructured?.component1()?.trim() ?: STRING_UNKNOWN,
            CAPACTIY_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            CHARGER_TEMP_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull()?.let { it/10 } ?: -1,
            CHARGER_TEMP_MAX_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull()?.let { it/10 } ?: -1,
            INPUT_CURRENT_LIMITED_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 1
            },
            voltageNow,
            VOLTAGE_MAX_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            VOLTAGE_QNOVO_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            currentNow,
            CURRENT_QNOVO_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            CONSTANT_CHARGE_CURRENT_MAX_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            TEMP_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull()?.let { it/10 } ?: -1,
            TECHNOLOGY_REGEXP.find(info)?.destructured?.component1() ?: STRING_UNKNOWN,
            STEP_CHARGING_ENABLED_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 1
            },
            SW_JEITA_ENABLED_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 1
            },
            TAPER_CONTROL_ENABLED_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 1
            },
            CHARGE_DISABLE_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 1
            },
            CHARGE_DONE_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 1
            },
            PARALLEL_DISABLE_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 1
            },
            SET_SHIP_MODE_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 1
            },
            DIE_HEALTH_REGEXP.find(info)?.destructured?.component1()?.trim() ?: STRING_UNKNOWN,
            RERUN_AICL_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 1
            },
            DP_DM_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 1
            },
            CHARGE_CONTROL_LIMIT_MAX_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            CHARGE_CONTROL_LIMIT_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            INPUT_CURRENT_MAX_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            CYCLE_COUNT_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            powerNow,
            hasCurrentReading = CURRENT_NOW_REGEXP.find(info) != null
        )
    }

    override suspend fun isBatteryCharging(): Boolean = withContext(Dispatchers.IO) {
        STATUS_REGEXP
            .find(
                RootShell.exec("/dev/.vr25/acc/acca -i").out.joinToString("\n")
            )?.destructured?.component1() == STRING_CHARGING
    }

    override suspend fun isAccdRunning(): Boolean = withContext(Dispatchers.IO) {
        mattecarra.accapp.acc.ModernAccDaemon.isRunning()
    }

    override suspend fun abcStartDaemon(): Boolean = withContext(Dispatchers.IO) {
        mattecarra.accapp.acc.ModernAccDaemon.start()
    }

    override fun getAccRestartDaemon(): String =  "/dev/.vr25/acc/acca -D restart"

    override suspend fun accRestartDaemon(): Boolean = mattecarra.accapp.acc.ModernAccDaemon.restart()

    override suspend fun abcStopDaemon(): Boolean = withContext(Dispatchers.IO) {
        mattecarra.accapp.acc.ModernAccDaemon.stop()
    }

    //Charging switches
    override suspend fun listChargingSwitches(): List<String> = withContext(Dispatchers.IO) {
        val res = RootShell.exec("/dev/.vr25/acc/acca -s s:")

        AccOutput.switches(res.code, res.out.joinToString("\n"))
    }

    override suspend fun testChargingSwitch(chargingSwitch: String?): Int = withContext(Dispatchers.IO) {
        // Switch tests sleep and poll hardware: allow a full minute.
        RootShell.exec("/dev/.vr25/acc/acca -t${chargingSwitch?.let{" $it"} ?: ""}", RootShell.LONG_TIMEOUT_SECS).code
    }

    override fun getCurrentChargingSwitch(config: String): String? {
        return AccOutput.configValue(config, "charging_switch")?.removeSuffix(" --")?.trim()?.ifBlank { null }
    }

    override fun isAutomaticSwitchEnabled(config: String): Boolean {
        return AccOutput.configValue(config, "charging_switch")?.endsWith(" --") != true
    }

    override fun isPrioritizeBatteryIdleMode(config: String): Boolean {
        return PRIORITIZE_BATTERY_IDLE.find(config)?.destructured?.component1()?.toBoolean() ?: false
    }

    override suspend fun setChargingLimitForOneCharge(limit: Int): Boolean = withContext(Dispatchers.IO) {
        RootShell.execScript("command -v acc >/dev/null || exit 127; acc -f $limit </dev/null >/dev/null 2>&1 &").isSuccess
    }

    override suspend fun isBatteryIdleSupported(): Pair<Int, Boolean> = withContext(Dispatchers.IO) {
        // The idle probe hangs forever when the battery isn't charging, so
        // don't even run it then: exit code 2 already means "plug in to test"
        // and the UI handles exactly that case.
        if (!isBatteryCharging()) return@withContext Pair(2, false)
        val res = RootShell.exec("/dev/.vr25/acc/acca -t --", RootShell.LONG_TIMEOUT_SECS)
        AccOutput.idleSupported(res.code, res.out.joinToString("\n"))
    }

    //Update config part:

    /**
     * Function takes in AccConfig file and will apply it.
     * @param accConfig Configuration file to apply.
     * @return ConfigUpdateResult data class.
     */
    override suspend fun updateAccConfig(accConfig: AccConfig, cue: ConfigUpdaterEnable): ConfigUpdateResult {
        // ACC discovers current-control files before processing a reset; it
        // can wait for charging even when both current limits are already off.
        return ConfigUpdater(accConfig, cue.skipUnchangedPowerLimits(readConfig(), accConfig))
            .execute(this)
    }

    override fun getUpdateResetUnpluggedCommand(resetUnplugged: Boolean) = "env async=true /dev/.vr25/acc/acca -s reset_batt_stats_on_unplug=$resetUnplugged"

    override fun getUpdateResetOnPauseCommand(resetOnPause: Boolean) = "env async=true /dev/.vr25/acc/acca -s reset_batt_stats_on_pause=$resetOnPause"

    override fun getUpdateAccCoolDownCommand(charge: Int?, pause: Int?): String = "env async=true /dev/.vr25/acc/acca -s cooldown_charge=${charge?.toString().orEmpty()} cooldown_pause=${pause?.toString().orEmpty()}"

    override fun getUpdateAccCapacityCommand(shutdown: Int, coolDown: Int, resume: Int, pause: Int): String = "env async=true /dev/.vr25/acc/acca -s shutdown_capacity=$shutdown cooldown_capacity=$coolDown resume_capacity=$resume pause_capacity=$pause"

    override fun getUpdateAccTemperatureCommand(temperature: AccConfig.ConfigTemperature): String =
        if (usesResumeTemperature) TemperatureConfig.command(temperature)
        else getUpdateAccTemperatureCommand(temperature.coolDownTemperature, temperature.maxTemperature, temperature.pause)

    override fun getUpdateAccTemperatureCommand(coolDownTemperature: Int, temperatureMax: Int, wait: Int): String = "env async=true /dev/.vr25/acc/acca -s cooldown_temp=${coolDownTemperature} max_temp=${temperatureMax} max_temp_pause=$wait"

    override fun getUpdateAccVoltControlCommand(voltFile: String?, voltMax: Int?): String = "env async=true /dev/.vr25/acc/acca --set --voltage ${voltMax?.toString() ?: "-"}"

    override fun getUpdateAccCurrentMaxCommand(currMax: Int?): String = "env async=true /dev/.vr25/acc/acca --set --current ${currMax?.toString() ?: "-"}"

    override fun getUpdateAccOnBootExitCommand(enabled: Boolean): String = "" //Not supported

    override fun getUpdateAccOnBootCommand(command: String?): String = "env async=true /dev/.vr25/acc/acca -s ${RootShell.quote("apply_on_boot=${command.orEmpty()}")}"


    override fun getUpdateAccOnPluggedCommand(command: String?) : String = "env async=true /dev/.vr25/acc/acca -s ${RootShell.quote("apply_on_plug=${command.orEmpty()}")}"

    override fun getUpdateAccChargingSwitchCommand(switch: String?, automaticSwitchingEnabled: Boolean) : String {
        return if(switch != null) {
            "env async=true /dev/.vr25/acc/acca -s \"charging_switch=${switch}${if (automaticSwitchingEnabled) "" else " --"}\""
        } else {
            "env async=true /dev/.vr25/acc/acca -s \"charging_switch=\""
        }
    }

    override fun getUpgradeCommand(version: String) = "/dev/.vr25/acc/acca --upgrade $version"

    override fun getUpdatePrioritizeBatteryIdleModeCommand(enabled: Boolean): String = "env async=true /dev/.vr25/acc/acca --set prioritize_batt_idle_mode=$enabled"

    override fun getAddChargingSwitchCommand(switch: String): String = getUpdateAccChargingSwitchCommand(switch, false)
}
