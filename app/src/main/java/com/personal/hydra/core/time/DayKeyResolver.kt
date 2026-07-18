package com.personal.hydra.core.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Resolves which "hydration day" an instant belongs to, using the device time
 * zone and the wake hour as the cut (NOT midnight): an intake before wake hour
 * counts for the previous day; at/after wake hour it opens the new day.
 */
class DayKeyResolver(private val clock: Clock = Clock.systemDefaultZone()) {

    fun dayKeyFor(epochMillis: Long, wakeHour: LocalTime, zone: ZoneId = clock.zone): String {
        val ldt = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
        val date = if (ldt.toLocalTime() < wakeHour) ldt.toLocalDate().minusDays(1) else ldt.toLocalDate()
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    fun todayKey(wakeHour: LocalTime, zone: ZoneId = clock.zone): String =
        dayKeyFor(Instant.now(clock).toEpochMilli(), wakeHour, zone)

    /** [startEpoch, endEpoch) in millis spanning the given hydration day. */
    fun boundsOf(dayKey: String, wakeHour: LocalTime, zone: ZoneId = clock.zone): LongRange {
        val date = LocalDate.parse(dayKey)
        val start = date.atTime(wakeHour).atZone(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atTime(wakeHour).atZone(zone).toInstant().toEpochMilli()
        return start until end
    }
}
