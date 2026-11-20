package com.crashs.crashslegacy.feature.itemtracker

import net.minecraft.network.chat.Component
import com.crashs.crashslegacy.text.NamedColors
import com.crashs.crashslegacy.text.TextComponentFlatten

/**
 * Builds an [ItemTrackerEntry] from the live component so the warehouse text keeps the server's real
 * colours (the plugin styles `大型仓库` gold, the brackets white and the page number green).
 */
object ItemTrackerStyledParser {

    /** Colour used when a styled run carries no explicit colour (the chat's default white). */
    private const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()

    fun parse(message: Component, plain: String, patternText: String): ItemTrackerEntry? {
        val normalized = plain.replace('\u00A0', ' ')
        val entry = ItemTrackerParser.parse(normalized, patternText) ?: return null
        val regex = runCatching { Regex(patternText.ifBlank { ItemTrackerParser.DEFAULT_PATTERN }) }.getOrNull() ?: return entry
        val match = regex.matchEntire(normalized) ?: return entry

        val parts = runCatching { TextComponentFlatten.flatten(message) }.getOrNull() ?: return entry
        if (parts.joinToString("") { it.text } != normalized) {
            // The flattened text does not line up with the plain string (padding/trimming), so the
            // §-code fallback from the plain parser is the safer choice.
            return entry
        }

        val itemGroup = match.groups[2]?.range ?: return entry
        val warehouseGroup = match.groups[3]?.range ?: return entry
        val warehouseEnd = normalized.length

        val itemParts = TextComponentFlatten.slice(parts, itemGroup.first, itemGroup.last + 1)
        val itemColor = itemParts.firstNotNullOfOrNull { colorOf(it.color) } ?: entry.itemColor

        val warehouseParts = TextComponentFlatten.slice(parts, warehouseGroup.first, warehouseEnd)
        val warehouse = warehouseParts
            .filter { it.text.isNotEmpty() }
            .map { ItemTrackerSegment(it.text, colorOf(it.color)) }
            .let { ItemTrackerParser.trimSegments(it) }
        if (warehouse.isEmpty()) return entry

        return entry.copy(itemColor = itemColor, warehouse = warehouse)
    }

    private fun colorOf(name: String?): Int? = name?.let { NamedColors.colorOf(it) }
}

/** Maps the named chat colours (`gold`, `white`, `#4c8dff`, …) to ARGB. */
