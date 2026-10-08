package mattecarra.accapp.viewmodel

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mattecarra.accapp.Preferences
import mattecarra.accapp.CurrentUnit
import mattecarra.accapp.MainApplication
import mattecarra.accapp.VoltageUnit
import mattecarra.accapp.acc.Acc
import mattecarra.accapp.models.BatteryInfo
import mattecarra.accapp.R
import mattecarra.accapp.models.DashboardValues

class DashboardViewModel : ViewModel() {

    private val dashboard: MutableLiveData<DashboardValues> = MutableLiveData()

    fun getDashboardValues(): LiveData<DashboardValues> {
        return dashboard
    }

    init {
        viewModelScope.launch() {
            ensureMicroInputUnits()
            while (true) {
                if (dashboard.hasActiveObservers()) {
                    dashboard.value = DashboardValues(
                        Acc.instance.getBatteryInfo(),
                        Acc.instance.isAccdRunning()
                    )
                }
                delay(2000)
            }
        }
    }

    /**
     * Fresh installs keep the legacy A/V input-unit defaults, but every
     * acca-era daemon reports in scales the app normalises to µA/µV, so the
     * dashboard shows 1000x values (issue #243: "4300 V", "10000000 mA").
     * Migrate the untouched A/V defaults to µA/µV once; explicit user
     * choices are respected afterwards.
     */
    private suspend fun ensureMicroInputUnits() = withContext(Dispatchers.IO) {
        try {
            if (Acc.instance.version < 202107280) return@withContext
            val prefs = Preferences(MainApplication.appContext)
            if (!prefs.unitsMicroMigrationDone &&
                prefs.currentInputUnitOfMeasure == CurrentUnit.A &&
                prefs.voltageInputUnitOfMeasure == VoltageUnit.V) {
                prefs.currentInputUnitOfMeasure = CurrentUnit.uA
                prefs.voltageInputUnitOfMeasure = VoltageUnit.uV
            }
            prefs.unitsMicroMigrationDone = true
        } catch (ex: Exception) {
            // Best effort only: root may be unavailable; the refresh loop
            // below must keep running regardless.
        }
    }
}
