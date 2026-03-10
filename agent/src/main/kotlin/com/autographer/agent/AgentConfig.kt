package com.autographer.agent

import com.autographer.agent.history.ContentStoragePolicy
import com.autographer.agent.history.strategy.HistoryStrategy
import com.autographer.agent.history.store.HistoryStore
import com.autographer.agent.llm.LlmConfig
import com.autographer.agent.llm.LlmProvider
import com.autographer.agent.tool.Tool
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class AgentConfig(
    val llmProvider: LlmProvider,
    val llmConfig: LlmConfig,
    val tools: List<Tool> = emptyList(),
    val historyStrategy: HistoryStrategy? = null,
    val historyStore: HistoryStore? = null,
    val contentStoragePolicy: ContentStoragePolicy = ContentStoragePolicy.DESCRIPTION,
    val systemPrompt: String? = null,
    val maxIterations: Int = 10,
    val tokenBudget: Int = 4096,
    val defaultTimeout: Duration = 60.seconds,
    val enableLogging: Boolean = false,
)
