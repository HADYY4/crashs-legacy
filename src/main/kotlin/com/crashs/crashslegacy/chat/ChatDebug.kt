package com.crashs.crashslegacy.chat

import net.minecraft.network.chat.Component
import com.crashs.crashslegacy.CrashsLegacyMod
import com.crashs.crashslegacy.text.ChatPart
import com.crashs.crashslegacy.text.TextComponentFlatten

/**
 * Chat diagnostics for future chat features: prints every incoming chat / action-bar message to the
 * log, split into its styled runs, so prefixes, ranks, colors and click events are visible while
 * developing (instead of only seeing the flattened text).
 *
 * Toggled with `/crashs debug chat`; off by default, so a normal session logs nothing.
 */
object ChatDebug {

    @Volatile
    var enabled: Boolean = false
        private set

    private const val MAX_PARTS = 16

    /** @return the new state. */
    fun toggle(): Boolean {
        enabled = !enabled
        return enabled
    }

    /**
     * Called for every chat / action-bar delivery, before any config gate, while debugging.
     * [duplicate] marks a second delivery of the same packet that the dedupe probe caught.
     */
    fun onMessage(event: ChatEvent, duplicate: Boolean = false) {
        if (!enabled) return
        val channel = if (event.overlay) "actionbar" else "chat"
        val parts = runCatching { flatten(event.text) }.getOrDefault(emptyList())
        val text = event.plain
        log(ChatDebugFormat.header(channel, text.length, parts.size, text, duplicate))
        parts.take(MAX_PARTS).forEachIndexed { index, part ->
            log(
                "[ChatDebug]   [$index] \"${ChatDebugFormat.escape(part.text, ChatDebugFormat.MAX_PART_TEXT)}\"" +
                    ChatDebugFormat.describeStyles(part),
            )
        }
        if (parts.size > MAX_PARTS) {
            log("[ChatDebug]   ... ${parts.size - MAX_PARTS} more parts")
        }
    }

    /** Flattens the component tree into styled runs, with the style inherited like the renderer does. */
    fun flatten(message: Component): List<ChatPart> = TextComponentFlatten.flatten(message)

    private fun log(line: String) {
        CrashsLegacyMod.LOGGER.info(line)
    }
}
