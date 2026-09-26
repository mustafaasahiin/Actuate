package com.actuate.app.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class SupportedLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String?): SupportedLanguage = ENGLISH
    }
}

class LocaleManager(context: Context) {

    private val prefs: SharedPreferences =
        (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentLanguage = MutableStateFlow(SupportedLanguage.ENGLISH)
    val currentLanguage: StateFlow<SupportedLanguage> = _currentLanguage

    private val _currentLocale = MutableStateFlow(Locale.ENGLISH)
    val currentLocale: StateFlow<Locale> = _currentLocale

    private val _showLanguagePicker = MutableStateFlow(false)
    val showLanguagePicker: StateFlow<Boolean> = _showLanguagePicker

    init {
        Locale.setDefault(Locale.ENGLISH)
        _currentLocale.value = Locale.ENGLISH
    }

    fun setLanguage(language: SupportedLanguage) {
        prefs.edit()
            .putString(KEY_LANGUAGE, SupportedLanguage.ENGLISH.code)
            .putBoolean(KEY_INITIALIZED, true)
            .putBoolean(KEY_SHOW_PICKER, false)
            .apply()
        _currentLanguage.value = SupportedLanguage.ENGLISH
        _showLanguagePicker.value = false
        applyLocale(SupportedLanguage.ENGLISH.code)
    }

    fun dismissPicker(selectedLanguage: SupportedLanguage? = null) {
        setLanguage(SupportedLanguage.ENGLISH)
    }

    fun applyLocaleToContext(context: Context, languageCode: String): Context {
        Locale.setDefault(Locale.ENGLISH)
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.ENGLISH)
        return context.createConfigurationContext(config)
    }

    private fun applyLocale(languageCode: String) {
        Locale.setDefault(Locale.ENGLISH)
        _currentLocale.value = Locale.ENGLISH
    }

    companion object {
        const val PREFS_NAME = "actuate_locale_prefs"
        const val KEY_LANGUAGE = "ui_language"
        private const val KEY_INITIALIZED = "locale_initialized"
        private const val KEY_SHOW_PICKER = "show_language_picker"

        fun getSavedLanguageCode(context: Context): String = "en"

        fun applyLocaleToContext(context: Context, languageCode: String): Context {
            Locale.setDefault(Locale.ENGLISH)
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale.ENGLISH)
            return context.createConfigurationContext(config)
        }
    }
}
