package com.crashs.crashslegacy.ui.theme

import imgui.ImFontConfig
import imgui.ImFontGlyphRangesBuilder
import imgui.ImGui
import imgui.ImGuiStyle
import imgui.flag.ImGuiCol
import com.crashs.crashslegacy.ui.CrashsImGuiLayout
import com.crashs.crashslegacy.ui.CrashsImGuiPrefs
import com.crashs.crashslegacy.ui.CrashsImGuiUiState

/** Complete color system (dark/light base scheme + accent) and ImGui style for the surface. */
object CrashsImGuiTheme {

    /** Active base scheme; switch with [applyTheme]. */
    var mode: CrashsThemeMode = CrashsThemeMode.DARK
        private set

    /** Active accent color; switch with [applyTheme]. */
    var accent: Int = CrashsThemePalette.DEFAULT_ACCENT
        private set

    /**
     * The complete color system of the active theme. Every draw site and every `ImGuiCol` entry is
     * resolved through it — nothing falls back to an ImGui default.
     */
    var colors: CrashsThemeColors = CrashsThemePalette.colors(mode, accent)
        private set

    // ---- structure ----
    val SURFACE get() = colors.window
    val WINDOW_BORDER get() = colors.windowBorder
    val SURFACE_CONTAINER_LOW get() = colors.card
    val SURFACE_CONTAINER get() = colors.popover
    val SURFACE_CONTAINER_HIGH get() = colors.cardHeader
    val SURFACE_CONTAINER_HIGHEST get() = colors.capsuleHover
    val RAIL get() = colors.rail
    val TOOLTIP_BG get() = colors.tooltip
    val SHADOW get() = colors.shadow

    // ---- controls ----
    val FIELD get() = colors.field
    val FIELD_HOVER get() = colors.fieldHover
    val FIELD_ACTIVE get() = colors.fieldActive
    val CAPSULE_BASE get() = colors.capsuleBase
    val CAPSULE_HOVER get() = colors.capsuleHover
    val SWITCH_TRACK_OFF get() = colors.switchTrackOff
    val SWITCH_KNOB_OFF get() = colors.switchKnobOff
    val SWITCH_KNOB_ON get() = colors.switchKnobOn
    val BORDER get() = colors.border
    val BORDER_STRONG get() = colors.borderStrong

    // ---- text ----
    val ON_SURFACE get() = colors.text
    val ON_SURFACE_VARIANT get() = colors.textMuted
    val TEXT_DISABLED get() = colors.textDisabled
    val ICON get() = colors.icon

    // ---- HUD editor overlays ----
    val GUIDE_SCREEN get() = colors.guideScreen
    val GUIDE_COMPONENT get() = colors.guideComponent
    val BOX_IDLE get() = colors.boxIdle
    val DRAG_HINT get() = colors.dragHint

    // ---- accent ----
    val PRIMARY get() = colors.accent
    val ACCENT_HOVER get() = colors.accentHover
    val ACCENT_PRESSED get() = colors.accentPressed
    val ON_PRIMARY get() = colors.onAccent
    val PRIMARY_CONTAINER get() = colors.accentContainer
    val ON_PRIMARY_CONTAINER get() = colors.onAccentContainer

    /** Hover fill shared by the navigation capsules and the module card headers. */
    val HOVER_FILL get() = colors.capsuleHover

    // ---- status ----
    val SUCCESS get() = colors.success
    val WARNING get() = colors.warning
    val INFO get() = colors.info
    val ERROR get() = colors.error

    /** Legacy aliases kept so older draw sites keep compiling. */
    val OUTLINE get() = colors.borderStrong
    val OUTLINE_VARIANT get() = colors.border
    val SECONDARY_CONTAINER get() = colors.warning
    val ON_SECONDARY_CONTAINER get() = colors.onAccent

    private var styleInitialized = false
    private var fontsInstalled = false
    private var bakedFontSize = 0f

    /** ClickGUI scale (0.5..2.0); persisted in the UI state. */
    var uiScale: Float = 1f
        private set

    /** Scale the atlas was baked at; live changes use io.fontGlobalScale until the next bake. */
    private var bakedScale = 1f
    private var fontData: ByteArray? = null
    private var extraGlyphs: String = ""

    /**
     * Characters used by the Crashs Legacy surface that the 2500-glyph
     * "Chinese simplified common" range does not cover (for example 馈 in 击杀反馈 or 聊 in
     * 聊天). ImGui substitutes '?' for any glyph missing from the atlas, so every CJK string
     * drawn through ImGui has to be registered here. `UiGlyphCoverageTest` fails when a character
     * used in the sources is missing, so this list cannot silently fall behind.
     */
    internal const val EXTRA_GLYPHS = "未命名播报管理启用本玩家其他存储关键词红色保取消恢复默认总开；闭后不解析任何。记录格式当前：文件已装备称包含该时使分类显示聊天的服务器会被为创建独立间持续音效识别成功放末影龙死亡凋零经验获到把结果写入数据库兼容路径！恭喜击杀了并得在中摸出进行重铸，息由失败型（）品质颜等级怪物材料拦截仅按口执金查询接收奖励箱缀原始自己刷新清空上一页下匹配条没有第制整宝话币双二平均值累计或字滚动以缓方单位千缩基础正则无声量高连组尾模指定体右侧锚点置统窗最大目距待淘汰透明度样头部来源额背景边框阴移画逐隔线除伤害受全局能秒反馈提、与设长益和适打现历史界面确搜索块调试所吗？将常规控是否处系避免栏同只于你荒林羊支加括号说校响地终仍主根次选择超过决内每留之垂直淡让从实际旧滑即更往左宽稳布绘钟小算水屏幕百比角对齐致相顶横外填充近列表准缘另离底隐藏完紧凑半多停知错误详情泡糖冠军盖亚仇故障艺术混沌序曲初辐射危机奇循环个卡牌暴爆炸碰撞荣誉普通载具生十种元素丢弃给添负纳端木火土风雷光暗珊瑚琥珀辑拽契抑烁→杉桦戮溢"

    /**
     * Glyph ranges baked into the atlas: the common CJK range plus every character the UI uses.
     * Pure ImGui code, no Minecraft dependency, so unit tests can call it without a client.
     */
    private val fullCjkText: String by lazy {
        buildString(22000) {
            for (code in 0x4E00..0x9FFF) append(code.toChar())
            for (code in 0x3400..0x4DBF) append(code.toChar())
            for (code in 0xF900..0xFAFF) append(code.toChar())
        }
    }

    internal fun buildGlyphRanges(atlas: imgui.ImFontAtlas, fullCjk: Boolean = true): ShortArray =
        ImFontGlyphRangesBuilder().apply {
            addRanges(atlas.glyphRangesDefault)
            addRanges(atlas.glyphRangesChineseSimplifiedCommon)
            addText(EXTRA_GLYPHS)
            addText(extraGlyphs)
            if (fullCjk) addText(fullCjkText)
        }.buildRanges()

    /** Optional sink for font diagnostics; the host wires it to its own logger. */
    var logSink: ((String) -> Unit)? = null

    private fun log(message: String) {
        logSink?.invoke(message)
    }

    /**
     * ImGui font size in pixels, read from `config/crashs-legacy-imgui-layout.json`
     * (`uiFontSize`). Every text call in the surface scales from this value, capsule labels and
     * icons included.
     *
     * The glyph atlas is baked once per session, so changing the size only takes effect after a
     * client restart. While the atlas exists this returns the size that was actually baked, so
     * editing the JSON cannot desynchronise the layout from the font that is on screen.
     * Atlas cost grows with size × density: 24px at 1.5 is 2048×4096 (32 MB), 20px and up at 2.0
     * is 4096×4096 (64 MB).
     */
    val fontSize: Float
        get() {
            CrashsImGuiLayout.load()
            return (CrashsImGuiLayout.fontSize() * uiScale).coerceIn(8f, 80f)
        }

    /**
     * Glyph supersampling factor, read from the same JSON (`rasterizerDensity`). Crisper, but the
     * atlas area grows with its square.
     */
    private val density: Float
        get() {
            CrashsImGuiLayout.load()
            return CrashsImGuiLayout.density()
        }

    fun initialize(fontBytes: ByteArray?, extraGlyphs: String = "") {
        if (extraGlyphs.isNotEmpty()) this.extraGlyphs = extraGlyphs
        val persistedScale = runCatching { CrashsImGuiPrefs.uiScale() }.getOrDefault(1f).coerceIn(0.5f, 2.0f)
        uiScale = persistedScale
        CrashsImGuiLayout.uiScale = persistedScale
        if (fontBytes != null) fontData = fontBytes
        installFonts()
        if (styleInitialized) return
        styleInitialized = true

        val style = ImGui.getStyle()
        applyStyleMetrics(style, persistedScale)
        loadStoredTheme()
        applyStyleColors(style)
    }

    /** Applies a new ClickGUI scale immediately (layout + style); the atlas is re-baked next open. */
    fun applyUiScale(scale: Float) {
        val clamped = scale.coerceIn(0.5f, 2.0f)
        uiScale = clamped
        CrashsImGuiLayout.uiScale = clamped
        if (!styleInitialized) return
        runCatching {
            applyStyleMetrics(ImGui.getStyle(), clamped)
            val global = if (bakedScale > 0f) clamped / bakedScale else 1f
            ImGui.getIO().fontGlobalScale = global
        }
    }

    /** Paddings and rounding at [scale]; explicit values so repeated calls never accumulate. */
    private fun applyStyleMetrics(style: ImGuiStyle, scale: Float) {
        style.setWindowRounding(12f * scale)
        style.setChildRounding(10f * scale)
        style.setFrameRounding(6f * scale)
        style.setGrabRounding(6f * scale)
        style.setPopupRounding(10f * scale)
        style.setScrollbarRounding(8f * scale)
        style.setWindowPadding(12f * scale, 12f * scale)
        style.setFramePadding(25f * scale, 11f * scale)
        style.setItemSpacing(14f * scale, 14f * scale)
        style.setScrollbarSize(11f * scale)
    }

    /** Switches scheme and accent; every draw site picks the new colors up on the next frame. */
    fun applyTheme(newMode: CrashsThemeMode, newAccent: Int) {
        val changed = mode != newMode || accent != newAccent
        mode = newMode
        accent = newAccent
        colors = CrashsThemePalette.colors(mode, accent)
        if (changed && styleInitialized) applyStyleColors(ImGui.getStyle())
    }

    /** Reads the persisted theme; falls back to the dark default when nothing was stored. */
    private fun loadStoredTheme() {
        mode = runCatching { CrashsThemeMode.valueOf(CrashsImGuiPrefs.themeMode()) }
            .getOrDefault(CrashsThemeMode.DARK)
        accent = CrashsImGuiPrefs.accentColor()
        colors = CrashsThemePalette.colors(mode, accent)
    }

    /**
     * Complete `ImGuiCol` table: every one of the 62 slots maps to a theme token, so no element can
     * fall back to an ImGui default (the light scheme used to show ImGui's navy tabs that way).
     * `CrashsThemeCoverageTest` reflects over `ImGuiCol` and fails if a slot is missing here.
     */
    internal fun colorEntries(): List<Pair<Int, Int>> = with(colors) {
        listOf(
            ImGuiCol.Text to text,
            ImGuiCol.TextDisabled to textDisabled,
            ImGuiCol.WindowBg to window,
            ImGuiCol.ChildBg to card,
            ImGuiCol.PopupBg to popover,
            ImGuiCol.Border to border,
            ImGuiCol.BorderShadow to shadow,
            ImGuiCol.FrameBg to field,
            ImGuiCol.FrameBgHovered to fieldHover,
            ImGuiCol.FrameBgActive to fieldActive,
            ImGuiCol.TitleBg to cardHeader,
            ImGuiCol.TitleBgActive to cardHeader,
            ImGuiCol.TitleBgCollapsed to windowBorder,
            ImGuiCol.MenuBarBg to cardHeader,
            ImGuiCol.ScrollbarBg to scrollbarBg,
            ImGuiCol.ScrollbarGrab to scrollbarGrab,
            ImGuiCol.ScrollbarGrabHovered to scrollbarGrabHover,
            ImGuiCol.ScrollbarGrabActive to scrollbarGrabActive,
            ImGuiCol.CheckMark to checkMark,
            ImGuiCol.SliderGrab to sliderGrab,
            ImGuiCol.SliderGrabActive to sliderGrabActive,
            ImGuiCol.Button to button,
            ImGuiCol.ButtonHovered to buttonHover,
            ImGuiCol.ButtonActive to buttonPressed,
            ImGuiCol.Header to header,
            ImGuiCol.HeaderHovered to headerHover,
            ImGuiCol.HeaderActive to headerActive,
            ImGuiCol.Separator to separator,
            ImGuiCol.SeparatorHovered to separatorHover,
            ImGuiCol.SeparatorActive to separatorActive,
            ImGuiCol.ResizeGrip to resizeGrip,
            ImGuiCol.ResizeGripHovered to resizeGripHover,
            ImGuiCol.ResizeGripActive to resizeGripActive,
            ImGuiCol.InputTextCursor to inputCursor,
            ImGuiCol.TabHovered to tabHover,
            ImGuiCol.Tab to tab,
            ImGuiCol.TabSelected to tabSelected,
            ImGuiCol.TabSelectedOverline to tabSelectedOverline,
            ImGuiCol.TabDimmed to tabDimmed,
            ImGuiCol.TabDimmedSelected to tabDimmedSelected,
            ImGuiCol.TabDimmedSelectedOverline to tabSelectedOverline,
            ImGuiCol.DockingPreview to accentHover,
            ImGuiCol.DockingEmptyBg to windowBorder,
            ImGuiCol.PlotLines to plotLines,
            ImGuiCol.PlotLinesHovered to plotLinesHover,
            ImGuiCol.PlotHistogram to plotHistogram,
            ImGuiCol.PlotHistogramHovered to plotHistogramHover,
            ImGuiCol.TableHeaderBg to tableHeader,
            ImGuiCol.TableBorderStrong to tableBorderStrong,
            ImGuiCol.TableBorderLight to tableBorderLight,
            ImGuiCol.TableRowBg to tableRow,
            ImGuiCol.TableRowBgAlt to tableRowAlt,
            ImGuiCol.TextLink to textLink,
            ImGuiCol.TextSelectedBg to textSelectedBg,
            ImGuiCol.TreeLines to treeLines,
            ImGuiCol.DragDropTarget to dragDropTarget,
            ImGuiCol.DragDropTargetBg to dragDropTargetBg,
            ImGuiCol.UnsavedMarker to unsavedMarker,
            ImGuiCol.NavCursor to navCursor,
            ImGuiCol.NavWindowingHighlight to navWindowingHighlight,
            ImGuiCol.NavWindowingDimBg to navWindowingDim,
            ImGuiCol.ModalWindowDimBg to modalDim,
        )
    }

    /** Applies the whole [colorEntries] table to the ImGui style. */
    private fun applyStyleColors(style: imgui.ImGuiStyle) {
        for ((index, argb) in colorEntries()) {
            setColor(style, index, argb)
        }
    }

    /**
     * Installs MiSans as the font used by the Crashs Legacy surface.
     *
     * `fabric-gui-imgui` bakes the atlas with ImGui's built-in ProggyClean before any screen
     * exists (`ImGuiService.configure()` calls `addFontDefault()` + `build()` at client start).
     * A font added afterwards only becomes `fonts[1]` and is never drawn: the UI keeps using
     * ProggyClean, which has no CJK glyphs, so every Chinese character renders as '?'.
     *
     * While no frame has been rendered the font texture does not exist yet, so the built-in font
     * can simply be dropped and MiSans becomes `fonts[0]` — the active font, at 17px for both CJK
     * and Latin. If a frame already ran, the atlas texture cannot be replaced by this backend
     * (`ImGuiBackendFlags_RendererHasTextures` is not supported), so the glyphs are merged into
     * the existing active font instead. Returns false when there is no ImGui context yet; callers
     * may retry.
     */
    fun installFonts(): Boolean {
        if (fontsInstalled) return true
        if (runCatching { ImGui.getCurrentContext() }.getOrNull() == null) return false
        val bytes = fontData ?: run {
            log("Crashs Legacy: no font bytes supplied, keeping the built-in ImGui font")
            return false
        }
        val io = runCatching { ImGui.getIO() }.getOrNull() ?: return false
        val atlas = io.fonts
        val requestedSize = fontSize
        val pristine = runCatching { ImGui.getFrameCount() == 0 }.getOrDefault(false)
        val standalone = if (pristine) {
            val full = runCatching {
                if (atlas.isBuilt) atlas.clearFonts()
                val ranges = buildGlyphRanges(atlas, fullCjk = true)
                val font = addFont(atlas, bytes, ranges, merge = false) ?: return@runCatching null
                if (!atlas.build()) return@runCatching null
                io.setFontDefault(font)
                font
            }.getOrNull()
            if (full != null) {
                log("Crashs Legacy font CJK mode=full")
                full
            } else {
                log("Crashs Legacy font CJK full atlas failed; retrying common range")
                runCatching {
                    atlas.clearFonts()
                    val ranges = buildGlyphRanges(atlas, fullCjk = false)
                    val font = addFont(atlas, bytes, ranges, merge = false) ?: return@runCatching null
                    if (!atlas.build()) return@runCatching null
                    io.setFontDefault(font)
                    font
                }.getOrNull()
            }
        } else {
            null
        }
        if (standalone != null) {
            bakedFontSize = requestedSize
            bakedScale = uiScale
            fontsInstalled = true
            runCatching { io.fontGlobalScale = 1f }
            log(
                "Crashs Legacy font installed as the only atlas font " +
                    "(size ${standalone.legacySize}, extra glyphs ${EXTRA_GLYPHS.length}+${extraGlyphs.length}, " +
                        "杉=${extraGlyphs.contains('杉')}, 桦=${extraGlyphs.contains('桦')})",
            )
            return true
        }

        val merged = runCatching {
            addFont(atlas, bytes, buildGlyphRanges(atlas, fullCjk = false), merge = true)?.also { atlas.build() }
        }.getOrNull()
        if (merged != null) {
            bakedFontSize = requestedSize
            bakedScale = uiScale
            fontsInstalled = true
            runCatching { io.fontGlobalScale = 1f }
            log(
                "Crashs Legacy font merged into the existing atlas font; CJK works but the " +
                    "Latin metrics stay at the built-in font size",
            )
            return true
        }
        log("Crashs Legacy: could not install its ImGui font")
        return false
    }

    private fun addFont(
        atlas: imgui.ImFontAtlas,
        bytes: ByteArray,
        ranges: ShortArray,
        merge: Boolean,
    ): imgui.ImFont? {
        val config = ImFontConfig()
        config.setMergeMode(merge)
        config.setFontDataOwnedByAtlas(false)
        config.setOversampleH(1)
        config.setOversampleV(1)
        config.setPixelSnapH(true)
        config.setRasterizerDensity(density)
        return atlas.addFontFromMemoryTTF(bytes, fontSize, config, ranges)
    }

    private fun setColor(style: imgui.ImGuiStyle, index: Int, argb: Int) {
        style.setColor(
            index,
            ((argb ushr 16) and 0xFF) / 255f,
            ((argb ushr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
            ((argb ushr 24) and 0xFF) / 255f,
        )
    }
}
