package com.lagradost.cloudstream3

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.runBlocking

object ParCollections {
    suspend fun <A, B> List<A>.apmap(f: suspend (A) -> B): List<B> = supervisorScope {
        map { async { runCatching { f(it) }.getOrNull() } }.awaitAll().filterNotNull()
    }

    suspend fun <A, B> List<A>.apmapIndexed(f: suspend (Int, A) -> B): List<B> = supervisorScope {
        mapIndexed { i, elem -> async { runCatching { f(i, elem) }.getOrNull() } }.awaitAll().filterNotNull()
    }

    suspend fun <A, B> List<A>.amap(f: suspend (A) -> B): List<B> =
        mapNotNull { runCatching { f(it) }.getOrNull() }

    suspend fun <A, B> List<A>.amapIndexed(f: suspend (Int, A) -> B): List<B> =
        mapIndexedNotNull { i, elem -> runCatching { f(i, elem) }.getOrNull() }

    suspend fun <A> List<A>.apfilter(predicate: suspend (A) -> Boolean): List<A> = supervisorScope {
        map { async { it to (runCatching { predicate(it) }.getOrDefault(false)) } }
            .awaitAll()
            .filter { it.second }
            .map { it.first }
    }

    suspend fun <A> List<A>.apfilterIndexed(predicate: suspend (Int, A) -> Boolean): List<A> = supervisorScope {
        mapIndexed { i, elem -> async { elem to (runCatching { predicate(i, elem) }.getOrDefault(false)) } }
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

// Top-level extensions & functions for direct imports: import com.lagradost.cloudstream3.apmap etc.
suspend fun <A, B> List<A>.apmap(f: suspend (A) -> B): List<B> = supervisorScope {
    map { async { runCatching { f(it) }.getOrNull() } }.awaitAll().filterNotNull()
}

suspend fun <A, B> List<A>.apmapIndexed(f: suspend (Int, A) -> B): List<B> = supervisorScope {
    mapIndexed { i, elem -> async { runCatching { f(i, elem) }.getOrNull() } }.awaitAll().filterNotNull()
}

suspend fun <A, B> List<A>.amap(f: suspend (A) -> B): List<B> =
    mapNotNull { runCatching { f(it) }.getOrNull() }

suspend fun <A, B> List<A>.amapIndexed(f: suspend (Int, A) -> B): List<B> =
    mapIndexedNotNull { i, elem -> runCatching { f(i, elem) }.getOrNull() }

suspend fun <A> List<A>.apfilter(predicate: suspend (A) -> Boolean): List<A> = supervisorScope {
    map { async { it to (runCatching { predicate(it) }.getOrDefault(false)) } }
        .awaitAll()
        .filter { it.second }
        .map { it.first }
}

suspend fun <A> List<A>.apfilterIndexed(predicate: suspend (Int, A) -> Boolean): List<A> = supervisorScope {
    mapIndexed { i, elem -> async { elem to (runCatching { predicate(i, elem) }.getOrDefault(false)) } }
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
