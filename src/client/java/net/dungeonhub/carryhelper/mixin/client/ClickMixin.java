package net.dungeonhub.carryhelper.mixin.client;

import net.dungeonhub.carryhelper.client.slayer.SlayerBossFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class ClickMixin {
    @Shadow
    public HitResult hitResult;

    @Shadow
    private int missTime;

    @Inject(at = @At("HEAD"), method = "startAttack")
    public void handleLeftClickMouse(CallbackInfoReturnable<Boolean> cir) {
        if (this.missTime > 0) return;

        SlayerBossFeature.INSTANCE.handleLeftClick(this.hitResult);
    }
}