package com.personal.hydra.steps

import java.time.LocalDate

/**
 * Shared fixed dates for the Cucumber suite. Pinning dates (never LocalDate.now())
 * is intentional — it keeps season/goal math deterministic regardless of when the
 * tests run. Centralized here so the "northern winter" choice lives in one place.
 */
object TestDates {
    /** A northern-hemisphere WINTER date: the default config's country resolves to
     *  NORTH, so no automatic heat-mode inflation of the goal. */
    val NORTHERN_WINTER: LocalDate = LocalDate.of(2026, 1, 15)

    /** Generic reference "today" used by streak/integration scenarios. */
    val REFERENCE: LocalDate = LocalDate.of(2026, 6, 14)
}
