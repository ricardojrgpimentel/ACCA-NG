package mattecarra.accapp.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.afollestad.materialdialogs.list.toggleItemChecked
import com.afollestad.materialdialogs.list.updateListItemsSingleChoice
import it.sephiroth.android.library.xtooltip.ClosePolicy
import it.sephiroth.android.library.xtooltip.Tooltip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mattecarra.accapp.Preferences
import mattecarra.accapp.R
import mattecarra.accapp.acc.Acc
import mattecarra.accapp.databinding.ActivityAccConfigEditorBinding
import mattecarra.accapp.databinding.AddChargingSwitchDialogBinding
import mattecarra.accapp.databinding.ContentAccConfigEditorBinding
import mattecarra.accapp.dialogs.powerLimitDialog
import mattecarra.accapp.dialogs.progress
import mattecarra.accapp.models.AccConfig
import mattecarra.accapp.models.AccaProfile
import mattecarra.accapp.models.ProfileEnables
import mattecarra.accapp.models.ProfilePreset
import mattecarra.accapp.utils.Constants
import mattecarra.accapp.utils.LogExt
import mattecarra.accapp.utils.ScopedAppActivity
import mattecarra.accapp.viewmodel.AccConfigEditorViewModel
import mattecarra.accapp.viewmodel.AccConfigEditorViewModelFactory

class AccConfigEditorActivity : ScopedAppActivity(),
    NumberPicker.OnValueChangeListener, CompoundButton.OnCheckedChangeListener
{
    private lateinit var binding: ActivityAccConfigEditorBinding
    private lateinit var content: ContentAccConfigEditorBinding
    private lateinit var viewModel: AccConfigEditorViewModel
    private lateinit var mUndoMenuItem: MenuItem
    private lateinit var mPreferences: Preferences
    private lateinit var initConfig: AccConfig
    private var accConfigOnly: Boolean = false

    private fun returnResults()
    {
        if (accConfigOnly)  // FIX OUT if load ONLY ACC Config
        {
            if (!viewModel.enables.eCoolDown) viewModel.coolDown = null
            if (!viewModel.enables.eVoltage) viewModel.voltageLimit = AccConfig.ConfigVoltage(null, null)
            if (!viewModel.enables.eRunOnBoot) viewModel.onBoot = null
            if (!viewModel.enables.eRunOnPlug) viewModel.onPlug = null
        }

        val returnIntent = Intent()
        returnIntent.putExtra(Constants.PROFILE_ID_KEY, intent.getIntExtra(Constants.PROFILE_ID_KEY, -1))
        returnIntent.putExtra(Constants.ACC_HAS_CHANGES, if (accConfigOnly)
            viewModel.profile.accConfig != viewModel.initialAccConfig else viewModel.unsavedChanges)
        returnIntent.putExtra(Constants.ACC_CONFIG_KEY, viewModel.profile.accConfig)
        returnIntent.putExtra(Constants.PROFILE_CONFIG_KEY, viewModel.profile)
        if (intent.getBooleanExtra(Constants.PROFILE_CREATION_KEY, false)) {
            ProfilePreset.matching(viewModel.capacity)?.let {
                returnIntent.putExtra(Constants.SUGGESTED_PROFILE_NAME_KEY, getString(it.nameRes))
            }
        }
        setResult(Activity.RESULT_OK, returnIntent)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        val binding = ActivityAccConfigEditorBinding.inflate(layoutInflater)
        this.binding = binding
        setContentView(binding.root)
        content = binding.contentAccConfigEditor
        accConfigOnly = !intent.hasExtra(Constants.PROFILE_CONFIG_KEY)

        // Load preferences
        mPreferences = Preferences(this)

        setSupportActionBar(binding.accConfEditorToolbar)
        supportActionBar?.title = intent?.getStringExtra(Constants.TITLE_KEY) ?: getString(R.string.acc_config_editor)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val profile = when // load profile from intent
        {
            savedInstanceState?.containsKey(Constants.PROFILE_CONFIG_KEY) == true ->
                savedInstanceState.getSerializable(Constants.PROFILE_CONFIG_KEY) as AccaProfile

            intent.hasExtra(Constants.PROFILE_CONFIG_KEY) ->
                intent.getSerializableExtra(Constants.PROFILE_CONFIG_KEY) as AccaProfile

            else ->
            {
                accConfigOnly = true
                AccaProfile(-1,"", AccConfig(), ProfileEnables())
            }
        }

        val config = when // load config from intent or current config
        {
            savedInstanceState?.containsKey(Constants.ACC_CONFIG_KEY) == true ->
                savedInstanceState.getSerializable(Constants.ACC_CONFIG_KEY) as AccConfig

            intent.hasExtra(Constants.ACC_CONFIG_KEY) ->
                intent.getSerializableExtra(Constants.ACC_CONFIG_KEY) as AccConfig

            // No config attached: read it from the ACC daemon. This performs
            // root shell I/O, so it must NOT block the UI thread (see issue
            // #258). Show a centered skeleton while loading and initialise
            // the editor once the config arrives.
            else -> {
                loadDaemonConfigAsync(profile)
                return
            }
        }

        initializeEditor(profile, config)
    }

    /**
     * Reads the live daemon config off the UI thread behind a skeleton
     * placeholder. Failures degrade to an error dialog with Retry (never an
     * endless spinner): retry re-runs the load, "use defaults" opens the
     * editor with stock values.
     */
    private fun loadDaemonConfigAsync(profile: AccaProfile) {
        content.root.visibility = View.GONE
        val skeleton = layoutInflater.inflate(
            R.layout.skeleton_acc_config_editor,
            binding.root as android.view.ViewGroup,
            false
        )
        (binding.root as android.view.ViewGroup).addView(skeleton)
        skeleton.startAnimation(
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.skeleton_pulse)
        )

        launch {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    Acc.instance.readConfig()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    null
                }
            }
            if (loaded != null) {
                dismissSkeleton(skeleton)
                initializeEditor(profile, loaded)
                return@launch
            }
            showConfigReadError(
                onRetry = { loadDaemonConfigAsync(profile) },
                onDefaults = {
                    launch {
                        val fallback = withContext(Dispatchers.IO) {
                            try {
                                Acc.instance.readDefaultConfig()
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                                AccConfig()
                            }
                        }
                        dismissSkeleton(skeleton)
                        initializeEditor(profile, fallback)
                    }
                }
            )
        }
    }

    private fun dismissSkeleton(skeleton: View) {
        skeleton.clearAnimation()
        (binding.root as android.view.ViewGroup).removeView(skeleton)
        content.root.visibility = View.VISIBLE
    }

    /**
     * Finishes Activity setup once the ACC config is available. Split out of
     * [onCreate] so the daemon-backed path can load asynchronously without
     * blocking the UI thread.
     */
    private fun initializeEditor(profile: AccaProfile, config: AccConfig) {
        if (accConfigOnly) profile.accConfig = config
        initConfig = profile.accConfig.copy()

        viewModel = ViewModelProvider(this, AccConfigEditorViewModelFactory(application, profile))
            .get(AccConfigEditorViewModel::class.java)

        // The options menu may already have been created while the config
        // was loading (async path); rebuild it so the undo observer binds.
        invalidateOptionsMenu()

        // Replaces the deprecated onBackPressed() override and is only active
        // once the editor is initialised (config may still be loading).
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.unsavedChanges)
                {
                    MaterialDialog(this@AccConfigEditorActivity).show {
                        title(R.string.unsaved_changes)
                        message(R.string.unsaved_changes_message)
                        positiveButton(R.string.save) { returnResults() }
                        negativeButton(R.string.close_without_saving) { finish() }
                        neutralButton(android.R.string.cancel)
                    }
                }
                else finish()
            }
        })

        initUi()

        viewModel.clearHistory()

        LogExt().d(javaClass.simpleName, "onCreate(): accConfigOnly=$accConfigOnly, profile=$profile")
    }

    private fun initUi()
    {
        val creating = intent.getBooleanExtra(Constants.PROFILE_CREATION_KEY, false)
        content.profileEditContext.visibility = if (creating) View.GONE else View.VISIBLE
        if (accConfigOnly) {
            val activeName = intent.getStringExtra(Constants.APPLIED_PROFILE_NAME_KEY)
            content.profileEditContextTitle.text = if (activeName == null)
                getString(R.string.profile_edit_live_context) else getString(R.string.profile_edit_from_profile, activeName)
            content.profileEditContextDescription.setText(if (activeName == null)
                R.string.profile_edit_custom_help else R.string.profile_edit_active_help)
        } else {
            content.profileEditContextTitle.setText(R.string.profile_edit_saved_context)
            content.profileEditContextDescription.setText(R.string.profile_edit_saved_help)
        }
        content.profilePresets.visibility =
            if (intent.getBooleanExtra(Constants.PROFILE_CREATION_KEY, false)) View.VISIBLE else View.GONE
        val presetButtons = listOf(
            content.presetDaily to ProfilePreset.DAILY,
            content.presetPlugged to ProfilePreset.PLUGGED,
            content.presetTravel to ProfilePreset.TRAVEL
        )
        presetButtons.forEach { (button, preset) ->
            button.text = getString(R.string.preset_option, getString(preset.nameRes),
                getString(preset.descriptionRes), preset.pause, preset.resume)
            button.setOnClickListener {
                viewModel.enables = viewModel.enables.copy(eCapacity = true)
                viewModel.capacity = preset.applyTo(viewModel.profile.accConfig).configCapacity
                button.isChecked = true
            }
        }
        viewModel.observeCapacity(this, Observer { capacity ->
            val selected = ProfilePreset.matching(capacity)
            presetButtons.forEach { (button, preset) -> button.isChecked = preset == selected }
        })

        viewModel.observeEnables(this, Observer
        {
            content.capacitySwitchEnabled.isChecked = it.eCapacity
            content.voltcontrolSwitchEnabled.isChecked = it.eVoltage
            content.tempSwitchEnabled.isChecked = it.eTemperature
            content.cooldownSwitchEnabled.isChecked = it.eCoolDown
            content.applyOnBootSwitchEnabled.isChecked = it.eRunOnBoot
            content.onPluggedSwitchEnabled.isChecked = it.eRunOnPlug
            renderChargeSummary()
        })

        viewModel.observePrioritizeBatteryIdleMode(this, Observer { content.batteryPrioritizeIdleSwitchEnabled.isChecked = it })
        viewModel.observeResetBSOnUnplug(this, Observer { content.resetStatusUnplugSwitch.isChecked = it })
        viewModel.observeResetBSOnPause(this, Observer { content.resetBSOnPauseSwitch.isChecked = it })
        viewModel.observeIsAutomaticSwitchEnabled(this, Observer { content.automaticSwitchEnabled.isChecked = it })

        viewModel.observeCapacity(this, Observer
        {
            configureUnitPicker(content.shutdownCapacityPicker, 2, 20, it.shutdown,
                R.string.charge_percentage_value)
            configureUnitPicker(content.resumeCapacityPicker, it.shutdown,
                if (it.pause == 101) 101 else it.pause - 1, it.resume,
                R.string.charge_percentage_value)
            configureUnitPicker(content.pauseCapacityPicker,
                if (it.resume == 101) 101 else it.resume + 1, 101, it.pause,
                R.string.charge_percentage_value)
            renderChargeSummary()
        })

        viewModel.observeChargeSwitch(this, Observer
        {
            content.chargingSwitchTextview.text = it ?: getString(R.string.automatic)
            content.automaticSwitchEnabled.isEnabled = it != null
            if (it == null) content.automaticSwitchEnabled.isChecked = true
        })

        viewModel.observeTemperature(this, Observer
        {
            configureUnitPicker(content.temperatureCooldownPicker, 20, 90, it.coolDownTemperature,
                R.string.charge_temperature_value)
            configureUnitPicker(content.temperatureMaxPicker, 20, 95, it.maxTemperature,
                R.string.charge_temperature_value)
            configureUnitPicker(content.temperatureMaxPauseSecondsPicker, 10, 120, it.pause,
                R.string.charge_seconds_value)
        })

        viewModel.observeCoolDown(this, Observer
        {
            configureUnitPicker(content.cooldownPercentagePicker, 0, 100, it?.atPercent ?: 60,
                R.string.charge_percentage_value)
            configureUnitPicker(content.cooldownChargeRatioPicker, 1, 120, it?.charge ?: 50,
                R.string.charge_seconds_value)
            configureUnitPicker(content.cooldownPauseRatioPicker, 1, 120, it?.pause ?: 10,
                R.string.charge_seconds_value)
        })

        viewModel.observeVoltageLimit(this, Observer
        {
            content.voltageControlFileSpinner.text = it.controlFile ?: "Not supported"
            content.voltageMaxEditText.text = it.max?.let { "$it mV" } ?: getString(R.string.disabled)
        })

        viewModel.observeCurrentMax(this, Observer
        {
            content.currentMaxEditText.text = it?.let { "$it mA" } ?: getString(R.string.disabled)
        })

        viewModel.observeOnPlug(this, Observer
        { configOnPlug ->
            content.tvConfigOnPlugged.text = configOnPlug?.let { if(it.isBlank()) getString(R.string.voltage_control_file_not_set) else it } ?: getString(R.string.voltage_control_file_not_set)
        })

        viewModel.observeOnBoot(this, Observer
        { configOnBoot ->
            content.tvConfigOnBoot.text = configOnBoot?.let { if(it.isBlank()) getString(R.string.voltage_control_file_not_set) else it } ?: getString(R.string.voltage_control_file_not_set)
        })

        //--------------------------------------------------------------------------

        // InfoClick
        content.capacityControlInfo.setOnClickListener { onInfoClick(it) }
        content.powerControlInfo.setOnClickListener { onInfoClick(it) }
        content.temperatureControlInfo.setOnClickListener { onInfoClick(it) }
        content.exitOnBootInfo.setOnClickListener { onInfoClick(it) }
        content.cooldownInfo.setOnClickListener { onInfoClick(it) }
        content.onPluggedInfo.setOnClickListener { onInfoClick(it) }
        content.batteryIdleControlInfo.setOnClickListener { onInfoClick(it) }
        content.miscellaneousInfo.setOnClickListener { onInfoClick(it) }

        //capacity card
        content.shutdownCapacityPicker.setOnValueChangedListener(this)
        content.resumeCapacityPicker.setOnValueChangedListener(this)
        content.pauseCapacityPicker.setOnValueChangedListener(this)

        //temps
        content.temperatureCooldownPicker.setOnValueChangedListener(this)
        content.temperatureMaxPicker.setOnValueChangedListener(this)
        content.temperatureMaxPauseSecondsPicker.setOnValueChangedListener(this)

        //coolDown
        content.cooldownPercentagePicker.setOnValueChangedListener(this)
        content.cooldownChargeRatioPicker.setOnValueChangedListener(this)
        content.cooldownPauseRatioPicker.setOnValueChangedListener(this)

        //power card
        if (Acc.instance.version >= 202002170) content.voltageControlFileLl.visibility = View.GONE
        else content.currentMaxLl.visibility = View.GONE

        //SwitchEnabled
        content.capacitySwitchEnabled.setOnCheckedChangeListener(this)
        if (Acc.instance.version < 202007220) content.automaticSwitchEnabled.visibility = View.GONE
        content.automaticSwitchEnabled.setOnCheckedChangeListener(this)
        content.voltcontrolSwitchEnabled.setOnCheckedChangeListener(this)
        content.batteryPrioritizeIdleSwitchEnabled.setOnCheckedChangeListener(this)
        content.tempSwitchEnabled.setOnCheckedChangeListener(this)
        content.cooldownSwitchEnabled.setOnCheckedChangeListener(this)
        content.applyOnBootSwitchEnabled.setOnCheckedChangeListener(this)
        content.onPluggedSwitchEnabled.setOnCheckedChangeListener(this)
        content.resetStatusUnplugSwitch.setOnCheckedChangeListener (this)
        content.resetBSOnPauseSwitch.setOnCheckedChangeListener(this)

        if (accConfigOnly) // FIX Checks and Visibility if loaded ONLY ACC Config
        {
            content.capacitySwitchEnabled.visibility = View.GONE  // can't disabled
            content.tempSwitchEnabled.visibility = View.GONE

            viewModel.enables.eVoltage =
                (viewModel.voltageLimit.controlFile != null || viewModel.voltageLimit.max != null)

            viewModel.enables.eCapacity = true
            viewModel.enables.eTemperature = true
            viewModel.enables.eCoolDown = viewModel.coolDown != null
            viewModel.enables.eRunOnBoot = true
            viewModel.enables.eRunOnPlug = true
        }
    }

    private fun showConfigReadError(onRetry: () -> Unit, onDefaults: () -> Unit)
    {
        MaterialDialog(this).show {
            title(R.string.config_error_title)
            message(R.string.config_error_dialog)
            positiveButton(R.string.retry) { onRetry() }
            negativeButton(R.string.use_default_config) { onDefaults() }
            cancelOnTouchOutside(false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        menuInflater.inflate(R.menu.acc_config_editor_menu, menu)
        mUndoMenuItem = menu.findItem(R.id.action_undo)
        // The config may still be loading asynchronously (daemon-backed
        // path); the menu is rebuilt via invalidateOptionsMenu() once the
        // ViewModel exists.
        if (::viewModel.isInitialized) {
            viewModel.undoOperationAvailableLiveData.observe(this, Observer { mUndoMenuItem.isEnabled = it })
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        // Ignore menu actions while the config is still loading.
        if (!::viewModel.isInitialized) return true
        when (item.itemId)
        {
            R.id.action_save -> returnResults()
            R.id.action_restore -> viewModel.profile.accConfig = initConfig.copy()
            R.id.action_undo -> viewModel.undoLastConfigOperation()
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); return true }
        }

        return super.onOptionsItemSelected(item)
    }

//    private fun updateAccSwitchCard(config: AccConfig)
//    {
//        content.automaticSwitchEnabled.isChecked = config.configIsAutomaticSwitchingEnabled
//        content.batteryPrioritizeIdleSwitchEnabled.isChecked = config.prioritizeBatteryIdleMode
//        content.resetBSOnPauseSwitch.isChecked = config.configResetBsOnPause
//        content.resetStatusUnplugSwitch.isChecked = config.configResetUnplugged
//    }

    private fun updateProfileSwitchCard(profileEnables: ProfileEnables)
    {}

    private fun updateCapacityCard(configCapacity: AccConfig.ConfigCapacity)
    {}

    private fun updateChargeSwitch(configChargeSwitch: String?)
    {}

    private fun updateTemperatureCard(configTemperature: AccConfig.ConfigTemperature)
    {}

    private fun updateCoolDownCard(configCoolDown: AccConfig.ConfigCoolDown?)
    {}

    private fun updateVoltageControlCard(configVoltage: AccConfig.ConfigVoltage)
    {}

    private fun updateCurrentMaxControlCard(currentMax: Int?)
    {}

    //-------------------------------------------------------------------------------------

    fun onBatteryIdleTestButtonClick(v: View)
    {
        launch {
            val dialog = MaterialDialog(this@AccConfigEditorActivity).show {
                title(R.string.test_battery_idle)
                progress(R.string.wait)
            }

            val (exitCode, supported) = Acc.instance.isBatteryIdleSupported()
            if (dialog.isShowing)
            {
                dialog.cancel()

                if (exitCode == 2)
                { //battery is not charging -> can not test
                    MaterialDialog(this@AccConfigEditorActivity).show {
                        title(R.string.test_battery_idle)
                        message(R.string.plug_battery_to_test)
                        positiveButton(R.string.retry) {
                            onBatteryIdleTestButtonClick(v)
                        }
                        negativeButton(android.R.string.cancel)
                    }
                }
                else
                {
                    if (!supported) viewModel.prioritizeBatteryIdleMode = false
                    content.batteryPrioritizeIdleSwitchEnabled.isEnabled = supported

                    MaterialDialog(this@AccConfigEditorActivity).show {
                        title(R.string.test_battery_idle)

                        if (!supported)
                        {
                            content.batteryPrioritizeIdleSwitchEnabled.isChecked = false
                            message(R.string.test_battery_idle_unsupported_result)
                        }
                        else
                        {
                            message(R.string.test_battery_idle_supported_result)
                        }
                        positiveButton(android.R.string.ok)
                    }
                }

            }
        }
    }

    private fun configureUnitPicker(picker: NumberPicker, min: Int, max: Int, value: Int,
                                    formatRes: Int) {
        // Displayed values keep the unit on the selected editable value too;
        // a formatter alone loses the suffix through the numeric input filter.
        if (picker.displayedValues == null || picker.minValue != min || picker.maxValue != max) {
            picker.displayedValues = null
            picker.minValue = min
            picker.maxValue = max
            picker.displayedValues = (min..max).map {
                if (it == 101 && formatRes == R.string.charge_percentage_value) "∞"
                else getString(formatRes, it)
            }.toTypedArray()
        }
        if (formatRes == R.string.charge_percentage_value) picker.wrapSelectorWheel = false
        picker.value = value
    }

    private fun renderChargeSummary() {
        val limits = viewModel.capacity
        val unlimited = limits.pause == 101 || limits.resume == 101
        content.chargeUnlimitedHint.visibility = if (unlimited) View.VISIBLE else View.GONE
        content.chargeCycleSummary.text = when {
            !viewModel.enables.eCapacity -> getString(R.string.charge_limit_disabled)
            unlimited -> getString(R.string.charge_unlimited_summary)
            else -> getString(R.string.charge_cycle_summary, limits.pause, limits.resume)
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean)
    {
        when (buttonView)
        {
            content.capacitySwitchEnabled ->
            {
                viewModel.enables = viewModel.enables.copy(eCapacity = isChecked)
                content.shutdownCapacityPicker.isEnabled = isChecked
                content.resumeCapacityPicker.isEnabled = isChecked
                content.pauseCapacityPicker.isEnabled = isChecked
            }

            content.automaticSwitchEnabled ->
            {
                viewModel.isAutomaticSwitchEanbled = isChecked
                viewModel.profile.accConfig.configIsAutomaticSwitchingEnabled = isChecked
            }

            content.voltcontrolSwitchEnabled ->
            {
                viewModel.enables = viewModel.enables.copy(eVoltage = isChecked)
                content.editVoltageLimit.isEnabled = isChecked
            }

            content.batteryPrioritizeIdleSwitchEnabled ->
            {
                viewModel.prioritizeBatteryIdleMode = isChecked
                viewModel.profile.accConfig.prioritizeBatteryIdleMode = isChecked
                content.batteryIdleTestButton.isEnabled = isChecked
            }

            content.tempSwitchEnabled ->
            {
                viewModel.enables = viewModel.enables.copy(eTemperature = isChecked)
                content.temperatureCooldownPicker.isEnabled = isChecked
                content.temperatureMaxPicker.isEnabled = isChecked
                content.temperatureMaxPauseSecondsPicker.isEnabled = isChecked
            }

            content.cooldownSwitchEnabled ->
            {
                viewModel.enables = viewModel.enables.copy(eCoolDown = isChecked)
                content.cooldownPercentagePicker.isEnabled = isChecked
                content.cooldownChargeRatioPicker.isEnabled = isChecked
                content.cooldownPauseRatioPicker.isEnabled = isChecked
            }

            content.applyOnBootSwitchEnabled ->
            {
                viewModel.enables = viewModel.enables.copy(eRunOnBoot = isChecked)
                content.tvConfigOnBoot.isEnabled = isChecked
            }

            content.onPluggedSwitchEnabled ->
            {
                viewModel.enables = viewModel.enables.copy(eRunOnPlug = isChecked)
                content.tvConfigOnPlugged.isEnabled = isChecked
            }

            content.resetStatusUnplugSwitch ->
            {
                viewModel.resetBSOnUnplug = isChecked
                viewModel.profile.accConfig.configResetUnplugged = isChecked
            }

            content.resetBSOnPauseSwitch ->
            {
                viewModel.resetBSOnPause = isChecked
                viewModel.profile.accConfig.configResetBsOnPause = isChecked
            }
        }
    }

    override fun onValueChange(picker: NumberPicker?, oldVal: Int, newVal: Int)
    {
        when (picker)
        {
            //capacity
            content.shutdownCapacityPicker -> viewModel.capacity = viewModel.capacity.copy(shutdown = newVal)
            content.resumeCapacityPicker -> viewModel.capacity = viewModel.capacity.copy(resume = newVal)
            content.pauseCapacityPicker -> viewModel.capacity = viewModel.capacity.copy(pause = newVal)
            content.temperatureCooldownPicker -> viewModel.temperature = viewModel.temperature.copy(coolDownTemperature = newVal)
            content.temperatureMaxPicker -> viewModel.temperature = viewModel.temperature.copy(maxTemperature = newVal)
            content.temperatureMaxPauseSecondsPicker -> viewModel.temperature = viewModel.temperature.copy(pause = newVal)

            //coolDown
            content.cooldownPercentagePicker, content.cooldownChargeRatioPicker,
            content.cooldownPauseRatioPicker -> viewModel.coolDown =
                AccConfig.ConfigCoolDown(
                    content.cooldownPercentagePicker.value,
                    content.cooldownChargeRatioPicker.value,
                    content.cooldownPauseRatioPicker.value)

            else -> return
        }
    }

    /**
     * Function for On Boot ImageView OnClick.
     * Opens the dialog to edit the On Boot mAccConfig parameter.
     */

    @SuppressLint("CheckResult")
    fun editOnBootOnClick(view: View)
    {
        MaterialDialog(this@AccConfigEditorActivity).show {
            title(R.string.edit_on_boot)
            message(R.string.edit_on_boot_dialog_message)
            input(
                prefill = viewModel.onBoot ?: "",
                allowEmpty = true,
                hintRes = R.string.edit_on_boot_dialog_hint
            ) { _, text -> viewModel.onBoot = if (text.isNotBlank()) text.toString() else null }
            positiveButton(R.string.save)
            negativeButton(android.R.string.cancel)
            neutralButton(text = "clear", click = { viewModel.onBoot = null }  )
        }
    }

    @SuppressLint("CheckResult")
    fun editOnPluggedOnClick(v: View)
    {
        MaterialDialog(this@AccConfigEditorActivity).show {
            title(R.string.edit_on_plugged)
            message(R.string.edit_on_plugged_dialog_message)
            input(
                prefill = viewModel.onPlug ?: "",
                allowEmpty = true,
                hintRes = R.string.edit_on_boot_dialog_hint
            ) { _, text -> viewModel.onPlug = if (text.trim().isNotEmpty()) text.toString() else null }
            positiveButton(R.string.save)
            negativeButton(android.R.string.cancel)
            neutralButton(text = "clear", click = { viewModel.onPlug = null }  )

        }
    }

    @SuppressLint("CheckResult")
    fun editChargingSwitchOnClick(v: View)
    {
        val automaticString = getString(R.string.automatic)
        val addNewChargingSwitchString = getString(R.string.add_charging_switch)
        val initialSwitch = viewModel.chargeSwitch

        MaterialDialog(this).show {
            title(R.string.edit_charging_switch)
            noAutoDismiss()

            launch {
                var chargingSwitches = listOf(
                    automaticString,
                    addNewChargingSwitchString,
                    *Acc.instance.listChargingSwitches().toTypedArray()
                )

                var currentIndex = chargingSwitches.indexOf(initialSwitch ?: automaticString)

                setActionButtonEnabled(WhichButton.POSITIVE, currentIndex != -1)
                setActionButtonEnabled(WhichButton.NEUTRAL, currentIndex != -1)

                listItemsSingleChoice(
                    items = chargingSwitches,
                    initialSelection = currentIndex,
                    waitForPositiveButton = false
                ) { _, index, text ->
                    if (index == 1)
                    { //Add new charging switch
                        val previousDialog =
                            this@show //I need to keep a reference of the listItems dialog, to update the list of items
                        MaterialDialog(this@AccConfigEditorActivity).show {
                            noAutoDismiss()
                            title(text = addNewChargingSwitchString)
//                            customView(R.layout.add_charging_switch_dialog)
                            val binding = AddChargingSwitchDialogBinding.inflate(layoutInflater)
                            customView(view = binding.root)
                            positiveButton { dialog ->
                                val progressDialog =
                                    MaterialDialog(this@AccConfigEditorActivity).show {
                                        title(R.string.test_switch)
                                        progress(R.string.wait)
                                    }

//                                val view = dialog.getCustomView()
//                                val switch = "${view.charging_switch_edit_text.text} ${view.charging_switch_on_value_edit_text.text} ${view.charging_switch_off_value_edit_text.text}"
                                val switch = "${binding.chargingSwitchEditText.text} ${binding.chargingSwitchOnValueEditText.text} ${binding.chargingSwitchOffValueEditText.text}"
                                this@AccConfigEditorActivity.launch {
                                    var success = true

                                    if (Acc.instance.isBatteryCharging())
                                    { //If battery is charging the switch is tested
                                        if (Acc.instance.testChargingSwitch(switch) != 0)
                                        {
                                            success = false
                                            Toast.makeText(
                                                this@AccConfigEditorActivity,
                                                R.string.charging_switch_does_not_work,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }

                                    if (success)
                                    {
                                        chargingSwitches = listOf(*chargingSwitches.toTypedArray(), switch) //update the list of switches with the new switch

                                        if (Acc.instance.addChargingSwitch(switch))
                                        {
                                            previousDialog.updateListItemsSingleChoice(items = chargingSwitches)
                                            currentIndex = chargingSwitches.size - 1
                                        }
                                        else Toast.makeText(this@AccConfigEditorActivity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                                    }

                                    progressDialog.dismiss()
                                    dismiss()
                                }
                            }
                            negativeButton { dismiss() }
                            onDismiss {
                                previousDialog.toggleItemChecked(currentIndex) //Select the correct item when closing this dialog
                            }
                        }

                        return@listItemsSingleChoice
                    }

                    currentIndex = index
                    setActionButtonEnabled(WhichButton.POSITIVE, index != -1)
                    setActionButtonEnabled(WhichButton.NEUTRAL, index != -1)
                }

                positiveButton(R.string.save) {
                    viewModel.chargeSwitch = if (currentIndex == 0) null else chargingSwitches[currentIndex]
                    dismiss()
                }

                neutralButton(R.string.test_switch) {
                    val switch = if (currentIndex == 0) null else chargingSwitches[currentIndex]

                    val dialog = MaterialDialog(this@AccConfigEditorActivity).show {
                        title(R.string.test_switch)
                        progress(R.string.wait)
                    }

                    this@AccConfigEditorActivity.launch {
                        val description = when (Acc.instance.testChargingSwitch(switch))
                        {
                            0 -> R.string.charging_switch_works
                            1 -> R.string.charging_switch_does_not_work
                            2 -> R.string.plug_battery_to_test
                            else -> R.string.error_occurred
                        }

                        dialog.cancel()

                        MaterialDialog(this@AccConfigEditorActivity).show {
                            title(R.string.test_switch)
                            message(description)
                            positiveButton(android.R.string.ok)
                        }
                    }
                }
            }

            negativeButton(android.R.string.cancel) { dismiss() }
        }
    }

    fun editPowerOnClick(v: View)
    {
        MaterialDialog(this@AccConfigEditorActivity).show {
            powerLimitDialog(viewModel.voltageLimit, viewModel.currentMaxLimit, this@AccConfigEditorActivity)
            { controlFile, voltageMaxEnabled, voltageMax, currentMaxEnabled, currentMax ->

                if (voltageMaxEnabled && voltageMax != null)
                {
                    viewModel.voltageLimit = AccConfig.ConfigVoltage(controlFile, voltageMax)
                }
                else
                {
                    viewModel.voltageLimit = viewModel.voltageLimit.copy(max = null)
                }

                viewModel.currentMaxLimit = if (currentMaxEnabled) currentMax else null
            }
            negativeButton(android.R.string.cancel)
        }
    }

    fun onInfoClick(v: View)
    {
        when (v)
        {
            content.capacityControlInfo -> R.string.capacity_control_info
            content.powerControlInfo -> R.string.power_control_info
            content.temperatureControlInfo -> R.string.temperature_control_info
            content.exitOnBootInfo -> R.string.description_exit_on_boot
            content.cooldownInfo -> R.string.cooldown_info
            content.onPluggedInfo -> R.string.on_plugged_info
            content.batteryIdleControlInfo -> R.string.battery_idle_info_label
            content.miscellaneousInfo -> R.string.miscellaneous_info_label
            else -> null

        }?.let {
            Tooltip.Builder(this).anchor(v, 0, 0, false).text(it).arrow(true)
                .closePolicy(ClosePolicy.TOUCH_ANYWHERE_CONSUME).showDuration(-1).overlay(false)
                .maxWidth((resources.displayMetrics.widthPixels / 1.3).toInt())
                .styleId(R.style.ToolTipAltStyle).create().show(v, Tooltip.Gravity.LEFT, true)
        }
    }

    fun onCapacityRestore(view: View)
    {
        viewModel.capacity = initConfig.configCapacity
        viewModel.chargeSwitch = initConfig.configChargeSwitch
    }

    fun onPowerControlRestore(view: View)
    {
        viewModel.voltageLimit = initConfig.configVoltage
        viewModel.currentMaxLimit = initConfig.configCurrMax
    }

    fun onTemperatureControlRestore(view: View)
    {
        viewModel.temperature = initConfig.configTemperature
    }

    fun onBootRestoreClick(view: View)
    {
        viewModel.onBoot = initConfig.configOnBoot
    }

    fun onPluggedRestore(view: View)
    {
        viewModel.onPlug = initConfig.configOnPlug
    }

    fun onCooldownRestore(view: View)
    {
        viewModel.coolDown = initConfig.configCoolDown
    }

    fun onBatteryIdleRestore(v: View)
    {
        viewModel.prioritizeBatteryIdleMode = initConfig.prioritizeBatteryIdleMode
    }

    fun onMiscRestore(v: View)
    {
        viewModel.resetBSOnUnplug = initConfig.configResetUnplugged
    }
}
