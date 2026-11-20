package com.crashs.crashslegacy

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.ui.CrashsImGuiLayout
import com.crashs.crashslegacy.ui.theme.CrashsImGuiTheme

/**
 * ClickGUI scale: the layout metrics follow `uiScale`, and the theme's font size is the design size
 * multiplied by it. The scale is clamped to 0.5..2.0 by the theme and the UI state.
 */
class CrashsUiScaleTest {

    @AfterEach
    fun resetScale() {
        CrashsImGuiLayout.uiScale = 1f
        CrashsImGuiTheme.applyUiScale(1f)
    }

    @Test
    fun layoutMetricsFollowTheScale() {
        CrashsImGuiLayout.uiScale = 2f
        assertEquals(2080f, CrashsImGuiLayout.windowWidthPx(), 0.01f)
        assertEquals(1520f, CrashsImGuiLayout.windowHeightPx(), 0.01f)
        assertEquals(380f, CrashsImGuiLayout.rail(), 0.01f)
        assertEquals(32f, CrashsImGuiLayout.cardGapPx(), 0.01f)
        assertEquals(164f, CrashsImGuiLayout.cardHeaderHeightPx(), 0.01f)
    }

    @Test
    fun halfScaleShrinksTheLayout() {
        CrashsImGuiLayout.uiScale = 0.5f
        assertEquals(520f, CrashsImGuiLayout.windowWidthPx(), 0.01f)
        assertEquals(95f, CrashsImGuiLayout.rail(), 0.01f)
        assertEquals(27f, CrashsImGuiLayout.pillHeight(), 0.01f)
    }

    @Test
    fun theThemeFontSizeIsScaledAndClamped() {
        CrashsImGuiTheme.applyUiScale(2f)
        assertEquals(48f, CrashsImGuiTheme.fontSize, 0.01f)

        CrashsImGuiTheme.applyUiScale(0.5f)
        assertEquals(12f, CrashsImGuiTheme.fontSize, 0.01f)

        // Out-of-range input is clamped instead of producing a tiny/huge atlas request.
        CrashsImGuiTheme.applyUiScale(4f)
        assertEquals(48f, CrashsImGuiTheme.fontSize, 0.01f)
    }
}
