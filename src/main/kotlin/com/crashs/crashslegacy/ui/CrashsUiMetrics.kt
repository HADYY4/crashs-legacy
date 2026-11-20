package com.crashs.crashslegacy.ui

/**
 * Pure geometry for the Crashs Legacy surface.
 *
 * Everything here is text-relative and free of ImGui and Minecraft types, so the numbers can be
 * asserted by unit tests instead of "open the game and look". The screen code must use these
 * helpers rather than re-deriving padding constants, otherwise the layout and its tests drift
 * apart again.
 */
object CrashsUiMetrics {

    /** The original design size; every padding constant below is expressed at this size. */
    const val BASE_FONT_SIZE = 17f

    /** Below this the full ClickGUI layout cannot be drawn safely; a fallback notice is shown. */
    const val MIN_UI_WIDTH = 480f
    const val MIN_UI_HEIGHT = 360f

    const val ICON_BOX = 18f
    const val ICON_GAP = 12f
    const val ARROW_RESERVE = 22f

    /** Smallest acceptable gap between the centred capsule content and the capsule edge. */
    const val MIN_PILL_PADDING = 8f

    /** Bottom breathing room inside a card body child window. */
    const val CARD_BODY_MARGIN = 8f

    fun scale(fontSize: Float): Float = fontSize / BASE_FONT_SIZE

    /**
     * Whether the full layout fits. The rail (190px), top bar and two-column cards need real space;
     * below this the surface draws a "window too small" fallback instead of risking native ImGui
     * asserts from negative content regions.
     */
    fun canRenderFull(displayWidth: Float, displayHeight: Float): Boolean =
        displayWidth >= MIN_UI_WIDTH && displayHeight >= MIN_UI_HEIGHT

    fun iconBox(fontSize: Float): Float = ICON_BOX * scale(fontSize)

    fun iconGap(fontSize: Float): Float = ICON_GAP * scale(fontSize)

    fun arrowReserve(fontSize: Float): Float = ARROW_RESERVE * scale(fontSize)

    /**
     * Width of the centred content group of a navigation capsule: optional icon plus gap, the
     * label itself, and the optional expand arrow. The capsule must be at least this wide plus
     * [MIN_PILL_PADDING] on both sides.
     */
    fun pillContentWidth(
        labelWidth: Float,
        hasIcon: Boolean,
        hasArrow: Boolean,
        fontSize: Float,
    ): Float {
        val icon = if (hasIcon) iconBox(fontSize) + iconGap(fontSize) else 0f
        val arrow = if (hasArrow) arrowReserve(fontSize) else 0f
        return icon + labelWidth + arrow
    }

    /**
     * Card width for a masonry column layout. [viewportInset] must cover the scrollbar of the
     * scrolling viewport, otherwise the right column ends up hidden underneath it.
     */
    fun cardWidth(
        contentWidth: Float,
        viewportInset: Float,
        pad: Float,
        gap: Float,
        columns: Int,
    ): Float {
        val safeColumns = columns.coerceAtLeast(1)
        val usable = (contentWidth - viewportInset - pad * 2f).coerceAtLeast(180f)
        return ((usable - gap * (safeColumns - 1)) / safeColumns).coerceAtLeast(180f)
    }

    /**
     * Height a card body child window needs. [contentBottom] is the cursor position after the
     * content, measured from the top edge of the child, so it already includes the description
     * block. Skipping that offset is what clipped the bottom of every expanded card.
     */
    fun requiredBodyHeight(contentBottom: Float, bottomMargin: Float = CARD_BODY_MARGIN): Float =
        (contentBottom + bottomMargin).coerceAtLeast(1f)
}
