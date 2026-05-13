package com.network

import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertTrue

class SimpleDatetimeTest {
    @Test
    fun testNow() {
        val now = Clock.System.now()
        println("Current time: $now")
        assertTrue(now.toEpochMilliseconds() > 0)
    }
}
