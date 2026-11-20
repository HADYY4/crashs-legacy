package com.crashs.crashslegacy.text

import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.Component
import java.util.Optional
import kotlin.math.max
import kotlin.math.min

/** Flattens a chat component into styled runs, with the style inherited like the renderer does. */
object TextComponentFlatten {

    fun flatten(message: Component): List<ChatPart> {
        val parts = ArrayList<ChatPart>()
        message.visit(
            FormattedText.StyledContentConsumer<Unit> { style, asString ->
                parts += ChatPart(
                    text = asString,
                    color = style.color?.serialize(),
                    bold = style.isBold,
                    italic = style.isItalic,
                    underlined = style.isUnderlined,
                    strikethrough = style.isStrikethrough,
                    obfuscated = style.isObfuscated,
                    click = style.clickEvent?.toString(),
                    hover = style.hoverEvent?.toString(),
                )
                Optional.empty()
            },
            Style.EMPTY,
        )
        return parts
    }

    /** Maps a character range of the flattened text back onto the styled runs. */
    fun slice(parts: List<ChatPart>, from: Int, to: Int): List<ChatPart> {
        if (to <= from) return emptyList()
        val result = ArrayList<ChatPart>(parts.size)
        var offset = 0
        for (part in parts) {
            val start = offset
            val end = offset + part.text.length
            offset = end
            val sliceStart = max(from, start)
            val sliceEnd = min(to, end)
            if (sliceEnd > sliceStart) {
                result += part.copy(text = part.text.substring(sliceStart - start, sliceEnd - start))
            }
        }
        return result
    }
}
