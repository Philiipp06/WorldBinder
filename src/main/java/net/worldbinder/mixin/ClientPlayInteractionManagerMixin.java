package net.worldbinder.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.worldbinder.client.WorldBinderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayInteractionManagerMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), require = 0)
    private void worldbinder$useItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || WorldBinderClient.capture() == null || hitResult == null) return;
        WorldBinderClient.capture().onInteractBlock(hitResult);
    }

    @Inject(method = "interact", at = @At("HEAD"), require = 0)
    private void worldbinder$interact(Player player, Entity entity, EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || WorldBinderClient.capture() == null || entity == null) return;
        WorldBinderClient.capture().onInteractEntity(entity);
    }

    @Inject(method = "attack", at = @At("HEAD"), require = 0)
    private void worldbinder$attack(Player player, Entity entity, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || WorldBinderClient.capture() == null || entity == null) return;
        WorldBinderClient.capture().onInteractEntity(entity);
    }
}
