package com.look.chat.logic

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object TimeFormat {

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun time(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (epochMs <= 0) return ""
        return TIME.format(Instant.ofEpochMilli(epochMs).atZone(zone))
    }
}
