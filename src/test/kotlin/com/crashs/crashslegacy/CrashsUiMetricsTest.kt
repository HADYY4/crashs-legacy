package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.ui.CrashsUiMetrics

/**
 * Pure math behind the surface. These numbers used to live inline in the screen, which is how a
 * 50px height error and an overlapping scrollbar survived several rounds of manual testing.
 */
class CrashsUiMetricsTest {

    @Test
    fun smallDisplaysFallBackToTheNoticeInsteadOfTheFullLayout() {
        assertFalse(CrashsUiMetrics.canRenderFull(479f, 400f))
        assertFalse(CrashsUiMetrics.canRenderFull(600f, 359f))
        assertTrue(CrashsUiMetrics.canRenderFull(CrashsUiMetrics.MIN_UI_WIDTH, CrashsUiMetrics.MIN_UI_HEIGHT))
        assertTrue(CrashsUiMetrics.canRenderFull(1920f, 1080f))
    }

    @Test
    fun scaleIsNormalisedToOneAtTheDesignSize() {
        assertEquals(1f, CrashsUiMetrics.scale(CrashsUiMetrics.BASE_FONT_SIZE), 1e-6f)
        assertEquals(2f, CrashsUiMetrics.scale(CrashsUiMetrics.BASE_FONT_SIZE * 2f), 1e-6f)
    }

    @Test
    fun pillContentWidthIncludesIconGapAndArrow() {
        val label = 100f
        val withIcon = CrashsUiMetrics.pillContentWidth(label, hasIcon = true, hasArrow = false, fontSize = 17f)
        assertEquals(label + 18f + 12f, withIcon, 1e-6f)
        val withArrow = CrashsUiMetrics.pillContentWidth(label, hasIcon = true, hasArrow = true, fontSize = 17f)
        assertEquals(label + 18f + 12f + 22f, withArrow, 1e-6f)
        val labelOnly = CrashsUiMetrics.pillContentWidth(label, hasIcon = false, hasArrow = false, fontSize = 17f)
        assertEquals(label, labelOnly, 1e-6f)
    }

    @Test
    fun bodyHeightAddsTheBottomMargin() {
        assertEquals(285f, CrashsUiMetrics.requiredBodyHeight(277f), 1e-6f)
        assertTrue(CrashsUiMetrics.requiredBodyHeight(0f) >= 1f)
    }

    @Test
    fun cardColumnsNeverOverflowTheViewport() {
        val contentWidth = 810f
        val inset = 13f
        val pad = 16f
        val gap = 16f
        val cardWidth = CrashsUiMetrics.cardWidth(contentWidth, inset, pad, gap, 2)
        val rightEdge = pad + cardWidth * 2 + gap
        assertTrue(
            rightEdge <= contentWidth - inset + 0.01f,
            "cards end at $rightEdge but the viewport only offers ${contentWidth - inset}",
        )
    }

    @Test
    fun cardWidthNeverGoesBelowTheUsableMinimum() {
        assertTrue(CrashsUiMetrics.cardWidth(200f, 10f, 16f, 16f, 2) >= 180f)
    }
}
