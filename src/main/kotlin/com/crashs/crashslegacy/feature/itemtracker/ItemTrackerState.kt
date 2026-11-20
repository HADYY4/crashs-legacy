package com.crashs.crashslegacy.feature.itemtracker

/** One visible row of the Item Tracker HUD. */
data class ItemTrackerRow(
    val id: Long,
    val entry: ItemTrackerEntry,
    val createdAt: Long,
    /** Signed net change inside the current window: positive = gain, negative = loss. */
    val signedCount: Int,
    /** Variant key for inventory rows (merged within a window); null for warehouse rows. */
    val rowKey: String?,
)

/**
 * Rolling list behind the Item Tracker HUD.
 *
 * A single **window** (default 6 s) owns the whole HUD: every new event refreshes the window, and
 * when it expires all rows disappear at once (no per-row timers, no fading).
 *
 * Inventory rows are merged by variant within the window and display their **net** change: picking
 * two logs then dropping two leaves no row at all.
 */
object ItemTrackerState {

    data class Snapshot(val rows: List<ItemTrackerRow>, val generation: Long)

    private const val HARD_LIMIT = 64

    private val rows = ArrayList<ItemTrackerRow>()
    private var lastActivityAt = 0L
    private var nextId = 1L
    private var generation = 0L

    @Synchronized
    fun add(
        entry: ItemTrackerEntry,
        now: Long = System.currentTimeMillis(),
        windowMs: Long = 6_000L,
    ): ItemTrackerRow? {
        if (lastActivityAt > 0L && now - lastActivityAt > windowMs) {
            rows.clear()
            generation++
        }
        lastActivityAt = now

        val signed = if (entry.kind == ItemTrackerKind.INVENTORY_LOSS) -entry.count else entry.count
        if (entry.rowKey != null && entry.kind != ItemTrackerKind.WAREHOUSE) {
            val index = rows.indexOfFirst { it.rowKey == entry.rowKey }
            if (index >= 0) {
                val existing = rows[index]
                val merged = existing.signedCount + signed
                if (merged == 0) {
                    rows.removeAt(index)
                    generation++
                    return null
                }
                val updated = existing.copy(
                    entry = existing.entry.copy(
                        kind = if (merged > 0) ItemTrackerKind.INVENTORY_GAIN else ItemTrackerKind.INVENTORY_LOSS,
                        count = kotlin.math.abs(merged),
                        itemName = entry.itemName,
                        itemColor = entry.itemColor ?: existing.entry.itemColor,
                    ),
                    signedCount = merged,
                )
                rows[index] = updated
                generation++
                return updated
            }
        }

        val row = ItemTrackerRow(nextId++, entry, now, signed, entry.rowKey)
        rows += row
        generation++
        while (rows.size > HARD_LIMIT) rows.removeAt(0)
        return row
    }

    @Synchronized
    fun snapshot(
        now: Long = System.currentTimeMillis(),
        windowMs: Long = 6_000L,
        maxRows: Int = 8,
    ): Snapshot {
        if (lastActivityAt > 0L && now - lastActivityAt > windowMs) {
            if (rows.isNotEmpty()) {
                rows.clear()
                generation++
            }
            lastActivityAt = 0L
            return Snapshot(emptyList(), generation)
        }
        val limit = maxRows.coerceIn(1, HARD_LIMIT)
        while (rows.size > limit) rows.removeAt(0)
        return Snapshot(rows.toList(), generation)
    }

    @Synchronized
    fun reset() {
        rows.clear()
        lastActivityAt = 0L
        generation++
    }
}
