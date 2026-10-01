package com.lagradost.cloudstream3.utils

import kotlinx.coroutines.*

object Coroutines {
    fun main(work: suspend () -> Unit): Job {
        return CoroutineScope(Dispatchers.Main).launch {
            try {
                work()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun <T> T.main(work: suspend ((T) -> Unit)): Job {
        val value = this
        return CoroutineScope(Dispatchers.Main).launch {
            try {
                work(value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun <T> T.ioSafe(work: suspend (CoroutineScope.(T) -> Unit)): Job {
        val value = this
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                work(value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun <T, V> V.ioWorkSafe(work: suspend (CoroutineScope.(V) -> T)): T? {
        val value = this
        return withContext(Dispatchers.IO) {
            try {
                work(value)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun <T, V> V.ioWork(work: suspend (CoroutineScope.(V) -> T)): T {
        val value = this
        return withContext(Dispatchers.IO) {
            work(value)
        }
    }

    suspend fun <T, V> V.mainWork(work: suspend (CoroutineScope.(V) -> T)): T {
        val value = this
        return withContext(Dispatchers.Main) {
            work(value)
        }
    }

    fun runOnMainThread(work: (() -> Unit)) {
        CoroutineScope(Dispatchers.Main).launch {
            work()
        }
    }

    class AtomicList<T>(private val list: MutableList<T> = mutableListOf()) : MutableList<T> by list {
        private val lock = Any()
        fun <R> withLock(action: (MutableList<T>) -> R): R = synchronized(lock) { action(list) }
    }
    fun <T> atomicListOf(): AtomicList<T> = AtomicList()
}
