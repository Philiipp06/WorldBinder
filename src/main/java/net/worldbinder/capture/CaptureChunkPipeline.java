package net.worldbinder.capture;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.status.WorldBinderActivityLog;
import net.worldbinder.util.Chat;
import net.worldbinder.util.Lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class CaptureChunkPipeline extends CaptureBlockScanner {
    private static final int DEFAULT_MAX_NEW_CHUNKS_PER_TICK = 1;
    private static final int DEFAULT_MAX_QUEUED_CHUNKS = 96;
    private static final int STALE_QUEUE_MARGIN_CHUNKS = 2;
    private static final int MAX_HOT_CHUNKS_PER_TICK = 1;
    private static final int MAX_HOTCACHE_CHUNKS_PER_TICK = 24;
    private static final double STATIONARY_EPSILON_SQUARED = 0.0025D;

    public int scannedChunks() {
        return completedChunkKeys.size();
    }

    public int queuedChunks() {
        return pendingChunkKeys.size() + activeScanCursors.size();
    }

    public int partialChunks() {
        return partialChunkKeys.size();
    }

    public int totalKnownChunks() {
        return completedChunkKeys.size() + partialChunkKeys.size() + queuedChunkKeys.size();
    }

    public Set<Long> downloadedChunksSnapshot() {
        return new LinkedHashSet<>(completedChunkKeys);
    }

    public Set<Long> downloadedChunksView() {
        return Collections.unmodifiableSet(completedChunkKeys);
    }

    public Set<Long> queuedChunksSnapshot() {
        return new LinkedHashSet<>(queuedChunkKeys);
    }

    public Set<Long> queuedChunksView() {
        return Collections.unmodifiableSet(queuedChunkKeys);
    }

    public Set<Long> partialChunksSnapshot() {
        return new LinkedHashSet<>(partialChunkKeys);
    }

    public Set<Long> partialChunksView() {
        return Collections.unmodifiableSet(partialChunkKeys);
    }

    public Set<Long> failedChunksSnapshot() {
        return new LinkedHashSet<>(failedChunkKeys);
    }

    public Set<Long> failedChunksView() {
        return Collections.unmodifiableSet(failedChunkKeys);
    }

    public Map<Long, ChunkSnapshot> chunkSnapshots() {
        return new LinkedHashMap<>(liveChunkSnapshots);
    }

    public Map<Long, ChunkSnapshot> chunkSnapshotsView() {
        return Collections.unmodifiableMap(liveChunkSnapshots);
    }

    public long mapDataRevision() {
        return mapDataRevision;
    }

    public long queueSkippedUnloadedCount() {
        return queueSkippedUnloaded;
    }

    public long queueSkippedFarAwayCount() {
        return queueSkippedFarAway;
    }

    public long queuePacketEnqueuedCount() {
        return queuePacketEnqueued;
    }

    public long queueLoadedViewEnqueuedCount() {
        return queueLoadedViewEnqueued;
    }

    public int observedLoadedChunkCount() {
        return observedLoadedChunkAges.size();
    }

    public String queueDiagnosticsLine() {
        return Lang.string("worldbinder.capture.queue_diagnostics", observedLoadedChunkAges.size(), queuePacketEnqueued, queueLoadedViewEnqueued, queueSkippedUnloaded, queueSkippedFarAway);
    }

    public int adaptiveThrottlePercent() {
        return adaptiveThrottlePercent;
    }

    public boolean renderingQualityReduced() {
        return WorldBinder.config().effectiveAdaptiveThrottleEnabled() && adaptiveThrottlePercent < 70;
    }

    public boolean highQueuePressure() {
        int limit = WorldBinder.config().effectiveChunkQueueLimit();
        int queued = queuedChunks();
        return queued > 512 || (limit != Integer.MAX_VALUE && queued > Math.max(24, limit * 3 / 4));
    }

    public String captureRouteHint() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return Lang.string("worldbinder.capture.route.waiting");
        }
        int playerChunkX = client.player.blockPosition().getX() >> 4;
        int playerChunkZ = client.player.blockPosition().getZ() >> 4;
        return CaptureRouteGuide.routeHint(playerChunkX, playerChunkZ, isCapturing(),
                pendingChunkKeys, queuedChunkKeys, partialChunkKeys, failedChunkKeys);
    }

    protected static int unpackChunkX(long key) {
        return (int) key;
    }

    protected static int unpackChunkZ(long key) {
        return (int) (key >> 32);
    }

    public void onChunkLoadedOrUpdated(int chunkX, int chunkZ) {
        if (!isCapturing() || !roamingCapture || paused || saving) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        long key = ChunkPos.pack(chunkX, chunkZ);
        if (!isInsideActiveRoamingRadius(client, key, 0)) {
            queueSkippedFarAway++;
            return;
        }
        if (!isClientChunkLoaded(client, chunkX, chunkZ)) {
            queueSkippedUnloaded++;
            return;
        }
        observedLoadedChunkAges.put(key, 0);
        if (isDone(key)) {
            return;
        }
        if (captureLoadedChunkHot(client, chunkX, chunkZ, key, "chunk-load", false)) {
            queuePacketEnqueued++;
        }
    }

    public void onChunkUnloaded(int chunkX, int chunkZ) {
        if (!isCapturing()) {
            return;
        }
        long key = ChunkPos.pack(chunkX, chunkZ);
        observedLoadedChunkAges.remove(key);
        if (!completedChunkKeys.contains(key) && queuedChunkKeys.contains(key) && !activeScanChunkKeys.contains(key)) {
            pendingChunkKeys.remove(key);
            queuedChunkKeys.remove(key);
            queueSkippedUnloaded++;
            ChunkSnapshot snapshot = liveChunkSnapshots.get(key);
            if (snapshot != null && !snapshot.isDone()) {
                snapshot.markPartial();
            }
            mapDataRevision++;
        }
    }

    public void queueChunkForRescan(int chunkX, int chunkZ) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        completedChunkKeys.remove(key);
        partialChunkKeys.remove(key);
        failedChunkKeys.remove(key);
        clearCapturedBlocksForChunk(chunkX, chunkZ);
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(key, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        snapshot.markQueued("manual-rescan", "ui");
        mapDataRevision++;
        if (queuedChunkKeys.add(key)) {
            pendingChunkKeys.addFirst(key);
        }
        Chat.infoKey("worldbinder.chat.chunk_rescan_queued", chunkX, chunkZ);
        WorldBinderActivityLog.add(Lang.string("worldbinder.activity.chunk_rescan_queued", chunkX, chunkZ));
    }

    protected void clearCapturedBlocksForChunk(int chunkX, int chunkZ) {
        if (capturedBlockPositions.isEmpty()) {
            return;
        }
        int minY = WorldBinder.config().effectiveCaptureMinY();
        int maxY = WorldBinder.config().effectiveCaptureMaxY();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    capturedBlockPositions.remove(new BlockPos((chunkX << 4) + x, y, (chunkZ << 4) + z).asLong());
                }
            }
        }
    }

    protected void updateObservedLoadedChunks(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            return;
        }
        int centerChunkX = client.player.blockPosition().getX() >> 4;
        int centerChunkZ = client.player.blockPosition().getZ() >> 4;
        int radius = Math.max(1, WorldBinder.config().effectiveRoamingRadiusChunks());
        int keepRadius = radius + STALE_QUEUE_MARGIN_CHUNKS;

        observedLoadedChunkAges.replaceAll((key, age) -> age == null ? 1 : age + 1);
        observedLoadedChunkAges.entrySet().removeIf(entry -> {
            long key = entry.getKey();
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            boolean far = Math.max(Math.abs(chunkX - centerChunkX), Math.abs(chunkZ - centerChunkZ)) > keepRadius;
            boolean expired = entry.getValue() > WorldBinder.config().effectiveQueueLoadedChunkGraceTicks();
            boolean unloaded = !isClientChunkLoaded(client, chunkX, chunkZ);
            return far || expired || unloaded;
        });

        for (int distance = 0; distance <= radius; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) {
                        continue;
                    }
                    int chunkX = centerChunkX + dx;
                    int chunkZ = centerChunkZ + dz;
                    if (isClientChunkLoaded(client, chunkX, chunkZ)) {
                        observedLoadedChunkAges.put(ChunkPos.pack(chunkX, chunkZ), 0);
                    }
                }
            }
        }
    }

    protected void primeLoadedChunkHotCache(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            return;
        }
        updateObservedLoadedChunks(client);
    }

    protected void flushLoadedChunksForFinish(Minecraft client) {
        if (!roamingCapture || client == null || client.level == null || client.player == null) {
            return;
        }
        updateObservedLoadedChunks(client);
        int centerChunkX = client.player.blockPosition().getX() >> 4;
        int centerChunkZ = client.player.blockPosition().getZ() >> 4;
        List<Long> observed = new ArrayList<>(observedLoadedChunkAges.keySet());
        observed.sort((a, b) -> Integer.compare(distanceSq(a, centerChunkX, centerChunkZ), distanceSq(b, centerChunkX, centerChunkZ)));
        for (long key : observed) {
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            if (isClientChunkLoaded(client, chunkX, chunkZ)) {
                captureLoadedChunkHot(client, chunkX, chunkZ, key, "finish-sync", false);
            }
        }
    }

    protected void cacheObservedLoadedChunks(Minecraft client) {
        int centerChunkX = client.player.blockPosition().getX() >> 4;
        int centerChunkZ = client.player.blockPosition().getZ() >> 4;
        int radius = Math.max(1, WorldBinder.config().effectiveRoamingRadiusChunks());
        trimStaleQueuedChunks(client, centerChunkX, centerChunkZ, radius + STALE_QUEUE_MARGIN_CHUNKS);

        List<Long> observed = new ArrayList<>(observedLoadedChunkAges.keySet());
        observed.sort((a, b) -> Integer.compare(distanceSq(a, centerChunkX, centerChunkZ), distanceSq(b, centerChunkX, centerChunkZ)));
        int captured = 0;
        int maxPerTick = hotCacheChunkLimit();
        long deadline = System.nanoTime() + adaptiveTickBudgetMillis(client) * 1_000_000L;
        for (long key : observed) {
            if (captured >= maxPerTick || System.nanoTime() >= deadline) {
                break;
            }
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            if (Math.max(Math.abs(chunkX - centerChunkX), Math.abs(chunkZ - centerChunkZ)) > radius) {
                queueSkippedFarAway++;
                continue;
            }
            if (!isClientChunkLoaded(client, chunkX, chunkZ)) {
                queueSkippedUnloaded++;
                continue;
            }
            if (isDone(key) || failedChunkKeys.contains(key)) {
                continue;
            }
            if (captureLoadedChunkHot(client, chunkX, chunkZ, key, "loaded-view", false)) {
                captured++;
                queueLoadedViewEnqueued++;
            }
        }
    }

    protected int hotCacheChunkLimit() {
        int configured = WorldBinder.config().effectiveNewChunksPerTick();
        if (configured == Integer.MAX_VALUE || configured < 0) {
            configured = isPlayerStationary() ? 20 : 10;
        }
        int limit = Math.max(1, Math.min(MAX_HOTCACHE_CHUNKS_PER_TICK, configured));
        if (WorldBinder.config().effectiveAdaptiveThrottleEnabled() && adaptiveThrottlePercent < 70) {
            limit = Math.max(1, limit * Math.max(35, adaptiveThrottlePercent) / 100);
        }
        if (multiplayerSafetyActive()) {
            limit = Math.min(limit, WorldBinder.config().performancePreset == net.worldbinder.config.WorldBinderConfig.PerformancePreset.EXTREME ? 16 : 10);
        }
        return limit;
    }

    protected boolean captureLoadedChunkHot(Minecraft client, int chunkX, int chunkZ, long key, String reason, boolean replaceDone) {
        if (!isCapturing() || activeScene == null || client == null || client.level == null) {
            return false;
        }
        if (!replaceDone && completedChunkKeys.contains(key)) {
            return false;
        }
        if (!isClientChunkLoaded(client, chunkX, chunkZ)) {
            queueSkippedUnloaded++;
            return false;
        }
        LevelChunk chunk = client.level.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            queueSkippedUnloaded++;
            return false;
        }
        pendingChunkKeys.remove(key);
        queuedChunkKeys.remove(key);
        removeActiveScan(key);
        blockStore.discard(key);
        partialChunkKeys.add(key);
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(key, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        snapshot.beginBlockScan();
        snapshot.markQueued(reason, "hot-cache");
        snapshot.markScanning();
        activeScene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
        mapDataRevision++;

        int minY = WorldBinder.config().effectiveCaptureMinY();
        int maxY = WorldBinder.config().effectiveCaptureMaxY();
        // Do not trust LevelChunk#isEmpty as a final export signal. In large roaming sessions
        // Minecraft can expose a chunk object before all client section data is useful to us.
        // WorldBinder therefore scans the readable sections and only completes the chunk after
        // real block payload was committed to the durable chunk cache.

        for (int sectionY = minY >> 4; sectionY <= maxY >> 4; sectionY++) {
            int yStart = Math.max(minY, sectionY << 4);
            int yEnd = Math.min(maxY, (sectionY << 4) + 15);
            if (!WorldBinder.config().captureAir && isEmptySection(chunk, yStart)) {
                int skippedBlocks = (yEnd - yStart + 1) * 16 * 16;
                snapshot.markScanned(skippedBlocks, 0, 0);
                processedBlocks += skippedBlocks;
                scheduledBlocks += skippedBlocks;
                continue;
            }
            for (int y = yStart; y <= yEnd; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockPos pos = new BlockPos((chunkX << 4) + x, y, (chunkZ << 4) + z);
                        captureBlockIntoSnapshot(client, chunk, pos, snapshot);
                        expandBounds(pos);
                        processedBlocks++;
                        scheduledBlocks++;
                    }
                }
            }
        }
        snapshot.hasBiomeData = true;
        snapshot.lightEstimated = true;
        partialChunkKeys.remove(key);
        completeChunkAfterBlockScan(key, snapshot, chunk);
        activeScene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
        return true;
    }

    protected void scheduleLoadedObservedChunks(Minecraft client) {
        int queueLimit = WorldBinder.config().effectiveChunkQueueLimit();
        int centerChunkX = client.player.blockPosition().getX() >> 4;
        int centerChunkZ = client.player.blockPosition().getZ() >> 4;
        int radius = Math.max(1, WorldBinder.config().effectiveRoamingRadiusChunks());
        int maxNew = newChunkScheduleLimit();

        trimStaleQueuedChunks(client, centerChunkX, centerChunkZ, radius + STALE_QUEUE_MARGIN_CHUNKS);
        reprioritizeVisibleQueue(centerChunkX, centerChunkZ);

        List<Long> observed = new ArrayList<>(observedLoadedChunkAges.keySet());
        observed.sort((a, b) -> Integer.compare(distanceSq(a, centerChunkX, centerChunkZ), distanceSq(b, centerChunkX, centerChunkZ)));

        int added = 0;
        for (long key : observed) {
            if (added >= maxNew) {
                break;
            }
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            if (Math.max(Math.abs(chunkX - centerChunkX), Math.abs(chunkZ - centerChunkZ)) > radius) {
                queueSkippedFarAway++;
                continue;
            }
            if (!isClientChunkLoaded(client, chunkX, chunkZ)) {
                queueSkippedUnloaded++;
                continue;
            }
            if (isDone(key) || failedChunkKeys.contains(key) || activeScanChunkKeys.contains(key)) {
                continue;
            }
            int priorityRadius = Math.max(1, WorldBinder.config().effectiveVisibleChunkPriorityRadius());
            boolean priority = distanceSq(key, centerChunkX, centerChunkZ) <= priorityRadius * priorityRadius;
            if (enqueueChunk(key, priority, "client-loaded", "loaded-view")) {
                added++;
                queueLoadedViewEnqueued++;
            } else if (priority && queuedChunkKeys.contains(key)) {
                pendingChunkKeys.remove(key);
                pendingChunkKeys.addFirst(key);
            }
            if (queueLimit != Integer.MAX_VALUE && pendingChunkKeys.size() >= queueLimit) {
                return;
            }
        }
    }

    protected boolean enqueueChunk(long key, boolean priority) {
        return enqueueChunk(key, priority, "legacy", "unknown");
    }

    protected boolean enqueueChunk(long key, boolean priority, String reason, String source) {
        Minecraft client = Minecraft.getInstance();
        if (roamingCapture && client != null && client.level != null && client.player != null) {
            if (!isInsideActiveRoamingRadius(client, key, 0)) {
                queueSkippedFarAway++;
                return false;
            }
            if (!isClientChunkLoaded(client, unpackChunkX(key), unpackChunkZ(key))) {
                queueSkippedUnloaded++;
                return false;
            }
        }
        if (!queuedChunkKeys.add(key)) {
            return false;
        }
        int queueLimit = WorldBinder.config().effectiveChunkQueueLimit();
        if (queueLimit != Integer.MAX_VALUE && pendingChunkKeys.size() >= queueLimit) {
            if (!priority) {
                queuedChunkKeys.remove(key);
                return false;
            }
            Long dropped = pendingChunkKeys.pollLast();
            if (dropped != null) {
                queuedChunkKeys.remove(dropped);
            }
        }
        int chunkX = unpackChunkX(key);
        int chunkZ = unpackChunkZ(key);
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(key, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        if (!snapshot.isDone()) {
            snapshot.markQueued(reason, source);
        }
        if (priority) {
            pendingChunkKeys.addFirst(key);
        } else {
            pendingChunkKeys.add(key);
        }
        mapDataRevision++;
        return true;
    }

    protected void trimStaleQueuedChunks(Minecraft client, int centerChunkX, int centerChunkZ, int keepRadius) {
        if (pendingChunkKeys.isEmpty()) {
            return;
        }
        pendingChunkKeys.removeIf(key -> {
            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            boolean stale = Math.max(Math.abs(chunkX - centerChunkX), Math.abs(chunkZ - centerChunkZ)) > keepRadius;
            boolean unloaded = roamingCapture && client != null && client.level != null && !isClientChunkLoaded(client, chunkX, chunkZ);
            if (stale || unloaded) {
                queuedChunkKeys.remove(key);
                if (stale) queueSkippedFarAway++;
                if (unloaded) queueSkippedUnloaded++;
            }
            return stale || unloaded;
        });
    }

    protected void reprioritizeVisibleQueue(int centerChunkX, int centerChunkZ) {
        if (pendingChunkKeys.size() < 2) {
            return;
        }
        List<Long> sorted = new ArrayList<>(pendingChunkKeys);
        sorted.sort((a, b) -> Integer.compare(distanceSq(a, centerChunkX, centerChunkZ), distanceSq(b, centerChunkX, centerChunkZ)));
        pendingChunkKeys.clear();
        pendingChunkKeys.addAll(sorted);
    }

    protected int distanceSq(long key, int centerChunkX, int centerChunkZ) {
        int dx = unpackChunkX(key) - centerChunkX;
        int dz = unpackChunkZ(key) - centerChunkZ;
        return dx * dx + dz * dz;
    }

    protected boolean isInsideActiveRoamingRadius(Minecraft client, long key, int extraRadius) {
        if (client == null || client.player == null) {
            return false;
        }
        int centerChunkX = client.player.blockPosition().getX() >> 4;
        int centerChunkZ = client.player.blockPosition().getZ() >> 4;
        int radius = Math.max(1, WorldBinder.config().effectiveRoamingRadiusChunks()) + Math.max(0, extraRadius);
        return Math.max(Math.abs(unpackChunkX(key) - centerChunkX), Math.abs(unpackChunkZ(key) - centerChunkZ)) <= radius;
    }

    protected boolean isClientChunkLoaded(Minecraft client, int chunkX, int chunkZ) {
        if (client == null || client.level == null) {
            return false;
        }
        try {
            return client.level.hasChunk(chunkX, chunkZ);
        } catch (Throwable ignored) {
            return false;
        }
    }

    protected int newChunkScheduleLimit() {
        int configured = WorldBinder.config().effectiveNewChunksPerTick();
        if (configured == Integer.MAX_VALUE || configured < 0) {
            configured = isPlayerStationary() ? 6 : 2;
        }
        int cap = isPlayerStationary() ? 6 : 2;
        if (WorldBinder.config().effectiveAdaptiveThrottleEnabled() && adaptiveThrottlePercent < 40) {
            cap = 1;
        } else if (WorldBinder.config().effectiveAdaptiveThrottleEnabled() && adaptiveThrottlePercent < 70) {
            cap = Math.max(1, cap / 2);
        }
        if (multiplayerSafetyActive()) {
            cap = Math.min(cap, WorldBinder.config().performancePreset == net.worldbinder.config.WorldBinderConfig.PerformancePreset.EXTREME ? 3 : 2);
        }
        return Math.max(DEFAULT_MAX_NEW_CHUNKS_PER_TICK, Math.min(configured, cap));
    }

    protected void updateMovementState(Minecraft client) {
        Vec3 current = client.player.position();
        if (lastPlayerPos != null && current.distanceToSqr(lastPlayerPos) <= STATIONARY_EPSILON_SQUARED) {
            stationaryTicks = Math.min(200, stationaryTicks + 1);
        } else {
            stationaryTicks = 0;
        }
        lastPlayerPos = current;
    }

    protected boolean isPlayerStationary() {
        return stationaryTicks >= 10;
    }

    protected void processHotVisibleChunks(Minecraft client) {
        int hotChunks = WorldBinder.config().effectiveHotChunksPerTick();
        if (hotChunks <= 0 || client == null || client.level == null || client.player == null) {
            return;
        }
        if (!isPlayerStationary()) {
            hotChunks = Math.min(1, hotChunks);
        }
        if (WorldBinder.config().effectiveAdaptiveThrottleEnabled() && adaptiveThrottlePercent < 50) {
            return;
        }
        hotChunks = Math.min(MAX_HOT_CHUNKS_PER_TICK, hotChunks);
        int centerChunkX = client.player.blockPosition().getX() >> 4;
        int centerChunkZ = client.player.blockPosition().getZ() >> 4;
        int queued = 0;
        int hotRadius = Math.max(1, Math.min(WorldBinder.config().effectiveVisibleChunkPriorityRadius(), WorldBinder.config().effectiveRoamingRadiusChunks()));
        for (int distance = 0; distance <= hotRadius && queued < hotChunks; distance++) {
            for (int dx = -distance; dx <= distance && queued < hotChunks; dx++) {
                for (int dz = -distance; dz <= distance && queued < hotChunks; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) {
                        continue;
                    }
                    int chunkX = centerChunkX + dx;
                    int chunkZ = centerChunkZ + dz;
                    long key = ChunkPos.pack(chunkX, chunkZ);
                    if (failedChunkKeys.contains(key) || isDone(key) || activeScanChunkKeys.contains(key)) {
                        continue;
                    }
                    if (!isClientChunkLoaded(client, chunkX, chunkZ)) {
                        queueSkippedUnloaded++;
                        continue;
                    }
                    observedLoadedChunkAges.put(key, 0);
                    if (enqueueChunk(key, true, "hot-visible", "loaded-view")) {
                        queued++;
                        queueLoadedViewEnqueued++;
                    }
                }
            }
        }
    }

}
