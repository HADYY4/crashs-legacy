package com.crashs.crashslegacy.ui

/**
 * Frame-rate independent exponential damping, ported from the FaMotion model.
 *
 * No overshoot; settles quickly and is stable across frame rates.
 */
class CrashsMotion(initial: Float = Float.NaN) {

    var value: Float = initial
        private set

    fun toward(target: Float, dt: Float, speed: Float): Float {
        if (value.isNaN()) value = target
        val t = 1f - kotlin.math.exp(-dt.coerceAtLeast(0f) * speed)
        value += (target - value) * t
        if (kotlin.math.abs(target - value) < 0.001f) value = target
        return value
    }

    fun snap(target: Float) {
        value = target
    }
}
