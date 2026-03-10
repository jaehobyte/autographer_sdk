package com.autographer.agent.llm.openai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val tools: List<OpenAiTool>? = null,
    val temperature: Float? = null,
    @Json(name = "max_tokens") val maxTokens: Int? = null,
    @Json(name = "top_p") val topP: Float? = null,
    val stream: Boolean = false,
    val stop: List<String>? = null,
)

@JsonClass(generateAdapter = true)
internal data class OpenAiMessage(
    val role: String,
    val content: Any?, // String or List<OpenAiContentPart>
    @Json(name = "tool_calls") val toolCalls: List<OpenAiToolCall>? = null,
    @Json(name = "tool_call_id") val toolCallId: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class OpenAiContentPart(
    val type: String,
    val text: String? = null,
    @Json(name = "image_url") val imageUrl: OpenAiImageUrl? = null,
)

@JsonClass(generateAdapter = true)
internal data class OpenAiImageUrl(
    val url: String,
    val detail: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class OpenAiTool(
    val type: String = "function",
    val function: OpenAiFunction,
)

@JsonClass(generateAdapter = true)
internal data class OpenAiFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>,
)

@JsonClass(generateAdapter = true)
internal data class OpenAiToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAiFunctionCall,
)

@JsonClass(generateAdapter = true)
internal data class OpenAiFunctionCall(
    val name: String,
    val arguments: String,
)

// Response models

@JsonClass(generateAdapter = true)
internal data class OpenAiChatResponse(
    val id: String?,
    val choices: List<OpenAiChoice>,
    val usage: OpenAiUsage? = null,
)

@JsonClass(generateAdapter = true)
internal data class OpenAiChoice(
    val index: Int,
    val message: OpenAiMessage? = null,
    val delta: OpenAiDelta? = null,
    @Json(name = "finish_reason") val finishReason: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null,
    @Json(name = "tool_calls") val toolCalls: List<OpenAiStreamToolCall>? = null,
)

@JsonClass(generateAdapter = true)
internal data class OpenAiStreamToolCall(
    val index: Int,
    val id: String? = null,
    val function: OpenAiStreamFunctionCall? = null,
)

@JsonClass(generateAdapter = true)
internal data class OpenAiStreamFunctionCall(
    val name: String? = null,
    val arguments: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class OpenAiUsage(
    @Json(name = "prompt_tokens") val promptTokens: Int,
    @Json(name = "completion_tokens") val completionTokens: Int,
    @Json(name = "total_tokens") val totalTokens: Int,
)
