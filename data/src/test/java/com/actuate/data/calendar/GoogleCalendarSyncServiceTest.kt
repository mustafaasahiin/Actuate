package com.actuate.data.calendar

import com.actuate.data.auth.GoogleCalendarAuthManager
import com.actuate.domain.model.Attendee
import com.actuate.domain.model.ParsedAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoogleCalendarSyncServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var authManager: GoogleCalendarAuthManager
    private lateinit var service: GoogleCalendarSyncService
    private val testDispatcher = StandardTestDispatcher()

    private val testStart = Instant.parse("2026-09-08T10:00:00Z")
    private val testEnd = Instant.parse("2026-09-08T11:00:00Z")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        authManager = mockk()
        service = GoogleCalendarSyncService(
            client = OkHttpClient.Builder().build(),
            authManager = authManager,
            apiBaseUrl = server.url("/").toString(),
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `insertEvent success for timed event with attendees and location`() = runTest(testDispatcher) {
        coEvery { authManager.getAccessToken(forceRefresh = false) } returns Result.success("valid-token")

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"evt-123","htmlLink":"https://calendar.google.com/event?eid=123"}"""),
        )

        val action = ParsedAction.Calendar(
            id = "act-1",
            title = "Engineering Standup",
            start = testStart,
            end = testEnd,
            allDay = false,
            location = "Room 101",
            description = "Daily sync",
            attendees = listOf(Attendee(name = "Dev", email = "dev@example.com")),
        )

        val result = service.insertEvent(action)

        assertTrue(result.isSuccess)
        val eventResult = result.getOrThrow()
        assertEquals("evt-123", eventResult.eventId)
        assertEquals("evt-123", eventResult.id)
        assertEquals("https://calendar.google.com/event?eid=123", eventResult.htmlLink)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/calendar/v3/calendars/primary/events", request.path)
        assertEquals("Bearer valid-token", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")?.startsWith("application/json") == true)

        val payload = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("Engineering Standup", payload["summary"]?.jsonPrimitive?.content)
        assertEquals("Daily sync", payload["description"]?.jsonPrimitive?.content)
        assertEquals("Room 101", payload["location"]?.jsonPrimitive?.content)

        val startObj = payload["start"]?.jsonObject
        assertNotNull(startObj)
        assertEquals("2026-09-08T10:00:00Z", startObj!!["dateTime"]?.jsonPrimitive?.content)

        val endObj = payload["end"]?.jsonObject
        assertNotNull(endObj)
        assertEquals("2026-09-08T11:00:00Z", endObj!!["dateTime"]?.jsonPrimitive?.content)

        val attendees = payload["attendees"]?.jsonArray
        assertNotNull(attendees)
        assertEquals(1, attendees!!.size)
        val firstAttendee = attendees[0].jsonObject
        assertEquals("dev@example.com", firstAttendee["email"]?.jsonPrimitive?.content)
        assertEquals("Dev", firstAttendee["displayName"]?.jsonPrimitive?.content)
    }

    @Test
    fun `insertEvent defaults end time to 1 hour after start when end is null`() = runTest(testDispatcher) {
        coEvery { authManager.getAccessToken(forceRefresh = false) } returns Result.success("valid-token")

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"evt-456"}"""),
        )

        val action = ParsedAction.Calendar(
            id = "act-2",
            title = "Quick Coffee",
            start = testStart,
            end = null,
            allDay = false,
        )

        val result = service.insertEvent(action)

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        val payload = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

        val startObj = payload["start"]?.jsonObject
        val endObj = payload["end"]?.jsonObject
        assertEquals("2026-09-08T10:00:00Z", startObj!!["dateTime"]?.jsonPrimitive?.content)
        assertEquals("2026-09-08T11:00:00Z", endObj!!["dateTime"]?.jsonPrimitive?.content)
    }

    @Test
    fun `insertEvent handles allDay event with date field instead of dateTime`() = runTest(testDispatcher) {
        coEvery { authManager.getAccessToken(forceRefresh = false) } returns Result.success("valid-token")

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"evt-allday-789"}"""),
        )

        val action = ParsedAction.Calendar(
            id = "act-3",
            title = "Company Holiday",
            start = testStart,
            end = null,
            allDay = true,
        )

        val result = service.insertEvent(action)

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        val payload = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

        val startObj = payload["start"]?.jsonObject
        val endObj = payload["end"]?.jsonObject
        assertNotNull(startObj!!["date"])
        assertFalse(startObj.containsKey("dateTime"))
        assertNotNull(endObj!!["date"])
        assertFalse(endObj.containsKey("dateTime"))
    }

    @Test
    fun `insertEvent attempts forceRefresh if initial token fetch fails`() = runTest(testDispatcher) {
        coEvery { authManager.getAccessToken(forceRefresh = false) } returns Result.failure(IllegalStateException("Expired cached token"))
        coEvery { authManager.getAccessToken(forceRefresh = true) } returns Result.success("refreshed-token")

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"evt-refreshed"}"""),
        )

        val action = ParsedAction.Calendar(
            id = "act-4",
            title = "Status Meeting",
            start = testStart,
            end = testEnd,
        )

        val result = service.insertEvent(action)

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("Bearer refreshed-token", request.getHeader("Authorization"))
        coVerify(exactly = 1) { authManager.getAccessToken(forceRefresh = true) }
    }

    @Test
    fun `insertEvent retries once with forceRefresh on HTTP 401 response`() = runTest(testDispatcher) {
        coEvery { authManager.getAccessToken(forceRefresh = false) } returns Result.success("expired-token")
        coEvery { authManager.getAccessToken(forceRefresh = true) } returns Result.success("new-token")

        // First attempt returns 401
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":401,"message":"Invalid Credentials"}}"""))
        // Second attempt returns 200
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"evt-after-401"}"""))

        val action = ParsedAction.Calendar(
            id = "act-5",
            title = "Sync with Team",
            start = testStart,
            end = testEnd,
        )

        val result = service.insertEvent(action)

        assertTrue(result.isSuccess)
        assertEquals("evt-after-401", result.getOrThrow().eventId)
        coVerify(exactly = 1) { authManager.getAccessToken(forceRefresh = true) }

        val firstReq = server.takeRequest()
        assertEquals("Bearer expired-token", firstReq.getHeader("Authorization"))
        val secondReq = server.takeRequest()
        assertEquals("Bearer new-token", secondReq.getHeader("Authorization"))
    }

    @Test
    fun `insertEvent returns GoogleCalendarAuthException when 401 retry also fails with 401`() = runTest(testDispatcher) {
        coEvery { authManager.getAccessToken(forceRefresh = false) } returns Result.success("token-1")
        coEvery { authManager.getAccessToken(forceRefresh = true) } returns Result.success("token-2")

        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val action = ParsedAction.Calendar(
            id = "act-6",
            title = "Doctor Appointment",
            start = testStart,
            end = testEnd,
        )

        val result = service.insertEvent(action)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is GoogleCalendarAuthException)
        assertTrue(exception!!.message!!.contains("reconnection required"))
    }

    @Test
    fun `insertEvent returns GoogleCalendarAuthException when token refresh fails after 401`() = runTest(testDispatcher) {
        coEvery { authManager.getAccessToken(forceRefresh = false) } returns Result.success("token-1")
        coEvery { authManager.getAccessToken(forceRefresh = true) } returns Result.failure(IllegalStateException("Revoked"))

        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val action = ParsedAction.Calendar(
            id = "act-7",
            title = "Dentist",
            start = testStart,
            end = testEnd,
        )

        val result = service.insertEvent(action)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is GoogleCalendarAuthException)
        assertTrue(exception!!.message!!.contains("reconnection required"))
    }

    @Test
    fun `insertEvent returns GoogleCalendarApiException on HTTP 400 Bad Request`() = runTest(testDispatcher) {
        coEvery { authManager.getAccessToken(forceRefresh = false) } returns Result.success("valid-token")

        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"Bad Request"}}"""))

        val action = ParsedAction.Calendar(
            id = "act-8",
            title = "Bad Event",
            start = testStart,
            end = testEnd,
        )

        val result = service.insertEvent(action)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is GoogleCalendarApiException)
        assertEquals(400, (exception as GoogleCalendarApiException).code)
    }

    @Test
    fun `insertEvent returns failure with IOException on network disconnection`() = runTest(testDispatcher) {
        coEvery { authManager.getAccessToken(forceRefresh = false) } returns Result.success("valid-token")

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val action = ParsedAction.Calendar(
            id = "act-9",
            title = "Offline Event",
            start = testStart,
            end = testEnd,
        )

        val result = service.insertEvent(action)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Expected IOException or cause to be IOException, got $exception", exception is IOException || exception?.cause is IOException)
    }
}
