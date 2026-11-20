package com.crashs.crashslegacy

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.ui.CrashsImGuiLayout

/** The JSON-driven metrics must stay inside the ranges the layout code assumes. */
class CrashsImGuiLayoutTest {

    private var railWidth: Float = 0f
    private var navWidth: Float = 0f
    private var navHeight: Float = 0f
    private var uiFontSize: Float = 0f
    private var rasterizerDensity: Float = 0f

    @BeforeEach
    fun remember() {
        railWidth = CrashsImGuiLayout.railWidth
        navWidth = CrashsImGuiLayout.navWidth
        navHeight = CrashsImGuiLayout.navHeight
        uiFontSize = CrashsImGuiLayout.uiFontSize
        rasterizerDensity = CrashsImGuiLayout.rasterizerDensity
    }

    @AfterEach
    fun restore() {
        CrashsImGuiLayout.railWidth = railWidth
        CrashsImGuiLayout.navWidth = navWidth
        CrashsImGuiLayout.navHeight = navHeight
        CrashsImGuiLayout.uiFontSize = uiFontSize
        CrashsImGuiLayout.rasterizerDensity = rasterizerDensity
    }

    @Test
    fun fontSizeIsClampedToTheSupportedRange() {
        CrashsImGuiLayout.uiFontSize = 4f
        assertEquals(12f, CrashsImGuiLayout.fontSize())
        CrashsImGuiLayout.uiFontSize = 400f
        assertEquals(40f, CrashsImGuiLayout.fontSize())
        CrashsImGuiLayout.uiFontSize = 24f
        assertEquals(24f, CrashsImGuiLayout.fontSize())
    }

    @Test
    fun rasterizerDensityStaysWithinOneAndTwo() {
        CrashsImGuiLayout.rasterizerDensity = 0.1f
        assertEquals(1f, CrashsImGuiLayout.density())
        CrashsImGuiLayout.rasterizerDensity = 9f
        assertEquals(2f, CrashsImGuiLayout.density())
    }

    @Test
    fun railIsClampedSoTheCardsAlwaysHaveRoom() {
        CrashsImGuiLayout.railWidth = 40f
        assertEquals(150f, CrashsImGuiLayout.rail())
        CrashsImGuiLayout.railWidth = 900f
        assertEquals(280f, CrashsImGuiLayout.rail())
    }

    @Test
    fun pillNeverGrowsPastTheRail() {
        CrashsImGuiLayout.railWidth = 150f
        CrashsImGuiLayout.navWidth = 400f
        assertTrue(
            CrashsImGuiLayout.pillWidth() <= CrashsImGuiLayout.rail() - 24f,
            "pill ${CrashsImGuiLayout.pillWidth()} does not fit the rail ${CrashsImGuiLayout.rail()}",
        )
    }

    @Test
    fun defaultsLeaveRoomForAFourCharacterLabel() {
        CrashsImGuiLayout.railWidth = 190f
        CrashsImGuiLayout.navWidth = 160f
        assertEquals(190f, CrashsImGuiLayout.rail())
        assertEquals(160f, CrashsImGuiLayout.pillWidth())
    }
}
