package com.autographer.agent.content

enum class ContentType {
    TEXT, IMAGE, VIDEO, AUDIO, FILE
}

enum class ImageDetail {
    LOW, HIGH, AUTO
}

enum class FrameSampling {
    FPS_0_5, FPS_1, FPS_5, KEY_FRAMES
}

sealed class Content {
    abstract val type: ContentType

    data class Text(
        val text: String,
        val urls: List<String>? = null,
    ) : Content() {
        override val type = ContentType.TEXT
    }

    data class Image(
        val source: ContentSource,
        val mimeType: String = "image/jpeg",
        val detail: ImageDetail = ImageDetail.AUTO,
    ) : Content() {
        override val type = ContentType.IMAGE
    }

    data class Video(
        val source: ContentSource,
        val mimeType: String = "video/mp4",
        val sampling: FrameSampling = FrameSampling.FPS_1,
    ) : Content() {
        override val type = ContentType.VIDEO
    }

    data class Audio(
        val source: ContentSource,
        val mimeType: String = "audio/wav",
    ) : Content() {
        override val type = ContentType.AUDIO
    }

    data class File(
        val source: ContentSource,
        val mimeType: String,
        val fileName: String,
    ) : Content() {
        override val type = ContentType.FILE
    }
}
