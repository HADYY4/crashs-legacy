package com.crashs.crashslegacy.feature.lightning

import com.crashs.crashslegacy.CrashsLegacyMod
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvent
import net.minecraft.resources.Identifier

/** Plays the configured replacement for a lightning sound packet. */
object LightningSoundPlayer {

    enum class PlayResult {
        PLAYED,
        MISSING_GROUP,
        MISSING_RESOURCE,
        NO_WORLD,
    }

    /**
     * Replacement is intentionally silent on failure: a missing group or missing ogg does not fall
     * back to the vanilla thunder.
     */
    fun play(packet: ClientboundSoundPacket, group: String): PlayResult {
        if (!LightningSoundCatalog.contains(group)) return PlayResult.MISSING_GROUP
        val files = LightningSoundCatalog.filesOf(group)
        if (files.any { !resourceExists(it) }) return PlayResult.MISSING_RESOURCE

        val world = Minecraft.getInstance().level ?: return PlayResult.NO_WORLD
        val event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(CrashsLegacyMod.MOD_ID, "lightning.$group"))
        world.playLocalSound(
            packet.x,
            packet.y,
            packet.z,
            event,
            packet.source,
            packet.volume,
            packet.pitch,
            false,
        )
        return PlayResult.PLAYED
    }

    private fun resourceExists(file: String): Boolean =
        LightningSoundPlayer::class.java
            .getResourceAsStream("/assets/crashs-legacy/sounds/funnysound/$file.ogg")
            ?.use { true }
            ?: false
}
