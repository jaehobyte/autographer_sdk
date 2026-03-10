package com.autographer.agent.core

import kotlinx.coroutines.Job
import kotlin.time.Duration

internal class CancellationController(
    private val job: Job,
) {
    @Volatile
    var timeoutMs: Long = Long.MAX_VALUE
        private set

    private val startTime = System.currentTimeMillis()

    val isCancelled: Boolean
        get() = !job.isActive

    val isTimedOut: Boolean
        get() = (System.currentTimeMillis() - startTime) >= timeoutMs

    fun setTimeout(duration: Duration) {
        timeoutMs = duration.inWholeMilliseconds
    }

    fun cancel() {
        job.cancel()
    }

    fun checkCancellation() {
        if (!job.isActive) {
            throw AgentCancellationException()
        }
        if (isTimedOut) {
            throw AgentTimeoutException()
        }
    }
}
