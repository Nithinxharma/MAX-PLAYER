package com.lagradost.api

import android.util.Log as AndroidLog

object Log {
    fun d(tag: String, message: String) {
        try {
            AndroidLog.d(tag, message)
        } catch (_: Throwable) {
            println("[$tag][DEBUG] $message")
        }
    }

    fun i(tag: String, message: String) {
        try {
            AndroidLog.i(tag, message)
        } catch (_: Throwable) {
            println("[$tag][INFO] $message")
        }
    }

    fun w(tag: String, message: String) {
        try {
            AndroidLog.w(tag, message)
        } catch (_: Throwable) {
            println("[$tag][WARN] $message")
        }
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        try {
            AndroidLog.w(tag, message, throwable)
        } catch (_: Throwable) {
            println("[$tag][WARN] $message: ${throwable.message}")
        }
    }

    fun e(tag: String, message: String) {
        try {
            AndroidLog.e(tag, message)
        } catch (_: Throwable) {
            println("[$tag][ERROR] $message")
        }
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        try {
            AndroidLog.e(tag, message, throwable)
        } catch (_: Throwable) {
            println("[$tag][ERROR] $message: ${throwable.message}")
        }
    }
}
