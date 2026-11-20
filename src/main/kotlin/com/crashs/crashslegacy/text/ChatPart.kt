package com.crashs.crashslegacy.text

/** One styled run of a chat component, flattened for logging. */
data class ChatPart(
    val text: String,
    val color: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underlined: Boolean = false,
    val strikethrough: Boolean = false,
    val obfuscated: Boolean = false,
    val click: String? = null,
    val hover: String? = null,
)
