package mattecarra.accapp.acc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mattecarra.accapp.utils.RootShell
import mattecarra.accapp.utils.AppLanguages

/** Identity and global preferences of the engine maintained for AccA-NG. */
object AccNg {
    const val repository = "ricardojrgpimentel/ACC-NG"
    private const val module = "/data/adb/vr25/acc/module.prop"
    private const val config = "/data/adb/vr25/acc-data/config.txt"
    private const val manager = "/dev/.vr25/acc/acca"

    fun archiveUrl(version: String): String {
        require(version == "main" || Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+-ng(?:[.-][A-Za-z0-9]+)*$").matches(version))
        return "https://github.com/$repository/archive/$version.tar.gz"
    }

    fun readContract(): NgEngineContract? {
        val result = RootShell.exec("cat $module", 5)
        return if (result.isSuccess) NgEngineContract.parse(result.out.joinToString("\n")) else null
    }

    fun isInstalled(): Boolean = RootShell.exec(
        "test \"$(sed -n 's/^ngApiVersion=//p' $module 2>/dev/null)\" = 1", 5).isSuccess

    suspend fun notificationsEnabled(): Boolean? = withContext(Dispatchers.IO) {
        if (!isInstalled()) return@withContext null
        val result = RootShell.exec("sed -n 's/^ngNotifications=//p' $config", 5)
        if (!result.isSuccess) null else result.out.firstOrNull()?.trim()?.toBooleanStrictOrNull() ?: true
    }

    private fun language(context: Context): String =
        if (AppLanguages.localizedContext(context).resources.configuration.locales[0].language.startsWith("pt")) "pt" else "en"

    suspend fun setNotificationLanguage(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isInstalled()) return@withContext false
        RootShell.execScript("async=true $manager -s ng_notification_language=${language(context)}", 10).isSuccess
    }

    suspend fun setNotifications(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isInstalled()) return@withContext false
        val result = RootShell.execScript("async=true $manager -s ng_notifications=$enabled ng_notification_language=${language(context)}", 10)
        result.isSuccess && notificationsEnabled() == enabled
    }
}
