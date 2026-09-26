package com.actuate.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.actuate.app.ui.auth.SignupScreen
import com.actuate.app.ui.auth.LoginScreen
import com.actuate.app.ui.home.HomeScreen
import com.actuate.app.ui.home.HomeViewModel
import com.actuate.app.ui.onboarding.LanguageSelectionScreen
import com.actuate.app.ui.settings.ConnectionsScreen
import com.actuate.app.ui.settings.SettingsScreen
import com.actuate.app.util.LocaleManager
import org.koin.compose.koinInject

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SIGNUP = "signup"
    const val LOGIN = "login"
    const val LANGUAGE_SELECTION = "language_selection"
    const val CONNECTIONS = "connections"
}

@Composable
fun ActuateNavHost(
    homeViewModel: HomeViewModel,
    micGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    val sessionStatus by homeViewModel.sessionStatus.collectAsStateWithLifecycle()
    val initialRoute = Routes.HOME

    LaunchedEffect(sessionStatus) {
        if (sessionStatus == "Login" || sessionStatus == "Signup") {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != Routes.LOGIN && currentRoute != Routes.SIGNUP) {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    val authState by homeViewModel.authState.collectAsStateWithLifecycle()
    LaunchedEffect(authState) {
        if (authState.succeeded) {
            navController.navigate(Routes.HOME) {
                popUpTo(0) { inclusive = true }
            }
            homeViewModel.consumeAuthSuccess()
        }
    }

    NavHost(
        navController = navController,
        startDestination = initialRoute,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                homeViewModel = homeViewModel,
                micGranted = micGranted,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.LANGUAGE_SELECTION) {
            val localeManager: LocaleManager = koinInject()
            LanguageSelectionScreen(
                onLanguageSelected = { selected ->
                    localeManager.setLanguage(selected)
                },
                onContinue = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LANGUAGE_SELECTION) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.CONNECTIONS) {
            ConnectionsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onResetAccount = {
                    homeViewModel.logOut()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenConnections = { navController.navigate(Routes.CONNECTIONS) },
            )
        }

        composable(Routes.SIGNUP) {
            val canGoBack = navController.previousBackStackEntry != null
            SignupScreen(
                onBack = { if (canGoBack) navController.popBackStack() },
                onSwitchToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SIGNUP) { inclusive = true }
                    }
                },
                onSignup = { email, password, name -> homeViewModel.signUp(email, password, name) },
                isLoading = authState.submitting,
                errorMessage = authState.error,
                showBackButton = canGoBack,
            )
        }

        composable(Routes.LOGIN) {
            val canGoBack = navController.previousBackStackEntry != null
            LoginScreen(
                onBack = { if (canGoBack) navController.popBackStack() },
                onSwitchToSignup = {
                    navController.navigate(Routes.SIGNUP) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onLogin = { email, password -> homeViewModel.logIn(email, password) },
                isLoading = authState.submitting,
                errorMessage = authState.error,
                showBackButton = canGoBack,
            )
        }
    }
}
