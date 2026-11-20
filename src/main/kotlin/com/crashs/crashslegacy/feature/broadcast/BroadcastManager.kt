package com.crashs.crashslegacy.feature.broadcast

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.LerpingBossEvent
import net.minecraft.world.BossEvent
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.TagParser
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.sounds.SoundEvents
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import com.crashs.crashslegacy.BroadcastCategorySettings
import com.crashs.crashslegacy.BroadcastStorage
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager

enum class BroadcastType { KILL, LOOT, REFORGE }

data class BroadcastRecord(
    val type: BroadcastType,
    val time: String,
    val player: String,
    val container: String,
    val mob: String,
    val level: Int,
    val material: String,
    val item: String,
    val prefix: String,
    val name: String,
    val quality: String,
    val color: String,
    val isSelf: Boolean = false,
    val rawText: String = "",
    val isoTime: String = "",
    val epochMs: Long = System.currentTimeMillis(),
    val category: String = "其他装备",
    val itemStyle: Style = Style.EMPTY,
)

object BroadcastManager {

    private val barTag = UUID.randomUUID().toString()
    private val bars = mutableListOf<Bar>()
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    private data class Bar(val id: UUID, val bar: LerpingBossEvent, val createdAt: Long, val durationMs: Long)

    private val hurtPattern = Regex("""^-\d+(?:\.\d+)?$""")
    private val killPattern = Regex("""^\s*[!！]\s*恭喜\s+(.+?)\s+击杀了\s+Lv\.(\d+)\s+(.+?)\s+并获得\s+(.+?)\s*[!！]\s*$""")
    private val lootPattern = Regex("""^\s*[!！]\s*恭喜\s+(.+?)\s+在\s+(.+?)\s+中摸出了\s+(.+?)\s*[!！]\s*$""")
    private val reforgePattern = Regex("""^\s*[!！]\s*恭喜\s+(.+?)\s+使用\s+(.+?)\s+进行重铸[，,]\s*获得了\s+(.+?)\s*[!！]\s*$""")

    fun handleMessage(message: Component): Boolean {
        val config = CrashsLegacyConfigManager.config
        val settings = config.broadcast
        if (!settings.enabled) return false
        val parsed = parse(message) ?: return false
        val localName = Minecraft.getInstance().player?.gameProfile?.name
            ?: Minecraft.getInstance().player?.name?.string
        val record = parsed.copy(isSelf = localName != null && parsed.player.equals(localName, ignoreCase = true))
        val (categoryName, _) = categoryFor(record)
        val routedRecord = record.copy(category = categoryName)
        val route = route(routedRecord) ?: return false

        if (settings.writeFile) {
            BroadcastStorage.append(routedRecord, settings.fileFormat)
        }
        if (route.showBossBar) addBossBar(routedRecord, route.bossBarSeconds)
        if (route.sound != "OFF") playSound(route.sound)
        return !route.showChat
    }

    private data class Route(
        val showChat: Boolean,
        val showBossBar: Boolean,
        val bossBarSeconds: Int,
        val sound: String,
    )

    private fun route(record: BroadcastRecord): Route? {
        val (_, category) = categoryFor(record)
        return Route(
            showChat = category.showChat,
            showBossBar = category.showBossBar,
            bossBarSeconds = category.bossBarSeconds.coerceIn(3, 60),
            sound = category.sound,
        )
    }

    private fun categoryFor(record: BroadcastRecord): Pair<String, BroadcastCategorySettings> {
        val settings = CrashsLegacyConfigManager.config.broadcast
        val group = if (record.isSelf) settings.own else settings.other
        return when {
            group.keyword.isNotBlank() && record.item.contains(group.keyword, ignoreCase = true) ->
                "关键词装备" to group.keywordCategory
            record.color.equals("red", ignoreCase = true) ->
                "红色装备" to group.redCategory
            else -> "其他装备" to group.otherCategory
        }
    }

    /** Only used by /crashs debug loot: decode a serialized text component first. */
    fun parseDebugComponent(input: String): Component? {
        val text = input.trim()
        if (!text.startsWith("{")) return Component.literal(input)
        runCatching {
            val nbt = TagParser.parseCompoundFully(text)
            ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElse(null)
        }.getOrNull()?.let { return it }
        return runCatching {
            val json = JsonParser.parseString(text)
            ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null)
        }.getOrNull()
    }

    /** Feeds arbitrary text through the same parser/route for /crashs debug loot. */
    fun debugMessage(message: Component): List<String> {
        val settings = CrashsLegacyConfigManager.config.broadcast
        val parsed = parse(message) ?: return listOf("[DebugLoot] 未识别该消息")
        val localName = Minecraft.getInstance().player?.gameProfile?.name
            ?: Minecraft.getInstance().player?.name?.string
        val record = parsed.copy(isSelf = localName != null && parsed.player.equals(localName, ignoreCase = true))
        val (categoryName, category) = categoryFor(record)
        val routedRecord = record.copy(category = categoryName)
        val route = route(routedRecord) ?: return listOf("[DebugLoot] 路由失败")
        val lines = mutableListOf(
            "[DebugLoot] 已解析",
            "类型：${record.type.name}",
            "玩家：${record.player}${if (record.isSelf) "（本玩家）" else ""}",
            "分类：$categoryName",
            "装备：${routedRecord.item}",
            "品质：${routedRecord.quality} / 颜色：${routedRecord.color}",
        )
        if (routedRecord.level > 0) lines += "等级：${routedRecord.level}"
        if (routedRecord.mob.isNotEmpty()) lines += "怪物：${routedRecord.mob}"
        if (routedRecord.container.isNotEmpty()) lines += "容器：${routedRecord.container}"
        if (routedRecord.material.isNotEmpty()) lines += "材料：${routedRecord.material}"
        lines += "聊天：${if (route.showChat) "显示" else "拦截"}"
        lines += "BossEvent：${if (route.showBossBar) "开启 ${route.bossBarSeconds}s" else "关闭"}"
        lines += "音效：${route.sound}"

        if (!settings.enabled) {
            lines += "播报管理：未启用，仅解析，不写入/不显示"
            return lines
        }
        if (settings.writeFile) BroadcastStorage.append(routedRecord, settings.fileFormat)
        if (route.showBossBar) addBossBar(routedRecord, route.bossBarSeconds)
        if (route.sound != "OFF") playSound(route.sound)
        lines += "已按入口行为执行"
        return lines
    }

    fun tick() {
        if (bars.isEmpty()) return
        val client = Minecraft.getInstance()
        val hud = client.gui?.bossOverlay ?: return
        val now = System.currentTimeMillis()
        val iterator = bars.iterator()
        while (iterator.hasNext()) {
            val bar = iterator.next()
            val elapsed = now - bar.createdAt
            if (elapsed >= bar.durationMs) {
                hud.update(ClientboundBossEventPacket.createRemovePacket(bar.id))
                iterator.remove()
                continue
            }
            bar.bar.progress = (1f - elapsed.toFloat() / bar.durationMs.toFloat()).coerceIn(0f, 1f)
            hud.update(ClientboundBossEventPacket.createUpdateProgressPacket(bar.bar))
        }
    }

    fun clear() {
        val client = Minecraft.getInstance()
        val hud = client.gui?.bossOverlay
        if (hud != null) {
            for (bar in bars) hud.update(ClientboundBossEventPacket.createRemovePacket(bar.id))
        }
        bars.clear()
        BroadcastStorage.close()
    }

    private fun addBossBar(record: BroadcastRecord, seconds: Int) {
        val client = Minecraft.getInstance()
        val hud = client.gui?.bossOverlay ?: return
        val durationMs = (seconds * 1000L).coerceAtLeast(1_000L)
        val id = UUID.randomUUID()
        val name = buildName(record)
        val color = when (record.color.lowercase()) {
            "red" -> BossEvent.BossBarColor.RED
            "green" -> BossEvent.BossBarColor.GREEN
            "blue" -> BossEvent.BossBarColor.BLUE
            "pink" -> BossEvent.BossBarColor.PINK
            "purple" -> BossEvent.BossBarColor.PURPLE
            "gold", "yellow" -> BossEvent.BossBarColor.YELLOW
            else -> BossEvent.BossBarColor.WHITE
        }
        val bar = LerpingBossEvent(id, name, 1f, color, BossEvent.BossBarOverlay.PROGRESS, false, false, false)
        hud.update(ClientboundBossEventPacket.createAddPacket(bar))
        bars += Bar(id, bar, System.currentTimeMillis(), durationMs)
    }

    private fun buildName(record: BroadcastRecord): Component {
        val prefix = Component.literal("[${record.time}] ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(record.player).withStyle(ChatFormatting.WHITE))
        return when (record.type) {
            BroadcastType.KILL -> prefix
                .append(Component.literal(" 击杀了 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Lv.${record.level} ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(record.mob).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("，获得 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(record.item).setStyle(styleForItem(record)))
            BroadcastType.LOOT -> prefix
                .append(Component.literal(" 在 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(record.container).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" 中摸出了 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(record.item).setStyle(styleForItem(record)))
            BroadcastType.REFORGE -> prefix
                .append(Component.literal(" 使用 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(record.material).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" 进行重铸，获得了 ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(record.item).setStyle(styleForItem(record)))
        }
    }

    private fun parse(message: Component): BroadcastRecord? {
        val full = message.string
        val runs = mutableListOf<Pair<String, Style>>()
        collect(message, Style.EMPTY, runs)

        killPattern.matchEntire(full)?.let { match ->
            return build(
                BroadcastType.KILL,
                match.groupValues[1],
                "",
                match.groupValues[3],
                match.groupValues[2].toIntOrNull() ?: 0,
                "",
                match.groupValues[4],
                runs,
                match.groups[4]?.range?.first ?: 0,
                full,
            )
        }
        lootPattern.matchEntire(full)?.let { match ->
            return build(
                BroadcastType.LOOT,
                match.groupValues[1],
                match.groupValues[2],
                "",
                0,
                "",
                match.groupValues[3],
                runs,
                match.groups[3]?.range?.first ?: 0,
                full,
            )
        }
        reforgePattern.matchEntire(full)?.let { match ->
            return build(
                BroadcastType.REFORGE,
                match.groupValues[1],
                "",
                "",
                0,
                match.groupValues[2],
                match.groupValues[3],
                runs,
                match.groups[3]?.range?.first ?: 0,
                full,
            )
        }
        return null
    }

    private fun build(
        type: BroadcastType,
        player: String,
        container: String,
        mob: String,
        level: Int,
        material: String,
        item: String,
        runs: List<Pair<String, Style>>,
        itemStart: Int,
        rawText: String,
    ): BroadcastRecord {
        val itemStyle = styleAt(runs, itemStart) ?: Style.EMPTY
        val color = colorAt(runs, itemStart, item)
        val now = System.currentTimeMillis()
        val iso = java.time.OffsetDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val quality = when (color.lowercase()) {
            "gold" -> "金"
            "red" -> "红"
            else -> color
        }
        val parts = item.trim().split(Regex("\\s+"), limit = 2)
        val prefix = if (parts.size > 1) parts[0] else ""
        val name = if (parts.size > 1) parts[1] else item
        return BroadcastRecord(
            type = type,
            time = OffsetDateTime.now().format(timeFormat),
            player = player.trim(),
            container = container.trim(),
            mob = mob.trim(),
            level = level,
            material = material.trim(),
            item = item.trim(),
            prefix = prefix,
            name = name,
            quality = quality,
            color = color,
            rawText = rawText,
            isoTime = iso,
            epochMs = now,
            itemStyle = itemStyle,
        )
    }

    private fun collect(text: Component, parentStyle: Style, runs: MutableList<Pair<String, Style>>) {
        val style = parentStyle.applyTo(text.style)
        if (text.string.isNotEmpty()) runs += text.string to style
        for (child in text.siblings) collect(child, style, runs)
    }

    private fun styleAt(runs: List<Pair<String, Style>>, start: Int): Style? {
        var offset = 0
        for ((text, style) in runs) {
            val end = offset + text.length
            if (start in offset until end) return style
            offset = end
        }
        return null
    }

    private fun colorAt(runs: List<Pair<String, Style>>, start: Int, item: String): String {
        colorName(styleAt(runs, start))?.let { return it }
        val target = item.trim()
        if (target.isNotEmpty()) {
            for ((text, style) in runs) {
                val value = text.trim()
                if (value.isNotEmpty() && (value.contains(target) || target.contains(value))) {
                    colorName(style)?.let { return it }
                }
            }
        }
        return "default"
    }

    private fun styleForItem(record: BroadcastRecord): Style {
        if (record.itemStyle.color != null) return record.itemStyle
        val formatting = when (record.color.lowercase()) {
            "red" -> ChatFormatting.RED
            "gold" -> ChatFormatting.GOLD
            "yellow" -> ChatFormatting.YELLOW
            "green" -> ChatFormatting.GREEN
            "aqua" -> ChatFormatting.AQUA
            "blue" -> ChatFormatting.BLUE
            "pink" -> ChatFormatting.LIGHT_PURPLE
            "purple" -> ChatFormatting.DARK_PURPLE
            "white" -> ChatFormatting.WHITE
            "gray" -> ChatFormatting.GRAY
            "dark_gray" -> ChatFormatting.DARK_GRAY
            else -> null
        }
        return if (formatting == null) record.itemStyle else record.itemStyle.withColor(formatting)
    }

    private fun colorName(style: Style?): String? {
        val color = style?.color ?: return null
        val named = color.serialize()
        if (!named.isNullOrBlank() && !named.equals("default", true)) return named.lowercase()
        return when (color.value) {
            0xFF5555, 0xFF0000 -> "red"
            0xFFAA00 -> "gold"
            0xFFFF55 -> "yellow"
            0x55FF55, 0x00FF00 -> "green"
            0x55FFFF, 0x00FFFF -> "aqua"
            0x5555FF, 0x0000FF -> "blue"
            0xFF55FF, 0xFF00FF -> "pink"
            0xAA00AA -> "purple"
            0xFFFFFF -> "white"
            0xAAAAAA -> "gray"
            0x555555 -> "dark_gray"
            else -> null
        }
    }

    private fun playSound(mode: String) {
        val player = Minecraft.getInstance().player ?: return
        val sound = when (mode.uppercase()) {
            "WITHER" -> SoundEvents.WITHER_DEATH
            "EXPERIENCE" -> SoundEvents.EXPERIENCE_ORB_PICKUP
            "OFF" -> return
            else -> SoundEvents.ENDER_DRAGON_DEATH
        }
        player.playSound(sound, 1f, 1f)
    }

}
