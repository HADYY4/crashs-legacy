package com.crashs.crashslegacy.feature.killfeed

import java.util.ArrayDeque
import java.util.Locale

data class KillReward(
    val coins: Double,
    val source: String,
    val level: Int?,
    val mob: String,
    val receivedAt: Long = System.currentTimeMillis(),
)

data class KillFeedEntry(
    val id: Long,
    val reward: KillReward,
    val createdAt: Long,
    var fadeStartedAt: Long? = null,
    var shiftedAt: Long? = null,
    var evicted: Boolean = false,
) {
    val isFading: Boolean get() = fadeStartedAt != null
}

data class KillFeedSnapshot(
    val entries: List<KillFeedEntry>,
    val totalCoins: Double,
    val totalKills: Int,
    val windowStartedAt: Long,
    val windowEndsAt: Long,
    val generation: Long,
)

object KillRewardParser {

    const val DEFAULT_PATTERN =
        """^\s*!\s*你获得了\s+([0-9]+(?:\.[0-9]+)?)\s*金币(?:\s*[（(][^）)]*[）)])?\s*[，,]\s*来自(?:于)?\s+(.+?)\s*$"""

    /** Default used before the optional VIP/bonus parenthesis was supported. */
    const val PREVIOUS_DEFAULT_PATTERN =
        """^\s*!\s*你获得了\s+([0-9]+(?:\.[0-9]+)?)\s*金币\s*[，,]\s*来自(?:于)?\s+(.+?)\s*$"""

    /** Default emitted by 1.0.0 before the server's "来自于" wording was supported. */
    const val LEGACY_DEFAULT_PATTERN =
        """^\s*!\s*你获得了\s+([0-9]+(?:\.[0-9]+)?)\s*金币\s*[，,]\s*来自\s+(.+?)\s*$"""

    private val levelPattern = Regex("""^Lv\.(\d+)\s+(.+)$""", RegexOption.IGNORE_CASE)

    private var cachedPatternText: String? = null
    private var cachedRegex: Regex? = null

    @Synchronized
    private fun regexFor(patternText: String): Regex {
        val text = patternText.ifBlank { DEFAULT_PATTERN }
        val cached = cachedRegex
        if (text == cachedPatternText && cached != null) return cached

        val compiled = runCatching { Regex(text) }
            .getOrElse { Regex(DEFAULT_PATTERN) }
        cachedPatternText = text
        cachedRegex = compiled
        return compiled
    }

    fun parse(rawText: String, patternText: String): KillReward? {
        val normalized = normalize(rawText)
        if (normalized.isEmpty()) return null

        val match = regexFor(patternText).matchEntire(normalized) ?: return null
        val coins = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
        val sourceText = match.groupValues.getOrNull(2)?.trim().orEmpty()
        if (sourceText.isEmpty()) return null

        val levelMatch = levelPattern.matchEntire(sourceText)
        val level = levelMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        val mob = levelMatch?.groupValues?.getOrNull(2)?.trim() ?: sourceText

        return KillReward(coins = coins, source = sourceText, level = level, mob = mob)
    }

    fun formatCoins(coins: Double): String {
        val value = String.format(Locale.ROOT, "%.2f", coins)
        return value.trimEnd('0').trimEnd('.')
    }

    private fun normalize(text: String): String =
        text.replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
}

object KillFeedState {

    const val FADE_DURATION_MS = 700L

    private val entries = ArrayDeque<KillFeedEntry>()
    private var nextId = 0L
    private var totalCoins = 0.0
    private var totalKills = 0
    private var windowStartedAt = 0L
    private var windowEndsAt = 0L
    private var generation = 0L
    private var lastRewardAt = 0L
    private var comboCount = 0

    @Synchronized
    fun add(reward: KillReward): Int {
        val now = System.currentTimeMillis()

        if (windowEndsAt > 0L && now >= windowEndsAt) {
            resetInternal()
        }

        totalCoins += reward.coins
        totalKills++
        lastRewardAt = now
        comboCount++

        for (entry in entries) {
            entry.shiftedAt = now
        }

        val entry = KillFeedEntry(
            id = nextId++,
            reward = reward,
            createdAt = now,
        )
        entries.addFirst(entry)
        return comboCount
    }

    @Synchronized
    fun update(
        now: Long,
        entryDurationMs: Long,
        maxRows: Int,
        statsWindowMs: Long,
        timedStatsWindow: Boolean,
    ) {
        if (!timedStatsWindow) {
            windowStartedAt = 0L
            windowEndsAt = 0L
        } else {
            if (lastRewardAt > windowStartedAt) {
                windowStartedAt = lastRewardAt
                windowEndsAt = lastRewardAt + statsWindowMs.coerceAtLeast(1_000L)
            } else if (windowStartedAt == 0L && entries.isNotEmpty()) {
                windowStartedAt = now
                windowEndsAt = now + statsWindowMs.coerceAtLeast(1_000L)
            }
            if (windowStartedAt != 0L && now >= windowEndsAt) {
                resetInternal()
                return
            }
        }

        val duration = entryDurationMs.coerceAtLeast(500L)
        val limit = maxRows.coerceIn(1, 20)

        for (entry in entries) {
            if (!entry.isFading && now - entry.createdAt >= duration) {
                entry.fadeStartedAt = now
            }
        }

        val active = entries.filter { !it.isFading }
        if (active.size > limit) {
            active.drop(limit).forEach { entry ->
                if (entry.fadeStartedAt == null) {
                    entry.fadeStartedAt = now
                    entry.evicted = true
                }
            }
        }

        entries.removeIf { entry ->
            val fadeStart = entry.fadeStartedAt
            fadeStart != null && now - fadeStart >= FADE_DURATION_MS
        }
    }

    @Synchronized
    fun snapshot(): KillFeedSnapshot = KillFeedSnapshot(
        entries = entries.toList(),
        totalCoins = totalCoins,
        totalKills = totalKills,
        windowStartedAt = windowStartedAt,
        windowEndsAt = windowEndsAt,
        generation = generation,
    )

    @Synchronized
    fun reset() {
        resetInternal()
    }

    private fun resetInternal() {
        entries.clear()
        totalCoins = 0.0
        totalKills = 0
        nextId = 0L
        windowStartedAt = 0L
        windowEndsAt = 0L
        generation++
        lastRewardAt = 0L
        comboCount = 0
    }
}
