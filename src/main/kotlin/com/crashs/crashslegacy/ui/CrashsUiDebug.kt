package com.crashs.crashslegacy.ui
import com.crashs.crashslegacy.CrashsLegacyMod

/**
 * Temporary diagnostics for the ImGui surface input handling.
 *
 * Toggled by `/crashs debug ui`; off by default, so every call site costs one boolean check.
 * Enable it, reproduce the problem, then read the log lines prefixed with `[UiDebug]`.
 */
object CrashsUiDebug {

    @Volatile
    var enabled: Boolean = false
        private set

    /** @return the new state. */
    fun toggle(): Boolean {
        enabled = !enabled
        return enabled
    }

    inline fun log(message: () -> String) {
        if (!enabled) return
        CrashsLegacyMod.LOGGER.info("[UiDebug] {}", message())
    }
}
