package com.personal.hydra.core.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Resolves which "hydration day" an instant belongs to, using the device time
 * zone. The day is the CALENDAR day: it rolls over at 00:00, so anything drunk
 * after midnight already counts for the new day.
 *
 * The wake/sleep times deliberately play NO part here — they only bound the
 * reminder window (see `ReminderEvaluator`). Using the wake hour as the cut used
 * to file early-morning water under the previous day, which is wrong.
 */
class DayKeyResolver(private val clock: Clock = Clock.systemDefaultZone()) {

    fun dayKeyFor(epochMillis: Long, zone: ZoneId = clock.zone): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun todayKey(zone: ZoneId = clock.zone): String = dayKeyFor(Instant.now(clock).toEpochMilli(), zone)

    /**
     * Milliseconds from now until the next calendar midnight in [zone] — how long
     * a live "today" subscription may sleep before it must re-key itself.
     *
     * Derived from the zone's own `atStartOfDay`, never from a naive +24 h: a DST
     * night is 23 or 25 hours long, and in the zones that switch AT midnight the
     * next 00:00 does not exist at all.
     */
    fun millisUntilNextDay(zone: ZoneId = clock.zone): Long {
        val now = Instant.now(clock)
        val nextStart = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        // Never zero: a clock nudged backwards must not spin the ticker.
        return (nextStart.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(1_000L)
    }

    /** [startEpoch, endEpoch) in millis spanning the given hydration day (midnight to midnight). */
    fun boundsOf(dayKey: String, zone: ZoneId = clock.zone): LongRange {
        val date = LocalDate.parse(dayKey)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start until end
    }
}
