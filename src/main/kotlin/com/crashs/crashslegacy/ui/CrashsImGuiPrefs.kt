package com.crashs.crashslegacy.ui

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.crashs.crashslegacy.ui.theme.CrashsThemeMode
import com.crashs.crashslegacy.ui.theme.CrashsThemePalette
import java.nio.file.Files

/**
 * User-facing UI preferences, stored in `config/crashs-legacy/ui.json`.
 *
 * Kept separate from [CrashsImGuiUiState] (window geometry, folds, tabs) so the file stays small
 * and easy to edit by hand.
 */
object CrashsImGuiPrefs {

    private const val FILE_NAME = "ui.json"

    private data class Prefs(
        var themeMode: String = CrashsThemeMode.DARK.name,
        var accentColor: Int = CrashsThemePalette.DEFAULT_ACCENT,
        var uiScale: Float = 1f,
    )

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private var prefs = Prefs()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val file = CrashsUiPaths.file(FILE_NAME) ?: return@runCatching
            if (!Files.exists(file)) return@runCatching
            val root = JsonParser.parseString(Files.readString(file)).asJsonObject
            prefs.themeMode = root.get("themeMode")?.asString ?: CrashsThemeMode.DARK.name
            prefs.accentColor = root.get("accentColor")?.asInt ?: CrashsThemePalette.DEFAULT_ACCENT
            prefs.uiScale = root.get("uiScale")?.asFloat ?: 1f
        }
    }

    fun uiScale(): Float {
        ensureLoaded()
        return prefs.uiScale.coerceIn(0.5f, 2.0f)
    }

    fun setUiScale(value: Float) {
        ensureLoaded()
        prefs.uiScale = value.coerceIn(0.5f, 2.0f)
    }

    fun themeMode(): String {
        ensureLoaded()
        return prefs.themeMode
    }

    fun setThemeMode(value: String) {
        ensureLoaded()
        prefs.themeMode = value
    }

    fun accentColor(): Int {
        ensureLoaded()
        return prefs.accentColor
    }

    fun setAccentColor(value: Int) {
        ensureLoaded()
        prefs.accentColor = value
    }

    fun save() {
        ensureLoaded()
        runCatching {
            val file = CrashsUiPaths.file(FILE_NAME) ?: return@runCatching
            Files.createDirectories(file.parent)
            val root = JsonObject()
            root.addProperty("themeMode", prefs.themeMode)
            root.addProperty("accentColor", prefs.accentColor)
            root.addProperty("uiScale", prefs.uiScale.coerceIn(0.5f, 2.0f))
            Files.writeString(file, gson.toJson(root))
        }
    }

    /** Drops cached values so `/crashs reload` picks up hand edits. */
    fun reload() {
        loaded = false
        prefs = Prefs()
        ensureLoaded()
    }
}
