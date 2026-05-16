package com.fortrx.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimeFormatsTest {
    @Test
    fun parseInstantOrNull_supportsZuluOffsetAndNaiveServerTimestamps() {
        val zulu = TimeFormats.parseInstantOrNull("2026-05-14T10:20:30Z")
        val offset = TimeFormats.parseInstantOrNull("2026-05-14T15:50:30+05:30")
        val naive = TimeFormats.parseInstantOrNull("2026-05-14T10:20:30")

        assertNotNull(zulu)
        assertNotNull(offset)
        assertNotNull(naive)
        assertEquals(zulu.toEpochMilliseconds(), offset.toEpochMilliseconds())
        assertEquals(zulu.toEpochMilliseconds(), naive.toEpochMilliseconds())
    }

    @Test
    fun sortEpochMillis_ordersByParsedInstant() {
        val earlier = TimeFormats.sortEpochMillis("2026-05-14T10:20:29")
        val later = TimeFormats.sortEpochMillis("2026-05-14T10:20:30Z")

        assertTrue(later > earlier)
    }
}
