package com.lagradost.cloudstream3.utils

import android.content.Context

object DataStoreHelper {
    fun <T> Context.setKey(folder: String, key: String, value: T) {
        DataStore.setKey(folder, key, value)
    }

    fun <T> Context.setKey(key: String, value: T) {
        DataStore.setKey(key, value)
    }

    fun <T> Context.getKey(folder: String, key: String, default: T): T {
        return DataStore.getKey(folder, key, default)
    }

    fun <T> Context.getKey(key: String, default: T): T {
        return DataStore.getKey(key, default)
    }

    fun Context.removeKey(folder: String, key: String) {
        DataStore.removeKey(folder, key)
    }

    fun Context.removeKey(key: String) {
        DataStore.removeKey(key)
    }
}
