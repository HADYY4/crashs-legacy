package com.crashs.crashslegacy.ui

import cn.enaium.fabric.imgui.ImGuiRenderable
import imgui.ImGui
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Common lifecycle for every ImGui-backed screen.
 *
 * The input gate must be open while such a screen is current and closed otherwise. Making
 * [init]/[repositionElements]/[removed] final guarantees a new ImGui screen cannot forget it —
 * the HUD editor regression in 1.11.77 came from exactly that omission.
 *
 * Subclasses implement [onInit]/[onRefreshWidgetPositions]/[onRemoved] instead.
 */
abstract class CrashsImGuiBaseScreen(
    parent: Screen?,
    title: Component,
) : Screen(title), ImGuiRenderable {

    final override fun init() {
        super.init()
        openGate()
        onInit()
    }

    final override fun repositionElements() {
        super.repositionElements()
        // Returning from a child screen / resizing goes through here, not init().
        openGate()
        onRefreshWidgetPositions()
    }

    final override fun removed() {
        CrashsImGuiInput.deactivate()
        super.removed()
        onRemoved()
    }

    private fun openGate() {
        CrashsImGuiInput.activate()
        // Drop any key/mouse state left over from the previous screen before the first frame.
        runCatching {
            ImGui.getIO().clearInputKeys()
            ImGui.getIO().clearInputMouse()
        }
    }

    protected open fun onInit() {}

    protected open fun onRefreshWidgetPositions() {}

    protected open fun onRemoved() {}
}
