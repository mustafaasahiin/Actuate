package com.actuate.app.ui.calendar

import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.model.ServerConfig
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.LocalActionStore
import com.actuate.domain.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val serverRepository: ServerRepository = mockk(relaxed = true)
    private val settingsRepository: AppSettingsRepository = mockk(relaxed = true)
    private val localActionStore: LocalActionStore = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    private val today = LocalDate.now()
    private val zone = ZoneId.systemDefault()
    private val todayInstant = today.atTime(10, 0).atZone(zone).toInstant()
    private val tomorrowInstant = today.plusDays(1).atTime(14, 0).atZone(zone).toInstant()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializes with cached actions grouped by date and selects today`() = runTest {
        val actionToday = ServerActionItem(
            id = "cal-1",
            type = "calendar_event",
            title = "Morning Standup",
            at = todayInstant,
            done = false,
            createdAt = 1000L,
        )
        val actionTomorrow = ServerActionItem(
            id = "rem-1",
            type = "reminder",
            title = "Doctor Appointment",
            at = tomorrowInstant,
            done = false,
            createdAt = 1000L,
        )

        coEvery { localActionStore.getActions() } returns listOf(actionToday, actionTomorrow)
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig()

        val viewModel = CalendarViewModel(serverRepository, settingsRepository, localActionStore)
        val state = viewModel.state.value

        assertEquals(today, state.selectedDate)
        assertEquals(YearMonth.from(today), state.currentMonth)
        assertEquals(1, state.selectedDayActions.size)
        assertEquals("Morning Standup", state.selectedDayActions.first().title)
        assertEquals(2, state.actionsByDate.size)
        assertTrue(state.actionsByDate.containsKey(today))
        assertTrue(state.actionsByDate.containsKey(today.plusDays(1)))
    }

    @Test
    fun `selectDate updates selectedDate and selectedDayActions`() = runTest {
        val actionTomorrow = ServerActionItem(
            id = "rem-1",
            type = "reminder",
            title = "Doctor Appointment",
            at = tomorrowInstant,
            done = false,
            createdAt = 1000L,
        )

        coEvery { localActionStore.getActions() } returns listOf(actionTomorrow)
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig()

        val viewModel = CalendarViewModel(serverRepository, settingsRepository, localActionStore)
        assertEquals(0, viewModel.state.value.selectedDayActions.size)

        val tomorrow = today.plusDays(1)
        viewModel.selectDate(tomorrow)

        val state = viewModel.state.value
        assertEquals(tomorrow, state.selectedDate)
        assertEquals(1, state.selectedDayActions.size)
        assertEquals("Doctor Appointment", state.selectedDayActions.first().title)
    }

    @Test
    fun `previousMonth nextMonth and goToToday manipulate currentMonth and selection`() = runTest {
        coEvery { localActionStore.getActions() } returns emptyList()
        coEvery { settingsRepository.readServerConfig() } returns ServerConfig()

        val viewModel = CalendarViewModel(serverRepository, settingsRepository, localActionStore)
        val initialMonth = viewModel.state.value.currentMonth

        viewModel.previousMonth()
        assertEquals(initialMonth.minusMonths(1), viewModel.state.value.currentMonth)

        viewModel.nextMonth()
        assertEquals(initialMonth, viewModel.state.value.currentMonth)

        viewModel.nextMonth()
        assertEquals(initialMonth.plusMonths(1), viewModel.state.value.currentMonth)

        viewModel.goToToday()
        assertEquals(initialMonth, viewModel.state.value.currentMonth)
        assertEquals(today, viewModel.state.value.selectedDate)
    }
}
