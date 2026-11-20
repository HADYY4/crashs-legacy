package com.crashs.crashslegacy.ui

import imgui.ImGui

/**
 * Shared layout helpers for ImGui form fields, so the editor and the module cards keep the same
 * design rules:
 *
 * - numeric inputs are as wide as four digits plus both frame paddings plus some breathing room on
 *   the right (a hardcoded 64px made values scroll and look pushed against the edge);
 * - coloured text always goes through one place so no call site hand-writes float components.
 */
object CrashsImGuiFields {

    fun numericWidth(): Float {
        val style = ImGui.getStyle()
        return ImGui.calcTextSize("0000").x + style.getFramePaddingX() * 2f + 8f
    }

    /** Pushes an ARGB token as an ImGui style color (ImGui expects float components). */
    fun pushStyleColor(index: Int, argb: Int) {
        ImGui.pushStyleColor(
            index,
            ((argb ushr 16) and 0xFF) / 255f,
            ((argb ushr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
            ((argb ushr 24) and 0xFF) / 255f,
        )
    }

    /** Theme-colored text without hardcoding float components at the call site. */
    fun colorText(argb: Int, message: String) {
        ImGui.textColored(
            ((argb ushr 16) and 0xFF) / 255f,
            ((argb ushr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
            ((argb ushr 24) and 0xFF) / 255f,
            message,
        )
    }
}
