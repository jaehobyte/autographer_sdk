package com.autographer.agent.core

sealed class AgentState {
    data object Idle : AgentState()
    data object Planning : AgentState()
    data class CallingTool(val toolName: String) : AgentState()
    data object AwaitingResponse : AgentState()
    data object Completed : AgentState()
    data class Failed(val error: Throwable) : AgentState()
    data object Cancelled : AgentState()
}
