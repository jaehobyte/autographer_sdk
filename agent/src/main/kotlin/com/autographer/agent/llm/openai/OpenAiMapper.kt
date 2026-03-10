package com.autographer.agent.llm.openai

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
import com.autographer.agent.util.JsonUtil

internal object OpenAiMapper {

    fun toApiRequest(
        messages: List<Message>,
        tools: List<ToolSchema>?,
        config: LlmConfig,
        stream: Boolean,
    ): OpenAiChatRequest {
        return OpenAiChatRequest(
            model = config.model,
            messages = messages.map { toApiMessage(it) },
            tools = tools?.map { toApiTool(it) }?.takeIf { it.isNotEmpty() },
            temperature = config.temperature,
            maxTokens = config.maxOutputTokens,
            topP = config.topP,
            stream = stream,
            stop = config.stopSequences,
        )
    }

    private fun toApiMessage(message: Message): OpenAiMessage {
        val role = when (message.role) {
            Role.SYSTEM -> "system"
            Role.USER -> "user"
            Role.ASSISTANT -> "assistant"
            Role.TOOL -> "tool"
        }

        val hasMultimodal = message.parts.any { it !is MessagePart.Text }

        val content: Any? = if (hasMultimodal) {
            message.parts.mapNotNull { toContentPart(it) }
        } else {
            message.parts.filterIsInstance<MessagePart.Text>()
                .joinToString("\n") { it.text }
                .takeIf { it.isNotEmpty() }
        }

        val toolCalls = message.toolCalls?.map { tc ->
            OpenAiToolCall(
                id = tc.id,
                function = OpenAiFunctionCall(
                    name = tc.name,
                    arguments = JsonUtil.toJson(tc.arguments),
                ),
            )
        }

        return OpenAiMessage(
            role = role,
            content = content,
            toolCalls = toolCalls,
            toolCallId = message.toolCallId,
        )
    }

    private fun toContentPart(part: MessagePart): OpenAiContentPart? {
        return when (part) {
            is MessagePart.Text -> OpenAiContentPart(type = "text", text = part.text)
            is MessagePart.ImageBase64 -> OpenAiContentPart(
                type = "image_url",
                imageUrl = OpenAiImageUrl(
                    url = "data:${part.mimeType};base64,${part.data}"
                ),
            )
            is MessagePart.ImageUrl -> OpenAiContentPart(
                type = "image_url",
                imageUrl = OpenAiImageUrl(url = part.url),
            )
            is MessagePart.Description -> OpenAiContentPart(
                type = "text",
                text = part.description,
            )
            else -> null // Video, Audio, File not natively supported by OpenAI vision
        }
    }

    private fun toApiTool(schema: ToolSchema): OpenAiTool {
        return OpenAiTool(
            function = OpenAiFunction(
                name = schema.name,
                description = schema.description,
                parameters = schema.parameters,
            ),
        )
    }

    fun fromApiResponse(response: OpenAiChatResponse): LlmResponse.Complete {
        val choice = response.choices.firstOrNull()
            ?: return LlmResponse.Complete(
                message = Message(role = Role.ASSISTANT, parts = emptyList()),
                finishReason = FinishReason.ERROR,
            )

        val apiMessage = choice.message ?: return LlmResponse.Complete(
            message = Message(role = Role.ASSISTANT, parts = emptyList()),
            finishReason = FinishReason.ERROR,
        )

        val parts = mutableListOf<MessagePart>()
        when (val content = apiMessage.content) {
            is String -> if (content.isNotEmpty()) parts.add(MessagePart.Text(content))
            else -> {}
        }

        val toolCalls = apiMessage.toolCalls?.map { tc ->
            val args: Map<String, Any?> = try {
                JsonUtil.fromJson<Map<String, Any?>>(tc.function.arguments) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
            ToolCall(id = tc.id, name = tc.function.name, arguments = args)
        }

        val finishReason = when (choice.finishReason) {
            "stop" -> FinishReason.STOP
            "tool_calls" -> FinishReason.TOOL_CALLS
            "length" -> FinishReason.MAX_TOKENS
            else -> FinishReason.STOP
        }

        val usage = response.usage?.let {
            TokenUsage(it.promptTokens, it.completionTokens)
        }

        return LlmResponse.Complete(
            message = Message(
                role = Role.ASSISTANT,
                parts = parts,
                toolCalls = toolCalls,
            ),
            finishReason = finishReason,
            usage = usage,
        )
    }

    fun fromStreamChunk(delta: OpenAiDelta?, finishReason: String?): LlmResponse.Chunk {
        val toolCallDeltas = delta?.toolCalls?.map { tc ->
            ToolCallDelta(
                index = tc.index,
                id = tc.id,
                name = tc.function?.name,
                argumentsDelta = tc.function?.arguments,
            )
        }

        return LlmResponse.Chunk(
            delta = delta?.content,
            toolCallDelta = toolCallDeltas?.firstOrNull(),
            finishReason = when (finishReason) {
                "stop" -> FinishReason.STOP
                "tool_calls" -> FinishReason.TOOL_CALLS
                "length" -> FinishReason.MAX_TOKENS
                else -> null
            },
        )
    }
}
