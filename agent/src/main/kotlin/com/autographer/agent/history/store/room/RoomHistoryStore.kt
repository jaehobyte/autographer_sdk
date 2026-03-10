package com.autographer.agent.history.store.room

import android.content.Context
import com.autographer.agent.history.ConversationSession
import com.autographer.agent.history.SessionMeta
import com.autographer.agent.history.store.HistoryStore
import com.autographer.agent.model.Message
import com.autographer.agent.model.MessagePart
import com.autographer.agent.model.Role
import com.autographer.agent.model.ToolCall
import com.autographer.agent.util.JsonUtil

class RoomHistoryStore(context: Context) : HistoryStore {

    private val dao = HistoryDatabase.getInstance(context).messageDao()

    override suspend fun save(session: ConversationSession) {
        // Save/update session entity
        dao.insertSession(
            SessionEntity(
                id = session.id,
                metadataJson = if (session.metadata.isNotEmpty()) {
                    JsonUtil.toJson(session.metadata)
                } else null,
                createdAt = session.createdAt,
                updatedAt = session.updatedAt,
            )
        )

        // Delete existing messages and re-insert
        dao.deleteBySession(session.id)
        session.messages.forEach { message ->
            dao.insertMessage(toEntity(session.id, message))
        }
    }

    override suspend fun load(sessionId: String): ConversationSession? {
        val sessionEntity = dao.getSession(sessionId) ?: return null
        val messageEntities = dao.getMessagesBySession(sessionId)

        val metadata = sessionEntity.metadataJson?.let {
            try {
                JsonUtil.fromJson<MutableMap<String, Any>>(it) ?: mutableMapOf()
            } catch (e: Exception) {
                mutableMapOf()
            }
        } ?: mutableMapOf()

        return ConversationSession(
            id = sessionEntity.id,
            messages = messageEntities.map { fromEntity(it) }.toMutableList(),
            metadata = metadata,
            createdAt = sessionEntity.createdAt,
            updatedAt = sessionEntity.updatedAt,
        )
    }

    override suspend fun delete(sessionId: String) {
        dao.deleteBySession(sessionId)
        dao.deleteSession(sessionId)
    }

    override suspend fun listAll(): List<SessionMeta> {
        return dao.getAllSessions().map { session ->
            SessionMeta(
                id = session.id,
                title = null,
                messageCount = dao.getMessageCount(session.id),
                createdAt = session.createdAt,
                updatedAt = session.updatedAt,
            )
        }
    }

    private fun toEntity(sessionId: String, message: Message): MessageEntity {
        val partsData = message.parts.map { part ->
            when (part) {
                is MessagePart.Text -> mapOf("type" to "text", "text" to part.text)
                is MessagePart.ImageBase64 -> mapOf(
                    "type" to "image_base64",
                    "data" to part.data,
                    "mimeType" to part.mimeType,
                )
                is MessagePart.ImageUrl -> mapOf("type" to "image_url", "url" to part.url)
                is MessagePart.VideoFrames -> mapOf(
                    "type" to "video_frames",
                    "frameCount" to part.frames.size.toString(),
                )
                is MessagePart.AudioData -> mapOf(
                    "type" to "audio",
                    "data" to part.data,
                    "mimeType" to part.mimeType,
                )
                is MessagePart.FileData -> mapOf(
                    "type" to "file",
                    "data" to part.data,
                    "mimeType" to part.mimeType,
                    "fileName" to part.fileName,
                )
                is MessagePart.Description -> mapOf(
                    "type" to "description",
                    "originalType" to part.originalType.name,
                    "description" to part.description,
                )
            }
        }

        return MessageEntity(
            sessionId = sessionId,
            role = message.role.name,
            contentJson = JsonUtil.toJson(partsData),
            toolCallsJson = message.toolCalls?.let { JsonUtil.toJson(it) },
            toolCallId = message.toolCallId,
            timestamp = message.timestamp,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun fromEntity(entity: MessageEntity): Message {
        val partsData = try {
            JsonUtil.fromJson<List<Map<String, String>>>(entity.contentJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val parts = partsData.mapNotNull { data ->
            when (data["type"]) {
                "text" -> MessagePart.Text(data["text"] ?: "")
                "image_base64" -> MessagePart.ImageBase64(
                    data = data["data"] ?: "",
                    mimeType = data["mimeType"] ?: "image/jpeg",
                )
                "image_url" -> MessagePart.ImageUrl(data["url"] ?: "")
                "audio" -> MessagePart.AudioData(
                    data = data["data"] ?: "",
                    mimeType = data["mimeType"] ?: "audio/wav",
                )
                "file" -> MessagePart.FileData(
                    data = data["data"] ?: "",
                    mimeType = data["mimeType"] ?: "application/octet-stream",
                    fileName = data["fileName"] ?: "unknown",
                )
                "description" -> MessagePart.Description(
                    originalType = try {
                        com.autographer.agent.content.ContentType.valueOf(data["originalType"] ?: "TEXT")
                    } catch (e: Exception) {
                        com.autographer.agent.content.ContentType.TEXT
                    },
                    description = data["description"] ?: "",
                )
                else -> null
            }
        }

        val toolCalls = entity.toolCallsJson?.let {
            try {
                JsonUtil.fromJson<List<ToolCall>>(it)
            } catch (e: Exception) {
                null
            }
        }

        return Message(
            role = try {
                Role.valueOf(entity.role)
            } catch (e: Exception) {
                Role.USER
            },
            parts = parts,
            toolCalls = toolCalls,
            toolCallId = entity.toolCallId,
            timestamp = entity.timestamp,
        )
    }
}
