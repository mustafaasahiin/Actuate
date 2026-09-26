package com.actuate.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.UUID

/** Thin permission check without leaking Android APIs into the ViewModel. */
class PermissionState(context: Context) {
    private val appContext = context.applicationContext ?: context

    fun hasMicrophone(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Anonymous, app-scoped install ID: a random UUID generated on first launch
     * and persisted locally. No hardware/device identifiers ever leave the app.
     */
    fun deviceId(): String {
        val prefs = appContext.getSharedPreferences("actuate_device", Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null) ?: prefs.getString("installation_id", null)
        if (!existing.isNullOrBlank()) return existing
        val fresh = UUID.randomUUID().toString()
        prefs.edit()
            .putString(KEY_DEVICE_ID, fresh)
            .putString("installation_id", fresh)
            .apply()
        return fresh
    }

    fun installationId(): String = deviceId()

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
