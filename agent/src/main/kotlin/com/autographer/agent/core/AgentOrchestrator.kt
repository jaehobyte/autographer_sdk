package com.autographer.agent.core

import com.autographer.agent.AgentCallback
import com.autographer.agent.AgentConfig
import com.autographer.agent.AgentResponse
import com.autographer.agent.content.ContentProcessor
import com.autographer.agent.content.DefaultContentProcessor
import com.autographer.agent.history.HistoryManager
import com.autographer.agent.llm.FinishReason
import com.autographer.agent.llm.LlmProvider
import com.autographer.agent.llm.LlmResponse
import com.autographer.agent.llm.TokenUsage
import com.autographer.agent.llm.ToolCallDelta
import com.autographer.agent.model.Message
import com.autographer.agent.model.MessagePart
import com.autographer.agent.model.Role
import com.autographer.agent.model.ToolCall
import com.autographer.agent.model.UserRequest
import com.autographer.agent.tool.ToolManager
import com.autographer.agent.tool.ToolResult
import com.autographer.agent.util.JsonUtil
import com.autographer.agent.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class AgentOrchestrator(
    private val config: AgentConfig,
    private val llmProvider: LlmProvider,
    private val toolManager: ToolManager,
    private val historyManager: HistoryManager,
    private val contentProcessor: ContentProcessor = DefaultContentProcessor(),
) {
    val stateFlow = MutableStateFlow<AgentState>(AgentState.Idle)

    suspend fun execute(
        request: UserRequest,
        sessionId: String,
        callback: AgentCallback,
        cancellation: CancellationController,
    ): AgentResponse {
        val toolCallHistory = mutableListOf<ToolCall>()
        var lastUsage: TokenUsage? = null

        try {
            emitState(AgentState.Planning, callback)

            // 1. Process multimodal content
            val messageParts = contentProcessor.process(
                request.contents,
                llmProvider.capabilities(),
            )

            val userMessage = Message(
                role = Role.USER,
                parts = messageParts,
            )

            historyManager.addMessage(sessionId, userMessage)

            // 2. Agent loop
            var iteration = 0
            while (iteration < config.maxIterations) {
                cancellation.checkCancellation()
                iteration++
                Logger.d("Agent loop iteration $iteration")

                // Get effective history
                val messages = historyManager.getEffectiveHistory(
                    sessionId, config.tokenBudget
                )

                // Get tool schemas
                val toolSchemas = toolManager.getSchemas().takeIf { it.isNotEmpty() }

                emitState(AgentState.AwaitingResponse, callback)

                // Call LLM
                val response = if (llmProvider.capabilities()
                        .contains(com.autographer.agent.llm.LlmCapability.STREAMING)
                ) {
                    collectStreamResponse(
                        llmProvider.stream(messages, toolSchemas, config.llmConfig),
                        callback,
                        cancellation,
                    )
                } else {
                    llmProvider.complete(messages, toolSchemas, config.llmConfig)
                }

                lastUsage = response.usage

                // Save assistant message to history
                historyManager.addMessage(sessionId, response.message)

                // Check if we have tool calls
                val toolCalls = response.message.toolCalls
                if (toolCalls.isNullOrEmpty() || response.finishReason != FinishReason.TOOL_CALLS) {
                    // Final response
                    emitState(AgentState.Completed, callback)
                    return AgentResponse(
                        message = response.message,
                        sessionId = sessionId,
                        toolCallHistory = toolCallHistory,
                        usage = lastUsage,
                    )
                }

                // Execute tool calls
                for (toolCall in toolCalls) {
                    cancellation.checkCancellation()

                    emitState(AgentState.CallingTool(toolCall.name), callback)
                    callback.onToolCall(toolCall)
                    toolCallHistory.add(toolCall)

                    Logger.d("Executing tool: ${toolCall.name}")
                    val result = toolManager.execute(
                        callId = toolCall.id,
                        name = toolCall.name,
                        args = toolCall.arguments,
                    )

                    callback.onToolResult(toolCall, result)

                    // Add tool result to history
                    val toolMessage = Message(
                        role = Role.TOOL,
                        parts = listOf(
                            MessagePart.Text(
                                when (result) {
                                    is ToolResult.Success -> result.output
                                    is ToolResult.Error -> "Error: ${result.message}"
                                }
                            )
                        ),
                        toolCallId = toolCall.id,
                    )

                    historyManager.addMessage(sessionId, toolMessage)
                }
            }

            // Max iterations exceeded
            throw MaxIterationsException(config.maxIterations)

        } catch (e: AgentCancellationException) {
            emitState(AgentState.Cancelled, callback)
            callback.onCancelled()
            throw e
        } catch (e: AgentTimeoutException) {
            emitState(AgentState.Failed(e), callback)
            callback.onError(e)
            throw e
        } catch (e: AgentException) {
            emitState(AgentState.Failed(e), callback)
            callback.onError(e)
            throw e
        } catch (e: Exception) {
            val agentError = LlmException("Unexpected error: ${e.message}", cause = e)
            emitState(AgentState.Failed(agentError), callback)
            callback.onError(agentError)
            throw agentError
        }
    }

    private suspend fun collectStreamResponse(
        flow: Flow<LlmResponse.Chunk>,
        callback: AgentCallback,
        cancellation: CancellationController,
    ): LlmResponse.Complete {
        val textBuilder = StringBuilder()
        val toolCallBuilders = mutableMapOf<Int, ToolCallBuilder>()
        var finishReason = FinishReason.STOP
        var usage: TokenUsage? = null

        flow.collect { chunk ->
            cancellation.checkCancellation()

            chunk.delta?.let { delta ->
                textBuilder.append(delta)
                callback.onPartialResponse(delta)
            }

            chunk.toolCallDelta?.let { delta ->
                val builder = toolCallBuilders.getOrPut(delta.index) {
                    ToolCallBuilder()
                }
                delta.id?.let { builder.id = it }
                delta.name?.let { builder.name = it }
                delta.argumentsDelta?.let { builder.argumentsBuilder.append(it) }
            }

            chunk.finishReason?.let { finishReason = it }
            chunk.usage?.let { usage = it }
        }

        val parts = mutableListOf<MessagePart>()
        val text = textBuilder.toString()
        if (text.isNotEmpty()) {
            parts.add(MessagePart.Text(text))
        }

        val toolCalls = toolCallBuilders.values.mapNotNull { builder ->
            val name = builder.name ?: return@mapNotNull null
            val id = builder.id ?: java.util.UUID.randomUUID().toString()
            val args = try {
                val argsStr = builder.argumentsBuilder.toString()
                if (argsStr.isNotEmpty()) {
                    JsonUtil.fromJson<Map<String, Any?>>(argsStr) ?: emptyMap()
                } else {
                    emptyMap()
                }
            } catch (e: Exception) {
                emptyMap<String, Any?>()
            }
            ToolCall(id = id, name = name, arguments = args)
        }

        return LlmResponse.Complete(
            message = Message(
                role = Role.ASSISTANT,
                parts = parts,
                toolCalls = toolCalls.takeIf { it.isNotEmpty() },
            ),
            finishReason = finishReason,
            usage = usage,
        )
    }

    private fun emitState(state: AgentState, callback: AgentCallback) {
        stateFlow.value = state
        callback.onStateChanged(state)
    }

    private class ToolCallBuilder {
        var id: String? = null
        var name: String? = null
        val argumentsBuilder = StringBuilder()
    }
}
