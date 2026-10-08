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
import kotlinx.coroutines.launch
import mattecarra.accapp.R
import mattecarra.accapp.acc.Acc
import mattecarra.accapp.activities.AccConfigEditorActivity
import mattecarra.accapp.databinding.ProfilesItemBinding
import mattecarra.accapp.models.AccConfig
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

    private var _binding: ProfilesItemBinding? = null
    private val binding get() = _binding!!

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 7 && resultCode == Activity.RESULT_OK && data?.getBooleanExtra(Constants.ACC_HAS_CHANGES, false) == true)
        {
            LogExt().d(javaClass.simpleName,"onActivityResult(): ACC_HAS_CHANGES=true")

            launch {
                mSharedViewModel.updateAccConfig(data.getSerializableExtra(Constants.ACC_CONFIG_KEY) as AccConfig) //TODO: Check assertion
                // Remove the current selected profile
                mSharedViewModel.clearCurrentSelectedProfile()

                updateInfo(getString(R.string.profile_not_selected), data.getSerializableExtra(Constants.ACC_CONFIG_KEY) as AccConfig)
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
        mViewModel = ViewModelProvider(this).get(ProfilesViewModel::class.java)
        mSharedViewModel = ViewModelProvider(this).get(SharedViewModel::class.java)

        mPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        mPrefs.registerOnSharedPreferenceChangeListener(this)

        view.setOnClickListener(View.OnClickListener {
            // After a failed load the card itself becomes the retry button:
            // a loader that never resolves is worse than no loader.
            if (mLoadFailed) checkProfile() else startAccConfigEditorActivity()
        })

        binding.editConfigButton.setOnClickListener {
            if (mLoadFailed) checkProfile() else startAccConfigEditorActivity()
        }

        checkProfile()
    }

    private fun startAccConfigEditorActivity()
    {
        startActivityForResult(Intent(context, AccConfigEditorActivity::class.java), 7)
    }

    fun checkProfile()
    {
        binding.itemProfileLoadImage.visibility = View.VISIBLE;
        binding.itemProfileInfo.visibility = View.GONE;
        mLoadFailed = false

        launch {
            try {
                val profileId = ProfileUtils.getCurrentProfile(mPrefs)
                val currentConfig = Acc.instance.readConfig()
                val selProfile = mViewModel.getProfileById(profileId)

                var name = getString(R.string.profile_not_selected)
                if (selProfile != null && currentConfig == selProfile.accConfig) name = selProfile.profileName

                updateInfo(name, currentConfig)
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
    }

    fun updateInfo(nameTitle: String, accConfig: AccConfig)
    {
        LogExt().d(javaClass.simpleName, "updateInfo(): name=$nameTitle , accConfig=$accConfig")

        binding.itemProfileTitleTextView.text = nameTitle
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
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)
    {
        if (key == Constants.PROFILE_KEY) checkProfile()
    }
}