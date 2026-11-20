package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.ui.CrashsWindowGeometry

/**
 * The ClickGUI window must survive resolution changes: a saved size/position from a bigger monitor
 * has to be clamped instead of rendering half off-screen.
 */
class CrashsWindowGeometryTest {

    @Test
    fun aSavedSizeIsClampedIntoTheDisplay() {
        val (w, h) = CrashsWindowGeometry.clampSize(
            wantedWidth = 1040f,
            wantedHeight = 760f,
            minWidth = 900f,
            minHeight = 600f,
            maxWidth = 1000f,
            maxHeight = 700f,
        )
        assertEquals(1000f, w, 0.01f)
        assertEquals(700f, h, 0.01f)
    }

    @Test
    fun aSizeBelowTheMinimumIsRaised() {
        val (w, h) = CrashsWindowGeometry.clampSize(400f, 300f, 900f, 600f, 1200f, 800f)
        assertEquals(900f, w, 0.01f)
        assertEquals(600f, h, 0.01f)
    }

    @Test
    fun aSavedPositionOffScreenIsPulledBack() {
        val (x, y) = CrashsWindowGeometry.clampPosition(
            x = 1500f,
            y = 900f,
            width = 1000f,
            height = 700f,
            screenWidth = 1280f,
            screenHeight = 720f,
        )
        assertEquals(280f, x, 0.01f)
        assertEquals(20f, y, 0.01f)
    }

    @Test
    fun aNegativePositionIsMovedToTheOrigin() {
        val (x, y) = CrashsWindowGeometry.clampPosition(-50f, -20f, 1000f, 700f, 1280f, 720f)
        assertEquals(0f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }

    @Test
    fun insideCheckAcceptsFittingRectanglesAndRejectsOverflow() {
        assertTrue(CrashsWindowGeometry.isInside(100f, 100f, 1040f, 760f, 1280f, 900f))
        assertFalse(CrashsWindowGeometry.isInside(400f, 100f, 1040f, 760f, 1280f, 900f))
        assertFalse(CrashsWindowGeometry.isInside(100f, 100f, 1040f, 860f, 1280f, 900f))
    }
}
