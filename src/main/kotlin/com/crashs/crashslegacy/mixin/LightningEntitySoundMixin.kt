package com.crashs.crashslegacy.mixin

import com.crashs.crashslegacy.config.CrashsLegacyConfigManager
import com.crashs.crashslegacy.feature.lightning.LightningDebug
import net.minecraft.world.entity.LightningBolt
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Redirect

/**
 * The vanilla client tick of a lightning entity plays thunder and impact directly through
 * `Level.playLocalSound`, bypassing `PlaySoundS2CPacket` entirely. That local playback is why the
 * original thunder was still audible after the packet-level replacement.
 *
 * Keeping the entity visible but suppressing these two local calls lets the packet path own the
 * custom sound (or silence), while VANILLA mode still behaves exactly like vanilla.
 */
@Mixin(LightningBolt::class)
abstract class LightningEntitySoundMixin {

    @Redirect(
        method = ["tick"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
            ),
        ],
        require = 0,
    )
    private fun crashsLegacySuppressLocalSound(
        world: Level,
        x: Double,
        y: Double,
        z: Double,
        sound: SoundEvent,
        category: SoundSource,
        volume: Float,
        pitch: Float,
        useDistance: Boolean,
    ) {
        val config = CrashsLegacyConfigManager.config
        if (config.lightningEnabled && config.lightningSoundMode != "VANILLA") {
            LightningDebug.localSuppressed(sound.location, config.lightningSoundMode, x, y, z)
            return
        }
        world.playLocalSound(x, y, z, sound, category, volume, pitch, useDistance)
    }
}
