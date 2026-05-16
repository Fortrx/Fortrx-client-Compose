package com.fortrx.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LongMessagePreviewFormatterTest {
    @Test
    fun format_collapsesOnlyWhenWordCountExceedsThreshold() {
        val shortText = (1..700).joinToString(" ") { "word$it" }
        val longText = (1..701).joinToString(" ") { "word$it" }

        val shortPreview = LongMessagePreviewFormatter.format(shortText, expanded = false)
        val longPreview = LongMessagePreviewFormatter.format(longText, expanded = false)
        val expandedLongPreview = LongMessagePreviewFormatter.format(longText, expanded = true)

        assertFalse(shortPreview.isCollapsible)
        assertEquals(shortText, shortPreview.visibleText)
        assertTrue(longPreview.isCollapsible)
        assertTrue(longPreview.visibleText.endsWith("..."))
        assertEquals(longText, expandedLongPreview.visibleText)
    }
}
