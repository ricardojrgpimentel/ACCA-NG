package mattecarra.accapp.services

import android.annotation.TargetApi
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import mattecarra.accapp.utils.AppLanguages
import mattecarra.accapp.R
import mattecarra.accapp.acc.Acc
import mattecarra.accapp.acc.ConfigUpdaterEnable
import mattecarra.accapp.acc.ConfigVerifier
import mattecarra.accapp.utils.ProfileUtils
import mattecarra.accapp.viewmodel.ProfilesViewModel
import kotlin.coroutines.CoroutineContext

@TargetApi(Build.VERSION_CODES.N)
class AccProfileTileService: TileService(), CoroutineScope {
    protected lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val LOG_TAG = "AccProfileTileService"
    private lateinit var profilesViewModel: ProfilesViewModel

    override fun onCreate()
    {
        super.onCreate()
        job = Job()
        profilesViewModel = ProfilesViewModel(application)
        profilesViewModel.getLiveData().observeForever { updateTile() }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun updateTile()
    {
        val tile = qsTile
        val profiles = profilesViewModel.getLiveData().value

        if(profiles?.isNotEmpty() == true)
        {
            val profileId = ProfileUtils.getCurrentProfile(PreferenceManager.getDefaultSharedPreferences(this))
            val currProfile = if(profileId != -1) profiles.find { it.uid == profileId } else null

            if(currProfile != null)
            {
                tile.label =  AppLanguages.localizedContext(this@AccProfileTileService).getString(R.string.profile_tile_label, currProfile.profileName)
                tile.state =  Tile.STATE_ACTIVE
            } else {
                tile.label = AppLanguages.localizedContext(this@AccProfileTileService).getString(R.string.profile_not_selected)
                tile.state =  Tile.STATE_INACTIVE
            }
            tile.icon = Icon.createWithResource(this, R.drawable.ic_battery_charging_80) //use acc icon once ready

        } else {
            tile.label = AppLanguages.localizedContext(this@AccProfileTileService).getString(R.string.no_profiles)
            tile.state =  Tile.STATE_UNAVAILABLE
            tile.icon = Icon.createWithResource(this, R.drawable.ic_battery_charging_full) //use acc icon once ready
        }

        tile.updateTile()
    }

    override fun onTileAdded()
    {
        super.onTileAdded()
        updateTile()
    }

    override fun onStartListening()
    {
        super.onStartListening()
        updateTile()
    }

    //Get profiles list and increment current profile of one unit.
    override fun onClick()
    {
        super.onClick()

        val mSharedPrefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        profilesViewModel.getLiveData().value?.let { profileList ->
            val currentProfile = ProfileUtils.getCurrentProfile(mSharedPrefs)

            var index = profileList.indexOfFirst { it.uid ==  currentProfile} + 1
            if(index >= profileList.size) index = 0

            val profile = profileList[index]

            //apply profile
            launch {
                val controls = ConfigUpdaterEnable(mSharedPrefs)
                val successful = try {
                    val res = Acc.instance.updateAccConfig(profile.accConfig, controls)
                    res.isSuccessful() && ConfigVerifier.awaitApplied(profile.accConfig,
                        controls.sendVoltage, controls.sendCurrMax) { Acc.instance.readConfig() } != null
                } catch (ex: CancellationException) { throw ex
                } catch (ex: Exception) { false }

                //Update tile infos
                qsTile.state = if (successful) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                qsTile.label = if (successful) AppLanguages.localizedContext(this@AccProfileTileService).getString(R.string.profile_tile_label, profile.profileName) else AppLanguages.localizedContext(this@AccProfileTileService).getString(R.string.error_occurred)
                qsTile.updateTile()

                if (successful) ProfileUtils.saveCurrentProfile(profile.uid, mSharedPrefs)
                else ProfileUtils.clearCurrentSelectedProfile(mSharedPrefs)
            }
        }
    }
}
