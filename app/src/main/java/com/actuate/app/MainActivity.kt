package com.actuate.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actuate.app.ui.navigation.ActuateNavHost
import com.actuate.app.ui.home.HomeViewModel
import com.actuate.app.ui.home.PermissionKind
import com.actuate.app.ui.home.UiEvent
import com.actuate.core.theme.ActuateTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ActuateTheme {
                var micGranted by remember {
                    mutableStateOf(
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { grants ->
                    // Only update mic state when the mic was actually requested,
                    // so a notifications-only result can't flip the UI.
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
                        is UiEvent.Message -> Unit // handled by each screen's snackbar
                        null -> Unit
                    }
                }

                ActuateNavHost(
                    homeViewModel = homeViewModel,
                    micGranted = micGranted,
                    modifier = Modifier,
                )
            }
        }
    }
}