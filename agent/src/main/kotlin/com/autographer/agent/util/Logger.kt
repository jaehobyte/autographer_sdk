package com.autographer.agent.util

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

internal object Logger {

    private const val TAG = "Autographer"

    private val _enabled = AtomicBoolean(false)

    var enabled: Boolean
        get() = _enabled.get()
        set(value) { _enabled.set(value) }

    fun d(message: String) {
        if (_enabled.get()) Log.d(TAG, message)
    }

    fun i(message: String) {
        if (_enabled.get()) Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (_enabled.get()) Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (_enabled.get()) Log.e(TAG, message, throwable)
    }
}
