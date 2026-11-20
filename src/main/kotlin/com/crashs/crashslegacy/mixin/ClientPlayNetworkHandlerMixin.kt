package com.crashs.crashslegacy.mixin

import com.crashs.crashslegacy.serverdamage.ServerDamageDebug
import com.crashs.crashslegacy.serverdamage.ServerDamageDisplayManager
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import com.crashs.crashslegacy.chat.ChatIntake
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerInventory

@Mixin(ClientPacketListener::class)
abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = ["handleSystemChat"], at = [At("HEAD")], cancellable = true)
    private fun onGameMessage(packet: ClientboundSystemChatPacket, callbackInfo: CallbackInfo) {
        if (ChatIntake.onChat(
                packet.content(),
                packet.overlay(),
                System.identityHashCode(packet),
            )
        ) {
            callbackInfo.cancel()
        }
    }

    @Inject(method = ["setActionBarText"], at = [At("HEAD")], cancellable = true)
    private fun onOverlayMessage(packet: ClientboundSetActionBarTextPacket, callbackInfo: CallbackInfo) {
        if (ChatIntake.onChat(
                packet.text(),
                true,
                System.identityHashCode(packet),
            )
        ) {
            callbackInfo.cancel()
        }
    }

    @Inject(method = ["handleContainerContent"], at = [At("TAIL")])
    private fun onInventory(packet: ClientboundContainerSetContentPacket, callbackInfo: CallbackInfo) {
        ItemTrackerInventory.onInventoryPacket(packet.containerId)
    }

    @Inject(method = ["handleContainerSetSlot"], at = [At("TAIL")])
    private fun onScreenHandlerSlotUpdate(packet: ClientboundContainerSetSlotPacket, callbackInfo: CallbackInfo) {
        ItemTrackerInventory.onInventoryPacket(packet.containerId)
    }

    @Inject(method = ["handleSetEntityData"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun cancelServerDamage(packet: ClientboundSetEntityDataPacket, callbackInfo: CallbackInfo) {
        if (ServerDamageDisplayManager.shouldCancel(packet)) {
            callbackInfo.cancel()
        }
    }

    @Inject(method = ["handleSetEntityData"], at = [At("TAIL")], require = 0)
    private fun styleServerDamage(packet: ClientboundSetEntityDataPacket, callbackInfo: CallbackInfo) {
        ServerDamageDisplayManager.applyStyle(packet)
    }

    // The server animates damage numbers by teleporting the TextDisplay every few ticks with
    // ClientboundEntityPositionSyncPacket. Dropping those updates for entities we already classified as
    // damage texts freezes the number where it first appeared ("禁止实体移动").
    @Inject(method = ["handleEntityPositionSync"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun freezeServerDamagePositionSync(packet: ClientboundEntityPositionSyncPacket, callbackInfo: CallbackInfo) {
        if (ServerDamageDebug.recording && ServerDamageDisplayManager.isTracked(packet.id())) {
            ServerDamageDebug.onSyncMove()
        }
        if (ServerDamageDisplayManager.shouldFreeze(packet.id())) {
            ServerDamageDebug.onFrozenCancel()
            callbackInfo.cancel()
        }
    }

    // Older servers send absolute teleports instead; same predicate, so it is a free safety net.
    @Inject(method = ["handleTeleportEntity"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun freezeServerDamagePosition(packet: ClientboundTeleportEntityPacket, callbackInfo: CallbackInfo) {
        if (ServerDamageDebug.recording && ServerDamageDisplayManager.isTracked(packet.id())) {
            ServerDamageDebug.onAbsoluteMove()
        }
        if (ServerDamageDisplayManager.shouldFreeze(packet.id())) {
            ServerDamageDebug.onFrozenCancel()
            callbackInfo.cancel()
        }
    }

    // Relative moves are the channel this server actually uses (measured: 228 packets in a few
    // seconds vs 9 absolute syncs), so freezing has to cover them as well.
    @Inject(method = ["handleMoveEntity"], at = [At("HEAD")], cancellable = true, require = 0)
    private fun freezeServerDamageRelativeMove(packet: ClientboundMoveEntityPacket, callbackInfo: CallbackInfo) {
        val id = (packet as EntityS2CPacketAccessor).crashsEntityId()
        if (ServerDamageDebug.recording && ServerDamageDisplayManager.isTracked(id)) {
            ServerDamageDebug.onRelativeMove()
        }
        if (ServerDamageDisplayManager.shouldFreeze(id)) {
            ServerDamageDebug.onFrozenCancel()
            callbackInfo.cancel()
        }
    }

    @Inject(method = ["handleSetEntityMotion"], at = [At("HEAD")], require = 0)
    private fun debugServerDamageVelocity(packet: ClientboundSetEntityMotionPacket, callbackInfo: CallbackInfo) {
        if (!ServerDamageDebug.recording) return
        if (ServerDamageDisplayManager.isTracked(packet.id)) {
            ServerDamageDebug.onVelocity()
        }
    }

    @Inject(method = ["handleRemoveEntities"], at = [At("HEAD")], require = 0)
    private fun forgetServerDamage(packet: ClientboundRemoveEntitiesPacket, callbackInfo: CallbackInfo) {
        ServerDamageDisplayManager.forget(packet.entityIds)
    }
}
