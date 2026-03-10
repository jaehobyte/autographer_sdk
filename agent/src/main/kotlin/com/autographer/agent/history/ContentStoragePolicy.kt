package com.autographer.agent.history

enum class ContentStoragePolicy {
    /** Keep original media inline in history */
    INLINE,
    /** Store only URI/URL reference, reload when needed */
    REFERENCE,
    /** Replace media with LLM-generated text description */
    DESCRIPTION,
    /** Remove media from older turns entirely */
    DISCARD,
}
