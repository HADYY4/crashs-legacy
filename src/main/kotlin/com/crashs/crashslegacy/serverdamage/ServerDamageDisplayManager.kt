package com.crashs.crashslegacy.serverdamage

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Display
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import com.crashs.crashslegacy.config.CrashsLegacyConfig
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager

/**
 * Applies the optional server TextDisplay damage-number controls.
 *
 * Two paths touch an entity:
 * - [applyStyle] runs after every tracker update (the packet carries serialized entries, so text
 *   is identified by value type and no private TEXT/BACKGROUND field names are needed);
 * - [refresh] re-applies the current switches to everything already styled, driven by config
 *   change notifications instead of a per-tick poll.
 *
 * The server state captured before our first write is kept in `baselines*`: those values are what
 * the switches combine with (see [ServerDamageStyle]) and what [restoreAll] puts back.
 */
object ServerDamageDisplayManager {

    private enum class DamageTextKind {
        ELEMENTAL,
        PLAYER_HURT,
    }

    private val hurtPattern = Regex("""^-\d+(?:\.\d+)?$""")
    private val trackedIds = LinkedHashSet<Int>()
    private val trackedKinds = HashMap<Int, DamageTextKind>()
    private val baselineBackgrounds = HashMap<Int, Int>()
    private val baselineFlags = HashMap<Int, Byte>()
    private val lock = Any()
    private val logger = org.slf4j.LoggerFactory.getLogger("crashs-legacy")

    @Volatile
    private var dirty = false

    @Volatile
    private var freezeLogged = false

    fun shouldCancel(packet: ClientboundSetEntityDataPacket): Boolean {
        val config = CrashsLegacyConfigManager.config
        if (!config.serverDamageEnabled || !config.serverDamageRemove) return false

        val text = textOf(packet) ?: return false
        val kind = classify(text) ?: return false

        val id = packet.id()
        synchronized(lock) {
            trackedIds += id
            trackedKinds[id] = kind
        }
        entityOf(id)?.discard()
        return true
    }

    /** Config changed: re-apply on the next tick instead of polling every tick. */
    fun markDirty() {
        dirty = true
    }

    /**
     * Whether a position update for [id] has to be dropped.
     *
     * The server moves every damage number with `EntityPositionSyncS2CPacket`; once an entity has
     * been classified as a damage text, the "禁止实体移动" switch keeps it where it first appeared
     * by discarding the later position updates. Component, background and style packets still apply, so
     * "移除伤害显示" and the other switches keep working unchanged.
     */
    fun shouldFreeze(id: Int): Boolean {
        if (id < 0) return false
        val config = CrashsLegacyConfigManager.config
        if (!config.serverDamageEnabled || !config.serverDamageFreeze) return false
        val tracked = synchronized(lock) { id in trackedIds }
        val freeze = ServerDamageStyle.shouldFreeze(
            serverDamageEnabled = config.serverDamageEnabled,
            freezeEnabled = config.serverDamageFreeze,
            tracked = tracked,
        )
        if (freeze && !freezeLogged) {
            freezeLogged = true
            logger.info("Crashs Legacy: freezing damage text #{} where it first appeared", id)
        }
        return freeze
    }

    /** Whether [id] is one of the damage texts we have classified (used by the packet diagnostics). */
    fun isTracked(id: Int): Boolean = synchronized(lock) { id in trackedIds }

    /**
     * Records whether this tracker update carries a display transform (translation/scale/affine),
     * which is how many servers animate the floating damage number instead of sending position
     * packets. Only feeds `/crashs debug damage`.
     */
    private fun noteTrackerForDebug(packet: ClientboundSetEntityDataPacket) {
        if (!ServerDamageDebug.recording) return
        val hasVector = packet.packedItems().any { entry ->
            val value = entry.value()
            value is org.joml.Vector3fc || value is com.mojang.math.Transformation
        }
        ServerDamageDebug.onTracker(hasVector)
    }

    /** Called once per client tick; does nothing unless something actually changed. */
    fun tick() {
        if (!dirty) return
        dirty = false
        refresh()
    }

    /** Re-applies the switches to every entity we have styled so far. */
    fun refresh() {
        val config = CrashsLegacyConfigManager.config
        val level = Minecraft.getInstance().level
        synchronized(lock) {
            if (!config.serverDamageEnabled || config.serverDamageRemove) {
                restoreAllLocked(level)
                return
            }
            if (!config.serverDamagePlayerHurt) {
                restoreKindLocked(level, DamageTextKind.PLAYER_HURT)
            }
            for (id in trackedIds.toList()) {
                val entity = level?.getEntity(id) as? Display.TextDisplay ?: continue
                applyLocked(entity, id, config)
            }
        }
    }

    fun applyStyle(packet: ClientboundSetEntityDataPacket) {
        val config = CrashsLegacyConfigManager.config
        if (!config.serverDamageEnabled || config.serverDamageRemove) return

        val entity = entityOf(packet.id()) as? Display.TextDisplay ?: return
        val kind = classify(entity.text) ?: return

        noteTrackerForDebug(packet)

        val id = packet.id()
        synchronized(lock) {
            trackedIds += id
            trackedKinds[id] = kind
            // While a switch is off the baseline follows the server; while it is on the baseline
            // stays frozen so the switch can be undone later.
            if (!config.serverDamageBackground) baselineBackgrounds[id] = entity.backgroundColor
            if (!config.serverDamageShadow) baselineFlags[id] = entity.flags
            applyLocked(entity, id, config)
        }
    }

    fun forget(ids: Iterable<Int>) {        synchronized(lock) {
            for (id in ids) {
                trackedIds -= id
                trackedKinds -= id
                baselineBackgrounds -= id
                baselineFlags -= id
            }
        }
    }

    fun clearWorld() {
        synchronized(lock) {
            trackedIds.clear()
            trackedKinds.clear()
            baselineBackgrounds.clear()
            baselineFlags.clear()
        }
    }

    /** Restores the server values and forgets every tracked entity. */
    fun restoreAll() {
        restoreAllLocked(Minecraft.getInstance().level)
    }

    private fun applyLocked(
        entity: Display.TextDisplay,
        id: Int,
        config: CrashsLegacyConfig,
    ) {
        val baseFlags = baselineFlags.getOrPut(id) { entity.flags }
        val baseBackground = baselineBackgrounds.getOrPut(id) { entity.getBackgroundColor() }
        entity.flags = ServerDamageStyle
            .flags(baseFlags.toInt(), config.serverDamageShadow, config.serverDamageBackground)
            .toByte()
        entity.setBackgroundColor( ServerDamageStyle.background(baseBackground, config.serverDamageBackground))
    }

    private fun restoreAllLocked(level: Level?) {
        synchronized(lock) {
            restoreLocked(level, trackedIds.toList())
        }
    }

    private fun restoreKindLocked(level: Level?, kind: DamageTextKind) {
        synchronized(lock) {
            restoreLocked(level, trackedKinds.filterValues { it == kind }.keys.toList())
        }
    }

    private fun restoreLocked(level: Level?, ids: List<Int>) {
        if (level != null) {
            for (id in ids) {
                val entity = level.getEntity(id) as? Display.TextDisplay ?: continue
                baselineBackgrounds[id]?.let { entity.setBackgroundColor(it) }
                baselineFlags[id]?.let { entity.flags = it }
            }
        }
        for (id in ids) {
            trackedIds -= id
            trackedKinds -= id
            baselineBackgrounds -= id
            baselineFlags -= id
        }
    }

    private fun classify(text: Component): DamageTextKind? {
        if (DamageElement.parse(text) != null) return DamageTextKind.ELEMENTAL
        if (CrashsLegacyConfigManager.config.serverDamagePlayerHurt && hurtPattern.matches(text.string.trim())) {
            return DamageTextKind.PLAYER_HURT
        }
        return null
    }

    private fun textOf(packet: ClientboundSetEntityDataPacket): Component? =
        packet.packedItems().firstNotNullOfOrNull { it.value() as? Component }

    private fun entityOf(id: Int): Display.TextDisplay? =
        Minecraft.getInstance().level?.getEntity(id) as? Display.TextDisplay
}
