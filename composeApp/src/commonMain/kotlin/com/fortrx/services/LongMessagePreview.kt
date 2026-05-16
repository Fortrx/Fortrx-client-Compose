package com.fortrx.services

data class LongMessagePreview(
    val visibleText: String,
    val isCollapsible: Boolean,
)

object LongMessagePreviewFormatter {
    fun format(
        text: String,
        expanded: Boolean,
        collapseThresholdWords: Int = 700,
        previewWords: Int = 220,
    ): LongMessagePreview {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val isLong = words.size > collapseThresholdWords
        val visibleText = if (!isLong || expanded) {
            text
        } else {
            words.take(previewWords).joinToString(" ") + "..."
        }
        return LongMessagePreview(
            visibleText = visibleText,
            isCollapsible = isLong,
        )
    }
}
