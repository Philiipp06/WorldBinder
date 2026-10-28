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
import net.worldbinder.scene.EntityRecord;
import net.worldbinder.scene.ProjectData;
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
    private final AuxiliaryCapture auxiliary = new AuxiliaryCapture();
    private final ContainerCapture containerCapture = new ContainerCapture();
    private ContainerScanner containerScanner=new ContainerScanner();
    public String scannerStatus(){return containerScanner.status();}
    public boolean scannerEnabled(){return containerScanner.enabled();}
    public int scannerRetries(){return containerScanner.retries();}
    public void toggleScanner(){containerScanner.toggle();}
    public void rescanContainers(){containerScanner.rescan();}
    public void resumeScanner(){containerScanner.resume();}
    public void onManualContainerInput(){containerScanner.manual();}
    public boolean projectMatchesCurrentWorld(){return captureWorldMatches(Minecraft.getInstance());}
    public boolean cacheHealthy(){return blockStore.cache()==null||!blockStore.cache().hasWriteFailures();}
    public boolean cacheWritePending(long key){return blockStore.cache()!=null&&blockStore.cache().writePending(key);}
    public boolean cacheWriteFailed(long key){return blockStore.cache()!=null&&blockStore.cache().hasWriteFailure(key);}
    public boolean cachePreviouslyDurable(long key){return blockStore.cache()!=null&&blockStore.cache().hasDurableChunk(key);}
    private final java.util.Set<Long> originalChunks = new java.util.LinkedHashSet<>();
    private boolean projectResume;
    private boolean exportReviewApproved;
    private boolean awaitingExportReview;
    private boolean exportReviewDeferred;
    private WorldScene preparedExport;
    private net.worldbinder.ui.WorldBinderExportScreen pendingExportReview;
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
    private volatile boolean recoverySaveRunning;
    private long finishStartedAtMillis;
    private long finishStartedProcessedBlocks;
    private int finishStartedQueueChunks;
    private BlockPos lastInteractedBlockEntityPos;
    private Entity lastInteractedEntity;
    private SelectionWork selectionWork;
    private long selectionConfirmedBlocks;
    private net.minecraft.client.multiplayer.ClientLevel captureLevel;
    private String routeReason;
    private boolean routeWasPaused,routeDialogShown;
    private java.util.concurrent.CompletableFuture<WorldScene> routeSnapshot;
    private WorldScene frozenRoute;
    private String routeFailure;
    private final Map<String,WorldScene> backendSessions=new java.util.LinkedHashMap<>();
    private String currentBackend,currentBackendName;
    private String backendNetwork;
    private long worldEpoch;
    private final java.util.ArrayDeque<Long> replayRefresh=new java.util.ArrayDeque<>();
    private final java.util.Set<Long> replayRefreshKeys=new java.util.HashSet<>();
    public void onReplayBlockChange(int x,int z){
        if(activeScene!=null&&activeScene.replayIdentity!=null&&!captureInputPaused()){
            long key=ChunkPos.pack(x,z);if(!activeScene.chunkSnapshots.containsKey(x+","+z)&&!activeScanChunkKeys.contains(key))return;if(replayRefreshKeys.add(key))replayRefresh.addLast(key);
        }
    }
    private boolean replayWasFenced;
    private final net.worldbinder.capture.JoinCaptureController joinCapture=new net.worldbinder.capture.JoinCaptureController();

    public boolean routingPending(){return routeReason!=null;}
    public boolean routingReady(){return frozenRoute!=null&&!saving;}
    public boolean routingFailed(){return routeFailure!=null;}
    public String routingFailure(){return routeFailure==null?"":Lang.resolve(routeFailure);}
    public void retryRouting(){if(routeFailure!=null&&!saving){routeFailure=null;routeSnapshot=null;routeDialogShown=false;}}
    public String routeReason(){return routeReason;}
    public net.worldbinder.scene.NetworkProfile networkProfile(){
        String identity=WorldIdentity.current(Minecraft.getInstance());
        return identity==null?new net.worldbinder.scene.NetworkProfile():WorldBinder.config().networkProfiles.computeIfAbsent(identity,k->new net.worldbinder.scene.NetworkProfile());
    }
    public void worldBoundary(String reason){
        worldEpoch++;
        joinCapture.boundary(reason);
        auxiliary.disconnected();containerScanner.suspend();containerCapture.clear();
        if(!isCapturing()){currentBackend=null;currentBackendName=null;backendNetwork=null;return;}
        if(activeScene.replayIdentity!=null&&!reason.equals("replay_dimension")){net.worldbinder.replay.ReplayPlayback.seek();containerScanner.suspend();containerCapture.clear();return;}
        if(routeReason==null){routeWasPaused=paused;routeReason=reason;routeSnapshot=null;frozenRoute=null;routeDialogShown=false;}
        else if(reason.equals("transfer"))routeReason=reason;
        containerScanner.suspend();containerCapture.clear();auxiliary.disconnected();
    }

    private void discardInterruptedScans(){
        replayRefresh.clear();replayRefreshKeys.clear();
        for(long key:activeScanChunkKeys)blockStore.discard(key);
        pendingBlocks.clear();pendingChunkKeys.clear();queuedChunkKeys.clear();clearActiveScans();
        if(selectionWork!=null){selectionWork.clear();processedBlocks=selectionConfirmedBlocks;}
    }
    private void routeTick(Minecraft client){
        if(routeFailure!=null)return;
        if(routeSnapshot==null&&!saving){
            OperationStatus.update(Lang.stored("worldbinder.network.saving_handoff"),progress());
            saving=true;WorldScene source=activeScene;discardInterruptedScans();
            routeSnapshot=java.util.concurrent.CompletableFuture.supplyAsync(()->{
                var frozen=copySceneForIo(source,true,true);
                try{Path recovery=net.worldbinder.scene.RecoveryProjects.folder(WorldBinderPaths.RECOVERY_ROOT,frozen);library.saveRecoverySnapshot(frozen,recovery);rememberProject(frozen,recovery);}
                catch(IOException error){throw new java.util.concurrent.CompletionException(error);}
                return frozen;
            },ioExecutor);
        }
        if(routeSnapshot==null||!routeSnapshot.isDone())return;
        if(frozenRoute==null){
            try{frozenRoute=routeSnapshot.join();backendSessions.clear();backendSessions.put(frozenRoute.worldIdentity+"/"+frozenRoute.backendId,frozenRoute);}
            catch(RuntimeException error){saving=false;paused=true;routeFailure=Lang.stored("worldbinder.network.snapshot_failed",net.worldbinder.validation.Finding.detail(error));OperationStatus.update(routeFailure,progress());Chat.errorKey("worldbinder.network.snapshot_failed",net.worldbinder.validation.Finding.detail(error));if(!(net.worldbinder.compat.ClientCompat.screen(client) instanceof net.worldbinder.ui.WorldBinderNetworkScreen))net.worldbinder.compat.ClientCompat.setScreen(client,new net.worldbinder.ui.WorldBinderNetworkScreen(net.worldbinder.compat.ClientCompat.screen(client)));return;}
            saving=false;
        }
        if(client.level==null||client.player==null)return;
        boolean sameNetwork=java.util.Objects.equals(activeScene.worldIdentity,WorldIdentity.current(client));
        boolean local=client.getSingleplayerServer()!=null&&activeScene.replayIdentity==null;
        if(sameNetwork&&local&&!activeScene.captureSettings.multiDimension){
            paused=true;
            if(WorldIdentity.dimensionMatches(activeScene.dimension,WorldIdentity.dimension(client))){
                // A return creates a new ClientLevel even for the original dimension. Keep the
                // committed project paused, but stop treating this confirmed return as a new handoff.
                captureLevel=client.level;
                primeLoadedChunkHotCache(client);
                clearRoute();
            }
            return;
        }
        if(sameNetwork&&activeScene.captureSettings.multiDimension&&(local||networkProfile().trustDimensionChanges&&(routeReason.equals("respawn")||routeReason.equals("level")))&&!routeReason.equals("transfer")&&!routeReason.equals("manual")){
            assignBackend(activeScene.backendId,activeScene.backendName,false);return;
        }
        if(!routeDialogShown){routeDialogShown=true;if(!(net.worldbinder.compat.ClientCompat.screen(client) instanceof net.worldbinder.ui.WorldBinderNetworkScreen))net.worldbinder.compat.ClientCompat.setScreen(client,new net.worldbinder.ui.WorldBinderNetworkScreen(net.worldbinder.compat.ClientCompat.screen(client)));}
        OperationStatus.update(Lang.stored("worldbinder.network.waiting"),progress());
    }
    private void clearRoute(){routeReason=null;routeSnapshot=null;frozenRoute=null;routeDialogShown=false;routeFailure=null;}
    public boolean assignBackend(String id,String name,boolean rememberJoin){
        Minecraft client=Minecraft.getInstance();if(client.level==null||client.player==null||saving)return false;
        if(isCapturing()&&frozenRoute==null)return false;
        if(id==null||id.isBlank())id=java.util.UUID.randomUUID().toString();
        String identity=WorldIdentity.current(client);if(identity==null)return false;
        String dimension=WorldIdentity.dimension(client);
        var profile=networkProfile();var backend=profile.backends.get(id);
        if(backend==null){backend=new net.worldbinder.scene.NetworkProfile.Backend();backend.id=id;backend.name=name;profile.backends.put(id,backend);}
        if(name==null||name.isBlank())name=backend.name;
        currentBackend=id;currentBackendName=name;backendNetwork=identity;
        if(rememberJoin)profile.joinBackend=id;
        WorldBinder.config().save();
        if(!isCapturing())return true;
        if(frozenRoute==null)return false;
        WorldScene root=backendSessions.get(identity+"/"+id);
        if(root==null){
            if(backend.projectPath!=null){
                Path path=Path.of(backend.projectPath);final String backendId=id,backendLabel=name;final long requestedEpoch=worldEpoch;final WorldScene requestedSource=activeScene;
                saving=true;ioExecutor.execute(()->{try{var loaded=library.read(path);client.execute(()->{if(activeScene!=requestedSource)return;saving=false;if(requestedEpoch!=worldEpoch){routeDialogShown=false;Chat.errorKey("worldbinder.network.identity_mismatch");return;}if(!java.util.Objects.equals(loaded.worldIdentity,WorldIdentity.current(client))||!java.util.Objects.equals(loaded.backendId,backendId)){Chat.errorKey("worldbinder.network.identity_mismatch");return;}backendSessions.put(identity+"/"+backendId,loaded);assignBackend(backendId,backendLabel,rememberJoin);});}catch(Exception error){client.execute(()->{if(activeScene!=requestedSource)return;saving=false;Chat.errorKey("worldbinder.network.load_failed",net.worldbinder.validation.Finding.detail(error));});}});return true;
            }
            String projectName=activeScene.name;var policy=activeScene.captureSettings.copy();
            resetActiveJob();beginNewScene(projectName+"_"+(name==null?id.substring(0,8):name),"world",client.player.blockPosition());
            activeScene.captureSettings=policy;activeScene.backendId=id;activeScene.backendName=name;roamingCapture=true;clearRoute();
        }else{
            var empty=new WorldScene();empty.dimension=dimension;empty.originX=client.player.getBlockX();empty.originY=client.player.getBlockY();empty.originZ=client.player.getBlockZ();
            empty.minecraftVersion=root.minecraftVersion;empty.targetMinecraftVersion=root.targetMinecraftVersion;
            var next=net.worldbinder.scene.ProjectDimensions.activate(root,dimension,empty);
            boolean wasPaused=routeWasPaused;resetActiveJob();clearRoute();applyRecoveryScene(net.worldbinder.scene.RecoveryProjects.folder(WorldBinderPaths.RECOVERY_ROOT,next),next);paused=wasPaused;
        }
        captureLevel=client.level;readDimensionMetadata(activeScene,client);primeLoadedChunkHotCache(client);mapDataRevision++;
        backendSessions.clear();
        Chat.infoKey("worldbinder.network.assigned",name,dimension);return true;
    }
    public void joined(){joinCapture.joined();}
    public void resumeAssignedProject(Path path){continueProject(path,currentBackend);}
    public void branchReplay(){
        if(activeScene==null||activeScene.replayIdentity==null||saving)return;
        var playback=net.worldbinder.replay.ReplayPlayback.state();if(!playback.ready()||net.worldbinder.replay.ReplayPlayback.fenced())return;
        saving=true;WorldScene source=activeScene;containerScanner.suspend();
        java.util.concurrent.CompletableFuture.runAsync(()->{
            try{var frozen=copySceneForIo(source,true,true);library.saveRecoverySnapshot(frozen,net.worldbinder.scene.RecoveryProjects.folder(WorldBinderPaths.RECOVERY_ROOT,frozen));}
            catch(IOException error){throw new java.util.concurrent.CompletionException(error);}
        },ioExecutor).whenComplete((ignored,error)->Minecraft.getInstance().execute(()->{
            saving=false;if(activeScene!=source)return;if(error!=null){Chat.errorKey("worldbinder.network.snapshot_failed",net.worldbinder.validation.Finding.detail(error));return;}
            String name=source.name+"_"+playback.millis()/1000;resetActiveJob();startRoamingCapture(name);
        }));
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
        var replay=net.worldbinder.replay.ReplayPlayback.poll();
        if(replay.present()&&!replay.ready()){Chat.errorKey("worldbinder.replay.not_ready");return;}
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
        OperationStatus.begin(net.worldbinder.util.Lang.stored("worldbinder.status.title"), net.worldbinder.util.Lang.stored("worldbinder.status.download_running"));
        Chat.infoKey("worldbinder.chat.world_download_started", activeArchiveName, WorldBinder.config().performancePreset.label(), WorldBinder.config().roamingRadiusChunks, WorldBinder.config().targetFps);
        WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored("worldbinder.activity.started_download", activeArchiveName));
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
        joinCapture.manualStop(Minecraft.getInstance());
        if (!isCapturing()) {
            Chat.warnKey("worldbinder.chat.no_capture_running");
            return;
        }
        if (saving) {
            return;
        }
        if (awaitingExportReview) {
            if(pendingExportReview!=null)net.worldbinder.compat.ClientCompat.setScreen(Minecraft.getInstance(),pendingExportReview);
            return;
        }
        if (roamingCapture) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                net.worldbinder.compat.ClientCompat.setScreen(client, new net.worldbinder.ui.WorldBinderFinishScreen(net.worldbinder.compat.ClientCompat.screen(client), this));
            }
            return;
        }
        if (hasPendingWork()) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                net.worldbinder.compat.ClientCompat.setScreen(client, new net.worldbinder.ui.WorldBinderFinishScreen(net.worldbinder.compat.ClientCompat.screen(client), this));
            }
            return;
        }
        Minecraft client = Minecraft.getInstance();
        requestSaveNowWithConfirm(client == null ? null : net.worldbinder.compat.ClientCompat.screen(client));
    }

    private void saveWorldDownloadNow() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.level != null && client.player != null && captureWorldMatches(client)) {
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
        OperationStatus.update(net.worldbinder.util.Lang.stored("worldbinder.status.finalizing"), 0.98D);
        saveActiveSceneAsync();
    }

    public void finishAfterQueue() {
        if (!isCapturing() || saving) {
            return;
        }
        finishing = true;
        exportReviewDeferred = false;
        paused = false;
        finishStartedAtMillis = System.currentTimeMillis();
        finishStartedProcessedBlocks = processedBlocks;
        finishStartedQueueChunks = queuedChunkCount();
        OperationStatus.update(net.worldbinder.util.Lang.stored("worldbinder.status.finishing_queue"), progress());
        Chat.infoKey("worldbinder.chat.finish_queue_before_save");
    }

    public boolean abortQueueAndSaveNow() {
        Minecraft client = Minecraft.getInstance();
        return requestSaveNowWithConfirm(client == null ? null : net.worldbinder.compat.ClientCompat.screen(client));
    }

    public boolean hasPendingWork() {
        return isCapturing() && (selectionWork!=null&&selectionConfirmedBlocks<selectionWork.bounds.volume() || !pendingBlocks.isEmpty() || !pendingChunkKeys.isEmpty() || !activeScanCursors.isEmpty());
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
        if (WorldBinder.config().confirmExistingWorld && targetWorldExists() && !projectResume) {
            if (client != null) {
                net.worldbinder.compat.ClientCompat.setScreen(client, new net.worldbinder.ui.WorldBinderExistingWorldScreen(parent, this));
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
            return Lang.string("worldbinder.capture.finish.scanning_count", activeScanCursors.size(), cursor.chunkX, cursor.chunkZ, cursor.y);
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
        return minutes > 0 ? Lang.string("worldbinder.time.minutes_seconds", minutes, rest) : Lang.string("worldbinder.time.seconds", rest);
    }

    public boolean stopAndSaveNow() {
        if (!isCapturing()) {
            Chat.warnKey("worldbinder.chat.no_capture_running");
            return false;
        }
        if (saving) {
            return true;
        }
        if(awaitingExportReview){requestFinishCapture();return false;}
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
        exportReviewDeferred = false;
        paused = false;
        OperationStatus.update(net.worldbinder.util.Lang.stored("worldbinder.status.saving_now"), 1.0D);
        return saveActiveSceneAsync();
    }

    public void cancelActiveCapture() {
        if (!isCapturing() || saving) {
            return;
        }
        joinCapture.manualStop(Minecraft.getInstance());
        StorageFlow.progress().dismissReview();
        resetActiveJob();
        OperationStatus.finish(net.worldbinder.util.Lang.stored("worldbinder.status.capture_cancelled"));
        Chat.warnKey("worldbinder.chat.capture_cancelled");
        WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored("worldbinder.activity.capture_cancelled"));
    }

    public void togglePause() {
        if(activeScene!=null&&activeScene.replayRewound){Chat.warnKey("worldbinder.replay.rewound");return;}
        if (!isCapturing() || finishing && selectionWork==null || saving) {
            return;
        }
        paused = !paused;
        if (!paused) exportReviewDeferred = false;
        if(paused)containerScanner.suspend();
        OperationStatus.update(net.worldbinder.util.Lang.stored("worldbinder.status.message", paused ? Lang.stored("worldbinder.status.paused") : statusLine()), progress());
        Chat.infoKey(paused ? "worldbinder.chat.capture_paused" : "worldbinder.chat.capture_resumed");
        WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored(paused ? "worldbinder.activity.capture_paused" : "worldbinder.activity.capture_resumed"));
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
        return activeScene.blockCount() + hotBlockCount();
    }

    public int capturedEntities() {
        if (activeScene == null) {
            return 0;
        }
        return activeScene.entityCount() + entityStore.hotRecordCount();
    }

    public boolean multiplayerSafetyActive() {
        Minecraft client = Minecraft.getInstance();
        return WorldBinder.config().serverSafetyMode && client.level != null && net.worldbinder.compat.ClientCompat.multiplayer(client);
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
        return blockStore.recordCount() - blockStore.replacedBaselineCount();
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
        if(net.worldbinder.replay.ReplayPlayback.state().present())return;
        containerScanner.manual();
        if (!isCapturing() || hitResult == null || captureInputPaused()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        BlockPos pos = hitResult.getBlockPos();
        containerCapture.target(pos);
        lastInteractedBlockEntityPos = pos.immutable();
        lastInteractedEntity = null;
        cacheBlockEntityHot(client, pos);
    }

    public void onInteractEntity(Entity entity) {
        if(net.worldbinder.replay.ReplayPlayback.state().present())return;
        containerScanner.manual();
        if (!isCapturing() || entity == null || captureInputPaused() || activeScene.captureSettings.excludes(entity)) {
            return;
        }
        containerCapture.target(entity);
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
            activeScene.storageNotes.add(net.worldbinder.util.Lang.stored("worldbinder.capture.map_observed", id));
        }
    }

    public void onContainerScreenClosed(Screen screen) {
        containerScanner.screenClosed();
        containerCapture.clear();
        lastInteractedBlockEntityPos = null;
        lastInteractedEntity = null;
    }
    public void onContainerOpened(int id) {
        if (isCapturing() && !captureInputPaused()) {containerCapture.opened(activeScene,Minecraft.getInstance(), id);
            if(activeScene.captureSettings.manualContainerMerge)containerScanner.manual();
            if(containerCapture.attached(id))containerScanner.opened(id);else containerScanner.suspend();}
    }
    public void onContainerContent(net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket packet) {
        if (isCapturing() && !captureInputPaused()) {containerCapture.content(activeScene, Minecraft.getInstance(), packet.containerId(), packet.stateId(), packet.items());
            if(containerCapture.verified(packet.containerId()))containerScanner.verified(packet.containerId());}
    }
    public void onContainerSlot(net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket packet) {
        if (isCapturing() && !captureInputPaused()) {
            containerCapture.slot(activeScene, Minecraft.getInstance(), packet.getContainerId(), packet.getStateId(), packet.getSlot(), packet.getItem());
            if(containerCapture.verified(packet.getContainerId()))containerScanner.verified(packet.getContainerId());
        }
    }
    public int containerKnownSlots(){return isCapturing()&&captureWorldMatches(Minecraft.getInstance())?containerCapture.knownSlots():0;}
    public int containerTotalSlots(){return isCapturing()&&captureWorldMatches(Minecraft.getInstance())?containerCapture.totalSlots():0;}
    public boolean mergeContainer(net.worldbinder.scene.ContainerMerging.Strategy choice,boolean projectDefault){
        if(!isCapturing()||saving||!captureWorldMatches(Minecraft.getInstance()))return false;
        containerScanner.manual();return containerCapture.apply(activeScene,Minecraft.getInstance(),choice,projectDefault);
    }
    public void onMapPacket(net.minecraft.network.protocol.game.ClientboundMapItemDataPacket packet) {
        auxiliary.map(isCapturing() && !captureInputPaused() ? activeScene : null, Minecraft.getInstance(), packet);
    }
    public void onStatsPacket(net.minecraft.network.protocol.game.ClientboundAwardStatsPacket packet) {
        auxiliary.stats(isCapturing() && !captureInputPaused() ? activeScene : null, Minecraft.getInstance(), packet);
    }
    public void onAdvancementsPacket(net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket packet) {
        auxiliary.advancements(isCapturing() && !captureInputPaused() ? activeScene : null, Minecraft.getInstance(), packet);
    }
    public void onLightPacket(int x, int z, net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData data) {
        auxiliary.light(Minecraft.getInstance(), x, z, data);
    }
    @Override protected void captureChunkExtras(net.minecraft.world.level.chunk.LevelChunk chunk, ChunkSnapshot snapshot) {
        auxiliary.sections(Minecraft.getInstance(), chunk, snapshot);
    }
    private boolean captureInputPaused() { return paused && (!finishing || selectionWork!=null) || saving || !captureWorldMatches(Minecraft.getInstance()); }
    @Override protected boolean captureWorldMatches(Minecraft client) {
        return routeReason==null&&net.worldbinder.replay.ReplayPlayback.accepts(activeScene)&&activeScene!=null&&client!=null&&client.level!=null&&java.util.Objects.equals(activeScene.worldIdentity,WorldIdentity.current(client))
                &&WorldIdentity.dimensionMatches(activeScene.dimension,WorldIdentity.dimension(client));
    }
    public void onDisconnected() { worldEpoch++;auxiliary.disconnected(); containerCapture.clear();containerScanner.suspend();joinCapture.disconnected(); }

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
        if(activeScene.selectionBounds!=null)return; // Bounded scans commit their block entities with the corresponding tile.
        BlockEntity blockEntity = client.level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        long key=ChunkPos.pack(pos.getX()>>4,pos.getZ()>>4);
        ChunkSnapshot snapshot = liveChunkSnapshots.compute(key, (ignored,current) -> detachedMetadataSnapshot(current,activeScene.chunkSnapshots.get((pos.getX()>>4)+","+(pos.getZ()>>4)),pos.getX()>>4,pos.getZ()>>4));
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
            activeScene.storageNotes.add(net.worldbinder.util.Lang.stored("worldbinder.capture.container_refreshed", pos.toShortString()));
        } catch (Throwable throwable) {
            snapshot.markError(net.worldbinder.util.Lang.stored("worldbinder.capture.interacted_block_entity_failed", pos.toShortString()));
            failedChunkKeys.add(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
            mapDataRevision++;
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.capture.hot_block_entity_failed", pos), throwable);
        }
    }

    /** Metadata failures belong to the live update; they must not invalidate the last committed revision. */
    static ChunkSnapshot detachedMetadataSnapshot(ChunkSnapshot live,ChunkSnapshot committed,int x,int z){
        return live==null?new ChunkSnapshot(x,z):live==committed?live.copy():live;
    }

    public void tick() {
        var playback=net.worldbinder.replay.ReplayPlayback.poll();
        Minecraft client = Minecraft.getInstance();
        joinCapture.tick(client,this);
        if (!isCapturing()) {
            return;
        }

        if(activeScene.replayIdentity!=null){
            long requestedTime=net.worldbinder.replay.ReplayPlayback.consumeSeekTarget();
            if(requestedTime<activeScene.replayHighWater||playback.ready()&&!net.worldbinder.replay.ReplayPlayback.fenced()&&java.util.Objects.equals(playback.identity(),activeScene.replayIdentity)&&playback.millis()<activeScene.replayHighWater){activeScene.replayRewound=true;paused=true;}
            boolean fenced=!net.worldbinder.replay.ReplayPlayback.accepts(activeScene);
            if(fenced){if(!replayWasFenced){if(net.worldbinder.replay.ReplayPlayback.fenced()||playback.present()&&playback.millis()<activeScene.replayHighWater)activeScene.replaySeekGaps++;containerScanner.suspend();containerCapture.clear();auxiliary.disconnected();discardInterruptedScans();lastRecoverySaveMillis=0;maybeRecoveryAutosave();}replayWasFenced=true;OperationStatus.update(Lang.stored("worldbinder.status.message",net.worldbinder.replay.ReplayPlayback.status(activeScene)),progress());return;}
            if(replayWasFenced){auxiliary.disconnected();replayWasFenced=false;observedLoadedChunkAges.clear();if(client.level!=null)for(var chunk:net.worldbinder.compat.ReceivedChunks.loaded(client.level))onReplayBlockChange(chunk.getPos().getMinBlockX()>>4,chunk.getPos().getMinBlockZ()>>4);}
            if(client.level!=null&&captureLevel!=client.level){
                if(WorldIdentity.dimensionMatches(activeScene.dimension,WorldIdentity.dimension(client)))captureLevel=client.level;
                else worldBoundary("replay_dimension");
            }
            if(!paused&&!saving)activeScene.replayHighWater=Math.max(activeScene.replayHighWater,playback.millis());
        }
        if(routeReason==null&&captureLevel!=null&&client.level!=null&&captureLevel!=client.level)worldBoundary("level");
        if(routeReason!=null){routeTick(client);return;}
        if(activeScene.replayIdentity!=null&&!captureInputPaused())for(int i=0;i<4&&!replayRefresh.isEmpty()&&queuedChunkKeys.size()<96;i++){
            long key=replayRefresh.removeFirst();
            if(activeScanChunkKeys.contains(key)){replayRefresh.addLast(key);continue;}
            replayRefreshKeys.remove(key);
            if(net.worldbinder.compat.ReceivedChunks.chunk(client.level,ChunkPos.getX(key),ChunkPos.getZ(key))!=null)queueChunkRefresh(ChunkPos.getX(key),ChunkPos.getZ(key),false);
        }

        // A pending or dismissed review is a deliberate pause, including after a disconnect.
        if (saving || awaitingExportReview || exportReviewDeferred) return;

        if (client.level == null || client.player == null) {
            if (finishing || WorldBinder.config().autoSaveOnDisconnect) {
                pendingBlocks.clear();
                pendingChunkKeys.clear();
                queuedChunkKeys.clear();
                clearActiveScans();
                finishing = true;
                OperationStatus.update(net.worldbinder.util.Lang.stored("worldbinder.status.writing_cached_export"), 1.0D);
                saveActiveSceneAsync();
            } else {
                cancelActiveCapture();
            }
            return;
        }

        if (saving) {
            OperationStatus.update(net.worldbinder.util.Lang.stored("worldbinder.status.writing_archive"), 1.0D);
            return;
        }

        if (!WorldIdentity.dimensionMatches(activeScene.dimension, WorldIdentity.dimension(client))
                || !java.util.Objects.equals(activeScene.worldIdentity, WorldIdentity.current(client))) {
            if (!paused) Chat.errorKey("worldbinder.project.wrong_world");
            containerScanner.suspend();
            paused = true;
            return;
        }
        updatePlayerSpawn(client.player);
        if(client.level.getGameTime()%20==0&&!paused)auxiliary.playerSnapshot(activeScene,client);
        maybeApplyMemoryGuard();
        containerScanner.tick(client,activeScene,activeScene.replayIdentity==null&&!captureInputPaused()&&!client.isPaused()&&!saving);

        if (paused && (!finishing || selectionWork!=null)) {
            OperationStatus.update(Lang.stored("worldbinder.capture.paused_status", statusLine()), progress());
            return;
        }

        try { if(selectionWork!=null){
            maybeRecoveryAutosave();processSelection(client);
        } else if (roamingCapture) {
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
        } catch(RuntimeException failure){
            paused=true;containerScanner.suspend();
            if(selectionWork!=null&&selectionWork.tile!=null){long key=ChunkPos.pack(selectionWork.tile.minX()>>4,selectionWork.tile.minZ()>>4);blockStore.discard(key);selectionWork.snapshot.markError(Lang.stored("worldbinder.capture.paused_error",net.worldbinder.validation.Finding.detail(failure)));failedChunkKeys.add(key);selectionWork.clear();processedBlocks=selectionConfirmedBlocks;}
            Chat.errorKey("worldbinder.capture.paused_error",net.worldbinder.validation.Finding.detail(failure));
            WorldBinder.LOGGER.error("Capture processing failed; paused with committed data retained",failure);return;
        }

        if (finishing && !hasPendingWork()) {
            flushLoadedChunksForFinish(client);
            captureNearbyEntities(client, true);
            updatePlayerSpawn(client.player);
            saveActiveSceneAsync();
        } else {
            OperationStatus.update(net.worldbinder.util.Lang.stored("worldbinder.status.message", statusLine()), progress());
        }
    }

    private void startSelectionCapture(String requestedName, String archiveType) {
        Minecraft client = Minecraft.getInstance();
        var replay=net.worldbinder.replay.ReplayPlayback.poll();
        if(replay.present()&&!replay.ready()){Chat.errorKey("worldbinder.replay.not_ready");return;}
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
        net.worldbinder.selection.CaptureBounds requested;
        try{requested=net.worldbinder.selection.CaptureBounds.of(selection);}catch(RuntimeException error){Chat.errorKey("worldbinder.area.invalid");return;}
        if(requested.minY()<client.level.getMinY()||requested.maxY()>=client.level.getMaxY()){Chat.errorKey("worldbinder.selection.height_range",client.level.getMinY(),client.level.getMaxY()-1);return;}
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        beginNewScene(requestedName, archiveType, min);
        activeScene.sizeX = max.getX() - min.getX() + 1;
        activeScene.sizeY = max.getY() - min.getY() + 1;
        activeScene.sizeZ = max.getZ() - min.getZ() + 1;

        activeScene.selectionBounds=requested;
        activeScene.captureSettings.multiDimension=false;
        selectionWork=new SelectionWork(activeScene.selectionBounds);selectionConfirmedBlocks=0;
        scheduledBlocks=selectionWork.bounds.volume();
        finishing = true;
        OperationStatus.begin(net.worldbinder.util.Lang.stored("worldbinder.status.title"), net.worldbinder.util.Lang.stored("worldbinder.status.capturing_selection"));
        Chat.infoKey("worldbinder.chat.position_archive_queued", scheduledBlocks);
    }

    private void beginNewScene(String requestedName, String archiveType, BlockPos origin) {
        Minecraft client = Minecraft.getInstance();
        activeArchiveName = requestedName == null || requestedName.isBlank() ? WorldBinder.config().defaultArchiveName : requestedName.trim();
        activeArchiveType = archiveType;
        activeOrigin = origin.immutable();
        activeScene = new WorldScene();
        activeScene.captureSettings=WorldBinder.config().captureDefaults.copy();
        if(activeScene.captureSettings.liveEntityFilters)activeScene.storageNotes.add(Lang.stored("worldbinder.capture_profile.history"));
        containerScanner=new ContainerScanner();
        selectionWork=null;selectionConfirmedBlocks=0;

        activeScene.name = activeArchiveName;
        activeScene.minecraftVersion = net.minecraft.SharedConstants.getCurrentVersion().name();
        activeScene.archiveType = archiveType;
        activeScene.dimension = WorldIdentity.dimension(client);
        activeScene.worldIdentity = WorldIdentity.current(client);
        if(!java.util.Objects.equals(backendNetwork,activeScene.worldIdentity)){
            currentBackend=null;currentBackendName=null;backendNetwork=activeScene.worldIdentity;
        }
        if(currentBackend==null){currentBackend=java.util.UUID.randomUUID().toString();currentBackendName=activeArchiveName;}
        activeScene.backendId=currentBackend;activeScene.backendName=currentBackendName;
        captureLevel=client.level;readDimensionMetadata(activeScene,client);net.worldbinder.replay.ReplayPlayback.bind(activeScene);
        if(WorldBinder.config().includeServerResourcePack)net.worldbinder.scene.ServerResourcePackLocator.findActiveServerPack().ifPresent(path->{
            activeScene.resourcePackSource=path.toString();activeScene.resourcePackExpected=true;
        });
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

        containerCapture.clear();
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
        originalChunks.clear();
        projectResume = false;
        activeScene.chunkCacheFolder = blockStore.start(activeArchiveName);
        if(activeScene.replayIdentity!=null)auxiliary.attachMaps(activeScene);
        auxiliary.playerSnapshot(activeScene, client);
        if (activeScene.replayIdentity==null&&client.getConnection() != null && WorldBinder.config().exportStats)
            client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                    net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.REQUEST_STATS));
    }

    private void readDimensionMetadata(WorldScene scene,Minecraft client){
        if(client.level==null)return;
        scene.dimensionMinY=client.level.getMinY();scene.dimensionHeight=client.level.getHeight();
        try{
            var holder=client.level.dimensionTypeRegistration();
            scene.dimensionType=holder.unwrapKey().map(k->k.identifier().toString()).orElse(null);
            var ops=client.level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            scene.dimensionTypeNbt=net.minecraft.world.level.dimension.DimensionType.DIRECT_CODEC.encodeStart(ops,holder.value()).result().map(Object::toString).orElse(null);
        }catch(RuntimeException error){WorldBinder.LOGGER.debug("Received dimension type could not be encoded",error);}
    }

    public void rememberProject(WorldScene scene,Path path){
        if(scene.backendId==null||scene.worldIdentity==null)return;
        Minecraft.getInstance().execute(()->{
            var profile=WorldBinder.config().networkProfiles.computeIfAbsent(scene.worldIdentity,k->new net.worldbinder.scene.NetworkProfile());
            var client=Minecraft.getInstance();
            if(java.util.Objects.equals(scene.worldIdentity,WorldIdentity.current(client))){var server=client.getCurrentServer();profile.name=server==null?scene.name:server.name;profile.address=server==null?null:server.ip;}
            var backend=profile.backends.computeIfAbsent(scene.backendId,k->{var b=new net.worldbinder.scene.NetworkProfile.Backend();b.id=k;b.name=scene.backendName;return b;});
            backend.projectPath=path.toAbsolutePath().toString();backend.projectId=scene.projectId;backend.dimensions=net.worldbinder.scene.ProjectDimensions.all(scene).stream().map(s->net.worldbinder.scene.ProjectDimensions.canonical(s.dimension)).filter(java.util.Objects::nonNull).sorted().toList();WorldBinder.config().save();
        });
    }

    public void startAutoCapture(String name,net.worldbinder.scene.CaptureSettings settings){
        startRoamingCapture(name);if(activeScene!=null&&settings!=null)activeScene.captureSettings=settings.copy();
    }

    private void updatePlayerSpawn(Player player) {
        if (activeScene == null || player == null || !captureWorldMatches(Minecraft.getInstance())) {
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
        if(net.worldbinder.replay.ReplayPlayback.state().present())return null;
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
        if (serverSafetyWarningSent || !WorldBinder.config().serverSafetyMode || !net.worldbinder.compat.ClientCompat.multiplayer(client)) {
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
        Path target = net.worldbinder.scene.RecoveryProjects.folder(WorldBinderPaths.RECOVERY_ROOT, source);
        WorldScene frozen;
        java.util.concurrent.CompletableFuture<Map<Long,List<BlockRecord>>> committed;
        Map<Long,List<BlockRecord>> hot=new java.util.LinkedHashMap<>(blockStore.hotChunks());
        Map<Long,Map<String,EntityRecord>> entities=new java.util.LinkedHashMap<>();
        entityStore.hotChunks().forEach((key,values)->entities.put(key,new java.util.LinkedHashMap<>(values)));
        try {
            auxiliary.playerSnapshot(source, Minecraft.getInstance());
            frozen=ProjectData.metadataCopy(source);
            frozen.blocks=List.copyOf(source.blocks);frozen.entities=List.copyOf(source.entities);
            source.chunkSnapshots.forEach((key,value)->frozen.chunkSnapshots.put(key,value.copy()));
            liveChunkSnapshots.forEach((key,value)->{if(!value.isDone())frozen.pendingChunks.put(value.chunkX+","+value.chunkZ,value.copy());});
            committed=blockStore.cache()==null?java.util.concurrent.CompletableFuture.completedFuture(Map.of()):blockStore.cache().snapshotAsync();
        } catch (RuntimeException exception) {
            recoverySaveRunning = false;
            WorldBinder.LOGGER.warn("Recovery snapshot failed; keeping previous recovery", exception);
            return;
        }
        ioExecutor.execute(() -> {
            try {
                WorldScene snapshot=CaptureSceneSnapshotFactory.copyFromCommitted(frozen,committed.join(),hot,entities);
                library.saveRecoverySnapshot(snapshot, target);
                rememberProject(snapshot,target);
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

    private void captureNearbyEntities(Minecraft client, boolean wide) {
        if(!captureWorldMatches(client))return;
        entityStore.captureNearby(client, wide, roamingCapture, activeScene, activeOrigin,
                liveChunkSnapshots, captureInputPaused());
    }

    private void processSelection(Minecraft client) {
        int limit=Math.min(4096,adaptiveBlockLimit(client)),checks=0;
        long deadline=System.nanoTime()+Math.max(1,Math.min(5,adaptiveTickBudgetMillis(client)))*1_000_000L;
        for(int count=0;count<limit&&System.nanoTime()<deadline;){
            if(selectionConfirmedBlocks>=selectionWork.bounds.volume())return;
            if(selectionWork.tile==null){
                if(++checks>32)return;
                var tile=selectionWork.nextTile();int cx=tile.minX()>>4,cz=tile.minZ()>>4;
                var previous=activeScene.chunkSnapshots.get(cx+","+cz);
                long key=ChunkPos.pack(cx,cz);
                if(failedChunkKeys.contains(key))continue;
                if(previous!=null&&!queuedChunkKeys.contains(key)&&net.worldbinder.selection.RecordedCoverage.knownVolume(activeScene,tile)==tile.volume())continue;
                if(!isClientChunkLoaded(client,cx,cz)||tile.minY()<client.level.getMinY()||tile.maxY()>=client.level.getMaxY())continue;
                var chunk=client.level.getChunk(cx,cz);var snapshot=new ChunkSnapshot(cx,cz);
                snapshot.captureMinY=tile.minY();snapshot.captureMaxY=tile.maxY();snapshot.captureBox=tile.array();snapshot.relightRequired=true;snapshot.markScanning();
                blockStore.discard(key);liveChunkSnapshots.put(key,snapshot);
                activeScene.pendingChunks.put(cx+","+cz,snapshot);partialChunkKeys.add(key);
                selectionWork.start(tile,chunk,snapshot);mapDataRevision++;
            }
            var tile=selectionWork.tile;long key=ChunkPos.pack(tile.minX()>>4,tile.minZ()>>4);
            if(!isClientChunkLoaded(client,tile.minX()>>4,tile.minZ()>>4)||client.level.getChunk(tile.minX()>>4,tile.minZ()>>4)!=selectionWork.source){
                blockStore.discard(key);selectionWork.snapshot.markPartial();selectionWork.clear();processedBlocks=selectionConfirmedBlocks;continue;
            }
            captureBlockIntoSnapshot(client,selectionWork.source,selectionWork.position(),selectionWork.snapshot);
            selectionWork.block++;count++;processedBlocks=selectionConfirmedBlocks+selectionWork.block;
            if(selectionWork.block==tile.volume()){
                var snapshot=selectionWork.snapshot;
                completeChunkAfterBlockScan(key,snapshot,selectionWork.source);
                if(snapshot.isDone()){selectionConfirmedBlocks+=tile.volume();partialChunkKeys.remove(key);queuedChunkKeys.remove(key);}
                selectionWork.clear();processedBlocks=selectionConfirmedBlocks;
            }
        }
    }

    @Override public void queueChunkForRescan(int x,int z){
        if(selectionWork==null){super.queueChunkForRescan(x,z);return;}
        if(saving||!projectMatchesCurrentWorld())return;
        var bounds=selectionWork.bounds;
        if(x<(bounds.minX()>>4)||x>(bounds.maxX()>>4)||z<(bounds.minZ()>>4)||z>(bounds.maxZ()>>4))return;
        long index=((long)z-(bounds.minZ()>>4))*((long)(bounds.maxX()>>4)-(bounds.minX()>>4)+1)+x-(bounds.minX()>>4);
        var tile=bounds.chunk(index);long key=ChunkPos.pack(x,z);
        if(queuedChunkKeys.add(key))selectionConfirmedBlocks-=net.worldbinder.selection.RecordedCoverage.knownVolume(activeScene,tile);
        if(selectionWork.tile!=null&&(selectionWork.tile.minX()>>4)==x&&(selectionWork.tile.minZ()>>4)==z){blockStore.discard(key);selectionWork.clear();}
        failedChunkKeys.remove(key);completedChunkKeys.remove(key);var snapshot=new ChunkSnapshot(x,z);snapshot.captureBox=tile.array();snapshot.captureMinY=tile.minY();snapshot.captureMaxY=tile.maxY();snapshot.markQueued("manual-rescan","selection");
        liveChunkSnapshots.put(key,snapshot);activeScene.pendingChunks.put(x+","+z,snapshot);selectionWork.nextChunk=index;processedBlocks=selectionConfirmedBlocks;mapDataRevision++;
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
            if (finishing) return Lang.stored("worldbinder.capture.status.finishing",queuedChunkCount());
            return Lang.stored(paused?"worldbinder.capture.status.world_paused":"worldbinder.capture.status.world");
        }
        return Lang.stored("worldbinder.capture.selection_progress",processedBlocks,scheduledBlocks,Math.max(0,scheduledBlocks-selectionConfirmedBlocks));
    }

    private WorldScene copySceneForIo(WorldScene source, boolean completedOnly, boolean includeRecoveryPartials) {
        source.pendingChunks = new java.util.LinkedHashMap<>();
        for (var entry : liveChunkSnapshots.entrySet()) if (!entry.getValue().isDone()) {
            ChunkSnapshot snapshot = entry.getValue();
            source.pendingChunks.put(snapshot.chunkX + "," + snapshot.chunkZ,
                    snapshot.copy());
        }
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
        continueProject(recoveryFolder,null);
    }
    private void continueProject(Path recoveryFolder,String expectedBackend) {
        Minecraft client = Minecraft.getInstance();
        long requestedEpoch=worldEpoch;
        if (client.level == null || client.player == null) {
            Chat.errorKey("worldbinder.chat.recovery_join_world");
            return;
        }
        if (isCapturing()) {
            Chat.warnKey("worldbinder.chat.recovery_active_capture");
            return;
        }
        if (!Files.exists(recoveryFolder)) {
            Chat.warnKey("worldbinder.chat.recovery_invalid_folder");
            return;
        }
        OperationStatus.begin(net.worldbinder.util.Lang.stored("worldbinder.status.recovery_title"), net.worldbinder.util.Lang.stored("worldbinder.status.recovery_loading_async"));
        Chat.infoKey("worldbinder.chat.recovery_loading_background");
        ioExecutor.execute(() -> {
            try {
                WorldScene scene = library.read(recoveryFolder);
                Minecraft.getInstance().execute(() -> {
                    if(requestedEpoch!=worldEpoch||expectedBackend!=null&&!expectedBackend.equals(scene.backendId)){OperationStatus.finish(Lang.stored("worldbinder.network.identity_mismatch"));Chat.errorKey("worldbinder.network.identity_mismatch");return;}
                    prepareProjectScene(recoveryFolder, scene);
                });
            } catch (Exception exception) {
                Minecraft.getInstance().execute(() -> {
                    OperationStatus.finish(net.worldbinder.util.Lang.stored("worldbinder.status.recovery_load_failed"));
                    Chat.errorKey("worldbinder.chat.recovery_continue_failed");
                });
                WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.recovery.continue_failed"), exception);
            }
        });
    }

    private void prepareProjectScene(Path folder, WorldScene scene) {
        Minecraft client = Minecraft.getInstance();
        if (scene.worldIdentity == null && client.level != null) {
            Screen parent = net.worldbinder.compat.ClientCompat.screen(client);
            net.worldbinder.compat.ClientCompat.setScreen(client, new net.minecraft.client.gui.screens.ConfirmScreen(accepted -> {
                net.worldbinder.compat.ClientCompat.setScreen(client, parent);
                if (accepted) {
                    scene.worldIdentity = WorldIdentity.current(client);
                    scene.dimension = WorldIdentity.dimension(client);
                    scene.storageNotes.add(net.worldbinder.util.Lang.stored("worldbinder.project.legacy_bound"));
                    applyRecoveryScene(folder, scene);
                }
            }, Lang.text("worldbinder.project.bind_title"), Lang.text("worldbinder.project.bind_message")));
        } else {
            if(scene.backendId!=null&&java.util.Objects.equals(scene.worldIdentity,WorldIdentity.current(client))){currentBackend=scene.backendId;currentBackendName=scene.backendName;backendNetwork=scene.worldIdentity;}
            WorldScene selected=scene;
            if(scene.captureSettings.multiDimension&&net.worldbinder.scene.ProjectDimensions.find(scene,WorldIdentity.dimension(client))!=null)
                selected=net.worldbinder.scene.ProjectDimensions.activate(scene,WorldIdentity.dimension(client),null);
            applyRecoveryScene(folder, selected);
        }
    }

    private void applyRecoveryScene(Path recoveryFolder, WorldScene scene) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            OperationStatus.finish(net.worldbinder.util.Lang.stored("worldbinder.status.recovery_load_cancelled"));
            Chat.errorKey("worldbinder.chat.recovery_join_world");
            return;
        }
        if (isCapturing()) {
            OperationStatus.finish(net.worldbinder.util.Lang.stored("worldbinder.status.recovery_load_cancelled"));
            Chat.warnKey("worldbinder.chat.recovery_active_capture");
            return;
        }
        if (scene == null) {
            OperationStatus.finish(net.worldbinder.util.Lang.stored("worldbinder.status.recovery_load_failed"));
            Chat.errorKey("worldbinder.chat.recovery_file_read_failed");
            return;
        }

        String currentIdentity = WorldIdentity.current(client);
        if (scene.worldIdentity == null || !java.util.Objects.equals(scene.worldIdentity, currentIdentity)
                || !WorldIdentity.dimensionMatches(scene.dimension, WorldIdentity.dimension(client))) {
            OperationStatus.finish(net.worldbinder.util.Lang.stored("worldbinder.project.wrong_world"));
            Chat.errorKey("worldbinder.project.wrong_world");
            return;
        }
        originalChunks.clear();
        if(scene.backendId==null){scene.backendId=java.util.UUID.randomUUID().toString();scene.backendName=scene.name;}
        if(!library.isRecovery(recoveryFolder))net.worldbinder.scene.ProjectDimensions.all(scene).forEach(s->s.recordedChunkChanges.clear());
        currentBackend=scene.backendId;currentBackendName=scene.backendName;backendNetwork=scene.worldIdentity;
        scene.blocks.forEach(b -> originalChunks.add(net.worldbinder.scene.ProjectData.chunkKey(scene,b)));
        scene.chunkSnapshots.values().stream().filter(ChunkSnapshot::isDone)
                .forEach(s -> originalChunks.add(ChunkPos.pack(s.chunkX, s.chunkZ)));
        projectResume = true;
        scene.revision++;

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

        containerCapture.clear();
        clearActiveScans();

        scene.chunkCacheFolder = null;
        activeScene = scene;
        if(scene.replayIdentity!=null)net.worldbinder.replay.ReplayPlayback.startContext();
        captureLevel=client.level;readDimensionMetadata(scene,client);
        containerScanner=new ContainerScanner();
        activeArchiveName = scene.name == null || scene.name.isBlank()
                ? recoveryFolder.getFileName().toString().replaceFirst("^_recovery_", "")
                : scene.name;
        scene.chunkCacheFolder = blockStore.resume(scene,
                activeArchiveName == null ? "recovery" : activeArchiveName);
        activeArchiveType = scene.archiveType == null || scene.archiveType.isBlank() ? "world" : scene.archiveType;
        activeOrigin = new BlockPos(scene.originX, scene.originY, scene.originZ);
        roamingCapture = true;
        finishing = false;
        selectionWork=scene.selectionBounds==null?null:new SelectionWork(scene.selectionBounds);
        if(selectionWork!=null){roamingCapture=false;finishing=true;selectionConfirmedBlocks=net.worldbinder.selection.RecordedCoverage.knownVolume(scene,scene.selectionBounds);}
        paused = false;
        saving = false;
        scheduledBlocks = scene.blockCount();
        processedBlocks = scene.blockCount();
        if(selectionWork!=null){scheduledBlocks=selectionWork.bounds.volume();processedBlocks=selectionConfirmedBlocks;}
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
                    if (selectionWork==null && queuedChunkKeys.add(key)) {
                        pendingChunkKeys.add(key);
                    }
                }
            }
        }
        if (scene.pendingChunks != null) for (ChunkSnapshot pending : scene.pendingChunks.values()) {
            long key = ChunkPos.pack(pending.chunkX, pending.chunkZ);
            completedChunkKeys.remove(key);
            liveChunkSnapshots.put(key, pending);
            if (selectionWork==null && queuedChunkKeys.add(key)) pendingChunkKeys.add(key);
            if(selectionWork!=null){
                var bounds=selectionWork.bounds;
                if(pending.chunkX>=(bounds.minX()>>4)&&pending.chunkX<=(bounds.maxX()>>4)&&pending.chunkZ>=(bounds.minZ()>>4)&&pending.chunkZ<=(bounds.maxZ()>>4)){
                    long index=((long)pending.chunkZ-(bounds.minZ()>>4))*((long)(bounds.maxX()>>4)-(bounds.minX()>>4)+1)+pending.chunkX-(bounds.minX()>>4);
                    if(queuedChunkKeys.add(key))selectionConfirmedBlocks-=net.worldbinder.selection.RecordedCoverage.knownVolume(scene,bounds.chunk(index));
                }
            }
        }
        if(selectionWork!=null)processedBlocks=selectionConfirmedBlocks;
        mapDataRevision++;
        OperationStatus.begin(net.worldbinder.util.Lang.stored("worldbinder.status.recovery_title"), net.worldbinder.util.Lang.stored("worldbinder.status.recovery_loaded"));
        Chat.warnKey("worldbinder.chat.recovery_loaded", activeArchiveName);
        if (largeSessionDetected()) {
            Chat.warnKey("worldbinder.chat.recovery_large_session");
        }
        WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored("worldbinder.activity.recovery_continued", activeArchiveName));
    }

    private boolean saveActiveSceneAsync() {
        if (saving || awaitingExportReview || activeScene == null) {
            return false;
        }
        Minecraft current = Minecraft.getInstance();
        if (!exportReviewApproved && current.player != null && current.level != null && !"scene".equals(activeArchiveType)) {
            paused = true;
            finishing = false;
            if(captureWorldMatches(current))auxiliary.playerSnapshot(activeScene, current);
            saving = true; // Also stops packet/tick mutations while the worker freezes the committed scene.
            awaitingExportReview = true;
            exportReviewDeferred = false;
            containerScanner.suspend();
            WorldScene source = activeScene;
            var parent = net.worldbinder.ui.WorldBinderStorageProgressScreen.returnScreen(net.worldbinder.compat.ClientCompat.screen(current));
            StorageFlow.progress().start(WorldBinderPaths.newWorldFolder(activeArchiveName,false), source, false);
            openStorageProgressScreen();
            ioExecutor.execute(() -> {
                try {
                    WorldScene preview = copySceneForIo(source, roamingCapture, false);
                    StorageFlow.progress().describe(preview);
                    String changes=projectChangesText();
                    current.execute(() -> {
                        if(activeScene!=source)return;
                        saving=false;StorageFlow.progress().reviewReady();
                        pendingExportReview=new net.worldbinder.ui.WorldBinderExportScreen(parent,preview,changes,()->{
                            if(activeScene!=source)return;
                            activeScene.exportSettings=preview.exportSettings;activeScene.targetMinecraftVersion=preview.targetMinecraftVersion;
                            preparedExport=preview;awaitingExportReview=false;pendingExportReview=null;
                            exportReviewApproved=true;saveActiveSceneAsync();
                        },()->{
                            if(activeScene!=source)return;
                            awaitingExportReview=false;exportReviewDeferred=true;preparedExport=null;pendingExportReview=null;
                            paused=true;finishing=false;StorageFlow.progress().dismissReview();
                            OperationStatus.update(Lang.stored("worldbinder.status.paused"),progress());
                        });
                        net.worldbinder.compat.ClientCompat.setScreen(current,pendingExportReview);
                    });
                }catch(RuntimeException exception){
                    WorldBinder.LOGGER.error("Cannot prepare committed export review",exception);
                    current.execute(()->{saving=false;awaitingExportReview=false;exportReviewDeferred=true;
                        StorageFlow.progress().fail(Lang.stored("worldbinder.status.message",net.worldbinder.validation.Finding.detail(exception)));
                        Chat.errorKey("worldbinder.chat.prepare_failed");});
                }
            });
            return false;
        }
        exportReviewApproved = false;
        exportReviewDeferred = false;
        saving = true;
        if(preparedExport==null&&captureWorldMatches(Minecraft.getInstance()))auxiliary.playerSnapshot(activeScene, Minecraft.getInstance());
        String name = activeArchiveName;
        String type = activeArchiveType;
        boolean sceneArchive = "scene".equals(type);
        boolean strictCompletedWorldExport = roamingCapture && !sceneArchive;
        BlockRecordChunkCache cacheToClean = blockStore.cache();
        WorldScene source = activeScene;
        WorldScene reviewed = preparedExport;
        preparedExport = null;
        Path target = sceneArchive
                ? WorldBinderPaths.SCENES.resolve(FileNames.archiveFileName(name, WorldBinder.config().appendTimestampToArchiveName))
                : WorldBinderPaths.newWorldFolder(name, WorldBinder.config().appendTimestampToArchiveName);

        OperationStatus.begin(net.worldbinder.util.Lang.stored("worldbinder.status.storage_title"), net.worldbinder.util.Lang.stored("worldbinder.status.storage_preparing"));
        StorageFlow.progress().start(target, reviewed == null ? source : reviewed, current.player == null || current.level == null);
        openStorageProgressScreen();
        ioExecutor.execute(() -> {
            try {
                WorldScene scene = reviewed == null ? copySceneForIo(source, strictCompletedWorldExport, false) : reviewed;
                StorageFlow.progress().describe(scene);
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> {
                    if(activeScene!=source)return;
                    saving = true;
                    OperationStatus.begin(net.worldbinder.util.Lang.stored("worldbinder.status.storage_title"), net.worldbinder.util.Lang.stored("worldbinder.status.storage_writing"));
                    StorageFlow.submitPrepared(library, scene, target, sceneArchive, savedPath -> {
                        rememberProject(scene,savedPath);
                        String validation = library.validationLine(savedPath);
                        client.execute(()->{
                        if(activeScene!=source)return;
                        resetActiveJob();
                        OperationStatus.finish(net.worldbinder.util.Lang.stored("worldbinder.status.saved_counts", scene.blockCount(), scene.entityCount(), validation));
                        Chat.savedArchive(scene.archiveType, scene.name, scene.blockCount(), scene.blockEntityCount(), scene.entityCount(), savedPath);
                        if (!Lang.string("worldbinder.validation.no_report").equals(validation)) {
                            Chat.infoKey("worldbinder.chat.export_validation", validation);
                            WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored("worldbinder.activity.validation_passed", validation));
                        }
                        WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored("worldbinder.activity.saved_archive", scene.name));
                        });
                        if (cacheToClean != null) {
                            cacheToClean.deleteAll();
                        }
                        if (WorldBinder.config().autoDeleteRecovery) ioExecutor.execute(() -> library.cleanupRecovery(scene));
                    }, throwable -> {
                        client.execute(()->{
                        if(activeScene!=source)return;
                        saving = false;
                        paused = true;
                        finishing = false;
                        exportReviewDeferred = true;
                        OperationStatus.finish(net.worldbinder.util.Lang.stored("worldbinder.status.save_failed"));
                        Chat.errorKey("worldbinder.chat.save_failed");
                        });
                    });
                });
            } catch (RuntimeException exception) {
                Minecraft.getInstance().execute(() -> {
                    if(activeScene!=source)return;
                    saving = false;
                    paused=true;finishing=false;exportReviewDeferred=true;
                    StorageFlow.progress().fail(Lang.stored("worldbinder.status.message",net.worldbinder.validation.Finding.detail(exception)));
                    OperationStatus.finish(net.worldbinder.util.Lang.stored("worldbinder.status.save_failed"));
                    Chat.errorKey("worldbinder.chat.prepare_failed");
                });
                WorldBinder.LOGGER.error(Lang.string("worldbinder.log.storage.prepare_archive_failed"), exception);
            }
        });
        return true;
    }

    public String projectChangesText() {
        int added = 0, updated = 0;
        if(activeScene!=null)for(var scope:net.worldbinder.scene.ProjectDimensions.all(activeScene))for(boolean changed:scope.recordedChunkChanges.values()){if(changed)updated++;else added++;}
        return Lang.string("worldbinder.project.changes", added, updated);
    }

    public WorldScene exportPreview() { return activeScene; }
    public java.util.concurrent.CompletableFuture<WorldScene> prepareAreaExport(){
        if(activeScene==null||saving)return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(Lang.string("worldbinder.area.no_source")));
        WorldScene source=activeScene;saving=true;containerScanner.suspend();
        var future=java.util.concurrent.CompletableFuture.supplyAsync(()->copySceneForIo(source,true,false),ioExecutor);
        future.whenComplete((value,error)->Minecraft.getInstance().execute(()->{if(activeScene==source)saving=false;}));
        return future;
    }

    private void openStorageProgressScreen() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        if (net.worldbinder.compat.ClientCompat.screen(client) instanceof net.worldbinder.ui.WorldBinderStorageProgressScreen) {
            return;
        }
        net.worldbinder.compat.ClientCompat.setScreen(client, new net.worldbinder.ui.WorldBinderStorageProgressScreen(net.worldbinder.compat.ClientCompat.screen(client)));
    }

    private void resetActiveJob() {
        replayRefresh.clear();replayRefreshKeys.clear();
        clearRoute();captureLevel=null;replayWasFenced=false;
        awaitingExportReview=false;exportReviewDeferred=false;preparedExport=null;pendingExportReview=null;exportReviewApproved=false;
        activeScene = null;
        activeOrigin = null;
        selectionWork=null;selectionConfirmedBlocks=0;
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

        containerCapture.clear();
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
            snapshot.dropDebugStateData();
            compacted++;
        }
        if (compacted > 0) {
            mapDataRevision++;
            if (urgent) {
                WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored("worldbinder.activity.compacted_snapshots", compacted));
            }
        }
    }
}
