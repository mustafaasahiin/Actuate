package com.actuate.data.parser

import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ParsedActions
import com.actuate.domain.model.ParserSource
import com.actuate.domain.parser.RuleBasedActionParser
import com.actuate.domain.repository.ServerParsedActions
import com.actuate.domain.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerActionParserTest {

    private val serverRepository: ServerRepository = mockk()
    private val fallback = RuleBasedActionParser()

    private val parser = ServerActionParser(serverRepository, fallback)
    private val now = Instant.parse("2026-08-19T10:00:00Z")

    @Test
    fun `uses server actions when the server responds`() = runTest {
        val serverAction = ParsedAction.ListItem(text = "buy chicken", list = "shopping")
        coEvery { serverRepository.parse(any()) } returns Result.success(
            ServerParsedActions(listOf(serverAction), ParserSource.LLM, 1f),
        )

        val result = parser.parse("add buy chicken to my shopping list", now)

        assertEquals(ParserSource.LLM, result.source)
        assertEquals(1, result.actions.size)
        assertEquals("buy chicken", (result.actions.single() as ParsedAction.ListItem).text)
    }

    @Test
    fun `falls back to rules when the server is unreachable`() = runTest {
        coEvery { serverRepository.parse(any()) } returns Result.failure(
            IllegalStateException("Connection refused"),
        )

        val result = parser.parse(
            "Schedule a gym session with Alex tomorrow at 5 PM and add buy chicken to my shopping list",
            now,
        )

        assertEquals(ParserSource.RULES, result.source)
        assertTrue(result.actions.isNotEmpty())
        assertTrue(result.actions.any { it is ParsedAction.Calendar })
        assertTrue(result.actions.any { it is ParsedAction.ListItem })
    }
}