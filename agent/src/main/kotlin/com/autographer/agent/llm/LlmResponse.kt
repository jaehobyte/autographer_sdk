package com.autographer.agent.llm

import com.autographer.agent.model.Message
import com.autographer.agent.model.ToolCall

enum class FinishReason {
    STOP, TOOL_CALLS, MAX_TOKENS, ERROR
}

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
) {
    val totalTokens: Int get() = promptTokens + completionTokens
}

sealed class LlmResponse {

    data class Complete(
        val message: Message,
        val finishReason: FinishReason,
        val usage: TokenUsage? = null,
    ) : LlmResponse()

    data class Chunk(
        val delta: String? = null,
        val toolCallDelta: ToolCallDelta? = null,
        val finishReason: FinishReason? = null,
        val usage: TokenUsage? = null,
    ) : LlmResponse()

    data class Error(
        val message: String,
        val code: Int? = null,
        val cause: Throwable? = null,
    ) : LlmResponse()
}

data class ToolCallDelta(
    val index: Int,
    val id: String? = null,
    val name: String? = null,
    val argumentsDelta: String? = null,
)
