package net.dungeonhub.carryhelper.mixin;

import net.dungeonhub.carryhelper.features.slayer.SlayerBossFeature;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setPose(Lnet/minecraft/world/entity/Pose;)V"))
    private void entityDeath(DamageSource source, CallbackInfo ci) {
        SlayerBossFeature.INSTANCE.onEntityDeath((LivingEntity)(Object)this);
    }
}