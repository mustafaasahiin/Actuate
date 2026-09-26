package com.actuate.data.calendar

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.actuate.data.auth.GoogleCalendarAuthManager
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.repository.LocalActionStore
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CalendarOfflineSyncManager(
    private val localActionStore: LocalActionStore,
    private val googleCalendarSyncService: GoogleCalendarSyncService,
    private val googleCalendarAuthManager: GoogleCalendarAuthManager,
    private val context: Context? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {

    init {
        context?.let { ctx ->
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                runCatching {
                    cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            coroutineScope.launch {
                                runCatching { syncPendingEvents() }
                            }
                        }
                    })
                }
            }
        }
    }

    suspend fun syncPendingEvents(): Int {
        val pendingActions = localActionStore.getPendingSyncActions()
        if (!googleCalendarAuthManager.isConnected()) {
            return 0
        }

        val calendarEvents = pendingActions.filter { it.type == "calendar_event" }
        var syncedCount = 0

        for (item in calendarEvents) {
            val calendarAction = ParsedAction.Calendar(
                id = item.id,
                title = item.title,
                start = item.at ?: Instant.ofEpochMilli(item.createdAt),
                end = item.end,
                location = item.location,
                attendees = item.attendees,
                description = item.description,
            )

            val result = runCatching {
                googleCalendarSyncService.insertEvent(calendarAction)
            }.getOrNull()

            if (result != null && result.isSuccess) {
                localActionStore.markActionSynced(item.id)
                syncedCount++
            }
        }

        return syncedCount
    }
}
