package com.crashs.crashslegacy.mixin

import com.crashs.crashslegacy.config.CrashsLegacyConfigManager
import net.minecraft.client.multiplayer.ClientLevel
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Mirrors the vanilla "hide lightning flashes" accessibility option while the module is enabled. */
@Mixin(ClientLevel::class)
abstract class ClientWorldLightningMixin {

    @Inject(method = ["getSkyFlashTime"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun crashsLegacyHideFlash(callbackInfo: CallbackInfoReturnable<Int>) {
        val config = CrashsLegacyConfigManager.config
        if (config.lightningEnabled && config.lightningHideFlash) {
            callbackInfo.setReturnValue(0)
        }
    }
}
