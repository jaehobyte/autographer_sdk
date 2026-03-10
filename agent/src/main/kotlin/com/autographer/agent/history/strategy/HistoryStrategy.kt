package com.autographer.agent.history.strategy

import com.autographer.agent.model.Message

interface HistoryStrategy {
    suspend fun compact(messages: List<Message>, maxTokens: Int): List<Message>
}
