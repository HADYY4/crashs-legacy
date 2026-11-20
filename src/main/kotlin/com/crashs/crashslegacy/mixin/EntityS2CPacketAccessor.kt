package com.crashs.crashslegacy.mixin

import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

/**
 * `ClientboundMoveEntityPacket` keeps its entity id in a protected field and exposes no getter, so relative
 * move packets cannot be matched to an entity without a world lookup. Reading the field keeps the
 * "禁止实体移动" hook to one integer read per packet.
 */
@Mixin(ClientboundMoveEntityPacket::class)
interface EntityS2CPacketAccessor {

    @Accessor("entityId")
    fun crashsEntityId(): Int
}
