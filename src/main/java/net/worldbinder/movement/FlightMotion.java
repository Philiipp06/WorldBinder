package net.worldbinder.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class FlightMotion {
    private static final int DOUBLE_JUMP_TICKS = 7;
    private static volatile LocalPlayer owner;
    private static volatile boolean flying;
    private static boolean jumpHeld;
    private static int tapWindow;

    private FlightMotion() {}

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player != owner) {
            reset();
            owner = player;
        }
        if (player == null || !MovementController.enabled(player, MovementTool.FLY)
                || !MovementController.eligible(player) || player.getAbilities().mayfly
                || player.getAbilities().flying || player.isFallFlying() || player.isSwimming()) {
            reset();
            return;
        }

        boolean jumpDown = client.options.keyJump.isDown();
        if (!MovementController.inputAllowed()) {
            tapWindow = 0;
            jumpHeld = jumpDown;
            return;
        }

        if (tapWindow > 0) tapWindow--;
        if (jumpDown && !jumpHeld) {
            if (tapWindow > 0) {
                flying = !flying;
                tapWindow = 0;
                player.setDeltaMovement(Vec3.ZERO);
                if (flying) player.resetFallDistance();
            } else {
                tapWindow = DOUBLE_JUMP_TICKS;
            }
        }
        jumpHeld = jumpDown;
        if (flying && player.onGround() && !jumpDown) {
            flying = false;
            tapWindow = 0;
        }
    }

    public static boolean active(Entity entity) {
        LocalPlayer current = owner;
        return current != null && flying && Minecraft.getInstance().player == current
                && current.getUUID().equals(entity.getUUID());
    }

    private static void reset() {
        owner = null;
        flying = false;
        jumpHeld = false;
        tapWindow = 0;
    }

    public static void travel(LocalPlayer player, Vec3 input, MovementSettings settings) {
        Minecraft client = Minecraft.getInstance();
        if (!MovementController.inputAllowed()) {
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            return;
        }
        double vertical = (client.options.keyJump.isDown() ? 1 : 0) - (client.options.keyShift.isDown() ? 1 : 0);
        Vec3 direction = new Vec3(input.x, 0, input.z);
        if (direction.lengthSqr() > 1) direction = direction.normalize();
        double speed = 0.25 * settings.value(MovementTool.FLY);
        double sprint = player.isSprinting() || client.options.keySprint.isDown() ? 2 : 1;
        Vec3 previous = player.getDeltaMovement();
        player.setDeltaMovement(Vec3.ZERO);
        player.moveRelative((float)(speed * sprint), direction);
        Vec3 target = player.getDeltaMovement();
        Vec3 motion = previous.scale(0.55).add(new Vec3(target.x, vertical * speed, target.z).scale(0.45));
        player.setDeltaMovement(motion);
        player.move(MoverType.SELF, motion);
        if (player.verticalCollision) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
        }
        player.resetFallDistance();
    }
}
