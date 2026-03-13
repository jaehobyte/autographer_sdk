package com.autographer.agent.history.store

import com.autographer.agent.history.ConversationSession
import com.autographer.agent.history.SessionMeta
import com.autographer.agent.model.MessagePart
import java.util.concurrent.ConcurrentHashMap

class InMemoryHistoryStore : HistoryStore {

    private val sessions = ConcurrentHashMap<String, ConversationSession>()

    override suspend fun save(session: ConversationSession) {
        session.updatedAt = System.currentTimeMillis()
        sessions[session.id] = session
    }

    override suspend fun load(sessionId: String): ConversationSession? {
        return sessions[sessionId]
    }

    override suspend fun delete(sessionId: String) {
        sessions.remove(sessionId)
    }

    override suspend fun listAll(): List<SessionMeta> {
        return sessions.values.map { session ->
            SessionMeta(
                id = session.id,
                title = session.messages.firstOrNull()?.parts
                    ?.filterIsInstance<MessagePart.Text>()
                    ?.firstOrNull()?.text?.take(50),
                messageCount = session.messages.size,
                createdAt = session.createdAt,
                updatedAt = session.updatedAt,
            )
        }
    }
}
