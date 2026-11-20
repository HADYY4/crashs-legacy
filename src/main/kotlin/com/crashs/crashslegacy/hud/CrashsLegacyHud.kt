package com.crashs.crashslegacy.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.crashs.crashslegacy.config.CrashsLegacyConfig
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager
import com.crashs.crashslegacy.feature.killfeed.KillFeedEntry
import com.crashs.crashslegacy.feature.killfeed.KillFeedSnapshot
import com.crashs.crashslegacy.feature.killfeed.KillFeedState
import com.crashs.crashslegacy.feature.killfeed.KillRewardParser
import com.crashs.crashslegacy.ui.CrashsHudEditorScreen

/**
 * Vanilla GuiGraphics HUD.
 */
object CrashsLegacyHud {

    private const val PADDING = 4f
    private const val HEADER_GAP = 3f
    private const val PROGRESS_GAP = 3f
    private const val MIN_WIDTH = 86f
    private const val AMOUNT_SCROLL_MS = 600f
    private const val MOB_REVEAL_MS = 260f

    private var displayedCoins = 0.0
    private var displayedKills = 0f
    private var lastTotalCoins = 0.0
    private var lastTotalKills = 0

    private var widthGeneration = Long.MIN_VALUE
    private var widthLayoutKey = Int.MIN_VALUE
    private var cachedContentWidth = 0f

    fun render(context: GuiGraphics) {
        val client = Minecraft.getInstance()
        val config = CrashsLegacyConfigManager.config
        if (!config.enabled || !config.hud) return
        // Keep drawing while the HUD editor is open: the editor draws its frame on top of the real
        // HUD, so hiding it there would leave nothing to drag.
        val editing = client.screen is CrashsHudEditorScreen
        if (client.player == null || client.options.hideGui) return
        if (client.screen != null && !editing && !config.hudAlwaysVisible) return

        val now = System.currentTimeMillis()
        KillFeedState.update(
            now = now,
            entryDurationMs = (config.entryDurationSeconds * 1000f).toLong(),
            maxRows = config.maxRows,
            statsWindowMs = (config.statsWindowSeconds * 1000f).toLong(),
            timedStatsWindow = config.statsMode.equals("TIMED", ignoreCase = true),
        )
        val snapshot = KillFeedState.snapshot()
        val headerStyle = config.headerStyle.uppercase()
        if (headerStyle == "HIDDEN" && snapshot.entries.isEmpty()) return

        val font = client.font
        animateTotals(snapshot)

        val layout = computeLayout(font, headerStyle, config, snapshot)
        val contentWidth = layout.contentWidth
        val height = layout.height
        val headerVisible = layout.headerVisible
        val headerHeight = layout.headerHeight
        val progressHeight = layout.progressHeight
        val rowHeight = layout.rowHeight
        val rowSpacing = layout.rowSpacing

        val screenWidth = context.guiWidth().toFloat()
        val screenHeight = context.guiHeight().toFloat()
        val bounds = HudPlacement.bounds(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            contentWidth = contentWidth,
            contentHeight = height,
            scale = layout.scale,
            percentX = config.hudX,
            percentY = config.hudY,
            right = config.hudRight,
        )
        val x = bounds.x
        val y = bounds.y

        context.pose().pushMatrix()
        context.pose().translate(x, y)
        context.pose().scale(layout.scale, layout.scale)

        if (config.background) {
            roundedRect(context, 0f, 0f, contentWidth, height, 3f, config.backgroundColor)
        }
        if (config.hudOutline) {
            roundedOutline(context, 0f, 0f, contentWidth, height, config.outlineColor)
        }

        var cursorY = PADDING
        if (headerVisible) {
            drawHeader(context, font, headerStyle, contentWidth, config)
            cursorY += headerHeight
        }

        drawProgress(context, snapshot, now, contentWidth, cursorY, config)
        cursorY += progressHeight

        val rowPitch = rowHeight + rowSpacing
        val pendingId = snapshot.entries
            .filter { !it.isFading }
            .takeIf { it.size >= config.maxRows }
            ?.lastOrNull()
            ?.id

        for (entry in snapshot.entries) {
            val age = now - entry.createdAt
            var alpha = entryAlpha(entry, now)
            if (entry.evicted || entry.id == pendingId) {
                alpha *= config.pendingEvictionAlpha.coerceIn(0.1f, 1f)
            }
            val insertOffset = if (age >= 160) 0f else (1f - easeOutCubic(age / 160f)) * 4f
            val pushOffset = if (!config.entryPushAnimation) {
                0f
            } else {
                entry.shiftedAt?.let { shiftedAt ->
                    val progress = easeOutCubic(((now - shiftedAt) / 180f).coerceIn(0f, 1f))
                    -(1f - progress) * rowPitch
                } ?: 0f
            }
            if (alpha > 0.01f) {
                drawRow(context, font, entry, contentWidth, cursorY + pushOffset + insertOffset, rowHeight, alpha, now, config)
            }
            cursorY += rowPitch
        }

        context.pose().popMatrix()
    }

    /** Layout of one HUD frame; shared by rendering and by the editor's measure(). */
    private data class Layout(
        val contentWidth: Float,
        val height: Float,
        val headerVisible: Boolean,
        val headerHeight: Float,
        val progressHeight: Float,
        val rowHeight: Float,
        val rowSpacing: Float,
        val scale: Float,
    )

    private fun computeLayout(
        font: Font,
        headerStyle: String,
        config: CrashsLegacyConfig,
        snapshot: KillFeedSnapshot,
    ): Layout {
        val headerVisible = headerStyle != "HIDDEN" && when (headerStyle) {
            "FULL" -> config.showCoins || config.showKills
            "COMPACT" -> config.showCoins || config.showKills
            "COINS_ONLY" -> config.showCoins
            "KILLS_ONLY" -> config.showKills
            else -> true
        }
        val targetContentWidth = max(
            MIN_WIDTH,
            max(
                if (headerVisible) headerWidth(font, headerStyle, config, snapshot) else 0f,
                snapshot.entries.maxOfOrNull { rowWidth(font, it, config) } ?: 0f,
            ) + PADDING * 2,
        )
        val layoutKey = widthLayoutKey(config, headerStyle)
        if (snapshot.generation != widthGeneration || layoutKey != widthLayoutKey) {
            widthGeneration = snapshot.generation
            widthLayoutKey = layoutKey
            cachedContentWidth = targetContentWidth
        } else {
            cachedContentWidth = max(cachedContentWidth, targetContentWidth)
        }
        val contentWidth = cachedContentWidth
        val headerHeight = if (headerVisible) font.lineHeight + HEADER_GAP else 0f
        val progressHeight = if (config.showProgress) config.progressHeight.coerceIn(1f, 8f) + PROGRESS_GAP else 2f
        val rowHeight = max(12f, font.lineHeight + 2f)
        val rowSpacing = config.rowSpacing.coerceIn(0f, 8f)
        val activeCount = snapshot.entries.count { !it.isFading }
        val reserveEvictionRow = activeCount >= config.maxRows.coerceIn(1, 20)
        val layoutRows = if (reserveEvictionRow) {
            max(config.maxRows.coerceIn(1, 20) + 1, snapshot.entries.size)
        } else {
            snapshot.entries.size
        }
        val listHeight = if (layoutRows == 0) 0f else
            layoutRows * rowHeight + (layoutRows - 1) * rowSpacing
        val height = PADDING * 2 + headerHeight + progressHeight + listHeight
        return Layout(
            contentWidth = contentWidth,
            height = height,
            headerVisible = headerVisible,
            headerHeight = headerHeight,
            progressHeight = progressHeight,
            rowHeight = rowHeight,
            rowSpacing = rowSpacing,
            scale = config.hudScale.coerceIn(0.5f, 2.5f),
        )
    }

    /**
     * On-screen rectangle the HUD would occupy right now, without drawing anything.
     *
     * The HUD editor uses it for the drag box, the same way [render] places the real HUD, so the
     * box can never drift from the visuals.
     */
    fun measure(screenWidth: Float, screenHeight: Float, config: CrashsLegacyConfig): HudPlacement.Bounds? {
        if (!config.enabled || !config.hud) return null
        val client = Minecraft.getInstance()
        val font = client.font ?: return null
        val snapshot = KillFeedState.snapshot()
        val headerStyle = config.headerStyle.uppercase()
        val layout = computeLayout(font, headerStyle, config, snapshot)
        return HudPlacement.bounds(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            contentWidth = layout.contentWidth,
            contentHeight = layout.height,
            scale = layout.scale,
            percentX = config.hudX,
            percentY = config.hudY,
            right = config.hudRight,
        )
    }

    private fun drawHeader(
        context: GuiGraphics,
        font: net.minecraft.client.gui.Font,
        style: String,
        width: Float,
        config: CrashsLegacyConfig,
    ) {
        val coins = "${formatNumber(displayedCoins, config)} Coins"
        val kills = "${formatNumber(displayedKills.roundToInt().toDouble(), config)} Kills"
        val color = config.headerColor

        when (style) {
            "COMPACT" -> {
                val text = when {
                    config.showCoins && config.showKills ->
                        "${formatShort(displayedCoins)}c  ${formatShort(displayedKills.roundToInt().toDouble())}k"
                    config.showCoins -> "${formatShort(displayedCoins)}c"
                    config.showKills -> "${formatShort(displayedKills.roundToInt().toDouble())}k"
                    else -> ""
                }
                if (text.isNotEmpty()) {
                    context.drawString(font, Component.literal(text), PADDING.toInt(), PADDING.toInt(), color, config.hudTextShadow)
                }
            }

            "COINS_ONLY" -> if (config.showCoins) {
                context.drawString(font, Component.literal(coins), PADDING.toInt(), PADDING.toInt(), color, config.hudTextShadow)
            }

            "KILLS_ONLY" -> if (config.showKills) {
                context.drawString(font, Component.literal(kills), PADDING.toInt(), PADDING.toInt(), color, config.hudTextShadow)
            }

            "HIDDEN" -> Unit

            else -> {
                if (config.showCoins) {
                    context.drawString(font, Component.literal(coins), PADDING.toInt(), PADDING.toInt(), color, config.hudTextShadow)
                }
                if (config.showKills) {
                    val killX = width - PADDING - font.width(kills)
                    context.drawString(font, Component.literal(kills), killX.roundToInt(), PADDING.toInt(), color, config.hudTextShadow)
                }
            }
        }
    }

    private fun drawProgress(
        context: GuiGraphics,
        snapshot: KillFeedSnapshot,
        now: Long,
        width: Float,
        y: Float,
        config: CrashsLegacyConfig,
    ) {
        if (!config.showProgress) return
        val x = PADDING
        val barWidth = width - PADDING * 2
        val barHeight = config.progressHeight.coerceIn(1f, 8f)
        val timed = snapshot.windowStartedAt > 0L && snapshot.windowEndsAt > snapshot.windowStartedAt

        if (timed) {
            val fill = ((snapshot.windowEndsAt - now).toFloat() /
                (snapshot.windowEndsAt - snapshot.windowStartedAt).toFloat()).coerceIn(0f, 1f)
            context.fill(
                x.toInt(),
                y.toInt(),
                (x + barWidth).toInt(),
                (y + barHeight).toInt(),
                config.progressBackground,
            )
            if (fill > 0f) {
                context.pose().pushMatrix()
                context.pose().translate(x, y)
                context.pose().scale(fill, 1f)
                context.fill(
                    0,
                    0,
                    barWidth.toInt(),
                    barHeight.toInt(),
                    config.progressColor,
                )
                context.pose().popMatrix()
            }
        } else {
            context.fill(x.toInt(), y.toInt(), (x + barWidth).toInt(), (y + 1f).toInt(), config.dividerColor)
        }
    }

    private fun drawRow(
        context: GuiGraphics,
        font: net.minecraft.client.gui.Font,
        entry: KillFeedEntry,
        width: Float,
        y: Float,
        rowHeight: Float,
        alpha: Float,
        now: Long,
        config: CrashsLegacyConfig,
    ) {
        val reward = entry.reward
        val age = System.currentTimeMillis() - entry.createdAt
        val source = buildString {
            if (config.showLevel && reward.level != null) {
                append("Lv.")
                append(reward.level)
                append(' ')
            }
            append(reward.mob)
        }
        val coins = if (config.amountScroll && age < AMOUNT_SCROLL_MS) {
            reward.coins * easeOutCubic(age / AMOUNT_SCROLL_MS)
        } else {
            reward.coins
        }
        val amount = "+${KillRewardParser.formatCoins(coins)}"
        val textY = (y + (rowHeight - font.lineHeight) / 2f).roundToInt()

        if (config.showSource) {
            drawRevealedSource(context, font, entry, textY, alpha, now, config)
        }
        if (config.showAmount) {
            val amountX = (width - PADDING - font.width(amount)).roundToInt()
            context.drawString(
                font,
                Component.literal(amount),
                amountX,
                textY,
                withAlpha(config.coinColor, alpha),
                true,
            )
        }
    }

    private fun drawRevealedSource(
        context: GuiGraphics,
        font: net.minecraft.client.gui.Font,
        entry: KillFeedEntry,
        textY: Int,
        alpha: Float,
        now: Long,
        config: CrashsLegacyConfig,
    ) {
        val reward = entry.reward
        val segments = mutableListOf<Pair<String, Int>>()
        if (config.showLevel && reward.level != null) {
            segments += "Lv.${reward.level} " to config.levelColor
        }
        segments += reward.mob to config.sourceColor
        if (segments.isEmpty()) return

        val fullText = segments.joinToString("") { it.first }
        if (fullText.isEmpty()) return
        val fullWidth = font.width(fullText)
        val reveal = if (!config.textRevealAnimation) {
            1f
        } else {
            easeOutCubic(((now - entry.createdAt) / MOB_REVEAL_MS).coerceIn(0f, 1f))
        }
        val visibleCount = (fullText.length * reveal).toInt().coerceIn(0, fullText.length)
        if (visibleCount <= 0) return
        val visibleStart = fullText.length - visibleCount
        val visibleText = fullText.substring(visibleStart)
        val visibleStartX = (PADDING + 2f) + fullWidth - font.width(visibleText)

        var segmentStart = 0
        for ((segmentText, color) in segments) {
            val segmentEnd = segmentStart + segmentText.length
            val from = maxOf(visibleStart, segmentStart)
            val to = segmentEnd
            if (from < to) {
                val part = segmentText.substring(from - segmentStart, to - segmentStart)
                val partX = visibleStartX + font.width(fullText.substring(visibleStart, from))
                context.drawString(
                    font,
                    Component.literal(part),
                    partX.roundToInt(),
                    textY,
                    withAlpha(color, alpha),
                    config.hudTextShadow,
                )
            }
            segmentStart = segmentEnd
        }
    }

    private fun headerWidth(
        font: net.minecraft.client.gui.Font,
        style: String,
        config: CrashsLegacyConfig,
        snapshot: KillFeedSnapshot,
    ): Float {
        val coins = "${formatNumber(snapshot.totalCoins, config)} Coins"
        val kills = "${formatNumber(snapshot.totalKills.toDouble(), config)} Kills"
        return when (style) {
            "HIDDEN" -> 0f
            "COMPACT" -> font.width(
                "${formatShort(snapshot.totalCoins)}c  ${formatShort(snapshot.totalKills.toDouble())}k",
            ).toFloat()
            "COINS_ONLY" -> if (config.showCoins) font.width(coins).toFloat() else 0f
            "KILLS_ONLY" -> if (config.showKills) font.width(kills).toFloat() else 0f
            else -> {
                var width = 0f
                if (config.showCoins) width += font.width(coins).toFloat()
                if (config.showKills) width += font.width(kills).toFloat() + 24f
                width
            }
        }
    }

    private fun rowWidth(
        font: net.minecraft.client.gui.Font,
        entry: KillFeedEntry,
        config: CrashsLegacyConfig,
    ): Float {
        val reward = entry.reward
        val source = buildString {
            if (config.showLevel && reward.level != null) {
                append("Lv.")
                append(reward.level)
                append(' ')
            }
            append(reward.mob)
        }
        val sourceWidth = if (config.showSource) font.width(source).toFloat() else 0f
        val amountWidth = if (config.showAmount) {
            font.width("+${KillRewardParser.formatCoins(reward.coins)}").toFloat()
        } else {
            0f
        }
        return sourceWidth + amountWidth + 12f
    }

    private fun widthLayoutKey(config: CrashsLegacyConfig, headerStyle: String): Int = listOf(
        config.hudScale,
        headerStyle,
        config.showCoins,
        config.showKills,
        config.showSource,
        config.showLevel,
        config.showAmount,
        config.numberFormat,
        config.maxRows,
        config.rowSpacing,
        config.entryDurationSeconds,
        config.statsWindowSeconds,
    ).hashCode()

    private fun animateTotals(snapshot: KillFeedSnapshot) {
        if (snapshot.totalKills < lastTotalKills || snapshot.totalCoins < lastTotalCoins) {
            displayedCoins = snapshot.totalCoins
            displayedKills = snapshot.totalKills.toFloat()
        } else {
            displayedCoins += (snapshot.totalCoins - displayedCoins) * 0.22
            displayedKills += (snapshot.totalKills - displayedKills) * 0.22f
        }
        lastTotalCoins = snapshot.totalCoins
        lastTotalKills = snapshot.totalKills
    }

    private fun entryAlpha(entry: KillFeedEntry, now: Long): Float {
        val insert = easeOutCubic(((now - entry.createdAt).coerceAtLeast(0L) / 160f).coerceIn(0f, 1f))
        val fade = entry.fadeStartedAt?.let {
            (1f - (now - it).toFloat() / KillFeedState.FADE_DURATION_MS).coerceIn(0f, 1f)
        } ?: 1f
        return min(insert, fade)
    }

    private fun easeOutCubic(value: Float): Float {
        val x = value.coerceIn(0f, 1f) - 1f
        return x * x * x + 1f
    }

    private fun formatNumber(value: Double, config: CrashsLegacyConfig): String = when (config.numberFormat.uppercase()) {
        "COMMA" -> "%,.0f".format(value)
        "SHORT" -> formatShort(value)
        else -> KillRewardParser.formatCoins(value)
    }

    private fun formatShort(value: Double): String = when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000)
        value >= 1_000 -> "%.1fK".format(value / 1_000)
        else -> value.toInt().toString()
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = ((color ushr 24 and 0xFF) * alpha.coerceIn(0f, 1f)).toInt()
        return (a shl 24) or (color and 0xFFFFFF)
    }

    private fun roundedRect(
        context: GuiGraphics,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        radius: Float,
        color: Int,
    ) {
        val r = radius.coerceAtMost(min(x2 - x1, y2 - y1) / 2f)
        context.fill((x1 + r).toInt(), y1.toInt(), (x2 - r).toInt(), y2.toInt(), color)
        context.fill(x1.toInt(), (y1 + r).toInt(), x2.toInt(), (y2 - r).toInt(), color)
    }

    private fun roundedOutline(
        context: GuiGraphics,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int,
    ) {
        context.fill(x1.toInt(), y1.toInt(), x2.toInt(), (y1 + 1f).toInt(), color)
        context.fill(x1.toInt(), (y2 - 1f).toInt(), x2.toInt(), y2.toInt(), color)
        context.fill(x1.toInt(), y1.toInt(), (x1 + 1f).toInt(), y2.toInt(), color)
        context.fill((x2 - 1f).toInt(), y1.toInt(), x2.toInt(), y2.toInt(), color)
    }
}
