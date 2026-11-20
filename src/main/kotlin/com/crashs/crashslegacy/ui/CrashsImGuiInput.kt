package com.crashs.crashslegacy.ui

/**
 * Gate for ImGui GLFW input.
 *
 * `fabric-gui-imgui` installs its GLFW callbacks at client startup, so without a gate ImGui keeps
 * receiving mouse/keyboard/char/scroll events even while the ClickGUI is closed. The mixin
 * `ImGuiImplGlfwGateMixin` drops those events at the point where they would be fed to `ImGuiIO`,
 * after MC's own callback has already run.
 *
 * [gateInstalled] is set by the mixin; when the injection could not be applied (library update) the
 * screen logs a warning and the old queue-draining fallback still applies.
 */
object CrashsImGuiInput {

    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    var gateInstalled: Boolean = false

    fun activate() {
        active = true
    }

    fun deactivate() {
        active = false
    }
}
