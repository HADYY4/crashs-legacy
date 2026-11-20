package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Modifier
import com.crashs.crashslegacy.hud.HudDragState
import com.crashs.crashslegacy.hud.HudPlacement
import com.crashs.crashslegacy.hud.HudSnap
import com.crashs.crashslegacy.ui.CrashsHudEditorScreen
import com.crashs.crashslegacy.ui.CrashsImGuiConfigBinder
import com.crashs.crashslegacy.ui.CrashsImGuiFields
import com.crashs.crashslegacy.ui.CrashsImGuiLayout
import com.crashs.crashslegacy.ui.CrashsImGuiSurface
import com.crashs.crashslegacy.ui.CrashsImGuiUiState
import com.crashs.crashslegacy.ui.CrashsMotion
import com.crashs.crashslegacy.ui.CrashsUiMetrics
import com.crashs.crashslegacy.ui.theme.CrashsImGuiTheme
import com.crashs.crashslegacy.ui.theme.CrashsThemePalette

/**
 * Guards the "a theme is complete" rule:
 *
 * 1. every `ImGuiCol` slot is themed explicitly (this is what caught ImGui's navy default tabs in
 *    the light scheme);
 * 2. no UI source hardcodes a color literal, so nothing can bypass the theme.
 */
class CrashsThemeCoverageTest {

    /** Files that draw the ClickGUI surface. They must not contain color literals. */
    private val uiSources = listOf(
        "CrashsImGuiSurface.kt",
        "CrashsImGuiConfigBinder.kt",
        "CrashsImGuiTheme.kt",
        "CrashsImGuiLayout.kt",
        "CrashsImGuiUiState.kt",
        "CrashsImGuiFields.kt",
        "CrashsHudEditorScreen.kt",
        "CrashsThemePalette.kt",
        "CrashsUiMetrics.kt",
        "CrashsMotion.kt",
        "HudPlacement.kt",
        "HudDragState.kt",
        "HudSnap.kt",
        "HudComponents.kt",
    )

    private val colorLiteral = Regex("""0x[0-9A-Fa-f]{8}""")

    @Test
    fun everyImGuiColSlotIsThemed() {
        val allSlots = imgui.flag.ImGuiCol::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
            .mapNotNull { runCatching { it.getInt(null) }.getOrNull() }
            .filter { it in 0..61 } // COUNT and the alias names fall outside the real slots
            .toSet()
        assertTrue(allSlots.size >= 62, "reflection did not find the ImGuiCol slots")

        val themed = CrashsImGuiTheme.colorEntries().map { it.first }.toSet()
        val missing = (allSlots - themed).sorted()
        assertTrue(missing.isEmpty(), "these ImGuiCol slots keep their ImGui default: $missing")
    }

    @Test
    fun everyColorEntryHasARealValue() {
        val entries = CrashsImGuiTheme.colorEntries()
        assertEquals(62, entries.size, "expected exactly one entry per ImGuiCol slot")
        assertEquals(62, entries.map { it.first }.toSet().size, "duplicate ImGuiCol slots")
    }

    @Test
    fun uiSourcesDoNotHardcodeColors() {
        val root = System.getProperty("crashs.projectDir") ?: error("crashs.projectDir is not set")
        val offenders = mutableListOf<String>()
        for (name in uiSources) {
            // The palette itself is where the literals are allowed to live.
            if (name == "CrashsThemePalette.kt") continue
            val file = File(root, "src/main/kotlin/com/crashs/crashslegacy/$name")
            if (!file.exists()) continue
            file.readLines().forEachIndexed { index, line ->
                if (colorLiteral.containsMatchIn(line)) {
                    offenders += "$name:${index + 1}: ${line.trim()}"
                }
            }
        }
        assertTrue(offenders.isEmpty(), "UI sources must take colors from the theme:\n${offenders.joinToString("\n")}")
    }

    /**
     * Theme tokens are ARGB while ImGui's draw lists want ABGR. A token that reaches a draw call
     * unconverted renders with red and blue swapped — that is how the amber accent turned into a
     * light blue swatch while the ImGui-styled slider stayed amber. Every color token has to go
     * through `CrashsThemePalette.imU32`, `setColor` or a float conversion.
     */
    @Test
    fun themeColoursAreConvertedBeforeDrawing() {
        val root = System.getProperty("crashs.projectDir") ?: error("crashs.projectDir is not set")
        val token = Regex("""CrashsImGuiTheme\.[A-Z_]+""")
        val offenders = mutableListOf<String>()
        for (name in listOf("CrashsImGuiSurface.kt", "CrashsImGuiConfigBinder.kt", "CrashsHudEditorScreen.kt")) {
            val file = File(root, "src/main/kotlin/com/crashs/crashslegacy/$name")
            if (!file.exists()) continue
            file.readLines().forEachIndexed { index, line ->
                if (!token.containsMatchIn(line)) return@forEachIndexed
                val converted = line.contains("imU32(") ||
                    line.contains("shr ") ||
                    line.contains("setColor") ||
                    line.contains("pushStyleColor(") ||
                    line.contains("colorText(") ||
                    line.contains("textColoredArgb(")
                if (!converted) {
                    offenders += "$name:${index + 1}: ${line.trim()}"
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "theme tokens must be converted before they reach ImGui:\n${offenders.joinToString("\n")}",
        )
    }
}
