package com.crashs.crashslegacy.feature.itemtracker

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.core.registries.BuiltInRegistries
import kotlin.math.abs
import com.crashs.crashslegacy.config.CrashsLegacyConfig
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager
import com.crashs.crashslegacy.text.NamedColors
import com.crashs.crashslegacy.text.TextComponentFlatten

/**
 * Tracks the player's own inventory and feeds gains/losses into the Item Tracker HUD.
 *
 * Detection is packet-driven with a cached aggregate (not polling):
 * - `syncId == 0` inventory packets (server sync),
 * - `dropSelectedItem` (Q without a screen),
 * - `clickSlot(THROW)` (Q inside the inventory screen).
 *
 * Items are aggregated by variant (item + display name), so a renamed "金苹果2" and a plain
 * "金苹果" are tracked separately and a loss row always shows the variant that actually changed.
 *
 * While any AbstractContainerScreen (player inventory or a container) is open, packets/hooks only set a
 * deferred flag; the single scan happens when the player is back in the game, and it always runs,
 * even when no packet/click was observed. This keeps container packet bursts from scanning the
 * inventory on every slot update.
 */
object ItemTrackerInventory {

    private data class Variant(val item: Item, val name: String)

    private data class Tracked(val count: Int, val name: String, val color: Int?)

    private val tracked = LinkedHashMap<Variant, Tracked>()
    private var initialized = false
    private var deferredUi = false

    /** Called from the client mixin after a local drop, which removes the stack without any packet. */
    @Synchronized
    fun onLocalChange(reason: String) {
        val client = Minecraft.getInstance()
        if (!isActive(client)) return
        if (client.player == null) return
        if (isInventoryUiOpen(client)) {
            deferredUi = true
            return
        }
        refresh(client, reason)
    }

    /** Called from the network mixin after `InventoryS2CPacket` / `ScreenHandlerSlotUpdateS2CPacket`. */
    @Synchronized
    fun onInventoryPacket(syncId: Int = 0) {
        val client = Minecraft.getInstance()
        val active = isActive(client)
        ItemTrackerDebug.packet(
            syncId = syncId,
            container = isContainerOpen(client),
            active = active,
            playerPresent = client.player != null,
            screen = screenName(client),
        )
        if (!active) return
        if (client.player == null) return
        if (isInventoryUiOpen(client)) {
            deferredUi = true
            return
        }
        refresh(client, "packet")
    }

    /** Called every client tick: container transitions, death and world changes only. */
    @Synchronized
    fun tick(client: Minecraft) {
        if (!isActive(client)) {
            reset()
            return
        }
        val player = client.player ?: run { reset(); return }

        if (player.isRemoved) {
            resync(client, "death")
            return
        }

        val uiOpen = isInventoryUiOpen(client)
        ItemTrackerDebug.tick(uiOpen, initialized, tracked.size, screenName(client))
        if (uiOpen) {
            deferredUi = true
            return
        }
        // Always scan once after the UI closes, even when no packet/click was seen: some inventory
        // changes are outside the packet/hook coverage, and the scan is the safety net.
        if (deferredUi) {
            deferredUi = false
            refresh(client, "ui-closed")
        }
        ItemTrackerDebug.reportIfDue(tracked.size, container = false)
    }

    @Synchronized
    fun reset() {
        tracked.clear()
        initialized = false
        deferredUi = false
    }

    private fun isActive(client: Minecraft): Boolean {
        val config = CrashsLegacyConfigManager.config
        return config.itemTrackerEnabled && config.itemTrackerInventoryEnabled && client.player != null
    }

    /** True while the player looks at their own inventory or any container GUI. */
    private fun isInventoryUiOpen(client: Minecraft): Boolean {
        val player = client.player ?: return false
        val screen = client.screen
        if (screen is AbstractContainerScreen<*>) return true
        // The container handler can arrive before the Screen is swapped in.
        return player.containerMenu !== player.inventoryMenu
    }

    /** Container-only classification, used by the diagnostics. */
    private fun isContainerOpen(client: Minecraft): Boolean {
        val screen = client.screen ?: return false
        val handler = (screen as? AbstractContainerScreen<*>)?.menu ?: return false
        return handler !== client.player?.inventoryMenu
    }

    private fun resync(client: Minecraft, reason: String) {
        deferredUi = false
        tracked.clear()
        tracked.putAll(scan(client))
        initialized = true
        ItemTrackerDebug.resync(reason, tracked.size)
    }

    private fun refresh(client: Minecraft, source: String) {
        val startedAt = System.nanoTime()
        val current = scan(client)
        if (!initialized) {
            tracked.putAll(current)
            initialized = true
            return
        }

        val oldCounts = tracked.mapValues { it.value.count }
        val newCounts = current.mapValues { it.value.count }
        val config = CrashsLegacyConfigManager.config
        val windowMs = windowMs(config)
        val now = System.currentTimeMillis()
        for ((key, delta) in ItemTrackerDiff.diff(oldCounts, newCounts)) {
            val meta = (if (delta > 0) current[key] else tracked[key]) ?: continue
            val total = current[key]?.count ?: 0
            ItemTrackerDebug.recordEvent()
            ItemTrackerDebug.delta(
                source = source,
                kind = if (delta > 0) "GAIN" else "LOSS",
                count = abs(delta),
                name = meta.name,
                total = total,
            )
            ItemTrackerState.add(
                ItemTrackerEntry(
                    kind = if (delta > 0) ItemTrackerKind.INVENTORY_GAIN else ItemTrackerKind.INVENTORY_LOSS,
                    count = abs(delta),
                    itemName = meta.name,
                    itemColor = meta.color,
                    warehouse = emptyList(),
                    page = null,
                    rowKey = rowKeyOf(key),
                ),
                now,
                windowMs,
            )
        }

        tracked.clear()
        tracked.putAll(current)
        ItemTrackerDebug.recordScan(source, System.nanoTime() - startedAt)
    }

    /** Aggregates every slot (main, hotbar, armor, offhand) by variant. */
    private fun scan(client: Minecraft): Map<Variant, Tracked> {
        val inventory = client.player?.inventory ?: return emptyMap()
        val result = LinkedHashMap<Variant, Tracked>()
        for (index in 0 until inventory.containerSize) {
            val stack = inventory.getItem(index)
            if (stack.isEmpty) continue
            val name = stack.hoverName.string
            val variant = Variant(stack.item, name)
            val previous = result[variant]
            val color = nameColor(stack)
            result[variant] = Tracked(
                count = (previous?.count ?: 0) + stack.count,
                name = name,
                color = previous?.color ?: color,
            )
        }
        return result
    }

    private fun rowKeyOf(variant: Variant): String =
        "${BuiltInRegistries.ITEM.getId(variant.item)}|${variant.name}"

    private fun windowMs(config: CrashsLegacyConfig): Long =
        (config.itemTrackerWindowSeconds.coerceIn(1f, 60f) * 1000f).toLong()

    /** Screen class plus how it is classified, so a fake menu can be recognised in the log. */
    private fun screenName(client: Minecraft): String {
        val screen = client.screen ?: return "none"
        val handler = (screen as? AbstractContainerScreen<*>)?.menu
        val kind = when {
            handler == null -> "plain"
            handler === client.player?.inventoryMenu -> "inventory"
            else -> "container"
        }
        return "${screen.javaClass.simpleName}($kind)"
    }

    /** The item's own name colour (the style the server/client put on the name text). */
    private fun nameColor(stack: ItemStack): Int? =
        TextComponentFlatten.flatten(stack.hoverName)
            .firstNotNullOfOrNull { part -> part.color?.let { NamedColors.colorOf(it) } }
}
