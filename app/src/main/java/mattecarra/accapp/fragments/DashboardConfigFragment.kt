package mattecarra.accapp.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mattecarra.accapp.R
import mattecarra.accapp.acc.Acc
import mattecarra.accapp.activities.AccConfigEditorActivity
import mattecarra.accapp.activities.MainActivity
import mattecarra.accapp.databinding.ProfilesItemBinding
import mattecarra.accapp.models.AccConfig
import mattecarra.accapp.models.ProfileActivation
import mattecarra.accapp.utils.Constants
import mattecarra.accapp.utils.LogExt
import mattecarra.accapp.utils.ProfileUtils
import mattecarra.accapp.utils.ScopedFragment
import mattecarra.accapp.viewmodel.ProfilesViewModel
import mattecarra.accapp.viewmodel.SharedViewModel

class DashboardConfigFragment() : ScopedFragment(), SharedPreferences.OnSharedPreferenceChangeListener  {
    private lateinit var mContext: Context
    private lateinit var mViewModel: ProfilesViewModel
    private lateinit var mSharedViewModel: SharedViewModel
    private lateinit var mPrefs: SharedPreferences

    private var mActiveProfile: Boolean = false
    private var mLoadFailed: Boolean = false
    private var mHasLoaded = false
    private var activeProfileName: String? = null
    private var readJob: Job? = null

    private var _binding: ProfilesItemBinding? = null
    private val binding get() = _binding!!

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 7 && resultCode == Activity.RESULT_OK && data?.getBooleanExtra(Constants.ACC_HAS_CHANGES, false) == true)
        {
            LogExt().d(javaClass.simpleName,"onActivityResult(): ACC_HAS_CHANGES=true")

            val shared = mSharedViewModel
            val config = data.getSerializableExtra(Constants.ACC_CONFIG_KEY) as AccConfig
            (requireActivity() as MainActivity).runAccCommand(R.string.command_apply_settings) {
                val successful = shared.updateAccConfig(config)
                if (successful) shared.clearCurrentSelectedProfile()
                successful
            }
        }
    }

    companion object
    {
        fun newInstance() = DashboardConfigFragment()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        _binding = ProfilesItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        LogExt().d(javaClass.simpleName,"onViewCreated()")
        super.onViewCreated(view, savedInstanceState)

        binding.itemProfileLoadImage.visibility = View.VISIBLE;
        binding.itemProfileInfo.visibility = View.GONE;
        binding.editConfigButton.visibility = View.VISIBLE;

        mContext = requireContext()
        mViewModel = ViewModelProvider(requireActivity()).get(ProfilesViewModel::class.java)
        mSharedViewModel = ViewModelProvider(requireActivity()).get(SharedViewModel::class.java)

        mPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        mPrefs.registerOnSharedPreferenceChangeListener(this)

        view.setOnClickListener(View.OnClickListener {
            // After a failed load the card itself becomes the retry button:
            // a loader that never resolves is worse than no loader.
            if (mLoadFailed) checkProfile()
        })

        binding.editConfigButton.setOnClickListener {
            if (mLoadFailed) checkProfile() else startAccConfigEditorActivity()
        }

        binding.chooseProfileButton.isVisible = true
        binding.chooseProfileButton.setOnClickListener { (requireActivity() as MainActivity).showProfiles() }

        mViewModel.getLiveData().observe(viewLifecycleOwner) { checkProfile() }

    }

    override fun onResume() {
        super.onResume()
        if ((requireActivity() as MainActivity).accCommands.state.value?.running != true) checkProfile()
    }

    override fun onDestroyView() {
        if (::mPrefs.isInitialized) mPrefs.unregisterOnSharedPreferenceChangeListener(this)
        readJob?.cancel()
        mHasLoaded = false
        super.onDestroyView()
        _binding = null
    }

    private fun startAccConfigEditorActivity()
    {
        startActivityForResult(Intent(context, AccConfigEditorActivity::class.java)
            .putExtra(Constants.TITLE_KEY, getString(R.string.profile_edit_live_title))
            .putExtra(Constants.APPLIED_PROFILE_NAME_KEY, activeProfileName), 7)
    }

    fun checkProfile()
    {
        if (_binding == null) return
        readJob?.cancel()
        binding.itemProfileLoadImage.isVisible = !mHasLoaded
        binding.itemProfileInfo.isVisible = mHasLoaded
        mLoadFailed = false

        readJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profileId = ProfileUtils.getCurrentProfile(mPrefs)
                val currentConfig = Acc.instance.readConfig()
                val selProfile = mViewModel.getProfileById(profileId)

                mActiveProfile = selProfile != null && ProfileActivation.matches(currentConfig, selProfile.accConfig,
                    mPrefs.getBoolean("cueVoltage", true), mPrefs.getBoolean("cueCurrMax", true))
                activeProfileName = selProfile?.profileName?.takeIf { mActiveProfile }
                val name = activeProfileName ?: getString(R.string.profile_custom_title)

                updateInfo(name, currentConfig)
                if (!mActiveProfile && profileId != -1) ProfileUtils.clearCurrentSelectedProfile(mPrefs)
            } catch (ex: CancellationException) {
                // Leaving the screen is not a configuration read failure.
                throw ex
            } catch (ex: Exception) {
                ex.printStackTrace()
                showLoadError()
            }
        }
    }

    /**
     * Error state for the config card: the loader is hidden and the card
     * shows what went wrong. Tapping the card retries. This guarantees the
     * spinner can never spin forever.
     */
    private fun showLoadError()
    {
        mLoadFailed = true
        activeProfileName = null
        binding.profileStateLabel.isGone = true
        binding.profileStateDescription.isGone = true
        binding.itemProfileTitleTextView.text = getString(R.string.config_error_title)
        binding.itemProfileCapacityTv.text = getString(R.string.config_error_dialog)
        binding.itemProfileSwitchLl.isGone = true
        binding.itemProfileChargingVoltageLl.isGone = true
        binding.itemProfileTemperatureTv.text = getString(R.string.retry)
        binding.itemProfileCooldownLl.isGone = true
        binding.itemProfileOnBootLl.isGone = true
        binding.itemProfileOnPlugLl.isGone = true
        binding.itemProfilePrioritizeBatteryIdleTv.isGone = true
        binding.itemProfileResetBsOnPauseTv.isGone = true
        binding.itemProfileResettUnpluggedTv.isGone = true
        binding.itemProfileOptionsIb.visibility = View.GONE
        binding.itemProfileLoadImage.visibility = View.GONE;
        binding.itemProfileInfo.visibility = View.VISIBLE;
        binding.editConfigButton.setText(R.string.retry)
    }

    fun updateInfo(nameTitle: String, accConfig: AccConfig)
    {
        LogExt().d(javaClass.simpleName, "updateInfo(): name=$nameTitle , accConfig=$accConfig")

        binding.itemProfileTitleTextView.text = nameTitle
        binding.editConfigButton.setText(R.string.profile_edit_action)
        binding.profileStateLabel.isVisible = true
        binding.profileStateLabel.setText(if (mActiveProfile) R.string.profile_active_label else R.string.profile_none_active)
        binding.profileStateDescription.isVisible = true
        binding.profileStateDescription.setText(if (mActiveProfile) R.string.profile_active_description else R.string.profile_custom_description)
        binding.chooseProfileButton.setText(if (mActiveProfile) R.string.profile_change_action else R.string.profile_choose_action)
        binding.itemProfileCapacityTv.text = accConfig.configCapacity.toString(mContext)

        binding.itemProfileSwitchLl.isGone = accConfig.configChargeSwitch.isNullOrEmpty()
        binding.itemProfileSwitchDataTv.text = accConfig.configChargeSwitch ?: mContext.getString(R.string.automatic)
        binding.itemProfileAutomaticSwitchingTv.isVisible = accConfig.configIsAutomaticSwitchingEnabled

        //-----------------------------------------------

        binding.itemProfileChargingVoltageLl.isVisible = (accConfig.configVoltage.controlFile != null || accConfig.configVoltage.max != null || accConfig.configCurrMax != null)

        binding.itemProfileChargingVoltageTv.text = accConfig.configVoltage.toString(mContext)
        binding.itemProfileCurrentMaxTv.text = mContext.getString(R.string.current_max) +" "+ accConfig.configCurrMax.toString()

        val volt = (accConfig.configVoltage.controlFile != null || accConfig.configVoltage.max != null)
        val currmax = accConfig.configCurrMax != null

        if ((volt && !currmax) || (!volt && currmax))
        {
            binding.itemProfileChargingVoltageTv.isVisible = volt
            binding.itemProfileCurrentMaxTv.isVisible = currmax
        }

        //-----------------------------------------------

        binding.itemProfileTemperatureTv.text = accConfig.configTemperature.toString(mContext)

        binding.itemProfileCooldownLl.isVisible = accConfig.configCoolDown != null
        binding.itemProfileCooldownTv.text = if (accConfig.configCoolDown == null) "-"
        else accConfig.configCoolDown?.toString(mContext)

        binding.itemProfileOnBootLl.isVisible = accConfig.configOnBoot != null
        binding.itemProfileOnBootTv.text = if (accConfig.configOnBoot == null) "-"
        else accConfig.configOnBoot

        binding.itemProfileOnPlugLl.isVisible = accConfig.configOnPlug != null
        binding.itemProfileOnPlugTv.text = if (accConfig.configOnPlug == null) "-"
        else accConfig.getOnPlug(mContext)

        binding.itemProfilePrioritizeBatteryIdleTv.isVisible = accConfig.prioritizeBatteryIdleMode
        binding.itemProfileResetBsOnPauseTv.isVisible = accConfig.configResetBsOnPause
        binding.itemProfileResettUnpluggedTv.isVisible = accConfig.configResetUnplugged

        binding.itemProfileOptionsIb.visibility = View.GONE
        binding.itemProfileSelectedIndicatorView.isVisible = mActiveProfile

        binding.itemProfileLoadImage.visibility = View.GONE;
        binding.itemProfileInfo.visibility = View.VISIBLE;
        mHasLoaded = true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)
    {
        if (key == Constants.PROFILE_KEY) checkProfile()
    }
}
