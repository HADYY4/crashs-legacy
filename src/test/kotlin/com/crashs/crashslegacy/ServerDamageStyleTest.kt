package com.crashs.crashslegacy.serverdamage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The 文字阴影 switch used to only ever *set* its flag bit, so a server that already rendered
 * shadows could never be turned off — both directions looked dead. These tests pin the override
 * semantics down so it cannot regress to "set only" again.
 */
class ServerDamageStyleTest {

    private val shadow = ServerDamageStyle.SHADOW_BIT
    private val defaultBackground = ServerDamageStyle.DEFAULT_BACKGROUND_BIT

    @Test
    fun shadowOnSetsTheBit() {
        val result = ServerDamageStyle.flags(baselineFlags = 0, shadow = true, transparentBackground = false)
        assertTrue(result and shadow != 0)
    }

    @Test
    fun shadowOffClearsTheServerBit() {
        // Server already renders a shadow: the switch must still be able to remove it.
        val baseline = shadow or defaultBackground
        val result = ServerDamageStyle.flags(baseline, shadow = false, transparentBackground = false)
        assertEquals(0, result and shadow)
    }

    @Test
    fun transparentBackgroundClearsTheDefaultBit() {
        val result = ServerDamageStyle.flags(defaultBackground, shadow = false, transparentBackground = true)
        assertEquals(0, result and defaultBackground)
    }

    @Test
    fun backgroundOffRestoresTheServerBit() {
        // The server colour is used only when the default-background bit is set again.
        val result = ServerDamageStyle.flags(defaultBackground, shadow = false, transparentBackground = false)
        assertTrue(result and defaultBackground != 0)
    }

    @Test
    fun switchesDoNotInterfereWithEachOther() {
        val baseline = defaultBackground
        val shadowOnAndBackgroundOn =
            ServerDamageStyle.flags(baseline, shadow = true, transparentBackground = true)
        assertEquals(shadow, shadowOnAndBackgroundOn and (shadow or defaultBackground))

        val shadowOnAndBackgroundOff =
            ServerDamageStyle.flags(baseline, shadow = true, transparentBackground = false)
        assertEquals(shadow or defaultBackground, shadowOnAndBackgroundOff and (shadow or defaultBackground))

        val bothOff = ServerDamageStyle.flags(baseline, shadow = false, transparentBackground = false)
        assertEquals(baseline, bothOff and (shadow or defaultBackground))
    }

    @Test
    fun applyingTwiceIsIdempotent() {
        val once = ServerDamageStyle.flags(defaultBackground, shadow = true, transparentBackground = true)
        val twice = ServerDamageStyle.flags(once, shadow = true, transparentBackground = true)
        assertEquals(once, twice)
    }

    @Test
    fun backgroundColourFollowsTheSwitch() {
        assertEquals(0, ServerDamageStyle.background(0x40FF0000, transparentBackground = true))
        assertEquals(0x40FF0000, ServerDamageStyle.background(0x40FF0000, transparentBackground = false))
    }

    @Test
    fun freezingNeedsTheModuleTheSwitchAndAKnownDamageText() {
        assertTrue(ServerDamageStyle.shouldFreeze(serverDamageEnabled = true, freezeEnabled = true, tracked = true))
        assertTrue(!ServerDamageStyle.shouldFreeze(serverDamageEnabled = false, freezeEnabled = true, tracked = true))
        assertTrue(!ServerDamageStyle.shouldFreeze(serverDamageEnabled = true, freezeEnabled = false, tracked = true))
        assertTrue(!ServerDamageStyle.shouldFreeze(serverDamageEnabled = true, freezeEnabled = true, tracked = false))
    }
}
