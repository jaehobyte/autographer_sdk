package com.autographer.agent.core

open class AgentException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class LlmException(
    message: String,
    val code: Int? = null,
    cause: Throwable? = null,
) : AgentException(message, cause)

class ToolExecutionException(
    val toolName: String,
    message: String,
    cause: Throwable? = null,
) : AgentException("Tool '$toolName': $message", cause)

class AgentCancellationException(
    message: String = "Agent execution was cancelled",
) : AgentException(message)

class AgentTimeoutException(
    message: String = "Agent execution timed out",
) : AgentException(message)

class MaxIterationsException(
    val iterations: Int,
) : AgentException("Agent loop exceeded max iterations: $iterations")
