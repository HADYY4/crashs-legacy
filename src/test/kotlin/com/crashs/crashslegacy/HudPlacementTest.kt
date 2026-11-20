package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.hud.HudPlacement

/**
 * Placement math shared by the HUD and its editor. A wrong inverse here would make the drag feel
 * offset or make the right anchor jump, so the round trip and the snapping rules are pinned down
 * without Minecraft or ImGui.
 */
class HudPlacementTest {

    private val screenW = 1000f
    private val screenH = 500f
    private val boxW = 200f
    private val boxH = 100f

    @Test
    fun leftAnchorPlacesTheBoxAtThePercentage() {
        val bounds = HudPlacement.bounds(screenW, screenH, boxW, boxH, 1f, 10f, 20f, right = false)
        assertEquals(100f, bounds.x, 0.01f)
        assertEquals(100f, bounds.y, 0.01f)
        assertEquals(boxW, bounds.width, 0.01f)
        assertEquals(boxH, bounds.height, 0.01f)
    }

    @Test
    fun rightAnchorMeasuresFromTheRightEdge() {
        val bounds = HudPlacement.bounds(screenW, screenH, boxW, boxH, 1f, 10f, 20f, right = true)
        assertEquals(700f, bounds.x, 0.01f)
        assertEquals(900f, bounds.right, 0.01f)
    }

    @Test
    fun scaleGrowsTheBoxButKeepsThePercentage() {
        val bounds = HudPlacement.bounds(screenW, screenH, boxW, boxH, 1.5f, 10f, 20f, right = false)
        assertEquals(100f, bounds.x, 0.01f)
        assertEquals(300f, bounds.width, 0.01f)
        assertEquals(150f, bounds.height, 0.01f)
    }

    @Test
    fun percentagesRoundTripForBothAnchors() {
        assertEquals(10f, HudPlacement.percentX(screenW, boxW, 100f, right = false), 0.01f)
        assertEquals(10f, HudPlacement.percentX(screenW, boxW, 700f, right = true), 0.01f)
        assertEquals(20f, HudPlacement.percentY(screenH, 100f), 0.01f)
    }

    @Test
    fun aBottomAnchoredBoxSitsAboveTheAnchor() {
        val box = HudPlacement.bounds(
            screenWidth = screenW,
            screenHeight = screenH,
            contentWidth = boxW,
            contentHeight = boxH,
            scale = 1f,
            percentX = 10f,
            percentY = 50f,
            right = false,
            bottomAnchor = true,
        )
        assertEquals(150f, box.y, 0.01f)
        assertEquals(250f, box.bottom, 0.01f)

        val percent = HudPlacement.percentY(screenH, box.y, boxH, bottomAnchor = true)
        assertEquals(50f, percent, 0.01f)
    }

    @Test
    fun boundsHitTestCoversTheWholeRectangle() {
        val bounds = HudPlacement.bounds(screenW, screenH, boxW, boxH, 1f, 10f, 20f, right = false)
        assertTrue(bounds.contains(100f, 100f))
        assertTrue(bounds.contains(300f, 200f))
        assertFalse(bounds.contains(99f, 150f))
        assertFalse(bounds.contains(301f, 150f))
    }

    @Test
    fun zeroScreenSizeDoesNotDivideByZero() {
        assertEquals(0f, HudPlacement.percentX(0f, boxW, 100f, right = false), 0.01f)
        assertEquals(0f, HudPlacement.percentY(0f, 100f), 0.01f)
    }
}
