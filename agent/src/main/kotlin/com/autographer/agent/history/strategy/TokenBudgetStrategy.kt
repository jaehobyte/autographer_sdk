package com.autographer.agent.history.strategy

import com.autographer.agent.model.Message
import com.autographer.agent.model.MessagePart
import com.autographer.agent.model.Role
import com.autographer.agent.util.TokenCounter

class TokenBudgetStrategy : HistoryStrategy {

    override suspend fun compact(messages: List<Message>, maxTokens: Int): List<Message> {
        if (messages.isEmpty()) return messages

        val systemMessages = messages.filter { it.role == Role.SYSTEM }
        val nonSystemMessages = messages.filter { it.role != Role.SYSTEM }

        var budgetRemaining = maxTokens

        // System messages always included
        for (msg in systemMessages) {
            budgetRemaining -= estimateTokens(msg)
        }

        // Add messages from newest to oldest
        val selected = mutableListOf<Message>()
        for (msg in nonSystemMessages.reversed()) {
            val tokens = estimateTokens(msg)
            if (budgetRemaining - tokens < 0) break
            budgetRemaining -= tokens
            selected.add(0, msg)
        }

        return systemMessages + selected
    }

    private fun estimateTokens(message: Message): Int {
        var count = 4 // role + formatting overhead
        for (part in message.parts) {
            count += when (part) {
                is MessagePart.Text -> TokenCounter.estimate(part.text)
                is MessagePart.ImageBase64 -> 85 // ~image token cost
                is MessagePart.ImageUrl -> 85
                is MessagePart.VideoFrames -> part.frames.size * 85
                is MessagePart.AudioData -> 100
                is MessagePart.FileData -> TokenCounter.estimate(part.data)
                is MessagePart.Description -> TokenCounter.estimate(part.description)
            }
        }
        return count
    }
}
