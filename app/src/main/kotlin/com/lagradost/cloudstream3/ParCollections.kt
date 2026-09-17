package com.lagradost.cloudstream3

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

object ParCollections {
    suspend fun <A, B> List<A>.apmap(f: suspend (A) -> B): List<B> = coroutineScope {
        map { async { f(it) } }.awaitAll()
    }

    suspend fun <A, B> List<A>.apmapIndexed(f: suspend (Int, A) -> B): List<B> = coroutineScope {
        mapIndexed { i, elem -> async { f(i, elem) } }.awaitAll()
    }

    suspend fun <A, B> List<A>.amap(f: suspend (A) -> B): List<B> =
        map { f(it) }

    suspend fun <A, B> List<A>.amapIndexed(f: suspend (Int, A) -> B): List<B> =
        mapIndexed { i, elem -> f(i, elem) }

    suspend fun <A> List<A>.apfilter(predicate: suspend (A) -> Boolean): List<A> = coroutineScope {
        map { async { it to predicate(it) } }
            .awaitAll()
            .filter { it.second }
            .map { it.first }
    }

    suspend fun <A> List<A>.apfilterIndexed(predicate: suspend (Int, A) -> Boolean): List<A> = coroutineScope {
        mapIndexed { i, elem -> async { elem to predicate(i, elem) } }
            .awaitAll()
            .filter { it.second }
            .map { it.first }
    }

    suspend fun argamap(vararg transforms: suspend () -> Any?): List<Any?> = coroutineScope {
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
suspend fun <A, B> List<A>.apmap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async { f(it) } }.awaitAll()
}

suspend fun <A, B> List<A>.apmapIndexed(f: suspend (Int, A) -> B): List<B> = coroutineScope {
    mapIndexed { i, elem -> async { f(i, elem) } }.awaitAll()
}

suspend fun <A, B> List<A>.amap(f: suspend (A) -> B): List<B> =
    map { f(it) }

suspend fun <A, B> List<A>.amapIndexed(f: suspend (Int, A) -> B): List<B> =
    mapIndexed { i, elem -> f(i, elem) }

suspend fun <A> List<A>.apfilter(predicate: suspend (A) -> Boolean): List<A> = coroutineScope {
    map { async { it to predicate(it) } }
        .awaitAll()
        .filter { it.second }
        .map { it.first }
}

suspend fun <A> List<A>.apfilterIndexed(predicate: suspend (Int, A) -> Boolean): List<A> = coroutineScope {
    mapIndexed { i, elem -> async { elem to predicate(i, elem) } }
        .awaitAll()
        .filter { it.second }
        .map { it.first }
}

suspend fun argamap(vararg transforms: suspend () -> Any?): List<Any?> = coroutineScope {
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
