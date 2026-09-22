package net.worldbinder.capture;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
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
import net.worldbinder.scene.SceneLibrary;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.selection.Selection;
import net.worldbinder.selection.SelectionManager;
import net.worldbinder.status.OperationStatus;
import net.worldbinder.status.WorldBinderActivityLog;
import net.worldbinder.storage.StorageFlow;
import net.worldbinder.storage.BlockRecordChunkCache;
import net.worldbinder.util.BlockStateStrings;
import net.worldbinder.util.Chat;
import net.worldbinder.util.FileNames;
import net.worldbinder.util.Lang;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Collections;

public final class SceneCaptureService extends CaptureChunkPipeline {

    private final SelectionManager selections;
    private final SceneLibrary library;
    private final CaptureEntityStore entityStore = new CaptureEntityStore();
    private long lastMemoryWarningMillis;
    private long lastSnapshotCompactionMillis;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "WorldBinder IO");
        thread.setDaemon(true);
        return thread;
    });

    private String activeArchiveName;
    private String activeArchiveType;
    private long lastRecoverySaveMillis;
    private boolean recoverySaveRunning;
    private long finishStartedAtMillis;
    private long finishStartedProcessedBlocks;
    private int finishStartedQueueChunks;
    private BlockPos lastInteractedBlockEntityPos;
    private Entity lastInteractedEntity;

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
            Chat.errorKey("worldbinder.chat.no_world_loaded");
            return;
        }
        if (isCapturing()) {
            Chat.warnKey("worldbinder.chat.capture_running");
            return;
        }

        beginNewScene(requestedName, "world", client.player.blockPosition());
        roamingCapture = true;
        OperationStatus.begin(Lang.string("worldbinder.status.title"), Lang.string("worldbinder.status.download_running"));
        Chat.infoKey("worldbinder.chat.world_download_started", activeArchiveName, WorldBinder.config().performancePreset, WorldBinder.config().roamingRadiusChunks, WorldBinder.config().targetFps);
        WorldBinderActivityLog.add(Lang.string("worldbinder.activity.started_download", activeArchiveName));
        if (multiplayerSafetyActive()) {
            Chat.infoKey("worldbinder.chat.server_safety_active");
        }
        primeLoadedChunkHotCache(client);
        captureNearbyEntities(client, true);
    }

    public void finishActiveCapture() {
        requestFinishCapture();
    }

    public void requestFinishCapture() {
        if (!isCapturing()) {
            Chat.warnKey("worldbinder.chat.no_capture_running");
            return;
        }
        if (saving) {
            return;
        }
        if (roamingCapture) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.gui.setScreen(new net.worldbinder.ui.WorldBinderFinishScreen(client.gui.screen(), this));
            }
            return;
        }
        if (hasPendingWork()) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.gui.setScreen(new net.worldbinder.ui.WorldBinderFinishScreen(client.gui.screen(), this));
            }
            return;
        }
        Minecraft client = Minecraft.getInstance();
        requestSaveNowWithConfirm(client == null ? null : client.gui.screen());
    }

    private void saveWorldDownloadNow() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.level != null && client.player != null) {
            updatePlayerSpawn(client.player);
            flushLoadedChunksForFinish(client);
            captureNearbyEntities(client, true);
        }
        pendingBlocks.clear();
        pendingChunkKeys.clear();
        queuedChunkKeys.clear();
        clearActiveScans();
        finishing = true;
        paused = false;
        OperationStatus.update(Lang.string("worldbinder.status.finalizing"), 0.98D);
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
        OperationStatus.update(Lang.string("worldbinder.status.finishing_queue"), progress());
        Chat.infoKey("worldbinder.chat.finish_queue_before_save");
    }

    public boolean abortQueueAndSaveNow() {
        Minecraft client = Minecraft.getInstance();
        return requestSaveNowWithConfirm(client == null ? null : client.gui.screen());
    }

    public boolean hasPendingWork() {
        return isCapturing() && (!pendingBlocks.isEmpty() || !pendingChunkKeys.isEmpty() || !activeScanCursors.isEmpty());
    }

    public String targetFolderName() {
        String name = activeArchiveName == null ? "worldbinder_export" : activeArchiveName;
        return WorldBinder.config().appendTimestampToArchiveName
                ? FileNames.archiveFolderName(name)
                : FileNames.cleanBaseName(name);
    }

    public boolean targetWorldExists() {
        if (WorldBinder.config().appendTimestampToArchiveName) {
            return false;
        }
        return activeArchiveType != null && !"scene".equals(activeArchiveType)
                && java.nio.file.Files.exists(WorldBinderPaths.MINECRAFT_SAVES.resolve(targetFolderName()));
    }

    public boolean saveNowConfirmed() {
        return stopAndSaveNow();
    }

    public boolean requestSaveNowWithConfirm(net.minecraft.client.gui.screens.Screen parent) {
        Minecraft client = Minecraft.getInstance();
        if (WorldBinder.config().confirmExistingWorld && targetWorldExists()) {
            if (client != null) {
                client.gui.setScreen(new net.worldbinder.ui.WorldBinderExistingWorldScreen(parent, this));
            }
            return false;
        }
        return stopAndSaveNow();
    }

    public String finishStatusLine() {
        if (!isCapturing()) {
            return Lang.string("worldbinder.capture.finish.idle");
        }
        if (saving) {
            return Lang.string("worldbinder.capture.finish.saving");
        }
        if (!hasPendingWork()) {
            return Lang.string("worldbinder.capture.finish.ready_to_save");
        }
        ScanCursor cursor = activeScanCursors.peek();
        if (cursor != null) {
            return Lang.string("worldbinder.capture.finish.scanning", activeScanCursors.size(), activeScanCursors.size() == 1 ? "" : "s", cursor.chunkX, cursor.chunkZ, cursor.y);
        }
        return Lang.string("worldbinder.capture.finish.waiting");
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
            return Lang.string("worldbinder.capture.eta.zero");
        }
        int seconds = estimatedFinishSeconds();
        if (seconds <= 0 || seconds >= 9999) {
            return Lang.string("worldbinder.capture.eta.calculating");
        }
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return minutes > 0 ? minutes + "m " + rest + "s" : rest + "s";
    }

    public boolean stopAndSaveNow() {
        if (!isCapturing()) {
            Chat.warnKey("worldbinder.chat.no_capture_running");
            return false;
        }
        if (saving) {
            return true;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.player != null) {
            updatePlayerSpawn(client.player);
            flushLoadedChunksForFinish(client);
            captureNearbyEntities(client, true);
        }
        pendingBlocks.clear();
        pendingChunkKeys.clear();
        queuedChunkKeys.clear();
        clearActiveScans();
        finishing = true;
        paused = false;
        OperationStatus.update(Lang.string("worldbinder.status.saving_now"), 1.0D);
        return saveActiveSceneAsync();
    }

    public void cancelActiveCapture() {
        if (!isCapturing()) {
            return;
        }
        resetActiveJob();
        OperationStatus.finish(Lang.string("worldbinder.status.capture_cancelled"));
        Chat.warnKey("worldbinder.chat.capture_cancelled");
        WorldBinderActivityLog.add(Lang.string("worldbinder.activity.capture_cancelled"));
    }

    public void togglePause() {
        if (!isCapturing() || finishing || saving) {
            return;
        }
        paused = !paused;
        OperationStatus.update(paused ? Lang.string("worldbinder.status.paused") : statusLine(), progress());
        Chat.infoKey(paused ? "worldbinder.chat.capture_paused" : "worldbinder.chat.capture_resumed");
        WorldBinderActivityLog.add(Lang.string(paused ? "worldbinder.activity.capture_paused" : "worldbinder.activity.capture_resumed"));
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
            return Lang.string("worldbinder.capture.mode.ready");
        }
        if (saving) {
            return Lang.string("worldbinder.capture.mode.saving");
        }
        if (paused) {
            return roamingCapture ? Lang.string("worldbinder.capture.mode.world_paused") : Lang.string("worldbinder.capture.mode.position_paused");
        }
        return roamingCapture ? Lang.string("worldbinder.capture.mode.world") : Lang.string("worldbinder.capture.mode.position");
    }

    public int pendingBlocks() {
        return pendingBlocks.size() + pendingChunkKeys.size() * 16 * 16;
    }

    public int capturedBlocks() {
        if (activeScene == null) {
            return 0;
        }
        return activeScene.blockCount() + blockStore.recordCount();
    }

    public int capturedEntities() {
        if (activeScene == null) {
            return 0;
        }
        return activeScene.entityCount() + entityStore.hotRecordCount();
    }

    public boolean multiplayerSafetyActive() {
        Minecraft client = Minecraft.getInstance();
        return WorldBinder.config().serverSafetyMode && client.level != null && !!client.isMultiplayerServer();
    }

    public String safetySummary() {
        if (!multiplayerSafetyActive()) {
            return Lang.string("worldbinder.capture.safety.local");
        }
        int radius = WorldBinder.config().roamingRadiusChunks;
        if (radius >= 12) {
            return Lang.string("worldbinder.capture.safety.large_radius");
        }
        return Lang.string("worldbinder.capture.safety.multiplayer");
    }

    private int hotBlockCount() {
        return blockStore.recordCount();
    }

    private int hotEntityCount() {
        return entityStore.hotRecordCount();
    }

    public void cacheEntityHot(Entity entity) {
        if (!isCapturing()) {
            return;
        }
        entityStore.cacheHot(entity, activeOrigin, activeScene, liveChunkSnapshots, captureInputPaused());
    }

    public void onEntityLoaded(Entity entity) {
        cacheEntityHot(entity);
    }

    public void onEntityRemoved(Entity entity) {
        cacheEntityHot(entity);
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
        lastInteractedEntity = null;
        cacheBlockEntityHot(client, pos);
    }

    public void onInteractEntity(Entity entity) {
        if (!isCapturing() || entity == null || captureInputPaused()) {
            return;
        }
        lastInteractedEntity = entity;
        lastInteractedBlockEntityPos = null;
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
        BlockPos interactedBlockEntityPos = lastInteractedBlockEntityPos;
        Entity interactedEntity = lastInteractedEntity;
        lastInteractedBlockEntityPos = null;
        lastInteractedEntity = null;
        if (!isCapturing() || captureInputPaused() || screen == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        int storedSlots = inspectVisibleContainerSlots(screen);
        if (interactedBlockEntityPos != null) {
            cacheBlockEntityHot(client, interactedBlockEntityPos);
            if (storedSlots > 0 && activeScene != null) {
                activeScene.storageNotes.add("Captured visible container screen at " + interactedBlockEntityPos.toShortString() + " (" + storedSlots + " slots visible)");
            }
        }
        if (interactedEntity != null) {
            cacheEntityHot(interactedEntity);
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
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.capture.inspect_closed_container_failed"), throwable);
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
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.capture.hot_block_entity_failed", pos), throwable);
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
                OperationStatus.update(Lang.string("worldbinder.status.writing_cached_export"), 1.0D);
                saveActiveSceneAsync();
            } else {
                cancelActiveCapture();
            }
            return;
        }

        if (saving) {
            OperationStatus.update(Lang.string("worldbinder.status.writing_archive"), 1.0D);
            return;
        }

        updatePlayerSpawn(client.player);

        maybeApplyMemoryGuard();

        if (paused && !finishing) {
            OperationStatus.update(Lang.string("worldbinder.capture.status.paused_prefix") + statusLine(), progress());
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
            updatePlayerSpawn(client.player);
            saveActiveSceneAsync();
        } else {
            OperationStatus.update(statusLine(), progress());
        }
    }

    private void startSelectionCapture(String requestedName, String archiveType) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            Chat.errorKey("worldbinder.chat.no_world_loaded");
            return;
        }
        if (isCapturing()) {
            Chat.warnKey("worldbinder.chat.capture_running_finish_first");
            return;
        }
        if (!selections.hasCompleteSelection()) {
            Chat.warnKey("worldbinder.chat.selection_missing_or_world_download");
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
        OperationStatus.begin(Lang.string("worldbinder.status.title"), Lang.string("worldbinder.status.capturing_selection"));
        Chat.infoKey("worldbinder.chat.position_archive_queued", scheduledBlocks);
    }

    private void beginNewScene(String requestedName, String archiveType, BlockPos origin) {
        Minecraft client = Minecraft.getInstance();
        activeArchiveName = requestedName == null || requestedName.isBlank() ? WorldBinder.config().defaultArchiveName : requestedName.trim();
        activeArchiveType = archiveType;
        activeOrigin = origin.immutable();
        activeScene = new WorldScene();
        activeScene.chunkCacheFolder = blockStore.start(activeArchiveName);
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
        updatePlayerSpawn(client.player);
        activeScene.sizeX = 0;
        activeScene.sizeY = 0;
        activeScene.sizeZ = 0;
        pendingBlocks.clear();
        pendingChunkKeys.clear();
        capturedBlockPositions.clear();
        entityStore.clear();
        queuedChunkKeys.clear();
        completedChunkKeys.clear();
        partialChunkKeys.clear();
        liveChunkSnapshots.clear();
        blockStore.clear();
        failedChunkKeys.clear();
        observedLoadedChunkAges.clear();
        queueSkippedUnloaded = 0L;
        queueSkippedFarAway = 0L;
        queuePacketEnqueued = 0L;
        queueLoadedViewEnqueued = 0L;
        lastRecoverySaveMillis = 0L;
        recoverySaveRunning = false;
        lastMemoryWarningMillis = 0L;
        adaptiveThrottlePercent = 100;
        serverSafetyWarningSent = false;
        lastPlayerPos = null;
        stationaryTicks = 0;
        mapDataRevision = 0L;
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

    private void updatePlayerSpawn(Player player) {
        if (activeScene == null || player == null) {
            return;
        }
        activeScene.hasPlayerSpawn = true;
        activeScene.playerSpawnX = player.getX();
        activeScene.playerSpawnY = player.getY();
        activeScene.playerSpawnZ = player.getZ();
        activeScene.playerSpawnYaw = player.getYRot();
        activeScene.playerSpawnPitch = player.getXRot();
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

    private void maybeWarnServerSafety(Minecraft client) {
        if (serverSafetyWarningSent || !WorldBinder.config().serverSafetyMode || !client.isMultiplayerServer()) {
            return;
        }
        serverSafetyWarningSent = true;
        if (WorldBinder.config().roamingRadiusChunks >= 12) {
            Chat.warnKey("worldbinder.chat.server_safety_large_radius");
        } else {
            Chat.infoKey("worldbinder.chat.server_safety_active");
        }
    }

    private void maybeRecoveryAutosave() {
        if (!WorldBinder.config().crashRecovery || activeScene == null || recoverySaveRunning) {
            return;
        }
        long now = System.currentTimeMillis();
        long interval = Math.max(1, WorldBinder.config().recoveryAutosaveSeconds) * 1000L;
        if (largeSessionDetected()) {
            interval = Math.max(interval, 120_000L);
        }
        if (lastRecoverySaveMillis != 0L && now - lastRecoverySaveMillis < interval) {
            return;
        }
        lastRecoverySaveMillis = now;
        recoverySaveRunning = true;
        WorldScene source = activeScene;
        String name = activeArchiveName == null ? "recovery" : activeArchiveName;
        Path target = WorldBinderPaths.RECOVERY_ROOT.resolve("_recovery_" + FileNames.cleanBaseName(name));
        ioExecutor.execute(() -> {
            try {
                WorldScene scene = copySceneForIo(source, true, true);
                deleteOldRecovery(target);
                library.saveRecoverySnapshot(scene, target);
            } catch (IOException exception) {
                library.markRecoveryFailed(target, exception.getMessage());
                WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.recovery.autosave_write_failed"), exception);
            } catch (RuntimeException exception) {
                library.markRecoveryFailed(target, exception.getMessage());
                WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.recovery.autosave_prepare_failed"), exception);
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
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.recovery.clean_failed"), exception);
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
        entityStore.captureNearby(client, wide, roamingCapture, activeScene, activeOrigin,
                liveChunkSnapshots, captureInputPaused());
    }

    @Override
    protected void expandBounds(BlockPos pos) {
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

    private WorldScene copySceneForIo(WorldScene source, boolean completedOnly, boolean includeRecoveryPartials) {
        Map<Long, List<BlockRecord>> staged = includeRecoveryPartials
                ? blockStore.stagedChunks() : Collections.emptyMap();
        return CaptureSceneSnapshotFactory.copyForStorage(source, completedOnly, blockStore.cache(),
                staged, blockStore.hotChunks(), entityStore.hotChunks(),
                key -> exportableChunk(key, includeRecoveryPartials));
    }

    private boolean exportableChunk(long key, boolean includeRecoveryPartials) {
        if (completedChunkKeys.contains(key)) {
            return true;
        }
        if (!includeRecoveryPartials) {
            return false;
        }
        ChunkSnapshot snapshot = liveChunkSnapshots.get(key);
        if (snapshot == null) {
            return false;
        }
        ChunkCaptureStatus status = snapshot.effectiveStatus();
        return status == ChunkCaptureStatus.PARTIAL || status == ChunkCaptureStatus.RECOVERY;
    }

    public void continueRecovery(Path recoveryFolder) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            Chat.errorKey("worldbinder.chat.recovery_join_world");
            return;
        }
        if (isCapturing()) {
            Chat.warnKey("worldbinder.chat.recovery_active_capture");
            return;
        }
        if (!library.canFinalizeRecovery(recoveryFolder)) {
            Chat.warnKey("worldbinder.chat.recovery_invalid_folder");
            return;
        }
        OperationStatus.begin(Lang.string("worldbinder.status.recovery_title"), Lang.string("worldbinder.status.recovery_loading_async"));
        Chat.infoKey("worldbinder.chat.recovery_loading_background");
        ioExecutor.execute(() -> {
            try {
                WorldScene scene = library.read(recoveryFolder);
                Minecraft.getInstance().execute(() -> applyRecoveryScene(recoveryFolder, scene));
            } catch (Exception exception) {
                Minecraft.getInstance().execute(() -> {
                    OperationStatus.finish(Lang.string("worldbinder.status.recovery_load_failed"));
                    Chat.errorKey("worldbinder.chat.recovery_continue_failed");
                });
                WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.recovery.continue_failed"), exception);
            }
        });
    }

    private void applyRecoveryScene(Path recoveryFolder, WorldScene scene) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            OperationStatus.finish(Lang.string("worldbinder.status.recovery_load_cancelled"));
            Chat.errorKey("worldbinder.chat.recovery_join_world");
            return;
        }
        if (isCapturing()) {
            OperationStatus.finish(Lang.string("worldbinder.status.recovery_load_cancelled"));
            Chat.warnKey("worldbinder.chat.recovery_active_capture");
            return;
        }
        if (scene == null) {
            OperationStatus.finish(Lang.string("worldbinder.status.recovery_load_failed"));
            Chat.errorKey("worldbinder.chat.recovery_file_read_failed");
            return;
        }

        pendingBlocks.clear();
        pendingChunkKeys.clear();
        capturedBlockPositions.clear();
        entityStore.clear();
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
        blockStore.clear();
        clearActiveScans();

        activeScene = scene;
        activeArchiveName = scene.name == null || scene.name.isBlank()
                ? recoveryFolder.getFileName().toString().replaceFirst("^_recovery_", "")
                : scene.name;
        if (scene.chunkCacheFolder == null || scene.chunkCacheFolder.isBlank()) {
            scene.chunkCacheFolder = recoveryCacheFolderFromManifest(recoveryFolder);
        }
        scene.chunkCacheFolder = blockStore.resume(scene,
                activeArchiveName == null ? "recovery" : activeArchiveName);
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
        OperationStatus.begin(Lang.string("worldbinder.status.recovery_title"), Lang.string("worldbinder.status.recovery_loaded"));
        Chat.warnKey("worldbinder.chat.recovery_loaded", activeArchiveName);
        if (largeSessionDetected()) {
            Chat.warnKey("worldbinder.chat.recovery_large_session");
        }
        WorldBinderActivityLog.add(Lang.string("worldbinder.activity.recovery_continued", activeArchiveName));
    }

    private boolean saveActiveSceneAsync() {
        if (saving || activeScene == null) {
            return false;
        }
        saving = true;
        String name = activeArchiveName;
        String type = activeArchiveType;
        boolean sceneArchive = "scene".equals(type);
        boolean strictCompletedWorldExport = roamingCapture && !sceneArchive;
        BlockRecordChunkCache cacheToClean = blockStore.cache();
        WorldScene source = activeScene;
        Path target = sceneArchive
                ? WorldBinderPaths.SCENES.resolve(FileNames.archiveFileName(name, WorldBinder.config().appendTimestampToArchiveName))
                : WorldBinderPaths.newWorldFolder(name, WorldBinder.config().appendTimestampToArchiveName);

        OperationStatus.begin(Lang.string("worldbinder.status.storage_title"), Lang.string("worldbinder.status.storage_preparing"));
        StorageFlow.progress().start(target);
        openStorageProgressScreen();
        ioExecutor.execute(() -> {
            try {
                WorldScene scene = copySceneForIo(source, strictCompletedWorldExport, false);
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> {
                    resetActiveJob();
                    saving = true;
                    OperationStatus.begin(Lang.string("worldbinder.status.storage_title"), Lang.string("worldbinder.status.storage_writing"));
                    openStorageProgressScreen();
                    StorageFlow.submit(library, scene, target, sceneArchive, savedPath -> {
                        saving = false;
                        String validation = library.validationLine(savedPath);
                        OperationStatus.finish(Lang.string("worldbinder.status.saved_counts", scene.blockCount(), scene.entityCount(), validation));
                        Chat.savedArchive(scene.archiveType, scene.name, scene.blockCount(), scene.blockEntityCount(), scene.entityCount(), savedPath);
                        if (!Lang.string("worldbinder.validation.no_report").equals(validation)) {
                            Chat.infoKey("worldbinder.chat.export_validation", validation);
                            WorldBinderActivityLog.add(Lang.string("worldbinder.activity.validation_passed", validation));
                        }
                        WorldBinderActivityLog.add(Lang.string("worldbinder.activity.saved_archive", scene.name));
                        if (cacheToClean != null) {
                            cacheToClean.deleteAll();
                        }
                    }, throwable -> {
                        saving = false;
                        OperationStatus.finish(Lang.string("worldbinder.status.save_failed"));
                        Chat.errorKey("worldbinder.chat.save_failed");
                    });
                });
            } catch (RuntimeException exception) {
                Minecraft.getInstance().execute(() -> {
                    saving = false;
                    StorageFlow.progress().fail(Lang.string("worldbinder.status.save_failed"));
                    OperationStatus.finish(Lang.string("worldbinder.status.save_failed"));
                    Chat.errorKey("worldbinder.chat.prepare_failed");
                });
                WorldBinder.LOGGER.error(Lang.string("worldbinder.log.storage.prepare_archive_failed"), exception);
            }
        });
        return true;
    }

    private void openStorageProgressScreen() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        if (client.gui.screen() instanceof net.worldbinder.ui.WorldBinderStorageProgressScreen) {
            return;
        }
        client.gui.setScreen(new net.worldbinder.ui.WorldBinderStorageProgressScreen(client.gui.screen()));
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
        finishStartedAtMillis = 0L;
        finishStartedProcessedBlocks = 0L;
        finishStartedQueueChunks = 0;
        clearActiveScans();
        pendingBlocks.clear();
        pendingChunkKeys.clear();
        capturedBlockPositions.clear();
        entityStore.clear();
        queuedChunkKeys.clear();
        completedChunkKeys.clear();
        partialChunkKeys.clear();
        failedChunkKeys.clear();
        blockStore.clear();
        lastMemoryWarningMillis = 0L;
        lastPlayerPos = null;
        stationaryTicks = 0;
        mapDataRevision++;
    }

    private String recoveryCacheFolderFromManifest(Path recoveryFolder) {
        if (recoveryFolder == null) {
            return null;
        }
        Path manifest = recoveryFolder.resolve("worldbinder").resolve("worldbinder_manifest.json");
        if (!Files.isRegularFile(manifest)) {
            return null;
        }
        try {
            String json = Files.readString(manifest);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"chunkCacheFolder\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
            return matcher.find() ? matcher.group(1) : null;
        } catch (IOException exception) {
            return null;
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
            Chat.warnKey("worldbinder.chat.memory_guard", (int) (ratio * 100.0D));
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
                WorldBinderActivityLog.add(Lang.string("worldbinder.activity.compacted_snapshots", compacted));
            }
        }
    }
}
