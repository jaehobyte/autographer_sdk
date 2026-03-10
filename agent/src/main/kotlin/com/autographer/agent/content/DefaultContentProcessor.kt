package com.autographer.agent.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.autographer.agent.llm.LlmCapability
import com.autographer.agent.model.MessagePart
import com.autographer.agent.util.Logger
import java.io.ByteArrayOutputStream
import java.net.URL

class DefaultContentProcessor(
    private val context: Context? = null,
    private val maxImageSize: Int = 1024,
) : ContentProcessor {

    override suspend fun process(
        contents: List<Content>,
        capabilities: Set<LlmCapability>,
    ): List<MessagePart> {
        return contents.flatMap { content ->
            processContent(content, capabilities)
        }
    }

    private fun processContent(
        content: Content,
        capabilities: Set<LlmCapability>,
    ): List<MessagePart> {
        return when (content) {
            is Content.Text -> listOf(MessagePart.Text(content.text))
            is Content.Image -> processImage(content, capabilities)
            is Content.Video -> processVideo(content, capabilities)
            is Content.Audio -> processAudio(content, capabilities)
            is Content.File -> processFile(content, capabilities)
        }
    }

    private fun processImage(
        image: Content.Image,
        capabilities: Set<LlmCapability>,
    ): List<MessagePart> {
        if (LlmCapability.IMAGE !in capabilities) {
            Logger.d("Image not supported, converting to description")
            return listOf(
                MessagePart.Description(ContentType.IMAGE, "[Image provided: ${image.mimeType}]")
            )
        }

        return when (val source = image.source) {
            is ContentSource.UrlSource -> listOf(MessagePart.ImageUrl(source.url))
            is ContentSource.Base64Encoded -> listOf(
                MessagePart.ImageBase64(source.encoded, source.mimeType)
            )
            is ContentSource.RawBytes -> {
                val resized = resizeImage(source.data)
                val encoded = Base64.encodeToString(resized, Base64.NO_WRAP)
                listOf(MessagePart.ImageBase64(encoded, image.mimeType))
            }
            is ContentSource.UriSource -> {
                val bytes = resolveUri(source)
                if (bytes != null) {
                    val resized = resizeImage(bytes)
                    val encoded = Base64.encodeToString(resized, Base64.NO_WRAP)
                    listOf(MessagePart.ImageBase64(encoded, image.mimeType))
                } else {
                    listOf(MessagePart.Description(ContentType.IMAGE, "[Failed to load image]"))
                }
            }
        }
    }

    private fun processVideo(
        video: Content.Video,
        capabilities: Set<LlmCapability>,
    ): List<MessagePart> {
        if (LlmCapability.VIDEO !in capabilities) {
            Logger.d("Video not supported, falling back to description")
            return listOf(
                MessagePart.Description(ContentType.VIDEO, "[Video provided: ${video.mimeType}]")
            )
        }

        // For providers that support video, encode as base64
        val bytes = resolveSource(video.source)
        return if (bytes != null) {
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            // Return as file data since MessagePart doesn't have a direct video type for raw upload
            listOf(MessagePart.FileData(encoded, video.mimeType, "video"))
        } else {
            listOf(MessagePart.Description(ContentType.VIDEO, "[Failed to load video]"))
        }
    }

    private fun processAudio(
        audio: Content.Audio,
        capabilities: Set<LlmCapability>,
    ): List<MessagePart> {
        if (LlmCapability.AUDIO !in capabilities) {
            return listOf(
                MessagePart.Description(ContentType.AUDIO, "[Audio provided: ${audio.mimeType}]")
            )
        }

        val bytes = resolveSource(audio.source)
        return if (bytes != null) {
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            listOf(MessagePart.AudioData(encoded, audio.mimeType))
        } else {
            listOf(MessagePart.Description(ContentType.AUDIO, "[Failed to load audio]"))
        }
    }

    private fun processFile(
        file: Content.File,
        capabilities: Set<LlmCapability>,
    ): List<MessagePart> {
        if (LlmCapability.FILE !in capabilities) {
            return listOf(
                MessagePart.Description(ContentType.FILE, "[File: ${file.fileName} (${file.mimeType})]")
            )
        }

        val bytes = resolveSource(file.source)
        return if (bytes != null) {
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            listOf(MessagePart.FileData(encoded, file.mimeType, file.fileName))
        } else {
            listOf(MessagePart.Description(ContentType.FILE, "[Failed to load file: ${file.fileName}]"))
        }
    }

    private fun resolveSource(source: ContentSource): ByteArray? {
        return when (source) {
            is ContentSource.RawBytes -> source.data
            is ContentSource.Base64Encoded -> Base64.decode(source.encoded, Base64.DEFAULT)
            is ContentSource.UriSource -> resolveUri(source)
            is ContentSource.UrlSource -> {
                try {
                    URL(source.url).readBytes()
                } catch (e: Exception) {
                    Logger.e("Failed to download from URL: ${source.url}", e)
                    null
                }
            }
        }
    }

    private fun resolveUri(source: ContentSource.UriSource): ByteArray? {
        val ctx = context ?: return null
        return try {
            ctx.contentResolver.openInputStream(source.uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Logger.e("Failed to resolve URI: ${source.uri}", e)
            null
        }
    }

    private fun resizeImage(data: ByteArray): ByteArray {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)

        val width = options.outWidth
        val height = options.outHeight

        if (width <= maxImageSize && height <= maxImageSize) return data

        val scale = maxImageSize.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            ?: return data
        val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        val output = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 85, output)

        if (resized != bitmap) resized.recycle()
        bitmap.recycle()

        return output.toByteArray()
    }
}
