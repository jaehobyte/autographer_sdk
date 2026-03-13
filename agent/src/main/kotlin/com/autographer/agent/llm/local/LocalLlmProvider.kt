package com.autographer.agent.llm.local

import com.autographer.agent.llm.LlmCapability
import com.autographer.agent.llm.LlmConfig
import com.autographer.agent.llm.LlmProvider
import com.autographer.agent.llm.LlmResponse
import com.autographer.agent.llm.openai.OpenAiProvider
import com.autographer.agent.model.Message
import com.autographer.agent.tool.ToolSchema
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

/**
 * Local LLM provider that communicates with OpenAI-compatible local servers
 * (e.g., llama.cpp, Ollama, vLLM, LM Studio).
 */
class LocalLlmProvider(
    baseUrl: String,
    apiKey: String = "no-key",
    client: OkHttpClient? = null,
    private val supportedCapabilities: Set<LlmCapability> = setOf(
        LlmCapability.TEXT,
        LlmCapability.STREAMING,
    ),
    private val contextSize: Int = 4096,
    private val modelName: String = "local-model",
) : LlmProvider {

    private val delegate: OpenAiProvider = if (client != null) {
        OpenAiProvider(apiKey = apiKey, baseUrl = baseUrl, client = client)
    } else {
        OpenAiProvider(apiKey = apiKey, baseUrl = baseUrl)
    }

    override val name: String = "local"

    override fun capabilities(): Set<LlmCapability> = supportedCapabilities

    override fun maxContextTokens(): Int = contextSize

    override fun defaultModel(): String = modelName

    override suspend fun complete(
        messages: List<Message>,
        tools: List<ToolSchema>?,
        config: LlmConfig,
    ): LlmResponse.Complete {
        val effectiveTools = if (LlmCapability.TOOL_CALLING in supportedCapabilities) tools else null
        return delegate.complete(messages, effectiveTools, config)
    }

    override fun stream(
        messages: List<Message>,
        tools: List<ToolSchema>?,
        config: LlmConfig,
    ): Flow<LlmResponse.Chunk> {
        val effectiveTools = if (LlmCapability.TOOL_CALLING in supportedCapabilities) tools else null
        return delegate.stream(messages, effectiveTools, config)
    }
}
