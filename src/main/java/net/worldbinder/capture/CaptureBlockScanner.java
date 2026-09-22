package net.worldbinder.capture;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.BlockRecord;
import net.worldbinder.scene.ChunkCaptureStatus;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.util.BlockStateStrings;
import net.worldbinder.util.Lang;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

abstract class CaptureBlockScanner {
    private static final int MAX_ACTIVE_SCAN_CURSORS = 6;

    protected final ArrayDeque<BlockPos> pendingBlocks = new ArrayDeque<>();
    protected final ArrayDeque<Long> pendingChunkKeys = new ArrayDeque<>();
    protected final ArrayDeque<ScanCursor> activeScanCursors = new ArrayDeque<>();
    protected final Set<Long> capturedBlockPositions = new HashSet<>();
    protected final Set<Long> queuedChunkKeys = new HashSet<>();
    protected final Set<Long> activeScanChunkKeys = new HashSet<>();
    protected final Map<Long, Integer> observedLoadedChunkAges = new LinkedHashMap<>();
    protected long queueSkippedUnloaded;
    protected long queueSkippedFarAway;
    protected long queuePacketEnqueued;
    protected long queueLoadedViewEnqueued;
    protected final Set<Long> completedChunkKeys = new LinkedHashSet<>();
    protected final Set<Long> partialChunkKeys = new LinkedHashSet<>();
    protected final Set<Long> failedChunkKeys = new LinkedHashSet<>();
    protected final Map<Long, ChunkSnapshot> liveChunkSnapshots = new LinkedHashMap<>();
    protected final CaptureBlockStore blockStore = new CaptureBlockStore();
    protected WorldScene activeScene;
    protected BlockPos activeOrigin;
    protected boolean roamingCapture;
    protected boolean finishing;
    protected boolean paused;
    protected boolean saving;
    protected long scheduledBlocks;
    protected long processedBlocks;
    protected int adaptiveThrottlePercent = 100;
    protected boolean serverSafetyWarningSent;
    protected Vec3 lastPlayerPos;
    protected int stationaryTicks;
    protected long mapDataRevision;

    protected abstract boolean isCapturing();

    protected abstract boolean multiplayerSafetyActive();

    protected abstract boolean largeSessionDetected();

    protected abstract void expandBounds(BlockPos pos);

    protected abstract int newChunkScheduleLimit();

    protected abstract boolean isClientChunkLoaded(Minecraft client, int chunkX, int chunkZ);

    protected abstract boolean isPlayerStationary();

    private static int unpackChunkX(long key) {
        return (int) key;
    }

    private static int unpackChunkZ(long key) {
        return (int) (key >> 32);
    }

    protected static final class ScanCursor {
        final long key;
        final int chunkX;
        final int chunkZ;
        int x;
        int y;
        int z;
        ChunkSnapshot snapshot;
        LevelChunk chunk;

        ScanCursor(long key, int chunkX, int chunkZ, int minY, ChunkSnapshot snapshot) {
            this(key, chunkX, chunkZ, minY, snapshot, null);
        }

        ScanCursor(long key, int chunkX, int chunkZ, int minY, ChunkSnapshot snapshot, LevelChunk chunk) {
            this.key = key;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.y = minY;
            this.snapshot = snapshot;
            this.chunk = chunk;
        }
    }
    protected void captureChunkImmediately(Minecraft client, int chunkX, int chunkZ, long key) {
        // Hot snapshots are reserved for chunks that are currently very valuable to capture.
        // The amount is capped per tick by processHotVisibleChunks so custom/aggressive values do
        // not turn one client tick into an unbounded full-world scan.
        pendingChunkKeys.remove(key);
        queuedChunkKeys.remove(key);
        removeActiveScan(key);
        partialChunkKeys.add(key);
        blockStore.discard(key);
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(key, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        snapshot.beginBlockScan();
        snapshot.markScanning();
        mapDataRevision++;
        int minY = WorldBinder.config().effectiveCaptureMinY();
        int maxY = WorldBinder.config().effectiveCaptureMaxY();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos((chunkX << 4) + x, y, (chunkZ << 4) + z);
                    captureBlockIntoSnapshot(client, pos, snapshot);
                    expandBounds(pos);
                    processedBlocks++;
                    scheduledBlocks++;
                }
            }
        }
        snapshot.hasBiomeData = true;
        snapshot.lightEstimated = true;
        activeScene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
        partialChunkKeys.remove(key);
        completeChunkAfterBlockScan(key, snapshot, null);
    }

    protected void completeChunkAfterBlockScan(long key, ChunkSnapshot snapshot, LevelChunk sourceChunk) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.savedBlocks <= 0 && snapshot.blockEntityCount <= 0 && !WorldBinder.config().captureAir) {
            blockStore.discard(key);
            snapshot.markPartial();
            completedChunkKeys.remove(key);
            failedChunkKeys.remove(key);
            partialChunkKeys.add(key);
            mapDataRevision++;
            return;
        }
        if (!blockStore.commit(key)) {
            snapshot.markPartial();
            completedChunkKeys.remove(key);
            failedChunkKeys.remove(key);
            partialChunkKeys.add(key);
            mapDataRevision++;
            return;
        }
        completeChunk(key, snapshot);
    }

    protected void completeChunk(long key, ChunkSnapshot snapshot) {
        if (snapshot.exportError) {
            completedChunkKeys.remove(key);
            failedChunkKeys.add(key);
        } else {
            failedChunkKeys.remove(key);
            snapshot.markDone();
            completedChunkKeys.add(key);
        }
        mapDataRevision++;
    }

    protected boolean isDone(long key) {
        if (!completedChunkKeys.contains(key)) {
            return false;
        }
        ChunkSnapshot snapshot = liveChunkSnapshots.get(key);
        return snapshot == null || snapshot.effectiveStatus() == ChunkCaptureStatus.DONE;
    }

    protected void queueBlock(BlockPos pos) {
        long key = pos.asLong();
        if (capturedBlockPositions.add(key)) {
            pendingBlocks.add(pos.immutable());
            scheduledBlocks++;
            expandBounds(pos);
        }
    }

    protected void processPendingBlocks(Minecraft client) {
        int limit = adaptiveBlockLimit(client);
        for (int i = 0; i < limit && !pendingBlocks.isEmpty(); i++) {
            BlockPos pos = pendingBlocks.poll();
            captureBlock(client, pos);
            processedBlocks++;
        }
    }

    protected void processRoamingBlocks(Minecraft client) {
        int limit = adaptiveBlockLimit(client);
        long deadline = System.nanoTime() + adaptiveTickBudgetMillis(client) * 1_000_000L;
        int processedThisTick = 0;
        ensureActiveScanCursors(client);
        while (processedThisTick < limit && System.nanoTime() < deadline) {
            ScanCursor cursor = activeScanCursors.poll();
            if (cursor == null) {
                ensureActiveScanCursors(client);
                cursor = activeScanCursors.poll();
                if (cursor == null) {
                    return;
                }
            }
            if (cursor.chunk == null) {
                cursor.snapshot.markPartial();
                partialChunkKeys.add(cursor.key);
                activeScanChunkKeys.remove(cursor.key);
                queuedChunkKeys.remove(cursor.key);
                continue;
            }
            BlockPos pos = new BlockPos((cursor.chunkX << 4) + cursor.x, cursor.y, (cursor.chunkZ << 4) + cursor.z);
            captureBlockIntoSnapshot(client, cursor.chunk, pos, cursor.snapshot);
            expandBounds(pos);
            processedBlocks++;
            scheduledBlocks++;
            processedThisTick++;
            if (!advanceScanCursor(cursor)) {
                activeScanCursors.add(cursor);
            }
            if (activeScanCursors.isEmpty()) {
                ensureActiveScanCursors(client);
            }
        }
    }

    protected void ensureActiveScanCursors(Minecraft client) {
        int target = activeScanCursorTarget(client);
        while (activeScanCursors.size() < target) {
            ScanCursor cursor = startNextChunk();
            if (cursor == null) {
                return;
            }
            activeScanCursors.add(cursor);
        }
    }

    protected int activeScanCursorTarget(Minecraft client) {
        int configured = WorldBinder.config().effectiveNewChunksPerTick();
        if (configured == Integer.MAX_VALUE || configured < 0) {
            configured = MAX_ACTIVE_SCAN_CURSORS;
        }
        int target = Math.max(1, configured);
        if (!isPlayerStationary()) {
            target = Math.max(1, target / 2);
        } else if (stationaryTicks > 40) {
            target = Math.max(target, Math.min(MAX_ACTIVE_SCAN_CURSORS, target + Math.max(1, WorldBinder.config().effectiveHotChunksPerTick())));
        }
        if (WorldBinder.config().effectiveAdaptiveThrottleEnabled() && adaptiveThrottlePercent < 70) {
            target = Math.max(1, target * Math.max(35, adaptiveThrottlePercent) / 100);
        }
        if (multiplayerSafetyActive()) {
            target = Math.min(target, WorldBinder.config().performancePreset == net.worldbinder.config.WorldBinderConfig.PerformancePreset.EXTREME ? 4 : 3);
        }
        return Math.max(1, Math.min(MAX_ACTIVE_SCAN_CURSORS, target));
    }

    protected ScanCursor startNextChunk() {
        Minecraft client = Minecraft.getInstance();
        Long next = pendingChunkKeys.poll();
        while (next != null) {
            int nextX = unpackChunkX(next);
            int nextZ = unpackChunkZ(next);
            boolean unavailable = roamingCapture && !isClientChunkLoaded(client, nextX, nextZ);
            if (completedChunkKeys.contains(next) || activeScanChunkKeys.contains(next) || failedChunkKeys.contains(next) || unavailable) {
                queuedChunkKeys.remove(next);
                if (unavailable) {
                    queueSkippedUnloaded++;
                    ChunkSnapshot snapshot = liveChunkSnapshots.get(next);
                    if (snapshot != null && !snapshot.isDone()) {
                        snapshot.markPartial();
                    }
                }
                next = pendingChunkKeys.poll();
                continue;
            }
            break;
        }
        if (next == null) {
            return null;
        }
        partialChunkKeys.add(next);
        activeScanChunkKeys.add(next);
        int chunkX = unpackChunkX(next);
        int chunkZ = unpackChunkZ(next);
        LevelChunk chunk = null;
        if (client != null && client.level != null) {
            try {
                chunk = client.level.getChunk(chunkX, chunkZ);
            } catch (Throwable ignored) {
                chunk = null;
            }
        }
        if (chunk == null) {
            queuedChunkKeys.remove(next);
            partialChunkKeys.remove(next);
            activeScanChunkKeys.remove(next);
            queueSkippedUnloaded++;
            return null;
        }
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(next, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        snapshot.beginBlockScan();
        snapshot.markScanning();
        activeScene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
        mapDataRevision++;
        return new ScanCursor(next, chunkX, chunkZ, WorldBinder.config().effectiveCaptureMinY(), snapshot, chunk);
    }

    protected boolean advanceScanCursor(ScanCursor cursor) {
        cursor.y++;
        if (cursor.y <= WorldBinder.config().effectiveCaptureMaxY()) {
            return false;
        }
        cursor.y = WorldBinder.config().effectiveCaptureMinY();
        cursor.z++;
        if (cursor.z < 16) {
            return false;
        }
        cursor.z = 0;
        cursor.x++;
        if (cursor.x < 16) {
            return false;
        }
        partialChunkKeys.remove(cursor.key);
        queuedChunkKeys.remove(cursor.key);
        activeScanChunkKeys.remove(cursor.key);
        completeChunkAfterBlockScan(cursor.key, cursor.snapshot, cursor.chunk);
        activeScene.chunkSnapshots.put(cursor.chunkX + "," + cursor.chunkZ, cursor.snapshot);
        return true;
    }

    protected void clearActiveScans() {
        if (!activeScanCursors.isEmpty()) {
            for (ScanCursor cursor : activeScanCursors) {
                cursor.snapshot.markPartial();
                partialChunkKeys.add(cursor.key);
                if (activeScene != null) {
                    activeScene.chunkSnapshots.put(cursor.chunkX + "," + cursor.chunkZ, cursor.snapshot);
                }
            }
            mapDataRevision++;
        }
        activeScanCursors.clear();
        activeScanChunkKeys.clear();
    }

    protected void removeActiveScan(long key) {
        if (!activeScanChunkKeys.remove(key)) {
            return;
        }
        activeScanCursors.removeIf(cursor -> cursor.key == key);
    }

    protected void captureBlock(Minecraft client, BlockPos pos) {
        BlockState state = client.level.getBlockState(pos);
        boolean savedBlock = WorldBinder.config().captureAir || !state.isAir();
        markChunkBlockScanned(pos, savedBlock, client.level.getBlockEntity(pos) != null);
        if (!savedBlock) {
            return;
        }
        BlockEntity blockEntity = client.level.getBlockEntity(pos);
        String blockEntityNbt = null;
        if (WorldBinder.config().captureBlockEntities && blockEntity != null) {
            try {
                CompoundTag nbt = blockEntity.saveWithFullMetadata(client.level.registryAccess());
                blockEntityNbt = nbt.toString();
            } catch (Throwable throwable) {
                WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.capture.block_entity_serialize_failed", pos), throwable);
            }
        }
        String stateString = BlockStateStrings.toCommandString(state);
        sampleChunkPixel(pos, stateString, state);
        appendBlockRecord(pos, new BlockRecord(
                pos.getX() - activeOrigin.getX(),
                pos.getY() - activeOrigin.getY(),
                pos.getZ() - activeOrigin.getZ(),
                stateString,
                blockEntity != null,
                blockEntityNbt
        ));
    }

    protected void captureBlockIntoSnapshot(Minecraft client, BlockPos pos, ChunkSnapshot snapshot) {
        captureBlockIntoSnapshot(client, null, pos, snapshot);
    }

    protected void captureBlockIntoSnapshot(Minecraft client, LevelChunk chunk, BlockPos pos, ChunkSnapshot snapshot) {
        BlockState state = blockStateAt(client, chunk, pos);
        boolean savedBlock = WorldBinder.config().captureAir || !state.isAir();
        BlockEntity blockEntity = savedBlock && WorldBinder.config().captureBlockEntities ? blockEntityAt(client, chunk, pos) : null;
        snapshot.markScanned(savedBlock, blockEntity != null);
        if (!savedBlock) {
            return;
        }
        String blockEntityNbt = null;
        if (blockEntity != null) {
            try {
                CompoundTag nbt = blockEntity.saveWithFullMetadata(client.level.registryAccess());
                blockEntityNbt = nbt.toString();
            } catch (Throwable throwable) {
                snapshot.markError("BlockEntity NBT failed at " + pos.toShortString());
                failedChunkKeys.add(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
                mapDataRevision++;
                WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.capture.block_entity_serialize_failed", pos), throwable);
            }
        }
        String stateString = BlockStateStrings.toCommandString(state);
        if (!state.isAir()) {
            snapshot.sample(pos.getX() & 15, pos.getZ() & 15, pos.getY(), stateString, blockPreviewColor(stateString));
        }
        appendBlockRecord(pos, new BlockRecord(
                pos.getX() - activeOrigin.getX(),
                pos.getY() - activeOrigin.getY(),
                pos.getZ() - activeOrigin.getZ(),
                stateString,
                blockEntity != null,
                blockEntityNbt
        ));
    }

    protected BlockState blockStateAt(Minecraft client, LevelChunk chunk, BlockPos pos) {
        if (chunk == null) {
            return client.level.getBlockState(pos);
        }
        int sectionIndex = chunk.getSectionIndex(pos.getY());
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return client.level.getBlockState(pos);
        }
        LevelChunkSection section = sections[sectionIndex];
        return section == null ? client.level.getBlockState(pos) : section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    protected BlockEntity blockEntityAt(Minecraft client, LevelChunk chunk, BlockPos pos) {
        if (chunk != null) {
            BlockEntity blockEntity = chunk.getBlockEntity(pos);
            if (blockEntity != null) {
                return blockEntity;
            }
        }
        return client.level.getBlockEntity(pos);
    }

    protected boolean isEmptySection(LevelChunk chunk, int y) {
        if (chunk == null) {
            return false;
        }
        int sectionIndex = chunk.getSectionIndex(y);
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return true;
        }
        LevelChunkSection section = sections[sectionIndex];
        return section == null || section.hasOnlyAir();
    }

    protected void appendBlockRecord(BlockPos pos, BlockRecord record) {
        if (roamingCapture) {
            long chunkKey = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
            blockStore.stage(chunkKey, record);
        } else {
            activeScene.blocks.add(record);
        }
    }

    protected void sampleChunkPixel(BlockPos pos, String stateString, BlockState state) {
        if (state.isAir() || activeScene == null) {
            return;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        long key = ChunkPos.pack(chunkX, chunkZ);
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(key, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        snapshot.sample(pos.getX() & 15, pos.getZ() & 15, pos.getY(), stateString, blockPreviewColor(stateString));
        activeScene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
    }

    protected static int blockPreviewColor(String stateString) {
        String id = stateString;
        int propertyStart = stateString.indexOf('[');
        if (propertyStart >= 0) {
            id = stateString.substring(0, propertyStart);
        }
        if (id.contains("water")) return 0xDD3D77D8;
        if (id.contains("lava")) return 0xDDEE6A24;
        if (id.contains("grass") || id.contains("leaves") || id.contains("moss") || id.contains("vine")) return 0xDD55AA55;
        if (id.contains("sand") || id.contains("end_stone")) return 0xDDD8C97A;
        if (id.contains("snow") || id.contains("white_")) return 0xDDEDEDF2;
        if (id.contains("stone") || id.contains("andesite") || id.contains("tuff") || id.contains("deepslate")) return 0xDD777982;
        if (id.contains("dirt") || id.contains("mud")) return 0xDD7A5638;
        if (id.contains("wood") || id.contains("log") || id.contains("planks")) return 0xDD9A6A3A;
        if (id.contains("copper")) return 0xDD5FAF9A;
        if (id.contains("bricks") || id.contains("brick")) return 0xDD9C4D3F;
        int hash = Math.abs(id.hashCode());
        int r = 72 + (hash & 63);
        int g = 72 + ((hash >> 6) & 63);
        int b = 72 + ((hash >> 12) & 63);
        return 0xDD000000 | (r << 16) | (g << 8) | b;
    }

    protected void markChunkBlockScanned(BlockPos pos, boolean savedBlock, boolean blockEntity) {
        if (activeScene == null) {
            return;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        long key = ChunkPos.pack(chunkX, chunkZ);
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(key, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        snapshot.markScanned(savedBlock, blockEntity);
        activeScene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
    }

    protected int expectedHeight() {
        return Math.max(1, WorldBinder.config().effectiveCaptureHeight());
    }

    protected int adaptiveBlockLimit(Minecraft client) {
        int configured = WorldBinder.config().effectiveBlocksPerTick();
        if (configured == Integer.MAX_VALUE) {
            long aggressive = 16L * 16L * Math.max(1, WorldBinder.config().effectiveCaptureHeight()) * Math.max(1, newChunkScheduleLimit());
            configured = (int) Math.min(Integer.MAX_VALUE, aggressive);
        }
        configured = Math.max(16, configured);
        if (WorldBinder.config().performancePreset == net.worldbinder.config.WorldBinderConfig.PerformancePreset.CUSTOM && WorldBinder.config().blocksPerTick < 0) {
            adaptiveThrottlePercent = 100;
            return configured;
        }
        if (multiplayerSafetyActive()) {
            configured = Math.min(configured, WorldBinder.config().performancePreset == net.worldbinder.config.WorldBinderConfig.PerformancePreset.EXTREME ? 4096 : 2048);
        }
        if (!WorldBinder.config().effectiveAdaptiveThrottleEnabled()) {
            adaptiveThrottlePercent = 100;
            return configured;
        }
        int fps = client == null ? Math.max(1, WorldBinder.config().targetFps) : Math.max(1, client.getFps());
        int target = Math.max(1, WorldBinder.config().targetFps);
        if (fps < target * 0.55D) {
            adaptiveThrottlePercent = 20;
        } else if (fps < target * 0.75D) {
            adaptiveThrottlePercent = 40;
        } else if (fps < target) {
            adaptiveThrottlePercent = 70;
        } else {
            adaptiveThrottlePercent = 100;
        }
        if (multiplayerSafetyActive() && WorldBinder.config().roamingRadiusChunks >= 12
                && !(WorldBinder.config().performancePreset == net.worldbinder.config.WorldBinderConfig.PerformancePreset.CUSTOM && WorldBinder.config().blocksPerTick < 0)) {
            adaptiveThrottlePercent = Math.min(adaptiveThrottlePercent, 55);
        }
        return Math.max(16, configured * adaptiveThrottlePercent / 100);
    }

    protected int adaptiveTickBudgetMillis(Minecraft client) {
        boolean unlimitedCustom = WorldBinder.config().performancePreset == net.worldbinder.config.WorldBinderConfig.PerformancePreset.CUSTOM && WorldBinder.config().tickBudgetMillis < 0;
        int budget = Math.max(1, WorldBinder.config().effectiveTickBudgetMillis());
        if (!unlimitedCustom) {
            budget = Math.min(budget, WorldBinder.config().effectiveMaxCaptureWorkMs());
        }
        if (WorldBinder.config().effectiveAdaptiveThrottleEnabled() && adaptiveThrottlePercent < 100 && !unlimitedCustom) {
            budget = Math.max(1, budget * adaptiveThrottlePercent / 100);
        }
        if (multiplayerSafetyActive() && !unlimitedCustom) {
            budget = Math.min(budget, WorldBinder.config().performancePreset == net.worldbinder.config.WorldBinderConfig.PerformancePreset.EXTREME ? 8 : 4);
        }
        return budget;
    }

}
