package com.crashs.crashslegacy

data class CoinsSessionSnapshot(
    val totalCoins: Double,
    val totalKills: Int,
    val startedAt: Long,
    val now: Long,
    val generation: Long,
) {
    val elapsedMs: Long get() = if (startedAt <= 0L) 0L else (now - startedAt).coerceAtLeast(0L)
    fun averagePerMinute(): Double = if (elapsedMs <= 0L) 0.0 else totalCoins / (elapsedMs / 60_000.0)
    fun averagePerHour(): Double = if (elapsedMs <= 0L) 0.0 else totalCoins / (elapsedMs / 3_600_000.0)
}

object CoinsSession {
    private var totalCoins = 0.0
    private var totalKills = 0
    private var startedAt = 0L
    private var generation = 0L

    @Synchronized
    fun record(coins: Double, now: Long = System.currentTimeMillis()) {
        if (coins <= 0.0) return
        if (startedAt <= 0L) startedAt = now
        totalCoins += coins
        totalKills++
    }

    @Synchronized
    fun snapshot(now: Long = System.currentTimeMillis()): CoinsSessionSnapshot =
        CoinsSessionSnapshot(totalCoins, totalKills, startedAt, now, generation)

    @Synchronized
    fun reset() {
        totalCoins = 0.0
        totalKills = 0
        startedAt = 0L
        generation++
    }
}
