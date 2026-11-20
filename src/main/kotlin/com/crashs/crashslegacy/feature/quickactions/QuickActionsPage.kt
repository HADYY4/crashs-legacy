package com.crashs.crashslegacy.feature.quickactions

import com.crashs.crashslegacy.ui.CrashsUiMetrics
import com.crashs.crashslegacy.ui.theme.CrashsImGuiTheme
import com.crashs.crashslegacy.ui.theme.CrashsThemePalette
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiDir
import imgui.type.ImInt
import imgui.type.ImString

/**
 * Quick Actions page and its inline editor.
 *
 * The icon picker and add-operation control deliberately live inside the editor modal instead of
 * child popups: opening a popup inside a modal and beginning it after `EndPopup()` uses a different
 * ImGui ID stack and silently does nothing.
 */
class QuickActionsPage(private val host: Host) {

    interface Host {
        fun execute(action: QuickAction)
        fun save()
        fun log(message: String)
    }

    var editMode: Boolean = false
        private set

    private var editing: QuickAction? = null
    private var editorOpen = false
    private var iconPickerOpen = false
    private var iconSearch = ImString(48)

    val popupOpen: Boolean get() = editorOpen || iconPickerOpen

    fun toggleEdit() {
        editMode = !editMode
        if (!editMode) closePopups()
    }

    fun onHidden() {
        editMode = false
        closePopups()
    }

    fun closePopups() {
        if (iconPickerOpen) {
            iconPickerOpen = false
            return
        }
        editorOpen = false
        editing = null
    }

    fun draw(originX: Float, originY: Float, contentWidth: Float, query: String = ""): Float {
        val needle = query.trim()
        val actions = QuickActionsStore.actions().filter { action ->
            needle.isEmpty() || action.name.contains(needle, ignoreCase = true)
        }
        val pad = 16f
        val gap = 12f
        val columns = 2
        val cardWidth = ((contentWidth - pad * 2 - gap * (columns - 1)) / columns).coerceAtLeast(220f)
        val cardHeight = 68f
        val scale = CrashsUiMetrics.scale(CrashsImGuiTheme.fontSize)
        val drawList = ImGui.getWindowDrawList()

        var row = 0
        var column = 0
        for (action in actions) {
            val x = originX + pad + column * (cardWidth + gap)
            val y = originY + pad + row * (cardHeight + gap)
            drawCard(action, x, y, cardWidth, cardHeight, scale, drawList)
            column++
            if (column >= columns) {
                column = 0
                row++
            }
        }
        val rows = if (actions.isEmpty()) 0 else (actions.size + columns - 1) / columns
        var contentHeight = pad * 2 + rows * cardHeight + (rows - 1).coerceAtLeast(0) * gap

        if (editMode) {
            ImGui.setCursorScreenPos(originX + pad, originY + contentHeight)
            if (ImGui.button("+ 新建快捷操作##qa-add")) {
                QuickActionsStore.addAction()
                host.save()
            }
            contentHeight += ImGui.getFrameHeight() + 12f
        }

        if (actions.isEmpty() && !editMode) {
            ImGui.setCursorScreenPos(originX + pad, originY + pad)
            ImGui.textDisabled("还没有快捷操作，点击右上角「编辑」创建。")
        }

        drawEditor()
        return contentHeight
    }

    private fun drawCard(
        action: QuickAction,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        scale: Float,
        drawList: imgui.ImDrawList,
    ) {
        val style = ImGui.getStyle()
        val editWidth = if (editMode) {
            ImGui.calcTextSize("编辑").x + style.getFramePaddingX() * 2f
        } else {
            0f
        }
        val clickWidth = if (editMode) (width - editWidth - 24f).coerceAtLeast(1f) else width
        ImGui.setCursorScreenPos(x, y)
        val clicked = ImGui.invisibleButton("qa-${action.id}", clickWidth, height)
        val hovered = ImGui.isItemHovered()
        val bg = if (hovered && !editMode) {
            CrashsImGuiTheme.SURFACE_CONTAINER
        } else {
            CrashsImGuiTheme.SURFACE_CONTAINER_LOW
        }
        drawList.addRectFilled(x, y, x + width, y + height, CrashsThemePalette.imU32(bg), 12f)
        drawList.addRect(x, y, x + width, y + height, CrashsThemePalette.imU32(CrashsImGuiTheme.OUTLINE_VARIANT), 12f)

        val iconSize = 40f
        val iconX = x + 14f
        val iconY = y + (height - iconSize) / 2f
        val texture = QuickActionIcons.textureId(action.icon)
        if (texture != null) {
            drawList.addImage(texture, iconX, iconY, iconX + iconSize, iconY + iconSize, 0f, 1f, 1f, 0f)
        } else {
            drawList.addRectFilled(iconX, iconY, iconX + iconSize, iconY + iconSize, CrashsThemePalette.imU32(CrashsImGuiTheme.SURFACE_CONTAINER), 6f)
        }

        val textX = iconX + iconSize + 12f
        val lineHeight = ImGui.getTextLineHeight()
        val lineGap = 4f * scale
        val blockHeight = lineHeight * 2f + lineGap
        val textY = y + (height - blockHeight) / 2f
        drawList.addText(textX, textY, CrashsThemePalette.imU32(CrashsImGuiTheme.ON_SURFACE), action.name)
        drawList.addText(
            textX,
            textY + lineHeight + lineGap,
            CrashsThemePalette.imU32(CrashsImGuiTheme.ON_SURFACE_VARIANT),
            "${action.operations.size} 步",
        )

        if (editMode) {
            ImGui.setCursorScreenPos(x + width - editWidth - 12f, y + height / 2f - ImGui.getFrameHeight() / 2f)
            if (ImGui.button("编辑##qa-edit-${action.id}")) {
                beginEditing(action)
            }
        } else if (clicked) {
            host.execute(action)
        }
    }

    private fun beginEditing(action: QuickAction) {
        editing = action
        iconPickerOpen = false
        editorOpen = true
        ImGui.openPopup("快捷操作编辑")
    }

    private fun drawEditor() {
        if (!editorOpen) return
        val action = editing ?: run { editorOpen = false; return }
        ImGui.setNextWindowSize(540f, 0f, ImGuiCond.FirstUseEver)
        if (!ImGui.beginPopupModal("快捷操作编辑")) {
            editorOpen = false
            return
        }
        try {
            val name = ImString(action.name, 64)
            if (ImGui.inputText("名称", name)) {
                action.name = name.get()
                host.save()
            }

            val texture = QuickActionIcons.textureId(action.icon)
            if (texture != null) ImGui.image(texture, 32f, 32f, 0f, 1f, 1f, 0f) else ImGui.dummy(32f, 32f)
            ImGui.sameLine()
            ImGui.text(action.icon)
            ImGui.sameLine()
            if (ImGui.button("选择图标##qa-pick")) {
                iconPickerOpen = !iconPickerOpen
            }

            if (iconPickerOpen) drawIconPickerInline(action)

            ImGui.separator()
            ImGui.text("操作列表")
            pendingRemove = -1
            pendingMoveUp = -1
            pendingMoveDown = -1
            for ((index, operation) in action.operations.withIndex()) {
                ImGui.pushID(index)
                ImGui.text("${index + 1}. ${QuickOperationTypes.label(operation.type)}")
                drawOperationControls(index)
                ImGui.spacing()
                when (operation.type) {
                    QuickOperationTypes.CHAT, QuickOperationTypes.COMMAND -> {
                        val value = ImString(operation.value, 256)
                        ImGui.setNextItemWidth(-1f)
                        if (ImGui.inputText("##value", value)) {
                            operation.value = value.get()
                            host.save()
                        }
                    }
                    QuickOperationTypes.WAIT_TICKS -> {
                        val ticks = ImInt(operation.ticks)
                        ImGui.setNextItemWidth(180f)
                        if (ImGui.inputInt("##ticks", ticks)) {
                            operation.ticks = ticks.get().coerceAtLeast(0)
                            host.save()
                        }
                    }
                    QuickOperationTypes.WAIT_MS -> {
                        val ms = ImInt(operation.ms)
                        ImGui.setNextItemWidth(180f)
                        if (ImGui.inputInt("##ms", ms)) {
                            operation.ms = ms.get().coerceAtLeast(0)
                            host.save()
                        }
                    }
                    QuickOperationTypes.WAIT_CONTAINER -> {
                        val title = ImString(operation.value, 256)
                        ImGui.setNextItemWidth(-1f)
                        if (ImGui.inputTextWithHint("##container-title", "容器标题，例如：仓库", title)) {
                            operation.value = title.get()
                            host.save()
                        }
                        val mode = ImInt(operation.button.coerceIn(0, 1))
                        ImGui.setNextItemWidth(180f)
                        if (ImGui.beginCombo("##container-match", if (mode.get() == 1) "完全匹配" else "包含")) {
                            if (ImGui.selectable("包含", mode.get() == 0)) {
                                operation.button = 0
                                host.save()
                            }
                            if (ImGui.selectable("完全匹配", mode.get() == 1)) {
                                operation.button = 1
                                host.save()
                            }
                            ImGui.endCombo()
                        }
                        val timeout = ImInt(operation.ms.takeIf { it > 0 } ?: 10000)
                        ImGui.setNextItemWidth(180f)
                        if (ImGui.inputInt("超时 (ms)##container-timeout", timeout)) {
                            operation.ms = timeout.get().coerceAtLeast(1000)
                            host.save()
                        }
                    }
                    QuickOperationTypes.CLICK_SLOT -> {
                        val slot = ImInt(operation.slot)
                        ImGui.setNextItemWidth(180f)
                        if (ImGui.inputInt("槽位 (0-based)##slot", slot)) {
                            operation.slot = slot.get().coerceAtLeast(0)
                            host.save()
                        }
                        val button = ImInt(operation.button)
                        ImGui.setNextItemWidth(180f)
                        if (ImGui.inputInt("按键 (0左/1右)##button", button)) {
                            operation.button = button.get().coerceIn(0, 1)
                            host.save()
                        }
                    }
                }
                ImGui.separator()
                ImGui.popID()
            }
            if (pendingRemove >= 0) {
                action.operations.removeAt(pendingRemove)
                host.save()
            }
            if (pendingMoveUp > 0) {
                val item = action.operations.removeAt(pendingMoveUp)
                action.operations.add(pendingMoveUp - 1, item)
                host.save()
            }
            if (pendingMoveDown in 0 until action.operations.lastIndex) {
                val item = action.operations.removeAt(pendingMoveDown)
                action.operations.add(pendingMoveDown + 1, item)
                host.save()
            }

            ImGui.setNextItemWidth(220f)
            if (ImGui.beginCombo("##qa-add-op", "添加操作")) {
                for (type in QuickOperationTypes.ALL) {
                    if (ImGui.selectable(QuickOperationTypes.label(type))) {
                        val operation = QuickOperation(type)
                        if (type == QuickOperationTypes.WAIT_CONTAINER) operation.ms = 10000
                        action.operations += operation
                        host.save()
                    }
                }
                ImGui.endCombo()
            }
            ImGui.sameLine()
            if (ImGui.button("删除此快捷操作##qa-del")) {
                QuickActionsStore.removeAction(action)
                host.save()
                editing = null
                editorOpen = false
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("完成##qa-done")) {
                host.save()
                editing = null
                editorOpen = false
                ImGui.closeCurrentPopup()
            }
        } finally {
            ImGui.endPopup()
        }
    }

    /**
     * Places up/down/delete at the right edge of the modal and returns whether a control was
     * clicked via the [ControlResult] object, so the caller can mutate the list after iterating.
     */
    private var pendingMoveUp: Int = -1
    private var pendingMoveDown: Int = -1
    private var pendingRemove: Int = -1

    private fun drawOperationControls(index: Int) {
        val style = ImGui.getStyle()
        val arrow = ImGui.getFrameHeight()
        val delWidth = ImGui.calcTextSize("删除").x + style.getFramePaddingX() * 2f
        val controls = arrow * 2f + delWidth + style.getItemSpacingX() * 2f
        val right = ImGui.getWindowContentRegionMax().x
        ImGui.sameLine((right - controls).coerceAtLeast(ImGui.getCursorPosX()))
        if (ImGui.arrowButton("##up", ImGuiDir.Up)) pendingMoveUp = index
        ImGui.sameLine()
        if (ImGui.arrowButton("##down", ImGuiDir.Down)) pendingMoveDown = index
        ImGui.sameLine()
        if (ImGui.smallButton("删除##del")) pendingRemove = index
    }

    private fun drawIconPickerInline(action: QuickAction) {
        ImGui.beginChild("##icon-picker", 0f, 220f, true)
        try {
            ImGui.inputTextWithHint("##icon-search", "搜索物品名或 ID", iconSearch)
            val results = QuickActionIcons.search(iconSearch.get())
            val clipper = imgui.ImGuiListClipper()
            clipper.begin(results.size)
            while (clipper.step()) {
                for (row in clipper.displayStart until clipper.displayEnd) {
                    val stack = results[row]
                    val id = QuickActionIcons.idOf(stack)
                    ImGui.pushID(id)
                    val texture = QuickActionIcons.textureId(id)
                    if (texture != null) {
                        ImGui.image(texture, 24f, 24f, 0f, 1f, 1f, 0f)
                    } else {
                        val pos = ImGui.getCursorScreenPos()
                        val list = ImGui.getWindowDrawList()
                        list.addRectFilled(
                            pos.x, pos.y, pos.x + 24f, pos.y + 24f,
                            CrashsThemePalette.imU32(CrashsImGuiTheme.SURFACE_CONTAINER), 4f,
                        )
                        list.addRect(
                            pos.x, pos.y, pos.x + 24f, pos.y + 24f,
                            CrashsThemePalette.imU32(CrashsImGuiTheme.OUTLINE_VARIANT), 4f,
                        )
                        ImGui.dummy(24f, 24f)
                    }
                    ImGui.sameLine()
                    if (ImGui.selectable("${QuickActionIcons.displayName(stack)}  ($id)")) {
                        action.icon = id
                        host.save()
                        iconPickerOpen = false
                    }
                    ImGui.popID()
                }
            }
            clipper.end()
        } finally {
            ImGui.endChild()
        }
    }
}
