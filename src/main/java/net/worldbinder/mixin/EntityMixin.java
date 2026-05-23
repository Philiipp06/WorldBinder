package net.worldbinder.mixin;

import net.minecraft.world.entity.Entity;
import net.worldbinder.client.WorldBinderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"), require = 0)
    private void worldbinder$cacheBeforeSetRemoved(Entity.RemovalReason removalReason, CallbackInfo ci) {
        if (WorldBinderClient.capture() != null) {
            WorldBinderClient.capture().onEntityRemoved((Entity) (Object) this);
        }
    }
}
