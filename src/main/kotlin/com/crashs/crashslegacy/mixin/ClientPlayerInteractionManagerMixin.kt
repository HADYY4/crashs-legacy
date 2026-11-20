package com.crashs.crashslegacy.mixin

import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickType
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerInventory

/**
 * Dropping from the inventory screen goes through `clickSlot(..., ClickType.THROW, ...)`,
 * which mutates the inventory locally (`ScreenHandler.onSlotClick`) before sending the packet.
 * The server usually does not echo the change, so the Item Tracker has to react here.
 */
@Mixin(MultiPlayerGameMode::class)
abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = ["handleInventoryMouseClick"], at = [At("TAIL")])
    private fun onClickSlot(
        syncId: Int,
        slotId: Int,
        button: Int,
        actionType: ClickType,
        player: Player,
        callbackInfo: CallbackInfo,
    ) {
        if (actionType == ClickType.THROW) {
            ItemTrackerInventory.onLocalChange("throw")
        } else if (slotId == -999 && actionType == ClickType.PICKUP) {
            // Dragging a stack out of the inventory and releasing it outside the GUI sends
            // clickSlot(-999, PICKUP); the local prediction already removed it from the slot.
            ItemTrackerInventory.onLocalChange("drop-outside")
        }
    }
}
