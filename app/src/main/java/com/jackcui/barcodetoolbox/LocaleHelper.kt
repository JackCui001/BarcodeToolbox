package com.jackcui.barcodetoolbox

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.preference.PreferenceManager
import java.util.Locale

object LocaleHelper {

    fun applyLocale(context: Context): Context {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val language = prefs.getString("language", "system") ?: "system"
        val locale = getLocaleByValue(language)
        return updateResources(context, locale)
    }

    private fun getLocaleByValue(value: String): Locale {
        return when (value) {
            "zh_CN" -> Locale.SIMPLIFIED_CHINESE
            "zh_TW" -> Locale.TRADITIONAL_CHINESE
            "en" -> Locale.ENGLISH
            else -> Resources.getSystem().configuration.locale
        }
    }

    private fun updateResources(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }
}
