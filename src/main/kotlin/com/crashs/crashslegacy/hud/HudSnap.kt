package com.crashs.crashslegacy.hud

import kotlin.math.abs
import kotlin.math.round

/**
 * Axis snapping for the HUD editor.
 *
 * Candidates come from two places, merged into one nearest-wins list (screen wins ties):
 *
 * - the screen: anchor edge, centre and the opposite edge;
 * - every other enabled HUD: left/right (or top/bottom) edges, centres, and both "abut" directions,
 *   so two HUDs can be lined up or placed next to each other.
 *
 * When nothing is within [thresholdPx] the position falls back to the 1% grid. Pure Kotlin, pinned
 * down by `HudSnapTest`.
 */
object HudSnap {

    enum class Source {
        SCREEN,
        COMPONENT,
        GRID,
        NONE,
    }

    /** Snapped top-left position in scaled screen pixels plus the guide line to draw, if any. */
    data class Snap(val positionPx: Float, val guidePx: Float?, val source: Source)

    private data class Candidate(val position: Float, val guide: Float, val screen: Boolean)

    /**
     * @param desiredPx wanted top-left position on this axis.
     * @param boxSize size of the dragged box on this axis.
     * @param screenSize screen size on this axis.
     * @param others start/end pairs of the other components on this axis.
     */
    fun snapAxis(
        desiredPx: Float,
        boxSize: Float,
        screenSize: Float,
        others: List<Pair<Float, Float>>,
        thresholdPx: Float = 8f,
        gridPercent: Float = 1f,
    ): Snap {
        if (screenSize <= 0f) return Snap(desiredPx, null, Source.NONE)
        val maxPosition = (screenSize - boxSize).coerceAtLeast(0f)

        val candidates = ArrayList<Candidate>(3 + others.size * 5)
        candidates += Candidate(0f, 0f, screen = true)
        candidates += Candidate(maxPosition / 2f, screenSize / 2f, screen = true)
        candidates += Candidate(maxPosition, screenSize, screen = true)

        for ((start, end) in others) {
            val centre = (start + end) / 2f
            candidates += Candidate(start, start, screen = false) // left/left
            candidates += Candidate(end - boxSize, end, screen = false) // right/right
            candidates += Candidate(centre - boxSize / 2f, centre, screen = false) // centre/centre
            candidates += Candidate(end, end, screen = false) // my start at their end
            candidates += Candidate(start - boxSize, start, screen = false) // my end at their start
        }

        var best: Candidate? = null
        var bestDistance = Float.MAX_VALUE
        for (candidate in candidates) {
            val distance = abs(candidate.position - desiredPx)
            val closer = distance < bestDistance - EPSILON
            val tieScreen = abs(distance - bestDistance) <= EPSILON && candidate.screen && best?.screen == false
            if (closer || tieScreen) {
                best = candidate
                bestDistance = distance
            }
        }

        if (best != null && bestDistance <= thresholdPx) {
            return Snap(
                positionPx = best.position.coerceIn(0f, maxPosition),
                guidePx = best.guide,
                source = if (best.screen) Source.SCREEN else Source.COMPONENT,
            )
        }

        if (gridPercent > 0f) {
            val step = screenSize / 100f * gridPercent
            val snapped = round(desiredPx / step) * step
            return Snap(snapped.coerceIn(0f, maxPosition), null, Source.GRID)
        }

        return Snap(desiredPx.coerceIn(0f, maxPosition), null, Source.NONE)
    }

    private const val EPSILON = 0.001f
}
