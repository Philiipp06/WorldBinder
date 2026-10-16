package net.worldbinder.movement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
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
        boolean grounded = player.onGround() && !player.isShiftKeyDown() && !player.isInWater() && !player.isInLava()
                && !player.onClimbable() && !spider && !player.input.keyPresses.jump();
        if (grounded && MovementController.enabled(player, MovementTool.AUTO_JUMP) && !MovementController.enabled(player, MovementTool.STEP)
                && forward && player.horizontalCollision && hasJumpableObstacle(player)) {
            player.jumpFromGround();
        } else if (grounded && MovementController.enabled(player, MovementTool.PARKOUR) && atEdge(player)) {
            player.jumpFromGround();
        } else if (grounded && forward && MovementController.enabled(player, MovementTool.BUNNY_HOP)) {
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

    /** True when the next movement step would leave the supporting block edge. */
    static boolean atEdge(LocalPlayer player) {
        Vec3 motion = player.getDeltaMovement();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontal < 0.08) return false;
        double lookAhead = 0.32 / horizontal;
        AABB box = player.getBoundingBox();
        boolean supportedNow = !player.level().noCollision(player, box.move(0, -0.55, 0));
        boolean supportedNext = !player.level().noCollision(player, box.move(motion.x * lookAhead, -0.55, motion.z * lookAhead));
        boolean headroom = player.level().noCollision(player, box.move(0, 1.0, 0));
        return supportedNow && !supportedNext && headroom;
    }

    public static void afterTravel(LocalPlayer player, MovementSettings settings) {
        if (settings == null || !MovementController.walking(player) || MovementController.spiderActive(player)) return;
        Vec3 motion = player.getDeltaMovement();
        if (!player.onClimbable() && MovementController.enabled(player, MovementTool.GLIDE) && !player.onGround() && !player.isInWater()
                && !player.isInLava() && motion.y < 0
                && !(MovementController.enabled(player, MovementTool.JETPACK) && net.minecraft.client.Minecraft.getInstance().options.keyJump.isDown())) {
            player.setDeltaMovement(motion.x, Math.max(motion.y, -0.3 / settings.value(MovementTool.GLIDE)), motion.z);
        }
    }
}
