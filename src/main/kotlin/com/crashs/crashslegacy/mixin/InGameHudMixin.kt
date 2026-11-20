package com.crashs.crashslegacy.mixin

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.Gui
import net.minecraft.client.DeltaTracker
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import com.crashs.crashslegacy.hud.CoinsInfoHud
import com.crashs.crashslegacy.hud.CrashsLegacyHud
import com.crashs.crashslegacy.hud.ItemTrackerHud

@Mixin(Gui::class)
abstract class InGameHudMixin {

    @Inject(method = ["render"], at = [At("TAIL")])
    private fun renderCrashsLegacy(context: GuiGraphics, tickCounter: DeltaTracker, callbackInfo: CallbackInfo) {
        CrashsLegacyHud.render(context)
        CoinsInfoHud.render(context)
        ItemTrackerHud.render(context)
    }
}
