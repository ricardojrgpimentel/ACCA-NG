package mattecarra.accapp.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import mattecarra.accapp.acc.Acc
import mattecarra.accapp.acc.ConfigUpdaterEnable
import mattecarra.accapp.models.AccConfig
import mattecarra.accapp.utils.LogExt
import mattecarra.accapp.utils.ProfileUtils

class SharedViewModel(application: Application) : AndroidViewModel(application)
{
    private val mSharedPrefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val config: MutableLiveData<Pair<AccConfig?, String?>> = MutableLiveData()

    init {
        viewModelScope.launch {
            try {
                config.postValue(Pair(Acc.instance.readConfig(), null))
            } catch (ex: Exception) {
                // Return null config and exception
                config.postValue(Pair(null, ex.stackTraceToString()))
            }
        }
    }

    fun loadDefaultConfig()
    {
        LogExt().d(javaClass.simpleName,"loadDefaultConfig()")

        viewModelScope.launch {
            try {
                config.postValue(Pair(Acc.instance.readDefaultConfig(), null))
            } catch (ex: Exception) {
                config.postValue(Pair(null, ex.stackTraceToString()))
            }
        }
    }

    /**
     * Sets an observer for config.
     */
    fun observeConfig(owner: LifecycleOwner, observer: Observer<Pair<AccConfig?, String?>>) {
        config.observe(owner, observer)
    }

    /*
    * This method is designed to get a parameter from AccConfig or sAccConfig itself
    * Example:
    * val parameter = getAccConfigValue { it.oneParameter }
    * */
    fun <T> getAccConfigValue(callback: (AccConfig) -> T): T {
        return callback(config.value!!.first!!)
    }

    /*
    * This method is designed to set a parameter of AccConfig and write on file
    * Example:
    * updateAccConfigValue { config ->
    *   config.oneParameter = 1
    * }
    * */
    suspend fun updateAccConfigValue(operation: (AccConfig) -> Boolean) {
        val value = config.value!!.first!!

        if(operation(value)) {
            this.config.postValue(Pair(value, null))
            saveAccConfig(value)
        }
    }

    /*
    * Updates the AccConfig and write on file
    */
    suspend fun updateAccConfig(value: AccConfig): Boolean
    {
        LogExt().d(javaClass.simpleName,"updateAccConfig()")
        return saveAccConfig(value)
    }

    /*
    * Saves config on file. It's run in an async thread every time config is updated.
    */
    private suspend fun saveAccConfig(value: AccConfig): Boolean
    {
        val result = Acc.instance.updateAccConfig(value, ConfigUpdaterEnable(mSharedPrefs))
        if (result.isSuccessful()) {
            config.postValue(Pair(value, null))
        } else {
            val currentConfig = try {
                Acc.instance.readConfig()
            } catch (ex: kotlinx.coroutines.CancellationException) { throw ex
            } catch (ex: Exception) { null }
            config.postValue(Pair(currentConfig, if (currentConfig == null) "Unable to read ACC settings" else null))
        }
        return result.isSuccessful()
    }

    /**
     * Clears the currently selected profile ID from Shared Preferences.
     */
    fun clearCurrentSelectedProfile() {
        ProfileUtils.clearCurrentSelectedProfile(mSharedPrefs)
    }

    /**
     * Sets the profile ID to the profile key in the app's shared preferences.
     * @param profileId ID of the profile selected.
     */
    fun setCurrentSelectedProfile(profileId: Int) {
        ProfileUtils.saveCurrentProfile(profileId, mSharedPrefs)
    }
}
