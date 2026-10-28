package net.worldbinder.test;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.ClientCompat;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.scene.*;
import net.worldbinder.ui.*;
import net.worldbinder.validation.ExportValidator;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

/** Real multiplayer, actual HTTP pack download, non-op client command, 2-minute capture and separate process resume. */
final class DedicatedServerClientTest {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
    private static JsonObject control;
    static void run(ClientGameTestContext context,String phase) {
        try {
            control=JsonParser.parseString(Files.readString(Path.of(System.getProperty("worldbinder.qaControl")))).getAsJsonObject();
            String name="server_project_"+Path.of(control.get("root").getAsString()).getFileName().toString().substring(4);
            context.runOnClient(c->{
                c.options.pauseOnLostFocus=false;
                var data=new ServerData("WorldBinder vanilla QA",control.get("host").getAsString(),ServerData.Type.OTHER);
                data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
                ConnectScreen.startConnecting(new TitleScreen(),c,ServerAddress.parseString(data.ip),data,false,null);
            });
            context.waitFor(c->c.player!=null&&c.level!=null&&c.getConnection()!=null&&c.getConnection().hasClientLoaded(),1200);
            context.waitFor(c->textureMatches(c),600);
            check(JsonParser.parseString(Files.readString(Path.of(control.get("root").getAsString()).resolve("ops.json"))).getAsJsonArray().isEmpty(),"Vanilla server has no operators");
            context.runOnClient(c->c.getConnection().sendCommand("worldbinder"));
            context.waitFor(c->ClientCompat.screen(c) instanceof WorldBinderScreen);context.runOnClient(c->ClientCompat.setScreen(c,null));
            Path original=WorldBinderPaths.MINECRAFT_SAVES.resolve(name);
            var commandChecks=new CommandAndStorageChecks();
            while(!context.computeOnClient(c->{try{commandChecks.tick(c,new WorldScene(),original,"en");return commandChecks.commandsDone();}catch(Exception error){throw new AssertionError(error);}}))context.waitTicks(3);
            check(commandChecks.commandsDone(),"Every registered client subcommand runs on the unmodded server without operator rights");
            if(phase.equals("record"))record(context,name,original);else resume(context,name,original);
            WorldBinder.LOGGER.info("WORLDBINDER_DEDICATED_{}_PASS",phase.toUpperCase(Locale.ROOT));
        }catch(Exception exception){throw new AssertionError("Dedicated server "+phase,exception);}
    }
    private static void configure(){WorldBinder.replaceConfig(new WorldBinderConfig());var cfg=WorldBinder.config();cfg.crashRecovery=true;cfg.recoveryAutosaveSeconds=20;cfg.autoDeleteRecovery=false;cfg.appendTimestampToArchiveName=false;
        cfg.targetMinecraftVersion="26.3";cfg.captureMinY=63;cfg.captureMaxY=66;cfg.roamingRadiusChunks=2;cfg.includeServerResourcePack=true;}
    private static void record(ClientGameTestContext context,String name,Path original)throws Exception {
        for(String command:List.of("gamemode creative @a","clear @a","fill -32 63 -32 32 63 32 minecraft:stone",
                "setblock 2 64 2 chest{Items:[{Slot:5b,id:\"minecraft:diamond_pickaxe\",count:1,components:{\"minecraft:damage\":12,\"minecraft:custom_name\":\"Dedicated tool\"}}]}",
                "setblock 4 64 4 barrel","setblock 6 64 6 purple_shulker_box{Items:[{Slot:2b,id:\"minecraft:emerald\",count:4}]}",
                "setblock 12 64 12 ender_chest","item replace entity @a enderchest.3 with minecraft:gold_ingot 5",
                "item replace entity @a hotbar.0 with minecraft:filled_map[minecraft:map_id=7]","experience set @a 12 levels","tp @a 1.5 65 1.5",
                "setblock 7 65 8 stone","summon item_frame 7.5 65.5 7.03125 {Pos:[7.5d,65.5d,7.03125d],block_pos:[I;7,65,7],Fixed:1b,Facing:2b,Item:{id:\"minecraft:filled_map\",count:1,components:{\"minecraft:map_id\":7}}}"))command(command);
        context.waitTicks(25);
        context.runOnClient(c->{configure();check(net.worldbinder.scene.ServerResourcePackLocator.findActiveServerPack().isPresent(),"Active HTTP server pack is identifiable");WorldBinderClient.capture().startRoamingCapture(name);});
        context.waitFor(c->WorldBinderClient.capture().downloadedChunksView().contains(0L));
        open(context,2,64,2);close(context);open(context,4,64,4);close(context);open(context,6,64,6);close(context);open(context,12,64,12);close(context);
        context.waitFor(c->WorldBinderClient.capture().exportPreview().maps.containsKey(7)&&WorldBinderClient.capture().exportPreview().maps.get(7).receivedPixels()==16384,600);
        long begin=System.nanoTime(),peak=0;int lowestFps=Integer.MAX_VALUE;
        for(int step=0;step<30;step++){
            command("tp @a "+(48*(step+1)+.5)+" 65 1.5");context.waitTicks(80);
            long used=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();peak=Math.max(peak,used);
            lowestFps=Math.min(lowestFps,context.computeOnClient(c->c.getFps()));
        }
        command("tp @a 1.5 65 1.5");context.waitTicks(25);
        final int[] chunks={0};context.runOnClient(c->{chunks[0]=WorldBinderClient.capture().chunkSnapshotsView().size();check(chunks[0]>=300,"Long dedicated capture recorded at least 300 chunks");WorldBinderClient.capture().abortQueueAndSaveNow();});
        saveReview(context,true);
        var archive=WorldBinderClient.scenes().read(original);checkData(archive,original);
        Files.writeString(WorldBinderPaths.GAME_DIR.resolve("dedicated-metrics.json"),"{\"recordedChunks\":"+chunks[0]+",\"travelSeconds\":"+((System.nanoTime()-begin)/1_000_000_000L)+",\"peakObservedHeapMiB\":"+(peak/(1024*1024))+",\"minimumSampledFps\":"+lowestFps+"}");
        context.takeScreenshot("dedicated-server-recorded");
        context.runOnClient(c->{ClientCompat.setScreen(c,null);WorldBinderClient.capture().startRoamingCapture(name+"_disconnect");});
        context.waitFor(c->WorldBinderClient.capture().scannedChunks()>0,600);
        disconnect(context);
        context.waitFor(c->!WorldBinderClient.capture().isCapturing()&&!WorldBinderClient.capture().isSaving(),2400);
        Path automatic=WorldBinderPaths.MINECRAFT_SAVES.resolve(name+"_disconnect");var disconnected=WorldBinderClient.scenes().read(automatic);
        check(disconnected.blockCount()>0&&ExportValidator.validate(disconnected,automatic).filesValid,"Disconnect auto-export freezes received chunks and writes a complete, read-back-valid world");
        check(net.worldbinder.storage.StorageFlow.progress().context().disconnected(),"Disconnect export retains its reason and recording counts in the dashboard");
        context.takeScreenshot("dedicated-disconnect-export");
        context.runOnClient(c->ClientCompat.setScreen(c,new TitleScreen()));
    }
    private static void resume(ClientGameTestContext context,String name,Path original)throws Exception {
        var before=WorldBinderClient.scenes().read(original);checkData(before,original);
        context.runOnClient(c->{configure();check(c.player.getUUID().toString().equals(before.player.uuid),"Same own player account after full client restart");WorldBinderClient.capture().continueRecovery(original);});
        context.waitFor(c->WorldBinderClient.capture().isCapturing());command("setblock 1 63 1 air");context.waitFor(c->c.level.getBlockState(new BlockPos(1,63,1)).isAir());
        context.runOnClient(c->WorldBinderClient.capture().queueChunkForRescan(0,0));context.waitFor(c->WorldBinderClient.capture().downloadedChunksView().contains(0L));
        context.runOnClient(c->WorldBinderClient.capture().abortQueueAndSaveNow());saveReview(context,false);
        Path updated=WorldBinderClient.scenes().scenes().stream().filter(p->p.getFileName().toString().startsWith(name)&&!p.equals(original)).findFirst().orElseThrow();
        var archive=WorldBinderClient.scenes().read(updated);checkData(archive,updated);
        check(archive.blocks.stream().noneMatch(b->archive.originX+b.x==1&&archive.originY+b.y==63&&archive.originZ+b.z==1),"Removed block survives multiplayer update/re-export");
        disconnect(context);
        try(var exported=new net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl(context,updated).open()){
            context.waitFor(c->c.player!=null&&c.level!=null);context.waitFor(c->textureMatches(c),600);
            context.waitFor(c->c.level.getMapData(new net.minecraft.world.level.saveddata.maps.MapId(7))!=null,600);
            context.runOnClient(c->{check(c.player.experienceLevel==12,"Own player XP loads offline");check(c.level.getBlockState(new BlockPos(1,63,1)).isAir(),"Updated air loads offline");});
            openLocal(context,2,64,2);context.runOnClient(c->{var menu=((AbstractContainerScreen<?>)ClientCompat.screen(c)).getMenu();check(menu.slots.get(5).getItem().getDamageValue()==12,"Dedicated container item components load offline");});close(context);
            context.takeScreenshot("dedicated-export-opened-pack-and-map");
        }
        Files.writeString(WorldBinderPaths.GAME_DIR.resolve("dedicated-RESULT.txt"),"PASS real unmodded dedicated server on loopback, non-op /worldbinder, HTTP resource pack, opened containers, received locked map art, 2-minute travel capture, full Minecraft process restart, project resume/update/re-export, offline world opened with original pack texture, map and player/container data. External hosting, modpacks and protocol translators are separate.\n");
    }
    private static void saveReview(ClientGameTestContext context,boolean player){context.waitFor(c->ClientCompat.screen(c) instanceof WorldBinderExportScreen,1200);if(player)context.clickScreenButton("Include my player state: Off");context.clickScreenButton("Save and export");context.waitFor(c->!WorldBinderClient.capture().isCapturing()&&!WorldBinderClient.capture().isSaving(),2400);}
    private static void checkData(WorldScene scene,Path folder)throws Exception {
        check(scene.containers.get(ContainerRecord.blockKey(2,64,2)).items.getFirst().contains("Dedicated tool"),"Dedicated full item retained");
        check(scene.containers.get(ContainerRecord.blockKey(4,64,4)).items.isEmpty(),"Dedicated checked-empty barrel retained");
        check(scene.player.enderChest!=null&&!scene.player.enderChest.items.isEmpty(),"Dedicated received ender chest retained");
        check(scene.maps.get(7).receivedPixels()==16384,"Dedicated complete map art persisted");for(int i=0;i<16384;i++)check(scene.maps.get(7).colors[i]==(byte)(i%128),"Dedicated original map-art pixels retained");
        check(scene.resourcePackSha256.equals(control.get("packSHA256").getAsString()),"Original HTTP pack bytes pinned in archive");
        check(ExportValidator.validate(scene,folder).filesValid,"Dedicated export read-back");
    }
    private static boolean textureMatches(net.minecraft.client.Minecraft c){try(var input=c.getResourceManager().getResource(Identifier.parse("minecraft:textures/block/stone.png")).orElseThrow().open()){
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())).equals(control.get("textureSHA256").getAsString());
    }catch(Exception e){return false;}}
    private static void disconnect(ClientGameTestContext context){context.runOnClient(c->c.disconnect(new TitleScreen(),false));context.waitFor(c->c.player==null);}
    private static void open(ClientGameTestContext context,int x,int y,int z)throws Exception {command("tp @a "+(x+.5)+" "+(y+1)+" "+(z+.5));context.waitTicks(6);use(context,x,y,z);}
    private static void openLocal(ClientGameTestContext context,int x,int y,int z){context.runOnClient(c->c.player.setPos(x+.5,y+1,z+.5));context.waitTicks(4);use(context,x,y,z);}
    private static void use(ClientGameTestContext context,int x,int y,int z){context.runOnClient(c->c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(x+.5,y+.5,z+.5),Direction.UP,new BlockPos(x,y,z),false)));context.waitFor(c->ClientCompat.screen(c) instanceof AbstractContainerScreen<?>);context.waitTicks(4);}
    private static void close(ClientGameTestContext context){context.runOnClient(c->c.player.closeContainer());context.waitTicks(3);}
    private static void command(String value)throws Exception {var body=new JsonObject();body.addProperty("command",value);var request=HttpRequest.newBuilder(URI.create(control.get("api").getAsString())).timeout(java.time.Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();check(HTTP.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()==200,"Server console command accepted");}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
