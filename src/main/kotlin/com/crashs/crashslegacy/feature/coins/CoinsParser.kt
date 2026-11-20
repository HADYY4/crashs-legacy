package com.crashs.crashslegacy.feature.coins
import com.crashs.crashslegacy.feature.killfeed.KillRewardParser

/**
 * Extracts a coin amount from a chat line using a configurable pattern.
 *
 * The default pattern is the kill-reward line (group 1 = coins), so out of the box the coins HUD
 * counts exactly what it counted before. Point it at another server line (for example a pure
 * "获得 N 金币" message) and the coins handler picks that up instead, without touching the kill feed.
 *
 * Pure logic, pinned down by `CoinsParserTest`.
 */
object CoinsParser {

    /** Default = the kill-reward pattern; the coins module only counts kill lines by design. */
    const val DEFAULT_PATTERN = KillRewardParser.DEFAULT_PATTERN

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

    /** @return the coin amount from capture group 1, or null when the line does not match. */
    fun parse(rawText: String, patternText: String): Double? {
        val normalized = normalize(rawText)
        if (normalized.isEmpty()) return null
        val regex = regexFor(patternText) ?: return null
        // `find` so a custom pattern can match part of a line; the default pattern is anchored
        // with ^...$ and therefore still requires the whole kill-reward line.
        val match = regex.find(normalized) ?: return null
        val rawGroup = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (rawGroup.isEmpty()) return null
        return rawGroup.replace(",", "").toDoubleOrNull()
    }

    private fun normalize(text: String): String =
        text.replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
}
