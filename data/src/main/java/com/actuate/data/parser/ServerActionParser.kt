package com.actuate.data.parser

import com.actuate.domain.model.ParsedActions
import com.actuate.domain.model.ParserSource
import com.actuate.domain.parser.ActionParser
import com.actuate.domain.parser.RuleBasedActionParser
import com.actuate.domain.repository.ServerRepository
import java.time.Instant

/**
 * Server-first action parser. Transcripts go to the Actuate dev server,
 * which runs OpenAI function-calling with a key that never ships in the app.
 * When the server is unreachable or unconfigured, parsing falls back to the
 * offline [RuleBasedActionParser] so the app still works.
 */
class ServerActionParser(
    private val serverRepository: ServerRepository,
    private val fallback: RuleBasedActionParser,
) : ActionParser {

    override suspend fun parse(transcript: String, now: Instant): ParsedActions {
        val serverResult = serverRepository.parse(transcript)
        if (serverResult.isSuccess) {
            val parsed = serverResult.getOrThrow()
            return ParsedActions(
                rawTranscript = transcript,
                actions = parsed.actions,
                source = parsed.source,
                confidence = parsed.confidence,
            )
        }
        return fallback.parse(transcript, now)
    }
}