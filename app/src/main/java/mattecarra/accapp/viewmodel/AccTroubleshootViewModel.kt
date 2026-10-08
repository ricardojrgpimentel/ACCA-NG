package mattecarra.accapp.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mattecarra.accapp.R
import mattecarra.accapp.acc.AccTroubleshooter
import mattecarra.accapp.acc.BundledAccDaemon
import mattecarra.accapp.models.AccHealthReport
import mattecarra.accapp.models.AccHealthSnapshot
import mattecarra.accapp.models.AccHealthTracker

class AccTroubleshootViewModel(application: Application) : AndroidViewModel(application) {
    val report = MutableLiveData<AccHealthReport>()
    val resultMessage = MutableLiveData<Int?>()
    val backup = MutableLiveData<String?>()
    val working = MutableLiveData(false)
    private val tracker = AccHealthTracker()

    suspend fun refresh() {
        val snapshot = try { AccTroubleshooter.read() }
            catch (ex: CancellationException) { throw ex }
            catch (ex: Exception) { AccHealthSnapshot() }
        val hash = withContext(Dispatchers.IO) { BundledAccDaemon.hash(getApplication()) }
        report.value = tracker.evaluate(snapshot, hash, SystemClock.elapsedRealtime())
    }

    suspend fun calibrate(): Boolean {
        working.value = true
        backup.value = null
        resultMessage.value = null
        try {
            val path = AccTroubleshooter.calibrate()
            backup.value = path
            resultMessage.value = if (path == null) R.string.troubleshoot_calibration_failed
                else R.string.troubleshoot_calibration_success
            return path != null
        } catch (ex: CancellationException) { throw ex
        } catch (ex: Exception) {
            resultMessage.value = R.string.troubleshoot_calibration_failed
            throw ex
        } finally { working.value = false; refresh() }
    }

    suspend fun restore(): Boolean {
        working.value = true
        backup.value = null
        resultMessage.value = null
        try {
            backup.value = AccTroubleshooter.restoreDaemon(getApplication())
            resultMessage.value = R.string.troubleshoot_restore_success
            return true
        } catch (ex: CancellationException) { throw ex
        } catch (ex: Exception) {
            resultMessage.value = R.string.troubleshoot_restore_failed
            backup.value = Regex("/data/adb/vr25/acc-data/backup/troubleshoot-[0-9a-f-]+")
                .find(ex.message.orEmpty())?.value
            throw ex
        } finally { working.value = false; refresh() }
    }
}
