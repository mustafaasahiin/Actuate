package com.actuate.data.network

import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ParserSource
import com.actuate.domain.model.ServerConfig
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActuateServerApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ActuateServerApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ActuateServerApi(
            client = OkHttpClient.Builder().build(),
            baseUrl = server.url("/").toString().trimEnd('/'),
            configProvider = {
                ServerConfig(baseUrl = server.url("/").toString().trimEnd('/'), token = "test-token")
            },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `register returns the session`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"userId":"u1","name":"Ada","email":"ada@example.com","isPro":false,
                        "quotaLimit":3,"quotaRemaining":3,"integrations":{},"token":"tok-1"}""",
                ),
        )

        val result = api.register("Ada", "ada@example.com", "device-1")

        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("u1", session.userId)
        assertEquals("tok-1", session.token)
        assertEquals(3, session.quotaRemaining)
        assertFalse(session.isPro)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.startsWith("/api/v1/auth/register"))
    }

    @Test
    fun `parse maps LLM actions to domain models`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"actions":[{"type":"calendar_event","title":"Gym","start":"2026-08-21T17:00:00Z",
                        "location":"Fitness Club","attendees":["Alex"]},
                        {"type":"list_item","text":"buy chicken","list":"shopping"},
                        {"type":"reminder","title":"pay rent","due_at":"2026-08-21T09:00:00Z"}],
                        "source":"llm","confidence":1.0}""",
                ),
        )

        val result = api.parse("Schedule a gym session with Alex tomorrow at 5 PM")

        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals(ParserSource.LLM, parsed.source)
        assertEquals(3, parsed.actions.size)

        val calendar = parsed.actions[0] as ParsedAction.Calendar
        assertEquals("Gym", calendar.title)
        assertEquals(listOf("Alex"), calendar.attendees)

        val listItem = parsed.actions[1] as ParsedAction.ListItem
        assertEquals("buy chicken", listItem.text)
        assertEquals("shopping", listItem.list)

        val reminder = parsed.actions[2] as ParsedAction.Reminder
        assertEquals(Instant.parse("2026-08-21T09:00:00Z"), reminder.dueAt)

        val recorded = server.takeRequest()
        assertTrue(recorded.getHeader("Authorization")!!.startsWith("Bearer "))
    }

    @Test
    fun `execute maps server results back to execution results`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"results":[
                        {"actionId":"a1","destination":"reminders","success":true,"message":"Reminder set"},
                        {"actionId":"a2","destination":"calendar","success":false,"message":"Calendar not configured"}
                        ],"quotaRemaining":2}""",
                ),
        )

        val reminder = ParsedAction.Reminder(id = "a1", title = "pay rent", dueAt = Instant.now())
        val calendar = ParsedAction.Calendar(
            id = "a2",
            title = "Gym",
            start = Instant.now(),
            end = null,
        )

        val result = api.execute(listOf(reminder, calendar))

        assertTrue(result.isSuccess)
        val results = result.getOrThrow()
        assertEquals(2, results.size)
        assertEquals(com.actuate.domain.model.Destination.REMINDERS, results[0].destination)
        assertTrue(results[0].success)
        assertEquals(com.actuate.domain.model.Destination.CALENDAR, results[1].destination)
        assertFalse(results[1].success)
    }

    @Test
    fun `server errors surface as failures with the server message`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"error":"Free tier: 3 actions per week. Upgrade for unlimited.",
                        "code":"quota_exceeded","quotaRemaining":0}""",
                ),
        )

        val result = api.execute(listOf(ParsedAction.ListItem(id = "a1", text = "buy milk")))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("3 actions per week"))
    }

    @Test
    fun `fetchLists groups list items into server lists`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"lists":[
                        {"name":"shopping","items":[
                            {"id":"a1","text":"buy chicken","list":"shopping","done":false,"createdAt":1000},
                            {"id":"a2","text":"buy milk","list":"shopping","done":true,"createdAt":2000}
                        ]},
                        {"name":"general","items":[
                            {"id":"a3","text":"write essay","list":"general","done":false,"createdAt":3000}
                        ]}
                    ]}""",
                ),
        )

        val result = api.fetchLists()

        assertTrue(result.isSuccess)
        val lists = result.getOrThrow()
        assertEquals(2, lists.size)
        assertEquals("shopping", lists[0].name)
        assertEquals(2, lists[0].items.size)
        assertFalse(lists[0].items[0].done)
        assertTrue(lists[0].items[1].done)
        assertEquals("write essay", lists[1].items.single().text)
    }

    @Test
    fun `setItemDone returns the new state from the server`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"a1","done":true}"""),
        )

        val result = api.setItemDone("a1", true)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.endsWith("/api/v1/actions/a1/done"))
    }

    @Test
    fun `fetchActions maps history to action items with resolved titles`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"actions":[
                        {"id":"r1","type":"reminder","title":"pay rent","at":"2026-08-21T09:00:00Z","done":false,"createdAt":1000},
                        {"id":"l1","type":"list_item","text":"buy chicken","done":false,"createdAt":2000}
                    ]}""",
                ),
        )

        val result = api.fetchActions()

        assertTrue(result.isSuccess)
        val actions = result.getOrThrow()
        assertEquals(2, actions.size)
        assertEquals("pay rent", actions[0].title)
        assertEquals(Instant.parse("2026-08-21T09:00:00Z"), actions[0].at)
        assertEquals("buy chicken", actions[1].title) // falls back to text
    }
}