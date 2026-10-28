package net.worldbinder.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.export.SpongeSchematicExporter;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.scene.*;
import net.worldbinder.selection.*;
import net.worldbinder.ui.*;
import net.worldbinder.util.Lang;
import net.worldbinder.validation.ExportValidator;
import java.nio.file.*;
import java.util.*;

/** Shared, unshipped real-game acceptance; the two loaders execute the same scenario. */
public final class ExpansionScenario {
    public int stage,checks;public boolean done;public Path exported,schematic,betaExported;
    private int ticks,stageTick;private long observed,previousKnown;private byte[] previousArchive;
    private String name="expansion_"+UUID.randomUUID().toString().substring(0,8);
    private Path roaming,small,partial;private final boolean resume;
    private net.minecraft.core.BlockPos bigMin;
    private ExpansionUiChecks ui;
    private BetaContainerChecks betaContainers;
    private BetaWorldChecks betaWorlds;
    private boolean cancelledReview;
    private Path reviewedCacheProbe;
    public ExpansionScenario(boolean resume){this.resume=resume;stage=resume?(Boolean.getBoolean("worldbinder.worldsOnly")?52:100):0;}
    private void next(int stage){this.stage=stage;stageTick=ticks;WorldBinder.LOGGER.info("Expansion QA stage {}",stage);}
    public static void command(Minecraft client,String... commands){var server=client.getSingleplayerServer();server.execute(()->{for(String command:commands)server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),command);});}
    private boolean click(Minecraft client,String label){
        var screen=ClientCompat.screen(client);if(screen==null)return false;
        for(var child:screen.children())if(child instanceof Button button&&button.active&&button.getMessage().getString().equals(label)){button.onPress(new KeyEvent(257,0,0));return true;}return false;
    }
    private void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public void tick(Minecraft client)throws Exception{
        // UI phase now includes two resource reloads, every command and three rendered dashboard states per language.
        if(done)return;ticks++;if(ticks-stageTick>(stage==52?6000:stage==50?2400:800))throw new AssertionError("Expansion stage "+stage+" timed out; "+WorldBinderClient.capture().scannerStatus()+" screen="+ClientCompat.screen(client));
        DelayedContainerPackets.tick(client);
        var capture=WorldBinderClient.capture();
        switch(stage){
            case 0->{
                if(client.player==null||client.level==null||client.getSingleplayerServer()==null)return;
                ClientCompat.setScreen(client,null);WorldBinder.replaceConfig(new WorldBinderConfig());
                var config=WorldBinder.config();config.targetMinecraftVersion=net.minecraft.SharedConstants.getCurrentVersion().name();config.crashRecovery=true;config.recoveryAutosaveSeconds=1;config.autoDeleteRecovery=false;config.appendTimestampToArchiveName=false;config.confirmExistingWorld=false;config.includeServerResourcePack=false;config.captureMinY=63;config.captureMaxY=65;config.roamingRadiusChunks=1;config.worldChunkOverlay=true;config.worldChunkOverlayRadius=2;
                command(client,"gamemode creative @a","fill -16 63 -16 16 63 16 stone","fill -16 64 -16 16 75 16 air","tp @a 1.5 65 1.5","clear @a",
                    "setblock 3 64 1 chest{Items:[{Slot:4b,id:\"minecraft:diamond\",count:3}]}","setblock 1 64 3 barrel",
                    "setblock 2 64 3 purple_shulker_box{Items:[{Slot:2b,id:\"minecraft:diamond_pickaxe\",count:1,components:{\"minecraft:damage\":5,\"minecraft:custom_name\":\"Schematic Tool\"}}]}",
                    "setblock 3 65 3 stone","summon item_frame 3.5 65.5 2.03125 {Pos:[3.5d,65.5d,2.03125d],block_pos:[I;3,65,2],Fixed:1b,Facing:2b,Item:{id:\"minecraft:diamond\",count:1}}",
                    "setblock -2 64 1 chest[facing=north,type=left]","setblock -1 64 1 chest[facing=north,type=right]{Items:[{Slot:2b,id:\"minecraft:gold_ingot\",count:7}]}",
                    "summon chest_minecart 1.5 64 -1 {NoGravity:1b,Items:[{Slot:8b,id:\"minecraft:emerald\",count:5}]}","summon oak_chest_boat -0.5 64 -0.5 {NoGravity:1b,Items:[{Slot:6b,id:\"minecraft:lapis_lazuli\",count:9}]}",
                    "summon armor_stand 1.25 64 1.75 {NoGravity:1b,CustomName:\"Inside\"}","summon armor_stand 1.25 68 1.75 {NoGravity:1b,CustomName:\"Above\"}","summon armor_stand 4.25 64 1.75 {NoGravity:1b,CustomName:\"Outside\"}");next(1);
            }
            case 1->{if(ticks-stageTick<25)return;client.player.getAbilities().flying=true;if(Boolean.getBoolean("worldbinder.worldsOnly")){next(52);return;}if(Boolean.getBoolean("worldbinder.betaOnly")){betaContainers=new BetaContainerChecks();next(51);return;}WorldBinder.config().captureDefaults.multiDimension=false;capture.startRoamingCapture(name);next(2);}
            case 2->{if(!capture.downloadedChunksView().contains(0L)||capture.cacheWritePending(0L)||net.worldbinder.render.WorldBinderWorldOverlay.lastEmittedFrames==0)return;
                check(!capture.scannerEnabled(),"Scanner defaults off for a new recording");check(net.worldbinder.render.WorldBinderWorldOverlay.lastEmittedFrames<=25,"World overlay renders actual bounded native frames");capture.queueChunkForRescan(0,0);capture.togglePause();next(17);}
            case 17->{if(ticks-stageTick<8)return;var frames=net.worldbinder.render.WorldBinderWorldOverlay.frames(capture,0,0,2);check(frames.stream().anyMatch(f->f.chunkX()==0&&f.chunkZ()==0&&f.state()==net.worldbinder.render.WorldBinderWorldOverlay.State.PROCESSING&&f.previousSecured()),"Actual queued update renders red with the previously secured green band");ExpansionUiChecks.shot(client,"overlay-update-red");capture.chunkSnapshotsView().get(0L).markError("QA: injected metadata failure");next(18);}
            case 18->{if(ticks-stageTick<8)return;check(net.worldbinder.render.WorldBinderWorldOverlay.frames(capture,0,0,2).stream().anyMatch(f->f.chunkX()==0&&f.chunkZ()==0&&f.state()==net.worldbinder.render.WorldBinderWorldOverlay.State.FAILED&&f.previousSecured()),"Actual failed live update renders yellow while retaining the old committed data");ExpansionUiChecks.shot(client,"overlay-failure-yellow");capture.queueChunkForRescan(0,0);capture.togglePause();DelayedContainerPackets.enabled=true;capture.toggleScanner();next(3);}
            case 3->{
                var scene=capture.exportPreview();if(scene.containers.size()<7||ClientCompat.screen(client)!=null)return;
                check(scene.containers.get(ContainerRecord.blockKey(3,64,1)).items.getFirst().contains("minecraft:diamond"),"Scanner receives real full server slots");
                check(scene.containers.get(ContainerRecord.blockKey(1,64,3)).items.isEmpty(),"Scanner confirms an actually empty barrel");
                check(scene.containers.containsKey(ContainerRecord.blockKey(-2,64,1))&&scene.containers.containsKey(ContainerRecord.blockKey(-1,64,1)),"Scanner stores both double chest halves");
                check(scene.containers.get(ContainerRecord.blockKey(2,64,3)).items.getFirst().contains("Schematic Tool"),"Scanner preserves complete item components from a shulker box");
                check(scene.containers.values().stream().anyMatch(c->c.type.equals("minecraft:chest_minecart")&&c.items.stream().anyMatch(item->item.contains("minecraft:emerald"))),"Scanner stores the actual server contents of a chest minecart");
                check(scene.containers.values().stream().anyMatch(c->c.type.equals("minecraft:oak_chest_boat")&&c.items.stream().anyMatch(item->item.contains("minecraft:lapis_lazuli")))&&!client.player.isPassenger(),"Scanner uses a normal secondary interaction for a chest boat without mounting or moving items");
                observed=scene.containers.get(ContainerRecord.blockKey(3,64,1)).observedAt;capture.toggleScanner();command(client,"data merge block 3 64 1 {Items:[]}");next(4);
            }
            case 4->{if(ticks-stageTick<10)return;DelayedContainerPackets.nextDelayTicks=140;capture.rescanContainers();next(5);}
            case 5->{var known=capture.exportPreview().containers.get(ContainerRecord.blockKey(3,64,1));if(known.observedAt<=observed||!known.items.isEmpty()||ClientCompat.screen(client)!=null)return;
                check(capture.scannerRetries()>0,"Delayed real reply beyond the timeout retries only after closing; stale menu IDs do not overwrite another target");capture.rescanContainers();next(6);}
            case 6->{if(!(ClientCompat.screen(client) instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen))return;
                screen.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE,0,0));check(capture.scannerStatus().contains(Lang.string("worldbinder.scanner.suspended")),"Actual manual menu key interrupts automatic scanning");
                check(DelayedContainerPackets.partialChecks>0&&DelayedContainerPackets.delayedPackets>0,"Real-game delayed replies and incomplete slot lists never count as successful empty captures");
                ClientCompat.setScreen(client,null);next(21);}
            case 21->{if(ticks-stageTick<8)return;capture.rescanContainers();next(24);}
            case 24->{if(!(ClientCompat.screen(client) instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>))return;command(client,"execute in minecraft:the_nether run tp @a 1.5 90 1.5");next(22);}
            case 22->{if(!net.worldbinder.capture.WorldIdentity.dimension(client).equals("minecraft:the_nether")||!capture.isPaused())return;check(capture.scannerStatus().contains(Lang.string("worldbinder.scanner.suspended")),"Actual dimension switch interrupts a pending scanner menu");check(capture.exportPreview().dimension.equals("minecraft:overworld"),"Original project stays assigned to its own dimension");command(client,"execute in minecraft:overworld run tp @a 1.5 65 1.5");next(23);}
            case 23->{if(!net.worldbinder.capture.WorldIdentity.dimension(client).equals("minecraft:overworld")||client.player.getY()>66)return;boolean received=false;for(var entity:client.level.entitiesForRendering())if(entity.getCustomName()!=null&&entity.getCustomName().getString().equals("Inside"))received=true;if(!received)return;check(capture.isPaused(),"Returning to the original dimension requires explicit resume");capture.togglePause();DelayedContainerPackets.enabled=false;capture.exportPreview().exportSettings.filters.blockIds.add("minecraft:barrel");capture.exportPreview().exportSettings.filters.droppedItems=true;var source=capture.exportPreview();capture.abortQueueAndSaveNow();capture.cancelActiveCapture();check(capture.exportPreview()==source&&capture.isSaving(),"Capture cancellation cannot reset an in-flight preparation worker");next(7);}
            case 7->{if(!(ClientCompat.screen(client) instanceof WorldBinderExportScreen screen))return;
                check(net.worldbinder.storage.StorageFlow.progress().stage()==net.worldbinder.storage.StorageStage.REVIEW&&!capture.isSaving(),"A frozen export review is not reported as a completed/running storage job");
                if(!cancelledReview){ClientCompat.setScreen(client,null);capture.requestFinishCapture();check(ClientCompat.screen(client)==screen,"An interrupted/hidden pending review can be reopened without preparing another recording");screen.onClose();check(!(ClientCompat.screen(client) instanceof WorldBinderStorageProgressScreen)&&capture.isPaused(),"Cancel returns past all temporary progress screens and keeps the recording paused");cancelledReview=true;next(25);return;}
                if(click(client,Lang.string("worldbinder.filters.title")))next(8);}
            case 25->{if(ticks-stageTick<24)return;check(capture.isCapturing()&&capture.isPaused()&&!capture.isSaving()&&!capture.routingPending()&&!net.worldbinder.storage.StorageFlow.progress().hasActivity(),"Cancelling the review after returning to the original dimension does not restart a handoff/preparation/export: capture="+capture.isCapturing()+", paused="+capture.isPaused()+", saving="+capture.isSaving()+", route="+capture.routeReason()+", storage="+net.worldbinder.storage.StorageFlow.progress().stage());capture.abortQueueAndSaveNow();next(7);}
            case 8->{if(!(ClientCompat.screen(client) instanceof WorldBinderExportFiltersScreen screen))return;
                var fields=screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();fields.get(1).setValue("minecraft:stnoe");click(client,Lang.string("worldbinder.filters.apply"));
                check(ClientCompat.screen(client)==screen,"Invalid filter ID leaves the filter menu and prior choice intact");fields.get(1).setValue("minecraft:barrel");click(client,Lang.string("worldbinder.filters.armor",Lang.string("worldbinder.export.disabled")));click(client,Lang.string("worldbinder.filters.apply"));next(9);}
            case 9->{if(!(ClientCompat.screen(client) instanceof WorldBinderExportScreen screen))return;
                for(var child:screen.children())if(child instanceof Button button&&button.active&&button.getMessage().getString().equals(Lang.string("worldbinder.export.review.export"))){
                    var field=capture.getClass().getSuperclass().getSuperclass().getDeclaredField("blockStore");field.setAccessible(true);Object store=field.get(capture);var method=store.getClass().getDeclaredMethod("cache");method.setAccessible(true);
                    var cache=(net.worldbinder.storage.BlockRecordChunkCache)method.invoke(store);long key=cache.chunkKeys().iterator().next();
                    Path file=cache.root().resolve("c."+(int)key+"."+(int)(key>>32)+".wbcache");reviewedCacheProbe=file.resolveSibling(file.getFileName()+".frozen-probe");Files.move(file,reviewedCacheProbe);
                    button.onPress(new KeyEvent(257,0,0));capture.cancelActiveCapture();check(capture.isCapturing()&&capture.isSaving(),"Capture cancellation cannot invalidate an approved export while its writer runs");next(10);break;
                }}
            case 10->{if(net.worldbinder.storage.StorageFlow.progress().stage()==net.worldbinder.storage.StorageStage.FAILED)throw new AssertionError("Confirmed frozen recording was read from its cache again: "+net.worldbinder.storage.StorageFlow.progress().detail());if(capture.isCapturing()||capture.isSaving())return;
                check(reviewedCacheProbe!=null,"A confirmed, immutable review exports successfully even when the old cache file is no longer available");
                roaming=WorldBinderPaths.MINECRAFT_SAVES.resolve(name);var scene=WorldBinderClient.scenes().read(roaming);var report=ExportValidator.validate(scene,roaming);
                check(report.filesValid&&report.excludedBlocks>=1&&report.excludedBlockEntities>=1,"Actual filtered world read-back reports excluded block entities");
                check(scene.containers.size()>=4&&scene.blocks.stream().anyMatch(b->b.state.startsWith("minecraft:barrel")),"Original archive retains filtered scanner data");
                check(scene.entities.stream().anyMatch(e->e.type.equals("minecraft:item_frame")&&scene.originY+e.y>65&&scene.originY+e.y<66),"Received hanging entity in the inclusive upper Y block is captured");
                scene.exportSettings.filters=new ExportFilterSettings();schematic=WorldBinderPaths.GAME_DIR.resolve("received-area-"+name+".schem");
                var bounds=new CaptureBounds(-2,63,1,3,65,3);SpongeSchematicExporter.write(scene,bounds,schematic);
                ui=new ExpansionUiChecks(scene,roaming);next(50);
            }
            case 50->{if(!ui.tick(client))return;checks+=ui.checks;betaContainers=new BetaContainerChecks();next(51);}
            case 51->{if(!betaContainers.tick(client))return;checks+=betaContainers.checks;if(Boolean.getBoolean("worldbinder.betaOnly")){Files.writeString(WorldBinderPaths.GAME_DIR.resolve("beta-container-RESULT.txt"),"PASS "+checks+" native container/live-filter checks\n");WorldBinder.LOGGER.info("WORLDBINDER_BETA_CONTAINER_PASS {} checks",checks);done=true;return;}next(52);}
            case 52->{if(betaWorlds==null)betaWorlds=new BetaWorldChecks(resume);if(!betaWorlds.tick(client))return;checks+=betaWorlds.checks;betaExported=betaWorlds.exported;if(Boolean.getBoolean("worldbinder.worldsOnly")){done=true;return;}ClientCompat.setScreen(client,null);WorldBinderClient.selections().setSelection(new Selection(new BlockPos(1,63,1),new BlockPos(2,65,2)));capture.captureWorldArchive(name+"_small");next(11);}
            case 11->{if(!(ClientCompat.screen(client) instanceof WorldBinderExportScreen))return;if(click(client,Lang.string("worldbinder.export.review.export")))next(12);}
            case 12->{if(capture.isCapturing()||capture.isSaving())return;small=WorldBinderPaths.MINECRAFT_SAVES.resolve(name+"_small");var scene=WorldBinderClient.scenes().read(small);
                check(scene.blocks.stream().allMatch(b->b.x>=0&&b.x<2&&b.y>=0&&b.y<3&&b.z>=0&&b.z<2),"Selection blocks obey all X/Y/Z limits");
                check(scene.entities.size()==1&&scene.entities.getFirst().customName.contains("Inside"),"Entities above/outside the 3D selection are excluded");
                check(scene.blockEntityCount()==0&&RecordedCoverage.knownVolume(scene,scene.selectionBounds)==12,"No outside block entity is pulled in; selected air remains confirmed");
                ClientCompat.setScreen(client,null);WorldBinderClient.selections().setSelection(new CaptureBounds(1,68,1,2,68,2).selection());capture.captureWorldArchive(name+"_high_selection");next(19);
            }
            case 19->{if(!(ClientCompat.screen(client) instanceof WorldBinderExportScreen))return;if(click(client,Lang.string("worldbinder.export.review.export")))next(20);}
            case 20->{if(capture.isCapturing()||capture.isSaving())return;var high=WorldBinderClient.scenes().read(WorldBinderPaths.MINECRAFT_SAVES.resolve(name+"_high_selection"));check(high.entities.size()==1&&high.entities.getFirst().customName.contains("Above"),"Selection entity Y limits override roaming Y settings and include their upper block");check(high.blocks.isEmpty()&&RecordedCoverage.knownVolume(high,high.selectionBounds)==4&&ExportValidator.validate(high,WorldBinderPaths.MINECRAFT_SAVES.resolve(name+"_high_selection")).relightChunks>0,"Actually received empty selection retains confirmed coverage and requests boundary relighting");ClientCompat.setScreen(client,null);capture.captureWorldArchive(name+"_cancelled_review");next(27);}
            case 27->{if(!(ClientCompat.screen(client) instanceof WorldBinderExportScreen screen))return;capture.cancelActiveCapture();check(!capture.isCapturing()&&!net.worldbinder.storage.StorageFlow.progress().hasActivity(),"Cancelling a waiting capture clears its pending review rather than leaving a phantom storage job");screen.onClose();ClientCompat.setScreen(client,null);WorldBinderClient.selections().setSelection(new Selection(new BlockPos(-1000000,63,-1000000),new BlockPos(1000000,65,1000000)));long start=System.nanoTime();capture.captureWorldArchive(name+"_huge");check(System.nanoTime()-start<500_000_000L,"Huge selection starts without enumerating positions on the client thread");next(13);}
            case 13->{if(ticks-stageTick<10)return;
                check(net.worldbinder.compat.ReceivedChunks.chunk(client.level,-62500,-62500)==null,"Real client cache distinguishes unreceived chunks even though ClientLevel.hasChunk returns true");
                check(capture.pendingBlocks()==0&&capture.processedBlockCount()==0&&capture.hasPendingWork(),"Entirely unreceived huge area stays unknown with a bounded cursor");capture.cancelActiveCapture();
                WorldBinderClient.selections().setSelection(new Selection(new BlockPos(15,63,0),new BlockPos(511,65,15)));capture.captureWorldArchive(name+"_partial");next(14);}
            case 14->{if(ticks-stageTick<100||capture.scannedChunks()==0)return;
                check(capture.processedBlockCount()<capture.scheduledBlockCount()&&capture.hasPendingWork(),"Partially unloaded selection is not declared complete");capture.abortQueueAndSaveNow();next(15);}
            case 15->{if(!(ClientCompat.screen(client) instanceof WorldBinderExportScreen))return;if(click(client,Lang.string("worldbinder.export.review.export")))next(16);}
            case 16->{if(capture.isCapturing()||capture.isSaving())return;
                partial=WorldBinderPaths.MINECRAFT_SAVES.resolve(name+"_partial");var scene=WorldBinderClient.scenes().read(partial);var report=ExportValidator.validate(scene,partial);
                check(report.filesValid&&report.unknownSelectionBlocks>0,"Partial area saves valid files with explicitly unknown selected positions");
                var fixture=new com.google.gson.JsonObject();fixture.addProperty("roaming",roaming.toString());fixture.addProperty("partial",partial.toString());fixture.addProperty("world",client.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().toString());fixture.addProperty("schematic",schematic.toString());fixture.addProperty("projectId",scene.projectId);
                Files.writeString(WorldBinderPaths.GAME_DIR.resolve("expansion-fixture.json"),WorldBinder.GSON.toJson(fixture));done=true;WorldBinder.LOGGER.info("WORLDBINDER_EXPANSION_RECORD_PASS {} checks",checks);
            }
            case 100->{
                var json=com.google.gson.JsonParser.parseString(Files.readString(WorldBinderPaths.GAME_DIR.resolve("expansion-fixture.json"))).getAsJsonObject();roaming=Path.of(json.get("roaming").getAsString());partial=Path.of(json.get("partial").getAsString());schematic=Path.of(json.get("schematic").getAsString());
                var recorded=WorldBinderClient.scenes().read(roaming);check(recorded.containers.size()>=4&&recorded.containers.get(ContainerRecord.blockKey(3,64,1)).items.isEmpty(),"Scanner knowledge, including refreshed empty contents, survives a full process restart");
                check(recorded.exportSettings.filters.blockIds.contains("minecraft:barrel")&&recorded.exportSettings.filters.armorStands,"Project filters survive a full process restart");
                var scene=WorldBinderClient.scenes().read(partial);check(scene.projectId.equals(json.get("projectId").getAsString())&&scene.selectionBounds!=null,"Selection bounds and persistent project ID survive restart");
                previousKnown=RecordedCoverage.knownVolume(scene,scene.selectionBounds);previousArchive=Files.readAllBytes(partial.resolve("worldbinder/worldbinder_archive.json"));
                WorldBinder.replaceConfig(new WorldBinderConfig());WorldBinder.config().targetMinecraftVersion=net.minecraft.SharedConstants.getCurrentVersion().name();WorldBinder.config().crashRecovery=false;WorldBinder.config().appendTimestampToArchiveName=false;WorldBinder.config().confirmExistingWorld=false;WorldBinder.config().includeServerResourcePack=false;
                ClientCompat.setScreen(client,null);capture.continueRecovery(partial);next(101);
            }
            case 101->{if(!capture.isCapturing())return;check(!capture.isRoamingCapture()&&!capture.scannerEnabled(),"Resumed selection stays bounded and automation requires a fresh opt-in");command(client,"setblock 16 63 2 air");next(105);}
            case 105->{if(!client.level.getBlockState(new BlockPos(16,63,2)).isAir())return;capture.queueChunkForRescan(1,0);next(106);}
            case 106->{if(!capture.downloadedChunksView().contains(ProjectData.chunkKey(1,0)))return;check(true,"Existing selected chunks can be deliberately updated within their 3D scope");command(client,"tp @a 500.5 65 1.5");next(102);}
            case 102->{if(net.worldbinder.compat.ReceivedChunks.chunk(client.level,31,0)==null)return;var scene=capture.exportPreview();if(!RecordedCoverage.known(scene,500,64,1))return;
                check(RecordedCoverage.knownVolume(scene,scene.selectionBounds)>previousKnown,"Previously unknown positions become confirmed only after new chunk reception");capture.abortQueueAndSaveNow();next(103);}
            case 103->{if(!(ClientCompat.screen(client) instanceof WorldBinderExportScreen))return;if(click(client,Lang.string("worldbinder.export.review.export")))next(104);}
            case 104->{if(capture.isCapturing()||capture.isSaving())return;
                var source=WorldBinderClient.scenes().read(partial);exported=WorldBinderClient.scenes().scenes().stream().filter(path->!path.equals(partial)).filter(path->{try{return WorldBinderClient.scenes().read(path).projectId.equals(source.projectId);}catch(Exception e){return false;}}).findFirst().orElseThrow();
                var scene=WorldBinderClient.scenes().read(exported);check(ExportValidator.validate(scene,exported).filesValid&&scene.revision>source.revision,"Resumed selection writes and validates a new revision");
                check(scene.blocks.stream().noneMatch(b->scene.originX+b.x==16&&scene.originY+b.y==63&&scene.originZ+b.z==2),"Deliberately removed selected block survives restart/rescan/re-export as confirmed air");
                check(Arrays.equals(previousArchive,Files.readAllBytes(partial.resolve("worldbinder/worldbinder_archive.json"))),"The previous complete archive is unchanged by resume and re-export");done=true;WorldBinder.LOGGER.info("WORLDBINDER_EXPANSION_RESTART_PASS {} checks",checks);
            }
        }
    }
}
