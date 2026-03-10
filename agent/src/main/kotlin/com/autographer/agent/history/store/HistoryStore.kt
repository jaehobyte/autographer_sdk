package com.autographer.agent.history.store

import com.autographer.agent.history.ConversationSession
import com.autographer.agent.history.SessionMeta

interface HistoryStore {
    suspend fun save(session: ConversationSession)
    suspend fun load(sessionId: String): ConversationSession?
    suspend fun delete(sessionId: String)
    suspend fun listAll(): List<SessionMeta>
}
