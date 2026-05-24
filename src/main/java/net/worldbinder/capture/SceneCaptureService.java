package net.worldbinder.capture;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.worldbinder.WorldBinder;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.scene.BlockRecord;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.scene.ChunkCaptureStatus;
import net.worldbinder.scene.EntityRecord;
import net.worldbinder.scene.SceneLibrary;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.selection.Selection;
import net.worldbinder.selection.SelectionManager;
import net.worldbinder.status.OperationStatus;
import net.worldbinder.status.WorldBinderActivityLog;
import net.worldbinder.storage.StorageFlow;
import net.worldbinder.util.BlockStateStrings;
import net.worldbinder.util.Chat;
import net.worldbinder.util.FileNames;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Collections;

public final class SceneCaptureService {
    private static final int DEFAULT_MAX_NEW_CHUNKS_PER_TICK = 1;
    private static final int DEFAULT_MAX_QUEUED_CHUNKS = 96;
    private static final int STALE_QUEUE_MARGIN_CHUNKS = 2;
    private static final int MAX_ACTIVE_SCAN_CURSORS = 6;
    private static final int MAX_HOT_CHUNKS_PER_TICK = 1;
    private static final int MAX_HOTCACHE_CHUNKS_PER_TICK = 24;
    private static final double STATIONARY_EPSILON_SQUARED = 0.0025D;

    private final SelectionManager selections;
    private final SceneLibrary library;
    private final ArrayDeque<BlockPos> pendingBlocks = new ArrayDeque<>();
    private final ArrayDeque<Long> pendingChunkKeys = new ArrayDeque<>();
    private final ArrayDeque<ScanCursor> activeScanCursors = new ArrayDeque<>();
    private final Set<Long> capturedBlockPositions = new HashSet<>();
    private final Set<Integer> capturedEntityIds = new HashSet<>();
    private final Set<Long> queuedChunkKeys = new HashSet<>();
    private final Set<Long> activeScanChunkKeys = new HashSet<>();
    private final Map<Long, Integer> observedLoadedChunkAges = new LinkedHashMap<>();
    private long queueSkippedUnloaded;
    private long queueSkippedFarAway;
    private long queuePacketEnqueued;
    private long queueLoadedViewEnqueued;
    private final Set<Long> completedChunkKeys = new LinkedHashSet<>();
    private final Set<Long> partialChunkKeys = new LinkedHashSet<>();
    private final Set<Long> failedChunkKeys = new LinkedHashSet<>();
    private final Map<Long, ChunkSnapshot> liveChunkSnapshots = new LinkedHashMap<>();
    private final Map<Long, List<BlockRecord>> hotChunkBlocks = new LinkedHashMap<>();
    private final Map<Long, Map<String, EntityRecord>> hotChunkEntities = new LinkedHashMap<>();
    private int hotBlockRecordCount;
    private int hotEntityRecordCount;
    private long lastMemoryWarningMillis;
    private long lastSnapshotCompactionMillis;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "WorldBinder IO");
        thread.setDaemon(true);
        return thread;
    });

    private WorldScene activeScene;
    private BlockPos activeOrigin;
    private String activeArchiveName;
    private String activeArchiveType;
    private boolean roamingCapture;
    private boolean finishing;
    private boolean paused;
    private boolean saving;
    private long scheduledBlocks;
    private long processedBlocks;
    private long lastRecoverySaveMillis;
    private boolean recoverySaveRunning;
    private int adaptiveThrottlePercent = 100;
    private boolean serverSafetyWarningSent;
    private long finishStartedAtMillis;
    private long finishStartedProcessedBlocks;
    private int finishStartedQueueChunks;
    private Vec3 lastPlayerPos;
    private int stationaryTicks;
    private long mapDataRevision;
    private int entityScanCooldownTicks;
    private BlockPos lastInteractedBlockEntityPos;
    private Entity lastInteractedEntity;

    private static final class ScanCursor {
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

    public SceneCaptureService(SelectionManager selections, SceneLibrary library) {
        this.selections = selections;
        this.library = library;
    }

    public void captureScene(String requestedName) {
        startSelectionCapture(requestedName, "scene");
    }

    public void captureWorldArchive(String requestedName) {
        startSelectionCapture(requestedName, "world");
    }

    public void capture(String requestedName) {
        captureWorldArchive(requestedName);
    }

    public void toggleRoamingCapture(String requestedName) {
        if (isCapturing()) {
            requestFinishCapture();
        } else {
            startRoamingCapture(requestedName);
        }
    }

    public void startRoamingCapture(String requestedName) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            Chat.error("No world loaded.");
            return;
        }
        if (isCapturing()) {
            Chat.warn("A WorldBinder capture is already running.");
            return;
        }

        beginNewScene(requestedName, "world", client.player.blockPosition());
        roamingCapture = true;
        OperationStatus.begin("WorldBinder", "World download running. Move through the world.");
        Chat.info("World download started: §f" + activeArchiveName + "§7 • preset §f" + WorldBinder.config().performancePreset + "§7 • radius §f" + WorldBinder.config().roamingRadiusChunks + "§7 chunks • target FPS §f" + WorldBinder.config().targetFps + "§7.");
        WorldBinderActivityLog.add("Started download: " + activeArchiveName);
        if (multiplayerSafetyActive()) {
            Chat.info("Server Safety Mode active: disconnect autosave and placement throttles are enabled.");
        }
        primeLoadedChunkHotCache(client);
        captureNearbyEntities(client, true);
    }

    public void finishActiveCapture() {
        requestFinishCapture();
    }

    public void requestFinishCapture() {
        if (!isCapturing()) {
            Chat.warn("No WorldBinder capture is running.");
            return;
        }
        if (saving) {
            return;
        }
        if (roamingCapture) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.setScreen(new net.worldbinder.ui.WorldBinderFinishScreen(client.screen, this));
            }
            return;
        }
        if (hasPendingWork()) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.setScreen(new net.worldbinder.ui.WorldBinderFinishScreen(client.screen, this));
            }
            return;
        }
        Minecraft client = Minecraft.getInstance();
        requestSaveNowWithConfirm(client == null ? null : client.screen);
    }

    private void saveWorldDownloadNow() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.level != null && client.player != null) {
            flushLoadedChunksForFinish(client);
            captureNearbyEntities(client, true);
        }
        pendingBlocks.clear();
        pendingChunkKeys.clear();
        queuedChunkKeys.clear();
        clearActiveScans();
        finishing = true;
        paused = false;
        OperationStatus.update("Finalizing current captured data...", 0.98D);
        saveActiveSceneAsync();
    }

    public void finishAfterQueue() {
        if (!isCapturing() || saving) {
            return;
        }
        finishing = true;
        paused = false;
        finishStartedAtMillis = System.currentTimeMillis();
        finishStartedProcessedBlocks = processedBlocks;
        finishStartedQueueChunks = queuedChunkCount();
        OperationStatus.update("Finishing queued chunks before save...", progress());
        Chat.info("WorldBinder will finish the remaining queue before writing the world save.");
    }

    public void abortQueueAndSaveNow() {
        Minecraft client = Minecraft.getInstance();
        requestSaveNowWithConfirm(client == null ? null : client.screen);
    }

    public boolean hasPendingWork() {
        return isCapturing() && (!pendingBlocks.isEmpty() || !pendingChunkKeys.isEmpty() || !activeScanCursors.isEmpty());
    }


    public String targetFolderName() {
        return activeArchiveName == null ? "worldbinder_export" : FileNames.cleanBaseName(activeArchiveName);
    }

    public boolean targetWorldExists() {
        return activeArchiveType != null && !"scene".equals(activeArchiveType)
                && java.nio.file.Files.exists(WorldBinderPaths.MINECRAFT_SAVES.resolve(targetFolderName()));
    }

    public void saveNowConfirmed() {
        stopAndSaveNow();
    }

    public void requestSaveNowWithConfirm(net.minecraft.client.gui.screens.Screen parent) {
        Minecraft client = Minecraft.getInstance();
        if (WorldBinder.config().confirmExistingWorld && targetWorldExists()) {
            client.setScreen(new net.worldbinder.ui.WorldBinderExistingWorldScreen(parent, this));
            return;
        }
        stopAndSaveNow();
    }

    public String finishStatusLine() {
        if (!isCapturing()) {
            return "Idle";
        }
        if (saving) {
            return "Writing vanilla save to disk";
        }
        if (!hasPendingWork()) {
            return "Queue finished. Saving will start now.";
        }
        ScanCursor cursor = activeScanCursors.peek();
        if (cursor != null) {
            return "Scanning " + activeScanCursors.size() + " chunk" + (activeScanCursors.size() == 1 ? "" : "s") + " • lead " + cursor.chunkX + ", " + cursor.chunkZ + " • Y " + cursor.y;
        }
        return "Waiting for next queued chunk";
    }

    public int queuedChunkCount() {
        return pendingChunkKeys.size() + activeScanCursors.size();
    }

    public long processedBlockCount() {
        return processedBlocks;
    }

    public long scheduledBlockCount() {
        return scheduledBlocks;
    }

    public int estimatedFinishSeconds() {
        if (!hasPendingWork()) {
            return 0;
        }
        int blocksPerChunk = 16 * 16 * Math.max(1, WorldBinder.config().effectiveCaptureHeight());
        long remainingBlocks = Math.max(0L, (long) queuedChunkCount() * blocksPerChunk);
        if (finishing && finishStartedAtMillis > 0L) {
            long elapsedMillis = Math.max(1L, System.currentTimeMillis() - finishStartedAtMillis);
            long processedSinceFinish = Math.max(0L, processedBlocks - finishStartedProcessedBlocks);
            double blocksPerSecond = processedSinceFinish <= 0L ? 0.0D : processedSinceFinish * 1000.0D / elapsedMillis;
            if (blocksPerSecond > 1.0D) {
                return (int) Math.min(9999L, Math.max(1L, Math.ceil(remainingBlocks / blocksPerSecond)));
            }
        }
        int perTick = Math.max(1, adaptiveBlockLimit(Minecraft.getInstance()));
        return (int) Math.min(9999L, Math.max(1L, remainingBlocks / Math.max(1L, perTick * 20L)));
    }

    public String estimatedFinishText() {
        if (!hasPendingWork()) {
            return "0s";
        }
        int seconds = estimatedFinishSeconds();
        if (seconds <= 0 || seconds >= 9999) {
            return "calculating";
        }
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return minutes > 0 ? minutes + "m " + rest + "s" : rest + "s";
    }

    public void stopAndSaveNow() {
        if (!isCapturing()) {
            Chat.warn("No WorldBinder capture is running.");
            return;
        }
        if (saving) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.player != null) {
            flushLoadedChunksForFinish(client);
            captureNearbyEntities(client, true);
        }
        pendingBlocks.clear();
        pendingChunkKeys.clear();
        queuedChunkKeys.clear();
        clearActiveScans();
        finishing = true;
        paused = false;
        OperationStatus.update("Saving archive now...", 1.0D);
        saveActiveSceneAsync();
    }

    public void cancelActiveCapture() {
        if (!isCapturing()) {
            return;
        }
        resetActiveJob();
        OperationStatus.finish("Capture cancelled");
        Chat.warn("WorldBinder capture cancelled.");
        WorldBinderActivityLog.add("Capture cancelled");
    }

    public void togglePause() {
        if (!isCapturing() || finishing || saving) {
            return;
        }
        paused = !paused;
        OperationStatus.update(paused ? "WorldBinder paused" : statusLine(), progress());
        Chat.info(paused ? "WorldBinder capture paused." : "WorldBinder capture resumed.");
        WorldBinderActivityLog.add(paused ? "Capture paused" : "Capture resumed");
    }

    public boolean isPaused() {
        return isCapturing() && paused;
    }

    public boolean isCapturing() {
        return activeScene != null;
    }

    public boolean isRoamingCapture() {
        return isCapturing() && roamingCapture;
    }

    public boolean isSaving() {
        return saving || StorageFlow.progress().isRunning();
    }

    public boolean isRecoverySaveRunning() {
        return recoverySaveRunning;
    }

    public String activeArchiveDisplayName() {
        return activeArchiveName == null || activeArchiveName.isBlank() ? WorldBinder.config().defaultArchiveName : activeArchiveName;
    }

    public boolean largeSessionDetected() {
        return totalKnownChunks() >= 50_000 || liveChunkSnapshots.size() >= 50_000;
    }

    public String modeName() {
        if (!isCapturing()) {
            return "Ready";
        }
        if (saving) {
            return "Saving archive";
        }
        if (paused) {
            return roamingCapture ? "World download paused" : "Position archive paused";
        }
        return roamingCapture ? "World download" : "Position archive";
    }

    public int pendingBlocks() {
        return pendingBlocks.size() + pendingChunkKeys.size() * 16 * 16;
    }

    public int capturedBlocks() {
        if (activeScene == null) {
            return 0;
        }
        return activeScene.blockCount() + hotBlockRecordCount;
    }

    public int capturedEntities() {
        if (activeScene == null) {
            return 0;
        }
        return activeScene.entityCount() + hotEntityRecordCount;
    }

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
        return "Observed " + observedLoadedChunkAges.size()
                + " • packet " + queuePacketEnqueued
                + " • loaded-view " + queueLoadedViewEnqueued
                + " • skipped unloaded " + queueSkippedUnloaded
                + " • skipped far " + queueSkippedFarAway;
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

    public boolean multiplayerSafetyActive() {
        Minecraft client = Minecraft.getInstance();
        return WorldBinder.config().serverSafetyMode && client.level != null && !client.isSingleplayer();
    }

    public String safetySummary() {
        if (!multiplayerSafetyActive()) {
            return "Singleplayer/local mode: full local export tools available.";
        }
        int radius = WorldBinder.config().roamingRadiusChunks;
        if (radius >= 12) {
            return "Server Safety: large radius detected. Commands and scanner throughput are capped.";
        }
        return "Server Safety: multiplayer detected. Disconnect autosave and placement throttles are active.";
    }


    public String captureRouteHint() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return "Route optimizer: waiting for player position.";
        }
        int playerChunkX = client.player.blockPosition().getX() >> 4;
        int playerChunkZ = client.player.blockPosition().getZ() >> 4;
        RouteTarget target = nearestRouteTarget(playerChunkX, playerChunkZ);
        if (target == null) {
            if (isCapturing()) {
                return "Route optimizer: nearby capture looks stable. Move toward uncaptured areas to discover more chunks.";
            }
            return "Route optimizer: start capture to receive movement suggestions.";
        }
        int dx = target.chunkX - playerChunkX;
        int dz = target.chunkZ - playerChunkZ;
        String direction = routeDirection(dx, dz);
        return "Route optimizer: " + target.reason + " at " + target.chunkX + ", " + target.chunkZ + " • " + direction + " • " + Math.max(Math.abs(dx), Math.abs(dz)) + " chunks";
    }

    private RouteTarget nearestRouteTarget(int playerChunkX, int playerChunkZ) {
        RouteTarget best = null;
        for (long key : pendingChunkKeys) {
            best = betterRouteTarget(best, key, playerChunkX, playerChunkZ, "queued chunk");
        }
        for (long key : queuedChunkKeys) {
            best = betterRouteTarget(best, key, playerChunkX, playerChunkZ, "queued chunk");
        }
        for (long key : partialChunkKeys) {
            best = betterRouteTarget(best, key, playerChunkX, playerChunkZ, "partial chunk");
        }
        for (long key : failedChunkKeys) {
            best = betterRouteTarget(best, key, playerChunkX, playerChunkZ, "problem chunk");
        }
        return best;
    }

    private RouteTarget betterRouteTarget(RouteTarget current, long key, int playerChunkX, int playerChunkZ, String reason) {
        int chunkX = unpackChunkX(key);
        int chunkZ = unpackChunkZ(key);
        int distance = Math.max(Math.abs(chunkX - playerChunkX), Math.abs(chunkZ - playerChunkZ));
        if (current == null || distance < current.distance) {
            return new RouteTarget(chunkX, chunkZ, distance, reason);
        }
        return current;
    }

    private static int unpackChunkX(long key) {
        return (int) key;
    }

    private static int unpackChunkZ(long key) {
        return (int) (key >> 32);
    }

    private static String routeDirection(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return "you are there";
        }
        String eastWest = dx > 0 ? "east" : dx < 0 ? "west" : "";
        String northSouth = dz > 0 ? "south" : dz < 0 ? "north" : "";
        if (eastWest.isEmpty()) {
            return "go " + northSouth;
        }
        if (northSouth.isEmpty()) {
            return "go " + eastWest;
        }
        return "go " + northSouth + "-" + eastWest;
    }

    private record RouteTarget(int chunkX, int chunkZ, int distance, String reason) {
    }


    private int hotBlockCount() {
        return hotBlockRecordCount;
    }

    private int hotEntityCount() {
        return hotEntityRecordCount;
    }

    public void cacheEntityHot(Entity entity) {
        if (!isCapturing() || activeOrigin == null || entity == null || !WorldBinder.config().captureEntities || captureInputPaused()) {
            return;
        }
        if (!WorldBinder.config().includeEntityPlayers && entity instanceof Player) {
            return;
        }
        EntityRecord record = createEntityRecord(entity);
        int chunkX = ((int) Math.floor(entity.getX())) >> 4;
        int chunkZ = ((int) Math.floor(entity.getZ())) >> 4;
        long key = ChunkPos.pack(chunkX, chunkZ);
        Map<String, EntityRecord> entityMap = hotChunkEntities.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        String cacheKey = entityCacheKey(entity);
        EntityRecord previous = entityMap.get(cacheKey);
        boolean newlyCached = previous == null;
        // Never replace a rich cached entity with a weaker fallback captured during unload/removal.
        if (previous == null || hasUsefulNbt(record) || !hasUsefulNbt(previous)) {
            entityMap.put(cacheKey, record);
        }
        if (newlyCached) {
            hotEntityRecordCount++;
        }
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(key, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        if (newlyCached) {
            snapshot.markEntity();
        }
        activeScene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
    }

    public void onEntityLoaded(Entity entity) {
        cacheEntityHot(entity);
    }

    public void onEntityRemoved(Entity entity) {
        cacheEntityHot(entity);
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

    public void onInteractBlock(BlockHitResult hitResult) {
        if (!isCapturing() || hitResult == null || captureInputPaused()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        BlockPos pos = hitResult.getBlockPos();
        lastInteractedBlockEntityPos = pos.immutable();
        cacheBlockEntityHot(client, pos);
    }

    public void onInteractEntity(Entity entity) {
        if (!isCapturing() || entity == null || captureInputPaused()) {
            return;
        }
        lastInteractedEntity = entity;
        cacheEntityHot(entity);
    }

    public void onMapStateObserved(Object mapIdComponent) {
        if (!isCapturing() || activeScene == null || mapIdComponent == null || !WorldBinder.config().exportMaps || captureInputPaused()) {
            return;
        }
        Integer id = extractMapId(mapIdComponent);
        if (id != null && !activeScene.mapIds.contains(id)) {
            activeScene.mapIds.add(id);
            activeScene.includesMapData = true;
            activeScene.storageNotes.add("Observed map_" + id + ".dat");
        }
    }


    public void onContainerScreenClosed(Screen screen) {
        if (!isCapturing() || captureInputPaused() || screen == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        int storedSlots = inspectVisibleContainerSlots(screen);
        if (lastInteractedBlockEntityPos != null) {
            cacheBlockEntityHot(client, lastInteractedBlockEntityPos);
            if (storedSlots > 0 && activeScene != null) {
                activeScene.storageNotes.add("Captured visible container screen at " + lastInteractedBlockEntityPos.toShortString() + " (" + storedSlots + " slots visible)");
            }
        }
        if (lastInteractedEntity != null) {
            cacheEntityHot(lastInteractedEntity);
            if (storedSlots > 0 && activeScene != null) {
                activeScene.storageNotes.add("Captured visible entity container screen (" + storedSlots + " slots visible)");
            }
        }
    }

    private int inspectVisibleContainerSlots(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return 0;
        }
        try {
            AbstractContainerMenu menu = containerScreen.getMenu();
            int stored = 0;
            for (Slot slot : menu.slots) {
                if (slot != null && slot.hasItem()) {
                    stored++;
                }
            }
            return stored;
        } catch (Throwable throwable) {
            WorldBinder.LOGGER.warn("Failed to inspect closed container screen", throwable);
            return 0;
        }
    }

    public void onStatisticsPacketSeen() {
        if (activeScene != null && WorldBinder.config().exportStats && !captureInputPaused()) {
            activeScene.includesStats = true;
        }
    }

    private boolean captureInputPaused() {
        return paused && !finishing && !saving;
    }

    private Integer extractMapId(Object component) {
        for (String methodName : new String[]{"id", "getId"}) {
            try {
                Object value = component.getClass().getMethod(methodName).invoke(component);
                if (value instanceof Number number) return number.intValue();
            } catch (Throwable ignored) {}
        }
        try {
            java.lang.reflect.Field field = component.getClass().getDeclaredField("id");
            field.setAccessible(true);
            Object value = field.get(component);
            if (value instanceof Number number) return number.intValue();
        } catch (Throwable ignored) {}
        return null;
    }

    private void cacheBlockEntityHot(Minecraft client, BlockPos pos) {
        if (activeScene == null || client.level == null || pos == null) {
            return;
        }
        BlockEntity blockEntity = client.level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4), ignored -> new ChunkSnapshot(pos.getX() >> 4, pos.getZ() >> 4));
        snapshot.markScanned(true, true);
        try {
            CompoundTag nbt = blockEntity.saveWithFullMetadata(client.level.registryAccess());
            String stateString = BlockStateStrings.toCommandString(client.level.getBlockState(pos));
            appendBlockRecord(pos, new BlockRecord(
                    pos.getX() - activeOrigin.getX(),
                    pos.getY() - activeOrigin.getY(),
                    pos.getZ() - activeOrigin.getZ(),
                    stateString,
                    true,
                    nbt.toString()
            ));
            activeScene.storageNotes.add("Container/BlockEntity refreshed at " + pos.toShortString());
        } catch (Throwable throwable) {
            snapshot.markError("Interacted BlockEntity NBT failed at " + pos.toShortString());
            failedChunkKeys.add(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
            mapDataRevision++;
            WorldBinder.LOGGER.warn("Failed to hot-cache interacted block entity at {}", pos, throwable);
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
        Chat.info("Queued chunk §f" + chunkX + ", " + chunkZ + "§7 for rescan.");
        WorldBinderActivityLog.add("Queued rescan for chunk " + chunkX + ", " + chunkZ);
    }

    private void clearCapturedBlocksForChunk(int chunkX, int chunkZ) {
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

    public void tick() {
        if (!isCapturing()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            if (finishing || WorldBinder.config().autoSaveOnDisconnect) {
                pendingBlocks.clear();
                pendingChunkKeys.clear();
                queuedChunkKeys.clear();
                clearActiveScans();
                finishing = true;
                OperationStatus.update("Writing cached WorldBinder export...", 1.0D);
                saveActiveSceneAsync();
            } else {
                cancelActiveCapture();
            }
            return;
        }

        if (saving) {
            OperationStatus.update("Writing archive to disk...", 1.0D);
            return;
        }

        maybeApplyMemoryGuard();

        if (paused && !finishing) {
            OperationStatus.update("Paused: " + statusLine(), progress());
            return;
        }

        if (roamingCapture) {
            updateMovementState(client);
            if (!finishing) {
                maybeWarnServerSafety(client);
                maybeRecoveryAutosave();
                updateObservedLoadedChunks(client);
                cacheObservedLoadedChunks(client);
            }
            if (!pendingChunkKeys.isEmpty() || !activeScanCursors.isEmpty()) {
                processRoamingBlocks(client);
            }
        } else {
            processPendingBlocks(client);
        }

        if (finishing && pendingBlocks.isEmpty() && pendingChunkKeys.isEmpty() && activeScanCursors.isEmpty()) {
            flushLoadedChunksForFinish(client);
            captureNearbyEntities(client, true);
            saveActiveSceneAsync();
        } else {
            OperationStatus.update(statusLine(), progress());
        }
    }

    private void startSelectionCapture(String requestedName, String archiveType) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            Chat.error("No world loaded.");
            return;
        }
        if (isCapturing()) {
            Chat.warn("A WorldBinder capture is already running. Finish or cancel it first.");
            return;
        }
        if (!selections.hasCompleteSelection()) {
            Chat.warn("Set both positions first or use World Download mode.");
            return;
        }

        Selection selection = selections.getSelection();
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        beginNewScene(requestedName, archiveType, min);
        activeScene.sizeX = max.getX() - min.getX() + 1;
        activeScene.sizeY = max.getY() - min.getY() + 1;
        activeScene.sizeZ = max.getZ() - min.getZ() + 1;

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    queueBlock(new BlockPos(x, y, z));
                }
            }
        }
        finishing = true;
        OperationStatus.begin("WorldBinder", "Capturing selected area...");
        Chat.info("Position archive queued with §f" + scheduledBlocks + "§7 blocks. It will be processed over multiple ticks.");
    }

    private void beginNewScene(String requestedName, String archiveType, BlockPos origin) {
        Minecraft client = Minecraft.getInstance();
        activeArchiveName = requestedName == null || requestedName.isBlank() ? WorldBinder.config().defaultArchiveName : requestedName.trim();
        activeArchiveType = archiveType;
        activeOrigin = origin.immutable();
        activeScene = new WorldScene();
        activeScene.name = activeArchiveName;
        activeScene.archiveType = archiveType;
        activeScene.dimension = client.level == null ? "unknown" : client.level.dimension().toString();
        activeScene.includesBlockEntityNbt = WorldBinder.config().captureBlockEntities;
        activeScene.includesEntityNbt = WorldBinder.config().captureEntities;
        activeScene.includesMapData = WorldBinder.config().exportMaps;
        activeScene.includesAdvancements = WorldBinder.config().exportAdvancements;
        activeScene.includesStats = WorldBinder.config().exportStats;
        activeScene.targetMinecraftVersion = net.worldbinder.version.TargetMinecraftVersion.normalize(WorldBinder.config().targetMinecraftVersion);
        activeScene.targetGenerationProfile = net.worldbinder.version.TargetMinecraftVersion.profileLabel(activeScene.targetMinecraftVersion);
        activeScene.gameRulesNbt = readGameRulesNbt(client);
        activeScene.originX = activeOrigin.getX();
        activeScene.originY = activeOrigin.getY();
        activeScene.originZ = activeOrigin.getZ();
        activeScene.sizeX = 0;
        activeScene.sizeY = 0;
        activeScene.sizeZ = 0;
        pendingBlocks.clear();
        pendingChunkKeys.clear();
        capturedBlockPositions.clear();
        capturedEntityIds.clear();
        queuedChunkKeys.clear();
        completedChunkKeys.clear();
        partialChunkKeys.clear();
        liveChunkSnapshots.clear();
        hotChunkBlocks.clear();
        hotChunkEntities.clear();
        failedChunkKeys.clear();
        observedLoadedChunkAges.clear();
        queueSkippedUnloaded = 0L;
        queueSkippedFarAway = 0L;
        queuePacketEnqueued = 0L;
        queueLoadedViewEnqueued = 0L;
        lastRecoverySaveMillis = 0L;
        recoverySaveRunning = false;
        hotBlockRecordCount = 0;
        hotEntityRecordCount = 0;
        lastMemoryWarningMillis = 0L;
        adaptiveThrottlePercent = 100;
        serverSafetyWarningSent = false;
        lastPlayerPos = null;
        stationaryTicks = 0;
        mapDataRevision = 0L;
        entityScanCooldownTicks = 0;
        lastInteractedBlockEntityPos = null;
        lastInteractedEntity = null;
        scheduledBlocks = 0L;
        processedBlocks = 0L;
        roamingCapture = false;
        finishing = false;
        paused = false;
        saving = false;
        clearActiveScans();
    }


    private String readGameRulesNbt(Minecraft client) {
        if (!WorldBinder.config().exportGameRules) {
            return null;
        }
        String override = WorldBinder.config().gameRulesOverride;
        if (override == null || override.isBlank()) {
            return null;
        }
        CompoundTag rules = new CompoundTag();
        for (String entry : override.split("[;\n]")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int sep = trimmed.indexOf('=');
            if (sep <= 0 || sep >= trimmed.length() - 1) {
                sep = trimmed.indexOf(':');
            }
            if (sep <= 0 || sep >= trimmed.length() - 1) {
                continue;
            }
            rules.putString(trimmed.substring(0, sep).trim(), trimmed.substring(sep + 1).trim());
        }
        return rules.isEmpty() ? null : rules.toString();
    }

    private void updateObservedLoadedChunks(Minecraft client) {
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


    private void primeLoadedChunkHotCache(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            return;
        }
        updateObservedLoadedChunks(client);
        cacheObservedLoadedChunks(client);
    }

    private void flushLoadedChunksForFinish(Minecraft client) {
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

    private void cacheObservedLoadedChunks(Minecraft client) {
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

    private int hotCacheChunkLimit() {
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

    private boolean captureLoadedChunkHot(Minecraft client, int chunkX, int chunkZ, long key, String reason, boolean replaceDone) {
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
        removeHotChunk(key);
        partialChunkKeys.add(key);
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(key, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        snapshot.markQueued(reason, "hot-cache");
        snapshot.markScanning();
        activeScene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
        mapDataRevision++;

        int minY = WorldBinder.config().effectiveCaptureMinY();
        int maxY = WorldBinder.config().effectiveCaptureMaxY();
        if (chunk.isEmpty()) {
            int skippedBlocks = 16 * 16 * Math.max(1, maxY - minY + 1);
            snapshot.markScanned(skippedBlocks, 0, 0);
            processedBlocks += skippedBlocks;
            scheduledBlocks += skippedBlocks;
            partialChunkKeys.remove(key);
            completeChunk(key, snapshot);
            return true;
        }

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
        completeChunk(key, snapshot);
        activeScene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
        return true;
    }

    private void scheduleLoadedObservedChunks(Minecraft client) {
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

    private boolean enqueueChunk(long key, boolean priority) {
        return enqueueChunk(key, priority, "legacy", "unknown");
    }

    private boolean enqueueChunk(long key, boolean priority, String reason, String source) {
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

    private void trimStaleQueuedChunks(Minecraft client, int centerChunkX, int centerChunkZ, int keepRadius) {
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

    private void reprioritizeVisibleQueue(int centerChunkX, int centerChunkZ) {
        if (pendingChunkKeys.size() < 2) {
            return;
        }
        List<Long> sorted = new ArrayList<>(pendingChunkKeys);
        sorted.sort((a, b) -> Integer.compare(distanceSq(a, centerChunkX, centerChunkZ), distanceSq(b, centerChunkX, centerChunkZ)));
        pendingChunkKeys.clear();
        pendingChunkKeys.addAll(sorted);
    }

    private int distanceSq(long key, int centerChunkX, int centerChunkZ) {
        int dx = unpackChunkX(key) - centerChunkX;
        int dz = unpackChunkZ(key) - centerChunkZ;
        return dx * dx + dz * dz;
    }

    private boolean isInsideActiveRoamingRadius(Minecraft client, long key, int extraRadius) {
        if (client == null || client.player == null) {
            return false;
        }
        int centerChunkX = client.player.blockPosition().getX() >> 4;
        int centerChunkZ = client.player.blockPosition().getZ() >> 4;
        int radius = Math.max(1, WorldBinder.config().effectiveRoamingRadiusChunks()) + Math.max(0, extraRadius);
        return Math.max(Math.abs(unpackChunkX(key) - centerChunkX), Math.abs(unpackChunkZ(key) - centerChunkZ)) <= radius;
    }

    private boolean isClientChunkLoaded(Minecraft client, int chunkX, int chunkZ) {
        if (client == null || client.level == null) {
            return false;
        }
        try {
            return client.level.hasChunk(chunkX, chunkZ);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int newChunkScheduleLimit() {
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

    private void updateMovementState(Minecraft client) {
        Vec3 current = client.player.position();
        if (lastPlayerPos != null && current.distanceToSqr(lastPlayerPos) <= STATIONARY_EPSILON_SQUARED) {
            stationaryTicks = Math.min(200, stationaryTicks + 1);
        } else {
            stationaryTicks = 0;
        }
        lastPlayerPos = current;
    }

    private boolean isPlayerStationary() {
        return stationaryTicks >= 10;
    }

    private void processHotVisibleChunks(Minecraft client) {
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

    private void captureChunkImmediately(Minecraft client, int chunkX, int chunkZ, long key) {
        // Hot snapshots are reserved for chunks that are currently very valuable to capture.
        // The amount is capped per tick by processHotVisibleChunks so custom/aggressive values do
        // not turn one client tick into an unbounded full-world scan.
        pendingChunkKeys.remove(key);
        queuedChunkKeys.remove(key);
        removeActiveScan(key);
        partialChunkKeys.add(key);
        removeHotChunk(key);
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(key, ignored -> new ChunkSnapshot(chunkX, chunkZ));
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
        completeChunk(key, snapshot);
    }

    private void completeChunk(long key, ChunkSnapshot snapshot) {
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

    private boolean isDone(long key) {
        if (!completedChunkKeys.contains(key)) {
            return false;
        }
        ChunkSnapshot snapshot = liveChunkSnapshots.get(key);
        return snapshot == null || snapshot.effectiveStatus() == ChunkCaptureStatus.DONE;
    }

    private void queueBlock(BlockPos pos) {
        long key = pos.asLong();
        if (capturedBlockPositions.add(key)) {
            pendingBlocks.add(pos.immutable());
            scheduledBlocks++;
            expandBounds(pos);
        }
    }

    private void processPendingBlocks(Minecraft client) {
        int limit = adaptiveBlockLimit(client);
        for (int i = 0; i < limit && !pendingBlocks.isEmpty(); i++) {
            BlockPos pos = pendingBlocks.poll();
            captureBlock(client, pos);
            processedBlocks++;
        }
    }

    private void processRoamingBlocks(Minecraft client) {
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
            BlockPos pos = new BlockPos((cursor.chunkX << 4) + cursor.x, cursor.y, (cursor.chunkZ << 4) + cursor.z);
            captureBlockIntoSnapshot(client, pos, cursor.snapshot);
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

    private void ensureActiveScanCursors(Minecraft client) {
        int target = activeScanCursorTarget(client);
        while (activeScanCursors.size() < target) {
            ScanCursor cursor = startNextChunk();
            if (cursor == null) {
                return;
            }
            activeScanCursors.add(cursor);
        }
    }

    private int activeScanCursorTarget(Minecraft client) {
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

    private ScanCursor startNextChunk() {
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
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(next, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        snapshot.markScanning();
        activeScene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
        mapDataRevision++;
        return new ScanCursor(next, chunkX, chunkZ, WorldBinder.config().effectiveCaptureMinY(), snapshot);
    }

    private boolean advanceScanCursor(ScanCursor cursor) {
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
        completeChunk(cursor.key, cursor.snapshot);
        activeScene.chunkSnapshots.put(cursor.chunkX + "," + cursor.chunkZ, cursor.snapshot);
        return true;
    }

    private void clearActiveScans() {
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

    private void removeActiveScan(long key) {
        if (!activeScanChunkKeys.remove(key)) {
            return;
        }
        activeScanCursors.removeIf(cursor -> cursor.key == key);
    }

    private void captureBlock(Minecraft client, BlockPos pos) {
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
                WorldBinder.LOGGER.warn("Failed to serialize block entity at {}", pos, throwable);
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


    private void captureBlockIntoSnapshot(Minecraft client, BlockPos pos, ChunkSnapshot snapshot) {
        captureBlockIntoSnapshot(client, null, pos, snapshot);
    }

    private void captureBlockIntoSnapshot(Minecraft client, LevelChunk chunk, BlockPos pos, ChunkSnapshot snapshot) {
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
                WorldBinder.LOGGER.warn("Failed to serialize block entity at {}", pos, throwable);
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

    private BlockState blockStateAt(Minecraft client, LevelChunk chunk, BlockPos pos) {
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

    private BlockEntity blockEntityAt(Minecraft client, LevelChunk chunk, BlockPos pos) {
        if (chunk != null) {
            BlockEntity blockEntity = chunk.getBlockEntity(pos);
            if (blockEntity != null) {
                return blockEntity;
            }
        }
        return client.level.getBlockEntity(pos);
    }

    private boolean isEmptySection(LevelChunk chunk, int y) {
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


    private void appendBlockRecord(BlockPos pos, BlockRecord record) {
        if (roamingCapture) {
            long chunkKey = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
            hotChunkBlocks.computeIfAbsent(chunkKey, ignored -> new ArrayList<>()).add(record);
            hotBlockRecordCount++;
        } else {
            activeScene.blocks.add(record);
        }
    }

    private void sampleChunkPixel(BlockPos pos, String stateString, BlockState state) {
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

    private static int blockPreviewColor(String stateString) {
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

    private void markChunkBlockScanned(BlockPos pos, boolean savedBlock, boolean blockEntity) {
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


    private int expectedHeight() {
        return Math.max(1, WorldBinder.config().effectiveCaptureHeight());
    }

    private int adaptiveBlockLimit(Minecraft client) {
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

    private int adaptiveTickBudgetMillis(Minecraft client) {
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

    private void maybeWarnServerSafety(Minecraft client) {
        if (serverSafetyWarningSent || !WorldBinder.config().serverSafetyMode || client.isSingleplayer()) {
            return;
        }
        serverSafetyWarningSent = true;
        if (WorldBinder.config().roamingRadiusChunks >= 12) {
            Chat.warn("Server Safety Mode: large radius on multiplayer. Scanner throttling and disconnect autosave are active.");
        } else {
            Chat.info("Server Safety Mode active: disconnect autosave and placement throttles are enabled.");
        }
    }

    private void maybeRecoveryAutosave() {
        if (!WorldBinder.config().crashRecovery || activeScene == null || recoverySaveRunning) {
            return;
        }
        long now = System.currentTimeMillis();
        long interval = Math.max(1, WorldBinder.config().recoveryAutosaveSeconds) * 1000L;
        if (lastRecoverySaveMillis != 0L && now - lastRecoverySaveMillis < interval) {
            return;
        }
        lastRecoverySaveMillis = now;
        recoverySaveRunning = true;
        WorldScene scene = copySceneForIo(activeScene, true);
        String name = activeArchiveName == null ? "recovery" : activeArchiveName;
        Path target = WorldBinderPaths.RECOVERY_ROOT.resolve("_recovery_" + FileNames.cleanBaseName(name));
        ioExecutor.execute(() -> {
            try {
                deleteOldRecovery(target);
                library.saveRecoverySnapshot(scene, target);
            } catch (IOException exception) {
                library.markRecoveryFailed(target, exception.getMessage());
                WorldBinder.LOGGER.warn("Failed to write recovery autosave", exception);
            } finally {
                recoverySaveRunning = false;
            }
        });
    }


    private void deleteOldRecovery(Path keepTarget) {
        if (!WorldBinder.config().autoDeleteRecovery) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(WorldBinderPaths.RECOVERY_ROOT);
            try (java.util.stream.Stream<Path> stream = java.nio.file.Files.list(WorldBinderPaths.RECOVERY_ROOT)) {
                for (Path path : stream.filter(java.nio.file.Files::isDirectory).filter(p -> p.getFileName().toString().startsWith("_recovery_")).toList()) {
                    if (!path.equals(keepTarget)) {
                        deleteRecursive(path);
                    }
                }
            }
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn("Failed to clean recovery folders", exception);
        }
    }

    private void deleteRecursive(Path root) throws IOException {
        if (!java.nio.file.Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(root)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                java.nio.file.Files.deleteIfExists(path);
            }
        }
    }

    private void captureNearbyEntities(Minecraft client, boolean wide) {
        if (!WorldBinder.config().captureEntities || activeScene == null || (!wide && captureInputPaused())) {
            return;
        }
        if (!wide) {
            if (entityScanCooldownTicks > 0) {
                entityScanCooldownTicks--;
                return;
            }
            entityScanCooldownTicks = WorldBinder.config().performancePreset == net.worldbinder.config.WorldBinderConfig.PerformancePreset.EXTREME ? 4 : 10;
        }
        int radiusBlocks = Math.max(16, WorldBinder.config().effectiveRoamingRadiusChunks() * 16);
        if (wide && activeScene.sizeX > 0 && activeScene.sizeZ > 0) {
            radiusBlocks = Math.max(radiusBlocks, Math.max(activeScene.sizeX, activeScene.sizeZ) + 32);
        }
        Vec3 center = new Vec3(client.player.getX(), client.player.getY(), client.player.getZ());
        List<Entity> entities = client.level.getEntitiesOfClass(Entity.class,
                new net.minecraft.world.phys.AABB(
                        center.x - radiusBlocks, WorldBinder.config().effectiveCaptureMinY(), center.z - radiusBlocks,
                        center.x + radiusBlocks, WorldBinder.config().effectiveCaptureMaxY(), center.z + radiusBlocks
                ),
                entity -> isEntityExportCandidate(entity) && (WorldBinder.config().includeEntityPlayers || !(entity instanceof Player))
        );
        for (Entity entity : entities) {
            if (capturedEntityIds.add(entity.getId()) || roamingCapture) {
                if (roamingCapture) {
                    cacheEntityHot(entity);
                } else {
                    captureEntity(entity);
                }
            }
        }
    }


    private boolean isEntityExportCandidate(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.isAlive()) {
            return true;
        }
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return type.equals("minecraft:block_display")
                || type.equals("minecraft:item_display")
                || type.equals("minecraft:text_display")
                || type.equals("minecraft:interaction")
                || type.equals("minecraft:armor_stand")
                || type.endsWith("display");
    }

    private void captureEntity(Entity entity) {
        EntityRecord record = createEntityRecord(entity);
        activeScene.entities.add(record);
        int chunkX = ((int) Math.floor(entity.getX())) >> 4;
        int chunkZ = ((int) Math.floor(entity.getZ())) >> 4;
        long key = ChunkPos.pack(chunkX, chunkZ);
        ChunkSnapshot snapshot = liveChunkSnapshots.computeIfAbsent(key, ignored -> new ChunkSnapshot(chunkX, chunkZ));
        snapshot.markEntity();
        activeScene.chunkSnapshots.put(chunkX + "," + chunkZ, snapshot);
    }

    private EntityRecord createEntityRecord(Entity entity) {
        Vec3 pos = new Vec3(entity.getX(), entity.getY(), entity.getZ());
        EntityRecord record = new EntityRecord();
        record.uuid = entity.getUUID().toString();
        record.runtimeId = entity.getId();
        record.type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        record.x = pos.x - activeOrigin.getX();
        record.y = pos.y - activeOrigin.getY();
        record.z = pos.z - activeOrigin.getZ();
        record.yaw = entity.getYRot();
        record.pitch = entity.getXRot();
        record.customName = entity.hasCustomName() && entity.getCustomName() != null ? entity.getCustomName().getString() : null;
        record.glowing = false;
        record.invisible = entity.isInvisible();
        record.noGravity = entity.isNoGravity();
        record.fullNbt = serializeEntity(entity);
        return record;
    }

    private String serializeEntity(Entity entity) {
        try {
            TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
            output.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            // saveWithoutId() keeps live entity data while WorldBinder supplies the registry id above.
            entity.saveWithoutId(output);
            return output.buildResult().toString();
        } catch (Throwable throwable) {
            WorldBinder.LOGGER.warn("Failed to serialize entity {}", entity.getType(), throwable);
        }
        return null;
    }

    private static String entityCacheKey(Entity entity) {
        String uuid = entity.getUUID().toString();
        if (uuid != null && !uuid.isBlank()) {
            return uuid;
        }
        return "runtime:" + entity.getId();
    }

    private static boolean hasUsefulNbt(EntityRecord record) {
        return record != null && record.fullNbt != null && !record.fullNbt.isBlank() && record.fullNbt.contains("Pos");
    }

    private void expandBounds(BlockPos pos) {
        if (activeScene == null || activeOrigin == null) {
            return;
        }
        int relX = pos.getX() - activeOrigin.getX();
        int relY = pos.getY() - activeOrigin.getY();
        int relZ = pos.getZ() - activeOrigin.getZ();
        activeScene.sizeX = Math.max(activeScene.sizeX, Math.abs(relX) + 1);
        activeScene.sizeY = Math.max(activeScene.sizeY, Math.abs(relY) + 1);
        activeScene.sizeZ = Math.max(activeScene.sizeZ, Math.abs(relZ) + 1);
    }

    private double progress() {
        if (roamingCapture) {
            int total = completedChunkKeys.size() + pendingChunkKeys.size() + activeScanCursors.size();
            if (total <= 0) {
                return 0.0D;
            }
            return Math.max(0.03D, Math.min(1.0D, completedChunkKeys.size() / (double) total));
        }
        if (scheduledBlocks <= 0L) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, processedBlocks / (double) scheduledBlocks));
    }

    private String statusLine() {
        if (roamingCapture) {
            if (finishing) {
                return "Finishing queue: " + queuedChunkCount() + " chunks left";
            }
            String pause = paused ? "Paused: " : "";
            return pause + "Downloading World";
        }
        return "Capturing: " + processedBlocks + " / " + scheduledBlocks + " blocks";
    }

    private WorldScene copySceneForIo(WorldScene source, boolean completedOnly) {
        WorldScene copy = new WorldScene();
        copy.formatVersion = source.formatVersion;
        copy.archiveType = source.archiveType;
        copy.name = source.name;
        copy.createdAt = source.createdAt;
        copy.minecraftVersion = source.minecraftVersion;
        copy.targetMinecraftVersion = source.targetMinecraftVersion == null || source.targetMinecraftVersion.isBlank()
                ? net.worldbinder.version.TargetMinecraftVersion.normalize(WorldBinder.config().targetMinecraftVersion)
                : net.worldbinder.version.TargetMinecraftVersion.normalize(source.targetMinecraftVersion);
        copy.targetGenerationProfile = net.worldbinder.version.TargetMinecraftVersion.profileLabel(copy.targetMinecraftVersion);
        copy.dimension = source.dimension;
        copy.originX = source.originX;
        copy.originY = source.originY;
        copy.originZ = source.originZ;
        copy.sizeX = source.sizeX;
        copy.sizeY = source.sizeY;
        copy.sizeZ = source.sizeZ;
        copy.includesBlockEntityNbt = source.includesBlockEntityNbt;
        copy.includesEntityNbt = source.includesEntityNbt;
        copy.gameRulesNbt = source.gameRulesNbt;
        copy.includesMapData = source.includesMapData;
        copy.includesAdvancements = source.includesAdvancements;
        copy.includesStats = source.includesStats;
        copy.compressedZip = source.compressedZip;
        copy.mapIds = source.mapIds == null ? new ArrayList<>() : new ArrayList<>(source.mapIds);
        copy.storageNotes = source.storageNotes == null ? new ArrayList<>() : new ArrayList<>(source.storageNotes);
        copy.blocks = new ArrayList<>();
        if (source.blocks != null) {
            for (BlockRecord block : source.blocks) {
                if (block != null && (!completedOnly || exportableForRecovery(blockChunkKey(source, block)))) {
                    copy.blocks.add(block);
                }
            }
        }
        for (Map.Entry<Long, List<BlockRecord>> entry : hotChunkBlocks.entrySet()) {
            if (!completedOnly || exportableForRecovery(entry.getKey())) {
                copy.blocks.addAll(entry.getValue());
            }
        }
        copy.entities = new ArrayList<>();
        if (source.entities != null) {
            for (EntityRecord entity : source.entities) {
                if (entity != null && (!completedOnly || exportableForRecovery(entityChunkKey(source, entity)))) {
                    copy.entities.add(entity);
                }
            }
        }
        for (Map.Entry<Long, Map<String, EntityRecord>> entry : hotChunkEntities.entrySet()) {
            if (!completedOnly || exportableForRecovery(entry.getKey())) {
                copy.entities.addAll(entry.getValue().values());
            }
        }
        copy.chunkSnapshots = new LinkedHashMap<>();
        if (source.chunkSnapshots != null) {
            for (Map.Entry<String, ChunkSnapshot> entry : source.chunkSnapshots.entrySet()) {
                ChunkSnapshot snapshot = entry.getValue();
                long key = snapshot == null ? Long.MIN_VALUE : ChunkPos.pack(snapshot.chunkX, snapshot.chunkZ);
                if (!completedOnly || exportableForRecovery(key)) {
                    copy.chunkSnapshots.put(entry.getKey(), snapshot);
                }
            }
        }
        return copy;
    }

    private boolean exportableForRecovery(long key) {
        if (completedChunkKeys.contains(key)) {
            return true;
        }
        ChunkSnapshot snapshot = liveChunkSnapshots.get(key);
        if (snapshot == null) {
            return false;
        }
        ChunkCaptureStatus status = snapshot.effectiveStatus();
        return status == ChunkCaptureStatus.PARTIAL || status == ChunkCaptureStatus.RECOVERY;
    }

    private long blockChunkKey(WorldScene scene, BlockRecord block) {
        int worldX = scene.originX + block.x;
        int worldZ = scene.originZ + block.z;
        return ChunkPos.pack(worldX >> 4, worldZ >> 4);
    }

    private long entityChunkKey(WorldScene scene, EntityRecord entity) {
        int worldX = scene.originX + (int) Math.floor(entity.x);
        int worldZ = scene.originZ + (int) Math.floor(entity.z);
        return ChunkPos.pack(worldX >> 4, worldZ >> 4);
    }


    public void continueRecovery(Path recoveryFolder) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            Chat.error("Join a world before continuing a recovery.");
            return;
        }
        if (isCapturing()) {
            Chat.warn("Finish or cancel the active WorldBinder capture before continuing a recovery.");
            return;
        }
        if (!library.canFinalizeRecovery(recoveryFolder)) {
            Chat.warn("Selected archive is not a valid completed recovery folder.");
            return;
        }
        OperationStatus.begin("WorldBinder Recovery", "Loading recovery asynchronously...");
        Chat.info("Loading recovery in the background. Large sessions will not freeze F9.");
        ioExecutor.execute(() -> {
            try {
                WorldScene scene = library.read(recoveryFolder);
                Minecraft.getInstance().execute(() -> applyRecoveryScene(recoveryFolder, scene));
            } catch (Exception exception) {
                Minecraft.getInstance().execute(() -> {
                    OperationStatus.finish("Recovery load failed");
                    Chat.error("Failed to continue recovery. Check the log.");
                });
                WorldBinder.LOGGER.warn("Failed to continue recovery", exception);
            }
        });
    }

    private void applyRecoveryScene(Path recoveryFolder, WorldScene scene) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            OperationStatus.finish("Recovery load cancelled");
            Chat.error("Join a world before continuing a recovery.");
            return;
        }
        if (isCapturing()) {
            OperationStatus.finish("Recovery load cancelled");
            Chat.warn("Finish or cancel the active WorldBinder capture before continuing a recovery.");
            return;
        }
        if (scene == null) {
            OperationStatus.finish("Recovery load failed");
            Chat.error("Recovery file could not be read.");
            return;
        }

        pendingBlocks.clear();
        pendingChunkKeys.clear();
        capturedBlockPositions.clear();
        capturedEntityIds.clear();
        queuedChunkKeys.clear();
        completedChunkKeys.clear();
        partialChunkKeys.clear();
        failedChunkKeys.clear();
        observedLoadedChunkAges.clear();
        queueSkippedUnloaded = 0L;
        queueSkippedFarAway = 0L;
        queuePacketEnqueued = 0L;
        queueLoadedViewEnqueued = 0L;
        activeScanChunkKeys.clear();
        liveChunkSnapshots.clear();
        hotChunkBlocks.clear();
        hotChunkEntities.clear();
        hotBlockRecordCount = 0;
        hotEntityRecordCount = 0;
        clearActiveScans();

        activeScene = scene;
        activeArchiveName = scene.name == null || scene.name.isBlank()
                ? recoveryFolder.getFileName().toString().replaceFirst("^_recovery_", "")
                : scene.name;
        activeArchiveType = scene.archiveType == null || scene.archiveType.isBlank() ? "world" : scene.archiveType;
        activeOrigin = new BlockPos(scene.originX, scene.originY, scene.originZ);
        roamingCapture = true;
        finishing = false;
        paused = false;
        saving = false;
        scheduledBlocks = scene.blockCount();
        processedBlocks = scene.blockCount();
        lastRecoverySaveMillis = 0L;
        recoverySaveRunning = false;
        entityScanCooldownTicks = 0;

        if (scene.chunkSnapshots != null) {
            for (ChunkSnapshot snapshot : scene.chunkSnapshots.values()) {
                if (snapshot == null) continue;
                snapshot.dropDebugStateData();
                long key = ChunkPos.pack(snapshot.chunkX, snapshot.chunkZ);
                liveChunkSnapshots.put(key, snapshot);
                ChunkCaptureStatus status = snapshot.effectiveStatus();
                if (status == ChunkCaptureStatus.DONE) {
                    completedChunkKeys.add(key);
                } else if (status == ChunkCaptureStatus.FAILED) {
                    failedChunkKeys.add(key);
                } else {
                    snapshot.markRecovery();
                    partialChunkKeys.add(key);
                    if (queuedChunkKeys.add(key)) {
                        pendingChunkKeys.add(key);
                    }
                }
            }
        }
        mapDataRevision++;
        OperationStatus.begin("WorldBinder Recovery", "Recovery loaded. Continue moving or finalize it from F9 > Archives.");
        Chat.warn("Recovery loaded: §f" + activeArchiveName + "§7. Continue scanning or finalize it from §fF9 > Archives§7.");
        if (largeSessionDetected()) {
            Chat.warn("Large session detected. UI detail may be reduced while recovery continues.");
        }
        WorldBinderActivityLog.add("Recovery continued: " + activeArchiveName);
    }

    private void saveActiveSceneAsync() {
        if (saving || activeScene == null) {
            return;
        }
        saving = true;
        WorldScene scene = copySceneForIo(activeScene, false);
        String name = activeArchiveName;
        String type = activeArchiveType;
        resetActiveJob();
        saving = true;
        OperationStatus.begin("WorldBinder Storage", "Writing archive to disk...");
        Path target = "scene".equals(type) ? WorldBinderPaths.SCENES.resolve(FileNames.archiveFileName(name)) : WorldBinderPaths.newWorldFolder(name);
        StorageFlow.submit(library, scene, target, "scene".equals(type), savedPath -> {
            saving = false;
            String validation = library.validationLine(savedPath);
            OperationStatus.finish("Saved " + scene.blockCount() + " blocks, " + scene.entityCount() + " entities • " + validation);
            Chat.savedArchive(scene.archiveType, scene.name, scene.blockCount(), scene.blockEntityCount(), scene.entityCount(), savedPath);
            if (!"No validation report".equals(validation)) {
                Chat.info("Export validation: §f" + validation + "§7. Open the save folder for the full report.");
                WorldBinderActivityLog.add("Validation passed: " + validation);
            }
            WorldBinderActivityLog.add("Saved archive: " + scene.name);
        }, throwable -> {
            saving = false;
            OperationStatus.finish("Save failed");
            Chat.error("Failed to save archive. Check the log.");
        });
    }

    private void resetActiveJob() {
        activeScene = null;
        activeOrigin = null;
        activeArchiveName = null;
        activeArchiveType = null;
        roamingCapture = false;
        finishing = false;
        paused = false;
        saving = false;
        scheduledBlocks = 0L;
        processedBlocks = 0L;
        entityScanCooldownTicks = 0;
        finishStartedAtMillis = 0L;
        finishStartedProcessedBlocks = 0L;
        finishStartedQueueChunks = 0;
        clearActiveScans();
        pendingBlocks.clear();
        pendingChunkKeys.clear();
        capturedBlockPositions.clear();
        capturedEntityIds.clear();
        queuedChunkKeys.clear();
        completedChunkKeys.clear();
        partialChunkKeys.clear();
        failedChunkKeys.clear();
        hotChunkBlocks.clear();
        hotChunkEntities.clear();
        hotBlockRecordCount = 0;
        hotEntityRecordCount = 0;
        lastMemoryWarningMillis = 0L;
        lastPlayerPos = null;
        stationaryTicks = 0;
        mapDataRevision++;
    }

    private void removeHotChunk(long key) {
        // Only replace block records for a rescanned chunk. Entity records are updated through the entity hot-cache
        // path and should not be dropped just because the block scanner revisited the same chunk.
        List<BlockRecord> oldBlocks = hotChunkBlocks.remove(key);
        if (oldBlocks != null) {
            hotBlockRecordCount = Math.max(0, hotBlockRecordCount - oldBlocks.size());
        }
    }

    private void maybeApplyMemoryGuard() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max <= 0L) {
            return;
        }
        long used = runtime.totalMemory() - runtime.freeMemory();
        double ratio = used / (double) max;
        long now = System.currentTimeMillis();
        if (largeSessionDetected() || ratio >= 0.72D) {
            compactDistantSnapshotDetails(now, ratio >= 0.86D);
        }
        if (ratio < 0.86D) {
            return;
        }
        if (now - lastMemoryWarningMillis > 10_000L) {
            lastMemoryWarningMillis = now;
            int queueBefore = pendingChunkKeys.size();
            pendingChunkKeys.clear();
            queuedChunkKeys.removeIf(key -> !completedChunkKeys.contains(key) && !partialChunkKeys.contains(key));
            Chat.warn("WorldBinder memory guard: high RAM usage detected (" + (int) (ratio * 100.0D) + "%). Dropped " + queueBefore + " queued chunks; already cached chunks are kept.");
        }
        if (!finishing) {
            adaptiveThrottlePercent = Math.min(adaptiveThrottlePercent, 35);
        }
    }

    private void compactDistantSnapshotDetails(long now, boolean urgent) {
        if (now - lastSnapshotCompactionMillis < (urgent ? 1500L : 5000L)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || liveChunkSnapshots.isEmpty()) {
            return;
        }
        lastSnapshotCompactionMillis = now;
        int playerChunkX = client.player.blockPosition().getX() >> 4;
        int playerChunkZ = client.player.blockPosition().getZ() >> 4;
        int keepRadius = urgent ? 12 : 24;
        int budget = urgent ? 2048 : 512;
        int compacted = 0;
        for (Map.Entry<Long, ChunkSnapshot> entry : liveChunkSnapshots.entrySet()) {
            if (compacted >= budget) {
                break;
            }
            ChunkSnapshot snapshot = entry.getValue();
            if (snapshot == null || !snapshot.isDone() || snapshot.colors == null || snapshot.colors.length == 0) {
                continue;
            }
            int dx = Math.abs(snapshot.chunkX - playerChunkX);
            int dz = Math.abs(snapshot.chunkZ - playerChunkZ);
            if (Math.max(dx, dz) <= keepRadius) {
                continue;
            }
            snapshot.compactVisualData();
            compacted++;
        }
        if (compacted > 0) {
            mapDataRevision++;
            if (urgent) {
                WorldBinderActivityLog.add("Compacted " + compacted + " distant chunk snapshots for memory pressure");
            }
        }
    }
}
