package com.network

import com.fortrx.network.executeWithAuthRetryFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiAuthRetryFlowTest {
    @Test
    fun retriesOnceAfterAuthFailureWhenRefreshSucceeds() = kotlinx.coroutines.test.runTest {
        var calls = 0
        var refreshTokenArg: String? = null

        val result = executeWithAuthRetryFlow(
            originalToken = "stale-token",
            allowAuthRetry = true,
            call = {
                calls++
                if (calls == 1) 401 else 200
            },
            shouldRetry = { it == 401 || it == 403 },
            refresh = {
                refreshTokenArg = it
                true
            },
        )

        assertEquals(2, calls)
        assertEquals("stale-token", refreshTokenArg)
        assertEquals(200, result)
    }

    @Test
    fun doesNotRetryWhenAuthRetryIsDisabled() = kotlinx.coroutines.test.runTest {
        var calls = 0

        val result = executeWithAuthRetryFlow(
            originalToken = "stale-token",
            allowAuthRetry = false,
            call = {
                calls++
                401
            },
            shouldRetry = { it == 401 || it == 403 },
            refresh = { true },
        )

        assertEquals(1, calls)
        assertEquals(401, result)
    }

    @Test
    fun returnsOriginalFailureWhenRefreshFails() = kotlinx.coroutines.test.runTest {
        var calls = 0
        var refreshCount = 0

        val result = executeWithAuthRetryFlow(
            originalToken = null,
            allowAuthRetry = true,
            call = {
                calls++
                403
            },
            shouldRetry = { it == 401 || it == 403 },
            refresh = {
                refreshCount++
                false
            },
        )

        assertEquals(1, calls)
        assertEquals(1, refreshCount)
        assertEquals(403, result)
    }

    @Test
    fun ignoresNonAuthFailures() = kotlinx.coroutines.test.runTest {
        var calls = 0
        var refreshArg: String? = "unset"

        val result = executeWithAuthRetryFlow(
            originalToken = "token",
            allowAuthRetry = true,
            call = {
                calls++
                500
            },
            shouldRetry = { it == 401 || it == 403 },
            refresh = {
                refreshArg = it
                true
            },
        )

        assertEquals(1, calls)
        assertEquals(500, result)
        assertEquals("unset", refreshArg)
    }
}
