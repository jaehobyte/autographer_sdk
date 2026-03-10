package com.autographer.agent

import android.content.Context
import com.autographer.agent.content.ContentProcessor
import com.autographer.agent.content.DefaultContentProcessor
import com.autographer.agent.core.AgentOrchestrator
import com.autographer.agent.core.AgentState
import com.autographer.agent.core.CancellationController
import com.autographer.agent.history.ContentStoragePolicy
import com.autographer.agent.history.HistoryManager
import com.autographer.agent.history.SessionMeta
import com.autographer.agent.history.store.HistoryStore
import com.autographer.agent.history.store.InMemoryHistoryStore
import com.autographer.agent.history.strategy.HistoryStrategy
import com.autographer.agent.history.strategy.SlidingWindowStrategy
import com.autographer.agent.llm.LlmConfig
import com.autographer.agent.llm.LlmProvider
import com.autographer.agent.model.UserRequest
import com.autographer.agent.model.userRequest
import com.autographer.agent.model.UserRequestBuilder
import com.autographer.agent.tool.Tool
import com.autographer.agent.tool.ToolManager
import com.autographer.agent.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class AgentClient private constructor(
    private val config: AgentConfig,
    private val toolManager: ToolManager,
    private val historyManager: HistoryManager,
    private val contentProcessor: ContentProcessor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentSessionId: String? = null

    /**
     * Send a text-only request.
     */
    fun request(
        prompt: String,
        callback: AgentCallback,
    ): RequestHandle {
        return request(UserRequest.text(prompt, currentSessionId), callback)
    }

    /**
     * Send a multimodal request.
     */
    fun request(
        request: UserRequest,
        callback: AgentCallback,
    ): RequestHandle {
        val requestId = UUID.randomUUID().toString()
        val stateFlow = MutableStateFlow<AgentState>(AgentState.Idle)
        var timeoutSetter: ((Duration) -> Unit)? = null

        val job = scope.launch {
            val sessionId = request.sessionId ?: currentSessionId ?: run {
                val session = historyManager.createSession(config.systemPrompt)
                currentSessionId = session.id
                session.id
            }

            val orchestrator = AgentOrchestrator(
                config = config,
                llmProvider = config.llmProvider,
                toolManager = toolManager,
                historyManager = historyManager,
                contentProcessor = contentProcessor,
            )

            val cancellation = CancellationController(coroutineContext[kotlinx.coroutines.Job]!!)
            cancellation.setTimeout(config.defaultTimeout)
            timeoutSetter = { duration -> cancellation.setTimeout(duration) }

            // Mirror orchestrator state
            scope.launch {
                orchestrator.stateFlow.collect { stateFlow.value = it }
            }

            val response = orchestrator.execute(
                request = request.copy(sessionId = sessionId),
                sessionId = sessionId,
                callback = callback,
                cancellation = cancellation,
            )

            callback.onComplete(response)
        }

        return RequestHandle(
            id = requestId,
            job = job,
            stateFlow = stateFlow,
            onTimeout = { duration -> timeoutSetter?.invoke(duration) },
        )
    }

    /**
     * DSL-style multimodal request.
     */
    fun request(
        callback: AgentCallback,
        block: UserRequestBuilder.() -> Unit,
    ): RequestHandle {
        val userReq = userRequest(block)
        return request(userReq, callback)
    }

    /**
     * Create a new conversation session.
     */
    suspend fun newSession(systemPrompt: String? = null): String {
        val session = historyManager.createSession(systemPrompt ?: config.systemPrompt)
        currentSessionId = session.id
        return session.id
    }

    /**
     * Load an existing session.
     */
    suspend fun loadSession(sessionId: String): Boolean {
        val session = historyManager.loadSession(sessionId)
        return if (session != null) {
            currentSessionId = sessionId
            true
        } else {
            false
        }
    }

    /**
     * List all saved sessions.
     */
    suspend fun listSessions(): List<SessionMeta> {
        return historyManager.listSessions()
    }

    /**
     * Delete a session.
     */
    suspend fun deleteSession(sessionId: String) {
        historyManager.deleteSession(sessionId)
        if (currentSessionId == sessionId) {
            currentSessionId = null
        }
    }

    /**
     * Register a tool at runtime.
     */
    fun registerTool(tool: Tool) {
        toolManager.register(tool)
    }

    /**
     * Unregister a tool at runtime.
     */
    fun unregisterTool(name: String) {
        toolManager.unregister(name)
    }

    /**
     * Release all resources.
     */
    fun destroy() {
        scope.cancel()
    }

    // ===== Builder =====

    class Builder {
        private var llmProvider: LlmProvider? = null
        private var llmConfig: LlmConfig? = null
        private var tools: List<Tool> = emptyList()
        private var historyStrategy: HistoryStrategy? = null
        private var historyStore: HistoryStore? = null
        private var contentStoragePolicy: ContentStoragePolicy = ContentStoragePolicy.DESCRIPTION
        private var systemPrompt: String? = null
        private var maxIterations: Int = 10
        private var tokenBudget: Int = 4096
        private var defaultTimeout: Duration = 60.seconds
        private var enableLogging: Boolean = false
        private var context: Context? = null

        fun provider(provider: LlmProvider) = apply { this.llmProvider = provider }
        fun llmConfig(config: LlmConfig) = apply { this.llmConfig = config }
        fun tools(tools: List<Tool>) = apply { this.tools = tools }
        fun historyStrategy(strategy: HistoryStrategy) = apply { this.historyStrategy = strategy }
        fun historyStore(store: HistoryStore) = apply { this.historyStore = store }
        fun contentStoragePolicy(policy: ContentStoragePolicy) = apply { this.contentStoragePolicy = policy }
        fun systemPrompt(prompt: String) = apply { this.systemPrompt = prompt }
        fun maxIterations(max: Int) = apply { this.maxIterations = max }
        fun tokenBudget(budget: Int) = apply { this.tokenBudget = budget }
        fun defaultTimeout(timeout: Duration) = apply { this.defaultTimeout = timeout }
        fun enableLogging(enable: Boolean) = apply { this.enableLogging = enable }
        fun context(context: Context) = apply { this.context = context.applicationContext }

        fun build(): AgentClient {
            val provider = requireNotNull(llmProvider) { "LlmProvider is required" }

            val config = AgentConfig(
                llmProvider = provider,
                llmConfig = llmConfig ?: LlmConfig(model = "gpt-4o"),
                tools = tools,
                historyStrategy = historyStrategy,
                historyStore = historyStore,
                contentStoragePolicy = contentStoragePolicy,
                systemPrompt = systemPrompt,
                maxIterations = maxIterations,
                tokenBudget = tokenBudget,
                defaultTimeout = defaultTimeout,
                enableLogging = enableLogging,
            )

            Logger.enabled = enableLogging

            val toolManager = ToolManager()
            tools.forEach { toolManager.register(it) }

            val historyManager = HistoryManager(
                store = historyStore ?: InMemoryHistoryStore(),
                strategy = historyStrategy ?: SlidingWindowStrategy(),
                contentPolicy = contentStoragePolicy,
            )

            val contentProcessor = DefaultContentProcessor(
                context = context,
            )

            return AgentClient(config, toolManager, historyManager, contentProcessor)
        }
    }
}
