package com.autographer.agent.util

internal object TokenCounter {

    /**
     * Approximate token count using the ~4 chars per token heuristic.
     * This is a rough estimate suitable for budget management.
     */
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        val charCount = text.length
        val wordCount = text.split(Regex("\\s+")).size
        return ((charCount + wordCount) / 4).coerceAtLeast(1)
    }
}
