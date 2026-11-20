package com.crashs.crashslegacy.ui

import cn.enaium.fabric.imgui.ImGuiRenderable
import com.crashs.crashslegacy.serverdamage.ServerDamageDisplayManager
import imgui.callback.ImStrConsumer
import imgui.callback.ImStrSupplier
import imgui.ImGui
import imgui.ImGuiIO
import imgui.internal.ImGuiContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import com.crashs.crashslegacy.CrashsLegacyMod
import com.crashs.crashslegacy.feature.quickactions.QuickAction
import com.crashs.crashslegacy.feature.quickactions.QuickActionIcons
import com.crashs.crashslegacy.feature.quickactions.QuickActionRunner
import com.crashs.crashslegacy.feature.quickactions.QuickActionsPage
import com.crashs.crashslegacy.feature.quickactions.QuickActionsStore
import com.crashs.crashslegacy.config.CrashsImGuiConfigSync
import com.crashs.crashslegacy.config.CrashsLegacyConfig
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager
import com.crashs.crashslegacy.ui.query.BroadcastQueryScreen
import com.crashs.crashslegacy.ui.theme.CrashsImGuiTheme
import com.crashs.crashslegacy.ui.theme.CrashsThemeMode

/**
 * Minecraft binding for [CrashsImGuiSurface].
 *
 * All drawing lives in the surface so the desktop preview can render the exact same UI; this class
 * only supplies the font bytes, the real config, and the screen transitions.
 */
class CrashsImGuiScreen(
    private val parent: Screen?,
    private val focusModule: String? = null,
) : CrashsImGuiBaseScreen(parent, Component.literal("Crashs Legacy")) {

    private val binder = CrashsImGuiConfigBinder()

    private val quickActionsPage = QuickActionsPage(
        object : QuickActionsPage.Host {
            override fun execute(action: QuickAction) {
                if (QuickActionRunner.start(action)) onClose()
            }

            override fun save() = QuickActionsStore.save()
            override fun log(message: String) = CrashsLegacyMod.LOGGER.info(message)
        },
    )

    private val host = object : CrashsUiHost {
        override val modules: List<CrashsUiModule> = buildModules()

        override fun frameTick() = CrashsImGuiConfigSync.tick()

        override fun configChanged() = CrashsImGuiConfigSync.touch()

        override fun closeScreen() {
            CrashsImGuiConfigSync.flush()
            CrashsImGuiUiState.save()
            Minecraft.getInstance().setScreen(safeParent())
        }

        override fun openExternal(moduleId: String) {
            CrashsUiDebug.log { "openExternal($moduleId) from #${System.identityHashCode(this@CrashsImGuiScreen)}" }
            if (moduleId != QUERY_MODULE) return
            CrashsImGuiConfigSync.flush()
            CrashsImGuiUiState.save()
            Minecraft.getInstance().setScreen(BroadcastQueryScreen(this@CrashsImGuiScreen))
        }

        override fun openHudEditor() {
            CrashsImGuiConfigSync.flush()
            CrashsImGuiUiState.save()
            Minecraft.getInstance().setScreen(CrashsHudEditorScreen(this@CrashsImGuiScreen))
        }

        override fun resetConfig() {
            CrashsLegacyConfigManager.apply(CrashsLegacyConfig())
            CrashsImGuiConfigSync.flush()
            // apply() does not go through touch(), so the derived styling needs a nudge here.
            ServerDamageDisplayManager.markDirty()
            binder.resetBuffers()
        }

        override fun log(message: String) = CrashsLegacyMod.LOGGER.info(message)

        override fun drawQuickActionsPage(originX: Float, originY: Float, contentWidth: Float, query: String): Float =
            quickActionsPage.draw(originX, originY, contentWidth, query)

        override fun quickActionsToolbarLabel(): String =
            if (quickActionsPage.editMode) "完成" else "编辑"

        override fun toggleQuickActionsEdit() = quickActionsPage.toggleEdit()
        override fun quickActionsPopupOpen(): Boolean = quickActionsPage.popupOpen
        override fun closeQuickActionsPopup() = quickActionsPage.closePopups()
        override fun quickActionsOnHidden() = quickActionsPage.onHidden()
        override fun quickActionsEnterOnOpen(): Boolean = QuickActionsStore.enterOnOpen()
        override fun setQuickActionsEnterOnOpen(value: Boolean) = QuickActionsStore.setEnterOnOpen(value)
    }

    private val surface = CrashsImGuiSurface(host)

    /** True between the first and the last ImGui call of our own frame. */
    private var inFrame = false

    /**
     * Context captured on `init`. imgui-java hands out a fresh Java wrapper per call, so identity
     * comparison would always fail; the native pointer is the real identity.
     */
    private var savedContext: ImGuiContext? = null
    private var savedContextPtr: Long = 0L
    private var contextWarned = false

    init {
        // The broadcast page hosts the query entry now (it used to be its own module card).
        binder.onOpenQuery = { hostRequestQuery() }
        // Opened from a per-feature command (e.g. /damagetext ui): jump straight to that card.
        if (focusModule != null) {
            CrashsImGuiUiState.setOpen("card.$focusModule", true)
        }
    }

    /**
     * Runs the first time this screen becomes current.
     *
     * Note: `Screen.init(int,int)` only calls this once per instance — coming back from another
     * screen goes through [refreshWidgetPositions] instead, which is where the queued input is
     * dropped.
     */
    override fun onInit() {
        CrashsUiDebug.log { "init #${System.identityHashCode(this)} frameCount=${ImGui.getFrameCount()}" }
        CrashsImGuiLayout.load()
        CrashsImGuiTheme.logSink = { message -> CrashsLegacyMod.LOGGER.info(message) }
        savedContext = runCatching { ImGui.getCurrentContext() }.getOrNull()
        savedContextPtr = savedContext?.ptr ?: 0L
        CrashsImGuiTheme.initialize(loadFontBytes(), CrashsUiGlyphs.itemNames())
        installClipboard()
        surface.discardQueuedInput("init", debug = debugSink())
        warnIfGateMissing()
        surface.armInputGuard(2)
        CrashsImGuiUiState.setCategory(CrashsImGuiUiState.category(0))
        if (focusModule != null) {
            surface.openModules()
        } else if (QuickActionsStore.enterOnOpen()) {
            surface.openQuickActions()
        }
    }

    /**
     * Called every time this already-initialised screen becomes current again (ESC from the
     * broadcast query list, window resize). Pointer events made on the previous screen are still
     * queued inside ImGui and would replay onto the cards, so they are dropped here.
     */
    override fun onRefreshWidgetPositions() {
        CrashsUiDebug.log {
            "refreshWidgetPositions #${System.identityHashCode(this)} inFrame=$inFrame frameCount=${ImGui.getFrameCount()}"
        }
        // A window resize can reach here while a frame is being built; pumping frames then would
        // nest frames, so keep it to the "screen came back" case.
        if (inFrame) return
        surface.armInputGuard(2)
        surface.discardQueuedInput("re-entry", debug = debugSink())
        // Draining is not enough: ESC and the mouse callbacks share one GLFW poll batch, so more
        // button events can be queued after the drain. The guard hides them from the first frames.
        surface.armInputGuard(2)
    }

    override fun render(io: ImGuiIO) {
        if (savedContextPtr != 0L) {
            val currentPtr = runCatching { ImGui.getCurrentContext() }.getOrNull()?.ptr ?: 0L
            if (currentPtr != savedContextPtr) {
                if (!contextWarned) {
                    contextWarned = true
                    CrashsLegacyMod.LOGGER.warn(
                        "[UI] ImGui context changed (other mod?), restoring Crashs Legacy's context",
                    )
                }
                runCatching { savedContext?.let { ImGui.setCurrentContext(it) } }
                val restoredPtr = runCatching { ImGui.getCurrentContext() }.getOrNull()?.ptr ?: 0L
                if (restoredPtr != savedContextPtr) return
            }
        }
        inFrame = true
        surface.debugSink = debugSink()
        try {
            surface.frame(io)
        } finally {
            inFrame = false
        }
    }

    override fun onClose() {
        CrashsImGuiInput.deactivate()
        CrashsImGuiConfigSync.flush()
        CrashsImGuiUiState.save()
        Minecraft.getInstance().setScreen(safeParent())
    }

    /**
     * Esc belongs to ImGui first: it should close the confirmation modal or cancel text editing
     * before the whole screen goes away. Every other key is consumed here because ImGui already
     * receives it through its own GLFW callbacks.
     */
    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (surface.hasActivePopup()) {
                surface.closeActivePopup()
                return true
            }
            if (surface.isEditingText()) return true
            onClose()
            return true
        }
        return true
    }

    override fun shouldCloseOnEsc(): Boolean = false

    /** The screen can be replaced (disconnect, world unload); still persist pending edits. */
    override fun onRemoved() {
        CrashsImGuiConfigSync.flush()
        CrashsImGuiUiState.save()
        CrashsImGuiPrefs.save()
        surface.resetTransient()
    }

    /**
     * Returns the parent only when it is still usable. A container screen whose handler was closed
     * by the server must not be restored: its slots no longer belong to the player's current handler.
     */
    private fun safeParent(): Screen? {
        val current = parent ?: return null
        if (current is AbstractContainerScreen<*>) {
            val client = Minecraft.getInstance()
            if (current.menu !== client.player?.containerMenu) return null
        }
        return current
    }

    /** `/crashs reload`: re-reads every JSON file; also used when the GUI is open. */
    fun reloadFromDisk() {
        reloadAll()
        binder.resetBuffers()
        surface.reloadState()
    }

    /** One-shot warning when the input gate mixin could not be applied (library update). */
    private fun warnIfGateMissing() {
        if (CrashsImGuiInput.gateInstalled || gateWarnLogged) return
        gateWarnLogged = true
        CrashsLegacyMod.LOGGER.warn(
            "[UI] ImGui input gate unavailable; falling back to queue draining",
        )
    }

    /** GLFW clipboard bridge, so Ctrl+C/V works in ImGui text fields (including Chinese). */
    private fun installClipboard() {
        if (clipboardInstalled) return
        val handle = Minecraft.getInstance().window.handle()
        val io = ImGui.getIO()
        // Our own state file is the single source of truth for window geometry; let ImGui write an
        // imgui.ini next to the game and the two would fight over position/size after a resize.
        io.setIniFilename(null)
        io.setGetClipboardTextFn(object : ImStrSupplier() {
            override fun get(): String =
                runCatching { GLFW.glfwGetClipboardString(handle) }.getOrNull() ?: ""
        })
        io.setSetClipboardTextFn(object : ImStrConsumer() {
            override fun accept(text: String) {
                runCatching { GLFW.glfwSetClipboardString(handle, text) }
            }
        })
        clipboardInstalled = true
    }

    private fun buildModules(): List<CrashsUiModule> {
        val config: () -> CrashsLegacyConfig = { CrashsLegacyConfigManager.config }
        return listOf(
            CrashsUiModule(
                id = "crashslegacy",
                title = "击杀反馈",
                description = "击杀金币提示、HUD、声音与动画设置。",
                category = "MYSTERIOUS",
                content = { binder.drawCrashsLegacy() },
                toggleGet = { config().enabled },
                toggleSet = { config().enabled = it },
            ),
            CrashsUiModule(
                id = "coinsinfo",
                title = "金币统计",
                description = "会话时长、总击杀、金币与平均收益 HUD。",
                category = "MYSTERIOUS",
                content = { binder.drawCoinsInfo() },
                toggleGet = { config().coinsInfoEnabled },
                toggleSet = { config().coinsInfoEnabled = it },
            ),
            CrashsUiModule(
                id = "itemtracker",
                title = "库存管理",
                description = "捕获仓库自动收纳提示，单独显示在 HUD 上。",
                category = "MYSTERIOUS",
                content = { binder.drawItemTracker() },
                toggleGet = { config().itemTrackerEnabled },
                toggleSet = { config().itemTrackerEnabled = it },
            ),
            CrashsUiModule(
                id = "serverdamage",
                title = "服务器伤害",
                description = "服务器伤害文本的显示、背景和玩家受伤适配。",
                category = "MYSTERIOUS",
                content = { binder.drawServerDamage() },
                toggleGet = { config().serverDamageEnabled },
                toggleSet = { config().serverDamageEnabled = it },
            ),
            CrashsUiModule(
                id = "lightning",
                title = "闪电管理",
                description = "隐藏天空闪烁、闪电实体，替换闪电颜色与音效。",
                category = "MYSTERIOUS",
                content = { binder.drawLightning() },
                toggleGet = { config().lightningEnabled },
                toggleSet = { config().lightningEnabled = it },
            ),
            CrashsUiModule(
                id = "broadcast",
                title = "播报管理",
                description = "本玩家与其他玩家的聊天、BossBar 和音效播报。",
                category = "MYSTERIOUS",
                content = { binder.drawBroadcast() },
                toggleGet = { config().broadcast.enabled },
                toggleSet = { config().broadcast.enabled = it },
            ),
        )
    }

    /** The action module needs the deferred open path that lives in the surface. */
    private fun hostRequestQuery() {
        CrashsUiDebug.log { "query card action invoked" }
        surface.requestExternal(QUERY_MODULE)
    }

    /** Only builds the diagnostic callback when `/crashs debug ui` is on. */
    private fun debugSink(): ((String) -> Unit)? =
        if (CrashsUiDebug.enabled) ({ message -> CrashsUiDebug.log { message } }) else null

    private fun loadFontBytes(): ByteArray? = CrashsImGuiScreen::class.java
        .getResourceAsStream("/assets/crashs-legacy/fonts/misans.ttf")
        ?.use { it.readBytes() }

    companion object {
        const val QUERY_MODULE = "query"
        var clipboardInstalled = false
        var gateWarnLogged = false

        /** Re-reads config, UI prefs/state and layout without requiring a screen instance. */
        fun reloadAll() {
            CrashsLegacyConfigManager.reload()
            QuickActionsStore.reload()
            QuickActionIcons.clearCache()
            QuickActionRunner.reset()
            CrashsImGuiPrefs.reload()
            CrashsImGuiUiState.reload()
            CrashsImGuiLayout.reload()
            val mode = runCatching { CrashsThemeMode.valueOf(CrashsImGuiPrefs.themeMode()) }
                .getOrDefault(CrashsThemeMode.DARK)
            CrashsImGuiTheme.applyTheme(mode, CrashsImGuiPrefs.accentColor())
            CrashsImGuiTheme.applyUiScale(CrashsImGuiPrefs.uiScale())
            CrashsImGuiConfigSync.onChanged?.invoke()
        }
    }
}
