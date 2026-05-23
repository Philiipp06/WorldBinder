package net.worldbinder.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.worldbinder.compat.ResourcePackCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void worldbinder$fakeResourcePackSuccess(Packet<?> packet, CallbackInfo ci) {
        Packet<?> replacement = ResourcePackCompatibility.replacementFor(packet);
        if (replacement == null) {
            return;
        }
        ((Connection) (Object) this).send(replacement);
        ci.cancel();
    }
}
