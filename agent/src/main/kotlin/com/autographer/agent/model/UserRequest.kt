package com.autographer.agent.model

import com.autographer.agent.content.Content

data class UserRequest(
    val contents: List<Content>,
    val sessionId: String? = null,
    val metadata: Map<String, Any>? = null,
) {
    companion object {
        fun text(prompt: String, sessionId: String? = null): UserRequest {
            return UserRequest(
                contents = listOf(Content.Text(prompt)),
                sessionId = sessionId,
            )
        }
    }
}

class UserRequestBuilder {
    private val contents = mutableListOf<Content>()
    var sessionId: String? = null
    var metadata: Map<String, Any>? = null

    fun text(text: String) {
        contents.add(Content.Text(text))
    }

    fun image(source: com.autographer.agent.content.ContentSource, mimeType: String = "image/jpeg") {
        contents.add(Content.Image(source, mimeType))
    }

    fun video(source: com.autographer.agent.content.ContentSource, mimeType: String = "video/mp4") {
        contents.add(Content.Video(source, mimeType))
    }

    fun audio(source: com.autographer.agent.content.ContentSource, mimeType: String = "audio/wav") {
        contents.add(Content.Audio(source, mimeType))
    }

    fun file(source: com.autographer.agent.content.ContentSource, mimeType: String, fileName: String) {
        contents.add(Content.File(source, mimeType, fileName))
    }

    fun build(): UserRequest = UserRequest(contents, sessionId, metadata)
}

inline fun userRequest(block: UserRequestBuilder.() -> Unit): UserRequest {
    return UserRequestBuilder().apply(block).build()
}
