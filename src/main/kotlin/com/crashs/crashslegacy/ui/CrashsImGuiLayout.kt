package com.crashs.crashslegacy.ui

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/** Editable layout metrics for the Crashs Legacy ImGui surface. */
object CrashsImGuiLayout {

    var windowWidth: Float = 1040f
    var windowHeight: Float = 760f
    var railWidth: Float = 190f
    var navWidth: Float = 160f
    var navHeight: Float = 54f
    var cardGap: Float = 16f
    var cardRadius: Float = 16f
    var cardHeaderHeight: Float = 82f
    var animationSpeed: Float = 14f

    /** ImGui font size in pixels. Changing it needs a client restart (glyphs are baked once). */
    var uiFontSize: Float = 24f

    /**
     * Glyph supersampling factor. The atlas area grows with its square, so big fonts may need
     * 1.25 instead of 1.5 to stay inside the texture budget.
     */
    var rasterizerDensity: Float = 1.5f

    fun fontSize(): Float = uiFontSize.coerceIn(12f, 40f)

    /** ClickGUI scale applied by the theme; 1.0 = the design size. */
    var uiScale: Float = 1f

    fun windowWidthPx(): Float = windowWidth * uiScale
    fun windowHeightPx(): Float = windowHeight * uiScale
    fun cardGapPx(): Float = cardGap * uiScale
    fun cardRadiusPx(): Float = cardRadius * uiScale
    fun cardHeaderHeightPx(): Float = cardHeaderHeight * uiScale

    fun density(): Float = rasterizerDensity.coerceIn(1f, 2f)

    /**
     * Rail width, clamped so a four-character label plus its icon always fits next to the
     * scroll area. Kept in one place so the rail and the card area can never disagree.
     */
    fun rail(): Float = (railWidth * uiScale).coerceIn(150f * uiScale, 280f * uiScale)

    /**
     * Capsule width. It is deliberately independent of the label text: the label is centred
     * inside it, so longer labels get symmetric padding instead of pushing the capsule into
     * the rail edge or over the card area.
     */
    fun pillWidth(): Float = (navWidth * uiScale).coerceIn(104f * uiScale, rail() - 24f * uiScale)

    fun pillHeight(): Float = (navHeight * uiScale).coerceIn(36f * uiScale, 56f * uiScale)

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val file = CrashsUiPaths.file("layout.json") ?: return@runCatching
            if (!Files.exists(file)) {
                writeDefaults(file)
                return@runCatching
            }
            val root = JsonParser.parseString(Files.readString(file)).asJsonObject
            windowWidth = root.get("windowWidth")?.asFloat ?: windowWidth
            windowHeight = root.get("windowHeight")?.asFloat ?: windowHeight
            railWidth = root.get("railWidth")?.asFloat ?: railWidth
            navWidth = root.get("navWidth")?.asFloat ?: navWidth
            navHeight = root.get("navHeight")?.asFloat ?: navHeight
            cardGap = root.get("cardGap")?.asFloat ?: cardGap
            cardRadius = root.get("cardRadius")?.asFloat ?: cardRadius
            cardHeaderHeight = root.get("cardHeaderHeight")?.asFloat ?: cardHeaderHeight
            animationSpeed = root.get("animationSpeed")?.asFloat ?: animationSpeed
            uiFontSize = root.get("uiFontSize")?.asFloat ?: uiFontSize
            rasterizerDensity = root.get("rasterizerDensity")?.asFloat ?: rasterizerDensity
        }
    }

    /** Re-reads layout.json after `/crashs reload`. */
    fun reload() {
        loaded = false
        load()
    }

    /** Writes a documented template so the metrics are discoverable without reading the source. */
    private fun writeDefaults(file: Path) {
        val root = JsonObject()
        root.addProperty("windowWidth", windowWidth)
        root.addProperty("windowHeight", windowHeight)
        root.addProperty("railWidth", railWidth)
        root.addProperty("navWidth", navWidth)
        root.addProperty("navHeight", navHeight)
        root.addProperty("cardGap", cardGap)
        root.addProperty("cardRadius", cardRadius)
        root.addProperty("cardHeaderHeight", cardHeaderHeight)
        root.addProperty("animationSpeed", animationSpeed)
        root.addProperty("uiFontSize", uiFontSize)
        root.addProperty("rasterizerDensity", rasterizerDensity)
        Files.createDirectories(file.parent)
        Files.writeString(file, GsonBuilder().setPrettyPrinting().create().toJson(root))
    }
}
