package com.lagradost.cloudstream3.utils

import android.content.Context
import androidx.annotation.StringRes
import com.lagradost.cloudstream3.utils.ExtractorApi

sealed class UiText {
    data class DynamicString(val value: String) : UiText()
    data class StringResource(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText()

    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args.toTypedArray())
        }
    }
}

fun txt(text: String): UiText = UiText.DynamicString(text)
fun txt(@StringRes resId: Int, vararg args: Any): UiText = UiText.StringResource(resId, args.toList())

object UIHelper {
    fun colorFromAttribute(attr: Int): Int = 0xFF00ADB5.toInt()
    fun Context.colorFromAttribute(attr: Int): Int = 0xFF00ADB5.toInt()
}

object AppContextUtils {
    fun Context.getApiProviderLangSettings(): Set<String> = setOf("All", "en")
}

val extractorApis: MutableList<ExtractorApi> get() = com.lagradost.cloudstream3.APIHolder.extractorApis
