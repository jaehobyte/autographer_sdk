package com.autographer.agent.tool

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ToolManager(
    toolTimeout: Duration = 30.seconds,
) {
    val registry = ToolRegistry()
    private val executor = ToolExecutor(registry, toolTimeout)

    fun register(tool: Tool) = registry.register(tool)
    fun unregister(name: String) = registry.unregister(name)
    fun getSchemas(): List<ToolSchema> = registry.getSchemas()

    suspend fun execute(
        callId: String,
        name: String,
        args: Map<String, Any?>,
        timeout: Duration? = null,
    ): ToolResult {
        return if (timeout != null) {
            executor.execute(callId, name, args, timeout)
        } else {
            executor.execute(callId, name, args)
        }
    }
}
