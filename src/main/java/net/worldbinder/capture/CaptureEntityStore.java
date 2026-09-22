package net.worldbinder.capture;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.worldbinder.WorldBinder;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.scene.EntityRecord;
import net.worldbinder.scene.WorldScene;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CaptureEntityStore {
    private final Set<Integer> capturedIds = new HashSet<>();
    private final Map<Long, Map<String, EntityRecord>> hotChunks = new LinkedHashMap<>();
    private int hotRecordCount;
    private int scanCooldownTicks;

    void cacheHot(Entity entity, BlockPos origin, WorldScene scene,
                  Map<Long, ChunkSnapshot> snapshots, boolean inputPaused) {
        if (entity == null || origin == null || scene == null || inputPaused
                || !WorldBinder.config().captureEntities
                || !WorldBinder.config().includeEntityPlayers && entity instanceof Player) {
            return;
        }
        EntityRecord record = CapturedEntityFactory.create(entity, origin);
        int chunkX = ((int) Math.floor(entity.getX())) >> 4;
        int chunkZ = ((int) Math.floor(entity.getZ())) >> 4;
        long key = ChunkPos.pack(chunkX, chunkZ);
        Map<String, EntityRecord> entities = hotChunks.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        String cacheKey = CapturedEntityFactory.cacheKey(entity);
        EntityRecord previous = entities.get(cacheKey);
        boolean newlyCached = previous == null;
        if (previous == null || CapturedEntityFactory.hasUsefulNbt(record)
                || !CapturedEntityFactory.hasUsefulNbt(previous)) {
            entities.put(cacheKey, record);
        }
        if (newlyCached) {
            hotRecordCount++;
        }
        ChunkSnapshot snapshot = snapshots.computeIfAbsent(key, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        if (newlyCached) {
            snapshot.markEntity();
        }
        scene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
    }

    void captureNearby(Minecraft client, boolean wide, boolean roamingCapture,
                       WorldScene scene, BlockPos origin, Map<Long, ChunkSnapshot> snapshots,
                       boolean inputPaused) {
        if (!WorldBinder.config().captureEntities || scene == null || origin == null
                || client == null || client.level == null || client.player == null
                || !wide && inputPaused) {
            return;
        }
        if (!wide) {
            if (scanCooldownTicks > 0) {
                scanCooldownTicks--;
                return;
            }
            scanCooldownTicks = WorldBinder.config().performancePreset == WorldBinderConfig.PerformancePreset.EXTREME
                    ? 4 : 10;
        }
        int radiusBlocks = Math.max(16, WorldBinder.config().effectiveRoamingRadiusChunks() * 16);
        if (wide && scene.sizeX > 0 && scene.sizeZ > 0) {
            radiusBlocks = Math.max(radiusBlocks, Math.max(scene.sizeX, scene.sizeZ) + 32);
        }
        Vec3 center = client.player.position();
        List<Entity> entities = client.level.getEntitiesOfClass(Entity.class,
                new AABB(center.x - radiusBlocks, WorldBinder.config().effectiveCaptureMinY(),
                        center.z - radiusBlocks, center.x + radiusBlocks,
                        WorldBinder.config().effectiveCaptureMaxY(), center.z + radiusBlocks),
                entity -> CapturedEntityFactory.isExportCandidate(entity)
                        && (WorldBinder.config().includeEntityPlayers || !(entity instanceof Player)));
        for (Entity entity : entities) {
            if (capturedIds.add(entity.getId()) || roamingCapture) {
                if (roamingCapture) {
                    cacheHot(entity, origin, scene, snapshots, false);
                } else {
                    capture(entity, origin, scene, snapshots);
                }
            }
        }
    }

    void clear() {
        capturedIds.clear();
        hotChunks.clear();
        hotRecordCount = 0;
        scanCooldownTicks = 0;
    }

    int hotRecordCount() {
        return hotRecordCount;
    }

    Map<Long, Map<String, EntityRecord>> hotChunks() {
        return hotChunks;
    }

    private void capture(Entity entity, BlockPos origin, WorldScene scene,
                         Map<Long, ChunkSnapshot> snapshots) {
        scene.entities.add(CapturedEntityFactory.create(entity, origin));
        int chunkX = ((int) Math.floor(entity.getX())) >> 4;
        int chunkZ = ((int) Math.floor(entity.getZ())) >> 4;
        long key = ChunkPos.pack(chunkX, chunkZ);
        ChunkSnapshot snapshot = snapshots.computeIfAbsent(key, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        snapshot.markEntity();
        scene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
    }
}
