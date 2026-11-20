package com.crashs.crashslegacy.ui

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files

/**
 * Persistent window and fold state for the Crashs Legacy ImGui surface, stored in
 * `config/crashs-legacy/ui-state.json`.
 *
 * Theme/accent/scale live in [CrashsImGuiPrefs] (ui.json) instead.
 */
object CrashsImGuiUiState {

    private const val FILE_NAME = "ui-state.json"

    private data class State(
        var category: Int = 0,
        var folds: MutableMap<String, Boolean> = mutableMapOf(),
        var tabs: MutableMap<String, Int> = mutableMapOf(),
        var windowX: Float = Float.NaN,
        var windowY: Float = Float.NaN,
        var windowWidth: Float = 980f,
        var windowHeight: Float = 720f,
        var page: String = "MODULES",
        var hudPanelX: Float = Float.NaN,
        var hudPanelY: Float = Float.NaN,
    )

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private var state = State()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val file = CrashsUiPaths.file(FILE_NAME) ?: return@runCatching
            if (!Files.exists(file)) return@runCatching
            val root = JsonParser.parseString(Files.readString(file)).asJsonObject
            state.category = root.get("category")?.asInt ?: 0
            state.windowWidth = root.get("windowWidth")?.asFloat ?: 980f
            state.windowHeight = root.get("windowHeight")?.asFloat ?: 720f
            state.windowX = root.get("windowX")?.asFloat ?: Float.NaN
            state.windowY = root.get("windowY")?.asFloat ?: Float.NaN
            root.getAsJsonObject("folds")?.entrySet()?.forEach { (key, value) ->
                state.folds[key] = value.asBoolean
            }
            root.getAsJsonObject("tabs")?.entrySet()?.forEach { (key, value) ->
                state.tabs[key] = value.asInt
            }
            state.page = root.get("page")?.asString ?: "MODULES"
            state.hudPanelX = root.get("hudPanelX")?.asFloat ?: Float.NaN
            state.hudPanelY = root.get("hudPanelY")?.asFloat ?: Float.NaN
        }
    }

    fun category(fallback: Int): Int {
        ensureLoaded()
        return state.category.takeIf { it >= 0 } ?: fallback
    }

    fun setCategory(value: Int) {
        ensureLoaded()
        state.category = value
    }

    fun isOpen(id: String): Boolean {
        ensureLoaded()
        return state.folds[id] ?: false
    }

    fun setOpen(id: String, value: Boolean) {
        ensureLoaded()
        state.folds[id] = value
    }

    /** Selected tab index for a tab bar, e.g. the 本玩家 / 其他玩家 selector. */
    fun selectedTab(id: String, fallback: Int): Int {
        ensureLoaded()
        return state.tabs[id] ?: fallback
    }

    fun setSelectedTab(id: String, value: Int) {
        ensureLoaded()
        state.tabs[id] = value
    }

    /** Which page the ClickGUI was showing: "MODULES" or "CONFIG". */
    fun page(): String {
        ensureLoaded()
        return state.page
    }

    fun setPage(value: String) {
        ensureLoaded()
        state.page = value
    }

    /** Remembered position of the HUD editor panel, so it does not always cover the top-right. */
    fun hudPanelPosition(): Pair<Float, Float>? {
        ensureLoaded()
        if (state.hudPanelX.isNaN() || state.hudPanelY.isNaN()) return null
        return state.hudPanelX to state.hudPanelY
    }

    fun setHudPanelPosition(x: Float, y: Float) {
        ensureLoaded()
        state.hudPanelX = x
        state.hudPanelY = y
    }

    fun windowPosition(): Pair<Float, Float>? {
        ensureLoaded()
        if (state.windowX.isNaN() || state.windowY.isNaN()) return null
        return state.windowX to state.windowY
    }

    fun windowSize(): Pair<Float, Float> {
        ensureLoaded()
        return state.windowWidth to state.windowHeight
    }

    fun updateWindow(x: Float, y: Float, width: Float, height: Float) {
        ensureLoaded()
        state.windowX = x
        state.windowY = y
        state.windowWidth = width
        state.windowHeight = height
    }

    fun save() {
        ensureLoaded()
        runCatching {
            val file = CrashsUiPaths.file(FILE_NAME) ?: return@runCatching
            Files.createDirectories(file.parent)
            val root = JsonObject()
            root.addProperty("category", state.category)
            root.addProperty("windowX", state.windowX)
            root.addProperty("windowY", state.windowY)
            root.addProperty("windowWidth", state.windowWidth)
            root.addProperty("windowHeight", state.windowHeight)
            root.addProperty("page", state.page)
            root.addProperty("hudPanelX", state.hudPanelX)
            root.addProperty("hudPanelY", state.hudPanelY)
            val folds = JsonObject()
            state.folds.forEach { (key, value) -> folds.addProperty(key, value) }
            root.add("folds", folds)
            val tabs = JsonObject()
            state.tabs.forEach { (key, value) -> tabs.addProperty(key, value) }
            root.add("tabs", tabs)
            Files.writeString(file, gson.toJson(root))
        }
    }

    /** Drops cached values so `/crashs reload` picks up hand edits (next open). */
    fun reload() {
        loaded = false
        state = State()
        ensureLoaded()
    }
}
