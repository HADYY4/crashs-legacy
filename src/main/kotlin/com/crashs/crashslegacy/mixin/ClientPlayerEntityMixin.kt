package com.crashs.crashslegacy.mixin

import net.minecraft.client.player.LocalPlayer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerInventory

/**
 * Client-side drops (Q / Ctrl+Q, including the creative variant) remove the stack from the local
 * inventory without any server packet. This hook lets the Item Tracker react to them immediately.
 */
@Mixin(LocalPlayer::class)
abstract class ClientPlayerEntityMixin {

    @Inject(method = ["drop"], at = [At("TAIL")])
    private fun onDropSelectedItem(dropEntireStack: Boolean, callbackInfo: CallbackInfoReturnable<Boolean>) {
        ItemTrackerInventory.onLocalChange("drop")
    }
}
