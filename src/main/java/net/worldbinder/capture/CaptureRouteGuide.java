package net.worldbinder.capture;

import net.worldbinder.util.Lang;

import java.util.Collection;

final class CaptureRouteGuide {
    private CaptureRouteGuide() {
    }

    static String routeHint(int playerChunkX, int playerChunkZ, boolean capturing,
                            Collection<Long> pendingChunks, Collection<Long> queuedChunks,
                            Collection<Long> partialChunks, Collection<Long> failedChunks) {
        RouteTarget target = null;
        target = nearest(target, pendingChunks, playerChunkX, playerChunkZ, Lang.string("worldbinder.capture.route.queued"));
        target = nearest(target, queuedChunks, playerChunkX, playerChunkZ, Lang.string("worldbinder.capture.route.queued"));
        target = nearest(target, partialChunks, playerChunkX, playerChunkZ, Lang.string("worldbinder.capture.route.partial"));
        target = nearest(target, failedChunks, playerChunkX, playerChunkZ, Lang.string("worldbinder.capture.route.problem"));
        if (target == null) {
            return Lang.string(capturing ? "worldbinder.capture.route.stable" : "worldbinder.capture.route.start");
        }
        int dx = target.chunkX() - playerChunkX;
        int dz = target.chunkZ() - playerChunkZ;
        return Lang.string("worldbinder.capture.route.target", target.reason(), target.chunkX(), target.chunkZ(),
                direction(dx, dz), Math.max(Math.abs(dx), Math.abs(dz)));
    }

    private static RouteTarget nearest(RouteTarget current, Collection<Long> candidates,
                                       int playerChunkX, int playerChunkZ, String reason) {
        RouteTarget best = current;
        for (long key : candidates) {
            int chunkX = (int) key;
            int chunkZ = (int) (key >> 32);
            int distance = Math.max(Math.abs(chunkX - playerChunkX), Math.abs(chunkZ - playerChunkZ));
            if (best == null || distance < best.distance()) {
                best = new RouteTarget(chunkX, chunkZ, distance, reason);
            }
        }
        return best;
    }

    private static String direction(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return Lang.string("worldbinder.capture.route.there");
        }
        String eastWest = dx > 0 ? Lang.string("worldbinder.direction.east")
                : dx < 0 ? Lang.string("worldbinder.direction.west") : "";
        String northSouth = dz > 0 ? Lang.string("worldbinder.direction.south")
                : dz < 0 ? Lang.string("worldbinder.direction.north") : "";
        if (eastWest.isEmpty()) {
            return Lang.string("worldbinder.capture.route.go", northSouth);
        }
        if (northSouth.isEmpty()) {
            return Lang.string("worldbinder.capture.route.go", eastWest);
        }
        return Lang.string("worldbinder.capture.route.go_diagonal", northSouth, eastWest);
    }

    private record RouteTarget(int chunkX, int chunkZ, int distance, String reason) {
    }
}
