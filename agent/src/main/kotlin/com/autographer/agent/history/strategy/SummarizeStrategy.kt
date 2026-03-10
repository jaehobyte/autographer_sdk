package com.autographer.agent.history.strategy

import com.autographer.agent.llm.LlmConfig
import com.autographer.agent.llm.LlmProvider
import com.autographer.agent.model.Message
import com.autographer.agent.model.MessagePart
import com.autographer.agent.model.Role

class SummarizeStrategy(
    private val summarizer: LlmProvider,
    private val recentWindowSize: Int = 5,
) : HistoryStrategy {

    override suspend fun compact(messages: List<Message>, maxTokens: Int): List<Message> {
        if (messages.size <= recentWindowSize + 1) return messages

        val systemMessages = messages.filter { it.role == Role.SYSTEM }
        val nonSystemMessages = messages.filter { it.role != Role.SYSTEM }

        if (nonSystemMessages.size <= recentWindowSize) {
            return systemMessages + nonSystemMessages
        }

        val olderMessages = nonSystemMessages.dropLast(recentWindowSize)
        val recentMessages = nonSystemMessages.takeLast(recentWindowSize)

        val summary = summarizeMessages(olderMessages)

        val summaryMessage = Message(
            role = Role.SYSTEM,
            parts = listOf(MessagePart.Text("[Previous conversation summary]\n$summary")),
        )

        return systemMessages + summaryMessage + recentMessages
    }

    private suspend fun summarizeMessages(messages: List<Message>): String {
        val conversationText = messages.joinToString("\n") { msg ->
            val text = msg.parts.filterIsInstance<MessagePart.Text>()
                .joinToString(" ") { it.text }
            "${msg.role.name}: $text"
        }

        val summaryRequest = listOf(
            Message(
                role = Role.SYSTEM,
                parts = listOf(
                    MessagePart.Text(
                        "Summarize the following conversation concisely, " +
                            "preserving key information and context needed for continuation."
                    )
                ),
            ),
            Message(
                role = Role.USER,
                parts = listOf(MessagePart.Text(conversationText)),
            ),
        )

        val response = summarizer.complete(
            messages = summaryRequest,
            config = LlmConfig(
                model = "gpt-4o-mini",
                temperature = 0.3f,
                maxOutputTokens = 500,
            ),
        )

        return response.message.parts
            .filterIsInstance<MessagePart.Text>()
            .joinToString(" ") { it.text }
    }
}
