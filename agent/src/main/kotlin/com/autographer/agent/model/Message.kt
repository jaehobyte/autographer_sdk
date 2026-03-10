package com.autographer.agent.model

enum class Role {
    SYSTEM, USER, ASSISTANT, TOOL
}

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>,
)

data class Message(
    val role: Role,
    val parts: List<MessagePart>,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
