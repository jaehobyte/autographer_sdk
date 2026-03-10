package com.autographer.agent.tool

import com.autographer.agent.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ToolExecutor(
    private val registry: ToolRegistry,
    private val defaultTimeout: Duration = 30.seconds,
) {

    suspend fun execute(
        callId: String,
        name: String,
        args: Map<String, Any?>,
        timeout: Duration = defaultTimeout,
    ): ToolResult {
        val tool = registry.get(name)
            ?: return ToolResult.Error(callId, "Tool '$name' not found")

        return try {
            withContext(Dispatchers.IO) {
                withTimeout(timeout.inWholeMilliseconds) {
                    tool.execute(args).also {
                        Logger.d("Tool '$name' executed successfully")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Logger.w("Tool '$name' timed out after $timeout")
            ToolResult.Error(callId, "Tool '$name' timed out after $timeout", e)
        } catch (e: Exception) {
            Logger.e("Tool '$name' failed: ${e.message}", e)
            ToolResult.Error(callId, "Tool '$name' failed: ${e.message}", e)
        }
    }
}
