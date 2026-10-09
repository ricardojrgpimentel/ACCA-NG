package mattecarra.accapp.utils

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import java.util.Locale
import xml.BatteryInfoWidget
import xml.WIDGET_ALL_UPDATE

/** AppCompat owns activity updates; legacy preferences store the locale before Android 13. */
object AppLanguages {
    private const val PREFERENCE = "language"
    private const val FRAMEWORK_MIGRATED = "language_framework_migrated"

    fun normalizeTag(value: String?): String {
        val tag = value?.trim().orEmpty().replace('_', '-')
        if (tag.isEmpty() || tag == "def") return ""
        // The old picker used the country code for Greek and Android's old Indonesian code.
        val language = tag.substringBefore('-')
        val canonical = when (language) {
            "gr" -> "el" + tag.removePrefix(language)
            "in" -> "id" + tag.removePrefix(language)
            else -> tag
        }
        return Locale.forLanguageTag(canonical).toLanguageTag().takeUnless { it == "und" }.orEmpty()
    }

    fun initialize(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = normalizeTag(preferences.getString(PREFERENCE, "def"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Migrate once, preserving any language already chosen in Android settings.
            // Afterwards the framework is authoritative, including "follow system".
            if (!preferences.getBoolean(FRAMEWORK_MIGRATED, false)) {
                val manager = context.getSystemService(LocaleManager::class.java)
                if (manager.applicationLocales.isEmpty && saved.isNotEmpty()) {
                    manager.applicationLocales = LocaleList.forLanguageTags(saved)
                }
                preferences.edit().putBoolean(FRAMEWORK_MIGRATED, true).apply()
            }
        } else {
            // Custom locale storage must be restored before any activity is created.
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(saved))
        }
    }

    fun selectedTag(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
                .let { if (it.isEmpty) "" else it[0].toLanguageTag() }
        } else {
            normalizeTag(PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREFERENCE, "def"))
        }

    fun apply(context: Context, value: String) {
        val tag = normalizeTag(value)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PREFERENCE, tag.ifEmpty { "def" }).apply()
        if (selectedTag(context) != tag || AppCompatDelegate.getApplicationLocales().toLanguageTags() != tag) {
            // Recreates affected activities using their saved state; never restarts the process.
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
        context.sendBroadcast(Intent(context, BatteryInfoWidget::class.java).setAction(WIDGET_ALL_UPDATE))
    }

    /** Widgets/services need their own resource context on Android 12 and earlier. */
    fun localizedContext(context: Context): Context {
        val tag = selectedTag(context)
        val locales = if (tag.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).systemLocales
            } else Resources.getSystem().configuration.locales
        } else LocaleList.forLanguageTags(tag)
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(locales)
        })
    }
}
