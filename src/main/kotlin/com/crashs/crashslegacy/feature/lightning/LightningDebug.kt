package com.crashs.crashslegacy.feature.lightning

import com.crashs.crashslegacy.CrashsLegacyMod
import net.minecraft.resources.Identifier

/** `/crashs debug lightning`: packet/action logs plus a once-per-second recolor counter. */
object LightningDebug {

    @Volatile
    var enabled: Boolean = false
        private set

    private var quads = 0L
    private var lastRecolorLog = 0L

    fun toggle(): Boolean {
        enabled = !enabled
        log(if (enabled) "debug enabled" else "debug disabled")
        return enabled
    }

    fun log(message: String) {
        if (enabled) CrashsLegacyMod.LOGGER.info("[LightningDebug] $message")
    }

    fun spawnCanceled(entityId: Int) {
        log("spawn canceled entity=$entityId")
    }

    fun sound(
        soundId: Identifier,
        mode: String,
        action: String,
        x: Double,
        y: Double,
        z: Double,
        detail: String = "",
    ) {
        val suffix = if (detail.isEmpty()) "" else " $detail"
        val pos = String.format(java.util.Locale.ROOT, "x=%.2f y=%.2f z=%.2f", x, y, z)
        log("sound=${soundId.path} mode=$mode action=$action $pos$suffix")
    }

    fun localSuppressed(soundId: Identifier, mode: String, x: Double, y: Double, z: Double) {
        val pos = String.format(java.util.Locale.ROOT, "x=%.2f y=%.2f z=%.2f", x, y, z)
        log("local-suppressed sound=${soundId.path} mode=$mode $pos")
    }

    fun playResult(group: String, result: LightningSoundPlayer.PlayResult) {
        log("specific group=$group result=${result.name.lowercase()}")
    }

    /** Called for every redirected vertex color; logs a one-second summary instead of spam. */
    fun recolorQuad() {
        if (!enabled) return
        quads++
        val now = System.currentTimeMillis()
        if (now - lastRecolorLog < 1_000L) return
        lastRecolorLog = now
        log("recolor color-calls/s=$quads")
        quads = 0L
    }
}
