package com.crashs.crashslegacy.config

/** Debounced disk writes for the live ImGui settings. */
object CrashsImGuiConfigSync {

    private const val SAVE_DELAY_MS = 400L
    private var dirty = false
    private var lastChangeAt = 0L

    /**
     * Invoked on every UI config change, so derived state (currently the server damage entity
     * styling) can refresh on demand instead of polling the config every tick.
     */
    var onChanged: (() -> Unit)? = null

    fun touch() {
        dirty = true
        lastChangeAt = System.currentTimeMillis()
        onChanged?.invoke()
    }

    fun tick() {
        if (!dirty) return
        if (System.currentTimeMillis() - lastChangeAt >= SAVE_DELAY_MS) {
            flush()
        }
    }

    fun flush() {
        if (!dirty) return
        CrashsLegacyConfigManager.save()
        dirty = false
    }
}
