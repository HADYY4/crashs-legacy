package com.crashs.crashslegacy.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.max
import com.crashs.crashslegacy.config.CrashsLegacyConfig
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerEntry
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerKind
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerState
import com.crashs.crashslegacy.ui.CrashsHudEditorScreen

/**
 * The Item Tracker HUD: one scrolling list for warehouse deposits and inventory changes.
 *
 * Row layout is fixed: a bracketed sign (`[+]` / `[-]`), the amount, the item name (in the server's
 * colour) and optionally `→ warehouse [page]`.
 *
 * The list is owned by a single window: when it expires every row disappears at once (no fading,
 * no per-row timers). `itemTrackerHudDirection` decides which edge the box is anchored to:
 * - UP (default): bottom edge fixed, newest row at the bottom, old rows pushed up;
 * - DOWN: top edge fixed, newest row at the top, old rows pushed down.
 */
object ItemTrackerHud {

    private const val PADDING = 4f
    private const val ROW_GAP = 1f
    private const val MIN_WIDTH = 120f
    private const val BRACKET_COLOR = 0xFFAAAAAA.toInt()
    private const val PLUS_COLOR = 0xFF55FF55.toInt()
    private const val COUNT_COLOR = 0xFF55FF55.toInt()
    private const val LOSS_COLOR = 0xFFFF5555.toInt()
    private const val ARROW_COLOR = 0xFFAAAAAA.toInt()
    private const val ITEM_FALLBACK_COLOR = 0xFFFFFFFF.toInt()
    private const val WAREHOUSE_FALLBACK_COLOR = 0xFFFFFFFF.toInt()
    private data class Segment(val text: String, val color: Int, val width: Float)

    fun render(context: GuiGraphics) {
        val client = Minecraft.getInstance()
        val config = CrashsLegacyConfigManager.config
        if (!config.itemTrackerEnabled || !config.itemTrackerHud) return
        val editing = client.screen is CrashsHudEditorScreen
        if (client.player == null || client.options.hideGui) return
        if (client.screen != null && !editing && !config.itemTrackerAlwaysVisible) return

        val font = client.font
        val now = System.currentTimeMillis()
        val snapshot = ItemTrackerState.snapshot(now, windowMs(config), config.itemTrackerMaxRows.coerceIn(1, 20))
        if (snapshot.rows.isEmpty()) return

        val up = isUp(config)
        val rows = if (up) snapshot.rows else snapshot.rows.asReversed()
        val scale = config.itemTrackerScale.coerceIn(0.5f, 2.5f)
        val widest = rows.maxOf { row -> segmentsWidth(font, row.entry, config) }
        val contentWidth = max(MIN_WIDTH, widest + PADDING * 2)
        val rowHeight = font.lineHeight + 2f
        val height = PADDING * 2 + rows.size * rowHeight + (rows.size - 1) * ROW_GAP

        val bounds = HudPlacement.bounds(
            screenWidth = context.guiWidth().toFloat(),
            screenHeight = context.guiHeight().toFloat(),
            contentWidth = contentWidth,
            contentHeight = height,
            scale = scale,
            percentX = config.itemTrackerX,
            percentY = config.itemTrackerY,
            right = config.itemTrackerRight,
            bottomAnchor = up,
        )

        context.pose().pushMatrix()
        context.pose().translate(bounds.x, bounds.y)
        context.pose().scale(scale, scale)

        if (config.itemTrackerBackground) {
            context.fill(0, 0, contentWidth.toInt(), height.toInt(), config.itemTrackerBackgroundColor)
        }
        if (config.itemTrackerOutline) {
            outline(context, contentWidth, height, config.itemTrackerOutlineColor)
        }

        var rowY = PADDING
        for (row in rows) {
            var rowX = PADDING
            for (segment in rowSegments(font, row.entry, config)) {
                context.drawString(
                    font,
                    Component.literal(segment.text),
                    rowX.toInt(),
                    rowY.toInt(),
                    segment.color,
                    config.itemTrackerTextShadow,
                )
                rowX += segment.width
            }
            rowY += rowHeight + ROW_GAP
        }

        context.pose().popMatrix()
    }

    /** On-screen rectangle for the HUD editor, without drawing anything. */
    fun measure(
        screenWidth: Float,
        screenHeight: Float,
        config: CrashsLegacyConfig,
    ): HudPlacement.Bounds? {
        if (!config.itemTrackerEnabled || !config.itemTrackerHud) return null
        val client = Minecraft.getInstance()
        val font = client.font ?: return null
        val snapshot = ItemTrackerState.snapshot(
            System.currentTimeMillis(),
            windowMs(config),
            config.itemTrackerMaxRows.coerceIn(1, 20),
        )
        val widest = snapshot.rows.maxOfOrNull { row -> segmentsWidth(font, row.entry, config) } ?: 0f
        val contentWidth = max(MIN_WIDTH, widest + PADDING * 2)
        val rowHeight = font.lineHeight + 2f
        val rows = snapshot.rows.size.coerceAtLeast(1)
        val height = PADDING * 2 + rows * rowHeight + (rows - 1) * ROW_GAP
        return HudPlacement.bounds(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            contentWidth = contentWidth,
            contentHeight = height,
            scale = config.itemTrackerScale.coerceIn(0.5f, 2.5f),
            percentX = config.itemTrackerX,
            percentY = config.itemTrackerY,
            right = config.itemTrackerRight,
            bottomAnchor = isUp(config),
        )
    }

    fun isUp(config: CrashsLegacyConfig): Boolean = config.itemTrackerHudDirection != "DOWN"

    private fun segmentsWidth(
        font: net.minecraft.client.gui.Font,
        entry: ItemTrackerEntry,
        config: CrashsLegacyConfig,
    ): Float = rowSegments(font, entry, config).fold(0f) { total, segment -> total + segment.width }

    private fun rowSegments(
        font: net.minecraft.client.gui.Font,
        entry: ItemTrackerEntry,
        config: CrashsLegacyConfig,
    ): List<Segment> {
        val segments = ArrayList<Segment>(4)
        fun add(text: String, color: Int) {
            if (text.isEmpty()) return
            segments += Segment(text, color, font.width(Component.literal(text)).toFloat())
        }
        val gained = entry.kind != ItemTrackerKind.INVENTORY_LOSS
        val accent = if (gained) PLUS_COLOR else LOSS_COLOR
        add("[", BRACKET_COLOR)
        add(if (gained) "+" else "-", accent)
        add("]", BRACKET_COLOR)
        add(" ${entry.count} ", if (gained) COUNT_COLOR else LOSS_COLOR)
        add(entry.itemName, entry.itemColor ?: ITEM_FALLBACK_COLOR)
        if (entry.kind == ItemTrackerKind.WAREHOUSE &&
            config.itemTrackerShowWarehouse &&
            entry.warehouse.isNotEmpty()
        ) {
            add(" ", ITEM_FALLBACK_COLOR)
            add("→", ARROW_COLOR)
            add(" ", ITEM_FALLBACK_COLOR)
            for (segment in entry.warehouse) {
                add(segment.text, segment.color ?: WAREHOUSE_FALLBACK_COLOR)
            }
        }
        return segments
    }

    private fun windowMs(config: CrashsLegacyConfig): Long =
        (config.itemTrackerWindowSeconds.coerceIn(1f, 60f) * 1000f).toLong()

    private fun outline(context: GuiGraphics, width: Float, height: Float, color: Int) {
        context.fill(0, 0, width.toInt(), 1, color)
        context.fill(0, height.toInt() - 1, width.toInt(), height.toInt(), color)
        context.fill(0, 0, 1, height.toInt(), color)
        context.fill(width.toInt() - 1, 0, width.toInt(), height.toInt(), color)
    }
}
