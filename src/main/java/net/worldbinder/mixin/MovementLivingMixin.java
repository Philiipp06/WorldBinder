package net.worldbinder.mixin;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.worldbinder.movement.MovementController;
import net.worldbinder.movement.MovementTool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MovementLivingMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void worldbinder$jumpPriority(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (MovementController.local(entity) && (MovementController.flight(entity) || MovementController.spiderActive(entity))) ci.cancel();
    }
    @Inject(method = "handleOnClimbable", at = @At("RETURN"), cancellable = true)
    private void worldbinder$ladderSpeed(Vec3 input, CallbackInfoReturnable<Vec3> cir) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (!MovementController.local(entity) || !MovementController.enabled(entity, MovementTool.FAST_LADDER)
                || !entity.onClimbable() || !MovementController.walking(entity) || !MovementController.inputAllowed()) return;
        net.minecraft.client.player.LocalPlayer player = (net.minecraft.client.player.LocalPlayer) entity;
        Vec3 motion = cir.getReturnValue();
        double speed = MovementController.settings().value(MovementTool.FAST_LADDER);
        double y = player.isShiftKeyDown() ? 0 : player.input.hasForwardImpulse() || player.input.keyPresses.jump()
                ? 0.2 * speed : -0.15 * speed;
        cir.setReturnValue(new Vec3(motion.x, y, motion.z));
    }
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void worldbinder$travel(Vec3 input, CallbackInfo ci) {
        if (MovementController.travel((LivingEntity)(Object)this, input)) ci.cancel();
    }
    @Inject(method = "travel", at = @At("RETURN"))
    private void worldbinder$afterTravel(Vec3 input, CallbackInfo ci) {
        MovementController.afterTravel((LivingEntity)(Object)this);
    }
    @Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
    private void worldbinder$waterSurface(FluidState state, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (state.is(FluidTags.WATER) && net.worldbinder.movement.WaterSurface.supported(entity)) cir.setReturnValue(true);
    }
}
