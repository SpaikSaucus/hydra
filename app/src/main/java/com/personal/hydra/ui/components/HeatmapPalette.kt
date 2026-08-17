package com.personal.hydra.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Heat-map cell colours, built RELATIVE to the card they sit on.
 *
 * The ramp used to be alpha stacked over whatever was behind it. On the dark
 * theme the card is near-black (`#121817`), so the empty square (`surfaceVariant`
 * at 25%) and the lowest logged step (the accent at 22%) both composited down to
 * almost the card colour: a week with no water looked exactly like a week that
 * missed the goal. It also made the direction of the scale theme-dependent —
 * "the darker, the closer to your goal" is only true on the light theme.
 *
 * Blending from the card outwards fixes both: every step sits further from the
 * card than the one before it, whichever theme is on, so the legend can talk
 * about the colour getting STRONGER instead of darker.
 *
 * Pure and Android-free so `chart_ux.feature` can assert the steps really are
 * distinguishable, on both schemes, without rendering anything.
 */
object HeatmapPalette {

    /** Steps above "empty". Step 0 means the day has no record at all. */
    const val STEPS = 4

    /** How far the empty square is nudged off the card so it reads as a slot. */
    private const val EMPTY_BLEND = 0.16f

    fun stepOf(percent: Double?): Int = when {
        percent == null -> 0
        percent >= 1.0 -> 4
        percent >= 0.75 -> 3
        percent >= 0.5 -> 2
        else -> 1
    }

    /**
     * A neutral square meaning "nothing logged" — nudged towards white on a dark
     * card and towards black on a light one. Deliberately NOT a faint tint of the
     * accent: that is what made an empty week look like a low-intake week.
     */
    fun emptyCell(card: Color): Color = ChartTint.awayFromCard(card, EMPTY_BLEND)

    fun cell(step: Int, card: Color, accent: Color): Color {
        val empty = emptyCell(card)
        if (step <= 0) return empty
        return lerp(empty, accent, step.coerceAtMost(STEPS) / STEPS.toFloat())
    }

    /** The whole ramp, empty first. The legend and the grid must never diverge. */
    fun ramp(card: Color, accent: Color): List<Color> = (0..STEPS).map { cell(it, card, accent) }
}
