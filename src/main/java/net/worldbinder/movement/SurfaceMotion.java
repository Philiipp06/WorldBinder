package net.worldbinder.movement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class SurfaceMotion {
    private static java.lang.ref.WeakReference<LocalPlayer> sprintOwner = new java.lang.ref.WeakReference<>(null);
    private SurfaceMotion() {}

    public static void updateSprint(LocalPlayer player) {
        boolean wantsSprint = MovementController.walking(player) && MovementController.inputAllowed()
                && MovementController.enabled(player, MovementTool.AUTO_SPRINT) && player.input.hasForwardImpulse() && !player.isShiftKeyDown()
                && !player.isUsingItem() && !player.horizontalCollision && player.getFoodData().getFoodLevel() > 6;
        if (wantsSprint) {
            if (!player.isSprinting()) sprintOwner = new java.lang.ref.WeakReference<>(player);
            player.setSprinting(true);
        } else if (sprintOwner.get() == player) {
            if (!net.minecraft.client.Minecraft.getInstance().options.keySprint.isDown() || !MovementController.inputAllowed()) {
                player.setSprinting(false);
            }
            sprintOwner.clear();
        }

    }

    public static void beforeTravel(LocalPlayer player, MovementSettings settings) {
        updateSprint(player);
        if (!MovementController.walking(player) || !MovementController.inputAllowed()) return;
        boolean forward = player.input.hasForwardImpulse();
        boolean spider = MovementController.spiderActive(player);
        if (settings.enabled(MovementTool.AUTO_JUMP) && !settings.enabled(MovementTool.STEP)
                && !spider && !player.onClimbable() && !player.input.keyPresses.jump()
                && forward && player.horizontalCollision && player.onGround() && !player.isShiftKeyDown()
                && !player.isInWater() && !player.isInLava() && hasJumpableObstacle(player)) {
            player.jumpFromGround();
        }
        if (spider) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(velocity.x, 0.2, velocity.z);
            player.resetFallDistance();
        }
    }

    private static boolean hasJumpableObstacle(LocalPlayer player) {
        double radians = Math.toRadians(player.getYRot());
        double x = -Math.sin(radians) * 0.35;
        double z = Math.cos(radians) * 0.35;
        var box = player.getBoundingBox();
        return !player.level().noCollision(player, box.move(x, 0, z))
                && player.level().noCollision(player, box.move(0, 1.25, 0))
                && player.level().noCollision(player, box.move(x, 1.25, z));
    }

    public static void afterTravel(LocalPlayer player, MovementSettings settings) {
        if (settings == null || !MovementController.walking(player) || MovementController.spiderActive(player)) return;
        Vec3 motion = player.getDeltaMovement();
        if (!player.onClimbable() && settings.enabled(MovementTool.GLIDE) && !player.onGround() && !player.isInWater()
                && !player.isInLava() && motion.y < 0) {
            player.setDeltaMovement(motion.x, Math.max(motion.y, -0.3 / settings.value(MovementTool.GLIDE)), motion.z);
        }
    }
}
