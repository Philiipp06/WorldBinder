package net.worldbinder.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.scene.*;
import net.worldbinder.ui.*;
import net.worldbinder.util.Lang;
import java.nio.file.*;
import java.util.*;

/** Real integrated-world dimension changes; backend reassignment here is manual, not a claimed proxy test. */
final class BetaWorldChecks {
    private final boolean resume;
    private int stage,ticks,since;int checks;
    private String name,project,backend,backendName,lobby;
    private Path original;
    Path exported;
    private byte[] oldBytes;
    private CaptureSettings policy;
    BetaWorldChecks(boolean resume){this.resume=resume;stage=resume?100:0;}
    private void next(int value){stage=value;since=ticks;WorldBinder.LOGGER.info("Beta world QA stage {}",value);}
    private void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    private boolean click(Minecraft client,String label){var screen=ClientCompat.screen(client);if(screen==null)return false;for(var child:screen.children())if(child instanceof Button b&&b.active&&b.visible&&b.getMessage().getString().equals(label)){b.onPress(new KeyEvent(257,0,0));return true;}return false;}
    private boolean has(WorldScene scene,String id){return scene.blocks.stream().anyMatch(b->scene.originX+b.x==1&&scene.originY+b.y==64&&scene.originZ+b.z==1&&b.state.equals(id));}
    private void cycle(Minecraft client,String dimension){if(!click(client,Lang.string("worldbinder.dimensions.view",dimension)))throw new AssertionError("Missing native dimension selector for "+dimension);}
    boolean tick(Minecraft client)throws Exception{
        ticks++;if(ticks-since>1200)throw new AssertionError("Beta world stage "+stage+" timed out: "+WorldBinderClient.capture().finishStatusLine()+" screen="+ClientCompat.screen(client));
        var capture=WorldBinderClient.capture();
        switch(stage){
            case 0->{
                policy=WorldBinder.config().captureDefaults.copy();WorldBinder.config().captureDefaults.multiDimension=true;
                WorldBinder.config().appendTimestampToArchiveName=false;WorldBinder.config().confirmExistingWorld=false;WorldBinder.config().autoCaptureOnJoin=false;
                name="beta_worlds_"+UUID.randomUUID().toString().substring(0,8);ClientCompat.setScreen(client,null);
                ExpansionScenario.command(client,"setblock 1 64 1 stone","setblock 3 64 1 chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:2}]}","tp @a 1.5 65 1.5");
                var server=client.getSingleplayerServer();server.execute(()->{var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);nether.getChunk(0,0);nether.setBlock(new BlockPos(1,64,1),Blocks.GOLD_BLOCK.defaultBlockState(),3);});next(1);
            }
            case 1->{if(ticks-since<12)return false;capture.startRoamingCapture(name);project=capture.exportPreview().projectId;backend=capture.exportPreview().backendId;backendName=capture.exportPreview().backendName;next(2);}
            case 2->{if(!capture.downloadedChunksView().contains(0L)||capture.cacheWritePending(0L))return false;
                client.gameMode.useItemOn(client.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(new BlockPos(3,64,1)),net.minecraft.core.Direction.UP,new BlockPos(3,64,1),false));next(3);}
            case 3->{if(capture.containerKnownSlots()!=27)return false;client.player.closeContainer();capture.toggleScanner();ExpansionScenario.command(client,"execute in minecraft:the_nether run tp @a 1.5 65 1.5");next(4);}
            case 4->{var scene=capture.exportPreview();if(scene==null||capture.routingPending()||!scene.dimension.equals("minecraft:the_nether"))return false;
                check(scene.projectId.equals(project)&&scene.backendId.equals(backend)&&scene.dimensions.containsKey("minecraft:overworld"),"Actual Overworld -> Nether transition keeps one project with separate coordinate spaces");
                var overworld=scene.dimensions.get("minecraft:overworld");check(has(overworld,"minecraft:stone")&&overworld.containers.get("block:3,64,1").items.getFirst().contains("diamond"),"Handoff keeps committed blocks and received container contents from the previous dimension");
                check(!capture.scannerEnabled(),"A scanner active during dimension handoff does not send openings into the new world");ExpansionScenario.command(client,"execute in minecraft:the_nether run setblock 1 64 1 gold_block");next(5);}
            case 5->{if(!client.level.getBlockState(new BlockPos(1,64,1)).is(Blocks.GOLD_BLOCK))return false;capture.queueChunkForRescan(0,0);next(17);}
            case 17->{if(!capture.downloadedChunksView().contains(0L)||capture.cacheWritePending(0L))return false;WorldBinder.config().map3d=false;ClientCompat.setScreen(client,new WorldBinderMapScreen(null));next(6);}
            case 6->{if(ticks-since<8)return false;cycle(client,"minecraft:the_nether");next(7);}
            case 7->{if(ticks-since<8)return false;var map=(WorldBinderMapScreen)ClientCompat.screen(client);var method=WorldBinderMapScreen.class.getDeclaredMethod("liveView");method.setAccessible(true);check(!(boolean)method.invoke(map),"Actual 2D dimension selection excludes current-world exploration and markers");
                int queued=capture.queuedChunks();method=WorldBinderMapScreen.class.getDeclaredMethod("queueSelectedRescan");method.setAccessible(true);method.invoke(map);check(capture.queuedChunks()==queued,"An inactive map dimension cannot queue a rescan in the current world");ExpansionUiChecks.shot(client,"dimensions-2d");WorldBinder.config().map3d=true;ClientCompat.setScreen(client,new WorldBinderMapScreen(null));cycle(client,"minecraft:the_nether");next(8);}
            case 8->{if(ticks-since<30||!net.worldbinder.render.Map3DView.shared().hasFrame())return false;ExpansionUiChecks.shot(client,"dimensions-3d");check(capture.exportPreview().dimension.equals("minecraft:the_nether"),"Browsing the archived 3D dimension never switches the active recording");ClientCompat.setScreen(client,null);ExpansionScenario.command(client,"execute in minecraft:overworld run tp @a 1.5 65 1.5");next(9);}
            case 9->{var scene=capture.exportPreview();if(capture.routingPending()||!scene.dimension.equals("minecraft:overworld"))return false;check(scene.projectId.equals(project)&&has(scene.dimensions.get("minecraft:the_nether"),"minecraft:gold_block"),"Actual Nether -> Overworld return reuses both independent saved states");capture.worldBoundary("manual");next(10);}
            case 10->{if(!capture.routingReady()||!(ClientCompat.screen(client) instanceof WorldBinderNetworkScreen))return false;for(var child:ClientCompat.screen(client).children())if(child instanceof EditBox input)input.setValue("Lobby QA");if(!click(client,Lang.string("worldbinder.network.new")))return false;
                lobby=capture.exportPreview().backendId;check(!lobby.equals(backend)&&!capture.exportPreview().projectId.equals(project)&&capture.exportPreview().dimensions.isEmpty(),"Manual ambiguous-backend assignment creates a separate project despite identical dimension IDs and chunk coordinates");ExpansionScenario.command(client,"setblock 1 64 1 diamond_block");next(11);}
            case 11->{if(!client.level.getBlockState(new BlockPos(1,64,1)).is(Blocks.DIAMOND_BLOCK))return false;capture.queueChunkForRescan(0,0);next(12);}
            case 12->{if(!capture.downloadedChunksView().contains(0L)||capture.cacheWritePending(0L))return false;ExpansionScenario.command(client,"setblock 1 64 1 stone");capture.worldBoundary("manual");next(13);}
            case 13->{if(!capture.routingReady()||!(ClientCompat.screen(client) instanceof WorldBinderNetworkScreen))return false;if(click(client,backendName))next(14);}
            case 14->{if(capture.routingPending()||capture.isSaving()||!capture.exportPreview().backendId.equals(backend))return false;
                check(capture.exportPreview().projectId.equals(project)&&capture.exportPreview().dimensions.containsKey("minecraft:the_nether"),"Returning to an explicitly chosen backend reloads its own project and dimensions");
                var profile=capture.networkProfile();var lobbyScene=WorldBinderClient.scenes().read(Path.of(profile.backends.get(lobby).projectPath));check(has(lobbyScene,"minecraft:diamond_block"),"The separate backend recovery retains its own block at the same coordinates");capture.requestFinishCapture();next(15);}
            case 15->{if(ClientCompat.screen(client) instanceof WorldBinderFinishScreen){click(client,Lang.string("worldbinder.finish.save_now"));return false;}if(!(ClientCompat.screen(client) instanceof WorldBinderExportScreen))return false;if(click(client,Lang.string("worldbinder.export.review.export")))next(16);}
            case 16->{if(capture.isCapturing()||capture.isSaving())return false;exported=WorldBinderPaths.MINECRAFT_SAVES.resolve(name);var saved=WorldBinderClient.scenes().read(exported);check(saved.dimensions.size()==1&&saved.projectId.equals(project),"The complete native capture writes one archive containing both dimensions");
                var fixture=new com.google.gson.JsonObject();fixture.addProperty("world",client.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().toString());fixture.addProperty("export",exported.toString());fixture.addProperty("project",project);fixture.addProperty("backend",backend);
                Files.writeString(WorldBinderPaths.GAME_DIR.resolve("beta-worlds-fixture.json"),WorldBinder.GSON.toJson(fixture));if(Boolean.getBoolean("worldbinder.worldsOnly"))Files.writeString(WorldBinderPaths.GAME_DIR.resolve("expansion-fixture.json"),WorldBinder.GSON.toJson(fixture));
                WorldBinder.config().captureDefaults=policy;var profile=capture.networkProfile();profile.autoCapture=true;profile.joinBackend=backend;WorldBinder.config().save();WorldBinder.LOGGER.info("WORLDBINDER_BETA_WORLDS_RECORD_PASS {} checks",checks);return true;}
            case 100->{var fixture=com.google.gson.JsonParser.parseString(Files.readString(WorldBinderPaths.GAME_DIR.resolve("beta-worlds-fixture.json"))).getAsJsonObject();original=Path.of(fixture.get("export").getAsString());project=fixture.get("project").getAsString();backend=fixture.get("backend").getAsString();oldBytes=Files.readAllBytes(original.resolve("worldbinder/worldbinder_archive.json"));
                var saved=WorldBinderClient.scenes().read(original);check(saved.dimensions.size()==1&&saved.backendId.equals(backend),"Multi-dimension identity, containers and settings survive a complete Minecraft process restart");next(101);}
            case 101->{if(!capture.isCapturing())return false;check(capture.exportPreview().projectId.equals(project)&&capture.exportPreview().dimensions.size()==1,"Automatic capture after a real world join resumes the explicitly associated backend project");
                var profile=capture.networkProfile();profile.autoCapture=false;WorldBinder.config().autoCaptureOnJoin=false;WorldBinder.config().save();capture.requestFinishCapture();next(102);}
            case 102->{if(ClientCompat.screen(client) instanceof WorldBinderFinishScreen){click(client,Lang.string("worldbinder.finish.save_now"));return false;}if(!(ClientCompat.screen(client) instanceof WorldBinderExportScreen))return false;if(click(client,Lang.string("worldbinder.export.review.export")))next(103);}
            case 103->{if(capture.isCapturing()||capture.isSaving())return false;exported=WorldBinderClient.scenes().scenes().stream().filter(path->!path.equals(original)).filter(path->{try{return WorldBinderClient.scenes().read(path).projectId.equals(project);}catch(Exception e){return false;}}).findFirst().orElseThrow();
                check(Arrays.equals(oldBytes,Files.readAllBytes(original.resolve("worldbinder/worldbinder_archive.json"))),"Automatic project continuation exports a new revision without changing the previous complete world");WorldBinder.LOGGER.info("WORLDBINDER_BETA_WORLDS_RESTART_PASS {} checks",checks);return true;}
        }return false;
    }
}
