package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.feature.killfeed.KillRewardParser

/**
 * The reward parser is driven by a user-editable regex, so both the default pattern and the
 * "invalid pattern falls back to the default" path are worth pinning down.
 */
class KillRewardParserTest {

    @Test
    fun parsesTheDefaultServerMessage() {
        val reward = KillRewardParser.parse("!你获得了 12.5 金币，来自于 Lv.3 僵尸", "")
        assertNotNull(reward)
        assertEquals(12.5, reward!!.coins, 1e-9)
        assertEquals(3, reward.level)
        assertEquals("僵尸", reward.mob)
        assertEquals("Lv.3 僵尸", reward.source)
    }

    @Test
    fun parsesTheOptionalVipParenthesis() {
        val reward = KillRewardParser.parse("!你获得了 10 金币（VIP +5），来自于 苦力怕", "")
        assertNotNull(reward)
        assertEquals(10.0, reward!!.coins, 1e-9)
        assertEquals("苦力怕", reward.mob)
    }

    @Test
    fun acceptsAsciiCommaAndMissingYu() {
        val reward = KillRewardParser.parse("!你获得了 8 金币, 来自 蜘蛛", "")
        assertNotNull(reward)
        assertEquals(8.0, reward!!.coins, 1e-9)
        assertEquals("蜘蛛", reward.mob)
        assertNull(reward.level)
    }

    @Test
    fun normalisesRepeatedWhitespace() {
        val reward = KillRewardParser.parse("!  你获得了   3   金币，来自于   骷髅  ", "")
        assertNotNull(reward)
        assertEquals(3.0, reward!!.coins, 1e-9)
        assertEquals("骷髅", reward.mob)
    }

    @Test
    fun ignoresUnrelatedChat() {
        assertNull(KillRewardParser.parse("你好，世界", ""))
        assertNull(KillRewardParser.parse("", ""))
    }

    @Test
    fun rejectsMessagesWithoutASource() {
        assertNull(KillRewardParser.parse("!你获得了 5 金币，来自于   ", ""))
    }

    @Test
    fun invalidPatternFallsBackToTheDefault() {
        val reward = KillRewardParser.parse("!你获得了 6 金币，来自于 羊", "([unclosed")
        assertNotNull(reward)
        assertEquals(6.0, reward!!.coins, 1e-9)
    }

    @Test
    fun formatsCoinsWithoutTrailingZeros() {
        assertEquals("12.5", KillRewardParser.formatCoins(12.5))
        assertEquals("10", KillRewardParser.formatCoins(10.0))
        assertEquals("0", KillRewardParser.formatCoins(0.0))
        assertEquals("1234.56", KillRewardParser.formatCoins(1234.56))
    }
}
