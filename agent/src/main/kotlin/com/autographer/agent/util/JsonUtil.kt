package com.autographer.agent.util

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal object JsonUtil {

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    inline fun <reified T> fromJson(json: String): T? {
        return moshi.adapter(T::class.java).fromJson(json)
    }

    inline fun <reified T> toJson(value: T): String {
        return moshi.adapter(T::class.java).toJson(value)
    }
}
