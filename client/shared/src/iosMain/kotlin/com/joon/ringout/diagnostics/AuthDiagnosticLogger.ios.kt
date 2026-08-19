package com.joon.ringout.diagnostics

import platform.Foundation.NSLog

internal actual object AuthDiagnosticLogger {
    actual fun debug(message: String) {
        NSLog("[RingoutAuth] $message")
    }

    actual fun error(
        message: String,
        cause: Throwable,
    ) {
        NSLog(
            "[RingoutAuth] $message cause=${cause::class.simpleName} " +
                "message=${cause.message.orEmpty()}",
        )
    }
}
