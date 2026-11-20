package com.crashs.crashslegacy.serverdamage

import net.minecraft.network.chat.Component

/**
 * The ten elemental damage symbols used by server-side TextDisplay damage numbers.
 */
enum class DamageElement(val symbol: String, val displayName: String) {
    METAL("\u25c6", "金"),
    WOOD("\ud83c\udf3f", "木"),
    WATER("\ud83d\udca7", "水"),
    FIRE("\ud83d\udd25", "火"),
    EARTH("\u26f0", "土"),
    WIND("\ud83c\udf2a", "风"),
    THUNDER("\u26a1", "雷"),
    LIGHT("\u2600", "光"),
    DARK("\u263e", "暗"),
    PHYSICAL("\u2694", "物理"),
    ;

    companion object {
        private val bySymbol = entries.associateBy { it.symbol }
        private val numberPattern = Regex("""^\d+(?:\.\d+)?$""")
        private val variationSelectors = charArrayOf('\uFE0E', '\uFE0F')

        fun parse(text: Component): DamageElement? = parse(text.string)

        fun parse(text: String): DamageElement? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null

            val symbolCodePoint = trimmed.codePointAt(0)
            val symbol = String(Character.toChars(symbolCodePoint))
            val element = bySymbol[symbol] ?: return null

            val rest = trimmed
                .substring(Character.charCount(symbolCodePoint))
                .trim()
                .trim(*variationSelectors)
                .trim()

            return element.takeIf { numberPattern.matches(rest) }
        }
    }
}
