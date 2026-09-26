package com.actuate.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actuate.app.ui.calendar.CalendarScreen
import com.actuate.app.ui.calendar.CalendarViewModel
import com.actuate.app.ui.history.HistoryContent
import com.actuate.app.ui.paywall.PaywallSheet
import com.actuate.app.ui.sections.*
import com.actuate.core.components.*
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Ash
import com.actuate.core.theme.AshDark
import com.actuate.core.theme.Capsule
import com.actuate.core.theme.DeepGraphite
import com.actuate.core.theme.ElevatedDark
import com.actuate.core.theme.Frost
import com.actuate.core.theme.Ice
import com.actuate.core.theme.Mist
import com.actuate.core.theme.MistDark
import com.actuate.core.theme.Onyx
import com.actuate.core.theme.Spacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class HomeTab(val title: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.Today),
    LISTS("Lists", Icons.Rounded.Checklist),
    ACTUATE("Actuate", Icons.Rounded.Mic),
    CALENDAR("Calendar", Icons.Rounded.CalendarToday),
    ACTIVITY("Activity", Icons.Rounded.History),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    micGranted: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sections: SectionsViewModel = koinViewModel()
    val calendar: CalendarViewModel = koinViewModel()
    val isDark = isSystemInDarkTheme()

    val state by sections.state.collectAsStateWithLifecycle()
    val bubble by homeViewModel.bubbleState.collectAsStateWithLifecycle()
    val status by homeViewModel.statusText.collectAsStateWithLifecycle()
    val draft by homeViewModel.draft.collectAsStateWithLifecycle()
    val result by homeViewModel.captureResult.collectAsStateWithLifecycle()
    val prepared by homeViewModel.prepared.collectAsStateWithLifecycle()
    val audioLevel by homeViewModel.audioLevel.collectAsStateWithLifecycle()
    val audioWaveform by homeViewModel.audioWaveform.collectAsStateWithLifecycle()
    val prepareLatencyMs by homeViewModel.prepareLatencyMs.collectAsStateWithLifecycle()
    val tick by homeViewModel.executionTick.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(HomeTab.TODAY.ordinal) }
    var paywall by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tick) {
        if (tick > 0) {
            sections.refresh()
            calendar.refresh()
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.events.collect { event ->
            when (event) {
                is UiEvent.QuotaExhausted -> paywall = true
                is UiEvent.Message -> snackbar.showSnackbar(event.text)
                is UiEvent.MessageRes -> snackbar.showSnackbar(context.getString(event.resId))
                else -> Unit
            }
        }
    }

    val canvasBackground = MaterialTheme.colorScheme.background
    val navContainerColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBackground),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    color = if (isDark) AppleBlue.copy(alpha = 0.2f) else AppleBlue.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = when (HomeTab.entries[tab]) {
                                    HomeTab.TODAY -> Icons.Rounded.Today
                                    HomeTab.LISTS -> Icons.Rounded.Checklist
                                    HomeTab.ACTUATE -> Icons.Rounded.GraphicEq
                                    HomeTab.CALENDAR -> Icons.Rounded.CalendarToday
                                    HomeTab.ACTIVITY -> Icons.Rounded.History
                                },
                                contentDescription = null,
                                tint = AppleBlue,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = HomeTab.entries[tab].title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                            ),
                        )
                    }
                },
                actions = {
                    Surface(
                        onClick = onOpenSettings,
                        shape = Capsule,
                        color = if (isDark) ElevatedDark else Ice,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .padding(end = Spacing.xs)
                            .height(34.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = AppleBlue,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = canvasBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (HomeTab.entries[tab]) {
                    HomeTab.TODAY -> TodayTab(
                        state = state,
                        onToggle = sections::toggleItem,
                        onRefresh = sections::refresh,
                        onOpenActuate = {
                            tab = HomeTab.ACTUATE.ordinal
                            if (bubble == BubbleState.IDLE) {
                                homeViewModel.onVoiceTap()
                            }
                        },
                        onTrySample = { sample ->
                            homeViewModel.updateDraft(sample)
                            tab = HomeTab.ACTUATE.ordinal
                            homeViewModel.submitTextCommand(sample)
                        },
                    )
                    HomeTab.LISTS -> ListsTab(
                        state = state,
                        onConnect = onOpenSettings,
                        onToggle = sections::toggleItem,
                        onAddItem = sections::addItem,
                        onTrySample = { sample ->
                            homeViewModel.updateDraft(sample)
                            tab = HomeTab.ACTUATE.ordinal
                            homeViewModel.submitTextCommand(sample)
                        },
                        onCreateList = sections::createList,
                        onRenameList = sections::renameList,
                        onDeleteList = { listName ->
                            sections.deleteList(listName)
                            scope.launch {
                                val res = snackbar.showSnackbar(
                                    message = "Deleted list \"$listName\"",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short,
                                )
                                if (res == SnackbarResult.ActionPerformed) {
                                    sections.undoListChange()
                                }
                            }
                        },
                        onEditItem = sections::editItem,
                        onDeleteItem = { item ->
                            sections.deleteItem(item)
                            scope.launch {
                                val res = snackbar.showSnackbar(
                                    message = "Deleted \"${item.text}\"",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short,
                                )
                                if (res == SnackbarResult.ActionPerformed) {
                                    sections.undoListChange()
                                }
                            }
                        },
                        onRefresh = sections::refresh,
                    )
                    HomeTab.ACTUATE -> ActuateTab(
                        bubbleState = bubble,
                        statusText = status,
                        draft = draft,
                        onDraftChange = homeViewModel::updateDraft,
                        onVoiceClick = homeViewModel::onVoiceTap,
                        onSubmitText = homeViewModel::submitTextCommand,
                        prepared = prepared,
                        onConfirm = homeViewModel::confirmActions,
                        onEditAction = homeViewModel::editAction,
                        result = result,
                        onRetry = homeViewModel::retryFailed,
                        onNewThought = homeViewModel::newThought,
                        audioLevel = audioLevel,
                        audioWaveform = audioWaveform,
                        prepareLatencyMs = prepareLatencyMs,
                        micGranted = micGranted,
                    )
                    HomeTab.CALENDAR -> CalendarScreen(
                        viewModel = calendar,
                        onRefresh = calendar::refresh,
                    )
                    HomeTab.ACTIVITY -> HistoryContent()
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = navContainerColor,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    NavigationBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        containerColor = navContainerColor,
                        tonalElevation = 0.dp,
                    ) {
                        HomeTab.entries.forEachIndexed { index, item ->
                            if (item == HomeTab.ACTUATE) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(68.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    VoiceBubble(
                                        state = bubble,
                                        onClick = {
                                            tab = HomeTab.ACTUATE.ordinal
                                            homeViewModel.onVoiceTap()
                                        },
                                        size = 50.dp,
                                        audioLevel = audioLevel,
                                        waveform = audioWaveform,
                                    )
                                }
                            } else {
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = {
                                        tab = index
                                        if (bubble == BubbleState.BUSY) {
                                            homeViewModel.resetVoiceState()
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.title,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Medium,
                                            ),
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.12f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )
    }

    if (paywall) {
        PaywallSheet(
            onDismiss = { paywall = false },
            onUpgradeSuccess = { paywall = false },
            onUnlockPro = { homeViewModel.upgradeToPro("SHIPATON2026") },
        )
    }
}
