package com.actuate.app.review

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.actuate.app.ui.calendar.CalendarContent
import com.actuate.app.ui.calendar.CalendarState
import com.actuate.app.ui.history.CaptureSessionGroup
import com.actuate.app.ui.history.CaptureStatus
import com.actuate.app.ui.history.HistoryGroupedContent
import com.actuate.app.ui.home.HomeTab
import com.actuate.app.ui.paywall.PaywallSheet
import com.actuate.app.ui.sections.*
import com.actuate.core.components.BubbleState
import com.actuate.core.components.VoiceBubble
import com.actuate.core.theme.ActuateTheme
import com.actuate.domain.model.*
import java.time.*

class DesignReviewActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "today"
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val start = today.atTime(14, 30).atZone(zone).toInstant()
        val end = start.plusSeconds(1800)
        val reminderTime = today.atTime(18, 0).atZone(zone).toInstant()

        val sampleActions = listOf(
            ParsedAction.Calendar("event", "30-minute design review", start, end, location = "Design Studio A"),
            ParsedAction.ListItem("milk", "milk", "shopping"),
            ParsedAction.ListItem("eggs", "eggs", "shopping"),
            ParsedAction.Reminder("reminder", "Send the invoice", reminderTime),
        )
        val transcript = "Tomorrow at 3 PM, schedule a 30-minute design review, add milk and eggs to my shopping list, and remind me at 6 PM to send the invoice."

        val sampleToday = listOf(
            ServerActionItem(
                id = "event-1",
                type = "calendar_event",
                title = "30-minute design review",
                at = start,
                end = end,
                location = "Design Studio A",
                pendingSync = false,
            ),
            ServerActionItem(
                id = "rem-1",
                type = "reminder",
                title = "Send the invoice",
                at = reminderTime,
                end = null,
                location = null,
                pendingSync = false,
            ),
        )

        val sampleLists = listOf(
            ServerList(
                name = "Shopping",
                items = listOf(
                    ServerListItem("item-1", "Milk", "Shopping", true, 1000L),
                    ServerListItem("item-2", "Eggs", "Shopping", false, 1100L),
                    ServerListItem("item-3", "Sourdough bread", "Shopping", false, 1200L),
                ),
            ),
            ServerList(
                name = "Work",
                items = listOf(
                    ServerListItem("item-4", "Prepare slide deck for ShipAThon", "Work", false, 2000L),
                    ServerListItem("item-5", "Review calendar sync integration", "Work", true, 2100L),
                ),
            ),
            ServerList(
                name = "Todo",
                items = listOf(
                    ServerListItem("item-6", "Call dentist for cleaning", "Todo", false, 3000L),
                ),
            ),
        )

        val sectionsState = SectionsState(
            today = if (scenario == "empty") emptyList() else sampleToday,
            lists = if (scenario == "empty") emptyList() else sampleLists,
            serverConfigured = true,
            loading = false,
        )

        val calendarState = CalendarState(
            currentMonth = YearMonth.from(today),
            selectedDate = today,
            actionsByDate = mapOf(today to sampleToday),
            selectedDayActions = sampleToday,
            serverConfigured = true,
            loading = false,
        )

        val captureGroups = listOf(
            CaptureSessionGroup(
                captureId = "cap-1",
                timestamp = Instant.now().minusSeconds(120),
                transcript = transcript,
                actions = listOf(
                    ActionRecord(
                        id = "rec-1",
                        timestamp = Instant.now().minusSeconds(120),
                        transcript = transcript,
                        summary = "30-minute design review · 14:30 - 15:00",
                        actionType = "calendar_event",
                        status = ActionStatus.DONE,
                        message = "Scheduled in Google Calendar",
                        destination = Destination.CALENDAR,
                        captureId = "cap-1",
                    ),
                    ActionRecord(
                        id = "rec-2",
                        timestamp = Instant.now().minusSeconds(115),
                        transcript = transcript,
                        summary = "Add milk and eggs to Shopping list",
                        actionType = "list_item",
                        status = ActionStatus.DONE,
                        message = "Added 2 items to Notion",
                        destination = Destination.NOTION,
                        captureId = "cap-1",
                    ),
                    ActionRecord(
                        id = "rec-3",
                        timestamp = Instant.now().minusSeconds(110),
                        transcript = transcript,
                        summary = "Remind me at 18:00 to send the invoice",
                        actionType = "reminder",
                        status = ActionStatus.DONE,
                        message = "Scheduled device alarm for 18:00",
                        destination = Destination.REMINDERS,
                        captureId = "cap-1",
                    ),
                ),
                overallStatus = CaptureStatus.ALL_SUCCESS,
            ),
        )

        val activeTab = when (scenario) {
            "lists" -> HomeTab.LISTS
            "calendar" -> HomeTab.CALENDAR
            "activity", "history" -> HomeTab.ACTIVITY
            "capture", "loading", "review", "results" -> HomeTab.ACTUATE
            else -> HomeTab.TODAY
        }

        setContent {
            ActuateTheme {
                var currentTab by remember { mutableStateOf(activeTab) }
                var showPaywall by remember { mutableStateOf(scenario == "paywall") }
                var draftText by remember {
                    mutableStateOf(if (scenario == "capture") "" else transcript)
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(currentTab.title, style = MaterialTheme.typography.titleLarge) },
                            actions = {
                                TextButton(onClick = { showPaywall = true }) {
                                    Text("Settings")
                                }
                            },
                        )
                    },
                    bottomBar = {
                        Column(Modifier.navigationBarsPadding()) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            NavigationBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp),
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp,
                            ) {
                                HomeTab.entries.forEach { tabItem ->
                                    if (tabItem == HomeTab.ACTUATE) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(68.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            VoiceBubble(
                                                state = if (scenario == "loading") BubbleState.BUSY else BubbleState.IDLE,
                                                onClick = { currentTab = HomeTab.ACTUATE },
                                                size = 52.dp,
                                                audioLevel = if (scenario == "loading") 0.65f else null,
                                            )
                                        }
                                    } else {
                                        NavigationBarItem(
                                            selected = currentTab == tabItem,
                                            onClick = { currentTab = tabItem },
                                            icon = { Icon(tabItem.icon, null, modifier = Modifier.size(22.dp)) },
                                            label = { Text(tabItem.title, style = MaterialTheme.typography.labelSmall) },
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding)) {
                        when (currentTab) {
                            HomeTab.TODAY -> TodayTab(
                                state = sectionsState,
                                onToggle = {},
                                onRefresh = {},
                                onOpenActuate = { currentTab = HomeTab.ACTUATE },
                                onTrySample = { sample ->
                                    draftText = sample
                                    currentTab = HomeTab.ACTUATE
                                },
                            )
                            HomeTab.LISTS -> ListsTab(
                                state = sectionsState,
                                onConnect = {},
                                onToggle = {},
                                onTrySample = {},
                            )
                            HomeTab.ACTUATE -> ActuateTab(
                                bubbleState = if (scenario == "loading") BubbleState.BUSY else BubbleState.IDLE,
                                statusText = if (scenario == "loading") "Understanding your thought…" else null,
                                draft = draftText,
                                onDraftChange = { draftText = it },
                                onVoiceClick = {},
                                onSubmitText = {},
                                prepared = if (scenario == "review") {
                                    ParsedActions(
                                        rawTranscript = transcript,
                                        actions = sampleActions,
                                        source = ParserSource.RULES,
                                        confidence = 1.0f,
                                    )
                                } else null,
                                onConfirm = {},
                                onEditAction = {},
                                result = if (scenario == "results") {
                                    VoiceRunResult(
                                        transcript = transcript,
                                        executed = listOf(
                                            ExecutionResult("event", Destination.CALENDAR, true, "Saved on this device · Waiting to sync"),
                                            ExecutionResult("shopping-1", Destination.NOTION, true, "Added 'milk' to Shopping list"),
                                            ExecutionResult("shopping-2", Destination.NOTION, true, "Added 'eggs' to Shopping list"),
                                            ExecutionResult("reminder", Destination.REMINDERS, true, "Reminder scheduled for 18:00"),
                                        ),
                                        remainingQuota = 2,
                                        actions = sampleActions,
                                    )
                                } else null,
                                onRetry = {},
                                onNewThought = {},
                                audioLevel = if (scenario == "loading") 0.65f else null,
                                micGranted = true,
                            )
                            HomeTab.CALENDAR -> CalendarContent(
                                state = calendarState,
                                onSelectDate = {},
                                onPreviousMonth = {},
                                onNextMonth = {},
                                onGoToToday = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            HomeTab.ACTIVITY -> HistoryGroupedContent(
                                groupedCaptures = if (scenario == "empty") emptyList() else captureGroups,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    if (showPaywall) {
                        PaywallSheet(
                            onDismiss = { showPaywall = false },
                            onUpgradeSuccess = { showPaywall = false },
                        )
                    }
                }
            }
        }
    }
}
