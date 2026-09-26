package com.actuate.app

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actuate.app.ui.home.HomeViewModel
import com.actuate.app.ui.home.PermissionKind
import com.actuate.app.ui.home.UiEvent
import com.actuate.app.ui.navigation.ActuateNavHost
import com.actuate.app.util.LocaleManager
import com.actuate.app.util.PermissionState
import com.actuate.core.audio.LocalTactileSound
import com.actuate.core.audio.TactileSoundEngine
import com.actuate.core.theme.ActuateTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModel()
    private val localeManager: LocaleManager by inject()

    override fun attachBaseContext(newBase: Context) {
        val langCode = LocaleManager.getSavedLanguageCode(newBase)
        val localizedContext = LocaleManager.applyLocaleToContext(newBase, langCode)
        super.attachBaseContext(localizedContext)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val currentLanguage by localeManager.currentLanguage.collectAsStateWithLifecycle()
            val configuration = LocalConfiguration.current
            val localizedConfiguration = remember(currentLanguage, configuration) {
                Configuration(configuration).apply {
                    setLocale(Locale.forLanguageTag(currentLanguage.code))
                }
            }

            val soundEngine: TactileSoundEngine by inject()

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration,
                // One engine for the whole tree: :core components pick it up by default,
                // so no screen has to thread it through manually.
                LocalTactileSound provides soundEngine,
            ) {
                ActuateTheme {
                    var micGranted by remember {
                        mutableStateOf(
                            PermissionState(this@MainActivity).hasMicrophone(),
                        )
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                    ) { grants ->
                        val grantedMic = grants[Manifest.permission.RECORD_AUDIO] ?: micGranted
                        micGranted = grantedMic
                        homeViewModel.onPermissionResult(PermissionKind.MICROPHONE, grantedMic)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val grantedNotifications =
                                grants[Manifest.permission.POST_NOTIFICATIONS] == true
                            homeViewModel.onPermissionResult(
                                PermissionKind.NOTIFICATIONS,
                                grantedNotifications,
                            )
                        }
                    }

                    val events by homeViewModel.events.collectAsStateWithLifecycle(initialValue = null)
                    LaunchedEffect(events) {
                        when (val event = events) {
                            is UiEvent.PermissionNeeded -> when (event.kind) {
                                PermissionKind.MICROPHONE -> permissionLauncher.launch(
                                    arrayOf(Manifest.permission.RECORD_AUDIO),
                                )
                                PermissionKind.NOTIFICATIONS -> if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                    )
                                }
                            }
                            is UiEvent.Message -> Unit
                            is UiEvent.MessageRes -> Unit
                            is UiEvent.QuotaExhausted -> Unit
                            null -> Unit
                        }
                    }

                    ActuateNavHost(
                        homeViewModel = homeViewModel,
                        micGranted = micGranted,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}