package com.network

import com.fortrx.network.Api
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiUrlPolicyTest {
    @Test
    fun trimsTrailingSlashAndPreservesHttpsOrigin() {
        assertEquals(
            "https://example.com",
            Api.normalizedBaseUrl("https://example.com/"),
        )
    }

    @Test
    fun rejectsUrlsWithPaths() {
        assertFailsWith<IllegalArgumentException> {
            Api.normalizedBaseUrl("https://example.com/api")
        }
    }

    @Test
    fun rejectsUrlsWithQueryParameters() {
        assertFailsWith<IllegalArgumentException> {
            Api.normalizedBaseUrl("https://example.com?token=secret")
        }
    }

    @Test
    fun rejectsCleartextRemoteHosts() {
        assertFailsWith<IllegalArgumentException> {
            Api.normalizedBaseUrl("http://example.com", allowInsecureLocal = true)
        }
    }

    @Test
    fun allowsLocalCleartextOnlyWhenExplicitlyEnabled() {
        assertEquals(
            "http://10.0.2.2:8000",
            Api.normalizedBaseUrl("http://10.0.2.2:8000", allowInsecureLocal = true),
        )
    }

    @Test
    fun convertsHttpsToSecureWebsocket() {
        assertEquals(
            "wss://example.com",
            Api.websocketBaseUrl("https://example.com"),
        )
    }

    @Test
    fun convertsHttpToPlainWebsocketForLocalDevelopment() {
        assertEquals(
            "ws://127.0.0.1:8000",
            Api.websocketBaseUrl("http://127.0.0.1:8000"),
        )
    }
}
