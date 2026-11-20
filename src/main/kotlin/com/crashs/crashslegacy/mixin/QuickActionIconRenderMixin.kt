package com.crashs.crashslegacy.mixin

import com.crashs.crashslegacy.feature.quickactions.QuickActionIcons
import net.minecraft.client.DeltaTracker
import net.minecraft.client.renderer.GameRenderer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Renders queued quick-action icons at the start of the frame, before Minecraft's GUI and before
 * fabric-gui-imgui opens its ImGui frame. This keeps the offscreen FBO switch out of the ImGui
 * newFrame/render pair and also works while a Screen is open.
 */
@Mixin(GameRenderer::class)
abstract class QuickActionIconRenderMixin {

    @Inject(method = ["render"], at = [At("HEAD")])
    private fun crashsLegacyRenderQueuedIcons(
        tracker: DeltaTracker,
        advanceGameTime: Boolean,
        callbackInfo: CallbackInfo,
    ) {
        QuickActionIcons.renderPending()
    }
}
