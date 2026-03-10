package com.autographer.agent.tool

interface Tool {
    val name: String
    val description: String
    val parameterSchema: ToolSchema

    suspend fun execute(args: Map<String, Any?>): ToolResult
}
