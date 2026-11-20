package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerDiff

/**
 * Aggregated inventory diff: only real gains/losses may produce rows, and moving a stack between
 * slots (same totals) must stay silent.
 */
class ItemTrackerDiffTest {

    @Test
    fun noChangeProducesNothing() {
        val counts = mapOf("晶簇鲷" to 5, "金鳟" to 64)
        assertTrue(ItemTrackerDiff.diff(counts, counts).isEmpty())
    }

    @Test
    fun aGainProducesAPositiveDelta() {
        val deltas = ItemTrackerDiff.diff(mapOf("晶簇鲷" to 5), mapOf("晶簇鲷" to 8))
        assertEquals(listOf("晶簇鲷" to 3), deltas)
    }

    @Test
    fun aPartialLossProducesANegativeDelta() {
        val deltas = ItemTrackerDiff.diff(mapOf("晶簇鲷" to 8), mapOf("晶簇鲷" to 5))
        assertEquals(listOf("晶簇鲷" to -3), deltas)
    }

    @Test
    fun aFullLossReportsTheOldCount() {
        val deltas = ItemTrackerDiff.diff(mapOf("晶簇鲷" to 5), emptyMap())
        assertEquals(listOf("晶簇鲷" to -5), deltas)
    }

    @Test
    fun aNewItemIsAGain() {
        val deltas = ItemTrackerDiff.diff(emptyMap(), mapOf("金鳟" to 64))
        assertEquals(listOf("金鳟" to 64), deltas)
    }

    @Test
    fun gainsAndLossesCanHappenTogether() {
        val deltas = ItemTrackerDiff.diff(
            mapOf("晶簇鲷" to 5, "金鳟" to 10),
            mapOf("晶簇鲷" to 7, "棘刺河豚" to 2),
        )
        assertEquals(
            setOf(
                "晶簇鲷" to 2,
                "棘刺河豚" to 2,
                "金鳟" to -10,
            ),
            deltas.toSet(),
        )
    }

    @Test
    fun zeroCountsAreIgnored() {
        assertTrue(ItemTrackerDiff.diff(mapOf("晶簇鲷" to 0), emptyMap()).isEmpty())
        assertTrue(ItemTrackerDiff.diff(emptyMap(), mapOf("晶簇鲷" to 0)).isEmpty())
    }

    @Test
    fun movingAStackBetweenSlotsIsInvisible() {
        // Aggregated input: both sides are the same totals, so nothing happens.
        assertTrue(ItemTrackerDiff.diff(mapOf("金鳟" to 64), mapOf("金鳟" to 64)).isEmpty())
    }
}
