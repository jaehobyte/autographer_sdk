package com.autographer.agent

import com.autographer.agent.core.AgentException
import com.autographer.agent.core.AgentState
import com.autographer.agent.model.Message
import com.autographer.agent.model.ToolCall
import com.autographer.agent.tool.ToolResult

interface AgentCallback {
    fun onStateChanged(state: AgentState) {}
    fun onPartialResponse(chunk: String) {}
    fun onToolCall(toolCall: ToolCall) {}
    fun onToolResult(toolCall: ToolCall, result: ToolResult) {}
    fun onComplete(response: AgentResponse) {}
    fun onError(error: AgentException) {}
    fun onCancelled() {}
}

data class AgentResponse(
    val message: Message,
    val sessionId: String,
    val toolCallHistory: List<ToolCall> = emptyList(),
    val usage: com.autographer.agent.llm.TokenUsage? = null,
)
