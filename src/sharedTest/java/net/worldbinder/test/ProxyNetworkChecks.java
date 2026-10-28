package net.worldbinder.test;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.compat.*;
import net.worldbinder.config.WorldBinderConfig;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.scene.*;
import net.worldbinder.ui.*;
import net.worldbinder.util.Lang;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

/** Real original proxies, same dimension/coordinates, full process restart and supported native transfer. */
final class ProxyNetworkChecks {
    private static JsonObject control;private static int checks;
    private interface Read<T>{T get(Minecraft c)throws Exception;}
    private static <T>T client(NativeClientContext context,Read<T> read){var value=new java.util.concurrent.atomic.AtomicReference<T>();context.runOnClient(c->{try{value.set(read.get(c));}catch(Exception error){throw new AssertionError(error);}});return value.get();}
    private static void waitFor(NativeClientContext context,Read<Boolean> condition,String label){WorldBinder.LOGGER.info("Proxy QA waiting: {}",label);for(int i=0;i<1800;i++){if(client(context,condition))return;context.waitTicks(1);}throw new AssertionError("Proxy QA timeout: "+label+" route="+client(context,c->WorldBinderClient.capture().routeReason())+" screen="+client(context,ClientCompat::screen));}
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void command(String target,String command)throws Exception{var json=new JsonObject();json.addProperty("target",target);json.addProperty("command",command);var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(control.get("api").getAsString())).POST(HttpRequest.BodyPublishers.ofString(json.toString())).build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new AssertionError(response.body());}
    private static void connect(NativeClientContext context){client(context,c->{var server=new ServerData("WorldBinder Proxy QA",control.get("host").getAsString(),ServerData.Type.OTHER);ConnectScreen.startConnecting(new TitleScreen(),c,ServerAddress.parseString(server.ip),server,false,null);return null;});}
    private static void configureTranslator(NativeClientContext context){
        if(!control.has("clientTranslator")||control.get("clientTranslator").isJsonNull())return;
        client(context,c->{String kind=control.get("clientTranslator").getAsString();var type=Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");Object version=type.getMethod("getClosest",String.class).invoke(null,control.get("serverMinecraft").getAsString());if(version==null)throw new AssertionError("Original translator has no requested protocol");
            if(kind.equals("viafabric")){Object config=Class.forName("com.viaversion.fabric.ViaFabric").getField("config").get(null);config.getClass().getMethod("setClientSideEnabled",boolean.class).invoke(config,true);config.getClass().getMethod("setClientSideVersion",int.class).invoke(config,type.getMethod("getVersion").invoke(version));}
            else if(kind.equals("viafabricplus")){Object api=Class.forName("com.viaversion.viafabricplus.ViaFabricPlus").getMethod("api").invoke(null);Object translation=api.getClass().getMethod("protocolTranslation").invoke(api);translation.getClass().getMethod("setTargetVersion",type,boolean.class).invoke(translation,version,false);if(!Objects.equals(translation.getClass().getMethod("targetVersion").invoke(translation),version))throw new AssertionError("Original translator did not select the requested protocol");}
            else throw new AssertionError("Unknown original client translator");
            WorldBinder.LOGGER.info("WORLDBINDER_ORIGINAL_CLIENT_TRANSLATOR {} target {}",kind,version);return null;});
    }
    private static boolean stone(Minecraft c){return c.level!=null&&ReceivedChunks.chunk(c.level,0,0)!=null&&c.level.getBlockState(new BlockPos(1,64,1)).is(Blocks.STONE);}
    private static boolean gold(Minecraft c){return c.level!=null&&ReceivedChunks.chunk(c.level,0,0)!=null&&c.level.getBlockState(new BlockPos(1,64,1)).is(Blocks.GOLD_BLOCK);}
    private static void committed(NativeClientContext context){waitFor(context,c->WorldBinderClient.capture().downloadedChunksView().contains(0L)&&!WorldBinderClient.capture().cacheWritePending(0L),"complete received chunk committed");}
    private static WorldScene snapshot(NativeClientContext context){var pending=client(context,c->WorldBinderClient.capture().prepareAreaExport());waitFor(context,c->pending.isDone()&&!WorldBinderClient.capture().isSaving(),"project snapshot");return pending.join();}
    private static boolean has(WorldScene scene,String state){return scene.blocks.stream().anyMatch(b->scene.originX+b.x==1&&scene.originY+b.y==64&&scene.originZ+b.z==1&&b.state.equals(state));}
    private static boolean button(Minecraft c,String key){var screen=ClientCompat.screen(c);if(screen==null)return false;for(var child:screen.children())if(child instanceof Button b&&b.active&&b.visible&&b.getMessage().getString().equals(Lang.string(key))){b.onPress(new KeyEvent(257,0,0));return true;}return false;}
    private static Path save(NativeClientContext context,String id)throws Exception{
        Set<Path> before=client(context,c->new HashSet<>(WorldBinderClient.scenes().scenes()));client(context,c->{WorldBinderClient.capture().requestFinishCapture();return null;});
        waitFor(context,c->{if(ClientCompat.screen(c) instanceof WorldBinderFinishScreen)button(c,"worldbinder.finish.save_now");if(ClientCompat.screen(c) instanceof WorldBinderExportScreen)button(c,"worldbinder.export.review.export");if(net.worldbinder.storage.StorageFlow.progress().stage()==net.worldbinder.storage.StorageStage.FAILED)throw new AssertionError(net.worldbinder.storage.StorageFlow.progress().detail());return !WorldBinderClient.capture().isCapturing()&&!WorldBinderClient.capture().isSaving();},"complete save/export");
        return client(context,c->WorldBinderClient.scenes().scenes().stream().filter(p->!before.contains(p)).filter(p->{try{return !WorldBinderClient.scenes().isRecovery(p)&&Objects.equals(WorldBinderClient.scenes().read(p).projectId,id);}catch(Exception error){return false;}}).findFirst().orElseThrow());
    }
    static void run(NativeClientContext context,String phase)throws Exception{
        checks=0;control=JsonParser.parseString(Files.readString(Path.of(System.getProperty("worldbinder.qaProxyControl")))).getAsJsonObject();
        boolean dimensions=control.has("dimensionChecks")&&control.get("dimensionChecks").getAsBoolean();int expectedDimensions=dimensions?4:2;
        client(context,c->{c.options.pauseOnLostFocus=false;c.options.enableVsync().set(false);c.options.framerateLimit().set(120);c.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);c.options.save();return null;});
        Path fixture=WorldBinderPaths.GAME_DIR.resolve("proxy-fixture.json");
        if(phase.equals("record"))client(context,c->{var config=new WorldBinderConfig();config.targetMinecraftVersion=net.minecraft.SharedConstants.getCurrentVersion().name();config.captureMinY=63;config.captureMaxY=75;config.roamingRadiusChunks=1;config.crashRecovery=true;config.recoveryAutosaveSeconds=1;config.autoDeleteRecovery=false;config.autoCaptureOnJoin=true;config.confirmExistingWorld=false;config.appendTimestampToArchiveName=false;config.includeServerResourcePack=false;WorldBinder.replaceConfig(config);config.save();return null;});
        else client(context,c->{WorldBinder.config().autoCaptureOnJoin=true;return null;});
        configureTranslator(context);connect(context);waitFor(context,c->stone(c)&&c.player!=null,"real Survival backend received");command("survival","tp @a 1.5 65 1.5");waitFor(context,c->WorldBinderClient.capture().isCapturing(),"automatic capture after proxy join");committed(context);
        Path archive;
        if(phase.equals("record")){
            var original=snapshot(context);String survival=original.backendId,project=original.projectId;check(has(original,"minecraft:stone"),"Automatic capture records only the received initial backend");
            waitFor(context,c->WorldBinderClient.capture().networkProfile().backends.containsKey(survival),"initial automatic Recovery associates the project profile");
            client(context,c->{var profile=WorldBinderClient.capture().networkProfile();profile.backends.get(survival).name="Survival";profile.joinBackend=survival;WorldBinder.config().save();DelayedContainerPackets.enabled=true;DelayedContainerPackets.minimumDelayTicks=200;WorldBinderClient.capture().toggleScanner();WorldBinderClient.capture().rescanContainers();return null;});
            waitFor(context,c->ClientCompat.screen(c) instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen&&WorldBinderClient.capture().containerTotalSlots()>=27&&WorldBinderClient.capture().containerKnownSlots()<WorldBinderClient.capture().containerTotalSlots(),"scanner opens a real menu and waits for its deliberately delayed full reply");
            check(client(context,c->WorldBinderClient.capture().exportPreview().containers.values().stream().noneMatch(ContainerRecord::complete)),"A visible menu without its full server reply is not a successful scanned container");context.takeScreenshot("beta-container-scanner-waiting");
            client(context,c->{c.getConnection().sendCommand("server lobby");return null;});waitFor(context,c->gold(c)&&WorldBinderClient.capture().routingReady(),"actual Velocity reconfiguration pauses for unknown Lobby");
            check(client(context,c->WorldBinderClient.capture().isCapturing()&&WorldBinderClient.capture().routingPending()),"Proxy switch preserves the active project and requires explicit backend assignment");check(has(snapshot(context),"minecraft:stone"),"Identical coordinates from the unassigned backend cannot overwrite Survival");
            check(client(context,c->WorldBinderClient.capture().scannerStatus().contains(Lang.string("worldbinder.scanner.suspended"))&&WorldBinderClient.capture().exportPreview().containers.values().stream().noneMatch(ContainerRecord::complete)),"Switching backend while a full container reply is pending suspends the scanner and preserves unknown contents");client(context,c->{DelayedContainerPackets.enabled=false;DelayedContainerPackets.minimumDelayTicks=0;return null;});
            client(context,c->{check(WorldBinderClient.capture().assignBackend(null,"Lobby",false),"User can explicitly isolate Lobby");ClientCompat.setScreen(c,null);return null;});committed(context);var lobby=snapshot(context);check(!lobby.projectId.equals(project)&&has(lobby,"minecraft:gold_block")&&!has(lobby,"minecraft:stone"),"Lobby gets a distinct project at identical dimension and coordinates");
            client(context,c->{c.getConnection().sendCommand("server survival");return null;});waitFor(context,c->stone(c)&&WorldBinderClient.capture().routingReady(),"real return to Survival");
            client(context,c->{check(WorldBinderClient.capture().assignBackend(survival,"Survival",true),"Known backend can load its saved handoff");ClientCompat.setScreen(c,null);return null;});waitFor(context,c->!WorldBinderClient.capture().routingPending()&&!WorldBinderClient.capture().isSaving()&&WorldBinderClient.capture().exportPreview().projectId.equals(project),"original project restored from Recovery");committed(context);check(has(snapshot(context),"minecraft:stone"),"Survival returns with its original data after Lobby");
            client(context,c->{WorldBinderClient.capture().networkProfile().trustDimensionChanges=true;WorldBinder.config().save();return null;});
            command("survival","execute in minecraft:the_nether run forceload add 0 0");command("survival","execute in minecraft:the_nether run setblock 1 64 1 diamond_block");command("survival","execute in minecraft:the_nether run tp @a 1.5 65 1.5");
            waitFor(context,c->c.level!=null&&c.level.dimension().identifier().toString().equals("minecraft:the_nether")&&!WorldBinderClient.capture().routingPending()&&ReceivedChunks.chunk(c.level,0,0)!=null,"trusted backend Nether continues");committed(context);
            command("survival","execute in minecraft:overworld run tp @a 1.5 65 1.5");waitFor(context,c->stone(c)&&!WorldBinderClient.capture().routingPending(),"trusted backend Overworld restored");committed(context);var multi=snapshot(context);check(ProjectDimensions.all(multi).size()==2&&multi.projectId.equals(project),"Related dimensions share a project while backend worlds remain isolated");
            if(control.has("contentChecks")&&control.get("contentChecks").getAsBoolean())checks+=ProtocolContentChecks.run(context,control,c->command("survival",c));
            if(dimensions)checks+=NativeDimensionChecks.run(context,c->command("survival",c),project);
            if(!control.has("nativeTransfer")||control.get("nativeTransfer").getAsBoolean()){
            command("survival","transfer 127.0.0.1 "+control.getAsJsonObject("ports").get("proxy").getAsInt()+" @a");
            waitFor(context,c->stone(c)&&WorldBinderClient.capture().routingReady(),"native Transfer packet and reconnect are fenced");
            check(client(context,c->WorldBinderClient.capture().exportPreview().projectId.equals(project)),"A native transfer does not auto-finalize the project");client(context,c->{WorldBinderClient.capture().assignBackend(survival,"Survival",true);ClientCompat.setScreen(c,null);return null;});waitFor(context,c->!WorldBinderClient.capture().routingPending()&&!WorldBinderClient.capture().isSaving(),"explicit post-transfer association");committed(context);
            }
            if(!control.has("fullUi")||control.get("fullUi").getAsBoolean())checks+=BetaUiChecks.run(context);
            context.takeScreenshot("proxy-network-dimensions");
            var switchingConnection=client(context,c->c.getConnection().getConnection());
            client(context,c->{c.getConnection().sendCommand("server lobby");return null;});waitFor(context,c->WorldBinderClient.capture().routingPending(),"next real backend switch enters its handoff");
            client(context,c->{switchingConnection.disconnect(net.minecraft.network.chat.Component.literal("WorldBinder local QA connection interrupted"));c.disconnectWithSavingScreen();return null;});waitFor(context,c->c.level==null&&WorldBinderClient.capture().routingReady(),"disconnect during a handoff preserves a complete Recovery");
            check(client(context,c->WorldBinderClient.capture().isCapturing()),"Disconnect during an uncertain backend switch retains the paused project");check(has(snapshot(context),"minecraft:stone"),"Offline handoff snapshot still belongs to Survival and contains no Lobby blocks");
            archive=save(context,project);var saved=WorldBinderClient.scenes().read(archive);check(ProjectDimensions.all(saved).size()==expectedDimensions&&saved.backendId.equals(survival),"Export/archive retain all related dimensions and backend identity");
            var json=new JsonObject();json.addProperty("archive",archive.toString());json.addProperty("project",project);json.addProperty("survival",survival);json.addProperty("lobby",lobby.projectId);Files.writeString(fixture,WorldBinder.GSON.toJson(json));
        }else{
            var json=JsonParser.parseString(Files.readString(fixture)).getAsJsonObject();Path previous=Path.of(json.get("archive").getAsString());byte[] bytes=Files.readAllBytes(previous.resolve("worldbinder/worldbinder_archive.json"));var resumed=snapshot(context);
            check(resumed.projectId.equals(json.get("project").getAsString())&&ProjectDimensions.all(resumed).size()==expectedDimensions,"Explicit automatic join association resumes the same multi-dimension project after complete Minecraft restart");
            check(has(resumed,"minecraft:stone"),"Lobby contents did not leak into the resumed Survival project");
            archive=save(context,resumed.projectId);check(Arrays.equals(bytes,Files.readAllBytes(previous.resolve("worldbinder/worldbinder_archive.json"))),"Restart and re-export do not mutate the previous complete revision");
        }
        Path opened=archive;
        if(control.has("contentChecks")&&control.get("contentChecks").getAsBoolean()){
            Path untouched=archive.resolveSibling(archive.getFileName()+"_target_copy");
            try(var files=Files.walk(archive)){for(Path source:files.toList()){Path target=untouched.resolve(archive.relativize(source));if(Files.isDirectory(source))Files.createDirectories(target);else if(!source.getFileName().toString().equals("session.lock"))Files.copy(source,target);}}
            var evidence=new JsonObject();evidence.addProperty("target",control.get("serverMinecraft").getAsString());evidence.addProperty("world",untouched.toString());evidence.addProperty("sourceArchive",archive.toString());
            Files.writeString(WorldBinderPaths.GAME_DIR.resolve("protocol-"+phase+"-target.json"),WorldBinder.GSON.toJson(evidence));
            // The target copy is opened by its actual original Minecraft client. Do not silently
            // upgrade it with the modern QA client (FileFixerUpper also needs Windows directory moves).
            String current=net.minecraft.SharedConstants.getCurrentVersion().name();
            if(!current.equals(control.get("serverMinecraft").getAsString())){
                WorldScene nativeCopy=WorldBinderClient.scenes().read(archive);nativeCopy.targetMinecraftVersion=current;
                opened=archive.resolveSibling(archive.getFileName()+"_native_"+current);
                net.worldbinder.export.VanillaWorldExporter.export(nativeCopy,opened);
                net.worldbinder.export.common.AuxiliaryDataWriter.write(net.worldbinder.export.common.DimensionExportView.prepare(net.worldbinder.export.common.ExportFiltering.apply(nativeCopy)),opened);
                check(net.worldbinder.validation.ExportValidator.validate(nativeCopy,opened).filesValid,"The modern native client receives its own matching target format while the older target copy stays untouched");
            }
        }
        client(context,c->{c.disconnectWithSavingScreen();return null;});waitFor(context,c->c.level==null,"proxy client disconnects after save");
        try(var world=context.openWorld(opened)){waitFor(context,c->stone(c)&&c.player!=null,"actual exported multi-dimension world opens");check(client(context,ProxyNetworkChecks::stone),"Exported backend world opens with its correct received block");context.takeScreenshot("proxy-"+phase+"-export-opened");if(dimensions)checks+=NativeDimensionChecks.opened(context);}
        String proxy=control.has("proxy")?control.get("proxy").getAsString():"velocity";
        Files.writeString(WorldBinderPaths.GAME_DIR.resolve("proxy-"+phase+"-RESULT.txt"),"PASS real "+proxy+" "+phase+": "+checks+" checks; "+archive+"\n");WorldBinder.LOGGER.info("WORLDBINDER_PROXY_PASS {} {} checks ({})",phase,checks,proxy);
    }
}
