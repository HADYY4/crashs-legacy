package com.crashs.crashslegacy

import imgui.ImGui
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import com.crashs.crashslegacy.ui.CrashsImGuiLayout
import com.crashs.crashslegacy.ui.CrashsUiMetrics
import com.crashs.crashslegacy.ui.theme.CrashsImGuiTheme

/**
 * Headless checks against the real ImGui build and the real font. These cover the two bug classes
 * that were invisible until someone launched the client:
 *
 * - a CJK character used by the UI but missing from the baked glyph ranges (rendered as '?');
 * - capsule / card geometry that no longer fits its container.
 *
 * No Minecraft classes are touched, so plain `./gradlew test` runs them.
 */
class UiHeadlessTest {

    companion object {
        private val projectDir: File = File(System.getProperty("crashs.projectDir") ?: ".")

        @JvmStatic
        @BeforeAll
        fun setUp() {
            // Always create our own context: `getCurrentContext()` cannot be used to detect "none"
            // because the binding hands back a wrapper either way.
            ImGui.createContext()
            val io = ImGui.getIO()
            io.setIniFilename(null)
            io.setDisplaySize(1040f, 760f)
            val size = CrashsImGuiLayout.fontSize()
            val fontFile = File(projectDir, "src/main/resources/assets/crashs-legacy/fonts/misans.ttf")
            assertTrue(fontFile.isFile, "bundled font not found: ${fontFile.absolutePath}")
            val font = io.fonts.addFontFromFileTTF(fontFile.absolutePath, size)
            assertNotNull(font, "ImGui refused to load MiSans")
            io.fonts.build()
            ImGui.pushFont(font, size)
        }

        private val stringLiteral = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"([^\"\\\\]|\\\\.)*\"")

        private fun isCjk(ch: Char): Boolean {
            val code = ch.code
            return code in 0x2E80..0x9FFF || code in 0x3000..0x303F || code in 0xFF00..0xFF65
        }

        private fun rangesContain(ranges: ShortArray, code: Int): Boolean {
            var i = 0
            while (i + 1 < ranges.size) {
                val start = ranges[i].toInt() and 0xFFFF
                val end = ranges[i + 1].toInt() and 0xFFFF
                if (start == 0 && end == 0) return false
                if (code in start..end) return true
                i += 2
            }
            return false
        }
    }

    @Test
    fun everyCjkCharacterInStringLiteralsIsInTheBakedRanges() {
        val ranges = CrashsImGuiTheme.buildGlyphRanges(ImGui.getIO().fonts)
        val sourceRoot = File(projectDir, "src/main/kotlin")
        assertTrue(sourceRoot.isDirectory, "source root not found: ${sourceRoot.absolutePath}")

        val missing = LinkedHashMap<Char, MutableSet<String>>()
        val seen = LinkedHashSet<Char>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                stringLiteral.findAll(text).forEach { match ->
                    match.value.forEach { ch ->
                        if (!isCjk(ch)) return@forEach
                        seen += ch
                        if (!rangesContain(ranges, ch.code)) {
                            missing.getOrPut(ch) { linkedSetOf() }.add(file.name)
                        }
                    }
                }
            }

        // Guards the scan itself: a broken string-literal regex would silently find nothing.
        assertTrue(seen.size >= 200, "只扫描到 ${seen.size} 个中文字符，字符串扫描逻辑可能失效了")

        assertTrue(missing.isEmpty()) {
            missing.entries.joinToString(
                prefix = "这些字会渲染成 '?'，请加入 CrashsImGuiTheme.EXTRA_GLYPHS：\n",
                separator = "\n",
            ) { (ch, files) -> "  '$ch' U+%04X  %s".format(ch.code, files.joinToString()) }
        }
    }

    @Test
    fun theCommonRangeAloneWouldMissCharacters() {
        // Documents why EXTRA_GLYPHS exists: the 2500-glyph common range really does miss them.
        val common = ImGui.getIO().fonts.glyphRangesChineseSimplifiedCommon
        assertTrue(!rangesContain(common, '馈'.code), "common range unexpectedly contains 馈")
        assertTrue(!rangesContain(common, '聊'.code), "common range unexpectedly contains 聊")
    }

    @Test
    fun thePreviouslyBrokenCharactersAreCovered() {
        val ranges = CrashsImGuiTheme.buildGlyphRanges(ImGui.getIO().fonts)
        "馈聊汰凋缀锚屏沌辐".forEach { ch ->
            assertTrue(rangesContain(ranges, ch.code), "'$ch' U+%04X is not baked".format(ch.code))
        }
    }

    @Test
    fun fourCharacterCapsuleStillHasPadding() {
        val labelWidth = ImGui.calcTextSize("击杀反馈").x
        val content = CrashsUiMetrics.pillContentWidth(
            labelWidth = labelWidth,
            hasIcon = true,
            hasArrow = false,
            fontSize = CrashsImGuiLayout.fontSize(),
        )
        val pill = CrashsImGuiLayout.pillWidth()
        assertTrue(
            pill - content >= CrashsUiMetrics.MIN_PILL_PADDING * 2f,
            "four-character label needs $content but the pill is only $pill wide",
        )
    }

    @Test
    fun parentCapsuleStillHasRoomForTheArrow() {
        val labelWidth = ImGui.calcTextSize("模块").x
        val content = CrashsUiMetrics.pillContentWidth(
            labelWidth = labelWidth,
            hasIcon = true,
            hasArrow = true,
            fontSize = CrashsImGuiLayout.fontSize(),
        )
        val pill = CrashsImGuiLayout.pillWidth()
        assertTrue(
            pill - content >= CrashsUiMetrics.MIN_PILL_PADDING * 2f,
            "capsule with arrow needs $content but the pill is only $pill wide",
        )
    }
}
