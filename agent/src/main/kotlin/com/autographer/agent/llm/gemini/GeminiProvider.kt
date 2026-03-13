package com.autographer.agent.llm.gemini

import com.autographer.agent.core.LlmException
import com.autographer.agent.llm.LlmCapability
import com.autographer.agent.llm.LlmConfig
import com.autographer.agent.llm.LlmProvider
import com.autographer.agent.llm.LlmResponse
import com.autographer.agent.model.Message
import com.autographer.agent.tool.ToolSchema
import com.autographer.agent.util.JsonUtil
import com.autographer.agent.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class GeminiProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val client: OkHttpClient = defaultClient(),
) : LlmProvider {

    override val name: String = "gemini"

    override fun capabilities(): Set<LlmCapability> = setOf(
        LlmCapability.TEXT,
        LlmCapability.IMAGE,
        LlmCapability.VIDEO,
        LlmCapability.AUDIO,
        LlmCapability.TOOL_CALLING,
        LlmCapability.STREAMING,
    )

    override fun maxContextTokens(): Int = 1_000_000

    override fun defaultModel(): String = "gemini-1.5-pro"

    override suspend fun complete(
        messages: List<Message>,
        tools: List<ToolSchema>?,
        config: LlmConfig,
    ): LlmResponse.Complete = withContext(Dispatchers.IO) {
        val apiRequest = GeminiMapper.toApiRequest(messages, tools, config)
        val jsonBody = JsonUtil.toJson(apiRequest)

        val url = "$baseUrl/models/${config.model}:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string()
            ?: throw LlmException("Empty response body from Gemini")

        if (!response.isSuccessful) {
            throw LlmException(
                "Gemini API error: $body",
                code = response.code,
            )
        }

        val apiResponse = JsonUtil.fromJson<GeminiResponse>(body)
            ?: throw LlmException("Failed to parse Gemini response")

        GeminiMapper.fromApiResponse(apiResponse)
    }

    override fun stream(
        messages: List<Message>,
        tools: List<ToolSchema>?,
        config: LlmConfig,
    ): Flow<LlmResponse.Chunk> = callbackFlow {
        val apiRequest = GeminiMapper.toApiRequest(messages, tools, config)
        val jsonBody = JsonUtil.toJson(apiRequest)

        val url = "$baseUrl/models/${config.model}:streamGenerateContent?alt=sse&key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)

        try {
            val response = call.execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw LlmException(
                    "Gemini API error: $errorBody",
                    code = response.code,
                )
            }

            val reader = BufferedReader(
                InputStreamReader(response.body!!.byteStream())
            )

            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val data = line ?: continue
                    if (!data.startsWith("data: ")) continue
                    val json = data.removePrefix("data: ").trim()

                    try {
                        val chunk = JsonUtil.fromJson<GeminiResponse>(json)
                        val candidate = chunk?.candidates?.firstOrNull() ?: continue
                        val mapped = GeminiMapper.fromStreamChunk(candidate)
                        trySend(mapped)
                    } catch (e: Exception) {
                        Logger.w("Failed to parse Gemini stream chunk", e)
                    }
                }
            } finally {
                reader.close()
                response.close()
            }
        } catch (e: LlmException) {
            close(e)
        } catch (e: Exception) {
            close(LlmException("Gemini streaming error: ${e.message}", cause = e))
        }

        awaitClose { call.cancel() }
    }

    companion object {
        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
