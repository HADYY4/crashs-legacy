package com.crashs.crashslegacy.mixin;

import com.crashs.crashslegacy.config.CrashsLegacyConfig;
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager;
import com.crashs.crashslegacy.feature.lightning.LightningDebug;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Recolors the vanilla lightning bolt without rebuilding its geometry.
 *
 * The 1.21.11 renderer calls {@code VertexConsumer.color(r, g, b, a)} for each of the four vertices
 * inside {@code drawBranch}; redirecting that call keeps the original branches, radii and alpha
 * intact. (The old Meteor mixin targeted {@code LightningBoltRenderer.quad}, which no longer
 * exists under these Yarn mappings.)
 */
@Mixin(LightningBoltRenderer.class)
public abstract class LightningEntityRendererMixin {

    @Redirect(
        method = "quad",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
        ),
        require = 0
    )
    private static VertexConsumer crashsLegacy$recolor(
        VertexConsumer buffer,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        CrashsLegacyConfig config = CrashsLegacyConfigManager.INSTANCE.getConfig();
        if (!config.getLightningEnabled() || !config.getLightningRecolor()) {
            return buffer.setColor(red, green, blue, alpha);
        }

        LightningDebug.INSTANCE.recolorQuad();
        int argb = config.getLightningColor();
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        return buffer.setColor(r, g, b, alpha);
    }
}
