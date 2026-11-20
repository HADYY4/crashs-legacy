package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.config.CrashsLegacyConfig
import com.crashs.crashslegacy.hud.CoinsInfoHudComponent
import com.crashs.crashslegacy.hud.CrashsLegacyHudComponent
import com.crashs.crashslegacy.hud.HUD_COMPONENTS
import com.crashs.crashslegacy.hud.HudComponent
import com.crashs.crashslegacy.hud.ItemTrackerHudComponent
import com.crashs.crashslegacy.ui.CrashsImGuiUiState
import com.crashs.crashslegacy.ui.CrashsUiPaths

/**
 * The HUD editor drives every component through [HudComponent]; these tests pin the config contract
 * (percent range, scale clamps, anchors, defaults) without Minecraft or ImGui.
 */
class HudComponentsTest {

    private fun config() = CrashsLegacyConfig()

    @Test
    fun crashsLegacyDefaultsComeFromReset() {
        val config = config()
        config.hudX = 3f
        config.hudY = 90f
        config.hudScale = 2.2f
        config.hudRight = true
        CrashsLegacyHudComponent.reset(config)
        assertEquals(50f, config.hudX)
        assertEquals(50f, config.hudY)
        assertEquals(1f, config.hudScale)
        assertFalse(config.hudRight)
    }

    @Test
    fun coinsDefaultsComeFromReset() {
        val config = config()
        config.coinsInfoScale = 2.2f
        config.coinsInfoRight = true
        CoinsInfoHudComponent.reset(config)
        assertEquals(35f, config.coinsInfoX)
        assertEquals(50f, config.coinsInfoY)
        assertEquals(1f, config.coinsInfoScale)
        assertFalse(config.coinsInfoRight)
    }

    @Test
    fun movingClampsToThePercentRange() {
        val config = config()
        CrashsLegacyHudComponent.moveTo(config, -20f, 140f)
        assertEquals(0f, config.hudX)
        assertEquals(100f, config.hudY)
        CoinsInfoHudComponent.moveTo(config, 150f, -5f)
        assertEquals(100f, config.coinsInfoX)
        assertEquals(0f, config.coinsInfoY)
    }

    @Test
    fun scaleIsClampedToTheRenderedRange() {
        val config = config()
        CrashsLegacyHudComponent.setScale(config, 9f)
        assertEquals(2.5f, config.hudScale)
        CrashsLegacyHudComponent.setScale(config, 0.1f)
        assertEquals(0.5f, config.hudScale)
        CoinsInfoHudComponent.setScale(config, 9f)
        assertEquals(2.5f, config.coinsInfoScale)
    }

    @Test
    fun enabledFollowsTheModuleAndComponentSwitches() {
        val config = config()
        config.enabled = true
        config.hud = true
        assertTrue(CrashsLegacyHudComponent.isEnabled(config))
        config.hud = false
        assertFalse(CrashsLegacyHudComponent.isEnabled(config))
        config.enabled = false
        config.hud = true
        assertFalse(CrashsLegacyHudComponent.isEnabled(config))

        config.enabled = true
        config.coinsInfoEnabled = false
        assertFalse(CoinsInfoHudComponent.isEnabled(config))
        config.coinsInfoEnabled = true
        assertTrue(CoinsInfoHudComponent.isEnabled(config))
    }

    @Test
    fun coinsStayEnabledWhenCrashsLegacyIsOff() {
        val config = config()
        config.enabled = false
        config.coinsInfoEnabled = true

        assertTrue(CoinsInfoHudComponent.isEnabled(config), "coins must not depend on the kill-feedback master")
        assertFalse(CrashsLegacyHudComponent.isEnabled(config), "the kill HUD still follows its own master")

        config.coinsInfoEnabled = false
        assertFalse(CoinsInfoHudComponent.isEnabled(config), "the coins switch is its own master")
    }

    @Test
    fun crashsLegacyStaysIndependentFromCoins() {
        val config = config()
        config.enabled = true
        config.hud = true
        config.coinsInfoEnabled = false

        assertTrue(CrashsLegacyHudComponent.isEnabled(config))
        assertFalse(CoinsInfoHudComponent.isEnabled(config))
    }

    @Test
    fun anchorsToggleWithoutTouchingThePosition() {
        val config = config()
        CrashsLegacyHudComponent.moveTo(config, 12f, 34f)
        CrashsLegacyHudComponent.setRight(config, true)
        assertTrue(CrashsLegacyHudComponent.right(config))
        assertEquals(12f, CrashsLegacyHudComponent.position(config).first)
        assertEquals(34f, CrashsLegacyHudComponent.position(config).second)
    }

    @Test
    fun componentsAreAddressableById() {
        assertEquals("crashslegacy", CrashsLegacyHudComponent.id)
        assertEquals("coinsinfo", CoinsInfoHudComponent.id)
        assertEquals("itemtracker", ItemTrackerHudComponent.id)
        assertEquals(3, HUD_COMPONENTS.size)
        assertEquals(
            HUD_COMPONENTS.toSet(),
            setOf(CrashsLegacyHudComponent, CoinsInfoHudComponent, ItemTrackerHudComponent),
        )
    }

    @Test
    fun theItemTrackerIsIndependentFromTheOtherModules() {
        val config = config()
        config.enabled = false
        config.coinsInfoEnabled = false
        config.itemTrackerEnabled = true
        config.itemTrackerHud = true
        assertTrue(ItemTrackerHudComponent.isEnabled(config))

        config.itemTrackerHud = false
        assertFalse(ItemTrackerHudComponent.isEnabled(config))
    }

    @Test
    fun theRememberedPageRoundTrips() {
        CrashsUiPaths.root = null
        CrashsImGuiUiState.reload()
        CrashsImGuiUiState.setPage("CONFIG")
        assertEquals("CONFIG", CrashsImGuiUiState.page())
        CrashsImGuiUiState.setPage("MODULES")
        assertEquals("MODULES", CrashsImGuiUiState.page())
    }

    @Test
    fun panelPositionRoundTrips() {
        CrashsUiPaths.root = null
        CrashsImGuiUiState.reload()
        CrashsImGuiUiState.setHudPanelPosition(123f, 45f)
        assertEquals(123f to 45f, CrashsImGuiUiState.hudPanelPosition())
    }
}
