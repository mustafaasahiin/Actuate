package com.actuate.app

import android.app.Application
import com.actuate.app.di.appModule
import com.actuate.app.util.PermissionState
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ActuateApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            if (BuildConfig.DEBUG) {
                androidLogger()
            }
            androidContext(this@ActuateApplication)
            modules(appModule)
        }

        val resKey = runCatching {
            getString(R.string.revenuecat_public_key)
        }.getOrElse {
            val id = resources.getIdentifier("revenuecat_public_key", "string", packageName)
            if (id != 0) getString(id) else "goog_OQkpQXWyGyBKfajmLnZaOYGfzCj"
        }.ifBlank { "goog_OQkpQXWyGyBKfajmLnZaOYGfzCj" }

        runCatching {
            val deviceId = PermissionState(this).deviceId()
            Purchases.configure(
                PurchasesConfiguration.Builder(this, resKey.trim())
                    .appUserID(deviceId)
                    .build(),
            )
        }
    }
}