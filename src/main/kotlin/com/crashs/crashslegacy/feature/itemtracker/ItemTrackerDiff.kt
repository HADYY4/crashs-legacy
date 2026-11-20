package com.crashs.crashslegacy.feature.itemtracker

/**
 * Compares two aggregated item counts and returns the changes.
 *
 * Because the input is aggregated by item, moving a stack between slots produces no delta; only real
 * gains/losses show up. Pure logic, keyed generically so it is testable without Minecraft types.
 */
object ItemTrackerDiff {

    fun <K> diff(old: Map<K, Int>, new: Map<K, Int>): List<Pair<K, Int>> {
        val deltas = ArrayList<Pair<K, Int>>()
        for ((key, count) in new) {
            if (count <= 0) continue
            val previous = old[key] ?: 0
            if (count != previous) {
                deltas += key to (count - previous)
            }
        }
        for ((key, count) in old) {
            if (key in new || count <= 0) continue
            deltas += key to -count
        }
        return deltas
    }
}
