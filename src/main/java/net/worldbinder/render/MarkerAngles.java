package net.worldbinder.render;

import net.minecraft.world.entity.player.Player;

/** Bounded angles and shortest-path interpolation; frame time is never an angle or an extrapolation factor. */
public final class MarkerAngles {
    private MarkerAngles() {}
    public static float normalize(float degrees) {
        if (!Float.isFinite(degrees)) return 0;
        float result = degrees % 360;
        return result >= 180 ? result - 360 : result < -180 ? result + 360 : result;
    }
    public static float interpolate(float previous, float current, float partial) {
        float fraction = Float.isFinite(partial) ? Math.clamp(partial, 0, 1) : 1;
        return normalize(normalize(previous) + normalize(normalize(current) - normalize(previous)) * fraction);
    }
    public static float look(Player player, float partial) {
        return player instanceof net.minecraft.client.player.LocalPlayer
                ? interpolate(player.yRotO, player.getYRot(), partial)
                : interpolate(player.yHeadRotO, player.getYHeadRot(), partial);
    }
    public static float projected(float yaw, double cameraYaw, double pitch) {
        double angle = Math.toRadians(normalize(yaw - (float) cameraYaw));
        return normalize((float) Math.toDegrees(Math.atan2(Math.sin(angle), Math.cos(angle) * Math.sin(Math.toRadians(pitch)))));
    }
}
