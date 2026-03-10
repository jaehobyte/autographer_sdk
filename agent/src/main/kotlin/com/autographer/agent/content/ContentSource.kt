package com.autographer.agent.content

import android.net.Uri

sealed class ContentSource {
    data class RawBytes(val data: ByteArray) : ContentSource() {
        override fun equals(other: Any?): Boolean =
            other is RawBytes && data.contentEquals(other.data)
        override fun hashCode(): Int = data.contentHashCode()
    }

    data class Base64Encoded(val encoded: String, val mimeType: String) : ContentSource()
    data class UriSource(val uri: Uri) : ContentSource()
    data class UrlSource(val url: String) : ContentSource()
}
