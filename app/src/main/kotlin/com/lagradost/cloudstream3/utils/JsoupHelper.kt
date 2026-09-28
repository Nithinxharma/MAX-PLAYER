package com.lagradost.cloudstream3.utils

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * Minimal CloudStream SDK Parity Helpers for Jsoup and Regex parsing.
 * Provides safe text, attribute extraction, and null-coalescing operations.
 */
fun Element.textClean(): String = this.text().trim()

fun Element.attrNull(attributeKey: String): String? = this.attr(attributeKey).ifBlank { null }

fun Element.src(): String? {
    val s = this.attr("src")
    if (s.isNotBlank()) return s
    val ds = this.attr("data-src")
    if (ds.isNotBlank()) return ds
    return null
}

fun Element.href(): String? = this.attr("href").ifBlank { null }

fun Elements.textClean(): List<String> = this.map { it.textClean() }

fun Elements.src(): List<String> = this.mapNotNull { it.src() }

fun Elements.href(): List<String> = this.mapNotNull { it.href() }

fun Element.selectFirstOrNull(cssQuery: String): Element? = runCatching { this.selectFirst(cssQuery) }.getOrNull()

fun Document.selectFirstOrNull(cssQuery: String): Element? = runCatching { this.selectFirst(cssQuery) }.getOrNull()

// Regex compatibility extensions
fun Regex.findFirst(input: CharSequence): String? = this.find(input)?.groupValues?.getOrNull(1) ?: this.find(input)?.value

fun CharSequence.findFirst(regex: Regex): String? = regex.findFirst(this)
