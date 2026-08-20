package com.actuate.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.actuate.app.ui.home.HomeScreen
import com.actuate.app.ui.home.HomeViewModel
import com.actuate.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun ActuateNavHost(
    homeViewModel: HomeViewModel,
    micGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                homeViewModel = homeViewModel,
                micGranted = micGranted,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onResetAccount = {
                    navController.popBackStack()
                    homeViewModel.resetSession()
                },
            )
        }
    }
}