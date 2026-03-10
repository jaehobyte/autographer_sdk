package com.autographer.agent

import com.autographer.agent.core.AgentState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

class RequestHandle internal constructor(
    val id: String,
    internal val job: Job,
    private val stateFlow: StateFlow<AgentState>,
    private val onTimeout: (Duration) -> Unit,
) {
    val state: AgentState get() = stateFlow.value

    fun observeState(): StateFlow<AgentState> = stateFlow

    fun cancel() {
        job.cancel()
    }

    fun setTimeout(duration: Duration) {
        onTimeout(duration)
    }

    val isActive: Boolean get() = job.isActive

    suspend fun await() {
        job.join()
    }
}
