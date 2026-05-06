package com.fortrx.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual fun httpEngineFactory(): HttpClientEngineFactory<*> = CIO
