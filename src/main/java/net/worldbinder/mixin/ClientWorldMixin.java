package net.worldbinder.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.worldbinder.client.WorldBinderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientWorldMixin {
    @Inject(method = "addEntity", at = @At("TAIL"), require = 0)
    private void worldbinder$cacheEntityAfterAdd(Entity entity, CallbackInfo ci) {
        if (WorldBinderClient.capture() != null && entity != null) {
            WorldBinderClient.capture().onEntityLoaded(entity);
        }
    }

    @Inject(method = "removeEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;onClientRemoval()V", shift = At.Shift.BEFORE), require = 0)
    private void worldbinder$cacheEntityBeforeRemoval(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci, @Local Entity entity) {
        if (WorldBinderClient.capture() != null && entity != null) {
            WorldBinderClient.capture().onEntityRemoved(entity);
        }
    }

    @Inject(method = "getMapState", at = @At("HEAD"), require = 0)
    private void worldbinder$mapState(MapId id, CallbackInfoReturnable<MapItemSavedData> cir) {
        if (WorldBinderClient.capture() != null) {
            WorldBinderClient.capture().onMapStateObserved(id);
        }
    }
}
