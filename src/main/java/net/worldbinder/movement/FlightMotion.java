package net.worldbinder.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class FlightMotion {
    private FlightMotion() {}

    public static void travel(LocalPlayer player, Vec3 input, MovementSettings settings) {
        Minecraft client = Minecraft.getInstance();
        boolean acceptsInput = MovementController.inputAllowed();
        double vertical = acceptsInput ? (client.options.keyJump.isDown() ? 1 : 0) - (client.options.keyShift.isDown() ? 1 : 0) : 0;
        Vec3 direction = acceptsInput ? new Vec3(input.x, 0, input.z) : Vec3.ZERO;
        if (direction.lengthSqr() > 1) direction = direction.normalize();
        player.setDeltaMovement(Vec3.ZERO);
        player.moveRelative((float)(0.25 * settings.value(MovementTool.FLY)), direction);
        Vec3 motion = player.getDeltaMovement().add(0, vertical * 0.25 * settings.value(MovementTool.FLY), 0);
        player.move(MoverType.SELF, motion);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }
}
