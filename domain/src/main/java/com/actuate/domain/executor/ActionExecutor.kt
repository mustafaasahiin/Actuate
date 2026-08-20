package com.actuate.domain.executor

import com.actuate.domain.model.ExecutionResult
import com.actuate.domain.model.ParsedAction

/** Executes a single parsed action against its destination app. */
interface ActionExecutor {
    suspend fun execute(action: ParsedAction): ExecutionResult
}