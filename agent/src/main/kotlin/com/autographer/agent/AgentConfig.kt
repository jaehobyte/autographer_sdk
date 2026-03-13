package com.autographer.agent

import com.autographer.agent.llm.LlmConfig
import com.autographer.agent.llm.LlmProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class AgentConfig(
    val llmProvider: LlmProvider,
    val llmConfig: LlmConfig,
    val systemPrompt: String? = null,
    val maxIterations: Int = 10,
    val tokenBudget: Int = 4096,
    val defaultTimeout: Duration = 60.seconds,
    val enableLogging: Boolean = false,
)
