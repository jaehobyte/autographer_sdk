package com.autographer.agent.content

import com.autographer.agent.llm.LlmCapability
import com.autographer.agent.model.MessagePart

interface ContentProcessor {
    suspend fun process(
        contents: List<Content>,
        capabilities: Set<LlmCapability>,
    ): List<MessagePart>
}
