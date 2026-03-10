package com.autographer.agent.tool

sealed class ToolResult {
    abstract val callId: String

    data class Success(
        override val callId: String,
        val output: String,
        val metadata: Map<String, Any>? = null,
    ) : ToolResult()

    data class Error(
        override val callId: String,
        val message: String,
        val cause: Throwable? = null,
    ) : ToolResult()
}
