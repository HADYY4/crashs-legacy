package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import com.crashs.crashslegacy.ui.theme.CrashsThemeMode
import com.crashs.crashslegacy.ui.theme.CrashsThemePalette

/**
 * A theme is a complete color system, so every pairing the UI actually draws has to stay readable in
 * both schemes and with every accent. These tests are the contract; they run without ImGui.
 */
class CrashsThemePaletteTest {

    private fun colors(mode: CrashsThemeMode, accent: Int = CrashsThemePalette.DEFAULT_ACCENT) =
        CrashsThemePalette.colors(mode, accent)

    private fun delta(first: Int, second: Int) =
        abs(CrashsThemePalette.luminance(first) - CrashsThemePalette.luminance(second))

    @Test
    fun luminanceAndContrastRatioBehave() {
        assertEquals(0f, CrashsThemePalette.luminance(0xFF000000.toInt()), 0.001f)
        assertEquals(1f, CrashsThemePalette.luminance(0xFFFFFFFF.toInt()), 0.001f)
        assertEquals(21f, CrashsThemePalette.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01f)
    }

    @Test
    fun enabledSwitchKnobStaysLightInEveryScheme() {
        // Regression: ON_PRIMARY resolves to black for blue/amber accents and turned the knob into
        // a black hole. The enabled knob must stay light on any accent.
        for (mode in CrashsThemeMode.entries) {
            for (accent in CrashsThemePalette.ACCENT_PRESETS) {
                val knob = CrashsThemePalette.colors(mode, accent).switchKnobOn
                assertTrue(
                    CrashsThemePalette.luminance(knob) > 0.8f,
                    "enabled knob must stay light for $mode/$accent",
                )
            }
        }
    }

    @Test
    fun contrastFlipsWithTheBackground() {
        assertTrue(CrashsThemePalette.luminance(CrashsThemePalette.contrast(0xFF151515.toInt())) > 0.8f)
        assertTrue(CrashsThemePalette.luminance(CrashsThemePalette.contrast(0xFFF4F4F6.toInt())) < 0.2f)
    }

    @Test
    fun textIsReadableOnEverySurface() {
        for (mode in CrashsThemeMode.entries) {
            val scheme = colors(mode)
            for (surface in listOf(scheme.window, scheme.card, scheme.rail, scheme.popover, scheme.tooltip)) {
                val ratio = CrashsThemePalette.contrastRatio(scheme.text, surface)
                assertTrue(ratio >= 4.5f, "$mode text on #${surface.toUInt().toString(16)} = $ratio")
            }
            val muted = CrashsThemePalette.contrastRatio(scheme.textMuted, scheme.card)
            assertTrue(muted >= 3f, "$mode muted text on card = $muted")
        }
    }

    @Test
    fun controlsAreVisibleAgainstTheirCard() {
        for (mode in CrashsThemeMode.entries) {
            val scheme = colors(mode)
            val bases = listOf(
                "field" to scheme.field,
                "button" to scheme.button,
                "sliderTrack" to scheme.sliderTrack,
            )
            for ((name, color) in bases) {
                val difference = delta(color, scheme.card)
                assertTrue(difference >= 0.03f, "$mode $name is invisible on the card ($difference)")
            }
            val headerDifference = delta(scheme.header, scheme.card)
            assertTrue(headerDifference >= 0.02f, "$mode header is invisible ($headerDifference)")
            val tabDifference = delta(scheme.tab, scheme.card)
            assertTrue(tabDifference >= 0.03f, "$mode tab is invisible ($tabDifference)")
            val capsuleDifference = delta(scheme.capsuleBase, scheme.rail)
            assertTrue(capsuleDifference >= 0.03f, "$mode capsule is invisible on the rail ($capsuleDifference)")

            // Hover states only have to be distinguishable from their own base.
            assertTrue(delta(scheme.fieldHover, scheme.field) >= 0.02f, "$mode field hover is invisible")
            assertTrue(delta(scheme.buttonHover, scheme.button) >= 0.02f, "$mode button hover is invisible")

            assertTrue(delta(scheme.border, scheme.card) >= 0.03f, "$mode border is invisible")
            assertTrue(delta(scheme.switchTrackOff, scheme.card) >= 0.06f, "$mode switch track is invisible")
            assertTrue(delta(scheme.switchKnobOff, scheme.switchTrackOff) >= 0.06f, "$mode switch knob is invisible")
        }
    }

    @Test
    fun accentTextStaysReadableOnEveryPreset() {
        for (mode in CrashsThemeMode.entries) {
            for (accent in CrashsThemePalette.ACCENT_PRESETS) {
                val scheme = colors(mode, accent)
                val onAccent = CrashsThemePalette.contrastRatio(scheme.onAccent, scheme.accent)
                assertTrue(onAccent >= 3f, "$mode #${accent.toUInt().toString(16)} on-accent = $onAccent")
                val onContainer = CrashsThemePalette.contrastRatio(scheme.onAccentContainer, scheme.accentContainer)
                assertTrue(onContainer >= 3f, "$mode #${accent.toUInt().toString(16)} on-container = $onContainer")
            }
        }
    }

    @Test
    fun selectedTabAndCapsuleAreDistinguishable() {
        for (mode in CrashsThemeMode.entries) {
            val scheme = colors(mode)
            assertTrue(delta(scheme.tabSelected, scheme.card) >= 0.02f, "$mode selected tab is invisible")
            assertTrue(delta(scheme.accentContainer, scheme.card) >= 0.02f, "$mode selected capsule is invisible")
        }
    }

    @Test
    fun statusColorsCarryColour() {
        for (mode in CrashsThemeMode.entries) {
            val scheme = colors(mode)
            for ((name, color) in listOf(
                "success" to scheme.success,
                "warning" to scheme.warning,
                "info" to scheme.info,
                "error" to scheme.error,
            )) {
                assertTrue(
                    CrashsThemePalette.contrastRatio(color, scheme.card) >= 2f,
                    "$mode $name is too close to the card",
                )
            }
        }
    }

    @Test
    fun lightSchemeIsLighterThanDarkScheme() {
        val dark = colors(CrashsThemeMode.DARK)
        val light = colors(CrashsThemeMode.LIGHT)
        assertTrue(CrashsThemePalette.luminance(dark.window) < 0.2f)
        assertTrue(CrashsThemePalette.luminance(light.window) > 0.8f)
        assertTrue(CrashsThemePalette.luminance(dark.text) > 0.7f)
        assertTrue(CrashsThemePalette.luminance(light.text) < 0.2f)
    }

    @Test
    fun blendHitsBothEndpoints() {
        val foreground = 0xFFFF615A.toInt()
        val background = 0xFF151515.toInt()
        assertEquals(foreground, CrashsThemePalette.blend(foreground, background, 0f))
        assertEquals(background, CrashsThemePalette.blend(foreground, background, 1f))
    }

    @Test
    fun editorGuideTokensDeriveFromTheAccent() {
        for (mode in CrashsThemeMode.entries) {
            for (accent in CrashsThemePalette.ACCENT_PRESETS) {
                val scheme = colors(mode, accent)
                // Same hue as the accent, only the alpha differs.
                assertEquals(accent and 0x00FFFFFF, scheme.guideScreen and 0x00FFFFFF)
                assertEquals(accent and 0x00FFFFFF, scheme.guideComponent and 0x00FFFFFF)
                assertEquals(accent and 0x00FFFFFF, scheme.boxIdle and 0x00FFFFFF)
                assertEquals(accent and 0x00FFFFFF, scheme.dragHint and 0x00FFFFFF)
                // Component guides are more prominent than screen guides, hints are faintest.
                assertTrue((scheme.guideScreen ushr 24) < (scheme.guideComponent ushr 24))
                assertTrue((scheme.dragHint ushr 24) < (scheme.guideScreen ushr 24))
            }
        }
    }

    @Test
    fun imU32SwapsRedAndBlueAndKeepsAlpha() {
        // Coral: ARGB 0xAARRGGBB -> ImU32 0xAABBGGRR
        assertEquals(0xFF5A61FF.toInt(), CrashsThemePalette.imU32(0xFFFF615A.toInt()))
        assertEquals(0xFFFF8D4C.toInt(), CrashsThemePalette.imU32(0xFF4C8DFF.toInt()))
        // Greys are symmetric, and the alpha byte stays on top.
        assertEquals(0x80EDEDED.toInt(), CrashsThemePalette.imU32(0x80EDEDED.toInt()))
        // Round trip.
        val token = 0xFFF5A35D.toInt()
        assertEquals(token, CrashsThemePalette.imU32(CrashsThemePalette.imU32(token)))
    }
}
