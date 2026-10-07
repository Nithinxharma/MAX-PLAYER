package com.lagradost.cloudstream3

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

object ParCollections {
    suspend fun <A, B> Iterable<A>.apmap(f: suspend (A) -> B): List<B> = supervisorScope {
        map { async { runCatching { f(it) }.getOrNull() } }.awaitAll().filterNotNull()
    }

    suspend fun <A, B> Array<A>.apmap(f: suspend (A) -> B): List<B> = supervisorScope {
        map { async { runCatching { f(it) }.getOrNull() } }.awaitAll().filterNotNull()
    }

    suspend fun <A, B> Iterable<A>.amap(f: suspend (A) -> B): List<B> =
        mapNotNull { runCatching { f(it) }.getOrNull() }

    suspend fun <A, B> Array<A>.amap(f: suspend (A) -> B): List<B> =
        mapNotNull { runCatching { f(it) }.getOrNull() }

    suspend fun <A, B> Iterable<A>.amapIndexed(f: suspend (Int, A) -> B): List<B> =
        mapIndexedNotNull { i, elem -> runCatching { f(i, elem) }.getOrNull() }

    suspend fun <A, B> Array<A>.amapIndexed(f: suspend (Int, A) -> B): List<B> =
        mapIndexedNotNull { i, elem -> runCatching { f(i, elem) }.getOrNull() }

    suspend fun <A> Iterable<A>.apfilter(predicate: suspend (A) -> Boolean): List<A> = supervisorScope {
        map { async { it to (runCatching { predicate(it) }.getOrDefault(false)) } }
            .awaitAll()
            .filter { it.second }
            .map { it.first }
    }

    suspend fun <A> Array<A>.apfilter(predicate: suspend (A) -> Boolean): List<A> = supervisorScope {
        map { async { it to (runCatching { predicate(it) }.getOrDefault(false)) } }
            .awaitAll()
            .filter { it.second }
            .map { it.first }
    }

    suspend fun argamap(vararg transforms: suspend () -> Any?): List<Any?> = supervisorScope {
        transforms.map {
            async {
                try {
                    it()
                } catch (t: Throwable) {
                    null
                }
            }
        }.awaitAll()
    }
}

// Top-level extensions for direct imports
suspend fun <A, B> Iterable<A>.apmap(f: suspend (A) -> B): List<B> = supervisorScope {
    map { async { runCatching { f(it) }.getOrNull() } }.awaitAll().filterNotNull()
}

suspend fun <A, B> Array<A>.apmap(f: suspend (A) -> B): List<B> = supervisorScope {
    map { async { runCatching { f(it) }.getOrNull() } }.awaitAll().filterNotNull()
}

suspend fun <A, B> Iterable<A>.amap(f: suspend (A) -> B): List<B> =
    mapNotNull { runCatching { f(it) }.getOrNull() }

suspend fun <A, B> Array<A>.amap(f: suspend (A) -> B): List<B> =
    mapNotNull { runCatching { f(it) }.getOrNull() }

suspend fun <A, B> Iterable<A>.amapIndexed(f: suspend (Int, A) -> B): List<B> =
    mapIndexedNotNull { i, elem -> runCatching { f(i, elem) }.getOrNull() }

suspend fun <A, B> Array<A>.amapIndexed(f: suspend (Int, A) -> B): List<B> =
    mapIndexedNotNull { i, elem -> runCatching { f(i, elem) }.getOrNull() }

suspend fun <A> Iterable<A>.apfilter(predicate: suspend (A) -> Boolean): List<A> = supervisorScope {
    map { async { it to (runCatching { predicate(it) }.getOrDefault(false)) } }
        .awaitAll()
        .filter { it.second }
        .map { it.first }
}

suspend fun <A> Array<A>.apfilter(predicate: suspend (A) -> Boolean): List<A> = supervisorScope {
    map { async { it to (runCatching { predicate(it) }.getOrDefault(false)) } }
        .awaitAll()
        .filter { it.second }
        .map { it.first }
}

suspend fun argamap(vararg transforms: suspend () -> Any?): List<Any?> = supervisorScope {
    transforms.map {
        async {
            try {
                it()
            } catch (t: Throwable) {
                null
            }
        }
    }.awaitAll()
}
