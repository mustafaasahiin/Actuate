package com.actuate.app.ui.sections

import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.model.ServerConfig
import com.actuate.domain.model.ServerList
import com.actuate.domain.model.ServerListItem
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.LocalActionStore
import com.actuate.domain.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SectionsViewModelTest {

    private val serverRepository: ServerRepository = mockk(relaxed = true)
    private val settingsRepository: AppSettingsRepository = mockk(relaxed = true)
    private val localActionStore: LocalActionStore = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    private val todayInstant: Instant = LocalDate.now(ZoneId.systemDefault())
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .plusSeconds(3600)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads local cached items so screen is never blank even if offline`() = runTest {
        val cachedAction = ServerActionItem(
            id = "act-1",
            type = "calendar_event",
            title = "Doctor Appointment",
            at = todayInstant,
            done = false,
            createdAt = 1000L,
        )
        val cachedListItem = ServerListItem(
            id = "item-1",
            text = "Apples",
            list = "Groceries",
            done = false,
            createdAt = 1000L,
        )
        coEvery { localActionStore.getActions() } returns listOf(cachedAction)
        coEvery { localActionStore.getListItems() } returns listOf(cachedListItem)
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig() // unconfigured

        val viewModel = SectionsViewModel(serverRepository, settingsRepository, localActionStore)
        val state = viewModel.state.value

        assertTrue(state.serverConfigured)
        assertEquals(1, state.today.size)
        assertEquals("Doctor Appointment", state.today.first().title)
        assertEquals(1, state.lists.size)
        assertEquals("Groceries", state.lists.first().name)
        assertEquals("Apples", state.lists.first().items.first().text)
    }

    @Test
    fun `refresh merges local cached items with server items gracefully`() = runTest {
        val localOnlyAction = ServerActionItem(
            id = "act-local",
            type = "reminder",
            title = "Drink water",
            at = todayInstant,
            done = false,
            createdAt = 1000L,
        )
        val serverAction = ServerActionItem(
            id = "act-server",
            type = "calendar_event",
            title = "Team Standup",
            at = todayInstant.plusSeconds(1800),
            done = false,
            createdAt = 2000L,
        )
        val localListItem = ServerListItem(
            id = "item-local",
            text = "Milk",
            list = "Groceries",
            done = false,
            createdAt = 1000L,
        )
        val serverListItem = ServerListItem(
            id = "item-server",
            text = "Bread",
            list = "Groceries",
            done = false,
            createdAt = 2000L,
        )

        coEvery { localActionStore.getActions() } returns listOf(localOnlyAction)
        coEvery { localActionStore.getListItems() } returns listOf(localListItem)
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig(token = "tok", userId = "u1")
        coEvery { serverRepository.fetchActions() } returns Result.success(listOf(serverAction))
        coEvery { serverRepository.fetchLists() } returns Result.success(
            listOf(ServerList("Groceries", listOf(serverListItem)))
        )

        val viewModel = SectionsViewModel(serverRepository, settingsRepository, localActionStore)
        val state = viewModel.state.value

        assertEquals(2, state.today.size)
        assertTrue(state.today.any { it.id == "act-local" })
        assertTrue(state.today.any { it.id == "act-server" })

        assertEquals(1, state.lists.size)
        val items = state.lists.first().items
        assertEquals(2, items.size)
        assertTrue(items.any { it.id == "item-local" })
        assertTrue(items.any { it.id == "item-server" })

        coVerify { localActionStore.saveActions(match { it.size == 2 }) }
        coVerify { localActionStore.saveListItems(match { it.size == 2 }) }
    }

    @Test
    fun `refresh when server is unreachable preserves local items and sets error`() = runTest {
        val localAction = ServerActionItem(
            id = "act-1",
            type = "calendar_event",
            title = "Dentist",
            at = todayInstant,
            done = false,
            createdAt = 1000L,
        )
        val localListItem = ServerListItem(
            id = "item-1",
            text = "Eggs",
            list = "Groceries",
            done = false,
            createdAt = 1000L,
        )

        coEvery { localActionStore.getActions() } returns listOf(localAction)
        coEvery { localActionStore.getListItems() } returns listOf(localListItem)
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig(token = "tok", userId = "u1")
        coEvery { serverRepository.fetchActions() } returns Result.failure(IOException("Server unreachable"))
        coEvery { serverRepository.fetchLists() } returns Result.failure(IOException("Server unreachable"))

        val viewModel = SectionsViewModel(serverRepository, settingsRepository, localActionStore)
        val state = viewModel.state.value

        assertEquals(1, state.today.size)
        assertEquals("Dentist", state.today.first().title)
        assertEquals(1, state.lists.size)
        assertEquals("Eggs", state.lists.first().items.first().text)
        assertEquals("Server unreachable", state.error)
        assertFalse(state.loading)
    }

    @Test
    fun `toggleItem updates state and local store optimistically and does not revert on offline failure`() = runTest {
        val initialItem = ServerListItem(
            id = "item-toggle",
            text = "Coffee beans",
            list = "Groceries",
            done = false,
            createdAt = 1000L,
        )
        coEvery { localActionStore.getActions() } returns emptyList()
        coEvery { localActionStore.getListItems() } returns listOf(initialItem)
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig(token = "tok", userId = "u1")
        coEvery { serverRepository.fetchActions() } returns Result.success(emptyList())
        coEvery { serverRepository.fetchLists() } returns Result.success(
            listOf(ServerList("Groceries", listOf(initialItem)))
        )
        coEvery { serverRepository.setItemDone("item-toggle", true) } returns Result.failure(IOException("Offline"))

        val viewModel = SectionsViewModel(serverRepository, settingsRepository, localActionStore)
        assertEquals(false, viewModel.state.value.lists.first().items.first().done)

        viewModel.toggleItem(initialItem)

        val updatedItem = viewModel.state.value.lists.first().items.first()
        assertTrue(updatedItem.done)
        coVerify { localActionStore.updateListItemDone("item-toggle", true) }
    }

    @Test
    fun `createList adds new empty list and undoListChange removes it`() = runTest {
        coEvery { localActionStore.getActions() } returns emptyList()
        coEvery { localActionStore.getListItems() } returns emptyList()
        coEvery { localActionStore.getListNames() } returns setOf("Work")
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig()

        val viewModel = SectionsViewModel(serverRepository, settingsRepository, localActionStore)
        assertEquals(1, viewModel.state.value.lists.size)
        assertEquals("Work", viewModel.state.value.lists.first().name)

        viewModel.createList("Personal")
        assertEquals(2, viewModel.state.value.lists.size)
        assertTrue(viewModel.state.value.lists.any { it.name == "Personal" })

        viewModel.undoListChange()
        assertEquals(1, viewModel.state.value.lists.size)
        assertFalse(viewModel.state.value.lists.any { it.name == "Personal" })
    }

    @Test
    fun `renameList updates list name and item list attribute, and undo restores it`() = runTest {
        val initialItem = ServerListItem(
            id = "item-1",
            text = "Buy milk",
            list = "Shopping",
            done = false,
            createdAt = 1000L,
        )
        coEvery { localActionStore.getActions() } returns emptyList()
        coEvery { localActionStore.getListItems() } returns listOf(initialItem)
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig()

        val viewModel = SectionsViewModel(serverRepository, settingsRepository, localActionStore)
        viewModel.renameList("Shopping", "Groceries")

        val renamedList = viewModel.state.value.lists.first()
        assertEquals("Groceries", renamedList.name)
        assertEquals("Groceries", renamedList.items.first().list)

        viewModel.undoListChange()
        val restoredList = viewModel.state.value.lists.first()
        assertEquals("Shopping", restoredList.name)
        assertEquals("Shopping", restoredList.items.first().list)
    }

    @Test
    fun `deleteList removes list and items, and undo restores it`() = runTest {
        val initialItem = ServerListItem(
            id = "item-1",
            text = "Buy milk",
            list = "Shopping",
            done = false,
            createdAt = 1000L,
        )
        coEvery { localActionStore.getActions() } returns emptyList()
        coEvery { localActionStore.getListItems() } returns listOf(initialItem)
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig()

        val viewModel = SectionsViewModel(serverRepository, settingsRepository, localActionStore)
        assertEquals(1, viewModel.state.value.lists.size)

        viewModel.deleteList("Shopping")
        assertTrue(viewModel.state.value.lists.isEmpty())

        viewModel.undoListChange()
        assertEquals(1, viewModel.state.value.lists.size)
        assertEquals("Shopping", viewModel.state.value.lists.first().name)
    }

    @Test
    fun `editItem and deleteItem modify items, and undo restores them`() = runTest {
        val initialItem = ServerListItem(
            id = "item-1",
            text = "Draft specs",
            list = "Work",
            done = false,
            createdAt = 1000L,
        )
        coEvery { localActionStore.getActions() } returns emptyList()
        coEvery { localActionStore.getListItems() } returns listOf(initialItem)
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig()

        val viewModel = SectionsViewModel(serverRepository, settingsRepository, localActionStore)
        viewModel.editItem(initialItem, "Draft architecture specs")
        assertEquals("Draft architecture specs", viewModel.state.value.lists.first().items.first().text)

        viewModel.deleteItem(viewModel.state.value.lists.first().items.first())
        assertTrue(viewModel.state.value.lists.first().items.isEmpty())

        viewModel.undoListChange()
        assertEquals(1, viewModel.state.value.lists.first().items.size)
        assertEquals("Draft architecture specs", viewModel.state.value.lists.first().items.first().text)
    }

    @Test
    fun `event scheduled for tomorrow is categorized as tomorrow and not today`() = runTest {
        val tomorrowInstant = LocalDate.now(ZoneId.systemDefault())
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .plusSeconds(3600 * 15) // 3:00 PM tomorrow

        val tomorrowAction = ServerActionItem(
            id = "act-tomorrow",
            type = "calendar_event",
            title = "Meeting with Priya",
            at = tomorrowInstant,
            done = false,
            createdAt = 1000L,
        )
        val todayAction = ServerActionItem(
            id = "act-today",
            type = "reminder",
            title = "Buy groceries",
            at = todayInstant,
            done = false,
            createdAt = 1000L,
        )

        coEvery { localActionStore.getActions() } returns listOf(tomorrowAction, todayAction)
        coEvery { localActionStore.getListItems() } returns emptyList()
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig()

        val viewModel = SectionsViewModel(serverRepository, settingsRepository, localActionStore)
        val state = viewModel.state.value

        assertEquals(1, state.today.size)
        assertEquals("Buy groceries", state.today.first().title)
        assertEquals(1, state.tomorrow.size)
        assertEquals("Meeting with Priya", state.tomorrow.first().title)
    }
}
