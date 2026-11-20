package com.crashs.crashslegacy.ui

/**
 * Window geometry for the ClickGUI window: keep it inside the display when the resolution,
 * monitor or fullscreen state changes, and never persist a collapsed size.
 *
 * Pure Kotlin so the clamping rules are unit tested instead of only "open the game and drag it".
 */
object CrashsWindowGeometry {

    /** Clamps a wanted window size into `[min, max]`. */
    fun clampSize(
        wantedWidth: Float,
        wantedHeight: Float,
        minWidth: Float,
        minHeight: Float,
        maxWidth: Float,
        maxHeight: Float,
    ): Pair<Float, Float> {
        val w = wantedWidth.coerceIn(minWidth, maxWidth.coerceAtLeast(minWidth))
        val h = wantedHeight.coerceIn(minHeight, maxHeight.coerceAtLeast(minHeight))
        return w to h
    }

    /** Clamps a window position so the whole rectangle stays on screen. */
    fun clampPosition(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        screenWidth: Float,
        screenHeight: Float,
    ): Pair<Float, Float> {
        val maxX = (screenWidth - width).coerceAtLeast(0f)
        val maxY = (screenHeight - height).coerceAtLeast(0f)
        return x.coerceIn(0f, maxX) to y.coerceIn(0f, maxY)
    }

    /** True when the rectangle is already fully inside the display. */
    fun isInside(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        screenWidth: Float,
        screenHeight: Float,
    ): Boolean =
        x >= 0f && y >= 0f && width <= screenWidth && height <= screenHeight &&
            x + width <= screenWidth && y + height <= screenHeight
}
