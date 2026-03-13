package com.autographer.agent.llm.openai

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

class OpenAiProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val client: OkHttpClient = defaultClient(),
) : LlmProvider {

    override val name: String = "openai"

    override fun capabilities(): Set<LlmCapability> = setOf(
        LlmCapability.TEXT,
        LlmCapability.IMAGE,
        LlmCapability.AUDIO,
        LlmCapability.TOOL_CALLING,
        LlmCapability.STREAMING,
    )

    override fun maxContextTokens(): Int = 128_000

    override fun defaultModel(): String = "gpt-4o"

    override suspend fun complete(
        messages: List<Message>,
        tools: List<ToolSchema>?,
        config: LlmConfig,
    ): LlmResponse.Complete = withContext(Dispatchers.IO) {
        val apiRequest = OpenAiMapper.toApiRequest(messages, tools, config, stream = false)
        val jsonBody = JsonUtil.toJson(apiRequest)

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string()
            ?: throw LlmException("Empty response body from OpenAI")

        if (!response.isSuccessful) {
            throw LlmException(
                "OpenAI API error: $body",
                code = response.code,
            )
        }

        val apiResponse = JsonUtil.fromJson<OpenAiChatResponse>(body)
            ?: throw LlmException("Failed to parse OpenAI response")

        OpenAiMapper.fromApiResponse(apiResponse)
    }

    override fun stream(
        messages: List<Message>,
        tools: List<ToolSchema>?,
        config: LlmConfig,
    ): Flow<LlmResponse.Chunk> = callbackFlow {
        val apiRequest = OpenAiMapper.toApiRequest(messages, tools, config, stream = true)
        val jsonBody = JsonUtil.toJson(apiRequest)

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)

        try {
            val response = call.execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw LlmException(
                    "OpenAI API error: $errorBody",
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
                    if (json == "[DONE]") break

                    try {
                        val chunk = JsonUtil.fromJson<OpenAiChatResponse>(json)
                        val choice = chunk?.choices?.firstOrNull() ?: continue
                        val mapped = OpenAiMapper.fromStreamChunk(
                            choice.delta, choice.finishReason
                        )
                        trySend(mapped)
                    } catch (e: Exception) {
                        Logger.w("Failed to parse stream chunk: $json", e)
                    }
                }
            } finally {
                reader.close()
                response.close()
            }
        } catch (e: LlmException) {
            close(e)
        } catch (e: Exception) {
            close(LlmException("OpenAI streaming error: ${e.message}", cause = e))
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
