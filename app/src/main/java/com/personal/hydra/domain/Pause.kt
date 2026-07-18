package com.personal.hydra.domain

import com.personal.hydra.domain.model.PausePeriod
import com.personal.hydra.domain.model.Ranges
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure policy for the "pause tracking for N days" feature. A pause silences
 * reminders and makes its days NEUTRAL for streaks (they neither count nor
 * break a run; a paused day whose goal is still met counts normally). Elapsed
 * periods are kept in the config so past paused days stay neutral forever.
 * All decisions take `today` injected — no clock in here.
 */
object PauseManager {

    fun isPaused(pauses: List<PausePeriod>, day: LocalDate): Boolean =
        pauses.any { day in it }

    fun activePause(pauses: List<PausePeriod>, today: LocalDate): PausePeriod? =
        pauses.firstOrNull { today in it }

    /** Days left INCLUDING today; 0 when not paused. */
    fun remainingDays(pauses: List<PausePeriod>, today: LocalDate): Int {
        val active = activePause(pauses, today) ?: return 0
        return (ChronoUnit.DAYS.between(today, LocalDate.parse(active.endDay)) + 1).toInt()
    }

    /**
     * Starts a pause of [days] days beginning today (today counts as day 1).
     * Clamps to 1..[Ranges.PAUSE_DAYS_MAX]; any pause still running is closed
     * first so periods never overlap.
     */
    fun startPause(pauses: List<PausePeriod>, today: LocalDate, days: Int): List<PausePeriod> {
        val d = days.coerceIn(1, Ranges.PAUSE_DAYS_MAX)
        val end = today.plusDays(d - 1L)
        return resumeEarly(pauses, today) + PausePeriod(today.toString(), end.toString())
    }

    /**
     * Ends the active pause as of today: days already elapsed stay recorded
     * (still neutral for streaks); today onward resumes tracking.
     */
    fun resumeEarly(pauses: List<PausePeriod>, today: LocalDate): List<PausePeriod> {
        val t = today.toString()
        return pauses.mapNotNull { p ->
            when {
                p.endDay < t -> p // already finished — keep for streak history
                p.startDay >= t -> null // starts today or later — drop entirely
                else -> p.copy(endDay = today.minusDays(1).toString())
            }
        }
    }
}
