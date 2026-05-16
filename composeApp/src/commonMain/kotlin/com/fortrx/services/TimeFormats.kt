package com.fortrx.services

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object TimeFormats {
    fun parseInstantOrNull(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().replace(' ', 'T')
        return runCatching { Instant.parse(normalized) }.getOrElse {
            val afterT = normalized.substringAfter('T', "")
            if (afterT.isEmpty()) return null
            val hasOffset = afterT.contains("Z") || afterT.contains("+") || afterT.drop(1).contains("-")
            if (!hasOffset) {
                runCatching { Instant.parse("${normalized}Z") }.getOrNull()
            } else {
                null
            }
        }
    }

    fun sortEpochMillis(raw: String?): Long =
        parseInstantOrNull(raw)?.toEpochMilliseconds() ?: Long.MIN_VALUE

    fun formatChatTime(raw: String?): String {
        val instant = parseInstantOrNull(raw) ?: return raw?.take(16)?.replace("T", " ").orEmpty()
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hours = local.hour
        val minutes = local.minute.toString().padStart(2, '0')
        val suffix = if (hours >= 12) "PM" else "AM"
        val hour12 = if (hours % 12 == 0) 12 else hours % 12
        return "$hour12:$minutes $suffix"
    }

    fun formatListTime(raw: String?): String {
        val instant = parseInstantOrNull(raw) ?: return raw?.substringAfter("T")?.take(5).orEmpty()
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    fun formatListDate(raw: String?): String {
        val instant = parseInstantOrNull(raw) ?: return raw?.take(10).orEmpty()
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val month = local.monthNumber.toString().padStart(2, '0')
        val day = local.dayOfMonth.toString().padStart(2, '0')
        return "${local.year}-$month-$day"
    }
}
