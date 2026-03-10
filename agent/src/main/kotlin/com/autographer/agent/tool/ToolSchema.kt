package com.autographer.agent.tool

data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>,
    val required: List<String> = emptyList(),
)
