package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.hud.HudDragState
import com.crashs.crashslegacy.hud.HudPlacement

/** Drag state machine behind the HUD editor: grab offset, mirroring, snapping and clamping. */
class HudDragStateTest {

    private val screenW = 1000f
    private val screenH = 500f
    private val boxW = 200f
    private val boxH = 100f

    private fun bounds(percentX: Float, right: Boolean = false) =
        HudPlacement.bounds(screenW, screenH, boxW, boxH, 1f, percentX, 20f, right)

    @Test
    fun pressingOutsideTheBoxDoesNotStartADrag() {
        val state = HudDragState()
        assertFalse(state.begin(10f, 10f, bounds(10f)))
        assertFalse(state.dragging)
        assertNull(state.drag(20f, 20f, screenW, screenH, boxW, boxH, right = false))
    }

    @Test
    fun grabbingInsideKeepsTheOffsetSoTheBoxDoesNotJump() {
        val state = HudDragState()
        assertTrue(state.begin(150f, 150f, bounds(10f)))
        val moved = state.drag(150f, 150f, screenW, screenH, boxW, boxH, right = false)
        assertNotNull(moved)
        assertEquals(10f, moved!!.percentX, 0.01f)
        assertEquals(20f, moved.percentY, 0.01f)
    }

    @Test
    fun draggingRightMovesTheLeftAnchoredBox() {
        val state = HudDragState()
        state.begin(150f, 150f, bounds(10f))
        val moved = state.drag(650f, 150f, screenW, screenH, boxW, boxH, right = false)!!
        assertEquals(60f, moved.percentX, 0.01f)
    }

    @Test
    fun rightAnchorMirrorsTheDrag() {
        val state = HudDragState()
        // percent 10 with the right anchor puts the box at x = 700.
        assertTrue(state.begin(750f, 150f, bounds(10f, right = true)))
        val moved = state.drag(750f, 150f, screenW, screenH, boxW, boxH, right = true)!!
        assertEquals(10f, moved.percentX, 0.01f)
    }

    @Test
    fun draggingPastTheEdgesClamps() {
        val state = HudDragState()
        state.begin(150f, 150f, bounds(10f))
        val topLeft = state.drag(-400f, -400f, screenW, screenH, boxW, boxH, right = false)!!
        assertEquals(0f, topLeft.percentX, 0.01f)
        assertEquals(0f, topLeft.percentY, 0.01f)
        val bottomRight = state.drag(2000f, 2000f, screenW, screenH, boxW, boxH, right = false)!!
        assertEquals(80f, bottomRight.percentX, 0.01f)
        assertEquals(80f, bottomRight.percentY, 0.01f)
    }

    @Test
    fun gridSnappingQuantisesToWholePercent() {
        val state = HudDragState()
        state.begin(150f, 150f, bounds(10f))
        // 100 -> 10% ; move by 123px -> desired 223px -> 22.3% -> 22%
        val moved = state.drag(273f, 150f, screenW, screenH, boxW, boxH, right = false)!!
        assertEquals(22f, moved.percentX, 0.01f)
    }

    @Test
    fun aLargerHitAreaLetsSmallHudsBeGrabbed() {
        val visual = HudPlacement.bounds(screenW, screenH, 94f, 30f, 1f, 10f, 20f, right = false)
        val hit = visual.expandedTo(220f, 88f)
        val state = HudDragState()
        // Just outside the tiny HUD but inside the minimum drag area.
        assertTrue(state.begin(visual.x + 150f, visual.y + 60f, visual, hit))
        assertTrue(state.dragging)
        val moved = state.drag(visual.x + 150f, visual.y + 60f, screenW, screenH, visual.width, visual.height, right = false)!!
        assertEquals(10f, moved.percentX, 0.01f)
    }

    @Test
    fun draggingSnapsToAnotherComponent() {
        val state = HudDragState()
        val visual = bounds(10f)
        assertTrue(state.begin(150f, 150f, visual))
        // Another HUD occupies x 300..500, y 200..300: drag so the left edge lands 4px away.
        val other = HudPlacement.Bounds(300f, 200f, 200f, 100f)
        val moved = state.drag(254f, 150f, screenW, screenH, boxW, boxH, right = false, others = listOf(other))!!
        // Grab offset was 50px, so desired left = 204 -> nearest candidate is `start - boxW`? No:
        // with box 200 wide, candidate "my right at their left" is 100, left-left is 300.
        // 204 is outside 8px of both, so it falls back to the 1% grid (200).
        assertEquals(20f, moved.percentX, 0.01f)

        // Now aim at the other component's left edge (300): 4px away snaps exactly onto it.
        val snapped = state.drag(354f, 150f, screenW, screenH, boxW, boxH, right = false, others = listOf(other))!!
        assertEquals(30f, snapped.percentX, 0.01f)
        assertEquals(300f, snapped.guideX!!, 0.01f)

        // Same on Y, aiming at the other component's bottom edge (300) which is unambiguous:
        // 300px on a 500px screen is 60%.
        val vertical = state.drag(150f, 354f, screenW, screenH, boxW, boxH, right = false, others = listOf(other))!!
        assertEquals(60f, vertical.percentY, 0.01f)
        assertEquals(300f, vertical.guideY!!, 0.01f)
    }

    @Test
    fun endingTheDragStopsUpdates() {
        val state = HudDragState()
        state.begin(150f, 150f, bounds(10f))
        state.end()
        assertFalse(state.dragging)
        assertNull(state.drag(300f, 300f, screenW, screenH, boxW, boxH, right = false))
    }
}
