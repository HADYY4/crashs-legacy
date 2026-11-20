package com.crashs.crashslegacy.chat

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import com.crashs.crashslegacy.CoinsSession
import com.crashs.crashslegacy.CrashsLegacyMod
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager
import com.crashs.crashslegacy.feature.broadcast.BroadcastManager
import com.crashs.crashslegacy.feature.coins.CoinsParser
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerState
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerStyledParser
import com.crashs.crashslegacy.feature.killfeed.ComboGroup
import com.crashs.crashslegacy.feature.killfeed.CrashsLegacySoundPlayer
import com.crashs.crashslegacy.feature.killfeed.KillFeedState
import com.crashs.crashslegacy.feature.killfeed.KillRewardParser
import com.crashs.crashslegacy.feature.killfeed.PostComboMode
import com.crashs.crashslegacy.feature.killfeed.PostComboSound

/**
 * One incoming chat / action-bar message, shared by every consumer.
 *
 * [plain] is flattened once here so broadcast parsing, the kill parser and the diagnostics all read
 * the same string instead of calling `Component.string` repeatedly.
 */
data class ChatEvent(
    val text: Component,
    val plain: String,
    val overlay: Boolean,
    val receivedAt: Long,
)

/**
 * The single entry point for incoming chat.
 *
 * Duplicate deliveries are handled here (one key per packet), then the event is passed through the
 * ordered handler chain. The aggregate decision is returned to the mixin, which cancels the packet
 * so vanilla does not draw the line.
 */
object ChatIntake {

    private val pipeline = ChatPipeline<ChatEvent>(
        onError = { error -> CrashsLegacyMod.LOGGER.error("[Chat] handler failed", error) },
    )
        .register(BroadcastChatHandler)
        .register(KillFeedChatHandler)
        .register(CoinsChatHandler)
        .register(ItemTrackerChatHandler)

    fun onChat(message: Component, overlay: Boolean, packetKey: Int): Boolean {
        val plain = message.string
        val event = ChatEvent(
            text = message,
            plain = plain,
            overlay = overlay,
            receivedAt = System.currentTimeMillis(),
        )
        val fingerprint = 31 * plain.hashCode() + if (overlay) 1 else 0
        val result = pipeline.dispatch(packetKey.toLong(), fingerprint, event)
        ChatDebug.onMessage(event, result.duplicate)
        return result.consume
    }

    /** Drops the dedupe memory, e.g. on disconnect so a new session cannot collide with old keys. */
    fun reset() = pipeline.clear()
}

/**
 * Captures the warehouse plugin's auto-deposit lines for the Item Tracker HUD.
 *
 * Consuming (hiding the line from chat) is a separate switch, and the message still also goes
 * through the broadcast handler — the user explicitly wanted both to process it.
 */
object ItemTrackerChatHandler : ChatHandler<ChatEvent> {

    override fun handle(event: ChatEvent): Boolean {
        val config = CrashsLegacyConfigManager.config
        if (!config.itemTrackerEnabled) return false
        val entry = ItemTrackerStyledParser.parse(event.text, event.plain, config.itemTrackerPattern)
            ?: return false
        val windowMs = (config.itemTrackerWindowSeconds.coerceIn(1f, 60f) * 1000f).toLong()
        ItemTrackerState.add(entry, event.receivedAt, windowMs)
        return config.itemTrackerIntercept
    }
}

/** Broadcast filtering: decides whether the line stays visible, records it, may show BossBar/sound. */
object BroadcastChatHandler : ChatHandler<ChatEvent> {
    override fun handle(event: ChatEvent): Boolean = BroadcastManager.handleMessage(event.text)
}

/**
 * Kill-feedback parsing plus coin accounting.
 *
 * Coins are recorded here (previously inside `KillFeedState.add`) so `CoinsSession` no longer depends
 * on the kill-feed state, and they are recorded even when the line is hidden by another handler.
 */
/**
 * Coin accounting with its own pattern, independent of the kill feed.
 *
 * It skips lines the kill handler already counted (`context.coinsRecorded`), so a kill-reward line is
 * never counted twice. With the default pattern this reproduces the old behaviour exactly; a custom
 * pattern lets pure coin messages count too.
 */
object CoinsChatHandler : ChatHandler<ChatEvent> {

    override fun handle(event: ChatEvent): Boolean {
        val config = CrashsLegacyConfigManager.config
        // Independent module: only the coins switch matters, not the kill-feedback master or its
        // ActionBar policy.
        if (!config.coinsInfoEnabled) return false

        // Conservative: coins are only counted from kill-reward lines, using the kill pattern.
        // A single pattern cannot cover every server's coin messages, so pure coin lines are
        // deliberately ignored.
        val coins = CoinsParser.parse(event.plain, config.pattern) ?: return false
        CoinsSession.record(coins, event.receivedAt)
        return false
    }
}

object KillFeedChatHandler : ChatHandler<ChatEvent> {

    override fun handle(event: ChatEvent): Boolean {
        val config = CrashsLegacyConfigManager.config
        if (!config.enabled) return false
        if (event.overlay && !config.includeOverlay) return false

        val reward = KillRewardParser.parse(event.plain, config.pattern) ?: return false
        // Coins are counted by CoinsChatHandler, so this module stays independent of the coin switch.
        val comboCount = KillFeedState.add(reward)

        if (config.sound) {
            val group = ComboGroup.byName(config.comboGroup)
            val postMode = PostComboMode.byName(config.postMode)
            val specific = PostComboSound.byName(config.specificSound)
            Minecraft.getInstance().execute {
                CrashsLegacySoundPlayer.play(
                    comboCount = comboCount,
                    group = group,
                    postMode = postMode,
                    specific = specific,
                    volume = config.soundVolume,
                    pitch = config.soundPitch,
                )
            }
        }

        if (config.intercept) {
            CrashsLegacyMod.LOGGER.debug(
                "Intercepted reward: {} coins from {}",
                KillRewardParser.formatCoins(reward.coins),
                reward.source,
            )
        }
        return config.intercept
    }
}
