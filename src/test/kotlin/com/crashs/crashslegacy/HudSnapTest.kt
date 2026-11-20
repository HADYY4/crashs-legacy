package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.hud.HudSnap

/**
 * Component-to-component snapping in the HUD editor: five alignments per axis, one 8px threshold,
 * nearest wins and the screen wins ties, with the 1% grid as the fallback.
 */
class HudSnapTest {

    private val screen = 1000f
    private val threshold = 8f

    private fun snap(desired: Float, box: Float, others: List<Pair<Float, Float>> = emptyList()) =
        HudSnap.snapAxis(desired, box, screen, others, threshold)

    @Test
    fun leftEdgesAlign() {
        val result = snap(desired = 303f, box = 200f, others = listOf(300f to 500f))
        assertEquals(300f, result.positionPx, 0.01f)
        assertEquals(300f, result.guidePx!!, 0.01f)
        assertEquals(HudSnap.Source.COMPONENT, result.source)
    }

    @Test
    fun rightEdgesAlign() {
        // Other box right edge = 500, dragged box is 150 wide -> left edge 350.
        val result = snap(desired = 347f, box = 150f, others = listOf(300f to 500f))
        assertEquals(350f, result.positionPx, 0.01f)
        assertEquals(500f, result.guidePx!!, 0.01f)
    }

    @Test
    fun centresAlign() {
        // Centre of the other box = 400, dragged box is 150 wide -> left edge 325.
        val result = snap(desired = 322f, box = 150f, others = listOf(300f to 500f))
        assertEquals(325f, result.positionPx, 0.01f)
        assertEquals(400f, result.guidePx!!, 0.01f)
    }

    @Test
    fun abuttingBothWays() {
        // My left edge on their right edge.
        val right = snap(desired = 502f, box = 150f, others = listOf(300f to 500f))
        assertEquals(500f, right.positionPx, 0.01f)
        assertEquals(500f, right.guidePx!!, 0.01f)

        // My right edge on their left edge: left edge = 300 - 150.
        val left = snap(desired = 148f, box = 150f, others = listOf(300f to 500f))
        assertEquals(150f, left.positionPx, 0.01f)
        assertEquals(300f, left.guidePx!!, 0.01f)
    }

    @Test
    fun nothingNearFallsBackToTheOnePercentGrid() {
        val result = snap(desired = 224f, box = 200f, others = listOf(300f to 500f))
        assertEquals(220f, result.positionPx, 0.01f)
        assertNull(result.guidePx)
        assertEquals(HudSnap.Source.GRID, result.source)
    }

    @Test
    fun theNearestCandidateWins() {
        val result = snap(
            desired = 604f,
            box = 200f,
            others = listOf(300f to 500f, 600f to 800f),
        )
        assertEquals(600f, result.positionPx, 0.01f)
        assertEquals(600f, result.guidePx!!, 0.01f)
    }

    @Test
    fun theScreenWinsTies() {
        // Screen candidate 0 and a component candidate at 8 are both 4px away.
        val result = snap(desired = 4f, box = 100f, others = listOf(8f to 200f))
        assertEquals(0f, result.positionPx, 0.01f)
        assertEquals(0f, result.guidePx!!, 0.01f)
        assertEquals(HudSnap.Source.SCREEN, result.source)
    }

    @Test
    fun screenCandidatesStillWorkWithoutOtherComponents() {
        val centre = snap(desired = 396f, box = 200f)
        assertEquals(400f, centre.positionPx, 0.01f)
        assertEquals(500f, centre.guidePx!!, 0.01f)
        assertEquals(HudSnap.Source.SCREEN, centre.source)

        val farEdge = snap(desired = 796f, box = 200f)
        assertEquals(800f, farEdge.positionPx, 0.01f)
    }

    @Test
    fun positionsStayOnScreen() {
        val beyond = snap(desired = 980f, box = 200f)
        assertEquals(800f, beyond.positionPx, 0.01f)
        val before = snap(desired = -60f, box = 200f)
        assertEquals(0f, before.positionPx, 0.01f)
    }

    @Test
    fun aZeroSizedScreenIsSafe() {
        val result = HudSnap.snapAxis(50f, 100f, 0f, emptyList(), threshold)
        assertEquals(50f, result.positionPx, 0.01f)
        assertNull(result.guidePx)
        assertEquals(HudSnap.Source.NONE, result.source)
    }

}
