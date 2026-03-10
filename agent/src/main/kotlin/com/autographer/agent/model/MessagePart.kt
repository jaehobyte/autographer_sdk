package com.autographer.agent.model

import com.autographer.agent.content.ContentType

sealed class MessagePart {

    data class Text(val text: String) : MessagePart()

    data class ImageBase64(
        val data: String,
        val mimeType: String,
    ) : MessagePart()

    data class ImageUrl(val url: String) : MessagePart()

    data class VideoFrames(
        val frames: List<ImageBase64>,
    ) : MessagePart()

    data class AudioData(
        val data: String,
        val mimeType: String,
    ) : MessagePart()

    data class FileData(
        val data: String,
        val mimeType: String,
        val fileName: String,
    ) : MessagePart()

    data class Description(
        val originalType: ContentType,
        val description: String,
    ) : MessagePart()
}
