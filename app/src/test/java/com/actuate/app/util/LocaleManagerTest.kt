package com.actuate.app.util

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class LocaleManagerTest {

    @Test
    fun supportedLanguageFromCodeAlwaysReturnsEnglish() {
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("en"))
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("fr"))
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("tr"))
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("es"))
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("unknown"))
    }

    @Test
    fun alwaysResolvesEnglishLanguageOnLaunch() {
        val prefs = FakeSharedPreferences()
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs

        val manager = LocaleManager(context)
        assertEquals(SupportedLanguage.ENGLISH, manager.currentLanguage.value)
        assertFalse(manager.showLanguagePicker.value)
    }

    @Test
    fun allowsLanguageSetEnglish() {
        val prefs = FakeSharedPreferences()
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs

        val manager = LocaleManager(context)
        manager.setLanguage(SupportedLanguage.ENGLISH)

        assertEquals(SupportedLanguage.ENGLISH, manager.currentLanguage.value)
        assertEquals("en", prefs.getString("ui_language", null))
        assertFalse(manager.showLanguagePicker.value)
    }

    @Test
    fun getSavedLanguageCodeAlwaysReturnsEn() {
        val prefs = FakeSharedPreferences()
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs

        val initial = LocaleManager.getSavedLanguageCode(context)
        assertEquals("en", initial)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = FakeEditor(data)

        private class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) temp.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                temp.clear()
                return this
            }
            override fun commit(): Boolean {
                data.putAll(temp)
                return true
            }
            override fun apply() {
                data.putAll(temp)
            }
        }
    }
}
