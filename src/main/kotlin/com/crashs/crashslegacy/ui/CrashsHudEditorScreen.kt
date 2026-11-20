package com.crashs.crashslegacy.ui

import cn.enaium.fabric.imgui.ImGuiRenderable
import imgui.ImGui
import imgui.ImGuiIO
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiMouseCursor
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import com.crashs.crashslegacy.CrashsLegacyMod
import com.crashs.crashslegacy.config.CrashsImGuiConfigSync
import com.crashs.crashslegacy.config.CrashsLegacyConfig
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager
import com.crashs.crashslegacy.hud.HUD_COMPONENTS
import com.crashs.crashslegacy.hud.HudComponent
import com.crashs.crashslegacy.hud.HudDragState
import com.crashs.crashslegacy.hud.HudPlacement
import com.crashs.crashslegacy.hud.HudSnap
import com.crashs.crashslegacy.ui.theme.CrashsImGuiTheme
import com.crashs.crashslegacy.ui.theme.CrashsThemePalette

/**
 * Visual editor for every HUD component in [HUD_COMPONENTS].
 *
 * The HUDs keep being drawn by their real render paths; this screen only relaxes the "no HUD while a
 * screen is open" rule for itself and paints drag boxes + a panel on top with ImGui. Clicking a box
 * selects that component, and the panel follows the last selection instead of listing everything.
 *
 * Coordinates: HUDs work in Minecraft's scaled screen space, ImGui works in framebuffer pixels, so
 * both are bridged by `io.displaySize / window.guiScaledWidth` (the GUI scale factor).
 */
class CrashsHudEditorScreen(private val parent: Screen?) : CrashsImGuiBaseScreen(parent, Component.literal("HUD 编辑器")) {

    private val drag = HudDragState()
    private val buffers = HashMap<String, ImString>()
    private val switchMotion = HashMap<String, Float>()
    private val errors = HashSet<String>()
    private var selected: HudComponent? = null
    private var lastSelectedId: String? = null
    private var firstFrame = true
    private var guideX: Float? = null
    private var guideY: Float? = null
    private var guideXSource = HudSnap.Source.NONE
    private var guideYSource = HudSnap.Source.NONE
    private var panelX = Float.NaN
    private var panelY = Float.NaN
    private var panelInitialised = false

    override fun onInit() {
        CrashsImGuiLayout.load()
        CrashsImGuiTheme.logSink = { message -> CrashsLegacyMod.LOGGER.info(message) }
        CrashsImGuiTheme.initialize(fontBytes(), CrashsUiGlyphs.itemNames())
        firstFrame = true
        panelInitialised = false
    }

    override fun render(io: ImGuiIO) {
        CrashsImGuiFrameState.active = true
        try {
            renderBody(io)
        } finally {
            CrashsImGuiFrameState.active = false
        }
    }

    private fun renderBody(io: ImGuiIO) {
        val client = Minecraft.getInstance()
        val scaledWidth = client.window.guiScaledWidth.toFloat()
        val scaledHeight = client.window.guiScaledHeight.toFloat()
        val factor = if (scaledWidth > 0f) io.displaySizeX / scaledWidth else 1f
        val config = CrashsLegacyConfigManager.config

        val enabled = HUD_COMPONENTS.filter { it.isEnabled(config) }
        if (selected == null || selected !in enabled) {
            selected = enabled.firstOrNull { it.id == lastSelectedId } ?: enabled.firstOrNull()
            lastSelectedId = selected?.id
        }
        val measured = enabled.mapNotNull { component ->
            component.measure(config, scaledWidth, scaledHeight)?.let { component to it }
        }

        handleInput(io, measured, factor, scaledWidth, scaledHeight, config)
        for ((component, bounds) in measured) {
            drawBox(component, bounds, factor, component === selected)
        }
        drawDragGuides(factor)
        drawPanel(config, measured, scaledWidth)
        firstFrame = false
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            onClose()
            return true
        }
        return super.keyPressed(input)
    }

    override fun onClose() {
        drag.end()
        CrashsImGuiConfigSync.flush()
        CrashsImGuiUiState.save()
        CrashsImGuiPrefs.save()
        Minecraft.getInstance().setScreen(parent)
    }

    override fun onRemoved() {
        CrashsImGuiConfigSync.flush()
        CrashsImGuiUiState.save()
        CrashsImGuiPrefs.save()
    }

    // ---- interaction ----

    private fun handleInput(
        io: ImGuiIO,
        measured: List<Pair<HudComponent, HudPlacement.Bounds>>,
        factor: Float,
        scaledWidth: Float,
        scaledHeight: Float,
        config: CrashsLegacyConfig,
    ) {
        if (firstFrame) return
        val mouse = io.getMousePos()
        val mouseX = mouse.x / factor
        val mouseY = mouse.y / factor
        val hit = measured
            .filter { (_, bounds) -> bounds.expandedTo(MIN_DRAG_WIDTH, MIN_DRAG_HEIGHT).contains(mouseX, mouseY) }
            .minByOrNull { (_, bounds) -> bounds.width * bounds.height }

        if (!drag.dragging && !io.getWantCaptureMouse() && io.getMouseClicked(0) && hit != null) {
            selected = hit.first
            lastSelectedId = hit.first.id
            drag.begin(mouseX, mouseY, hit.second, hit.second.expandedTo(MIN_DRAG_WIDTH, MIN_DRAG_HEIGHT))
        }

        if (!drag.dragging) {
            if (!io.getWantCaptureMouse() && hit != null) {
                ImGui.setMouseCursor(ImGuiMouseCursor.ResizeAll)
            }
            return
        }

        val component = selected
        val bounds = component?.measure(config, scaledWidth, scaledHeight)
        if (component == null || bounds == null) {
            drag.end()
            return
        }
        if (io.getMouseDown(0)) {
            val percent = drag.drag(
                mouseX = mouseX,
                mouseY = mouseY,
                screenWidth = scaledWidth,
                screenHeight = scaledHeight,
                boxWidth = bounds.width,
                boxHeight = bounds.height,
                right = component.right(config),
                bottomAnchor = component.bottomAnchor(config),
                others = measured.filter { it.first !== component }.map { it.second },
            )
            if (percent != null) {
                component.moveTo(config, percent.percentX, percent.percentY)
                guideX = percent.guideX
                guideY = percent.guideY
                guideXSource = percent.guideXSource
                guideYSource = percent.guideYSource
                CrashsImGuiConfigSync.touch()
            }
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeAll)
        } else {
            drag.end()
            guideX = null
            guideY = null
        }
    }

    private fun drawBox(
        component: HudComponent,
        bounds: HudPlacement.Bounds,
        factor: Float,
        selected: Boolean,
    ) {
        val drawList = ImGui.getForegroundDrawList()
        val left = bounds.x * factor
        val top = bounds.y * factor
        val right = bounds.right * factor
        val bottom = bounds.bottom * factor
        val accent = CrashsThemePalette.imU32(CrashsImGuiTheme.PRIMARY)
        val color = if (selected) accent else CrashsThemePalette.imU32(CrashsImGuiTheme.BOX_IDLE)

        val hit = bounds.expandedTo(MIN_DRAG_WIDTH, MIN_DRAG_HEIGHT)
        if (hit.width > bounds.width || hit.height > bounds.height) {
            val hint = CrashsThemePalette.imU32(CrashsImGuiTheme.DRAG_HINT)
            drawList.addRect(left, top, (hit.x + hit.width) * factor, (hit.y + hit.height) * factor, hint, 0f, 0, 1f)
        }
        drawList.addRect(left, top, right, bottom, color, 0f, 0, if (selected) 1.5f else 1f)

        if (selected) {
            val handle = 4f
            for ((hx, hy) in listOf(left to top, right to top, left to bottom, right to bottom)) {
                drawList.addRectFilled(hx - handle, hy - handle, hx + handle, hy + handle, accent, 2f)
            }
        }

        val label = component.title
        val fontSize = CrashsImGuiTheme.fontSize.roundToInt()
        val textWidth = ImGui.calcTextSize(label).x
        val labelY = if (top >= 26f) top - 24f else bottom + 4f
        drawList.addRectFilled(
            left,
            labelY,
            left + textWidth + 16f,
            labelY + 20f,
            CrashsThemePalette.imU32(CrashsImGuiTheme.PRIMARY_CONTAINER),
            4f,
        )
        drawList.addText(
            ImGui.getFont(),
            fontSize,
            ImVec2(left + 8f, labelY + 2f),
            CrashsThemePalette.imU32(CrashsImGuiTheme.ON_PRIMARY_CONTAINER),
            label,
        )
    }

    /** Guide lines produced by the snapping (screen edge/centre or another component's edge). */
    private fun drawDragGuides(factor: Float) {
        val x = guideX
        val y = guideY
        if (x == null && y == null) return
        val drawList = ImGui.getForegroundDrawList()
        val width = ImGui.getIO().displaySizeX
        val height = ImGui.getIO().displaySizeY
        if (x != null) {
            val screenX = x * factor
            drawList.addLine(screenX, 0f, screenX, height, guideColor(guideXSource), 1f)
        }
        if (y != null) {
            val screenY = y * factor
            drawList.addLine(0f, screenY, width, screenY, guideColor(guideYSource), 1f)
        }
    }

    private fun guideColor(source: HudSnap.Source): Int =
        CrashsThemePalette.imU32(if (source == HudSnap.Source.COMPONENT) CrashsImGuiTheme.GUIDE_COMPONENT else CrashsImGuiTheme.GUIDE_SCREEN)

    // ---- panel ----

    private fun drawPanel(
        config: CrashsLegacyConfig,
        measured: List<Pair<HudComponent, HudPlacement.Bounds>>,
        scaledWidth: Float,
    ) {
        val io = ImGui.getIO()
        val width = 320f
        val defaultX = (io.displaySizeX - width - 12f).coerceAtLeast(8f)
        val defaultY = 12f

        if (!panelInitialised) {
            panelInitialised = true
            val saved = CrashsImGuiUiState.hudPanelPosition()
            var x = saved?.first ?: defaultX
            var y = saved?.second ?: defaultY
            if (!x.isFinite() || x < 40f - width || x > io.displaySizeX - 80f) x = defaultX
            if (!y.isFinite() || y < 0f || y > io.displaySizeY - 40f) y = defaultY
            panelX = x
            panelY = y
            ImGui.setNextWindowPos(x, y, ImGuiCond.Always)
            ImGui.setNextWindowSizeConstraints(width, 0f, width, 10000f)
        }

        // Same surface as every other floating panel (the configuration modal), not the darker
        // window background the ClickGUI uses behind its cards.
        CrashsImGuiFields.pushStyleColor(ImGuiCol.WindowBg, CrashsImGuiTheme.SURFACE_CONTAINER)
        CrashsImGuiFields.pushStyleColor(ImGuiCol.TitleBg, CrashsImGuiTheme.SURFACE_CONTAINER_HIGH)
        CrashsImGuiFields.pushStyleColor(ImGuiCol.TitleBgActive, CrashsImGuiTheme.SURFACE_CONTAINER_HIGH)
        val flags = ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.AlwaysAutoResize
        if (ImGui.begin("HUD 编辑器##crashs-hud-editor", flags)) {
            val component = selected
            if (component == null) {
                ImGui.text("没有启用的 HUD 组件")
                CrashsImGuiFields.colorText(CrashsImGuiTheme.ON_SURFACE_VARIANT, "在「击杀反馈 → HUD」或「金币统计」里开启")
            } else {
                drawComponentControls(component, config, measured, scaledWidth)
            }

            ImGui.separator()
            if (ImGui.button("重置面板位置##hud-panel-reset")) {
                panelX = defaultX
                panelY = defaultY
                ImGui.setWindowPos(panelX, panelY)
                CrashsImGuiUiState.setHudPanelPosition(panelX, panelY)
            }
            ImGui.sameLine()
            if (ImGui.button("完成##hud-done")) {
                onClose()
            }

            val pos = ImGui.getWindowPos()
            if (pos.x != panelX || pos.y != panelY) {
                panelX = pos.x
                panelY = pos.y
                CrashsImGuiUiState.setHudPanelPosition(pos.x, pos.y)
            }
        }
        ImGui.end()
        ImGui.popStyleColor(3)
    }

    private fun drawComponentControls(
        component: HudComponent,
        config: CrashsLegacyConfig,
        measured: List<Pair<HudComponent, HudPlacement.Bounds>>,
        scaledWidth: Float,
    ) {
        ImGui.text(component.title)
        ImGui.separator()

        editableFloat(
            key = "hudScale.${component.id}",
            label = "缩放",
            value = component.scale(config),
            min = 0.5f,
            max = 2.5f,
            format = "%.2f",
        ) { component.setScale(config, it) }
        val sliderValue = floatArrayOf(component.scale(config))
        ImGui.setNextItemWidth(-1f)
        if (ImGui.sliderFloat("##hud-scale-${component.id}", sliderValue, 0.5f, 2.5f, "%.2f")) {
            component.setScale(config, sliderValue[0])
            CrashsImGuiConfigSync.touch()
        }

        val right = component.right(config)
        switchRow("右侧锚点", right, "hud-anchor-${component.id}") { newRight ->
            if (newRight != right) {
                mirrorAnchor(component, config, measured, scaledWidth)
                component.setRight(config, newRight)
                CrashsImGuiConfigSync.touch()
            }
        }

        val (percentX, percentY) = component.position(config)
        editableFloat("hudX.${component.id}", "X 位置", percentX, 0f, 100f, "%.1f") {
            component.moveTo(config, it, component.position(config).second)
        }
        editableFloat("hudY.${component.id}", "Y 位置", percentY, 0f, 100f, "%.1f") {
            component.moveTo(config, component.position(config).first, it)
        }

        val bounds = measured.firstOrNull { it.first === component }?.second
        if (bounds == null) {
            CrashsImGuiFields.colorText(CrashsImGuiTheme.ON_SURFACE_VARIANT, "当前尺寸不可测量")
        } else {
            CrashsImGuiFields.colorText(CrashsImGuiTheme.ON_SURFACE_VARIANT, "尺寸 %.0f × %.0f".format(bounds.width, bounds.height))
        }

        ImGui.separator()
        if (ImGui.button("重置该组件##hud-reset-${component.id}")) {
            component.reset(config)
            CrashsImGuiConfigSync.touch()
        }
    }

    /** Left/right anchors are mirror images, so the mirrored percent is `max - percent`. */
    private fun mirrorAnchor(
        component: HudComponent,
        config: CrashsLegacyConfig,
        measured: List<Pair<HudComponent, HudPlacement.Bounds>>,
        scaledWidth: Float,
    ) {
        val boxWidth = measured.firstOrNull { it.first === component }?.second?.width ?: return
        if (scaledWidth <= 0f) return
        val maxPercent = (scaledWidth - boxWidth) / scaledWidth * 100f
        val (percentX, percentY) = component.position(config)
        component.moveTo(config, (maxPercent - percentX).coerceIn(0f, 100f), percentY)
    }

    /** Label on the left, pill switch on the right — the same control the module cards use. */
    private fun switchRow(label: String, value: Boolean, key: String, onChange: (Boolean) -> Unit) {
        val dt = ImGui.getIO().deltaTime.coerceIn(0f, 0.1f)
        val target = if (value) 1f else 0f
        val current = switchMotion.getOrDefault(key, target)
        val progress = current + (target - current) * (1f - kotlin.math.exp(-dt * 18f))
        switchMotion[key] = if (abs(progress - target) < 0.002f) target else progress

        ImGui.text(label)
        ImGui.sameLine()
        val trackWidth = 42f
        val trackHeight = 20f
        val available = ImGui.getContentRegionAvailX()
        if (available > trackWidth + 8f) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + available - trackWidth)
        }
        val origin = ImGui.getCursorScreenPos()
        val x = origin.x
        val y = origin.y + 2f
        val clicked = ImGui.invisibleButton("##$key", trackWidth, trackHeight)
        val drawList = ImGui.getWindowDrawList()
        val track = CrashsThemePalette.imU32(if (progress > 0.5f) CrashsImGuiTheme.PRIMARY else CrashsImGuiTheme.SWITCH_TRACK_OFF)
        val knob = CrashsThemePalette.imU32(if (progress > 0.5f) CrashsImGuiTheme.SWITCH_KNOB_ON else CrashsImGuiTheme.SWITCH_KNOB_OFF)
        drawList.addRectFilled(x, y, x + trackWidth, y + trackHeight, track, trackHeight / 2f)
        val knobX = x + trackHeight / 2f + (trackWidth - trackHeight) * progress
        drawList.addCircleFilled(knobX, y + trackHeight / 2f, trackHeight / 2f - 2f, knob)
        if (clicked) onChange(!value)
    }

    /** Label on the left, right-aligned numeric input; commits through [onChange]. */
    private fun editableFloat(
        key: String,
        label: String,
        value: Float,
        min: Float,
        max: Float,
        format: String,
        onChange: (Float) -> Unit,
    ) {
        val formatted = String.format(Locale.ROOT, format, value)
        val buffer = buffers.getOrPut(key) { ImString(formatted, 24) }
        if (key in errors) {
            CrashsImGuiFields.colorText(CrashsImGuiTheme.ERROR, "!")
            ImGui.sameLine()
        }
        ImGui.text(label)
        ImGui.sameLine()
        val fieldWidth = CrashsImGuiFields.numericWidth()
        val available = ImGui.getContentRegionAvailX()
        if (available > fieldWidth) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + available - fieldWidth)
        }
        ImGui.setNextItemWidth(fieldWidth)
        val entered = ImGui.inputText("##$key", buffer)
        if (entered || ImGui.isItemDeactivatedAfterEdit()) {
            val parsed = buffer.get().replace(',', '.').toFloatOrNull()
            if (parsed == null) {
                errors += key
            } else {
                errors -= key
                val clamped = parsed.coerceIn(min, max)
                buffer.set(String.format(Locale.ROOT, format, clamped))
                onChange(clamped)
                CrashsImGuiConfigSync.touch()
            }
        } else if (!ImGui.isItemActive() && buffer.get() != formatted) {
            buffer.set(formatted)
        }
    }

    private fun fontBytes(): ByteArray? = CrashsHudEditorScreen::class.java
        .getResourceAsStream("/assets/crashs-legacy/fonts/misans.ttf")
        ?.use { it.readBytes() }

    private companion object {
        /** Minimum hit box: small HUDs must still be grabbable, without inflating normal ones. */
        const val MIN_DRAG_WIDTH = 20f
        const val MIN_DRAG_HEIGHT = 10f
    }
}
