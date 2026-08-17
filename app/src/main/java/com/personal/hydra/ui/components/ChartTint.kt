package com.personal.hydra.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Fills that have to stay visible on BOTH themes.
 *
 * Stacking alpha over a card whose colour the caller doesn't know is what keeps
 * collapsing these: `surfaceVariant` at 25-30% is nearly the card itself on the
 * near-black dark card AND on the very light one. Blending away from the card
 * instead guarantees a visible shape either way, because the direction is chosen
 * from the card's own luminance.
 */
object ChartTint {

    /** Lighter on a dark card, darker on a light one, by [amount] (0..1). */
    fun awayFromCard(card: Color, amount: Float): Color =
        lerp(card, if (card.luminance() < 0.5f) Color.White else Color.Black, amount)
}
