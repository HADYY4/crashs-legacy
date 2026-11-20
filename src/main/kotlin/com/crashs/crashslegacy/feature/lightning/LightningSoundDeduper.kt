package com.crashs.crashslegacy.feature.lightning

/**
 * Collapses the duplicate lightning sound packets of a single strike.
 *
 * The same strike can send thunder/impact several times at the same position within a few
 * milliseconds. A position + 50 ms window keeps one custom sound per strike while still allowing
 * two real strikes at different positions (or the same position after the window) to play.
 */
object LightningSoundDeduper {

    const val WINDOW_MS = 50L

    private data class Key(val x: Long, val y: Long, val z: Long)

    private var lastKey: Key? = null
    private var lastPlayedAt: Long = Long.MIN_VALUE

    @Synchronized
    fun shouldPlay(x: Double, y: Double, z: Double, now: Long = System.currentTimeMillis()): Boolean {
        val key = Key(Math.round(x * 8.0), Math.round(y * 8.0), Math.round(z * 8.0))
        val duplicate = key == lastKey && now - lastPlayedAt <= WINDOW_MS
        if (!duplicate) {
            lastKey = key
            lastPlayedAt = now
        }
        return !duplicate
    }

    @Synchronized
    fun reset() {
        lastKey = null
        lastPlayedAt = Long.MIN_VALUE
    }
}
