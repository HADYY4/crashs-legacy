package com.crashs.crashslegacy.chat
import com.crashs.crashslegacy.text.ChatPart

/** One styled run of a chat component, flattened for logging. */

/**
 * Pure formatting for the chat diagnostics: no Minecraft types, so the log layout is unit tested
 * while [ChatDebug] only supplies the component walks.
 */
object ChatDebugFormat {

    const val MAX_PART_TEXT = 256
    const val MAX_HEADER_TEXT = 512

    /** One-line summary: channel, duplicate flag, length, part count and the flattened text. */
    fun header(channel: String, length: Int, parts: Int, text: String, duplicate: Boolean): String =
        "[ChatDebug] $channel dup=$duplicate len=$length parts=$parts text=\"${escape(text, MAX_HEADER_TEXT)}\""

    /** `color=#ff5555 bold click=...` — only the attributes that are actually set. */
    fun describeStyles(part: ChatPart): String {
        val styles = ArrayList<String>(8)
        part.color?.let { styles += "color=$it" }
        if (part.bold) styles += "bold"
        if (part.italic) styles += "italic"
        if (part.underlined) styles += "underlined"
        if (part.strikethrough) styles += "strikethrough"
        if (part.obfuscated) styles += "obfuscated"
        part.click?.let { styles += "click=${escape(it, MAX_PART_TEXT)}" }
        part.hover?.let { styles += "hover=${escape(it, MAX_PART_TEXT)}" }
        return if (styles.isEmpty()) "" else " " + styles.joinToString(" ")
    }

    /** Escapes quotes and control characters so one message stays on one log line. */
    fun escape(value: String, limit: Int): String {
        val builder = StringBuilder(minOf(value.length, limit) + 8)
        for (char in value) {
            if (builder.length >= limit) {
                builder.append('…')
                break
            }
            when (char) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> if (char.code < 0x20) {
                    builder.append("\\u").append("%04x".format(char.code))
                } else {
                    builder.append(char)
                }
            }
        }
        return builder.toString()
    }
}
