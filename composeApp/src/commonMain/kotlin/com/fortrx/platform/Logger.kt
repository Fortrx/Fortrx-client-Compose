package com.fortrx.platform

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter

object AppLogger {
    private val logger = Logger(
        config = StaticConfig(logWriterList = listOf(platformLogWriter())),
        tag = "Fortrx"
    )

    fun d(message: String, throwable: Throwable? = null) {
        logger.d(throwable) { message }
    }

    fun i(message: String, throwable: Throwable? = null) {
        logger.i(throwable) { message }
    }

    fun e(message: String, throwable: Throwable? = null) {
        logger.e(throwable) { message }
    }

    fun w(message: String, throwable: Throwable? = null) {
        logger.w(throwable) { message }
    }
}

// Helper for easier migration from debugLog
fun debugLog(message: String, throwable: Throwable? = null) {
    AppLogger.d(message, throwable)
}
