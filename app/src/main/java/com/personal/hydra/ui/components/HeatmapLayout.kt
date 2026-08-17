package com.personal.hydra.ui.components

/**
 * Pure sizing math for [CalendarHeatmap]. Kept out of the composable (and free
 * of Android types) so the "do the squares actually fill the card?" question is
 * answerable by a unit test instead of by squinting at a screenshot.
 *
 * All values are in dp.
 */
object HeatmapLayout {

    const val GAP = 3f
    const val ROWS = 7

    /**
     * Square side that makes [columns] columns span exactly [availableWidth].
     * The canvas height is DERIVED from this (see [gridHeight]) — pinning the
     * height first is what used to cap the squares at ~65% of the card width.
     */
    fun cellSide(availableWidth: Float, columns: Int, gap: Float = GAP): Float {
        if (columns <= 0) return 0f
        return ((availableWidth - gap * (columns - 1)) / columns).coerceAtLeast(1f)
    }

    fun gridWidth(cellSide: Float, columns: Int, gap: Float = GAP): Float =
        if (columns <= 0) 0f else cellSide * columns + gap * (columns - 1)

    fun gridHeight(cellSide: Float, gap: Float = GAP): Float = cellSide * ROWS + gap * (ROWS - 1)
}
