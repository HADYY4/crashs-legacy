package com.crashs.crashslegacy.feature.itemtracker

import java.lang.management.ManagementFactory
import java.util.Locale
import com.crashs.crashslegacy.CrashsLegacyMod

/**
 * Diagnostics for the inventory tracker: `/crashs debug item`.
 *
 * Logs every player-inventory packet, suppression transitions and every detected delta, so it is
 * possible to tell whether a change arrived as a packet or only as client-side prediction.
 *
 * Also aggregates performance counters (scan count, average/max scan time, thread allocation rate)
 * and prints one `stats` line every few seconds while enabled.
 */
object ItemTrackerDebug {

    @Volatile
    var enabled: Boolean = false
        private set

    private const val REPORT_INTERVAL_MS = 5_000L

    private var scans = 0L
    private var scanNanos = 0L
    private var maxNanos = 0L
    private var events = 0L
    private var packetScans = 0L
    private var tickScans = 0L
    private var dropScans = 0L
    private var throwScans = 0L
    private var resyncs = 0L

    private var lastReportAt = 0L
    private var lastScans = 0L
    private var lastEvents = 0L
    private var lastScanNanos = 0L
    private var lastPacketScans = 0L
    private var lastTickScans = 0L
    private var lastDropScans = 0L
    private var lastThrowScans = 0L
    private var lastResyncs = 0L
    private var lastAllocatedBytes = -1L

    private val threadBean = runCatching {
        ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    }.getOrNull()

    fun toggle(): Boolean {
        enabled = !enabled
        if (enabled) resetCounters()
        return enabled
    }

    fun packet(syncId: Int, container: Boolean, active: Boolean, playerPresent: Boolean, screen: String) {
        if (!enabled) return
        log("packet syncId=$syncId container=$container active=$active player=$playerPresent screen=$screen")
    }

    private var lastContainer = false
    private var tickCounter = 0

    fun tick(uiOpen: Boolean, initialized: Boolean, tracked: Int, screen: String) {
        if (!enabled) return
        tickCounter++
        val changed = uiOpen != lastContainer
        lastContainer = uiOpen
        if (!changed && tickCounter % 20 != 0) return
        log("tick ui-open=$uiOpen baseline=$initialized items=$tracked screen=$screen")
    }

    fun delta(source: String, kind: String, count: Int, name: String, total: Int) {
        if (!enabled) return
        log("delta[$source] $kind $count x $name (now $total)")
    }

    fun resync(reason: String, items: Int) {
        resyncs++
        if (!enabled) return
        log("baseline rebuilt ($reason) items=$items")
    }

    fun info(message: String) {
        if (!enabled) return
        log(message)
    }

    /** Records one full refresh (scan + diff) so the stats line can report its real cost. */
    fun recordScan(source: String, nanos: Long) {
        scans++
        scanNanos += nanos
        if (nanos > maxNanos) maxNanos = nanos
        when (source) {
            "packet" -> packetScans++
            "drop" -> dropScans++
            "throw" -> throwScans++
            else -> tickScans++
        }
    }

    fun recordEvent() {
        events++
    }

    /** Prints a summary every [REPORT_INTERVAL_MS] while enabled; call once per tick. */
    fun reportIfDue(items: Int, container: Boolean) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (lastReportAt == 0L) {
            lastReportAt = now
            lastAllocatedBytes = allocatedBytes()
            return
        }
        val elapsedMs = now - lastReportAt
        if (elapsedMs < REPORT_INTERVAL_MS) return

        val intervalScans = scans - lastScans
        val intervalEvents = events - lastEvents
        val intervalNanos = scanNanos - lastScanNanos
        val intervalPacket = packetScans - lastPacketScans
        val intervalTick = tickScans - lastTickScans
        val intervalDrop = dropScans - lastDropScans
        val intervalThrow = throwScans - lastThrowScans
        val intervalResync = resyncs - lastResyncs
        val avgUs = if (intervalScans > 0) intervalNanos.toDouble() / intervalScans / 1_000.0 else 0.0
        val maxUs = maxNanos / 1_000.0
        val allocated = allocatedBytes()
        val allocKb = if (allocated >= 0 && lastAllocatedBytes >= 0) {
            (allocated - lastAllocatedBytes) / 1024.0
        } else {
            -1.0
        }

        log(
            String.format(
                Locale.ROOT,
                "stats %.1fs scans=%d (packet=%d drop=%d throw=%d tick=%d) avg=%.1fus max=%.1fus events=%d resync=%d items=%d container=%s threadAlloc=%.0fKB",
                elapsedMs / 1000.0,
                intervalScans,
                intervalPacket,
                intervalDrop,
                intervalThrow,
                intervalTick,
                avgUs,
                maxUs,
                intervalEvents,
                intervalResync,
                items,
                container.toString(),
                allocKb,
            ),
        )

        lastReportAt = now
        lastScans = scans
        lastEvents = events
        lastScanNanos = scanNanos
        lastPacketScans = packetScans
        lastTickScans = tickScans
        lastDropScans = dropScans
        lastThrowScans = throwScans
        lastResyncs = resyncs
        maxNanos = 0L
        lastAllocatedBytes = allocated
    }

    private fun allocatedBytes(): Long {
        val bean = threadBean ?: return -1L
        return runCatching { bean.getThreadAllocatedBytes(Thread.currentThread().id) }.getOrDefault(-1L)
    }

    private fun resetCounters() {
        scans = 0
        scanNanos = 0
        maxNanos = 0
        events = 0
        packetScans = 0
        tickScans = 0
        dropScans = 0
        throwScans = 0
        resyncs = 0
        lastReportAt = 0
        lastScans = 0
        lastEvents = 0
        lastScanNanos = 0
        lastPacketScans = 0
        lastTickScans = 0
        lastDropScans = 0
        lastThrowScans = 0
        lastResyncs = 0
        lastAllocatedBytes = -1
    }

    private fun log(line: String) {
        CrashsLegacyMod.LOGGER.info("[ItemDebug] $line")
    }
}
