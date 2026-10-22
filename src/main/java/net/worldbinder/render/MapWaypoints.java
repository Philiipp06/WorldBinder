package net.worldbinder.render;

import net.minecraft.client.Minecraft;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.config.WorldBinderConfig.MapWaypoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Map waypoints: named points the player places on the live map (right click), plus an optional automatic
 * "last death" point. Waypoints are stored in the config per world/server and dimension.
 */
public final class MapWaypoints {
    public static final int MAX_PER_WORLD = 200;
    public static final int[] COLORS = {0xFF4FD1C5, 0xFFFFB45E, 0xFF78E08A, 0xFFFF6B8B, 0xFF9B8CFF, 0xFFFFE066, 0xFF5DADE2, 0xFFE5E7EB};
    private static boolean wasDead;

    private MapWaypoints() {
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            wasDead = false;
            return;
        }
        boolean dead = client.player.isDeadOrDying();
        if (dead && !wasDead && WorldBinder.config().mapDeathWaypoint) {
            WorldBinderConfig config = WorldBinder.config();
            String world = worldKey(client);
            config.mapWaypoints.removeIf(point -> point.death && world.equals(point.world));
            MapWaypoint point = create(client, "", client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ());
            point.death = true;
            point.color = 0xFFFF5D5D;
            config.mapWaypoints.add(point);
            config.save();
        }
        wasDead = dead;
    }

    /** Waypoints of the current world and dimension. */
    public static List<MapWaypoint> current() {
        Minecraft client = Minecraft.getInstance();
        List<MapWaypoint> result = new ArrayList<>();
        if (client.level == null) {
            return result;
        }
        String world = worldKey(client);
        String dimension = dimensionKey(client);
        for (MapWaypoint point : WorldBinder.config().mapWaypoints) {
            if (world.equals(point.world) && dimension.equals(point.dimension)) {
                result.add(point);
            }
        }
        return result;
    }

    public static MapWaypoint add(int x, int y, int z) {
        Minecraft client = Minecraft.getInstance();
        WorldBinderConfig config = WorldBinder.config();
        List<MapWaypoint> existing = current();
        if (existing.size() >= MAX_PER_WORLD) {
            return null;
        }
        int number = existing.size() + 1;
        MapWaypoint point = create(client, "", x, y, z);
        point.name = net.worldbinder.util.Lang.string("worldbinder.map.waypoint.default_name", number);
        point.color = COLORS[(number - 1) % COLORS.length];
        config.mapWaypoints.add(point);
        config.save();
        return point;
    }

    public static void remove(MapWaypoint point) {
        WorldBinder.config().mapWaypoints.remove(point);
        WorldBinder.config().save();
    }

    public static void clearCurrent() {
        List<MapWaypoint> current = current();
        WorldBinder.config().mapWaypoints.removeAll(current);
        WorldBinder.config().save();
    }

    public static String label(MapWaypoint point) {
        return point.death ? net.worldbinder.util.Lang.string("worldbinder.map.waypoint.death") : point.name;
    }

    private static MapWaypoint create(Minecraft client, String name, int x, int y, int z) {
        MapWaypoint point = new MapWaypoint();
        point.name = name;
        point.world = worldKey(client);
        point.dimension = dimensionKey(client);
        point.x = x;
        point.y = y;
        point.z = z;
        point.createdAt = System.currentTimeMillis();
        return point;
    }

    static String worldKey(Minecraft client) {
        try {
            if (client.getSingleplayerServer() != null) {
                return "sp:" + client.getSingleplayerServer().getWorldData().getLevelName();
            }
            if (client.getCurrentServer() != null) {
                return "mp:" + client.getCurrentServer().ip;
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return "unknown";
    }

    static String dimensionKey(Minecraft client) {
        return client.level == null ? "" : client.level.dimension().toString();
    }
}
