package com.crashs.crashslegacy.hud

/**
 * Screen placement math shared by the HUDs and the HUD editor.
 *
 * Percentages are `0..100` of the scaled screen. With the right anchor the percentage is measured
 * from the right edge, which mirrors the resulting rectangle. Keeping this in one place means the
 * editor drags exactly what the HUD renders, and it is pure Kotlin so it can be unit tested.
 */
object HudPlacement {

    data class Bounds(val x: Float, val y: Float, val width: Float, val height: Float) {
        val right: Float get() = x + width
        val bottom: Float get() = y + height

        fun contains(px: Float, py: Float): Boolean = px >= x && px <= right && py >= y && py <= bottom

        /**
         * Grows the rectangle to at least [minWidth]×[minHeight], keeping the top-left corner.
         * The editor uses it as the drag hit area: a HUD without entries is only ~94×30, too small
         * to grab, while the visual box must stay accurate.
         */
        fun expandedTo(minWidth: Float, minHeight: Float): Bounds =
            Bounds(x, y, maxOf(width, minWidth), maxOf(height, minHeight))
    }

    fun bounds(
        screenWidth: Float,
        screenHeight: Float,
        contentWidth: Float,
        contentHeight: Float,
        scale: Float,
        percentX: Float,
        percentY: Float,
        right: Boolean,
        bottomAnchor: Boolean = false,
    ): Bounds {
        val width = contentWidth * scale
        val height = contentHeight * scale
        val px = percentX.coerceIn(0f, 100f)
        val py = percentY.coerceIn(0f, 100f)
        val x = if (right) {
            screenWidth - width - screenWidth * px / 100f
        } else {
            screenWidth * px / 100f
        }
        val y = if (bottomAnchor) screenHeight * py / 100f - height else screenHeight * py / 100f
        return Bounds(x, y, width, height)
    }

    /** Inverse of [bounds] X mapping: turns a desired left edge into a percentage. */
    fun percentX(screenWidth: Float, width: Float, desiredX: Float, right: Boolean): Float {
        if (screenWidth <= 0f) return 0f
        val percent = if (right) {
            (screenWidth - width - desiredX) / screenWidth * 100f
        } else {
            desiredX / screenWidth * 100f
        }
        return percent.coerceIn(0f, 100f)
    }

    /** Inverse of [bounds] Y mapping: turns a desired top edge into a percentage. */
    fun percentY(
        screenHeight: Float,
        desiredY: Float,
        contentHeight: Float = 0f,
        bottomAnchor: Boolean = false,
    ): Float {
        if (screenHeight <= 0f) return 0f
        val anchor = if (bottomAnchor) desiredY + contentHeight else desiredY
        return (anchor / screenHeight * 100f).coerceIn(0f, 100f)
    }

}
