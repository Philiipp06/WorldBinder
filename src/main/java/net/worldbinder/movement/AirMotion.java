package net.worldbinder.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Airborne and fluid helpers added in WorldBinder 1.6.0. All of them only touch the local player's motion. */
public final class AirMotion {
    private AirMotion() {}

    /** Elytra Boost: steers elytra velocity towards the look direction while Forward is held. */
    public static void elytraBoost(LocalPlayer player, MovementSettings settings) {
        if (!MovementController.enabled(player, MovementTool.ELYTRA_BOOST) || !MovementController.inputAllowed()
                || !player.input.hasForwardImpulse()) return;
        double strength = settings.value(MovementTool.ELYTRA_BOOST);
        Vec3 look = player.getLookAngle();
        Vec3 velocity = player.getDeltaMovement();
        double cruise = 0.45 + 0.35 * strength;
        Vec3 target = look.scale(cruise);
        player.setDeltaMovement(velocity.add(target.subtract(velocity).scale(0.035 * strength)));
    }

    /** Jetpack: holding Jump in the air produces smooth upward thrust. */
    public static void jetpack(LocalPlayer player, MovementSettings settings) {
        if (!MovementController.enabled(player, MovementTool.JETPACK) || player.onGround() || player.isInWater()
                || player.isInLava() || player.onClimbable() || MovementController.spiderActive(player)
                || !Minecraft.getInstance().options.keyJump.isDown()) return;
        double thrust = settings.value(MovementTool.JETPACK);
        Vec3 velocity = player.getDeltaMovement();
        double targetY = 0.11 * thrust;
        if (velocity.y < targetY) {
            player.setDeltaMovement(velocity.x, velocity.y + (targetY - velocity.y) * 0.32, velocity.z);
        }
        player.resetFallDistance();
    }

    /** Auto Surface: floats up through water like holding Jump; Sneak or sprint-swimming dives. */
    public static void autoSurface(LocalPlayer player) {
        if (!MovementController.enabled(player, MovementTool.AUTO_SURFACE) || !player.isInWater()
                || player.isShiftKeyDown() || player.isSwimming() || WaterSurface.supported(player)
                || Minecraft.getInstance().options.keyJump.isDown()) return;
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y < 0.12) {
            player.setDeltaMovement(velocity.x, velocity.y + 0.04, velocity.z);
        }
    }

    /** Long Jump: extends horizontal distance of sprint jumps (called after vanilla jumpFromGround). */
    public static void afterGroundJump(LivingEntity entity) {
        if (!MovementController.local(entity) || !MovementController.walking(entity)) return;
        LocalPlayer player = (LocalPlayer) entity;
        MovementSettings settings = MovementController.settings();
        double factor = 1.0;
        if (MovementController.enabled(player, MovementTool.LONG_JUMP) && player.isSprinting()) {
            factor *= 1.0 + (settings.value(MovementTool.LONG_JUMP) - 1.0) * 0.45;
        }
        if (MovementController.enabled(player, MovementTool.BUNNY_HOP) && player.input.hasForwardImpulse()) {
            factor *= 1.0 + (settings.value(MovementTool.BUNNY_HOP) - 1.0) * 0.35;
        }
        if (factor <= 1.0001) return;
        Vec3 velocity = player.getDeltaMovement();
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        double capped = Math.min(horizontal * factor, 1.4);
        if (horizontal < 1.0E-4) return;
        double scale = capped / horizontal;
        player.setDeltaMovement(velocity.x * scale, velocity.y, velocity.z * scale);
    }
}
