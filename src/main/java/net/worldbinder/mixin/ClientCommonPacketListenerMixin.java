package net.worldbinder.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerMixin {
    @Inject(method="handleTransfer",at=@At("HEAD"))
    private void worldbinder$transfer(ClientboundTransferPacket packet,CallbackInfo ci){
        if(net.minecraft.client.Minecraft.getInstance().isSameThread()&&net.worldbinder.client.WorldBinderClient.capture()!=null)
            net.worldbinder.client.WorldBinderClient.capture().worldBoundary("transfer");
    }
}
