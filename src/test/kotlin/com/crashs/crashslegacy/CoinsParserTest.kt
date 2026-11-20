package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.feature.coins.CoinsParser

/**
 * Coin extraction with its own pattern. The default must reproduce the old behaviour (coins only
 * from kill-reward lines), and a custom pattern must work for pure coin messages.
 */
class CoinsParserTest {

    private fun parse(text: String, pattern: String = CoinsParser.DEFAULT_PATTERN) =
        CoinsParser.parse(text, pattern)

    @Test
    fun theDefaultPatternReadsTheKillRewardLine() {
        assertEquals(12.5, parse("! 你获得了 12.5 金币，来自于 Lv.30 僵尸")!!, 0.001)
        assertEquals(3.0, parse("! 你获得了 3 金币，来自 Lv.1 猪")!!, 0.001)
    }

    @Test
    fun theOptionalVipParenthesisStillWorks() {
        assertEquals(10.0, parse("! 你获得了 10 金币（VIP+1），来自于 Lv.5 猪")!!, 0.001)
    }

    @Test
    fun aCustomPatternReadsPureCoinMessages() {
        val pattern = """获得\s+([0-9,]+)\s*金币"""
        assertEquals(1234.0, parse("你在副本获得 1,234 金币", pattern)!!, 0.001)
        assertEquals(50.0, parse("击杀奖励：获得 50 金币", pattern)!!, 0.001)
    }

    @Test
    fun unrelatedLinesDoNotMatch() {
        assertNull(parse("Player1: 你好"))
        assertNull(parse("! 你获得了 金币，来自于 Lv.1 猪"))
    }

    @Test
    fun anInvalidPatternCountsNothing() {
        assertNull(parse("! 你获得了 10 金币，来自于 Lv.1 猪", "(["))
    }

    @Test
    fun aBlankPatternFallsBackToTheDefault() {
        assertEquals(7.0, parse("! 你获得了 7 金币，来自于 Lv.2 羊", "")!!, 0.001)
    }

    @Test
    fun nonBreakingSpacesAreNormalized() {
        assertEquals(4.0, parse("! 你获得了\u00A04\u00A0金币，来自于 Lv.2 羊")!!, 0.001)
    }
}
