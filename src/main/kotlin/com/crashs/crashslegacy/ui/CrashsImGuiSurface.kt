package com.crashs.crashslegacy.ui

import imgui.ImGui
import imgui.ImGuiIO
import imgui.flag.ImDrawFlags
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import kotlin.math.exp
import kotlin.math.roundToInt
import com.crashs.crashslegacy.ui.theme.CrashsImGuiTheme
import com.crashs.crashslegacy.ui.theme.CrashsThemeMode
import com.crashs.crashslegacy.ui.theme.CrashsThemePalette

/** One module card: metadata plus the callback that draws its settings. */
class CrashsUiModule(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val content: () -> Unit,
    val toggleGet: (() -> Boolean)? = null,
    val toggleSet: ((Boolean) -> Unit)? = null,
    val action: (() -> Unit)? = null,
)

/**
 * Everything the surface cannot do by itself: module content, config persistence and screen
 * changes. Implemented against Minecraft by [CrashsImGuiScreen], and against demo widgets by the
 * desktop preview, so both draw the exact same UI code.
 */
/**
 * Whether one of our UIs is currently inside an ImGui frame.
 *
 * `ImGui.newFrame()` asserts when it is called while a frame is still open, and screens can be
 * switched from inside a frame (card actions, the HUD editor's 完成 button). The drain in
 * [CrashsImGuiSurface.discardQueuedPointerInput] pumps throwaway frames, so it must know when that
 * is not allowed; the entry input guard still runs and protects the first frames either way.
 */
object CrashsImGuiFrameState {
    @Volatile
    var active: Boolean = false
}

interface CrashsUiHost {
    val modules: List<CrashsUiModule>
    fun frameTick()
    fun configChanged()
    fun closeScreen()
    fun openExternal(moduleId: String)
    fun openHudEditor()
    fun resetConfig()
    fun log(message: String)

    /** Draws the Quick Actions page content (Minecraft-only code lives in the host). */
    fun drawQuickActionsPage(originX: Float, originY: Float, contentWidth: Float, query: String): Float = 0f

    /** Toolbar label on the Quick Actions page: 编辑 or 完成. */
    fun quickActionsToolbarLabel(): String = "编辑"

    fun toggleQuickActionsEdit() {}
    fun quickActionsPopupOpen(): Boolean = false
    fun closeQuickActionsPopup() {}

    /** Called when navigation leaves the Quick Actions page. */
    fun quickActionsOnHidden() {}

    fun quickActionsEnterOnOpen(): Boolean = true
    fun setQuickActionsEnterOnOpen(value: Boolean) {}
}

/**
 * Three-level Crashs Legacy surface, free of Minecraft types:
 * category rail -> module cards -> inline expanded module settings.
 */
class CrashsImGuiSurface(private val host: CrashsUiHost) {

    private data class CardState(
        var progress: Float = 0f,
        var hover: Float = 0f,
        var bodyHeight: Float = 120f,
        var targetX: Float = 0f,
        var targetY: Float = 0f,
        var targetHeight: Float = CrashsImGuiLayout.cardHeaderHeightPx(),
        var lane: Int = -1,
        val xMotion: CrashsMotion = CrashsMotion(),
        val yMotion: CrashsMotion = CrashsMotion(),
    )

    private var category = CrashsImGuiUiState.category(0).let { if (it in 0..1) it else 0 }
    private var firstFrame = true
    private var resetRequestedAt = 0L
    /** Draft value of the scale slider; applied only when the user presses 保存. */
    private var uiScaleDraft = 1f
    private var uiScaleDraftLoaded = false

    private var resetPopupOpen = false
    private var resetPopupPending = false
    private var page: String = CrashsImGuiUiState.page()
    private val demoWindow = ImBoolean(false)
    private val search = ImString("", 64)
    private var lastSearch = ""
    private val cardStates = HashMap<String, CardState>()
    private val cardSwitchMotion = HashMap<String, Float>()
    private val navFillMotion = HashMap<String, Float>()
    private val navGrowMotion = HashMap<String, Float>()
    private var navModulesOpen = true
    private var pendingExternal: String? = null
    private var lastCardsLayoutKey = ""

    /** Optional diagnostics sink, wired by the host only while `/crashs debug ui` is on. */
    var debugSink: ((String) -> Unit)? = null

    /**
     * Frames left where mouse buttons are forced to "up".
     *
     * Draining the event queue at screen-change time is not enough: ESC and the mouse callbacks
     * arrive in the same GLFW poll batch, so more button events can be queued *after* the drain and
     * still replay on the next frame. The guard makes the first frames after (re)entering ignore
     * any queued click.
     */
    private var inputGuardFrames = 0

    /** Last frame's "a text field has keyboard focus", used to route Esc correctly. */
    private var textInputActive = false

    /** One-shot log so the fallback reason appears once, not every frame. */
    private var fallbackLogged = false

    /** Arm the entry guard; call after [discardQueuedPointerInput]. */
    fun armInputGuard(frames: Int = 2) {
        inputGuardFrames = frames.coerceAtLeast(1)
    }

    /** Size factor for text-relative geometry; 1.0 corresponds to the original 17px design. */
    private val textScale: Float get() = CrashsUiMetrics.scale(CrashsImGuiTheme.fontSize)

    /** Top bar grows with the font so the title and the search box stay vertically centred. */
    private val topBarHeight: Float get() = 64f + (CrashsImGuiTheme.fontSize - 17f) * 1.6f

    private val categories = arrayOf("全部", "无名")
    private val categoryKeys = arrayOf("ALL", "MYSTERIOUS")

    private val modules: List<CrashsUiModule> get() = host.modules

    /** Re-syncs category/page after `/crashs reload` changed ui-state.json. */
    fun reloadState() {
        category = CrashsImGuiUiState.category(0).let { if (it in 0..1) it else 0 }
        page = CrashsImGuiUiState.page()
    }

    fun frame(io: ImGuiIO) {
        CrashsImGuiFrameState.active = true
        try {
            frameBody(io)
        } finally {
            CrashsImGuiFrameState.active = false
        }
    }

    private fun frameBody(io: ImGuiIO) {
        // Minimised windows report a zero display size; building a frame then would divide by zero
        // and can trip ImGui asserts. Skip the frame and wait for the window to come back.
        if (io.displaySizeX < 1f || io.displaySizeY < 1f) return
        applyInputGuard(io)
        host.frameTick()
        val width = io.displaySizeX.coerceAtLeast(1f)
        val height = io.displaySizeY.coerceAtLeast(1f)

        if (!CrashsUiMetrics.canRenderFull(width, height)) {
            // Never build the rail/cards in a window this small: negative content regions are what
            // makes native ImGui asserts fire (the process aborts, Java cannot catch it).
            if (!fallbackLogged) {
                fallbackLogged = true
                host.log("UI layout fallback: display=${width.toInt()}x${height.toInt()} (too small for the full menu)")
            }
            drawSmallWindowFallback(width, height)
            return
        }
        fallbackLogged = false
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarPadding, 2f)

        // Constraints are re-applied every frame so F11 / window resizes shrink the menu instead of
        // leaving it hanging off the screen. The minimum is itself clamped to the display, so the
        // window can never be forced larger than the screen.
        val maxW = (width - 20f).coerceAtLeast(1f)
        val maxH = (height - 20f).coerceAtLeast(1f)
        val minW = minOf(320f, maxW)
        val minH = minOf(320f, maxH)
        val wantMinW = minOf(900f, maxOf(minW, width - 40f))
        val wantMinH = minOf(600f, maxOf(minH, height - 40f))
        ImGui.setNextWindowSizeConstraints(wantMinW, wantMinH, maxW, maxH)

        if (firstFrame) {
            firstFrame = false
            // One-shot diagnostic: the configured size means MiSans is the active font; 13 means
            // the built-in ProggyClean is still in front and CJK will render as '?'.
            host.log(
                "Crashs Legacy UI font check: activeSize=${ImGui.getFontSize()}, " +
                    "frameCount=${ImGui.getFrameCount()}",
            )
            val (savedW, savedH) = CrashsImGuiUiState.windowSize()
            val (targetW, targetH) = CrashsWindowGeometry.clampSize(savedW, savedH, wantMinW, wantMinH, maxW, maxH)
            ImGui.setNextWindowSize(targetW, targetH, ImGuiCond.FirstUseEver)
            val saved = CrashsImGuiUiState.windowPosition()
            if (saved != null) {
                // A saved position can belong to a monitor that is gone (or a larger resolution).
                val (x, y) = CrashsWindowGeometry.clampPosition(saved.first, saved.second, targetW, targetH, width, height)
                ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver)
            } else {
                val x = (width - CrashsImGuiLayout.windowWidthPx()).coerceAtLeast(0f) / 2f
                val y = (height - CrashsImGuiLayout.windowHeightPx()).coerceAtLeast(0f) / 2f
                ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver)
            }
        }

        if (ImGui.begin("Crashs Legacy", ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoCollapse)) {
            drawTopBar()
            drawCategoryRail()
            drawCards()
            drawResetPopup()
            if (resetPopupPending) {
                resetPopupPending = false
                ImGui.openPopup("确认恢复默认")
            }

            val pos = ImGui.getWindowPos()
            val size = ImGui.getWindowSize()
            // Keep the menu inside the display after a resolution change; ImGui does not move
            // existing windows on its own.
            if (!CrashsWindowGeometry.isInside(pos.x, pos.y, size.x, size.y, width, height)) {
                val (x, y) = CrashsWindowGeometry.clampPosition(pos.x, pos.y, size.x, size.y, width, height)
                ImGui.setWindowPos(x, y)
            }
            // Never persist a collapsed size from a minimised/degenerate frame.
            if (size.x >= 200f && size.y >= 200f) {
                CrashsImGuiUiState.updateWindow(pos.x, pos.y, size.x, size.y)
            }
        }
        ImGui.end()
        if (ImGui.isKeyPressed(ImGuiKey.F9)) {
            demoWindow.set(!demoWindow.get())
        }
        if (demoWindow.get()) {
            ImGui.showDemoWindow(demoWindow)
            ImGui.showStyleEditor()
        }
        val target = pendingExternal
        if (target != null && !ImGui.getIO().getMouseDown(0)) {
            val io = ImGui.getIO()
            io.setMouseDown(0, false)
            io.setMouseClicked(0, false)
            io.setMouseReleased(0, false)
            io.setMouseDoubleClicked(0, false)
            pendingExternal = null
            host.openExternal(target)
        }
        ImGui.popStyleVar()
        textInputActive = io.wantTextInput
    }

    /** True when an ImGui text field had focus during the last frame. */
    fun isEditingText(): Boolean = textInputActive

    /** True when the reset-confirmation modal or a Quick Action popup is open. */
    fun hasActivePopup(): Boolean = resetPopupOpen || host.quickActionsPopupOpen()

    fun closeActivePopup() {
        if (host.quickActionsPopupOpen()) {
            host.closeQuickActionsPopup()
            return
        }
        if (!resetPopupOpen) return
        resetPopupOpen = false
        runCatching { ImGui.closeCurrentPopup() }
    }

    /**
     * Drops pointer events that queued up while another screen was showing.
     *
     * The ImGui GLFW backend keeps feeding `io.addMouseButtonEvent` while no frame is rendered, so
     * after coming back from e.g. the broadcast query list the first frames replay those clicks on
     * whatever sits under the unchanged cursor — which re-triggers the very card that opened the
     * other screen. Pumping throwaway frames consumes and discards the queue; `clearInputMouse()`
     * does not (it only resets the state fields, as verified against this ImGui build).
     */
    fun discardQueuedPointerInput(
        reason: String = "manual",
        passes: Int = 3,
        debug: ((String) -> Unit)? = null,
    ) {
        val io = runCatching { ImGui.getIO() }.getOrNull() ?: return
        if (CrashsImGuiFrameState.active) return
        if (io.displaySizeX < 1f || io.displaySizeY < 1f) return
        if (runCatching { ImGui.getFrameCount() }.getOrDefault(0) == 0) return
        val before = if (debug == null) null else describePointerState(io)
        repeat(passes.coerceAtLeast(1)) {
            ImGui.newFrame()
            ImGui.endFrame()
            ImGui.render()
        }
        if (debug != null && before != null) {
            debug("drain($reason): $before -> ${describePointerState(io)}")
        }
    }

    /**
     * Drains queued pointer *and* keyboard events, then resets the key state.
     *
     * The GLFW backend feeds ImGui even while another screen (chat, inventory, …) is open, so the
     * keys typed there would otherwise be replayed into our text fields when the menu reopens.
     */
    fun discardQueuedInput(reason: String = "manual", debug: ((String) -> Unit)? = null) {
        discardQueuedPointerInput(reason, debug = debug)
        val io = runCatching { ImGui.getIO() }.getOrNull() ?: return
        runCatching { io.clearInputKeys() }
        runCatching { io.clearInputMouse() }
    }

    /** Clears per-screen interaction state when the screen goes away (disconnect, replaced, …). */
    fun resetTransient() {
        inputGuardFrames = 0
        textInputActive = false
        resetPopupOpen = false
        resetPopupPending = false
    }

    /**
     * For the first frames after entering the screen, hide the mouse buttons from the widgets so a
     * replayed click cannot activate a card. Runs right after `ImGui.newFrame()`, i.e. after the
     * queued events were applied, which is exactly why clearing the state here works.
     */
    private fun applyInputGuard(io: ImGuiIO) {
        if (inputGuardFrames <= 0) return
        inputGuardFrames--
        io.setMouseDown(0, false)
        io.setMouseClicked(0, false)
        io.setMouseReleased(0, false)
        io.setMouseDoubleClicked(0, false)
        debugSink?.invoke("input guard: legacyMouseDown=${io.getMouseDown(0)} framesLeft=$inputGuardFrames")
    }

    private fun describePointerState(io: ImGuiIO): String =
        "down=${io.getMouseDown(0)} clicked=${io.getMouseClicked(0)} " +
            "pos=${ImGui.getMousePosX().toInt()},${ImGui.getMousePosY().toInt()} " +
            "frames=${ImGui.getFrameCount()}"

    /** Pushes a clip rect only when it has a real area; inverted/empty rects trip native asserts. */
    private fun pushClipSafe(x: Float, y: Float, width: Float, height: Float): Boolean {
        if (width <= 0.5f || height <= 0.5f) return false
        ImGui.pushClipRect(x, y, x + width, y + height, true)
        return true
    }

    /** Shown instead of the full menu when the game window is too small to lay it out safely. */
    private fun drawSmallWindowFallback(width: Float, height: Float) {
        val w = minOf(360f, (width - 20f).coerceAtLeast(1f))
        val h = minOf(150f, (height - 20f).coerceAtLeast(1f))
        ImGui.setNextWindowPos((width - w) / 2f, (height - h) / 2f, ImGuiCond.Always)
        ImGui.setNextWindowSize(w, h, ImGuiCond.Always)
        if (ImGui.begin(
                "Crashs Legacy",
                ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoResize or
                    ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoCollapse,
            )
        ) {
            ImGui.textWrapped("窗口太小，请放大游戏窗口后再打开")
            ImGui.dummy(1f, 4f)
            ImGui.textWrapped("按 Esc 关闭")
        }
        ImGui.end()
    }

    private fun drawTopBar() {
        val windowPos = ImGui.getWindowPos()
        val drawList = ImGui.getWindowDrawList()
        drawList.addRectFilled(
            windowPos.x,
            windowPos.y,
            windowPos.x + ImGui.getWindowWidth(),
            windowPos.y + topBarHeight,
            CrashsThemePalette.imU32(CrashsImGuiTheme.SURFACE_CONTAINER),
        )
        ImGui.setCursorScreenPos(windowPos.x + 24f, windowPos.y + (topBarHeight - ImGui.getFontSize()) / 2f)
        textColoredArgb(CrashsImGuiTheme.ON_SURFACE, "Crashs Legacy")
        val buttonY = windowPos.y + (topBarHeight - ImGui.getFrameHeight()) / 2f
        ImGui.setCursorScreenPos(windowPos.x + 190f, buttonY)
        val searchRoom = (ImGui.getWindowWidth() - 190f - 200f).coerceAtLeast(40f)
        ImGui.setNextItemWidth(minOf(280f, searchRoom))
        val searchHint = if (isQuickActionsPage()) "搜索快捷操作" else "搜索模块"
        if (isConfigPage()) {
            ImGui.beginDisabled(true)
            ImGui.inputTextWithHint("##search", searchHint, search)
            ImGui.endDisabled()
        } else {
            ImGui.inputTextWithHint("##search", searchHint, search)
        }
        val query = search.get()
        if (query != lastSearch) lastSearch = query
        val style = ImGui.getStyle()
        val right = windowPos.x + ImGui.getWindowWidth() - 16f
        val gap = 12f
        fun widthOf(text: String): Float = ImGui.calcTextSize(text).x + style.getFramePaddingX() * 2f
        val closeWidth = widthOf("关闭")
        val resetLabel = if (isQuickActionsPage()) host.quickActionsToolbarLabel() else "重置"
        val resetWidth = widthOf(resetLabel)
        val debugWidth = widthOf("UI 调试")
        var x = right - closeWidth
        ImGui.setCursorScreenPos(x, buttonY)
        if (ImGui.button("关闭")) host.closeScreen()
        x -= gap + resetWidth
        ImGui.setCursorScreenPos(x, buttonY)
        if (ImGui.button(resetLabel)) {
            if (isQuickActionsPage()) {
                host.toggleQuickActionsEdit()
            } else {
                resetRequestedAt = System.currentTimeMillis()
                resetPopupOpen = true
                resetPopupPending = true
            }
        }
        x -= gap + debugWidth
        ImGui.setCursorScreenPos(x, buttonY)
        if (ImGui.button("UI 调试")) {
            demoWindow.set(!demoWindow.get())
        }
    }

    private fun drawCategoryRail() {
        val windowPos = ImGui.getWindowPos()
        val windowSize = ImGui.getWindowSize()
        val drawList = ImGui.getWindowDrawList()
        val railWidth = CrashsImGuiLayout.rail()
        val pillWidth = CrashsImGuiLayout.pillWidth()
        val pillHeight = CrashsImGuiLayout.pillHeight()
        val top = windowPos.y + topBarHeight
        drawList.addRectFilled(
            windowPos.x,
            top,
            windowPos.x + railWidth,
            windowPos.y + windowSize.y,
            CrashsThemePalette.imU32(CrashsImGuiTheme.RAIL),
        )
        val pillX = windowPos.x + (railWidth - pillWidth) / 2f
        var y = top + 20f
        val quickClicked = drawCapsule(
            id = "nav.quick",
            x = pillX,
            y = y,
            width = pillWidth,
            height = pillHeight,
            label = "快捷操作",
            selected = isQuickActionsPage(),
        ) { centerX, centerY, color -> drawQuickIcon(centerX, centerY, color) }
        if (quickClicked) {
            setPage(PAGE_QUICK_ACTIONS)
            cardStates.clear()
        }
        y += pillHeight + 10f
        val modulesClicked = drawCapsule(
            id = "nav.modules",
            x = pillX,
            y = y,
            width = pillWidth,
            height = pillHeight,
            label = "模块",
            selected = false,
            arrow = true,
            arrowExpanded = navModulesOpen,
        ) { centerX, centerY, color -> drawModulesIcon(centerX, centerY, color) }
        if (modulesClicked) navModulesOpen = !navModulesOpen
        y += pillHeight + 10f
        if (navModulesOpen) {
            for ((index, label) in categories.withIndex()) {
                val selected = page == PAGE_MODULES && category == index
                val clicked = drawCapsule(
                    id = "nav.$index",
                    x = pillX,
                    y = y,
                    width = pillWidth,
                    height = pillHeight,
                    label = label,
                    selected = selected,
                ) { centerX, centerY, color -> drawNavIcon(index, centerX, centerY, color) }
                if (clicked) {
                    category = index
                    CrashsImGuiUiState.setCategory(index)
                    setPage(PAGE_MODULES)
                    cardStates.clear()
                }
                y += pillHeight + 10f
            }
        }

        val hudY = windowPos.y + windowSize.y - pillHeight - 28f
        val configY = hudY - pillHeight - 10f
        val configClicked = drawCapsule(
            id = "nav.config",
            x = pillX,
            y = configY,
            width = pillWidth,
            height = pillHeight,
            label = "配置",
            selected = isConfigPage(),
        ) { centerX, centerY, color -> drawSlidersIcon(centerX, centerY, color) }
        if (configClicked) {
            setPage(if (isConfigPage()) PAGE_MODULES else PAGE_CONFIG)
        }
        val hudClicked = drawCapsule(
            id = "nav.hud",
            x = pillX,
            y = hudY,
            width = pillWidth,
            height = pillHeight,
            label = "HUD",
            selected = false,
        ) { centerX, centerY, color -> drawPenIcon(centerX, centerY, color) }
        if (hudClicked) host.openHudEditor()
    }

    /**
     * Draws one navigation capsule.
     *
     * The content group (icon + gap + label + optional arrow) is centred inside [width], so a
     * label of any length gets equal padding on both sides instead of running into the rail
     * edge. The hover/selection highlight grows symmetrically around the same centre.
     */
    private fun drawCapsule(
        id: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        label: String,
        selected: Boolean,
        arrow: Boolean = false,
        arrowExpanded: Boolean = false,
        icon: ((Float, Float, Int) -> Unit)? = null,
    ): Boolean {
        ImGui.setCursorScreenPos(x, y)
        val clicked = ImGui.invisibleButton("##$id", width, height)
        val hovered = ImGui.isItemHovered()
        val dt = ImGui.getIO().deltaTime.coerceIn(0f, 0.1f)

        val target = if (hovered || selected) 1f else 0f
        val current = navFillMotion.getOrDefault(id, 0f)
        val progress = current + (target - current) * (1f - kotlin.math.exp(-dt * 12f))
        navFillMotion[id] = if (kotlin.math.abs(progress - target) < 0.002f) target else progress

        val growTarget = if (hovered || selected) 4f else 0f
        val growCurrent = navGrowMotion.getOrDefault(id, 0f)
        val grow = growCurrent + (growTarget - growCurrent) * (1f - kotlin.math.exp(-dt * 10f))
        navGrowMotion[id] = if (kotlin.math.abs(grow - growTarget) < 0.05f) growTarget else grow

        val drawList = ImGui.getWindowDrawList()
        val radius = height / 2f
        val left = x - grow
        val right = x + width + grow
        drawList.addRectFilled(left, y, right, y + height, CrashsThemePalette.imU32(CrashsImGuiTheme.CAPSULE_BASE), radius)
        if (progress > 0.001f && pushClipSafe(left, y, (right - left) * progress, height)) {
            drawList.addRectFilled(
                left,
                y,
                right,
                y + height,
                CrashsThemePalette.imU32(if (selected) CrashsImGuiTheme.PRIMARY_CONTAINER else CrashsImGuiTheme.HOVER_FILL),
                radius,
            )
            ImGui.popClipRect()
        }

        val color = when {
            selected -> CrashsThemePalette.imU32(CrashsImGuiTheme.ON_PRIMARY_CONTAINER)
            hovered -> CrashsThemePalette.imU32(CrashsImGuiTheme.ON_SURFACE)
            else -> CrashsThemePalette.imU32(CrashsImGuiTheme.ON_SURFACE_VARIANT)
        }

        val fontSize = CrashsImGuiTheme.fontSize
        val scale = CrashsUiMetrics.scale(fontSize)
        val iconBox = CrashsUiMetrics.iconBox(fontSize)
        val iconGap = CrashsUiMetrics.iconGap(fontSize)
        val labelSize = ImGui.calcTextSize(label)
        val groupWidth = CrashsUiMetrics.pillContentWidth(
            labelWidth = labelSize.x,
            hasIcon = icon != null,
            hasArrow = arrow,
            fontSize = fontSize,
        )
        var cursorX = x + (width - groupWidth) / 2f
        val centerY = y + height / 2f
        if (icon != null) {
            icon(cursorX + iconBox / 2f, centerY, color)
            cursorX += iconBox + iconGap
        }
        drawList.addText(
            ImGui.getFont(),
            CrashsImGuiTheme.fontSize.roundToInt(),
            imgui.ImVec2(cursorX, y + (height - labelSize.y) / 2f),
            color,
            label,
        )
        cursorX += labelSize.x
        if (arrow) {
            val arrowSize = (CrashsImGuiTheme.fontSize * 0.82f).roundToInt()
            drawList.addText(
                ImGui.getFont(),
                arrowSize,
                imgui.ImVec2(cursorX + 6f * scale, y + (height - arrowSize) / 2f - 1f),
                color,
                if (arrowExpanded) "^" else "v",
            )
        }
        return clicked
    }

    private fun drawModulesIcon(centerX: Float, centerY: Float, color: Int) {
        val s = textScale
        val d = ImGui.getWindowDrawList()
        val rounding = 2f * s
        d.addRect(centerX - 9f * s, centerY - 8f * s, centerX - 1f * s, centerY, color, rounding)
        d.addRect(centerX + 1f * s, centerY - 8f * s, centerX + 9f * s, centerY, color, rounding)
        d.addRect(centerX - 9f * s, centerY + 2f * s, centerX - 1f * s, centerY + 10f * s, color, rounding)
        d.addRect(centerX + 1f * s, centerY + 2f * s, centerX + 9f * s, centerY + 10f * s, color, rounding)
    }

    private fun drawPenIcon(centerX: Float, centerY: Float, color: Int) {
        val s = textScale
        val d = ImGui.getWindowDrawList()
        d.addLine(centerX - 7f * s, centerY + 7f * s, centerX + 5f * s, centerY - 5f * s, color, 3f * s)
        d.addTriangleFilled(
            centerX - 8f * s, centerY + 8f * s,
            centerX - 3f * s, centerY + 6f * s,
            centerX - 6f * s, centerY + 3f * s,
            color,
        )
        d.addLine(centerX + 5f * s, centerY - 7f * s, centerX + 8f * s, centerY - 4f * s, color, 2f * s)
    }

    private fun drawQuickIcon(centerX: Float, centerY: Float, color: Int) {
        val d = ImGui.getWindowDrawList()
        val s = 14f * textScale
        d.addTriangleFilled(
            centerX - s * 0.2f, centerY - s,
            centerX - s * 0.7f, centerY + s * 0.1f,
            centerX - s * 0.05f, centerY + s * 0.1f,
            color,
        )
        d.addTriangleFilled(
            centerX + s * 0.2f, centerY + s,
            centerX + s * 0.7f, centerY - s * 0.1f,
            centerX + s * 0.05f, centerY - s * 0.1f,
            color,
        )
    }

    private fun drawNavIcon(index: Int, centerX: Float, centerY: Float, color: Int) {
        val s = textScale
        val d = ImGui.getWindowDrawList()
        if (index == 1) {
            val outline = CrashsThemePalette.imU32(CrashsImGuiTheme.SHADOW)
            d.addLine(centerX - 7f * s, centerY + 8f * s, centerX + 6f * s, centerY - 7f * s, outline, 6f * s)
            d.addLine(centerX - 5f * s, centerY + 3f * s, centerX + 1f * s, centerY + 9f * s, outline, 4f * s)
            d.addLine(centerX - 7f * s, centerY + 8f * s, centerX + 6f * s, centerY - 7f * s, color, 3f * s)
            d.addLine(centerX - 5f * s, centerY + 3f * s, centerX + 1f * s, centerY + 9f * s, color, 2f * s)
            d.addTriangleFilled(
                centerX + 4f * s, centerY - 9f * s,
                centerX + 9f * s, centerY - 4f * s,
                centerX + 3f * s, centerY - 4f * s,
                color,
            )
            d.addCircleFilled(centerX - 9f * s, centerY + 10f * s, 2f * s, color)
        } else {
            val rounding = 2f * s
            d.addRect(centerX - 8f * s, centerY - 8f * s, centerX - 1f * s, centerY - 1f * s, color, rounding)
            d.addRect(centerX + 1f * s, centerY - 8f * s, centerX + 8f * s, centerY - 1f * s, color, rounding)
            d.addRect(centerX - 8f * s, centerY + 1f * s, centerX - 1f * s, centerY + 8f * s, color, rounding)
            d.addRect(centerX + 1f * s, centerY + 1f * s, centerX + 8f * s, centerY + 8f * s, color, rounding)
        }
    }

    private fun drawCards() {
        val windowPos = ImGui.getWindowPos()
        val windowSize = ImGui.getWindowSize()
        val railWidth = CrashsImGuiLayout.rail()
        val contentX = windowPos.x + railWidth + 20f
        val contentY = windowPos.y + topBarHeight + 20f
        val contentWidth = (windowSize.x - railWidth - 40f).coerceAtLeast(1f)
        val contentHeight = (windowSize.y - topBarHeight - 40f).coerceAtLeast(1f)
        ImGui.setCursorScreenPos(contentX, contentY)
        // EndChild() is mandatory even when BeginChild() reports "not visible", otherwise the next
        // End() trips ImGui's "(g.WithinEndChildID == window->ID)" assertion.
        val viewportVisible = ImGui.beginChild("##cardsViewport", contentWidth, contentHeight, false)
        if (viewportVisible) {
            val origin = ImGui.getCursorScreenPos()
            val maxHeight = if (isConfigPage()) {
                drawConfigPage(origin.x, origin.y, contentWidth)
            } else {
                drawCardsArea(origin.x, origin.y, contentWidth)
            }
            ImGui.setCursorScreenPos(origin.x, origin.y + maxHeight)
            ImGui.dummy(1f, 1f)
        }
        ImGui.endChild()
    }

    private fun drawCardsArea(originX: Float, originY: Float, contentWidth: Float): Float {
        if (isQuickActionsPage()) {
            return host.drawQuickActionsPage(originX, originY, contentWidth, search.get())
        }
        val pad = 16f
        // Child windows have no window padding in ImGui 1.92, so only the scrollbar has to be
        // reserved; without it the right column ends up clipped by / hidden under the bar as soon
        // as the cards overflow the viewport.
        val style = ImGui.getStyle()
        val viewportInset = style.getScrollbarSize() + 2f
        val usableWidth = (contentWidth - viewportInset - pad * 2f).coerceAtLeast(180f)
        val columns = if (usableWidth >= 660f) 2 else 1
        val gap = CrashsImGuiLayout.cardGapPx()
        val cardWidth = CrashsUiMetrics.cardWidth(contentWidth, viewportInset, pad, gap, columns)
            .coerceAtMost((contentWidth - pad * 2f).coerceAtLeast(60f))
        val query = lastSearch.trim()
        val categoryKey = categoryKeys.getOrNull(category) ?: "ALL"
        val visible = modules.filter { module ->
            (categoryKey == "ALL" || module.category == categoryKey) &&
                (query.isEmpty() || module.title.contains(query, true) || module.description.contains(query, true))
        }
        if (visible.isEmpty()) {
            ImGui.setCursorScreenPos(originX + pad, originY + pad)
            CrashsImGuiFields.colorText(CrashsImGuiTheme.ON_SURFACE_VARIANT, "没有匹配的模块")
            return pad * 2f
        }

        val layoutKey = "$category|$query|$columns|$cardWidth"
        val snap = layoutKey != lastCardsLayoutKey
        lastCardsLayoutKey = layoutKey
        if (snap) {
            for (module in visible) cardStates.getOrPut(module.id) { CardState() }.lane = -1
        }
        val columnHeights = FloatArray(columns)
        val dt = ImGui.getIO().deltaTime.coerceIn(0f, 0.1f)
        val headerHeight = CrashsImGuiLayout.cardHeaderHeightPx()

        for (module in visible) {
            val state = cardStates.getOrPut(module.id) { CardState() }
            val expandable = module.action == null
            val expanded = expandable && CrashsImGuiUiState.isOpen("card.${module.id}")
            state.targetHeight = headerHeight + if (expanded) state.bodyHeight + 10f else 0f
            var lane = state.lane
            if (lane < 0 || lane >= columns) {
                lane = 0
                for (index in 1 until columns) {
                    if (columnHeights[index] < columnHeights[lane] - 0.5f) lane = index
                }
                state.lane = lane
            }
            state.targetX = originX + pad + lane * (cardWidth + gap)
            state.targetY = originY + pad + columnHeights[lane]
            columnHeights[lane] += state.targetHeight + gap
        }

        val drawOrder = visible.sortedByDescending { cardStates[it.id]?.targetY ?: 0f }
        for (module in drawOrder) {
            val state = cardStates.getOrPut(module.id) { CardState() }
            val expandable = module.action == null
            val expanded = expandable && CrashsImGuiUiState.isOpen("card.${module.id}")
            val target = if (expanded) 1f else 0f
            state.progress += (target - state.progress) * (1f - exp(-dt * CrashsImGuiLayout.animationSpeed))
            if (kotlin.math.abs(state.progress - target) < 0.005f) state.progress = target

            if (snap || state.xMotion.value.isNaN()) {
                state.xMotion.snap(state.targetX)
                state.yMotion.snap(state.targetY)
            } else {
                state.xMotion.toward(state.targetX, dt, 16f)
                state.yMotion.toward(state.targetY, dt, 16f)
            }

            val body = if (state.progress > 0.001f) state.bodyHeight * ease(state.progress) else 0f
            val totalHeight = headerHeight + body + if (body > 0f) 10f else 0f
            drawCard(
                module,
                state,
                state.xMotion.value,
                state.yMotion.value,
                cardWidth,
                headerHeight,
                body,
                totalHeight,
                expanded,
            )
        }
        return (columnHeights.maxOrNull() ?: 0f) + pad * 2f
    }

    private fun drawCard(
        module: CrashsUiModule,
        state: CardState,
        x: Float,
        y: Float,
        width: Float,
        headerHeight: Float,
        bodyHeight: Float,
        totalHeight: Float,
        expanded: Boolean,
    ) {
        val drawList = ImGui.getWindowDrawList()
        val radius = CrashsImGuiLayout.cardRadiusPx()
        val action = module.action
        if (!pushClipSafe(x, y, width, totalHeight)) return
        drawList.addRectFilled(x, y, x + width, y + totalHeight, CrashsThemePalette.imU32(CrashsImGuiTheme.SURFACE_CONTAINER_LOW), radius)
        drawList.addRect(x, y, x + width, y + totalHeight, CrashsThemePalette.imU32(CrashsImGuiTheme.OUTLINE_VARIANT), radius)

        // Hover highlight over the whole header, with the same sweep as the navigation capsules:
        // it is driven by the mouse over the full header rectangle (switch and arrow included),
        // not by the narrower click button, and it grows in/out from the left edge.
        val headerHovered = ImGui.isWindowHovered() &&
            ImGui.isMouseHoveringRect(x, y, x + width, y + headerHeight, false)
        val hoverTarget = if (headerHovered) 1f else 0f
        val dt = ImGui.getIO().deltaTime.coerceIn(0f, 0.1f)
        state.hover += (hoverTarget - state.hover) * (1f - exp(-dt * 12f))
        if (kotlin.math.abs(state.hover - hoverTarget) < 0.002f) state.hover = hoverTarget
        if (state.hover > 0.001f) {
            // Cards that are only a header (jump-to-screen modules) highlight as a whole pill;
            // expanded cards only round the top corners so the header stays attached to the body.
            val fillFlags = if (totalHeight <= headerHeight + 0.5f) ImDrawFlags.RoundCornersAll
            else ImDrawFlags.RoundCornersTop
            if (pushClipSafe(x, y, width * state.hover, headerHeight)) {
                drawList.addRectFilled(
                    x,
                    y,
                    x + width,
                    y + headerHeight,
                    CrashsThemePalette.imU32(CrashsImGuiTheme.HOVER_FILL),
                    radius,
                    fillFlags,
                )
                ImGui.popClipRect()
            }
        }

        var headerEnabled: Boolean? = null
        if (action != null) {
            ImGui.setCursorScreenPos(x, y)
            if (ImGui.invisibleButton("##cardHeader${module.id}", width, headerHeight)) {
                action.invoke()
            }
        } else {
            val enabled = module.toggleGet?.invoke() ?: true
            val reserve = if (module.toggleGet != null) 94f * textScale else 42f * textScale
            ImGui.setCursorScreenPos(x, y)
            val clicked = ImGui.invisibleButton("##cardHeader${module.id}", (width - reserve).coerceAtLeast(40f), headerHeight)
            if (clicked) {
                CrashsImGuiUiState.setOpen("card.${module.id}", !expanded)
            }
            headerEnabled = enabled
        }

        ImGui.setCursorScreenPos(x + 18f, y + (headerHeight - ImGui.getFontSize()) / 2f)
        textColoredArgb(CrashsImGuiTheme.ON_SURFACE, module.title)
        if (action != null) {
            drawHeaderArrow(x + width - 30f * textScale, y + headerHeight / 2f, ">")
        }
        if (action == null) {
            val enabled = headerEnabled ?: true
            if (module.toggleGet != null && module.toggleSet != null) {
                drawModuleSwitch(
                    module,
                    x + width - 88f * textScale,
                    y + (headerHeight - 22f * textScale) / 2f,
                    enabled,
                )
            }
            ImGui.setCursorScreenPos(x + width - 42f * textScale, y + (headerHeight - 28f * textScale) / 2f)
            if (ImGui.invisibleButton("##cardArrow${module.id}", 30f * textScale, 28f * textScale)) {
                CrashsImGuiUiState.setOpen("card.${module.id}", !expanded)
            }
            drawHeaderArrow(x + width - 30f * textScale, y + headerHeight / 2f, if (expanded) "^" else "v")
        }

        if (action == null && bodyHeight > 0.5f) {
            val bodyX = x + 16f
            val bodyY = y + headerHeight + 4f
            val bodyWidth = (width - 32f).coerceAtLeast(1f)
            ImGui.setCursorScreenPos(bodyX, bodyY)
            ImGui.beginChild(
                "##body${module.id}",
                bodyWidth,
                bodyHeight,
                false,
                ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse,
            )
            ImGui.dummy(1f, 6f)
            ImGui.textWrapped(module.description)
            ImGui.dummy(1f, 6f)
            module.content()
            // getCursorPosY() is relative to the top edge of the child and already includes the
            // description block (child windows carry no window padding). The old code started
            // measuring *after* the description, which made every expanded card ~50px too short:
            // the bottom rows, including the whole broadcast tab content, were clipped out of the
            // card and could not be clicked either.
            val measured = CrashsUiMetrics.requiredBodyHeight(ImGui.getCursorPosY())
            ImGui.endChild()
            state.bodyHeight = measured
        }
        ImGui.popClipRect()
    }

    private fun drawModuleSwitch(module: CrashsUiModule, x: Float, y: Float, enabled: Boolean) {
        val dt = ImGui.getIO().deltaTime.coerceIn(0f, 0.1f)
        val target = if (enabled) 1f else 0f
        val current = cardSwitchMotion.getOrDefault(module.id, target)
        val progress = current + (target - current) * (1f - kotlin.math.exp(-dt * 18f))
        cardSwitchMotion[module.id] = progress
        val s = textScale
        val trackWidth = 44f * s
        val trackHeight = 22f * s
        ImGui.setCursorScreenPos(x, y)
        val clicked = ImGui.invisibleButton("##moduleSwitch${module.id}", trackWidth, trackHeight)
        val drawList = ImGui.getWindowDrawList()
        val track = CrashsThemePalette.imU32(if (progress > 0.5f) CrashsImGuiTheme.PRIMARY else CrashsImGuiTheme.SWITCH_TRACK_OFF)
        drawList.addRectFilled(x, y, x + trackWidth, y + trackHeight, track, trackHeight / 2f)
        val knobX = x + 11f * s + 22f * s * progress
        drawList.addCircleFilled(
            knobX,
            y + 11f * s,
            8f * s,
            CrashsThemePalette.imU32(if (progress > 0.5f) CrashsImGuiTheme.SWITCH_KNOB_ON else CrashsImGuiTheme.SWITCH_KNOB_OFF),
        )
        if (clicked) {
            module.toggleSet?.invoke(!enabled)
            host.configChanged()
        }
    }

    private fun drawHeaderArrow(centerX: Float, centerY: Float, glyph: String) {
        val half = ImGui.getFontSize() / 2f
        ImGui.setCursorScreenPos(centerX - half, centerY - half)
        CrashsImGuiFields.colorText(CrashsImGuiTheme.ON_SURFACE_VARIANT, glyph)
    }

    /**
     * Queues a screen change (e.g. opening the broadcast query list). It is executed from the next
     * [frame] once the mouse button is released, because switching screens on the press frame made
     * the new screen inherit the pressed state and swallow the next click.
     */
    fun requestExternal(moduleId: String) {
        if (pendingExternal == null) pendingExternal = moduleId
    }

    private fun ease(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t)
    }

    private fun isConfigPage(): Boolean = page == PAGE_CONFIG

    private fun isQuickActionsPage(): Boolean = page == PAGE_QUICK_ACTIONS

    /** Used by the screen to honour the "enter Quick Actions on open" preference. */
    fun openQuickActions() {
        setPage(PAGE_QUICK_ACTIONS)
        cardStates.clear()
    }

    fun openModules() {
        setPage(PAGE_MODULES)
        cardStates.clear()
    }

    private fun setPage(value: String) {
        if (page == value) return
        val leavingQuick = page == PAGE_QUICK_ACTIONS
        page = value
        CrashsImGuiUiState.setPage(value)
        if (leavingQuick) host.quickActionsOnHidden()
    }

    /**
     * The 配置 capsule opens this page inside the content area instead of a modal: the theme lives
     * here as one group, and future settings can be added as more groups without touching navigation.
     */
    private fun drawConfigPage(originX: Float, originY: Float, contentWidth: Float): Float {
        val pad = 16f
        val style = ImGui.getStyle()
        val width = (contentWidth - style.getScrollbarSize() - 2f - pad * 2f).coerceAtLeast(240f)
        ImGui.setCursorScreenPos(originX + pad, originY + pad)
        val top = originY + pad
        if (ImGui.beginChild("##appearance", width, 0f, true)) {
            ImGui.text("外观")
            ImGui.separator()
            ImGui.text("主题模式")
            themeModeSelector()
            ImGui.spacing()
            ImGui.text("主题色")
            drawAccentSwatches()
            ImGui.spacing()
            if (ImGui.button("恢复默认主题##theme-reset")) {
                selectTheme(CrashsThemeMode.DARK, CrashsThemePalette.DEFAULT_ACCENT)
            }
            ImGui.spacing()
            ImGui.spacing()
            ImGui.text("界面缩放")
            // The slider edits a draft only: dragging it must not resize the window under the mouse.
            if (!uiScaleDraftLoaded) {
                uiScaleDraftLoaded = true
                uiScaleDraft = CrashsImGuiPrefs.uiScale()
            }
            val draft = floatArrayOf(uiScaleDraft)
            ImGui.setNextItemWidth(220f)
            if (ImGui.sliderFloat("##ui-scale", draft, 0.5f, 2.0f, "%.2fx")) {
                uiScaleDraft = draft[0]
            }
            ImGui.sameLine()
            val dirty = kotlin.math.abs(uiScaleDraft - CrashsImGuiPrefs.uiScale()) > 0.001f
            ImGui.beginDisabled(!dirty)
            if (ImGui.button("保存##ui-scale-save")) {
                CrashsImGuiPrefs.setUiScale(uiScaleDraft)
                CrashsImGuiTheme.applyUiScale(uiScaleDraft)
                host.configChanged()
            }
            ImGui.endDisabled()
            ImGui.sameLine()
            if (ImGui.button("重置为 1.00x##ui-scale-reset")) {
                uiScaleDraft = 1f
            }
            if (dirty) {
                ImGui.textDisabled("拖动滑块后按「保存」才会生效")
            }
            ImGui.spacing()
            ImGui.separator()
            ImGui.text("界面")
            val enterQuick = ImBoolean(host.quickActionsEnterOnOpen())
            if (ImGui.checkbox("进入 UI 时进入快捷操作页", enterQuick)) {
                host.setQuickActionsEnterOnOpen(enterQuick.get())
            }
        }
        ImGui.endChild()
        return (ImGui.getItemRectMaxY() - top) + pad * 2f
    }

    private fun themeModeSelector() {
        val dark = CrashsImGuiTheme.mode == CrashsThemeMode.DARK
        if (themeModeButton("深色", dark, 0)) {
            selectTheme(CrashsThemeMode.DARK, CrashsImGuiTheme.accent)
        }
        ImGui.sameLine()
        if (themeModeButton("浅色", !dark, 1)) {
            selectTheme(CrashsThemeMode.LIGHT, CrashsImGuiTheme.accent)
        }
    }

    /** Segmented button: the active mode keeps the accent tint, both share the field shape. */
    private fun themeModeButton(label: String, selected: Boolean, index: Int): Boolean {
        if (selected) {
            CrashsImGuiFields.pushStyleColor(ImGuiCol.Button, CrashsImGuiTheme.PRIMARY_CONTAINER)
            CrashsImGuiFields.pushStyleColor(ImGuiCol.ButtonHovered, CrashsImGuiTheme.PRIMARY_CONTAINER)
            CrashsImGuiFields.pushStyleColor(ImGuiCol.ButtonActive, CrashsImGuiTheme.PRIMARY_CONTAINER)
            CrashsImGuiFields.pushStyleColor(ImGuiCol.Text, CrashsImGuiTheme.ON_PRIMARY_CONTAINER)
        } else {
            CrashsImGuiFields.pushStyleColor(ImGuiCol.Button, CrashsImGuiTheme.FIELD)
            CrashsImGuiFields.pushStyleColor(ImGuiCol.ButtonHovered, CrashsImGuiTheme.FIELD_HOVER)
            CrashsImGuiFields.pushStyleColor(ImGuiCol.ButtonActive, CrashsImGuiTheme.PRIMARY_CONTAINER)
            CrashsImGuiFields.pushStyleColor(ImGuiCol.Text, CrashsImGuiTheme.ON_SURFACE)
        }
        val clicked = ImGui.button("$label##theme-mode-$index")
        ImGui.popStyleColor(4)
        return clicked && !selected
    }

    private fun drawAccentSwatches() {
        val drawList = ImGui.getWindowDrawList()
        val style = ImGui.getStyle()
        val size = CrashsImGuiTheme.fontSize * 1.1f
        val gap = 10f
        val origin = ImGui.getCursorScreenPos()
        for ((index, preset) in CrashsThemePalette.ACCENT_PRESETS.withIndex()) {
            val x = origin.x + index * (size + gap)
            ImGui.setCursorScreenPos(x, origin.y)
            val clicked = ImGui.invisibleButton("##accent$index", size, size)
            val hovered = ImGui.isItemHovered()
            val selected = CrashsImGuiTheme.accent == preset
            drawList.addRectFilled(x, origin.y, x + size, origin.y + size, CrashsThemePalette.imU32(preset), size / 2f)
            val ring = when {
                selected -> CrashsThemePalette.imU32(CrashsImGuiTheme.ON_SURFACE)
                hovered -> CrashsThemePalette.imU32(CrashsImGuiTheme.ON_SURFACE_VARIANT)
                else -> 0
            }
            if (ring != 0) {
                drawList.addCircle(
                    x + size / 2f,
                    origin.y + size / 2f,
                    size / 2f + 3f,
                    ring,
                    24,
                    if (selected) 2f else 1.5f,
                )
            }
            if (hovered) {
                ImGui.setTooltip(CrashsThemePalette.ACCENT_NAMES.getOrNull(index) ?: "")
            }
            if (clicked) {
                selectTheme(CrashsImGuiTheme.mode, preset)
            }
        }
        ImGui.setCursorScreenPos(origin.x, origin.y + size + style.getItemSpacingY())
    }

    private fun selectTheme(mode: CrashsThemeMode, accent: Int) {
        CrashsImGuiTheme.applyTheme(mode, accent)
        CrashsImGuiPrefs.setThemeMode(mode.name)
        CrashsImGuiPrefs.setAccentColor(accent)
        CrashsImGuiPrefs.save()
        debugSink?.invoke("theme: mode=$mode accent=#${accent.toUInt().toString(16)}")
    }

    /** Theme-colored text without hardcoding float components at the call site. */
    private fun textColoredArgb(argb: Int, message: String) {
        ImGui.textColored(
            ((argb ushr 16) and 0xFF) / 255f,
            ((argb ushr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
            ((argb ushr 24) and 0xFF) / 255f,
            message,
        )
    }

    /** Hand-drawn "sliders" icon for the configuration capsule (no font glyph needed). */
    private fun drawSlidersIcon(centerX: Float, centerY: Float, color: Int) {
        val s = textScale
        val drawList = ImGui.getWindowDrawList()
        val half = CrashsUiMetrics.iconBox(CrashsImGuiTheme.fontSize) / 2f
        val step = half * 0.62f
        val knobs = floatArrayOf(-0.35f, 0.45f, -0.05f)
        for (row in 0..2) {
            val y = centerY - step + row * step
            drawList.addLine(centerX - half, y, centerX + half, y, color, 1.6f * s)
            drawList.addCircleFilled(centerX + half * knobs[row], y, 2.6f * s, color)
        }
    }

    private fun drawResetPopup() {
        if (!resetPopupOpen) return
        if (ImGui.beginPopupModal("确认恢复默认")) {
            val elapsed = System.currentTimeMillis() - resetRequestedAt
            val remain = ((10_000L - elapsed) / 1000L).coerceAtLeast(0L)
            ImGui.text("确定恢复所有 Crashs Legacy 配置的默认值吗？")
            ImGui.text("窗口将在 ${remain} 秒后自动取消。")
            ImGui.spacing()
            if (ImGui.button("确认恢复")) {
                host.resetConfig()
                resetPopupOpen = false
                ImGui.closeCurrentPopup()
                category = 0
                CrashsImGuiUiState.setCategory(0)
            }
            ImGui.sameLine()
            if (ImGui.button("取消")) {
                resetPopupOpen = false
                ImGui.closeCurrentPopup()
            }
            if (elapsed >= 10_000L) {
                resetPopupOpen = false
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    private companion object {
        const val QUERY_MODULE = "query"
        const val PAGE_MODULES = "MODULES"
        const val PAGE_CONFIG = "CONFIG"
        const val PAGE_QUICK_ACTIONS = "QUICK_ACTIONS"
    }
}
