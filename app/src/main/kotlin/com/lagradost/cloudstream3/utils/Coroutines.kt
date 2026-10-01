package com.lagradost.cloudstream3.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Coroutines {
    fun main(work: suspend (() -> Unit)): Job =
        CoroutineScope(Dispatchers.Main).launch {
            work()
        }

    fun io(work: suspend (() -> Unit)): Job =
        CoroutineScope(Dispatchers.IO).launch {
            work()
        }

    suspend fun <T> ioSafe(work: suspend (() -> T)): T? = withContext(Dispatchers.IO) {
        try {
            work()
        } catch (t: Throwable) {
            android.util.Log.e("CloudStream", "ioSafe caught error: ${t.message}", t)
            null
        }
    }
}
