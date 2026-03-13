package com.autographer.agent.llm

import com.autographer.agent.model.Message
import com.autographer.agent.tool.ToolSchema
import kotlinx.coroutines.flow.Flow

enum class LlmCapability {
    TEXT, IMAGE, VIDEO, AUDIO, FILE, TOOL_CALLING, STREAMING
}

interface LlmProvider {

    val name: String

    fun capabilities(): Set<LlmCapability>

    fun maxContextTokens(): Int

    fun defaultModel(): String

    suspend fun complete(
        messages: List<Message>,
        tools: List<ToolSchema>? = null,
        config: LlmConfig,
    ): LlmResponse.Complete

    fun stream(
        messages: List<Message>,
        tools: List<ToolSchema>? = null,
        config: LlmConfig,
    ): Flow<LlmResponse.Chunk>
}
