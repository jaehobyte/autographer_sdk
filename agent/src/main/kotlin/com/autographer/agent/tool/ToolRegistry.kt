package com.autographer.agent.tool

import java.util.concurrent.ConcurrentHashMap

class ToolRegistry {

    private val tools = ConcurrentHashMap<String, Tool>()

    fun register(tool: Tool) {
        require(!tools.containsKey(tool.name)) {
            "Tool '${tool.name}' is already registered"
        }
        tools[tool.name] = tool
    }

    fun unregister(name: String): Tool? {
        return tools.remove(name)
    }

    fun get(name: String): Tool? = tools[name]

    fun getAll(): List<Tool> = tools.values.toList()

    fun getSchemas(): List<ToolSchema> = tools.values.map { it.parameterSchema }

    fun clear() {
        tools.clear()
    }

    val size: Int get() = tools.size
}
