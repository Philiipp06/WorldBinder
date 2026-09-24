package net.worldbinder.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.worldbinder.WorldBinder;

public final class MovementController {
    private MovementController() {}

    public static MovementSettings settings() { return WorldBinder.config().movement; }

    public static boolean local(Entity entity) {
        return entity instanceof LocalPlayer && entity == Minecraft.getInstance().player;
    }

    public static boolean ownedPlayer(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        return entity instanceof Player && (local(entity) ||
                (client.hasSingleplayerServer() && client.isLocalPlayer(entity.getUUID())));
    }

    public static boolean enabled(Entity entity, MovementTool tool) {
        return ownedPlayer(entity) && settings() != null && settings().enabled(tool);
    }

    public static boolean flight(Entity entity) {
        return enabled(entity, MovementTool.FLY) && eligible(entity)
                && !((Player)entity).getAbilities().mayfly && FlightMotion.active(entity);
    }

    public static boolean eligible(Entity entity) {
        return ownedPlayer(entity) && entity.isAlive() && !entity.isPassenger() && !entity.isSpectator();
    }

    public static boolean walking(Entity entity) {
        return eligible(entity) && !flight(entity) && !((Player)entity).getAbilities().flying
                && !((Player)entity).isFallFlying();
    }

    public static boolean inputAllowed() {
        return Minecraft.getInstance().gui.screen() == null;
    }

    public static boolean spiderActive(Entity entity) {
        return local(entity) && walking(entity) && inputAllowed() && enabled(entity, MovementTool.SPIDER)
                && !entity.isShiftKeyDown() && !((LocalPlayer)entity).onClimbable()
                && !entity.isInWater() && !entity.isInLava()
                && ((LocalPlayer)entity).input.hasForwardImpulse() && entity.horizontalCollision
                && !entity.level().noCollision(entity, entity.getBoundingBox().inflate(0.04, -0.01, 0.04));
    }

    public static boolean travel(LivingEntity entity, Vec3 input) {
        if (!local(entity)) return false;
        LocalPlayer player = (LocalPlayer) entity;
        SurfaceMotion.updateSprint(player);
        if (!eligible(entity)) return false;
        MovementSettings settings = settings();
        if (settings == null) return false;
        if (flight(player)) {
            FlightMotion.travel(player, input, settings);
            return true;
        }
        if (!walking(player)) return false;
        SurfaceMotion.beforeTravel(player, settings);
        if (!inputAllowed()) return false;
        if (settings.enabled(MovementTool.AIR_CONTROL) && !player.onGround() && !player.isInWater()
                && !player.isInLava() && !player.onClimbable()) {
            player.moveRelative((float)(0.02 * (settings.value(MovementTool.AIR_CONTROL) - 1)), input);
        }
        if (settings.enabled(MovementTool.WATER_SPEED) && player.isInWater() && !WaterSurface.supported(player)) {
            player.moveRelative((float)(0.02 * (settings.value(MovementTool.WATER_SPEED) - 1)), input);
        }
        return false;
    }

    public static void afterTravel(LivingEntity entity) {
        if (!local(entity) || !walking(entity)) return;
        SurfaceMotion.afterTravel((LocalPlayer) entity, settings());
    }
}
