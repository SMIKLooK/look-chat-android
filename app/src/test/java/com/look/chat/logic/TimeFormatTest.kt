package com.look.chat.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimeFormatTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `formats epoch millis as HHmm in given zone`() {
        val noon = Instant.parse("2026-01-15T14:05:00Z").toEpochMilli()
        assertEquals("14:05", TimeFormat.time(noon, utc))
    }

    @Test
    fun `pads minutes with zero`() {
        val time = Instant.parse("2026-01-15T09:07:00Z").toEpochMilli()
        assertEquals("09:07", TimeFormat.time(time, utc))
    }

    @Test
    fun `zero or negative time gives empty string`() {
        assertEquals("", TimeFormat.time(0L, utc))
        assertEquals("", TimeFormat.time(-1L, utc))
    }
}
