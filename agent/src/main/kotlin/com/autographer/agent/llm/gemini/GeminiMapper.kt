package com.autographer.agent.llm.gemini

import com.autographer.agent.llm.FinishReason
import com.autographer.agent.llm.LlmConfig
import com.autographer.agent.llm.LlmResponse
import com.autographer.agent.llm.TokenUsage
import com.autographer.agent.llm.ToolCallDelta
import com.autographer.agent.model.Message
import com.autographer.agent.model.MessagePart
import com.autographer.agent.model.Role
import com.autographer.agent.model.ToolCall
import com.autographer.agent.tool.ToolSchema
import java.util.UUID

internal object GeminiMapper {

    fun toApiRequest(
        messages: List<Message>,
        tools: List<ToolSchema>?,
        config: LlmConfig,
    ): GeminiRequest {
        val systemMessage = messages.firstOrNull { it.role == Role.SYSTEM }
        val nonSystemMessages = messages.filter { it.role != Role.SYSTEM }

        return GeminiRequest(
            contents = nonSystemMessages.map { toGeminiContent(it) },
            tools = tools?.let {
                listOf(
                    GeminiToolDeclaration(
                        functionDeclarations = it.map { schema ->
                            GeminiFunctionDeclaration(
                                name = schema.name,
                                description = schema.description,
                                parameters = schema.parameters,
                            )
                        }
                    )
                )
            }?.takeIf { tools.isNotEmpty() },
            systemInstruction = systemMessage?.let { toGeminiContent(it) },
            generationConfig = GeminiGenerationConfig(
                temperature = config.temperature,
                maxOutputTokens = config.maxOutputTokens,
                topP = config.topP,
                stopSequences = config.stopSequences,
            ),
        )
    }

    private fun toGeminiContent(message: Message): GeminiContent {
        val role = when (message.role) {
            Role.USER, Role.SYSTEM -> "user"
            Role.ASSISTANT -> "model"
            Role.TOOL -> "function"
        }

        val parts = mutableListOf<GeminiPart>()

        // Handle tool call results
        if (message.role == Role.TOOL && message.toolCallId != null) {
            val textContent = message.parts.filterIsInstance<MessagePart.Text>()
                .joinToString("\n") { it.text }
            parts.add(
                GeminiPart(
                    functionResponse = GeminiFunctionResponse(
                        name = message.toolCallId!!,
                        response = mapOf("result" to textContent),
                    )
                )
            )
            return GeminiContent(role = role, parts = parts)
        }

        // Handle tool calls from assistant
        message.toolCalls?.forEach { tc ->
            parts.add(
                GeminiPart(
                    functionCall = GeminiFunctionCall(name = tc.name, args = tc.arguments)
                )
            )
        }

        // Handle content parts
        for (part in message.parts) {
            when (part) {
                is MessagePart.Text -> parts.add(GeminiPart(text = part.text))
                is MessagePart.ImageBase64 -> parts.add(
                    GeminiPart(
                        inlineData = GeminiInlineData(
                            mimeType = part.mimeType,
                            data = part.data,
                        )
                    )
                )
                is MessagePart.AudioData -> parts.add(
                    GeminiPart(
                        inlineData = GeminiInlineData(
                            mimeType = part.mimeType,
                            data = part.data,
                        )
                    )
                )
                is MessagePart.FileData -> parts.add(
                    GeminiPart(
                        inlineData = GeminiInlineData(
                            mimeType = part.mimeType,
                            data = part.data,
                        )
                    )
                )
                is MessagePart.VideoFrames -> {
                    part.frames.forEach { frame ->
                        parts.add(
                            GeminiPart(
                                inlineData = GeminiInlineData(
                                    mimeType = frame.mimeType,
                                    data = frame.data,
                                )
                            )
                        )
                    }
                }
                is MessagePart.ImageUrl -> parts.add(GeminiPart(text = "[Image: ${part.url}]"))
                is MessagePart.Description -> parts.add(GeminiPart(text = part.description))
            }
        }

        if (parts.isEmpty()) {
            parts.add(GeminiPart(text = ""))
        }

        return GeminiContent(role = role, parts = parts)
    }

    fun fromApiResponse(response: GeminiResponse): LlmResponse.Complete {
        val candidate = response.candidates?.firstOrNull()
            ?: return LlmResponse.Complete(
                message = Message(role = Role.ASSISTANT, parts = emptyList()),
                finishReason = FinishReason.ERROR,
            )

        val parts = mutableListOf<MessagePart>()
        val toolCalls = mutableListOf<ToolCall>()

        candidate.content?.parts?.forEach { part ->
            when {
                part.text != null -> parts.add(MessagePart.Text(part.text))
                part.functionCall != null -> {
                    toolCalls.add(
                        ToolCall(
                            id = UUID.randomUUID().toString(),
                            name = part.functionCall.name,
                            arguments = part.functionCall.args ?: emptyMap(),
                        )
                    )
                }
            }
        }

        val finishReason = when (candidate.finishReason) {
            "STOP" -> FinishReason.STOP
            "MAX_TOKENS" -> FinishReason.MAX_TOKENS
            else -> if (toolCalls.isNotEmpty()) FinishReason.TOOL_CALLS else FinishReason.STOP
        }

        val usage = response.usageMetadata?.let {
            TokenUsage(
                promptTokens = it.promptTokenCount ?: 0,
                completionTokens = it.candidatesTokenCount ?: 0,
            )
        }

        return LlmResponse.Complete(
            message = Message(
                role = Role.ASSISTANT,
                parts = parts,
                toolCalls = toolCalls.takeIf { it.isNotEmpty() },
            ),
            finishReason = finishReason,
            usage = usage,
        )
    }

    fun fromStreamChunk(candidate: GeminiCandidate): LlmResponse.Chunk {
        var textDelta: String? = null
        var toolCallDelta: ToolCallDelta? = null

        candidate.content?.parts?.forEachIndexed { index, part ->
            when {
                part.text != null -> textDelta = (textDelta ?: "") + part.text
                part.functionCall != null -> {
                    toolCallDelta = ToolCallDelta(
                        index = index,
                        id = UUID.randomUUID().toString(),
                        name = part.functionCall.name,
                        argumentsDelta = part.functionCall.args?.toString(),
                    )
                }
            }
        }

        val finishReason = when (candidate.finishReason) {
            "STOP" -> FinishReason.STOP
            "MAX_TOKENS" -> FinishReason.MAX_TOKENS
            else -> null
        }

        return LlmResponse.Chunk(
            delta = textDelta,
            toolCallDelta = toolCallDelta,
            finishReason = finishReason,
        )
    }
}
