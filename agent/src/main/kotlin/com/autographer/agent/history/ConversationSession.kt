package com.autographer.agent.history

import com.autographer.agent.model.Message

data class ConversationSession(
    val id: String,
    val messages: MutableList<Message> = mutableListOf(),
    val metadata: MutableMap<String, Any> = mutableMapOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
)

data class SessionMeta(
    val id: String,
    val title: String? = null,
    val messageCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
