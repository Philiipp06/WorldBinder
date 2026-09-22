package net.worldbinder.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.worldbinder.movement.MovementAttributes;
import net.worldbinder.movement.MovementController;
import net.worldbinder.movement.MovementTool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MovementPlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void worldbinder$attributes(CallbackInfo ci) { MovementAttributes.tick((Player)(Object)this); }

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void worldbinder$noFall(double distance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player)(Object)this;
        if (MovementController.enabled(player, MovementTool.NO_FALL) || MovementController.flight(player)) cir.setReturnValue(false);
    }

    @Inject(method = "maybeBackOffFromEdge", at = @At("RETURN"), cancellable = true)
    private void worldbinder$safeWalk(Vec3 movement, MoverType type, CallbackInfoReturnable<Vec3> cir) {
        Player player = (Player)(Object)this;
        if (!MovementController.local(player) || !MovementController.enabled(player, MovementTool.SAFE_WALK)
                || !player.onGround() || !MovementController.walking(player) || player.isShiftKeyDown()
                || type != MoverType.SELF) return;
        Vec3 result = cir.getReturnValue();
        double x = result.x;
        double z = result.z;
        while (x != 0 && player.level().noCollision(player, player.getBoundingBox().move(x, -0.6, 0))) x = reduce(x);
        while (z != 0 && player.level().noCollision(player, player.getBoundingBox().move(0, -0.6, z))) z = reduce(z);
        while (x != 0 && z != 0 && player.level().noCollision(player, player.getBoundingBox().move(x, -0.6, z))) {
            x = reduce(x); z = reduce(z);
        }
        cir.setReturnValue(new Vec3(x, result.y, z));
    }
    @org.spongepowered.asm.mixin.Unique
    private static double reduce(double value) { return Math.abs(value) < 0.05 ? 0 : value - Math.copySign(0.05, value); }
}
