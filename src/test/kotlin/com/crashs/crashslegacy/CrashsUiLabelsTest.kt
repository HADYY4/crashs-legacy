package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import com.crashs.crashslegacy.feature.killfeed.ComboGroup
import com.crashs.crashslegacy.feature.killfeed.PostComboMode
import com.crashs.crashslegacy.feature.killfeed.PostComboSound
import com.crashs.crashslegacy.ui.ChoiceOption
import com.crashs.crashslegacy.ui.CrashsImGuiConfigBinder
import com.crashs.crashslegacy.ui.CrashsUiLabels

/**
 * Every dropdown option must be readable in Chinese while the config keeps the stable English value.
 * The tests also fail when a new enum constant or a raw `choice(...)` call forgets its label.
 */
class CrashsUiLabelsTest {

    private val projectDir = File(System.getProperty("crashs.projectDir") ?: ".")

    private val lists: Map<String, List<ChoiceOption>> = linkedMapOf(
        "comboGroups" to CrashsUiLabels.comboGroups,
        "postModes" to CrashsUiLabels.postModes,
        "postSounds" to CrashsUiLabels.postSounds,
        "headerStyles" to CrashsUiLabels.headerStyles,
        "numberFormats" to CrashsUiLabels.numberFormats,
        "statsModes" to CrashsUiLabels.statsModes,
        "averageUnits" to CrashsUiLabels.averageUnits,
        "fileFormats" to CrashsUiLabels.fileFormats,
        "itemTrackerDirections" to CrashsUiLabels.itemTrackerDirections,
        "broadcastSounds" to CrashsUiLabels.broadcastSounds,
    )

    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x2E80..0x9FFF || code in 0x3000..0x303F || code in 0xFF00..0xFF65
    }

    @Test
    fun everyOptionHasAChineseLabel() {
        for ((name, options) in lists) {
            assertTrue(options.isNotEmpty(), "$name is empty")
            for (option in options) {
                assertTrue(option.label.isNotBlank(), "$name/${option.value} has a blank label")
                assertTrue(
                    option.label.any { isCjk(it) },
                    "$name/${option.value} label is not Chinese: '${option.label}'",
                )
            }
        }
    }

    @Test
    fun valuesAreUniqueWithinEachList() {
        for ((name, options) in lists) {
            val values = options.map { it.value }
            assertEquals(values.size, values.toSet().size, "$name has duplicate values: $values")
        }
    }

    @Test
    fun everyEnumConstantHasALabel() {
        val source = File(projectDir, "src/main/kotlin/com/crashs/crashslegacy/feature/killfeed/CrashsLegacySound.kt").readText()

        assertEquals(
            enumConstants(source, "ComboGroup"),
            CrashsUiLabels.comboGroups.map { it.value }.toSet(),
            "ComboGroup labels are out of sync with the enum",
        )
        assertEquals(
            enumConstants(source, "PostComboMode"),
            CrashsUiLabels.postModes.map { it.value }.toSet(),
            "PostComboMode labels are out of sync with the enum",
        )
        assertEquals(
            enumConstants(source, "PostComboSound"),
            CrashsUiLabels.postSounds.map { it.value }.toSet(),
            "PostComboSound labels are out of sync with the enum",
        )
    }

    @Test
    fun theBinderNeverPassesRawOptionLists() {
        val binder = File(projectDir, "src/main/kotlin/com/crashs/crashslegacy/ui/CrashsImGuiConfigBinder.kt").readText()
        val unlocalized = binder.lineSequence()
            .filter { it.contains("choice(\"") }
            .filterNot { it.contains("CrashsUiLabels.") }
            .toList()
        assertTrue(unlocalized.isEmpty(), "choice() calls must use CrashsUiLabels:\n${unlocalized.joinToString("\n")}")
    }

    private fun enumConstants(source: String, enumName: String): Set<String> {
        val start = source.indexOf("enum class $enumName")
        require(start >= 0) { "enum class $enumName not found" }
        val bodyStart = source.indexOf('{', start)
        val end = source.indexOf("companion object", bodyStart).let { if (it < 0) source.length else it }
        val body = source.substring(bodyStart, end)
        return Regex("""(?m)^\s{4}([A-Z][A-Z0-9_]+)\s*[(,;]""")
            .findAll(body)
            .map { it.groupValues[1] }
            .toSet()
    }
}
