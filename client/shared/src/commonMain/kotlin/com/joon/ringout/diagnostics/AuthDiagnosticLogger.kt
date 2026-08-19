package com.joon.ringout.diagnostics

internal expect object AuthDiagnosticLogger {
    fun debug(message: String)

    fun error(
        message: String,
        cause: Throwable,
    )
}
