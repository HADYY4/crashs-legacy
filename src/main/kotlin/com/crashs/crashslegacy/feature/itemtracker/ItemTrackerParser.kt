package com.crashs.crashslegacy.feature.itemtracker

/** Where a tracker row came from. */
enum class ItemTrackerKind { WAREHOUSE, INVENTORY_GAIN, INVENTORY_LOSS }

/** One styled run of a warehouse name, as the server sent it. */
data class ItemTrackerSegment(val text: String, val color: Int?)

/** One parsed auto-deposit line: `[PlayerInv] 5x §5晶簇鲷 ==> 大型仓库 [1]`. */
data class ItemTrackerEntry(
    val kind: ItemTrackerKind = ItemTrackerKind.WAREHOUSE,
    val count: Int,
    /** Item name without colour codes, ready for measuring/drawing. */
    val itemName: String,
    /** First colour of the item name, if the server sent one. */
    val itemColor: Int?,
    /** Everything after `==>`, with the original colours preserved per run. */
    val warehouse: List<ItemTrackerSegment>,
    val page: Int?,
    /** Variant key for inventory rows (merged within a window); null for warehouse rows. */
    val rowKey: String? = null,
) {
    val warehouseText: String get() = warehouse.joinToString("") { it.text }
}

/**
 * Parser for the warehouse plugin's auto-deposit notifications.
 *
 * The server puts `§x` colour codes inside the item name as literal text, so they are stripped for
 * display and turned into a colour value instead. The warehouse part keeps its per-run colours: the
 * plain-string parser recovers them from `§x` codes, while `ItemTrackerStyledParser` reads the real
 * component styles when the message is still a `Text`.
 */
object ItemTrackerParser {

    const val DEFAULT_PATTERN =
        """^\[PlayerInv\]\s+(\d+)x\s+(.+?)\s+==>\s+(.+)$"""

    private val pagePattern = Regex("""\[(\d+)\]\s*$""")

    private val codePattern = Regex("§[0-9a-fk-orA-FK-ORxX]")
    private val colourCodes = mapOf(
        '0' to 0xFF000000.toInt(), '1' to 0xFF0000AA.toInt(), '2' to 0xFF00AA00.toInt(),
        '3' to 0xFF00AAAA.toInt(), '4' to 0xFFAA0000.toInt(), '5' to 0xFFAA00AA.toInt(),
        '6' to 0xFFFFAA00.toInt(), '7' to 0xFFAAAAAA.toInt(), '8' to 0xFF555555.toInt(),
        '9' to 0xFF5555FF.toInt(), 'a' to 0xFF55FF55.toInt(), 'b' to 0xFF55FFFF.toInt(),
        'c' to 0xFFFF5555.toInt(), 'd' to 0xFFFF55FF.toInt(), 'e' to 0xFFFFFF55.toInt(),
        'f' to 0xFFFFFFFF.toInt(),
    )

    private var cachedPatternText: String? = null
    private var cachedRegex: Regex? = null

    @Synchronized
    private fun regexFor(patternText: String): Regex? {
        val text = patternText.ifBlank { DEFAULT_PATTERN }
        val cached = cachedRegex
        if (text == cachedPatternText && cached != null) return cached
        val compiled = runCatching { Regex(text) }.getOrNull() ?: return null
        cachedPatternText = text
        cachedRegex = compiled
        return compiled
    }

    fun parse(rawText: String, patternText: String): ItemTrackerEntry? {
        val normalized = normalize(rawText)
        if (normalized.isEmpty()) return null
        val regex = regexFor(patternText) ?: return null
        val match = regex.matchEntire(normalized) ?: return null

        val count = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val rawItem = match.groupValues.getOrNull(2).orEmpty()
        val rawWarehouse = match.groupValues.getOrNull(3).orEmpty()
        val page = match.groupValues.getOrNull(4)?.toIntOrNull() ?: pagePattern.find(rawWarehouse)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val itemName = stripCodes(rawItem).trim()
        if (itemName.isEmpty()) return null

        return ItemTrackerEntry(
            count = count,
            itemName = itemName,
            itemColor = firstColour(rawItem),
            warehouse = segmentsOf(rawWarehouse),
            page = page,
        )
    }

    /** Splits a raw string on `§x` codes into coloured runs, dropping the codes themselves. */
    fun segmentsOf(raw: String): List<ItemTrackerSegment> {
        if (raw.isEmpty()) return emptyList()
        val segments = ArrayList<ItemTrackerSegment>()
        var current = StringBuilder()
        var colour: Int? = null
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (char == '§' && index + 1 < raw.length) {
                val code = raw[index + 1].lowercaseChar()
                val mapped = colourCodes[code]
                if (mapped != null || code in 'k'..'o' || code == 'x') {
                    if (current.isNotEmpty()) {
                        segments += ItemTrackerSegment(current.toString(), colour)
                        current = StringBuilder()
                    }
                    colour = mapped ?: colour
                    index += 2
                    continue
                }
            }
            current.append(char)
            index++
        }
        if (current.isNotEmpty()) segments += ItemTrackerSegment(current.toString(), colour)
        return trimSegments(segments)
    }

    /** Removes `§x` formatting codes (and the legacy `§x§r§r§g§g§b§b` hex form). */
    fun stripCodes(text: String): String = codePattern.replace(text, "")

    /** Colour of the first `§x` code in [text], or null when the name has no colour. */
    fun firstColour(text: String): Int? {
        val match = codePattern.find(text) ?: return null
        val code = match.value.getOrNull(1)?.lowercaseChar() ?: return null
        return colourCodes[code]
    }

    /** Trims leading/trailing whitespace of a segment list without losing colours. */
    fun trimSegments(segments: List<ItemTrackerSegment>): List<ItemTrackerSegment> {
        val result = ArrayList<ItemTrackerSegment>(segments.size)
        for (segment in segments) {
            var text = segment.text
            if (result.isEmpty()) text = text.trimStart()
            if (text.isNotEmpty()) result += segment.copy(text = text)
        }
        if (result.isEmpty()) return result
        val last = result.last()
        val trimmed = last.text.trimEnd()
        if (trimmed.isEmpty()) result.removeAt(result.size - 1) else result[result.size - 1] = last.copy(text = trimmed)
        return result
    }

    private fun normalize(text: String): String =
        text.replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
}
