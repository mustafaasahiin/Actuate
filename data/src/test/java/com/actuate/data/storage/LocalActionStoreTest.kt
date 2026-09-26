package com.actuate.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.actuate.domain.model.Attendee
import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.model.ServerListItem
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalActionStoreTest {

    private lateinit var store: LocalActionStore

    @Before
    fun setUp() {
        val inMemoryDataStore = InMemoryPreferencesDataStore()
        store = LocalActionStore(inMemoryDataStore)
    }

    @Test
    fun `saveActions and getActions round-trip correctly`() = runTest {
        val now = Instant.parse("2026-09-07T12:00:00Z")
        val actions = listOf(
            ServerActionItem(
                id = "act-1",
                type = "calendar_event",
                title = "Meeting with Alice",
                at = now,
                done = false,
                createdAt = 1000L,
            ),
            ServerActionItem(
                id = "act-2",
                type = "reminder",
                title = "Call Bob",
                at = now.plusSeconds(3600),
                done = true,
                createdAt = 2000L,
            ),
        )

        store.saveActions(actions)
        val loaded = store.getActions()

        assertEquals(2, loaded.size)
        assertEquals("act-1", loaded[0].id)
        assertEquals("Meeting with Alice", loaded[0].title)
        assertEquals("calendar_event", loaded[0].type)
        assertEquals(now, loaded[0].at)
        assertFalse(loaded[0].done)

        assertEquals("act-2", loaded[1].id)
        assertEquals("Call Bob", loaded[1].title)
        assertEquals("reminder", loaded[1].type)
        assertTrue(loaded[1].done)
    }

    @Test
    fun `addAction appends new action and updates existing action`() = runTest {
        val now = Instant.parse("2026-09-07T12:00:00Z")
        val action1 = ServerActionItem("act-1", "reminder", "Initial Title", now, false, 1000L)
        store.addAction(action1)

        val loaded1 = store.getActions()
        assertEquals(1, loaded1.size)
        assertEquals("Initial Title", loaded1[0].title)

        val action1Updated = ServerActionItem("act-1", "reminder", "Updated Title", now, true, 1000L)
        store.addAction(action1Updated)

        val loaded2 = store.getActions()
        assertEquals(1, loaded2.size)
        assertEquals("Updated Title", loaded2[0].title)
        assertTrue(loaded2[0].done)

        val action2 = ServerActionItem("act-2", "calendar_event", "Second", now, false, 2000L)
        store.addAction(action2)

        val loaded3 = store.getActions()
        assertEquals(2, loaded3.size)
    }

    @Test
    fun `saveListItems, addListItem, and updateListItemDone work correctly`() = runTest {
        val item1 = ServerListItem("item-1", "Buy Milk", "Shopping", false, 1000L)
        val item2 = ServerListItem("item-2", "Eggs", "Shopping", false, 1000L)

        store.saveListItems(listOf(item1, item2))
        val loaded1 = store.getListItems()
        assertEquals(2, loaded1.size)

        store.updateListItemDone("item-1", true)
        val loaded2 = store.getListItems()
        assertTrue(loaded2.first { it.id == "item-1" }.done)
        assertFalse(loaded2.first { it.id == "item-2" }.done)

        val item3 = ServerListItem("item-3", "Bread", "Groceries", false, 2000L)
        store.addListItem(item3)
        val loaded3 = store.getListItems()
        assertEquals(3, loaded3.size)
    }

    @Test
    fun `clear removes all actions and list items`() = runTest {
        store.addAction(ServerActionItem("act-1", "reminder", "Task", null, false, 1000L))
        store.addListItem(ServerListItem("item-1", "Item", "List", false, 1000L))

        assertEquals(1, store.getActions().size)
        assertEquals(1, store.getListItems().size)

        store.clear()

        assertTrue(store.getActions().isEmpty())
        assertTrue(store.getListItems().isEmpty())
    }

    @Test
    fun `saveActions and getActions preserve end, location, description, and pendingSync`() = runTest {
        val start = Instant.parse("2026-09-07T14:00:00Z")
        val end = Instant.parse("2026-09-07T15:00:00Z")
        val item = ServerActionItem(
            id = "cal-offline-1",
            type = "calendar_event",
            title = "Design Review",
            at = start,
            end = end,
            location = "Room 101",
            description = "Sprint 4 review",
            pendingSync = true,
            done = false,
            createdAt = 1234L,
        )

        store.addAction(item)
        val loaded = store.getActions()

        assertEquals(1, loaded.size)
        val loadedItem = loaded.first()
        assertEquals("cal-offline-1", loadedItem.id)
        assertEquals("Design Review", loadedItem.title)
        assertEquals(start, loadedItem.at)
        assertEquals(end, loadedItem.end)
        assertEquals("Room 101", loadedItem.location)
        assertEquals("Sprint 4 review", loadedItem.description)
        assertTrue(loadedItem.pendingSync)
    }

    @Test
    fun `getPendingSyncActions and markActionSynced manage pending sync queue`() = runTest {
        val start = Instant.parse("2026-09-07T14:00:00Z")
        val item1 = ServerActionItem(
            id = "pending-1",
            type = "calendar_event",
            title = "Offline Event 1",
            at = start,
            end = start.plusSeconds(3600),
            pendingSync = true,
        )
        val item2 = ServerActionItem(
            id = "synced-1",
            type = "calendar_event",
            title = "Online Event",
            at = start,
            end = start.plusSeconds(3600),
            pendingSync = false,
        )
        store.saveActions(listOf(item1, item2))

        val pending = store.getPendingSyncActions()
        assertEquals(1, pending.size)
        assertEquals("pending-1", pending.first().id)

        store.markActionSynced("pending-1")

        val pendingAfter = store.getPendingSyncActions()
        assertTrue(pendingAfter.isEmpty())

        val allActions = store.getActions()
        val updated = allActions.first { it.id == "pending-1" }
        assertFalse(updated.pendingSync)
    }

    @Test
    fun `saveActions and getActions preserve attendee name and email`() = runTest {
        val start = Instant.parse("2026-09-08T16:00:00Z")
        val attendees = listOf(
            Attendee(name = "Sarah", email = "sarah@example.com"),
            Attendee(name = "John", email = null),
        )
        val item = ServerActionItem(
            id = "cal-attendees-1",
            type = "calendar_event",
            title = "Meeting with Sarah",
            at = start,
            end = start.plusSeconds(3600),
            location = "Main Office",
            attendees = attendees,
            description = "Discussion on project scope",
            pendingSync = false,
            done = false,
            createdAt = 12345L,
        )

        store.addAction(item)
        val loaded = store.getActions()

        assertEquals(1, loaded.size)
        val loadedItem = loaded.first()
        assertEquals("cal-attendees-1", loadedItem.id)
        assertEquals("Meeting with Sarah", loadedItem.title)
        assertEquals(2, loadedItem.attendees.size)
        assertEquals("Sarah", loadedItem.attendees[0].name)
        assertEquals("sarah@example.com", loadedItem.attendees[0].email)
        assertEquals("John", loadedItem.attendees[1].name)
        assertNull(loadedItem.attendees[1].email)
    }

    private class InMemoryPreferencesDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
