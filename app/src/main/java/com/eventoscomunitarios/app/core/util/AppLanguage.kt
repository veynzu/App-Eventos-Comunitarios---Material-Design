package com.eventoscomunitarios.app.core.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Android 13 guarda la elección del sistema; versiones anteriores usan preferencias locales. */
@Singleton
class AppLanguageStore @Inject constructor(@ApplicationContext private val context: Context) {
    val languageTag: String get() = AppLanguage.languageTag(context)
    fun save(languageTag: String) = AppLanguage.save(context, languageTag)
}

object AppLanguage {
    private const val PREFERENCES = "appearance"
    private const val LANGUAGE = "language"
    val supportedTags = listOf("", "es", "en")

    fun languageTag(context: Context): String {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(LANGUAGE, "").orEmpty()
        }
        return tag.takeIf { it in supportedTags }.orEmpty()
    }

    fun save(context: Context, languageTag: String) {
        require(languageTag in supportedTags)
        // commit garantiza restaurar el idioma incluso si el proceso termina al recrear la actividad.
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(LANGUAGE, languageTag).commit()
    }

    fun wrapContext(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tag = languageTag(context)
        if (tag.isEmpty()) return context
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(configuration)
    }

    fun apply(context: Context, languageTag: String) {
        save(context, languageTag)
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(languageTag)
        } else {
            var current = context
            while (current is ContextWrapper) {
                if (current is Activity) { current.recreate(); return }
                current = current.baseContext
            }
        }
    }
}
