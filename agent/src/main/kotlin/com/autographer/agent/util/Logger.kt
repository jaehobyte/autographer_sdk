package com.autographer.agent.util

import android.util.Log

internal object Logger {

    private const val TAG = "Autographer"

    var enabled = false

    fun d(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun i(message: String) {
        if (enabled) Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (enabled) Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (enabled) Log.e(TAG, message, throwable)
    }
}
