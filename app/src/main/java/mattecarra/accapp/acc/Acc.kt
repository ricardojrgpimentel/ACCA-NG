package mattecarra.accapp.acc

import android.content.Context
import com.topjohnwu.superuser.Shell
import mattecarra.accapp.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mattecarra.accapp.CurrentUnit
import mattecarra.accapp.Preferences
import mattecarra.accapp.R
import mattecarra.accapp.VoltageUnit
import mattecarra.accapp.acc._interface.AccInterface
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.math.abs

object Acc {
    // Bundled daemon updated to v2023.10.16 (was 2021.8.31). Devices running
    // an older ACC are offered the bundled one on startup.
    const val bundledVersion = 202310160

    /**
     * App files dir. Must be initialised from MainApplication.onCreate via
     * [initAppDirs] because the applicationId of this fork differs from the
     * original package, so the path cannot be hardcoded.
     */
    private var appFilesDir: File? = null

    fun initAppDirs(filesDir: File) {
        appFilesDir = filesDir
    }

    private fun filesDir(): File =
        appFilesDir ?: File("/data/data/mattecarra.accapp/files")

    /*
    * This method returns the name of the package with a compatible AccInterface
    * Note: there won't be a package per version. There will be a package for every uncompatible version
    * Ex: if releases from 201903071->201907211 are all compatible there will only be a package, but if a new release is incompatible a new package is created
    * */
    private fun getAccInterfaceForversion(v: Int): AccInterface {
        return when {
            v >= 202107280 -> mattecarra.accapp.acc.v202107280.AccHandler(v)
            v >= 202007220 -> mattecarra.accapp.acc.v202107280.AccHandler(v)
            v >= 202007030 -> mattecarra.accapp.acc.v202007030.AccHandler(v)
            v >= 202006140 -> mattecarra.accapp.acc.v202006140.AccHandler(v)
            v >= 202002290 -> mattecarra.accapp.acc.v202002290.AccHandler(v)
            v >= 202002170 -> mattecarra.accapp.acc.v202002170.AccHandler(v)
            v >= 201910130 -> mattecarra.accapp.acc.v201910132.AccHandler(v)
            v >= 201903071 -> mattecarra.accapp.acc.v201903071.AccHandler(v)
            else           -> mattecarra.accapp.acc.legacy.AccHandler(v)/* This is used for all the versions before v20190371*/
        }
    }

    @Volatile
    private var INSTANCE: AccInterface? = null

    val instance: AccInterface
        get() {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }

            synchronized(this) {
                // Create acc instance here
                initAcc(filesDir())
                return createAccInstance()
            }
        }

    internal fun createAccInstance(version: Int = getAccVersion() ?: bundledVersion): AccInterface{
        INSTANCE = getAccInterfaceForversion(version)
        return INSTANCE as AccInterface
    }

    fun isAccInstalled(installationDir: File): Boolean {
        return RootShell.exec("test -f ${File(installationDir, "acc/service.sh").absolutePath}").isSuccess
    }

    fun isInstalledAccOutdated(): Boolean = runBlocking {
        instance.getAccVersion()?.let { it < bundledVersion } ?: true
    }

    fun initAcc(installationDir: File): Boolean {
        return if(isAccInstalled(installationDir))
            RootShell.exec("[ -f /dev/.vr25/acc/acca ] || ${File(installationDir, "acc/service.sh").absolutePath}").isSuccess
        else
            false
    }

    suspend fun installBundledAccModule(context: Context): Shell.Result?  = withContext(Dispatchers.IO) {
        try {
            val bundleFile = File(context.filesDir, "acc_bundle.tar.gz")

            context.resources.openRawResource(R.raw.acc_bundle).use { out ->
                FileOutputStream(bundleFile).use {
                    out.copyTo(it)
                }
            }

            installLocalAccModule(context)
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
            null
        }
    }

    suspend fun installAccModuleVersion(context: Context, version: String): Shell.Result?  = withContext(Dispatchers.IO) {
        try {
            val bundleFile = File(context.filesDir, "acc_bundle.tar.gz")

            BufferedInputStream(URL("https://github.com/VR-25/acc/archive/$version.tar.gz").openStream())
                .use { inStream ->
                    FileOutputStream(bundleFile)
                        .use {
                            val buf = ByteArray(1024)
                            var bytesRead = inStream.read(buf, 0, 1024)

                            while (bytesRead != -1) {
                                it.write(buf, 0, bytesRead)
                                bytesRead = inStream.read(buf, 0, 1024)
                            }
                        }
                }

            installLocalAccModule(context)
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
            null
        }
    }

    /*
    * This function assumes that acc tar gz is already in place
    */
    private suspend fun installLocalAccModule(context: Context): Shell.Result? = withContext(Dispatchers.IO){
        try {
            val installShFile = File(context.filesDir, "install-tarball.sh")

            context.resources.openRawResource(R.raw.install).use { installer ->
                FileOutputStream(installShFile).use {
                    installer.copyTo(it)
                }
            }

            // Module installs can take a while on slow storage: generous bound.
            val res = RootShell.exec("sh ${installShFile.absolutePath} acc", RootShell.LONG_TIMEOUT_SECS)

            val version = getAccVersion() ?: throw java.lang.Exception("ACC installation failed")

            createAccInstance()

            if(version >= 202002292) {
                val preferences = Preferences(context)
                preferences.currentInputUnitOfMeasure = CurrentUnit.A
                preferences.voltageInputUnitOfMeasure = VoltageUnit.V
            } else if(version >= 202002290) {
                val preferences = Preferences(context)
                preferences.currentInputUnitOfMeasure = CurrentUnit.mA
                preferences.voltageInputUnitOfMeasure = VoltageUnit.V
            } else {
                calibrateMeasurements(context)
            }

            res
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
            null
        }
    }

    private suspend fun calibrateMeasurements(context: Context) = withContext(Dispatchers.IO) {

        var microVolts = 0
        var microAmpere = 0

        for (i in 0..10) {
            val batteryInfo = Acc.instance.getBatteryInfo()
            if(batteryInfo.getRawVoltageNow() > 1000000) microVolts++
            if(abs(batteryInfo.getRawCurrentNow()) > 10000) microAmpere++
            delay(250)
        }

        val preferences = Preferences(context)
        preferences.currentInputUnitOfMeasure = if(microAmpere >= 6) CurrentUnit.uA else CurrentUnit.mA
        preferences.voltageInputUnitOfMeasure = if(microVolts >= 6)  VoltageUnit.uV else VoltageUnit.mV
    }

    private fun getAccVersion(): Int? {
        return RootShell.exec("/dev/.vr25/acc/acc --version").out.joinToString(separator = "\n").split("(").last().split(")").first().trim().toIntOrNull() ?: getAccVersionLegacy()
    }

    fun getAccVersionToStr(): String {
        return RootShell.exec("/dev/acca --version").out.joinToString(separator = "\n").toString()
    }

    private fun getAccVersionLegacy(): Int? {
        return RootShell.exec("acc --version").out.joinToString(separator = "\n").split("(").last().split(")").first().trim().toIntOrNull()
    }
}
