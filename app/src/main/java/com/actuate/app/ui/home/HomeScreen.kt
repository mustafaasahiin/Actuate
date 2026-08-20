package com.actuate.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actuate.app.ui.history.HistoryContent
import com.actuate.app.ui.sections.ListsTab
import com.actuate.app.ui.sections.SectionsViewModel
import com.actuate.app.ui.sections.TodayTab
import com.actuate.core.components.ActuateSidebar
import com.actuate.core.theme.Carbon
import com.actuate.core.theme.Frost
import com.actuate.core.theme.Graphite
import com.actuate.core.theme.Spacing
import org.koin.androidx.compose.koinViewModel

enum class HomeTab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.Today),
    LISTS("Lists", Icons.Rounded.Checklist),
    HISTORY("History", Icons.Rounded.History),
}

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    micGranted: Boolean,
    onOpenSettings: () -> Unit,
) {
    val sectionsViewModel: SectionsViewModel = koinViewModel()
    val sidebarState by homeViewModel.sidebarState.collectAsStateWithLifecycle()
    val bubbleState by homeViewModel.bubbleState.collectAsStateWithLifecycle()
    val statusText by homeViewModel.statusText.collectAsStateWithLifecycle()
    val quotaText by homeViewModel.quotaText.collectAsStateWithLifecycle()
    val recentHistory by homeViewModel.recentHistory.collectAsStateWithLifecycle()
    val executionTick by homeViewModel.executionTick.collectAsStateWithLifecycle()
    val sectionsState by sectionsViewModel.state.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(HomeTab.TODAY.ordinal) }
    val currentTab = HomeTab.entries[selectedTab]

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        homeViewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is UiEvent.PermissionNeeded -> Unit
            }
        }
    }

    LaunchedEffect(Unit) { sectionsViewModel.refresh() }
    LaunchedEffect(executionTick) {
        if (executionTick > 0) sectionsViewModel.refresh()
    }

    Box(modifier = Modifier.fillMaxSize().background(Frost)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header + tab content.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xxl, bottom = Spacing.md),
            ) {
                Text(
                    text = "Actuate",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Carbon,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Speak. Actuate.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Graphite,
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentTab) {
                    HomeTab.TODAY -> TodayTab(sectionsState, onConnect = onOpenSettings)
                    HomeTab.LISTS -> ListsTab(
                        state = sectionsState,
                        onConnect = onOpenSettings,
                        onToggle = sectionsViewModel::toggleItem,
                    )
                    HomeTab.HISTORY -> HistoryContent()
                }
            }

            // Bottom tab bar.
            NavigationBar(
                containerColor = Frost,
                modifier = Modifier.navigationBarsPadding(),
            ) {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { selectedTab = tab.ordinal },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }

        // Right-edge floating sidebar with the voice bubble.
        ActuateSidebar(
            state = sidebarState,
            bubbleState = bubbleState,
            statusText = statusText,
            quotaText = quotaText,
            onToggle = homeViewModel::toggleSidebar,
            onVoiceClick = homeViewModel::onVoiceTap,
            onHistoryClick = { selectedTab = HomeTab.HISTORY.ordinal },
            onSettingsClick = onOpenSettings,
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xxl),
        )
    }
}