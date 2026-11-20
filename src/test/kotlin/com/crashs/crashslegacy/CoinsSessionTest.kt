package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Coin session semantics (option A): the timer starts at the first recorded line and runs on the
 * wall clock afterwards; with no records there is no timer at all. Totals are always bound to the
 * recorded lines, so "kills" stays in sync with the coin count.
 */
class CoinsSessionTest {

    @BeforeEach
    fun resetSession() {
        CoinsSession.reset()
    }

    @Test
    fun beforeTheFirstRecordThereIsNoTimer() {
        val snapshot = CoinsSession.snapshot(now = 10_000L)
        assertEquals(0L, snapshot.elapsedMs)
        assertEquals(0.0, snapshot.totalCoins, 0.001)
        assertEquals(0, snapshot.totalKills)
    }

    @Test
    fun theFirstRecordStartsTheTimer() {
        CoinsSession.record(5.0, now = 1_000L)
        val snapshot = CoinsSession.snapshot(now = 61_000L)
        assertEquals(60_000L, snapshot.elapsedMs)
        assertEquals(5.0, snapshot.totalCoins, 0.001)
        assertEquals(1, snapshot.totalKills)
    }

    @Test
    fun laterRecordsDoNotRestartTheTimer() {
        CoinsSession.record(1.0, now = 1_000L)
        CoinsSession.record(2.0, now = 2_000L)
        val snapshot = CoinsSession.snapshot(now = 4_000L)
        assertEquals(3_000L, snapshot.elapsedMs, "the timer keeps the first record as its origin")
        assertEquals(3.0, snapshot.totalCoins, 0.001)
        assertEquals(2, snapshot.totalKills)
    }

    @Test
    fun nonPositiveAmountsAreIgnoredCompletely() {
        CoinsSession.record(0.0, now = 1_000L)
        CoinsSession.record(-4.0, now = 2_000L)
        val snapshot = CoinsSession.snapshot(now = 3_000L)
        assertEquals(0L, snapshot.elapsedMs, "a zero record must not start the timer")
        assertEquals(0.0, snapshot.totalCoins, 0.001)
        assertEquals(0, snapshot.totalKills)
    }

    @Test
    fun resetClearsTotalsAndTimer() {
        CoinsSession.record(9.0, now = 1_000L)
        CoinsSession.reset()
        val snapshot = CoinsSession.snapshot(now = 9_999L)
        assertEquals(0L, snapshot.elapsedMs)
        assertEquals(0.0, snapshot.totalCoins, 0.001)
        assertEquals(0, snapshot.totalKills)
    }

    @Test
    fun averagesUseTheElapsedTime() {
        CoinsSession.record(10.0, now = 1_000L)
        val afterOneMinute = CoinsSession.snapshot(now = 61_000L)
        assertEquals(10.0, afterOneMinute.averagePerMinute(), 0.001)
        assertEquals(600.0, afterOneMinute.averagePerHour(), 0.001)

        val immediately = CoinsSession.snapshot(now = 1_000L)
        assertEquals(0.0, immediately.averagePerMinute(), 0.001, "no elapsed time means no average")
    }
}
