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

    fun d(tag: String, message: String, throwable: Throwable) {
        try {
            AndroidLog.d(tag, message, throwable)
        } catch (_: Throwable) {
            println("[$tag][DEBUG] $message: ${throwable.message}")
        }
    }

    fun d(message: String) = d("CloudStream", message)

    fun i(tag: String, message: String) {
        try {
            AndroidLog.i(tag, message)
        } catch (_: Throwable) {
            println("[$tag][INFO] $message")
        }
    }

    fun i(tag: String, message: String, throwable: Throwable) {
        try {
            AndroidLog.i(tag, message, throwable)
        } catch (_: Throwable) {
            println("[$tag][INFO] $message: ${throwable.message}")
        }
    }

    fun i(message: String) = i("CloudStream", message)

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

    fun w(message: String) = w("CloudStream", message)

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

    fun e(message: String) = e("CloudStream", message)

    fun v(tag: String, message: String) {
        try {
            AndroidLog.v(tag, message)
        } catch (_: Throwable) {
            println("[$tag][VERBOSE] $message")
        }
    }

    fun v(tag: String, message: String, throwable: Throwable) {
        try {
            AndroidLog.v(tag, message, throwable)
        } catch (_: Throwable) {
            println("[$tag][VERBOSE] $message: ${throwable.message}")
        }
    }

    fun v(message: String) = v("CloudStream", message)
}
