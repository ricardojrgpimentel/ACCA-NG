package mattecarra.accapp.viewmodel

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.os.Build
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import mattecarra.accapp.models.BatteryDiagnostics
import mattecarra.accapp.utils.BatteryDiagnosticsReader
import mattecarra.accapp.utils.BatteryPowerReader
import mattecarra.accapp.models.BatteryPowerSnapshot

class DashboardViewModel : ViewModel() {

    private val dashboard: MutableLiveData<DashboardValues> = MutableLiveData()
    val daemonRunning = MutableLiveData<Boolean?>()
    private val refreshMutex = Mutex()
    private var diagnostics = BatteryDiagnostics()
    private var diagnosticsUpdatedAt: Long? = null

    fun getDashboardValues(): LiveData<DashboardValues> {
        return dashboard
    }

    init {
        viewModelScope.launch() {
            ensureMicroInputUnits()
            while (true) {
                if (dashboard.hasActiveObservers()) {
                    refresh()
                }
                delay(2000)
            }
        }
    }

    suspend fun refresh() = refreshMutex.withLock {
        val daemon = try {
            Acc.instance.isAccdRunning()
        } catch (ex: CancellationException) { throw ex
        } catch (ex: Exception) { null }
        daemonRunning.value = daemon
        var batteryReadingFresh = true
        val batteryInfo = try {
            Acc.instance.getBatteryInfo()
        } catch (ex: CancellationException) { throw ex
        } catch (ex: Exception) {
            batteryReadingFresh = false
            dashboard.value?.batteryInfo
        } ?: return@withLock
        val power = try { BatteryPowerReader.read()
        } catch (ex: CancellationException) { throw ex
        } catch (ex: Exception) { BatteryPowerSnapshot() }
        val batteryState = MainApplication.appContext.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val now = SystemClock.elapsedRealtime()
        if (diagnosticsUpdatedAt == null || now - diagnosticsUpdatedAt!! >= 30_000) {
            try {
                val androidCycles = if (Build.VERSION.SDK_INT >= 34)
                    batteryState?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) else null
                diagnostics = BatteryDiagnosticsReader.read(batteryInfo.cycleCount, androidCycles)
            } catch (ex: CancellationException) { throw ex
            } catch (ex: Exception) { /* Keep the last available diagnostics. */ }
            diagnosticsUpdatedAt = now
        }
        dashboard.value = DashboardValues(batteryInfo, daemon,
            batteryState?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)?.takeIf { it >= 0 },
            batteryState?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)?.takeIf { it >= 0 },
            diagnostics, power, batteryReadingFresh)
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
