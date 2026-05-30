package net.dungeonhub.carryhelper.mixin;

import net.dungeonhub.carryhelper.features.overlay.TicketOverlayFeature;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class HudRenderMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRenderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        TicketOverlayFeature.INSTANCE.render(graphics);
    }
}
