package com.autographer.agent.llm

data class LlmConfig(
    val model: String,
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 4096,
    val topP: Float? = null,
    val systemPrompt: String? = null,
    val stopSequences: List<String>? = null,
)
