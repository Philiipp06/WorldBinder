package net.worldbinder.test.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class DelayedContainerMixin {
    @Inject(method="handleContainerContent",at=@At("HEAD"),cancellable=true)
    private void worldbinder$delayActualContents(ClientboundContainerSetContentPacket packet,CallbackInfo ci){
        if(net.worldbinder.test.DelayedContainerPackets.intercept((ClientPacketListener)(Object)this,packet))ci.cancel();
    }
}
