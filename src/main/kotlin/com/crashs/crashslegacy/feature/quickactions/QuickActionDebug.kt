package com.crashs.crashslegacy.feature.quickactions

import com.crashs.crashslegacy.CrashsLegacyMod

object QuickActionDebug {

    @Volatile
    var enabled: Boolean = false
        private set

    fun toggle(): Boolean {
        enabled = !enabled
        log(if (enabled) "debug enabled" else "debug disabled")
        return enabled
    }

    fun log(message: String) {
        if (enabled) CrashsLegacyMod.LOGGER.info("[QuickActionDebug] $message")
    }
}
