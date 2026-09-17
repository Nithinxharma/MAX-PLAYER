package com.lagradost.api

import android.util.Log as AndroidLog

object Log {
    fun d(tag: String, msg: String) {
        AndroidLog.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        AndroidLog.i(tag, msg)
    }

    fun w(tag: String, msg: String) {
        AndroidLog.w(tag, msg)
    }

    fun e(tag: String, msg: String) {
        AndroidLog.e(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable?) {
        AndroidLog.e(tag, msg, tr)
    }

    fun printStackTrace(t: Throwable?) {
        t?.printStackTrace()
    }
}
