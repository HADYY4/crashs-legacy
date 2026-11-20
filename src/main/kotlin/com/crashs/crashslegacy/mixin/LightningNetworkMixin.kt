package com.crashs.crashslegacy.mixin

import com.crashs.crashslegacy.config.CrashsLegacyConfigManager
import com.crashs.crashslegacy.feature.lightning.LightningDebug
import com.crashs.crashslegacy.feature.lightning.LightningSoundCatalog
import com.crashs.crashslegacy.feature.lightning.LightningSoundDeduper
import com.crashs.crashslegacy.feature.lightning.LightningSoundPlayer
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.world.entity.EntityType
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo


/** Drops lightning spawn packets and replaces (or silences) the two vanilla lightning sounds. */
@Mixin(ClientPacketListener::class)
abstract class LightningNetworkMixin {

    @Inject(method = ["handleAddEntity"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun crashsLegacyHideLightning(packet: ClientboundAddEntityPacket, callbackInfo: CallbackInfo) {
        val config = CrashsLegacyConfigManager.config
        if (!config.lightningEnabled || !config.lightningHideEntity) return
        if (packet.type != EntityType.LIGHTNING_BOLT) return
        LightningDebug.spawnCanceled(packet.id)
        callbackInfo.cancel()
    }

    @Inject(method = ["handleSoundEvent"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun crashsLegacyReplaceSound(packet: ClientboundSoundPacket, callbackInfo: CallbackInfo) {
        val config = CrashsLegacyConfigManager.config
        if (!config.lightningEnabled) return

        val soundId = packet.sound.value().location
        if (soundId.namespace != "minecraft" || soundId.path !in LightningSoundCatalog.LIGHTNING_SOUND_IDS) return

        when (config.lightningSoundMode) {
            "VANILLA" -> LightningDebug.sound(
                soundId, "VANILLA", "pass-through", packet.x, packet.y, packet.z,
            )
            "SILENT" -> {
                LightningDebug.sound(soundId, "SILENT", "cancel", packet.x, packet.y, packet.z)
                callbackInfo.cancel()
            }
            "SPECIFIC" -> {
                callbackInfo.cancel()
                if (!LightningSoundDeduper.shouldPlay(packet.x, packet.y, packet.z)) {
                    LightningDebug.sound(
                        soundId, "SPECIFIC", "dedupe", packet.x, packet.y, packet.z,
                        "group=${config.lightningSound}",
                    )
                    return
                }
                val result = LightningSoundPlayer.play(packet, config.lightningSound)
                LightningDebug.sound(
                    soundId, "SPECIFIC", "replace", packet.x, packet.y, packet.z,
                    "group=${config.lightningSound}",
                )
                LightningDebug.playResult(config.lightningSound, result)
            }
        }
    }
}
