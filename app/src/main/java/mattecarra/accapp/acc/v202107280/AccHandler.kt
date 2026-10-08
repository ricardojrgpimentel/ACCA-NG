package mattecarra.accapp.acc.v202107280

import androidx.annotation.WorkerThread
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mattecarra.accapp.acc.ConfigUpdateResult
import mattecarra.accapp.acc.ConfigUpdater
import mattecarra.accapp.acc.ConfigUpdaterEnable
import mattecarra.accapp.acc._interface.AccInterface
import mattecarra.accapp.models.AccConfig
import mattecarra.accapp.models.BatteryInfo
import java.io.IOException
import java.util.regex.Pattern

open class AccHandler(override val version: Int) : AccInterface {
    // String resources
    private val STRING_UNKNOWN = "Unknown"
    private val STRING_NOT_CHARGING = "Not charging"
    private val STRING_DISCHARGING = "Discharging"
    private val STRING_CHARGING = "Charging"

    // RegEx Values
    // Capacity
    val SHUTDOWN_CAPACITY_REGEXP = """^\s*shutdown_capacity=(\d*)""".toRegex(RegexOption.MULTILINE)
    val COOLDOWN_CAPACITY_REGEXP = """^\s*cooldown_capacity=(\d*)""".toRegex(RegexOption.MULTILINE)
    val RESUME_CAPACITY_REGEXP = """^\s*resume_capacity=(\d*)""".toRegex(RegexOption.MULTILINE)
    val PAUSE_CAPACITY_REGEXP = """^\s*pause_capacity=(\d*)""".toRegex(RegexOption.MULTILINE)

    // Cool Down
    val COOLDOWN_TEMP_REGEXP = """^\s*cooldown_temp=(\d*)""".toRegex(RegexOption.MULTILINE)
    val MAX_TEMP_REGEXP = """^\s*max_temp=(\d*)""".toRegex(RegexOption.MULTILINE)
    val MAX_TEMP_PAUSE_REGEXP = """^\s*max_temp_pause=(\d*)""".toRegex(RegexOption.MULTILINE)

    val COOLDOWN_CHARGE_REGEXP = """^\s*cooldown_charge=(\d*)""".toRegex(RegexOption.MULTILINE)
    val COOLDOWN_PAUSE_REGEXP  = """^\s*cooldown_pause=(\d*)""".toRegex(RegexOption.MULTILINE)

    // Plugged/Pause
    val RESET_UNPLUGGED_CONFIG_REGEXP = """^\s*reset_batt_stats_on_unplug=(true|false)""".toRegex(RegexOption.MULTILINE)
    val RESET_ON_PAUSE_CONFIG_REGEXP = """^\s*reset_batt_stats_on_pause=(true|false)""".toRegex(RegexOption.MULTILINE)
    val ON_BOOT = """^\s*apply_on_boot=((?:(?!#).)*)""".toRegex(RegexOption.MULTILINE)
    val ON_PLUGGED = """^\s*apply_on_plug=((?:(?!#).)*)""".toRegex(RegexOption.MULTILINE)

    val MAX_CHARGING_VOLTAGE = """^\s*max_charging_voltage=(\d*)""".toRegex(RegexOption.MULTILINE)
    val MAX_CHARGING_CURRENT = """^\s*max_charging_current=(\d*)""".toRegex(RegexOption.MULTILINE)

    val SWITCH = """^\s*charging_switch=((?:(?!#).)*)""".toRegex(RegexOption.MULTILINE)
    val AUTOMATIC_SWITCH_DISABLED = """^(.*) --\s*""".toRegex(RegexOption.MULTILINE)
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
                waitSeconds?.toIntOrNull() ?: 90),
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
        val defaultConfig = Shell.su("/dev/.vr25/acc/acca --set --print-default").exec().out.joinToString(separator = "\n")

        parseConfig(defaultConfig)
    }

    @Throws(IOException::class)
    @WorkerThread
    open fun readConfigToString(): String {
        return Shell.su("/dev/.vr25/acc/acca --set --print").exec().out.joinToString(separator = "\n")
    }

    // Returns OnBoot value
    private fun getOnBoot(config: CharSequence) : String? {
        return ON_BOOT.find(config)?.destructured?.component1()?.trim()?.ifBlank { null }
    }

    // Returns OnPlugged value
    private fun getOnPlugged(config: CharSequence) : String? {
        return ON_PLUGGED.find(config)?.destructured?.component1()?.trim()?.ifBlank { null }
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
        val res = Shell.su("/dev/.vr25/acc/acca -v :").exec()

        if(res.isSuccess)
            res.out.filter { it.isNotEmpty() }
        else
            emptyList()
    }

    override suspend fun resetBatteryStats(): Boolean = withContext(Dispatchers.IO) {
        Shell.su("/dev/.vr25/acc/acca -R").exec().isSuccess
    }

    /**
     * Regex for acc -i (info)
     */
    // Regex for determining NAME of BATTERY
    private val NAME_REGEXP = """^\s*NAME=([a-zA-Z0-9]+)""".toRegex(RegexOption.MULTILINE)
    // Regex for INPUT_SUSPEND
    private val INPUT_SUSPEND_REGEXP = """^\s*INPUT_SUSPEND=([01])""".toRegex(RegexOption.MULTILINE)
    private val STATUS_REGEXP = """^\s*STATUS=(${STRING_CHARGING}|${STRING_DISCHARGING}|${STRING_NOT_CHARGING})""".toRegex(RegexOption.MULTILINE)
    private val HEALTH_REGEXP = """^\s*HEALTH=([a-zA-Z]+)""".toRegex(RegexOption.MULTILINE)
    // Regex for PRESENT value
    private val PRESENT_REGEXP = """^\s*PRESENT=(\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for determining CHARGE_TYPE
    private val CHARGE_TYPE_REGEXP = """^\s*CHARGE_TYPE=(N/A|[a-zA-Z]+)""".toRegex(RegexOption.MULTILINE)
    // Regex for battery CAPACITY
    private val CAPACTIY_REGEXP = """^\s*CAPACITY=(\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for CHARGER_TEMP
    private val CHARGER_TEMP_REGEXP = """^\s*CHARGER_TEMP=(\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for CHARGER_TEMP_MAX
    private val CHARGER_TEMP_MAX_REGEXP = """^\s*CHARGER_TEMP_MAX=(\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for INPUT_CURRENT_LIMITED, 0 = false, 1 = true
    private val INPUT_CURRENT_LIMITED_REGEXP = """^\s*INPUT_CURRENT_LIMITED=([01])""".toRegex(RegexOption.MULTILINE)
    private val VOLTAGE_NOW_REGEXP = """^\s*VOLTAGE_NOW=([+-]?([0-9]*[.])?[0-9]+)""".toRegex(RegexOption.MULTILINE)
    // Regex for VOLTAGE_MAX
    private val VOLTAGE_MAX_REGEXP = """^\s*VOLTAGE_MAX=(\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for VOLTAGE_QNOVO
    private val VOLTAGE_QNOVO_REGEXP = """^\s*VOLTAGE_QNOVO=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val CURRENT_NOW_REGEXP = """^\s*CURRENT_NOW=([+-]?([0-9]*[.])?[0-9]+)""".toRegex(RegexOption.MULTILINE)
    // Regex for CURRENT_QNOVO
    private val CURRENT_QNOVO_REGEXP = """^\s*CURRENT_NOW=(-?\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for CONSTANT_CHARGE_CURRENT_MAX
    private val CONSTANT_CHARGE_CURRENT_MAX_REGEXP = """^\s*CONSTANT_CHARGE_CURRENT_MAX=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val TEMP_REGEXP = """^\s*TEMP=(\d+)""".toRegex(RegexOption.MULTILINE)
    // Regex for remaining 'acc -i' values
    private val TECHNOLOGY_REGEXP = """^\s*TECHNOLOGY=([a-zA-Z\-]+)""".toRegex(RegexOption.MULTILINE)
    private val STEP_CHARGING_ENABLED_REGEXP = """^\s*STEP_CHARGING_ENABLED=([01])""".toRegex(RegexOption.MULTILINE)
    private val SW_JEITA_ENABLED_REGEXP = """^\s*SW_JEITA_ENABLED=([01])""".toRegex(RegexOption.MULTILINE)
    private val TAPER_CONTROL_ENABLED_REGEXP = """^\s*TAPER_CONTROL_ENABLED=([01])""".toRegex(RegexOption.MULTILINE)
    // CHARGE_DISABLE is true when ACC disables charging due to conditions
    private val CHARGE_DISABLE_REGEXP = """^\s*TAPER_CONTROL_ENABLED=([01])""".toRegex(RegexOption.MULTILINE)
    // CHARGE_DONE is true when the battery is done charging.
    private val CHARGE_DONE_REGEXP = """^\s*CHARGE_DONE=([01])""".toRegex(RegexOption.MULTILINE)
    private val PARALLEL_DISABLE_REGEXP = """^\s*PARALLEL_DISABLE=([01])""".toRegex(RegexOption.MULTILINE)
    private val SET_SHIP_MODE_REGEXP = """^\s*SET_SHIP_MODE=([01])""".toRegex(RegexOption.MULTILINE)
    private val DIE_HEALTH_REGEXP = """^\s*DIE_HEALTH=([a-zA-Z]+)""".toRegex(RegexOption.MULTILINE)
    private val RERUN_AICL_REGEXP = """^\s*RERUN_AICL=([01])""".toRegex(RegexOption.MULTILINE)
    private val DP_DM_REGEXP = """^\s*DP_DM=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val CHARGE_CONTROL_LIMIT_MAX_REGEXP = """^\s*CHARGE_CONTROL_LIMIT_MAX=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val CHARGE_CONTROL_LIMIT_REGEXP = """^\s*CHARGE_CONTROL_LIMIT=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val CHARGE_COUNTER_REGEXP = """^\s*CHARGE_COUNTER=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val INPUT_CURRENT_MAX_REGEXP = """^\s*INPUT_CURRENT_MAX=(\d+)""".toRegex(RegexOption.MULTILINE)
    private val CYCLE_COUNT_REGEXP = """^\s*CYCLE_COUNT=(\d+)""".toRegex(RegexOption.MULTILINE)

    private val POWER_NOW_REGEXP = """^\s*POWER_NOW=([+-]?([0-9]*[.])?[0-9]+)""".toRegex(RegexOption.MULTILINE)

    override suspend fun getBatteryInfo(): BatteryInfo = withContext(Dispatchers.IO) {
        val info =  Shell.su("/dev/.vr25/acc/acca -i").exec().out.joinToString(separator = "\n")

        // ACC >= 2022 prints CURRENT_NOW/VOLTAGE_NOW/POWER_NOW in A/V/W
        // (e.g. -0.19, 4.34, -0.82) while older releases used µA/µV/µW.
        // Normalise to the legacy micro scales the rest of the app expects
        // (issue #243: wrong battery readings with new ACC).
        val rawVoltage = VOLTAGE_NOW_REGEXP.find(info)?.destructured?.component1()?.toFloatOrNull() ?: 0f
        val voltageNow = if (rawVoltage != 0f && kotlin.math.abs(rawVoltage) < 10000f) rawVoltage * 1000000f else rawVoltage
        val rawCurrent = CURRENT_NOW_REGEXP.find(info)?.destructured?.component1()?.toFloatOrNull() ?: 0f
        val currentNow = if (rawCurrent != 0f && kotlin.math.abs(rawCurrent) < 10000f) rawCurrent * 1000000f else rawCurrent
        val rawPower = POWER_NOW_REGEXP.find(info)?.destructured?.component1()?.toFloatOrNull() ?: 0f
        val powerNow = if (rawPower != 0f && kotlin.math.abs(rawPower) < 10000f) rawPower * 1000000f else rawPower

        BatteryInfo(
            NAME_REGEXP.find(info)?.destructured?.component1() ?: STRING_UNKNOWN,
            INPUT_SUSPEND_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let { // If r == true (input is suspended)
                it == 0
            },
            STATUS_REGEXP.find(info)?.destructured?.component1() ?: STRING_DISCHARGING,
            HEALTH_REGEXP.find(info)?.destructured?.component1() ?: STRING_UNKNOWN,
            PRESENT_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            CHARGE_TYPE_REGEXP.find(info)?.destructured?.component1() ?: STRING_UNKNOWN,
            CAPACTIY_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            CHARGER_TEMP_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull()?.let { it/10 } ?: -1,
            CHARGER_TEMP_MAX_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull()?.let { it/10 } ?: -1,
            INPUT_CURRENT_LIMITED_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 0
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
                it == 0
            },
            SW_JEITA_ENABLED_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 0
            },
            TAPER_CONTROL_ENABLED_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 0
            },
            CHARGE_DISABLE_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 0
            },
            CHARGE_DONE_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 0
            },
            PARALLEL_DISABLE_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 0
            },
            SET_SHIP_MODE_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 0
            },
            DIE_HEALTH_REGEXP.find(info)?.destructured?.component1() ?: STRING_UNKNOWN,
            RERUN_AICL_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 0
            },
            DP_DM_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull().let {
                it == 0
            },
            CHARGE_CONTROL_LIMIT_MAX_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            CHARGE_CONTROL_LIMIT_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            INPUT_CURRENT_MAX_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            CYCLE_COUNT_REGEXP.find(info)?.destructured?.component1()?.toIntOrNull() ?: -1,
            powerNow
        )
    }

    override suspend fun isBatteryCharging(): Boolean = withContext(Dispatchers.IO) {
        STATUS_REGEXP
            .find(
                Shell.su("/dev/.vr25/acc/acca -i").exec().out.joinToString("\n")
            )?.destructured?.component1() == STRING_CHARGING
    }

    override suspend fun isAccdRunning(): Boolean = withContext(Dispatchers.IO) {
        val code = Shell.su("/dev/.vr25/acc/acca -D").exec().code
        code == 0 || code == 8
    }

    override suspend fun abcStartDaemon(): Boolean = withContext(Dispatchers.IO) {
        Shell.su("/dev/.vr25/acc/acca -D start").exec().isSuccess
    }

    override fun getAccRestartDaemon(): String =  "/dev/.vr25/acc/acca -D restart"

    override suspend fun abcStopDaemon(): Boolean = withContext(Dispatchers.IO) {
        // NOTE: the historical "accd." shortcut no longer stops modern ACC
        // (it resolves to acc.sh, which drops into its main loop and hangs
        // forever, leaking a stray daemon process per tap). Use the
        // documented daemon manager instead. Bounded by the global libsu
        // job timeout if the daemon lock is wedged.
        Shell.su("/dev/.vr25/acc/acca -D stop").exec()
        // Report the real end state: ACC's lock/status bookkeeping can go
        // stale, making the stop command "succeed" without effect.
        !isAccdRunning()
    }

    //Charging switches
    override suspend fun listChargingSwitches(): List<String> = withContext(Dispatchers.IO) {
        val res = Shell.su("/dev/.vr25/acc/acca -s s:").exec()

        if(res.isSuccess)
            res.out.map { it.trim() }.filter { it.isNotEmpty() }
        else
            emptyList()
    }

    override suspend fun testChargingSwitch(chargingSwitch: String?): Int = withContext(Dispatchers.IO) {
        Shell.su("/dev/.vr25/acc/acca -t${chargingSwitch?.let{" $it"} ?: ""}").exec().code
    }

    override fun getCurrentChargingSwitch(config: String): String? {
        return SWITCH.find(config)?.destructured?.component1()?.trim()?.ifBlank { null }?.replace(AUTOMATIC_SWITCH_DISABLED, "$1")
    }

    override fun isAutomaticSwitchEnabled(config: String): Boolean {
        return AUTOMATIC_SWITCH_DISABLED.find(config) == null
    }

    override fun isPrioritizeBatteryIdleMode(config: String): Boolean {
        return PRIORITIZE_BATTERY_IDLE.find(config)?.destructured?.component1()?.toBoolean() ?: false
    }

    override suspend fun setChargingLimitForOneCharge(limit: Int): Boolean = withContext(Dispatchers.IO) {
        Shell.su("(acc -f $limit &) &").exec().isSuccess
    }

    val BATTERY_IDLE_SUPPORTED = """^\s*-\s*battIdleMode=true""".toPattern(Pattern.MULTILINE)
    override suspend fun isBatteryIdleSupported(): Pair<Int, Boolean> = withContext(Dispatchers.IO) {
        val res = Shell.su("/dev/.vr25/acc/acca -t --").exec()
        Pair(
            res.code,
            BATTERY_IDLE_SUPPORTED.matcher(res.out.joinToString("\n")).find()
        )
    }

    //Update config part:

    /**
     * Function takes in AccConfig file and will apply it.
     * @param accConfig Configuration file to apply.
     * @return ConfigUpdateResult data class.
     */
    override suspend fun updateAccConfig(accConfig: AccConfig, cue: ConfigUpdaterEnable): ConfigUpdateResult {
        return ConfigUpdater(accConfig, cue)
            .execute(this)
    }

    override fun getUpdateResetUnpluggedCommand(resetUnplugged: Boolean) = "/dev/.vr25/acc/acca -s reset_batt_stats_on_unplug=$resetUnplugged"

    override fun getUpdateResetOnPauseCommand(resetOnPause: Boolean) = "/dev/.vr25/acc/acca -s reset_batt_stats_on_pause=$resetOnPause"

    override fun getUpdateAccCoolDownCommand(charge: Int?, pause: Int?): String = "/dev/.vr25/acc/acca -s cooldown_charge=${charge?.toString().orEmpty()} cooldown_pause=${pause?.toString().orEmpty()}"

    override fun getUpdateAccCapacityCommand(shutdown: Int, coolDown: Int, resume: Int, pause: Int): String = "/dev/.vr25/acc/acca -s shutdown_capacity=$shutdown cooldown_capacity=$coolDown resume_capacity=$resume pause_capacity=$pause"

    override fun getUpdateAccTemperatureCommand(coolDownTemperature: Int, temperatureMax: Int, wait: Int): String = "/dev/.vr25/acc/acca -s cooldown_temp=${coolDownTemperature} max_temp=${temperatureMax} max_temp_pause=$wait"

    override fun getUpdateAccVoltControlCommand(voltFile: String?, voltMax: Int?): String = "/dev/.vr25/acc/acca --set --voltage ${voltMax?.toString() ?: "-"}"

    override fun getUpdateAccCurrentMaxCommand(currMax: Int?): String = "/dev/.vr25/acc/acca --set --current ${currMax?.toString() ?: "-"}"

    override fun getUpdateAccOnBootExitCommand(enabled: Boolean): String = "" //Not supported

    override fun getUpdateAccOnBootCommand(command: String?): String = "/dev/.vr25/acc/acca -s \"apply_on_boot=${command.orEmpty()}\""


    override fun getUpdateAccOnPluggedCommand(command: String?) : String = "/dev/.vr25/acc/acca -s \"apply_on_plug=${command.orEmpty()}\""

    override fun getUpdateAccChargingSwitchCommand(switch: String?, automaticSwitchingEnabled: Boolean) : String {
        return if(switch != null) {
            "/dev/.vr25/acc/acca -s \"charging_switch=${switch}${if (automaticSwitchingEnabled) "" else " --"}\""
        } else {
            "/dev/.vr25/acc/acca -s \"charging_switch=\""
        }
    }

    override fun getUpgradeCommand(version: String) = "/dev/.vr25/acc/acca --upgrade $version"

    override fun getUpdatePrioritizeBatteryIdleModeCommand(enabled: Boolean): String = "/dev/.vr25/acc/acca --set prioritize_batt_idle_mode=$enabled"

    override fun getAddChargingSwitchCommand(switch: String): String = getUpdateAccChargingSwitchCommand(switch, false)
}