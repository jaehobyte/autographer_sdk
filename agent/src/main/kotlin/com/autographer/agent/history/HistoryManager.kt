package com.autographer.agent.history

import com.autographer.agent.history.store.HistoryStore
import com.autographer.agent.history.store.InMemoryHistoryStore
import com.autographer.agent.history.strategy.HistoryStrategy
import com.autographer.agent.history.strategy.SlidingWindowStrategy
import com.autographer.agent.model.Message
import com.autographer.agent.model.MessagePart
import com.autographer.agent.model.Role
import java.util.UUID

class HistoryManager(
    private val store: HistoryStore = InMemoryHistoryStore(),
    private val strategy: HistoryStrategy = SlidingWindowStrategy(),
    private val contentPolicy: ContentStoragePolicy = ContentStoragePolicy.DESCRIPTION,
) {

    suspend fun createSession(systemPrompt: String? = null): ConversationSession {
        val session = ConversationSession(id = UUID.randomUUID().toString())
        if (systemPrompt != null) {
            session.messages.add(
                Message(
                    role = Role.SYSTEM,
                    parts = listOf(MessagePart.Text(systemPrompt)),
                )
            )
        }
        store.save(session)
        return session
    }

    suspend fun loadSession(sessionId: String): ConversationSession? {
        return store.load(sessionId)
    }

    suspend fun addMessage(sessionId: String, message: Message) {
        val session = store.load(sessionId) ?: return
        val processedMessage = applyContentPolicy(message, session.messages.size)
        session.messages.add(processedMessage)
        session.updatedAt = System.currentTimeMillis()
        store.save(session)
    }

    suspend fun getEffectiveHistory(sessionId: String, maxTokens: Int): List<Message> {
        val session = store.load(sessionId) ?: return emptyList()
        return strategy.compact(session.messages, maxTokens)
    }

    suspend fun deleteSession(sessionId: String) {
        store.delete(sessionId)
    }

    suspend fun listSessions(): List<SessionMeta> {
        return store.listAll()
    }

    private fun applyContentPolicy(message: Message, turnIndex: Int): Message {
        if (contentPolicy == ContentStoragePolicy.INLINE) return message

        val processedParts = message.parts.map { part ->
            when (contentPolicy) {
                ContentStoragePolicy.INLINE -> part
                ContentStoragePolicy.REFERENCE -> part // keep as-is; URL parts already reference
                ContentStoragePolicy.DESCRIPTION -> convertToDescriptionIfMedia(part)
                ContentStoragePolicy.DISCARD -> discardMediaPart(part)
            }
        }.filterNotNull()

        return message.copy(parts = processedParts)
    }

    private fun convertToDescriptionIfMedia(part: MessagePart): MessagePart {
        return when (part) {
            is MessagePart.Text -> part
            is MessagePart.ImageBase64 -> MessagePart.Description(
                com.autographer.agent.content.ContentType.IMAGE,
                "[Image: ${part.mimeType}]"
            )
            is MessagePart.ImageUrl -> MessagePart.Description(
                com.autographer.agent.content.ContentType.IMAGE,
                "[Image: ${part.url}]"
            )
            is MessagePart.VideoFrames -> MessagePart.Description(
                com.autographer.agent.content.ContentType.VIDEO,
                "[Video: ${part.frames.size} frames]"
            )
            is MessagePart.AudioData -> MessagePart.Description(
                com.autographer.agent.content.ContentType.AUDIO,
                "[Audio: ${part.mimeType}]"
            )
            is MessagePart.FileData -> MessagePart.Description(
                com.autographer.agent.content.ContentType.FILE,
                "[File: ${part.fileName}]"
            )
            is MessagePart.Description -> part
        }
    }

    @Suppress("USELESS_CAST")
    private fun discardMediaPart(part: MessagePart): MessagePart? {
        return when (part) {
            is MessagePart.Text -> part
            is MessagePart.Description -> part
            else -> null
        }
    }
}
