package com.actuate.domain.parser

import com.actuate.domain.model.ParsedActions
import java.time.Instant

/**
 * Turns a natural-language transcript into structured actions.
 * Implementations: LLM (OpenRouter function calling) and rule-based fallback.
 */
interface ActionParser {
    suspend fun parse(transcript: String, now: Instant = Instant.now()): ParsedActions
}