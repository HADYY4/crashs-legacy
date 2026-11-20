package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerEntry
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerKind
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerSegment
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerState

/**
 * Item Tracker window semantics: one global window owns the HUD, inventory rows merge by variant
 * and show the net change (a gain followed by the same loss leaves no row), and when the window
 * expires everything disappears at once.
 */
class ItemTrackerStateTest {

    @BeforeEach
    fun resetState() {
        ItemTrackerState.reset()
    }

    private fun gain(name: String, count: Int, key: String = name) = ItemTrackerEntry(
        kind = ItemTrackerKind.INVENTORY_GAIN,
        count = count,
        itemName = name,
        itemColor = null,
        warehouse = emptyList(),
        page = null,
        rowKey = key,
    )

    private fun loss(name: String, count: Int, key: String = name) = ItemTrackerEntry(
        kind = ItemTrackerKind.INVENTORY_LOSS,
        count = count,
        itemName = name,
        itemColor = null,
        warehouse = emptyList(),
        page = null,
        rowKey = key,
    )

    private fun warehouse(name: String, count: Int) = ItemTrackerEntry(
        kind = ItemTrackerKind.WAREHOUSE,
        count = count,
        itemName = name,
        itemColor = null,
        warehouse = listOf(ItemTrackerSegment("大型仓库 [1]", null)),
        page = 1,
        rowKey = null,
    )

    @Test
    fun rowsKeepTheirArrivalOrder() {
        ItemTrackerState.add(gain("晶簇鲷", 5), now = 1_000L, windowMs = 6_000L)
        ItemTrackerState.add(gain("棘刺河豚", 18), now = 1_100L, windowMs = 6_000L)
        val snapshot = ItemTrackerState.snapshot(now = 1_200L, windowMs = 6_000L, maxRows = 8)
        assertEquals(listOf("晶簇鲷", "棘刺河豚"), snapshot.rows.map { it.entry.itemName })
    }

    @Test
    fun theWindowExpiresAllRowsAtOnce() {
        ItemTrackerState.add(gain("晶簇鲷", 5), now = 1_000L, windowMs = 6_000L)
        assertTrue(ItemTrackerState.snapshot(now = 6_999L, windowMs = 6_000L, maxRows = 8).rows.isNotEmpty())
        assertTrue(ItemTrackerState.snapshot(now = 7_001L, windowMs = 6_000L, maxRows = 8).rows.isEmpty())
    }

    @Test
    fun aNewEventRefreshesTheWholeWindow() {
        ItemTrackerState.add(gain("晶簇鲷", 5), now = 1_000L, windowMs = 6_000L)
        ItemTrackerState.add(gain("金鳟", 64), now = 6_000L, windowMs = 6_000L)
        // 10 s after the first event but only 4 s after the second: still alive.
        assertEquals(2, ItemTrackerState.snapshot(now = 10_000L, windowMs = 6_000L, maxRows = 8).rows.size)
    }

    @Test
    fun gainsAndLossesOfTheSameVariantMergeIntoTheNetValue() {
        ItemTrackerState.add(gain("橡木原木", 1), now = 1_000L, windowMs = 6_000L)
        ItemTrackerState.add(gain("橡木原木", 1), now = 1_100L, windowMs = 6_000L)
        val merged = ItemTrackerState.snapshot(now = 1_200L, windowMs = 6_000L, maxRows = 8).rows.single()
        assertEquals(2, merged.entry.count)
        assertEquals(ItemTrackerKind.INVENTORY_GAIN, merged.entry.kind)
    }

    @Test
    fun aNetZeroVariantDisappears() {
        ItemTrackerState.add(gain("橡木原木", 2), now = 1_000L, windowMs = 6_000L)
        ItemTrackerState.add(loss("橡木原木", 2), now = 1_100L, windowMs = 6_000L)
        assertTrue(ItemTrackerState.snapshot(now = 1_200L, windowMs = 6_000L, maxRows = 8).rows.isEmpty())
    }

    @Test
    fun aPartialOffsetKeepsTheNetSign() {
        ItemTrackerState.add(gain("橡木原木", 2), now = 1_000L, windowMs = 6_000L)
        ItemTrackerState.add(loss("橡木原木", 3), now = 1_100L, windowMs = 6_000L)
        val row = ItemTrackerState.snapshot(now = 1_200L, windowMs = 6_000L, maxRows = 8).rows.single()
        assertEquals(ItemTrackerKind.INVENTORY_LOSS, row.entry.kind)
        assertEquals(1, row.entry.count)
    }

    @Test
    fun differentVariantsAreTrackedSeparately() {
        ItemTrackerState.add(gain("金苹果", 1, key = "minecraft:golden_apple|金苹果"), now = 1_000L, windowMs = 6_000L)
        ItemTrackerState.add(gain("金苹果2", 1, key = "minecraft:golden_apple|金苹果2"), now = 1_100L, windowMs = 6_000L)
        val rows = ItemTrackerState.snapshot(now = 1_200L, windowMs = 6_000L, maxRows = 8).rows
        assertEquals(listOf("金苹果", "金苹果2"), rows.map { it.entry.itemName })
    }

    @Test
    fun warehouseRowsAreNotMerged() {
        ItemTrackerState.add(warehouse("晶簇鲷", 5), now = 1_000L, windowMs = 6_000L)
        ItemTrackerState.add(warehouse("晶簇鲷", 5), now = 1_100L, windowMs = 6_000L)
        assertEquals(2, ItemTrackerState.snapshot(now = 1_200L, windowMs = 6_000L, maxRows = 8).rows.size)
    }

    @Test
    fun theRowCapDropsTheOldestEntries() {
        repeat(6) { index ->
            ItemTrackerState.add(gain("item$index", 1), now = 1_000L + index, windowMs = 60_000L)
        }
        val rows = ItemTrackerState.snapshot(now = 1_100L, windowMs = 60_000L, maxRows = 3).rows
        assertEquals(listOf("item3", "item4", "item5"), rows.map { it.entry.itemName })
    }

    @Test
    fun resetClearsEverything() {
        ItemTrackerState.add(gain("晶簇鲷", 5), now = 1_000L, windowMs = 6_000L)
        ItemTrackerState.reset()
        assertTrue(ItemTrackerState.snapshot(now = 1_100L, windowMs = 6_000L, maxRows = 8).rows.isEmpty())
    }
}
