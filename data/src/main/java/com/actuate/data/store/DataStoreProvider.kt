package com.actuate.data.store

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** Single app-wide DataStore shared by all repositories in the data module. */
val Context.actuateDataStore by preferencesDataStore(name = "actuate_store")