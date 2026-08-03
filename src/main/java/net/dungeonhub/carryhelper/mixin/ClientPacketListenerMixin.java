package net.dungeonhub.carryhelper.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.dungeonhub.carryhelper.features.dungeons.DungeonsFeature;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @WrapOperation(method = "handleBundlePacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private void wrapPacketHandle(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        if(packet instanceof ClientboundSystemChatPacket(Component content, boolean overlay) && !overlay) {
            DungeonsFeature.INSTANCE.handleMessage(content.getString().trim());
        }

        original.call(packet, listener);
    }
}
