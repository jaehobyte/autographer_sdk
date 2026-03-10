package com.autographer.agent.history.store.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "role")
    val role: String,

    @ColumnInfo(name = "content_json")
    val contentJson: String,

    @ColumnInfo(name = "tool_calls_json")
    val toolCallsJson: String? = null,

    @ColumnInfo(name = "tool_call_id")
    val toolCallId: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "metadata_json")
    val metadataJson: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
