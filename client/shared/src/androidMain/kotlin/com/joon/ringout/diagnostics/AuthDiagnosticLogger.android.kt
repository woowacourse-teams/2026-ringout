package com.joon.ringout.diagnostics

import android.util.Log

internal actual object AuthDiagnosticLogger {
    actual fun debug(message: String) {
        runCatching {
            Log.d(AUTH_LOG_TAG, message)
        }
    }

    actual fun error(
        message: String,
        cause: Throwable,
    ) {
        runCatching {
            Log.e(
                AUTH_LOG_TAG,
                "$message cause=${cause::class.simpleName} message=${cause.message.orEmpty()}",
            )
        }
    }
}

private const val AUTH_LOG_TAG = "RingoutAuth"
