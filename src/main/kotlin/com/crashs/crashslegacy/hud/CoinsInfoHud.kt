package com.crashs.crashslegacy.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import com.crashs.crashslegacy.CoinsSession
import com.crashs.crashslegacy.CoinsSessionSnapshot
import com.crashs.crashslegacy.config.CrashsLegacyConfig
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager
import com.crashs.crashslegacy.feature.killfeed.KillRewardParser
import com.crashs.crashslegacy.ui.CrashsHudEditorScreen

object CoinsInfoHud {

    private data class Row(val left: Component, val right: Component?, val leftColor: Int, val rightColor: Int)

    private var displayedTotal = 0.0
    private var displayedAverage = 0.0
    private var displayGeneration = Long.MIN_VALUE
    private var displayUnit: String? = null

    private var widthGeneration = Long.MIN_VALUE
    private var widthLayoutKey = Int.MIN_VALUE
    private var cachedWidth = 0f

    fun render(context: GuiGraphics) {
        val client = Minecraft.getInstance()
        val config = CrashsLegacyConfigManager.config
        if (!config.coinsInfoEnabled) return
        // Same editor exception as the kill-feedback HUD: keep drawing behind the HUD editor.
        val editing = client.screen is CrashsHudEditorScreen
        if (client.player == null || client.options.hideGui) return
        if (client.screen != null && !editing && !config.coinsInfoAlwaysVisible) return

        val snapshot = CoinsSession.snapshot()
        animate(snapshot, config)
        val rows = rows(snapshot, config, animated = true)
        if (rows.isEmpty()) return

        val font = client.font
        val scale = config.coinsInfoScale.coerceIn(0.5f, 2.5f)
        val width = contentWidth(font, snapshot, config)
        val lineHeight = font.lineHeight.toFloat()
        val gap = if (rows.size > 1) 2f else 0f
        val headerVisible = config.coinsInfoShowSessionTime || config.coinsInfoShowKills
        val headerHeight = if (headerVisible) font.lineHeight + 2f else 0f
        val dividerHeight = if (headerVisible) config.progressHeight.coerceIn(1f, 8f) + 3f else 0f
        val height = rows.size * lineHeight + gap * (rows.size - 1) +
            headerHeight + dividerHeight + PADDING * 2

        val screenWidth = context.guiWidth().toFloat()
        val screenHeight = context.guiHeight().toFloat()
        val percentX = config.coinsInfoX.coerceIn(0f, 100f)
        val percentY = config.coinsInfoY.coerceIn(0f, 100f)
        val x = if (config.coinsInfoRight) {
            screenWidth - width * scale - screenWidth * percentX / 100f
        } else {
            screenWidth * percentX / 100f
        }
        val y = screenHeight * percentY / 100f

        context.pose().pushMatrix()
        context.pose().translate(x, y)
        context.pose().scale(scale, scale)

        if (config.coinsInfoBackground) {
            context.fill(0, 0, width.toInt(), height.toInt(), config.coinsInfoBackgroundColor)
        }
        if (config.coinsInfoOutline) {
            outline(context, 0f, 0f, width, height, config.coinsInfoOutlineColor)
        }

        var cursorY = PADDING
        if (headerVisible) {
            if (config.coinsInfoShowSessionTime) {
                context.drawString(
                    font,
                    Component.literal(formatDuration(snapshot.elapsedMs)),
                    PADDING.toInt(),
                    cursorY.toInt(),
                    config.coinsInfoSessionTimeColor,
                    config.coinsInfoTextShadow,
                )
            }
            if (config.coinsInfoShowKills) {
                val kills = Component.literal("${snapshot.totalKills} Kills")
                val killsWidth = font.width(kills).toFloat()
                context.drawString(
                    font,
                    kills,
                    (width - PADDING - killsWidth).toInt(),
                    cursorY.toInt(),
                    config.coinsInfoKillsColor,
                    config.coinsInfoTextShadow,
                )
            }
            cursorY += headerHeight
            val barHeight = config.progressHeight.coerceIn(1f, 8f).toInt()
            context.fill(PADDING.toInt(), cursorY.toInt(), (width - PADDING).toInt(), cursorY.toInt() + barHeight, config.coinsInfoDividerBackground)
            context.fill(PADDING.toInt(), cursorY.toInt(), (width - PADDING).toInt(), cursorY.toInt() + 1, config.coinsInfoDividerColor)
            cursorY += dividerHeight
        }

        for ((index, row) in rows.withIndex()) {
            context.drawString(font, row.left, PADDING.toInt(), cursorY.toInt(), row.leftColor, config.coinsInfoTextShadow)
            row.right?.let { right ->
                val rightWidth = font.width(right).toFloat()
                context.drawString(
                    font,
                    right,
                    (width - PADDING - rightWidth).toInt(),
                    cursorY.toInt(),
                    row.rightColor,
                    config.coinsInfoTextShadow,
                )
            }
            cursorY += lineHeight
            if (index < rows.size - 1) cursorY += gap
        }

        context.pose().popMatrix()
    }

    private fun animate(snapshot: CoinsSessionSnapshot, config: CrashsLegacyConfig) {
        val targetTotal = snapshot.totalCoins
        val targetAverage = average(snapshot, config)
        if (!config.coinsInfoScroll ||
            snapshot.generation != displayGeneration ||
            config.coinsInfoAverageUnit != displayUnit
        ) {
            displayedTotal = targetTotal
            displayedAverage = targetAverage
            displayGeneration = snapshot.generation
            displayUnit = config.coinsInfoAverageUnit
            return
        }
        displayedTotal += (targetTotal - displayedTotal) * 0.22
        displayedAverage += (targetAverage - displayedAverage) * 0.22
    }

    /**
     * On-screen rectangle the coins HUD would occupy right now, without drawing anything.
     * Mirrors the sizing in [render] so the editor box can never drift from the visuals.
     */
    fun measure(screenWidth: Float, screenHeight: Float, config: CrashsLegacyConfig): HudPlacement.Bounds? {
        if (!config.coinsInfoEnabled) return null
        val client = Minecraft.getInstance()
        val font = client.font ?: return null
        val snapshot = CoinsSession.snapshot()
        val rows = rows(snapshot, config, animated = false)
        val layoutRows = rows.size.coerceAtLeast(1)
        val width = contentWidth(font, snapshot, config)
        val lineHeight = font.lineHeight.toFloat()
        val gap = if (layoutRows > 1) 2f else 0f
        val headerVisible = config.coinsInfoShowSessionTime || config.coinsInfoShowKills
        val headerHeight = if (headerVisible) font.lineHeight + 2f else 0f
        val dividerHeight = if (headerVisible) config.progressHeight.coerceIn(1f, 8f) + 3f else 0f
        val height = layoutRows * lineHeight + gap * (layoutRows - 1) +
            headerHeight + dividerHeight + PADDING * 2
        return HudPlacement.bounds(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            contentWidth = width,
            contentHeight = height,
            scale = config.coinsInfoScale.coerceIn(0.5f, 2.5f),
            percentX = config.coinsInfoX,
            percentY = config.coinsInfoY,
            right = config.coinsInfoRight,
        )
    }

    private fun contentWidth(
        font: net.minecraft.client.gui.Font,
        snapshot: CoinsSessionSnapshot,
        config: CrashsLegacyConfig,
    ): Float {
        val rows = rows(snapshot, config, animated = false)
        val headerWidth = if (config.coinsInfoShowSessionTime || config.coinsInfoShowKills) {
            val time = if (config.coinsInfoShowSessionTime) {
                font.width(Component.literal(formatDuration(snapshot.elapsedMs))).toFloat()
            } else 0f
            val kills = if (config.coinsInfoShowKills) {
                font.width(Component.literal("${snapshot.totalKills} Kills")).toFloat()
            } else 0f
            time + (if (time > 0f && kills > 0f) 8f else 0f) + kills
        } else 0f
        val target = max(
            MIN_WIDTH,
            max(headerWidth, rows.maxOfOrNull { rowWidth(font, it) } ?: 0f) + PADDING * 2,
        )
        val key = layoutKey(config)
        if (snapshot.generation != widthGeneration || key != widthLayoutKey) {
            widthGeneration = snapshot.generation
            widthLayoutKey = key
            cachedWidth = target
        } else {
            cachedWidth = max(cachedWidth, target)
        }
        return cachedWidth
    }

    private fun rows(
        snapshot: CoinsSessionSnapshot,
        config: CrashsLegacyConfig,
        animated: Boolean,
    ): List<Row> {
        val totalValue = if (animated) displayedTotal else snapshot.totalCoins
        val avgValue = if (animated) displayedAverage else average(snapshot, config)
        val total = formatNumber(totalValue, config)
        val avg = formatNumber(avgValue, config)
        val unit = if (config.coinsInfoAverageUnit.equals("HOUR", true)) "avg/h" else "avg/min"

        if (config.coinsInfoTwoLines) {
            return buildList {
                if (config.coinsInfoShowTotal) {
                    add(Row(Component.literal(total), Component.literal("Coins"), config.coinsInfoTotalColor, config.coinsInfoTotalColor))
                }
                if (config.coinsInfoShowAverage) {
                    add(Row(Component.literal(avg), Component.literal(unit), config.coinsInfoAverageColor, config.coinsInfoAverageColor))
                }
            }
        }

        return when {
            config.coinsInfoShowTotal && config.coinsInfoShowAverage -> listOf(
                Row(
                    Component.literal("$total Coins"),
                    Component.literal("$avg $unit"),
                    config.coinsInfoTotalColor,
                    config.coinsInfoAverageColor,
                ),
            )
            config.coinsInfoShowTotal -> listOf(
                Row(Component.literal("$total Coins"), null, config.coinsInfoTotalColor, config.coinsInfoTotalColor),
            )
            config.coinsInfoShowAverage -> listOf(
                Row(Component.literal("$avg $unit"), null, config.coinsInfoAverageColor, config.coinsInfoAverageColor),
            )
            else -> emptyList()
        }
    }

    private fun rowWidth(font: net.minecraft.client.gui.Font, row: Row): Float {
        val left = font.width(row.left).toFloat()
        val right = row.right?.let { font.width(it).toFloat() } ?: 0f
        return left + if (row.right != null) 8f + right else 0f
    }

    private fun average(snapshot: CoinsSessionSnapshot, config: CrashsLegacyConfig): Double =
        if (config.coinsInfoAverageUnit.equals("HOUR", true)) snapshot.averagePerHour() else snapshot.averagePerMinute()

    private fun layoutKey(config: CrashsLegacyConfig): Int = listOf(
        config.coinsInfoScale,
        config.coinsInfoTwoLines,
        config.coinsInfoShowTotal,
        config.coinsInfoShowAverage,
        config.coinsInfoShowSessionTime,
        config.coinsInfoShowKills,
        config.coinsInfoAverageUnit,
        config.coinsInfoNumberFormat,
    ).hashCode()

    private fun formatNumber(value: Double, config: CrashsLegacyConfig): String = when (config.coinsInfoNumberFormat.uppercase()) {
        "RAW" -> KillRewardParser.formatCoins(value)
        "SHORT" -> formatShort(value)
        else -> String.format(Locale.ROOT, "%,.2f", value).trimEnd('0').trimEnd('.')
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        } else {
            "%02d:%02d".format(Locale.ROOT, minutes, seconds)
        }
    }

    private fun formatShort(value: Double): String = when {
        value >= 1_000_000 -> "%.1fM".format(Locale.ROOT, value / 1_000_000)
        value >= 1_000 -> "%.1fK".format(Locale.ROOT, value / 1_000)
        else -> value.toInt().toString()
    }

    private fun outline(context: GuiGraphics, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        context.fill(x1.toInt(), y1.toInt(), x2.toInt(), (y1 + 1f).toInt(), color)
        context.fill(x1.toInt(), (y2 - 1f).toInt(), x2.toInt(), y2.toInt(), color)
        context.fill(x1.toInt(), y1.toInt(), (x1 + 1f).toInt(), y2.toInt(), color)
        context.fill((x2 - 1f).toInt(), y1.toInt(), x2.toInt(), y2.toInt(), color)
    }

    private const val PADDING = 3f
    private const val MIN_WIDTH = 100f
}
