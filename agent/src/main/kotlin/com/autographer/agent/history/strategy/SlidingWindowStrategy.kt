package com.autographer.agent.history.strategy

import com.autographer.agent.model.Message
import com.autographer.agent.model.Role

class SlidingWindowStrategy(
    private val windowSize: Int = 20,
) : HistoryStrategy {

    override suspend fun compact(messages: List<Message>, maxTokens: Int): List<Message> {
        if (messages.size <= windowSize) return messages

        val systemMessages = messages.filter { it.role == Role.SYSTEM }
        val nonSystemMessages = messages.filter { it.role != Role.SYSTEM }

        val windowed = nonSystemMessages.takeLast(windowSize)
        return systemMessages + windowed
    }
}
