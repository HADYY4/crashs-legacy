package com.crashs.crashslegacy.hud

/**
 * Drag state machine for the HUD editor, kept free of ImGui so it can be unit tested.
 *
 * Percentages stay in the config domain (0..100, mirrored by the right anchor), while the drag
 * itself works in pixels: grabbing remembers where inside the box the mouse landed so the HUD does
 * not jump on the first move.
 */
class HudDragState(
    private val thresholdPx: Float = 8f,
    private val gridPercent: Float = 1f,
) {
    var dragging: Boolean = false
        private set

    private var grabOffsetX = 0f
    private var grabOffsetY = 0f

    /**
     * @param bounds the visual rectangle the drag moves.
     * @param hit the area that accepts the press; defaults to [bounds] but the editor passes a
     *   larger minimum box so a small HUD can still be grabbed.
     * @return true when the press landed inside [hit] and a drag started.
     */
    fun begin(
        mouseX: Float,
        mouseY: Float,
        bounds: HudPlacement.Bounds,
        hit: HudPlacement.Bounds = bounds,
    ): Boolean {
        if (!hit.contains(mouseX, mouseY)) return false
        dragging = true
        grabOffsetX = mouseX - bounds.x
        grabOffsetY = mouseY - bounds.y
        return true
    }

    /** Snapped percentages plus the guide lines to draw (scaled screen pixels, null = no guide). */
    data class DragResult(
        val percentX: Float,
        val percentY: Float,
        val guideX: Float?,
        val guideY: Float?,
        val guideXSource: HudSnap.Source,
        val guideYSource: HudSnap.Source,
    )

    /**
     * Converts the current mouse position into snapped percentages, or null when not dragging.
     * [boxWidth]/[boxHeight] are the on-screen HUD size (content size × scale); [others] are the
     * other enabled components, used for alignment snapping.
     */
    fun drag(
        mouseX: Float,
        mouseY: Float,
        screenWidth: Float,
        screenHeight: Float,
        boxWidth: Float,
        boxHeight: Float,
        right: Boolean,
        bottomAnchor: Boolean = false,
        others: List<HudPlacement.Bounds> = emptyList(),
    ): DragResult? {
        if (!dragging) return null
        val desiredX = mouseX - grabOffsetX
        val desiredY = mouseY - grabOffsetY
        val snapX = HudSnap.snapAxis(
            desiredPx = desiredX,
            boxSize = boxWidth,
            screenSize = screenWidth,
            others = others.map { it.x to it.right },
            thresholdPx = thresholdPx,
            gridPercent = gridPercent,
        )
        val snapY = HudSnap.snapAxis(
            desiredPx = desiredY,
            boxSize = boxHeight,
            screenSize = screenHeight,
            others = others.map { it.y to it.bottom },
            thresholdPx = thresholdPx,
            gridPercent = gridPercent,
        )
        return DragResult(
            percentX = HudPlacement.percentX(screenWidth, boxWidth, snapX.positionPx, right),
            percentY = HudPlacement.percentY(screenHeight, snapY.positionPx, boxHeight, bottomAnchor),
            guideX = snapX.guidePx,
            guideY = snapY.guidePx,
            guideXSource = snapX.source,
            guideYSource = snapY.source,
        )
    }

    fun end() {
        dragging = false
    }
}
