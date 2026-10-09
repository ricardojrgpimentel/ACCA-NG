package mattecarra.accapp

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.topjohnwu.superuser.Shell
import mattecarra.accapp.acc.Acc
import mattecarra.accapp.utils.LogExt
import mattecarra.accapp.utils.AppLanguages

class MainApplication: Application()
{
    companion object
    {
        var mDEBUG: Int = 0

        @SuppressLint("StaticFieldLeak")
        lateinit var appContext: Context
            private set

        init
        {
            Shell.Config.setFlags(Shell.FLAG_REDIRECT_STDERR)
            Shell.Config.verboseLogging(BuildConfig.DEBUG)
            Shell.Config.setTimeout(10)
        }
    }

    @SuppressLint("LogNotTimber")
    override fun onCreate()
    {
        super.onCreate()
        AppLanguages.initialize(this)
        appContext = applicationContext
        // Point Acc at this fork's files dir (applicationId changed from the
        // original mattecarra.accapp, so the path must not be hardcoded).
        Acc.initAppDirs(filesDir)
        mDEBUG = (getDefaultSharedPreferences(applicationContext).getString("appdebug", "0") ?: "0").toInt()
        LogExt().s(javaClass.simpleName, "DEBUG=$mDEBUG " +when(mDEBUG) {0->"[NONE]" 1->"[CONSOLE]" 2->"[FILE]" else->"[UNKNOWN]"})
    }
}
