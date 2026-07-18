package com.personal.hydra.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language override that works on every API level without AppCompat:
 * the chosen language tag is stored in a tiny SharedPreferences and applied in
 * MainActivity.attachBaseContext via createConfigurationContext. An empty tag
 * means "follow the device".
 */
object LocaleHelper {
    private const val PREFS = "hydra_locale"
    private const val KEY = "lang"

    fun setLocale(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    fun currentTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun wrap(base: Context): Context {
        val tag = currentTag(base)
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
