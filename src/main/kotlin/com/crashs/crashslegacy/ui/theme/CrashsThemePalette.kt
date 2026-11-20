package com.crashs.crashslegacy.ui.theme

/** Base scheme of the Crashs Legacy surface. */
enum class CrashsThemeMode {
    DARK,
    LIGHT,
}

/**
 * Every color the surface draws — a theme is a complete color system, not a set of overrides.
 *
 * Nothing in the UI may fall back to an ImGui default or use a literal: [CrashsThemePalette.colors]
 * fills this record in for a base scheme plus an accent, `CrashsThemeCoverageTest` fails when an
 * `ImGuiCol` is left unthemed, and `CrashsThemeHardcodedColorTest` fails when a UI source contains a
 * color literal.
 */
data class CrashsThemeColors(
    // ---- structure ----
    val window: Int,
    val windowBorder: Int,
    val card: Int,
    val cardHeader: Int,
    val rail: Int,
    val popover: Int,
    val tooltip: Int,
    val overlay: Int,
    val shadow: Int,
    // ---- controls ----
    val field: Int,
    val fieldHover: Int,
    val fieldActive: Int,
    val button: Int,
    val buttonHover: Int,
    val buttonPressed: Int,
    val header: Int,
    val headerHover: Int,
    val headerActive: Int,
    val tab: Int,
    val tabHover: Int,
    val tabSelected: Int,
    val tabSelectedOverline: Int,
    val tabDimmed: Int,
    val tabDimmedSelected: Int,
    val sliderTrack: Int,
    val sliderGrab: Int,
    val sliderGrabActive: Int,
    val scrollbarBg: Int,
    val scrollbarGrab: Int,
    val scrollbarGrabHover: Int,
    val scrollbarGrabActive: Int,
    val border: Int,
    val borderStrong: Int,
    val separator: Int,
    val separatorHover: Int,
    val separatorActive: Int,
    val resizeGrip: Int,
    val resizeGripHover: Int,
    val resizeGripActive: Int,
    val textSelectedBg: Int,
    val navCursor: Int,
    val navWindowingHighlight: Int,
    val navWindowingDim: Int,
    val modalDim: Int,
    val tableHeader: Int,
    val tableBorderStrong: Int,
    val tableBorderLight: Int,
    val tableRow: Int,
    val tableRowAlt: Int,
    val plotLines: Int,
    val plotLinesHover: Int,
    val plotHistogram: Int,
    val plotHistogramHover: Int,
    val dragDropTarget: Int,
    val dragDropTargetBg: Int,
    val treeLines: Int,
    val textLink: Int,
    val inputCursor: Int,
    val checkMark: Int,
    val unsavedMarker: Int,
    // ---- text ----
    val text: Int,
    val textMuted: Int,
    val textDisabled: Int,
    // ---- accent ----
    val accent: Int,
    val accentHover: Int,
    val accentPressed: Int,
    val accentContainer: Int,
    val onAccent: Int,
    val onAccentContainer: Int,
    // ---- status ----
    val success: Int,
    val warning: Int,
    val info: Int,
    val error: Int,
    // ---- custom drawn widgets ----
    val capsuleBase: Int,
    val capsuleHover: Int,
    val switchTrackOff: Int,
    val switchKnobOff: Int,
    val switchKnobOn: Int,
    val icon: Int,
    // ---- HUD editor overlays ----
    val guideScreen: Int,
    val guideComponent: Int,
    val boxIdle: Int,
    val dragHint: Int,
)

/**
 * The palette behind every color the surface draws.
 *
 * Kept free of ImGui and Minecraft types so a scheme can be unit tested and stays shared with the
 * desktop preview.
 */
object CrashsThemePalette {

    const val DEFAULT_ACCENT = 0xFFFF615A.toInt()

    /** Accent presets offered by the configuration panel. */
    val ACCENT_PRESETS = intArrayOf(
        0xFFFF615A.toInt(), // 珊瑚（默认）
        0xFF4C8DFF.toInt(), // 天蓝
        0xFFA26BF0.toInt(), // 紫
        0xFF2FBF9E.toInt(), // 青
        0xFFF5A35D.toInt(), // 琥珀
        0xFFFF6FAE.toInt(), // 粉
    )

    val ACCENT_NAMES = arrayOf("珊瑚", "天蓝", "紫", "青", "琥珀", "粉")

    /** Builds the complete color system for one base scheme and accent. */
    fun colors(mode: CrashsThemeMode, accent: Int): CrashsThemeColors {
        val dark = mode == CrashsThemeMode.DARK
        val accentContainer = blend(
            accent,
            if (dark) 0xFF191919.toInt() else 0xFFFFFFFF.toInt(),
            if (dark) 0.5f else 0.86f,
        )
        val accentHover = lighten(accent, 0.12f)
        val accentPressed = darken(accent, 0.14f)
        val overlay = if (dark) 0xB0000000.toInt() else 0x40000000
        val field = if (dark) 0xFF141414.toInt() else 0xFFECECF1.toInt()
        val fieldHover = if (dark) 0xFF1E1E1E.toInt() else 0xFFE2E2E9.toInt()
        val text = if (dark) 0xFFEDEDED.toInt() else 0xFF17171A.toInt()
        val border = if (dark) 0xFF242424.toInt() else 0xFFD8D8E0.toInt()
        val borderStrong = if (dark) 0xFF303030.toInt() else 0xFFC9C9D3.toInt()
        val warning = if (dark) 0xFFF5A35D.toInt() else 0xFFB26A12.toInt()
        val control = if (dark) 0xFF262626.toInt() else 0xFFECECF1.toInt()
        val controlHover = if (dark) 0xFF303030.toInt() else 0xFFE2E2E9.toInt()

        return CrashsThemeColors(
            // structure
            window = if (dark) 0xFF151515.toInt() else 0xFFEDEDF1.toInt(),
            windowBorder = if (dark) 0xFF101010.toInt() else 0xFFE2E2E8.toInt(),
            card = if (dark) 0xFF1C1C1C.toInt() else 0xFFFFFFFF.toInt(),
            cardHeader = if (dark) 0xFF191919.toInt() else 0xFFFAFAFC.toInt(),
            rail = if (dark) 0xFF1C1C1C.toInt() else 0xFFF4F4F6.toInt(),
            popover = if (dark) 0xFF1C1C1C.toInt() else 0xFFFFFFFF.toInt(),
            tooltip = if (dark) 0xFF222222.toInt() else 0xFFFFFFFF.toInt(),
            overlay = overlay,
            shadow = if (dark) 0xFF0F0F0F.toInt() else 0xFFC6C6CD.toInt(),
            // controls
            field = field,
            fieldHover = fieldHover,
            fieldActive = accentContainer,
            button = control,
            buttonHover = controlHover,
            buttonPressed = accentContainer,
            header = control,
            headerHover = controlHover,
            headerActive = accentContainer,
            tab = control,
            tabHover = controlHover,
            tabSelected = accentContainer,
            tabSelectedOverline = accent,
            tabDimmed = if (dark) 0xFF111111.toInt() else 0xFFF4F4F6.toInt(),
            tabDimmedSelected = accentContainer,
            sliderTrack = field,
            sliderGrab = accent,
            sliderGrabActive = accentPressed,
            scrollbarBg = if (dark) 0xFF1C1C1C.toInt() else 0xFFF4F4F6.toInt(),
            scrollbarGrab = if (dark) 0xFF2E2E2E.toInt() else 0xFFC9C9D3.toInt(),
            scrollbarGrabHover = if (dark) 0xFF3A3A3A.toInt() else 0xFFBCBCC8.toInt(),
            scrollbarGrabActive = accent,
            border = border,
            borderStrong = borderStrong,
            separator = border,
            separatorHover = accentContainer,
            separatorActive = accent,
            resizeGrip = if (dark) 0xFF2A2A2A.toInt() else 0xFFD8D8E0.toInt(),
            resizeGripHover = accent,
            resizeGripActive = accentPressed,
            textSelectedBg = accentContainer,
            navCursor = accent,
            navWindowingHighlight = accent,
            navWindowingDim = overlay,
            modalDim = overlay,
            tableHeader = field,
            tableBorderStrong = borderStrong,
            tableBorderLight = border,
            tableRow = 0x00000000,
            tableRowAlt = if (dark) 0x14FFFFFF else 0x0F000000,
            plotLines = accent,
            plotLinesHover = accentHover,
            plotHistogram = accentContainer,
            plotHistogramHover = accent,
            dragDropTarget = accent,
            dragDropTargetBg = accentContainer,
            treeLines = border,
            textLink = accent,
            inputCursor = accent,
            checkMark = accent,
            unsavedMarker = warning,
            // text
            text = text,
            textMuted = if (dark) 0xFF8A8A8A.toInt() else 0xFF48484F.toInt(),
            textDisabled = if (dark) 0xFF4A4A4A.toInt() else 0xFF9A9AA3.toInt(),
            // accent
            accent = accent,
            accentHover = accentHover,
            accentPressed = accentPressed,
            accentContainer = accentContainer,
            onAccent = contrast(accent),
            onAccentContainer = contrast(accentContainer),
            // status
            success = if (dark) 0xFF3FBF7F.toInt() else 0xFF1B7F4F.toInt(),
            warning = warning,
            info = if (dark) 0xFF4C8DFF.toInt() else 0xFF2A62C4.toInt(),
            error = if (dark) 0xFFFF615A.toInt() else 0xFFC4343A.toInt(),
            // custom drawn widgets
            capsuleBase = if (dark) 0xFF262626.toInt() else 0xFFFFFFFF.toInt(),
            capsuleHover = controlHover,
            switchTrackOff = if (dark) 0xFF303030.toInt() else 0xFFC9C9D3.toInt(),
            switchKnobOff = if (dark) 0xFFEDEDED.toInt() else 0xFFFFFFFF.toInt(),
            // The knob stays light when the switch is on; ON_PRIMARY is a text color and resolves to
            // black for blue/amber accents, which is what made the enabled knob look like a hole.
            switchKnobOn = 0xFFFFFFFF.toInt(),
            icon = text,
            // Editor overlays derive from the accent so a theme switch recolours them too.
            guideScreen = withAlpha(accent, 0.40f),
            guideComponent = withAlpha(accent, 0.55f),
            boxIdle = withAlpha(accent, 0.40f),
            dragHint = withAlpha(accent, 0.27f),
        )
    }

    /** Relative luminance in gamma space; good enough to pick black or white text. */
    fun luminance(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /** WCAG-style contrast ratio between two opaque colors (1..21). */
    fun contrastRatio(first: Int, second: Int): Float {
        val a = luminance(first)
        val b = luminance(second)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** Picks near-black or near-white, whichever contrasts more with [argb] (WCAG ratio). */
    fun contrast(argb: Int): Int {
        val dark = 0xFF0B0B0E.toInt()
        val light = 0xFFF7F7F7.toInt()
        return if (contrastRatio(argb, light) >= contrastRatio(argb, dark)) light else dark
    }

    /** Keeps the RGB of [argb] and replaces the alpha channel. */
    fun withAlpha(argb: Int, alpha: Float): Int =
        (argb and 0x00FFFFFF) or (((alpha.coerceIn(0f, 1f) * 255f).toInt()) shl 24)

    fun lighten(argb: Int, amount: Float): Int = blend(argb, 0xFFFFFFFF.toInt(), amount)

    fun darken(argb: Int, amount: Float): Int = blend(argb, 0xFF000000.toInt(), amount)

    /**
     * Converts a theme token (ARGB, `0xAARRGGBB`) to ImGui's packed vertex color (ImU32, `0xAABBGGRR`).
     *
     * Theme tokens are ARGB because that is what the config file and the color math use; ImGui draw
     * lists want ABGR. Passing a token through unchanged swaps red and blue — which used to turn the
     * amber accent into a light blue swatch while the ImGui-styled slider stayed amber.
     */
    fun imU32(argb: Int): Int =
        (argb and 0xFF000000.toInt()) or
            ((argb and 0xFF) shl 16) or
            (argb and 0x0000FF00) or
            ((argb ushr 16) and 0xFF)

    /** Linear blend; [amount] is the weight of [background] (0 = foreground, 1 = background). */
    fun blend(foreground: Int, background: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun mix(shift: Int): Int {
            val fg = (foreground shr shift) and 0xFF
            val bg = (background shr shift) and 0xFF
            return (fg + (bg - fg) * t).toInt().coerceIn(0, 255)
        }
        val alpha = (foreground ushr 24) and 0xFF
        return (alpha shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}
