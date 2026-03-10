package com.autographer.agent.llm.gemini

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class GeminiRequest(
    val contents: List<GeminiContent>,
    val tools: List<GeminiToolDeclaration>? = null,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null,
)

@JsonClass(generateAdapter = true)
internal data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@JsonClass(generateAdapter = true)
internal data class GeminiPart(
    val text: String? = null,
    @Json(name = "inlineData") val inlineData: GeminiInlineData? = null,
    @Json(name = "functionCall") val functionCall: GeminiFunctionCall? = null,
    @Json(name = "functionResponse") val functionResponse: GeminiFunctionResponse? = null,
)

@JsonClass(generateAdapter = true)
internal data class GeminiInlineData(
    @Json(name = "mimeType") val mimeType: String,
    val data: String, // base64
)

@JsonClass(generateAdapter = true)
internal data class GeminiToolDeclaration(
    @Json(name = "functionDeclarations") val functionDeclarations: List<GeminiFunctionDeclaration>,
)

@JsonClass(generateAdapter = true)
internal data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>,
)

@JsonClass(generateAdapter = true)
internal data class GeminiFunctionCall(
    val name: String,
    val args: Map<String, Any?>? = null,
)

@JsonClass(generateAdapter = true)
internal data class GeminiFunctionResponse(
    val name: String,
    val response: Map<String, Any?>,
)

@JsonClass(generateAdapter = true)
internal data class GeminiGenerationConfig(
    val temperature: Float? = null,
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = null,
    @Json(name = "topP") val topP: Float? = null,
    @Json(name = "stopSequences") val stopSequences: List<String>? = null,
)

// Response

@JsonClass(generateAdapter = true)
internal data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    @Json(name = "usageMetadata") val usageMetadata: GeminiUsageMetadata? = null,
)

@JsonClass(generateAdapter = true)
internal data class GeminiCandidate(
    val content: GeminiContent? = null,
    @Json(name = "finishReason") val finishReason: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class GeminiUsageMetadata(
    @Json(name = "promptTokenCount") val promptTokenCount: Int? = null,
    @Json(name = "candidatesTokenCount") val candidatesTokenCount: Int? = null,
    @Json(name = "totalTokenCount") val totalTokenCount: Int? = null,
)
