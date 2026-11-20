package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerParser

/**
 * Parsing of the warehouse plugin's auto-deposit lines. The samples come from a real session
 * (`/crashs chatdebug`), including the literal `§x` colour codes inside the item name.
 */
class ItemTrackerParserTest {

    private fun parse(text: String, pattern: String = ItemTrackerParser.DEFAULT_PATTERN) =
        ItemTrackerParser.parse(text, pattern)

    @Test
    fun parsesTheObservedLines() {
        val cluster = parse("[PlayerInv] 5x §5晶簇鲷 ==> 大型仓库 [1]")!!
        assertEquals(5, cluster.count)
        assertEquals("晶簇鲷", cluster.itemName)
        assertEquals(0xFFAA00AA.toInt(), cluster.itemColor)
        assertEquals("大型仓库 [1]", cluster.warehouseText)
        assertEquals(1, cluster.page)

        val puffer = parse("[PlayerInv] 18x §3棘刺河豚 ==> 大型仓库 [1]")!!
        assertEquals(18, puffer.count)
        assertEquals("棘刺河豚", puffer.itemName)
        assertEquals(0xFF00AAAA.toInt(), puffer.itemColor)

        val trout = parse("[PlayerInv] 64x §2金鳟 ==> 大型仓库 [1]")!!
        assertEquals(64, trout.count)
        assertEquals("金鳟", trout.itemName)
    }

    @Test
    fun thePageIsOptional() {
        val entry = parse("[PlayerInv] 3x §6幽灯鮟鱇 ==> 小型仓库")!!
        assertEquals(3, entry.count)
        assertEquals("小型仓库", entry.warehouseText)
        assertNull(entry.page)
    }

    @Test
    fun itemNamesMayContainSpacesAndLoseTheirColours() {
        val entry = parse("[PlayerInv] 1x §fIron Sword ==> Main Vault")!!
        assertEquals("Iron Sword", entry.itemName)
        assertEquals(0xFFFFFFFF.toInt(), entry.itemColor)
        assertEquals("Main Vault", entry.warehouseText)
    }

    @Test
    fun unrelatedChatDoesNotMatch() {
        assertNull(parse("Player1: 你好"))
        assertNull(parse("! 你获得了 10 金币，来自于 Lv.3 猪"))
        assertNull(parse("[PlayerInv] 5x 没有分隔符"))
    }

    @Test
    fun anInvalidPatternParsesNothing() {
        assertNull(parse("[PlayerInv] 5x §5晶簇鲷 ==> 大型仓库 [1]", "(["))
    }

    @Test
    fun aBlankPatternFallsBackToTheDefault() {
        val entry = parse("[PlayerInv] 7x §e金苹果 ==> 仓库", "")!!
        assertEquals(7, entry.count)
        assertEquals("金苹果", entry.itemName)
    }

    @Test
    fun theWarehouseKeepsItsPerRunColours() {
        val entry = parse("[PlayerInv] 5x §5晶簇鲷 ==> §6大型仓库 §f[§a1§f]")!!
        assertEquals("大型仓库 [1]", entry.warehouseText)
        assertEquals(
            listOf(0xFFFFAA00.toInt(), 0xFFFFFFFF.toInt(), 0xFF55FF55.toInt(), 0xFFFFFFFF.toInt()),
            entry.warehouse.map { it.color },
        )
        assertEquals(listOf("大型仓库 ", "[", "1", "]"), entry.warehouse.map { it.text })
    }

    @Test
    fun stripAndColourHelpers() {
        assertEquals("晶簇鲷", ItemTrackerParser.stripCodes("§5晶簇鲷"))
        assertEquals("Iron Sword", ItemTrackerParser.stripCodes("§fIron Sword"))
        assertEquals(0xFF55FF55.toInt(), ItemTrackerParser.firstColour("§a苹果"))
        assertNull(ItemTrackerParser.firstColour("苹果"))
    }
}
