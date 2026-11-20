package com.crashs.crashslegacy.ui.query

import net.minecraft.client.Minecraft
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt
import com.crashs.crashslegacy.BroadcastQueryRepository
import com.crashs.crashslegacy.BroadcastQueryRow
import com.crashs.crashslegacy.ui.CrashsUiDebug

class BroadcastQueryScreen(private val parent: Screen?) : Screen(Component.literal("播报查询")) {

    private enum class Field(val header: String, val width: Int, val value: (BroadcastQueryRow) -> String) {
        ID("ID", 42, { it.id.toString() }),
        RECEIVED("接收时间", 150, { it.receivedAt }),
        TYPE("类型", 50, { it.type }),
        PLAYER("玩家", 90, { it.player }),
        CONTAINER("奖励箱", 100, { it.container }),
        MOB("怪物", 100, { it.mob }),
        LEVEL("等级", 40, { it.level.toString() }),
        PREFIX("装备前缀", 90, { it.itemPrefix }),
        NAME("装备名", 130, { it.itemName }),
        QUALITY("品质", 50, { it.quality }),
        COLOR("颜色", 60, { it.itemColor }),
        RAW("原始文本", 260, { it.rawText }),
        SELF("自己", 40, { it.selfText }),
        MATERIAL("材料", 90, { it.material }),
    }

    private val filters = Array(Field.entries.size) { "" }
    private val filterBoxes = HashMap<Field, EditBox>()
    private var rows: List<BroadcastQueryRow> = emptyList()
    private var page = 0
    private var loaded = false
    private var scrollX = 0f
    private var copyNotice = ""
    private var copyNoticeUntil = 0L

    private val totalWidth get() = Field.entries.sumOf { it.width }
    private val viewLeft get() = 8
    private val viewRight get() = width - 8
    private val viewWidth get() = (viewRight - viewLeft).coerceAtLeast(1)

    override fun init() {
        CrashsUiDebug.log {
            "BroadcastQueryScreen.init #${System.identityHashCode(this)} firstInit=${!loaded}"
        }
        if (!loaded) {
            load()
            loaded = true
        }
        scrollX = scrollX.coerceIn(0f, maxScroll())

        for (field in Field.entries) {
            val box = EditBox(font, 0, FILTER_TOP, field.width - 2, 18, Component.literal(""))
            box.setMaxLength(512)
            box.setValue(filters[field.ordinal])
            box.setResponder {
                filters[field.ordinal] = it
                page = 0
            }
            addRenderableWidget(box)
            filterBoxes[field] = box
        }
        layoutFilters()

        val buttonY = height - 25
        addRenderableWidget(Button.builder(Component.literal("刷新")) { load() }.bounds(10, buttonY, 52, 20).build())
        addRenderableWidget(Button.builder(Component.literal("清空")) {
            for (box in filterBoxes.values) box.setValue("")
            filters.fill("")
            page = 0
        }.bounds(66, buttonY, 52, 20).build())
        addRenderableWidget(Button.builder(Component.literal("上一页")) {
            if (page > 0) page--
        }.bounds(width - 178, buttonY, 52, 20).build())
        addRenderableWidget(Button.builder(Component.literal("下一页")) {
            if (page < maxPage()) page++
        }.bounds(width - 122, buttonY, 52, 20).build())
        addRenderableWidget(Button.builder(Component.literal("关闭")) {
            onClose()
        }.bounds(width - 66, buttonY, 56, 20).build())
    }

    private fun load() {
        rows = BroadcastQueryRepository.load()
        page = 0
    }

    private fun filtered(): List<BroadcastQueryRow> = rows.filter { row ->
        Field.entries.withIndex().all { (index, field) ->
            val filter = filters[index].trim()
            filter.isEmpty() ||
                field.value(row).contains(filter, ignoreCase = true) ||
                display(field, row).contains(filter, ignoreCase = true)
        }
    }

    private fun maxPage(results: List<BroadcastQueryRow> = filtered()): Int {
        val count = resultRows()
        return if (results.isEmpty()) 0 else (results.size - 1) / count
    }

    private fun resultRows(): Int = ((height - TABLE_TOP - 65) / ROW_HEIGHT).coerceIn(3, 10)

    private fun display(field: Field, row: BroadcastQueryRow): String = when (field) {
        Field.RECEIVED -> formatTime(row.receivedAt)
        Field.TYPE -> typeLabel(row.type)
        Field.SELF -> if (row.self) "自己" else "其他"
        else -> field.value(row)
    }

    private fun formatTime(raw: String): String {
        if (raw.isBlank()) return ""
        val offset = runCatching { OffsetDateTime.parse(raw) }.getOrNull()
        if (offset != null) return offset.format(TIME_FORMAT)
        val local = runCatching { LocalDateTime.parse(raw) }.getOrNull()
        if (local != null) return local.format(TIME_FORMAT)
        return raw
    }

    private fun formatId(row: BroadcastQueryRow): String = row.id.toString()

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0xC0101010.toInt())
        val results = filtered()
        val rowCount = resultRows()
        page = page.coerceIn(0, maxPage(results))
        val tableBottom = TABLE_TOP + rowCount * ROW_HEIGHT

        context.fill(viewLeft, HEADER_TOP, viewRight, FILTER_TOP + 18, 0xCC202020.toInt())
        context.fill(viewLeft, FILTER_TOP - 1, viewRight, FILTER_TOP + 18, 0xCC303030.toInt())
        for (index in 0 until rowCount) {
            val y = TABLE_TOP + index * ROW_HEIGHT
            context.fill(viewLeft, y - 1, viewRight, y + ROW_HEIGHT - 1, if (index % 2 == 0) 0x40202020 else 0x402A2A2A)
        }

        super.render(context, mouseX, mouseY, delta)

        context.drawCenteredString(font, title, width / 2, 5, 0xFFFFFFFF.toInt())
        context.drawString(
            font,
            Component.literal("匹配 ${results.size} / ${rows.size} 条"),
            width - 180,
            12,
            0xFF80FF80.toInt(),
            false,
        )

        context.enableScissor(viewLeft, HEADER_TOP, viewRight, tableBottom + 1)
        var columnX = viewLeft - scrollX
        for (field in Field.entries) {
            context.drawString(font, Component.literal(fit(field.header, field.width - 3)), columnX.toInt() + 1, 24, 0xFFFFFFFF.toInt(), false)
            columnX += field.width + 1
        }

        val start = page * rowCount
        for (index in 0 until rowCount) {
            val row = results.getOrNull(start + index) ?: break
            val y = TABLE_TOP + index * ROW_HEIGHT
            columnX = viewLeft - scrollX
            for (field in Field.entries) {
                context.drawString(
                    font,
                    Component.literal(fit(display(field, row), field.width - 3)),
                    columnX.toInt() + 1,
                    y,
                    0xFFFFFFFF.toInt(),
                    false,
                )
                columnX += field.width + 1
            }
        }

        columnX = viewLeft - scrollX
        for (field in Field.entries) {
            val x = columnX + field.width + 1
            context.fill(x.toInt() - 1, HEADER_TOP, x.toInt(), tableBottom + 1, 0x66404040)
            columnX += field.width + 1
        }
        context.disableScissor()

        if (results.isEmpty()) {
            context.drawCenteredString(font, Component.literal("没有匹配的数据"), width / 2, TABLE_TOP + 24, 0xFFAAAAAA.toInt())
        }
        context.drawString(
            font,
            Component.literal("第 ${page + 1} / ${maxPage(results) + 1} 页"),
            viewLeft,
            height - 38,
            0xFFAAAAAA.toInt(),
            false,
        )
        if (System.currentTimeMillis() < copyNoticeUntil) {
            context.drawString(font, Component.literal(copyNotice), width / 2 - 40, height - 38, 0xFF80FF80.toInt(), false)
        }
        drawScrollbar(context)

        val hovered = hoveredCell(mouseX, mouseY, results, rowCount)
        if (hovered != null) {
            val fullText = display(hovered.first, hovered.second)
            if (fullText.isNotEmpty()) {
                context.setTooltipForNextFrame(font, Component.literal(fullText), mouseX, mouseY)
            }
        }
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (super.mouseClicked(click, doubled)) return true
        if (click.button() == 0 && click.y() >= TABLE_TOP) {
            val results = filtered()
            val rowCount = resultRows()
            val row = rowAt(click.y(), results, rowCount) ?: return false
            val column = columnAt(click.x()) ?: return false
            val text = if (hasShift(click)) rowCopy(row) else display(column, row)
            if (text.isNotEmpty()) {
                Minecraft.getInstance().keyboardHandler.clipboard = text
                copyNotice = if (hasShift(click)) "已复制整行" else "已复制"
                copyNoticeUntil = System.currentTimeMillis() + 1500
            }
            return true
        }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (mouseX in viewLeft.toDouble()..viewRight.toDouble() && mouseY in HEADER_TOP.toDouble()..(height - 50).toDouble()) {
            scrollX = (scrollX - vertical * 40.0).toFloat().coerceIn(0f, maxScroll())
            layoutFilters()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    private fun hasShift(click: MouseButtonEvent): Boolean = (click.modifiers() and 1) != 0

    private fun columnAt(mouseX: Double): Field? {
        if (mouseX < viewLeft || mouseX > viewRight) return null
        var x = viewLeft - scrollX
        for (field in Field.entries) {
            if (mouseX >= x && mouseX < x + field.width) return field
            x += field.width + 1
        }
        return null
    }

    private fun rowAt(mouseY: Double, results: List<BroadcastQueryRow>, rowCount: Int): BroadcastQueryRow? {
        if (mouseY < TABLE_TOP || mouseY >= TABLE_TOP + rowCount * ROW_HEIGHT) return null
        val index = ((mouseY - TABLE_TOP) / ROW_HEIGHT).toInt()
        return results.getOrNull(page * rowCount + index)
    }

    private fun hoveredCell(mouseX: Int, mouseY: Int, results: List<BroadcastQueryRow>, rowCount: Int): Pair<Field, BroadcastQueryRow>? {
        if (mouseY < TABLE_TOP) return null
        val row = rowAt(mouseY.toDouble(), results, rowCount) ?: return null
        val field = columnAt(mouseX.toDouble()) ?: return null
        return field to row
    }

    private fun drawScrollbar(context: GuiGraphics) {
        val top = height - 49
        val bottom = top + 6
        context.fill(viewLeft, top, viewRight, bottom, 0x66303030)
        if (totalWidth <= viewWidth) return
        val thumbWidth = (viewWidth * viewWidth / totalWidth).coerceAtLeast(20)
        val travel = (viewWidth - thumbWidth).coerceAtLeast(1)
        val progress = (scrollX / maxScroll()).coerceIn(0f, 1f)
        val thumbX = viewLeft + (travel * progress).roundToInt()
        context.fill(thumbX, top, thumbX + thumbWidth, bottom, 0xFFAAAAAA.toInt())
    }

    private fun maxScroll(): Float = (totalWidth - viewWidth).coerceAtLeast(0).toFloat()

    private fun layoutFilters() {
        scrollX = scrollX.coerceIn(0f, maxScroll())
        var x = viewLeft - scrollX
        for (field in Field.entries) {
            val box = filterBoxes[field] ?: continue
            box.setX(x.toInt())
            box.visible = x >= viewLeft - 2 && x + field.width <= viewRight + 2
            x += field.width + 1
        }
    }

    private fun fit(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && font.width(text.substring(0, end) + "...") > maxWidth) end--
        return text.substring(0, end) + "..."
    }

    private fun rowCopy(row: BroadcastQueryRow): String = listOf(
        row.id.toString(), row.receivedAt, row.epochMs.toString(), row.type, row.player,
        row.container, row.mob, row.level.toString(), row.itemPrefix, row.itemName,
        row.quality, row.itemColor, row.rawText, row.selfText, row.material,
    ).joinToString("\t")

    private fun typeLabel(type: String): String = when (type.lowercase()) {
        "kill" -> "击杀"
        "loot" -> "摸宝"
        "reforge" -> "重铸"
        else -> type
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }

    private companion object {
        const val HEADER_TOP = 20
        const val FILTER_TOP = 40
        const val TABLE_TOP = 60
        const val ROW_HEIGHT = 18
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
