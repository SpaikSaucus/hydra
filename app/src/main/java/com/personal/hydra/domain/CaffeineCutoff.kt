package com.personal.hydra.domain

/**
 * When caffeine starts eating into sleep, as a window that ENDS at the user's
 * configured bedtime.
 *
 * Expressed in hours before bedtime rather than as a wall-clock hour, so moving
 * the sleep time moves the warning with it instead of leaving a stale "after
 * 14:00" that only made sense for one schedule.
 *
 * [HOURS_BEFORE_SLEEP] = 9 rounds the 8.8 h that Gardiner et al. (2023, Sleep
 * Medicine Reviews, meta-analysis of 24 studies) identify as the point where a
 * standard 107 mg coffee stops measurably reducing total sleep time. It sits at
 * the conservative end of the evidence on purpose: Drake et al. (2013) put the
 * sleep-hygiene MINIMUM at 6 h and still measured over an hour of lost sleep
 * there, and a 2024 crossover trial found 400 mg fragmenting sleep 8 h out.
 *
 * Dose is what spreads those numbers, which is why the copy states a floor and
 * says earlier is better: tea carries roughly half a coffee's caffeine (the same
 * molecule — "theine" is only a historical name) while a long mate session can
 * reach ~260 mg. Advisory only: nothing here feeds the goal, the streaks or the
 * reminders.
 */
object CaffeineCutoff {

    const val HOURS_BEFORE_SLEEP = 9

    /** Minute of day the warning starts, i.e. bedtime minus the window. */
    fun warningStartMinute(sleepMinuteOfDay: Int): Int =
        Math.floorMod(sleepMinuteOfDay - HOURS_BEFORE_SLEEP * 60, 1440)

    /**
     * Whether [nowMinuteOfDay] falls inside the window. The window wraps midnight
     * whenever bedtime is past 00:00 — someone who sleeps at 01:00 should be
     * warned from 16:00 through to that bedtime, across the calendar boundary.
     */
    fun shouldWarn(nowMinuteOfDay: Int, sleepMinuteOfDay: Int): Boolean {
        val start = warningStartMinute(sleepMinuteOfDay)
        return Math.floorMod(nowMinuteOfDay - start, 1440) < HOURS_BEFORE_SLEEP * 60
    }
}
