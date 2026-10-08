package mattecarra.accapp.fragments

import android.os.Bundle
import android.os.BatteryManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.Toast
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import kotlinx.coroutines.launch
import mattecarra.accapp.Preferences
import mattecarra.accapp.R
import mattecarra.accapp.acc.Acc
import mattecarra.accapp.activities.MainActivity
import mattecarra.accapp.databinding.DashboardFragmentBinding
import mattecarra.accapp.databinding.EditChargingLimitOnceDialogBinding
import mattecarra.accapp.models.BatteryPowerState
import mattecarra.accapp.models.BatteryPowerMode
import mattecarra.accapp.models.DashboardValues
import mattecarra.accapp.CurrentUnit
import java.text.NumberFormat
import kotlin.math.abs
import mattecarra.accapp.utils.LogExt
import mattecarra.accapp.utils.ScopedFragment
import mattecarra.accapp.viewmodel.DashboardViewModel
import mattecarra.accapp.viewmodel.SharedViewModel

class DashboardFragment : ScopedFragment()
{

    private lateinit var binding :DashboardFragmentBinding

    private val LOG_TAG = "DashboardFragment"

    private val PERMISSION_REQUEST: Int = 0
    private val ACC_CONFIG_EDITOR_REQUEST: Int = 1
    private val ACC_PROFILE_CREATOR_REQUEST: Int = 2
    private val ACC_PROFILE_EDITOR_REQUEST: Int = 3
    private val ACC_PROFILE_SCHEDULER_REQUEST: Int = 4

    companion object
    {
        fun newInstance() = DashboardFragment()
    }

    private val mViewModel: DashboardViewModel by activityViewModels()
    private lateinit var mDashboardConfigFrg: DashboardConfigFragment
    private lateinit var configViewModel: SharedViewModel
    private lateinit var preferences: Preferences
    private var mIsDaemonRunning: Boolean? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        binding = DashboardFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        LogExt().d(javaClass.simpleName, "onViewCreated()")

        super.onViewCreated(view, savedInstanceState)
        preferences = Preferences(requireContext())

        //-----------------------------------------------------------------

        mDashboardConfigFrg = childFragmentManager.findFragmentById(R.id.current_profile)
            as? DashboardConfigFragment ?: DashboardConfigFragment.newInstance().also {
                childFragmentManager.beginTransaction().replace(R.id.current_profile, it).commit()
            }

        //-----------------------------------------------------------------

        mViewModel.getDashboardValues().observe(viewLifecycleOwner) { dash ->
            renderEnergy(dash)
            binding.dashBatteryCyclesTextView.text = dash.diagnostics.cycles?.toString()
                ?: getString(R.string.battery_data_unavailable)
            binding.dashBatteryFullCapacityTextView.text = dash.diagnostics.fullCapacityMah?.let {
                getString(R.string.battery_capacity_mah, it)
            } ?: getString(R.string.battery_data_unavailable)
            binding.dashBatteryDesignCapacityTextView.text = dash.diagnostics.designCapacityMah?.let {
                getString(R.string.battery_capacity_mah, it)
            } ?: getString(R.string.battery_data_unavailable)
        }

        activity?.let { it ->

            preferences = Preferences(it)
            configViewModel = ViewModelProvider(it).get(SharedViewModel::class.java)

            binding.dashResetBatteryStatsButton.setOnClickListener {
                (requireActivity() as MainActivity).runAccCommand(R.string.command_reset_stats) {
                    Acc.instance.resetBatteryStats()
                }
            }

            binding.dashEditCargingLimitOnceButton.setOnClickListener {
                val dialog = EditChargingLimitOnceDialogBinding.inflate(layoutInflater)
                MaterialDialog(it.context).show {
                    title(R.string.edit_charging_limit_once_button)
                    message(R.string.edit_charging_limit_once_dialog_msg)
                    cancelOnTouchOutside(false)
                    customView(view=dialog.root)
                    positiveButton(R.string.apply) {
                        val limit = getCustomView().findViewById<NumberPicker>(R.id.charging_limit).value
                        (requireActivity() as MainActivity).runAccCommand(R.string.command_charge_once) {
                            Acc.instance.setChargingLimitForOneCharge(limit)
                        }
                    }
                    negativeButton(android.R.string.cancel) {
                        launch {
                            Toast.makeText(context, R.string.charge_limit_not_applied, Toast.LENGTH_LONG).show()
                        }
                    }
                }

                val picker = dialog.chargingLimit
                picker.maxValue = 100
                picker.minValue = 20
                picker.value = 100
            }
        }

        binding.dashDaemonToggleButton.setOnClickListener {
            val shouldRun = mIsDaemonRunning != true
            val label = if (shouldRun) R.string.command_start_acc else R.string.command_stop_acc
            (requireActivity() as MainActivity).runAccCommand(label) {
                val successful = if (Acc.instance.isAccdRunning() == shouldRun) true
                    else if (shouldRun) Acc.instance.abcStartDaemon() else Acc.instance.abcStopDaemon()
                successful && Acc.instance.isAccdRunning() == shouldRun
            }
        }

        binding.dashDaemonRestartButton.setOnClickListener {
            val dashboard = mViewModel
            if (mIsDaemonRunning == null) {
                (requireActivity() as MainActivity).runAccCommand(R.string.command_read_status) {
                    dashboard.refresh()
                    dashboard.daemonRunning.value != null
                }
            } else {
                (requireActivity() as MainActivity).runAccCommand(R.string.command_restart_acc) {
                    Acc.instance.accRestartDaemon() && Acc.instance.isAccdRunning()
                }
            }
        }

        mViewModel.daemonRunning.observe(viewLifecycleOwner) { running ->
            mIsDaemonRunning = running
            setAccdStatusUi(running)
        }
        (requireActivity() as MainActivity).accCommands.state.observe(viewLifecycleOwner) {
            setAccdStatusUi(mIsDaemonRunning)
        }
    }

    private data class EnergyCopy(val title: Int, val description: Int, val source: Int, val activity: Int)

    private fun renderEnergy(dash: DashboardValues) {
        val info = dash.batteryInfo
        val currentMa = (dash.power.currentMa ?: if (dash.batteryReadingFresh && info.hasCurrentReading)
            info.getCurrentNow(preferences.currentInputUnitOfMeasure) else null)?.takeIf { it.isFinite() }
        val accStatus = if (dash.batteryReadingFresh) info.status else "Unknown"
        val status = if (accStatus.equals("Unknown", true)) dash.power.status ?: accStatus else accStatus
        val plugged = dash.plugged?.let { it != 0 }
        val state = BatteryPowerState.from(status, plugged, currentMa,
            dash.power.status.equals("Full", true) || dash.systemBatteryStatus == BatteryManager.BATTERY_STATUS_FULL,
            dash.power.bypassReported, dash.power.idleThresholdMa)
        val copy = when (state.mode) {
            BatteryPowerMode.CHARGING -> EnergyCopy(R.string.energy_charging, R.string.energy_charging_help,
                R.string.energy_source_charger, R.string.energy_activity_charging)
            BatteryPowerMode.BATTERY_ONLY -> EnergyCopy(R.string.energy_battery, R.string.energy_battery_help,
                R.string.energy_source_battery, R.string.energy_activity_discharging)
            BatteryPowerMode.CONNECTED_DISCHARGING -> EnergyCopy(R.string.energy_connected_discharge,
                R.string.energy_connected_discharge_help, R.string.energy_source_battery_in_use, R.string.energy_activity_discharging)
            BatteryPowerMode.DISCHARGING -> EnergyCopy(R.string.energy_discharging, R.string.energy_discharging_help,
                R.string.energy_source_battery_in_use, R.string.energy_activity_discharging)
            BatteryPowerMode.IDLE_ESTIMATED -> EnergyCopy(R.string.energy_idle, R.string.energy_idle_help,
                R.string.energy_source_probable, if (state.full) R.string.energy_activity_full_residual else R.string.energy_activity_residual)
            BatteryPowerMode.BYPASS_REPORTED -> EnergyCopy(R.string.energy_bypass, R.string.energy_bypass_help,
                R.string.energy_source_reported, if (currentMa == null) R.string.energy_activity_paused else R.string.energy_activity_residual)
            BatteryPowerMode.PAUSED -> EnergyCopy(R.string.energy_paused, R.string.energy_paused_help,
                R.string.energy_not_confirmed, R.string.energy_activity_paused)
            BatteryPowerMode.FULL -> EnergyCopy(R.string.energy_full, R.string.energy_full_help,
                R.string.energy_not_confirmed, R.string.energy_activity_full)
            BatteryPowerMode.UNKNOWN -> EnergyCopy(R.string.energy_unknown, R.string.energy_unknown_help,
                R.string.energy_not_confirmed, R.string.battery_data_unavailable)
        }
        binding.dashBatteryCapacityPBar.isVisible = dash.batteryReadingFresh && info.capacity in 0..100
        binding.dashBatteryCapacityPBar.progress = info.capacity.coerceIn(0, 100)
        binding.dashBatteryStatusTextView.setText(copy.title)
        binding.dashEnergyDescriptionTextView.setText(copy.description)
        binding.dashEnergySourceTextView.setText(copy.source)
        binding.dashBatteryActivityTextView.setText(copy.activity)
        binding.dashBypassModeTextView.setText(when {
            plugged == false -> R.string.energy_bypass_disconnected
            state.mode == BatteryPowerMode.BYPASS_REPORTED -> R.string.energy_bypass_reported
            else -> R.string.energy_not_confirmed
        })
        binding.dashPowerConnectionTextView.setText(when (dash.plugged) {
            0 -> R.string.battery_power_disconnected
            BatteryManager.BATTERY_PLUGGED_AC -> R.string.battery_power_ac
            BatteryManager.BATTERY_PLUGGED_USB -> R.string.battery_power_usb
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.string.battery_power_wireless
            BatteryManager.BATTERY_PLUGGED_DOCK -> R.string.battery_power_dock
            null -> R.string.battery_power_unknown
            else -> R.string.battery_power_connected
        })
        binding.dashChargingSpeedTextView.text = if (currentMa == null) getString(R.string.battery_data_unavailable) else {
            val amperes = preferences.currentOutputUnitOfMeasure == CurrentUnit.A
            val formatter = NumberFormat.getNumberInstance().apply {
                maximumFractionDigits = if (amperes) 3 else 1
                minimumFractionDigits = 0
            }
            val value = formatter.format(abs(currentMa) / if (amperes) 1000 else 1) + if (amperes) " A" else " mA"
            when (state.mode) {
                BatteryPowerMode.CHARGING -> getString(R.string.energy_current_in, value)
                BatteryPowerMode.BATTERY_ONLY, BatteryPowerMode.CONNECTED_DISCHARGING, BatteryPowerMode.DISCHARGING ->
                    getString(R.string.energy_current_out, value)
                BatteryPowerMode.IDLE_ESTIMATED, BatteryPowerMode.BYPASS_REPORTED ->
                    getString(R.string.energy_current_residual, value)
                else -> value
            }
        }
        binding.dashBatteryTemperatureTextView.text = if (dash.batteryReadingFresh && info.temperature >= 0)
            info.getTemperature(preferences.temperatureOutputUnitOfMeasure, true) else getString(R.string.battery_data_unavailable)
        binding.dashBatteryVoltageTextView.text = if (dash.batteryReadingFresh && info.voltageNow > 0)
            info.getVoltageNow(preferences.voltageInputUnitOfMeasure, preferences.voltageOutputUnitOfMeasure, true)
            else getString(R.string.battery_data_unavailable)
        binding.dashBatteryHealthTextView.text = if (!dash.batteryReadingFresh) getString(R.string.battery_data_unavailable) else
            when (info.health.lowercase()) {
                "good" -> getString(R.string.energy_health_good)
                "overheat" -> getString(R.string.energy_health_overheat)
                "dead" -> getString(R.string.energy_health_dead)
                "over voltage", "overvoltage" -> getString(R.string.energy_health_overvoltage)
                "unspecified failure" -> getString(R.string.energy_health_failure)
                "cold" -> getString(R.string.energy_health_cold)
                "unknown" -> getString(R.string.battery_data_unavailable)
                else -> info.health
            }
    }

    fun refreshConfig() {
        if (view != null) (childFragmentManager.findFragmentById(R.id.current_profile) as? DashboardConfigFragment)?.checkProfile()
    }

    private fun setAccdStatusUi(running: Boolean?)
    {
        val command = (requireActivity() as MainActivity).accCommands.state.value
        if (command?.running == true) {
            binding.dashAccdStatusPb.visibility = View.VISIBLE
            binding.dashAccdStatusImageView.visibility = View.GONE
            binding.dashAccdStatusTextView.setText(command.label)
            binding.dashDaemonToggleButton.isEnabled = false
            binding.dashDaemonRestartButton.isEnabled = false
            binding.dashResetBatteryStatsButton.isEnabled = false
            binding.dashEditCargingLimitOnceButton.isEnabled = false
            return
        }
        binding.dashResetBatteryStatsButton.isEnabled = true
        binding.dashEditCargingLimitOnceButton.isEnabled = true
        binding.dashDaemonRestartButton.setText(if (running == null) R.string.retry else R.string.restart)
        if (running == null) {
            binding.dashAccdStatusPb.visibility = View.GONE
            binding.dashAccdStatusImageView.visibility = View.VISIBLE
            binding.dashAccdStatusImageView.setImageResource(R.drawable.ic_outline_error_outline_24px)
            binding.dashAccdStatusTextView.setText(R.string.acc_status_unavailable)
            binding.dashDaemonToggleButton.isEnabled = false
            binding.dashDaemonRestartButton.isEnabled = true
            return
        }

        if (running)
        {
            // Hide progress bar
            binding.dashAccdStatusPb.visibility = View.GONE
            // Show and change icon
            binding.dashAccdStatusImageView.visibility = View.VISIBLE
            binding.dashAccdStatusImageView.imageTintList = android.content.res.ColorStateList.valueOf(
                getColor(requireContext(), R.color.colorSuccessful))
            binding.dashAccdStatusImageView.setImageResource(R.drawable.ic_outline_check_circle_24px)
            binding.dashAccdStatusTextView.setText(R.string.acc_daemon_status_running)
            // Enable buttons
            binding.dashDaemonRestartButton.isEnabled = true
            binding.dashDaemonToggleButton.isEnabled = true
            binding.dashDaemonToggleButton.setIconResource(R.drawable.ic_outline_stop_24px)
            binding.dashDaemonToggleButton.setText(R.string.stop)
        }
        else
        {
            // Hide progress bar
            binding.dashAccdStatusPb.visibility = View.GONE
            // Show and change icon
            binding.dashAccdStatusImageView.visibility = View.VISIBLE
            binding.dashAccdStatusImageView.imageTintList = android.content.res.ColorStateList.valueOf(
                getColor(requireContext(), R.color.color_error))
            binding.dashAccdStatusImageView.setImageResource(R.drawable.ic_outline_error_outline_24px)
            binding.dashAccdStatusTextView.setText(R.string.acc_daemon_status_not_running)
            // Enable buttons
            binding.dashDaemonRestartButton.isEnabled = true
            binding.dashDaemonToggleButton.isEnabled = true
            binding.dashDaemonToggleButton.setIconResource(R.drawable.ic_outline_play_arrow_24px)
            binding.dashDaemonToggleButton.setText(R.string.start)
        }
    }

}
